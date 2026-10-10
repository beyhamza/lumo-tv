package tv.lumo.api.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * S10B-02: {@code X-Forwarded-For} is believed only when our own proxy sent it.
 *
 * <p>Every address is a literal. Nothing here touches a resolver, and nothing in
 * {@link ClientIp} may either: the header is attacker input.
 */
class ClientIpTest {

    private static MockHttpServletRequest request(String peer, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwarded != null) {
            request.addHeader("X-Forwarded-For", forwarded);
        }
        return request;
    }

    @Test
    @DisplayName("sans proxy de confiance, l'en-tête forgé est ignoré")
    void ignoresTheHeaderByDefault() {
        ClientIp clientIp = new ClientIp(List.of());

        assertThat(clientIp.of(request("203.0.113.7", "198.51.100.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("d'un pair qui n'est pas un proxy de confiance, l'en-tête est ignoré")
    void ignoresTheHeaderFromAnUntrustedPeer() {
        ClientIp clientIp = new ClientIp(List.of("10.0.0.0/8"));

        assertThat(clientIp.of(request("203.0.113.7", "198.51.100.1"))).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("derrière un proxy de confiance, l'entrée la plus à droite non fiable est le client")
    void takesTheRightMostUntrustedHop() {
        ClientIp clientIp = new ClientIp(List.of("10.0.0.0/8"));

        // The caller wrote "1.2.3.4" itself; our proxy appended what it saw.
        String key = clientIp.of(request("10.0.0.5", "1.2.3.4, 198.51.100.9"));

        assertThat(key).isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("les proxies de confiance enchaînés sont sautés")
    void skipsChainedTrustedProxies() {
        ClientIp clientIp = new ClientIp(List.of("10.0.0.0/8", "192.168.1.10"));

        String key = clientIp.of(request("10.0.0.5", "198.51.100.9, 192.168.1.10, 10.2.3.4"));

        assertThat(key).isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("derrière un proxy de confiance sans en-tête, le pair est la clé")
    void fallsBackToThePeer() {
        ClientIp clientIp = new ClientIp(List.of("10.0.0.5"));

        assertThat(clientIp.of(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("IPv6 : un bloc CIDR couvre ses adresses et seulement elles")
    void matchesIpv6Blocks() {
        ClientIp clientIp = new ClientIp(List.of("fd00::/8"));

        assertThat(clientIp.of(request("fd12::1", "2001:db8::1"))).isEqualTo("2001:db8::1");
        assertThat(clientIp.of(request("fe80::1", "2001:db8::1"))).isEqualTo("fe80::1");
    }

    @Test
    @DisplayName("une valeur forgée démesurée est tronquée")
    void boundsTheKey() {
        ClientIp clientIp = new ClientIp(List.of("10.0.0.5"));

        assertThat(clientIp.of(request("10.0.0.5", "x".repeat(500)))).hasSize(45);
    }

    @Test
    @DisplayName("une configuration invalide ou /0 refuse de démarrer")
    void rejectsBadConfiguration() {
        assertThatThrownBy(() -> new ClientIp(List.of("0.0.0.0/0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIp(List.of("proxy.internal"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIp(List.of("10.0.0.0/33"))).isInstanceOf(IllegalArgumentException.class);
    }
}
