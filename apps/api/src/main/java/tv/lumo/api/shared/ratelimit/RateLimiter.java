package tv.lumo.api.shared.ratelimit;

/**
 * Counts attempts against a key and says whether one more is allowed.
 *
 * <p>Keys are opaque to the implementation and are built by callers as
 * {@code "<action>:<client ip>"} or {@code "<action>:<user id>"}. They are never
 * logged: on the sign-in surface a key identifies who is trying to sign in.
 *
 * <p>The {@link CaffeineRateLimiter} backing this is <b>per instance</b>. With
 * more than one replica the effective limit multiplies by the replica count.
 * That is acceptable for v1 (single instance) and is the reason this is an
 * interface: a Redis-backed implementation drops in without touching a caller.
 */
public interface RateLimiter {

    /**
     * Records an attempt and reports the outcome.
     *
     * @param key      opaque bucket key
     * @param perMinute attempts allowed per rolling minute
     */
    Decision attempt(String key, int perMinute);

    /**
     * Forgets a key. Called after a successful sign-in so a user who mistyped
     * their password four times is not still throttled once they get it right.
     */
    void reset(String key);

    /**
     * @param allowed           whether the caller may proceed
     * @param retryAfterSeconds how long to wait; meaningful only when not allowed
     */
    record Decision(boolean allowed, long retryAfterSeconds) {

        public static Decision allow() {
            return new Decision(true, 0);
        }

        public static Decision deny(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
