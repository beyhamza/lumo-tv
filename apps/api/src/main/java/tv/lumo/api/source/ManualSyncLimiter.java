package tv.lumo.api.source;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * One synchronisation a user asked for, per source, per interval (C4).
 *
 * <p>This limit protects the <i>user's own</i> IPTV server: an Xtream ingestion
 * is hundreds of requests to a third-party panel, and a panel that sees them
 * every thirty seconds throttles or bans the account. It is a product safeguard,
 * not a capacity one (ADR 0005).
 *
 * <p>Not the {@code RateLimiter} the sign-in surface uses, and deliberately: that
 * one counts attempts per minute and answers with a backoff that doubles, which
 * is the right shape against guessing and the wrong one here. The contract
 * promises a {@code Retry-After} the client can show — "again in 3 min" — so the
 * answer has to be the time that is actually left, not a penalty.
 *
 * <p>Only {@code POST /sources/{id}/sync} comes through here. The automatic
 * refresh does not, and neither does the ingestion that follows a change of
 * credentials: correcting a password must never make anyone wait.
 *
 * <p>Per instance, like every limiter in this application, and acceptable for
 * the same reason: v1 runs one.
 */
@Component
public class ManualSyncLimiter {

    private static final int MAX_TRACKED_SOURCES = 100_000;

    private final Duration interval;
    private final Ticker ticker;
    /** Source id → the ticker reading when its last manual sync was accepted. */
    private final Cache<UUID, Long> accepted;

    @Autowired
    public ManualSyncLimiter(LumoProperties properties) {
        this(properties.rateLimit().manualSyncInterval(), Ticker.systemTicker());
    }

    ManualSyncLimiter(Duration interval, Ticker ticker) {
        this.interval = interval;
        this.ticker = ticker;
        this.accepted = Caffeine.newBuilder()
                .expireAfterWrite(interval)
                .ticker(ticker)
                .maximumSize(MAX_TRACKED_SOURCES)
                .build();
    }

    /**
     * Claims the interval for a source.
     *
     * @return zero when the caller may proceed, otherwise the seconds left
     *         before it may — never less than one, so a client never reads
     *         "retry in 0 s" and retries into the same refusal
     */
    public long tryAcquire(UUID sourceId) {
        long now = ticker.read();
        long[] wait = {0};
        accepted.asMap().compute(sourceId, (id, last) -> {
            if (last == null || now - last >= interval.toNanos()) {
                return now;
            }
            long leftNanos = interval.toNanos() - (now - last);
            wait[0] = Math.max(1, Duration.ofNanos(leftNanos).toSeconds()
                    + (leftNanos % 1_000_000_000L == 0 ? 0 : 1));
            return last;
        });
        return wait[0];
    }

    /** Gives the interval back when the synchronisation did not start after all. */
    public void release(UUID sourceId) {
        accepted.invalidate(sourceId);
    }
}
