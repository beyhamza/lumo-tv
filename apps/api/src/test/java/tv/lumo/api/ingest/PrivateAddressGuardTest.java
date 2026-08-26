package tv.lumo.api.ingest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tv.lumo.api.generated.model.IngestionErrorCode;

/**
 * The guard that keeps {@code POST /sources} from being a way to read our own
 * network.
 *
 * <p>Every address here is a literal, so nothing in this test asks a resolver
 * anything. That is deliberate twice over: a unit test that depends on DNS is a
 * unit test that fails on a train, and the addresses themselves are the
 * specification — each line is one route in.
 */
class PrivateAddressGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",        // loopback: the server itself
            "127.1.2.3",        // the rest of 127/8, which people forget
            "0.0.0.0",          // unspecified
            "10.1.2.3",         // private
            "172.16.5.4",       // private
            "192.168.1.1",      // private, and the router of whoever runs this
            "169.254.169.254",  // cloud metadata, the one that hands out credentials
            "100.64.0.1",       // carrier NAT, no JDK predicate of its own
            "::1",              // loopback, IPv6
            "fd00::1",          // unique local, IPv6
    })
    @DisplayName("une adresse interne est refusée, quelle que soit la famille")
    void refusesInternalAddresses(String address) {
        assertThatThrownBy(() -> PrivateAddressGuard.requirePublic(address, false))
                .isInstanceOf(IngestionException.class)
                .satisfies(thrown -> org.assertj.core.api.Assertions
                        .assertThat(((IngestionException) thrown).code())
                        // Unreachable, not "forbidden": the answer must not tell a
                        // caller whether something is listening on the address they
                        // probed.
                        .isEqualTo(IngestionErrorCode.SOURCE_UNREACHABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "8.8.8.8", "2001:4860:4860::8888"})
    @DisplayName("une adresse publique passe")
    void allowsPublicAddresses(String address) {
        assertThatCode(() -> PrivateAddressGuard.requirePublic(address, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un nom qui ne résout pas est traité comme un serveur éteint")
    void unresolvableHostsAreUnreachable() {
        // .invalid is reserved by RFC 2606 precisely so that it never resolves.
        assertThatThrownBy(() ->
                PrivateAddressGuard.requirePublic("nothing.here.invalid", false))
                .isInstanceOf(IngestionException.class);
    }

    @Test
    @DisplayName("l'échappatoire des tests laisse passer la boucle locale, et n'existe que pour ça")
    void theTestEscapeHatchOpensLoopback() {
        // The end-to-end stack serves its bench from a container on a private
        // network. Nothing else may set this, and application.yml says so.
        assertThatCode(() -> PrivateAddressGuard.requirePublic("127.0.0.1", true))
                .doesNotThrowAnyException();
    }
}
