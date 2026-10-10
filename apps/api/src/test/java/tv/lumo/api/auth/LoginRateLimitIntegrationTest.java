package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * S10B-02: spreading sign-in attempts over many addresses no longer escapes the
 * limit.
 *
 * <p>The test client is the trusted proxy here ({@code 127.0.0.1}), so every
 * attempt really does arrive from a different client address — the strongest
 * form of the bypass. The per-IP+email key then never fills; the per-email cap
 * must.
 */
@Import(PostgresContainerInitializer.class)
@TestPropertySource(properties = {
        "lumo.rate-limit.trusted-proxies=127.0.0.1,::1",
        "lumo.rate-limit.auth-attempts-per-minute=5",
        "lumo.rate-limit.login-attempts-per-email-per-minute=10",
})
class LoginRateLimitIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("une adresse différente à chaque essai : l'email seul finit par limiter")
    void capsAttemptsPerEmailAcrossAddresses() {
        String email = "spray-" + UUID.randomUUID() + "@test.example";

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(login(email, "198.51.100." + attempt)).isEqualTo(401);
        }

        assertThat(login(email, "198.51.100.200")).isEqualTo(429);
    }

    @Test
    @DisplayName("le plafond par email ne touche pas un autre compte")
    void otherEmailsAreUnaffected() {
        String target = "target-" + UUID.randomUUID() + "@test.example";
        for (int attempt = 1; attempt <= 11; attempt++) {
            login(target, "203.0.113." + attempt);
        }

        assertThat(login("other-" + UUID.randomUUID() + "@test.example", "203.0.113.50")).isEqualTo(401);
    }

    private int login(String email, String forwardedFor) {
        return RestClient.create("http://localhost:" + port)
                .post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", forwardedFor)
                .body("""
                        {"email":"%s","password":"wrong-password","device":{"platform":"WEB"}}
                        """.formatted(email))
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
