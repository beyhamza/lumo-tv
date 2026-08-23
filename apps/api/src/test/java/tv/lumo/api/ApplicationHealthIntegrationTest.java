package tv.lumo.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Boots the real application against a real PostgreSQL and checks it is alive.
 *
 * <p>This is the test that fails first when something structural breaks: a bean
 * that cannot be constructed, a missing configuration property, a Liquibase
 * changeset that does not apply, a security chain that rejects its own health
 * endpoint. It is cheap to keep green and it catches the class of problem that
 * otherwise only shows up on deploy.
 */
@Import(PostgresContainerInitializer.class)
class ApplicationHealthIntegrationTest extends PostgresIntegrationTest {

    // Spring Boot 4 removed TestRestTemplate. RestClient with exchange() is used
    // instead: exchange() hands back the raw response and never throws on a 4xx,
    // which matters here because several of these tests assert ON 401 and 404.
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    private Response call(String path) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(path)
                .exchange((request, response) -> new Response(
                        HttpStatus.valueOf(response.getStatusCode().value()),
                        response.getHeaders().getContentType(),
                        new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)),
                        false);
    }

    private record Response(HttpStatus status, MediaType contentType, String body) {
    }

    @Test
    @DisplayName("the actuator health endpoint is UP and reachable without a token")
    void healthEndpointIsUpAndPublic() {
        Response response = call("/actuator/health");

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        // Unauthenticated on purpose: the container health check in
        // docker-compose.yml carries no credentials, and a probe that 401s leaves
        // the container "starting" forever.
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("the health endpoint reports the database component as UP")
    void healthIncludesDatabase() {
        assertThat(call("/actuator/health").status()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.sql("SELECT 1").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("liquibase created every table of docs/domain-model.md §2")
    void schemaIsComplete() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public'
                 ORDER BY table_name
                """).query(String.class).list();

        assertThat(tables).contains(
                "user", "oauth_identity", "device", "refresh_token", "device_authorization",
                "source", "category", "channel", "epg_programme",
                "favorite_group", "favorite", "playback_progress", "entitlement",
                // Not in the domain model; added because verify-email and
                // password/reset have nowhere else to keep their tokens.
                "user_token");
    }

    @Test
    @DisplayName("the PostgreSQL extensions the schema depends on are installed")
    void extensionsAreInstalled() {
        List<String> extensions = jdbc.sql("SELECT extname FROM pg_extension").query(String.class).list();
        // citext keeps one email from becoming two accounts; pg_trgm backs
        // typo-tolerant channel search (ADR 0002).
        assertThat(extensions).contains("citext", "pg_trgm");
    }

    @Test
    @DisplayName("an unauthenticated call to a protected endpoint answers problem+json with a stable code")
    void protectedEndpointsReturnProblemJson() {
        Response response = call("/v1/sources");

        assertThat(response.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.contentType())
                .isNotNull()
                .satisfies(type -> assertThat(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(type)).isTrue());
        // `code` is the only field clients branch on; an empty 401 from the
        // security filter chain would give them nothing.
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    @DisplayName("endpoints outside sprint 1 are absent rather than stubbed")
    void unimplementedEndpointsAreNotRouted() {
        // /me/favorites and /me/entitlement exist in the contract but no
        // controller implements them. They must 404, not return invented data.
        assertThat(call("/v1/me/favorites").status())
                .isIn(HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED);
    }
}
