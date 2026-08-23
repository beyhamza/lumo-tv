package tv.lumo.api.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limiter with progressive backoff.
 *
 * <p>Counters live in a Caffeine cache bounded by both size and time. That
 * bounding is the point: a hand-rolled map plus a scheduled sweep grows
 * unbounded between sweeps, and the scenario a rate limiter is built for is
 * exactly the one that fills it — an attacker cycling through keys.
 *
 * <p><b>Progressive backoff</b> (US-02: "après 5 échecs consécutifs, une
 * temporisation progressive s'applique"). Once over the limit, the wait doubles
 * with each further attempt, capped at five minutes. A flat window lets an
 * attacker spend the full quota every minute forever; doubling makes sustained
 * guessing cost more than it returns, while a user who mistyped their password
 * twice barely notices.
 *
 * <p>The window is a rolling minute per key, reset by
 * {@link #reset(String)} on success.
 */
@Component
public class CaffeineRateLimiter implements RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long MAX_BACKOFF_SECONDS = 300;
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(WINDOW)
            .maximumSize(MAX_TRACKED_KEYS)
            .build();

    @Override
    public Decision attempt(String key, int perMinute) {
        AtomicInteger counter = counters.get(key, k -> new AtomicInteger());
        int attempts = counter.incrementAndGet();

        if (attempts <= perMinute) {
            return Decision.allow();
        }
        return Decision.deny(backoffSeconds(attempts - perMinute));
    }

    @Override
    public void reset(String key) {
        counters.invalidate(key);
    }

    /** 1 over the limit → 2 s, then 4, 8, 16 … capped at five minutes. */
    private static long backoffSeconds(int over) {
        if (over >= 9) {
            return MAX_BACKOFF_SECONDS;
        }
        return Math.min(MAX_BACKOFF_SECONDS, 1L << over);
    }
}
