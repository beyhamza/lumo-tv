package tv.lumo.api.ingest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * Explicit backpressure for every outbound call in this package.
 *
 * <p><b>This class exists because virtual threads removed the ceiling we used to
 * get for free.</b> A bounded platform-thread pool was implicit backpressure: it
 * could not run more work than it had threads. Virtual threads have no such
 * limit, so ten thousand concurrent syncs would happily open ten thousand
 * connections to one user's IPTV panel.
 *
 * <p>That is not a performance problem, it is a product problem. The server being
 * hammered belongs to the user, and the account that gets throttled or banned is
 * theirs. ADR 0005 §1 calls this mandatory rather than an optimisation, and it is
 * the reason it is enforced at a chokepoint instead of being left to each caller.
 *
 * <p>Two gates, taken in this order: a global cap on concurrent syncs, then a
 * per-host cap. Global first, so a queue for one slow host cannot occupy every
 * worker in the application.
 */
@Component
public class HostConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(HostConcurrencyLimiter.class);
    private static final Duration HOST_ENTRY_TTL = Duration.ofHours(1);
    private static final int MAX_TRACKED_HOSTS = 10_000;

    private final Semaphore globalPermits;
    private final int perHostPermits;
    private final long acquireTimeoutSeconds;

    /**
     * Per-host semaphores, evicted when a host has been idle for an hour.
     *
     * <p>The eviction window is deliberately far longer than any sync: a host
     * being actively used is being accessed, so it is never evicted while it
     * matters. An entry dropped and immediately recreated could briefly allow one
     * extra concurrent call to a host nobody has touched for an hour, which is a
     * far smaller problem than a map that grows once per host forever.
     */
    private final Cache<String, Semaphore> hostPermits = Caffeine.newBuilder()
            .expireAfterAccess(HOST_ENTRY_TTL)
            .maximumSize(MAX_TRACKED_HOSTS)
            .build();

    public HostConcurrencyLimiter(LumoProperties properties) {
        this.globalPermits = new Semaphore(properties.ingest().maxConcurrentSyncs());
        this.perHostPermits = properties.ingest().maxConcurrentPerHost();
        // Waiting longer than the request timeout would be pointless: the call
        // would time out on its own the moment it started.
        this.acquireTimeoutSeconds = properties.ingest().httpTimeout().toSeconds() * 2;
    }

    /**
     * Runs {@code work} holding a global permit and a permit for {@code host}.
     *
     * @throws IngestionException {@code SOURCE_UNREACHABLE} if a permit cannot be
     *                            obtained in time — from the user's point of view a
     *                            request that never got to start and one that timed
     *                            out are the same event
     */
    public <T> T onHost(String host, Callable<T> work) {
        boolean globalHeld = false;
        Semaphore hostSemaphore = null;
        try {
            globalHeld = globalPermits.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
            if (!globalHeld) {
                log.warn("Global ingestion concurrency cap reached; rejecting work for host {}", host);
                throw new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                        "Ingestion capacity exhausted");
            }

            hostSemaphore = hostPermits.get(host, key -> new Semaphore(perHostPermits));
            if (!hostSemaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Per-host concurrency cap reached for {}", host);
                throw new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE,
                        "Too many concurrent requests to this host");
            }

            try {
                return work.call();
            } finally {
                hostSemaphore.release();
                hostSemaphore = null;
            }
        } catch (IngestionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionException(IngestionErrorCode.SOURCE_UNREACHABLE, "Interrupted while queued");
        } catch (Exception e) {
            throw IngestionException.from(e);
        } finally {
            if (hostSemaphore != null) {
                hostSemaphore.release();
            }
            if (globalHeld) {
                globalPermits.release();
            }
        }
    }
}
