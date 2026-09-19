package tv.lumo.api.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pace of the synchronisations a user asks for (lot C4).
 *
 * <p>What matters here is the number that goes out in {@code Retry-After}: the
 * client shows it, so it has to be the time that is actually left — not a
 * penalty that grows, and never zero.
 */
class ManualSyncLimiterTest {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    private final AtomicLong nanos = new AtomicLong();
    private final ManualSyncLimiter limiter = new ManualSyncLimiter(FIVE_MINUTES, nanos::get);

    @Test
    @DisplayName("la première demande passe, la seconde apprend combien il reste")
    void theSecondRequestIsToldHowLongIsLeft() {
        UUID source = UUID.randomUUID();

        assertThat(limiter.tryAcquire(source)).isZero();

        advance(Duration.ofSeconds(90));
        assertThat(limiter.tryAcquire(source)).isEqualTo(210);
    }

    @Test
    @DisplayName("un refus ne rallonge pas l'attente")
    void aRefusalDoesNotExtendTheWait() {
        UUID source = UUID.randomUUID();
        limiter.tryAcquire(source);

        // Ten impatient taps. A limiter that counted them — the one the sign-in
        // surface uses does, on purpose — would push the wait out each time.
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire(source);
        }
        advance(Duration.ofMinutes(4));

        assertThat(limiter.tryAcquire(source)).isEqualTo(60);
    }

    @Test
    @DisplayName("une fois l'intervalle écoulé, la demande repasse")
    void theIntervalEnds() {
        UUID source = UUID.randomUUID();
        limiter.tryAcquire(source);

        advance(FIVE_MINUTES);

        assertThat(limiter.tryAcquire(source)).isZero();
    }

    @Test
    @DisplayName("l'attente annoncée n'est jamais zéro")
    void theWaitIsNeverZero() {
        UUID source = UUID.randomUUID();
        limiter.tryAcquire(source);

        advance(FIVE_MINUTES.minusMillis(200));

        // "Retry in 0 s" is an invitation to retry into the same refusal.
        assertThat(limiter.tryAcquire(source)).isEqualTo(1);
    }

    @Test
    @DisplayName("chaque source a son propre intervalle")
    void sourcesDoNotShareAnInterval() {
        limiter.tryAcquire(UUID.randomUUID());

        assertThat(limiter.tryAcquire(UUID.randomUUID())).isZero();
    }

    @Test
    @DisplayName("une synchronisation qui n'a pas démarré rend son intervalle")
    void releaseGivesTheIntervalBack() {
        UUID source = UUID.randomUUID();
        limiter.tryAcquire(source);

        limiter.release(source);

        assertThat(limiter.tryAcquire(source)).isZero();
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
