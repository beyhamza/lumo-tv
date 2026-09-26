package tv.lumo.api.epg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.error.ApiException;

/**
 * The gate itself, without a Spring context (I-5, S9-07-03).
 *
 * <p>What matters is that "unset" and "0" are inert, that an armed value is
 * served with the status it names, and that a value that is not a server error
 * is refused at construction rather than answered as a body. The HTTP wiring is
 * {@code EpgFaultIntegrationTest}'s.
 */
class EpgFaultInjectionTest {

    @Test
    @DisplayName("désarmé : ne fait rien")
    void offIsInert() {
        assertThatCode(() -> new EpgFaultInjection(0).check()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("armé à 503 : refuse en SERVICE_UNAVAILABLE, code générique")
    void armedServesTheNamedStatus() {
        ApiException thrown = assertThrows(ApiException.class, () -> new EpgFaultInjection(503).check());

        assertThat(thrown.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(thrown.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("une valeur qui n'est ni 0 ni un 5xx arrête le démarrage")
    void aNonServerStatusIsRefusedAtStartUp() {
        assertThatThrownBy(() -> new EpgFaultInjection(200))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LUMO_EPG_FAULT");
        assertThatThrownBy(() -> new EpgFaultInjection(404))
                .isInstanceOf(IllegalStateException.class);
    }
}
