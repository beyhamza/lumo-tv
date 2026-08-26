package tv.lumo.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * How an endpoint that has no controller answers — and how that differs from a
 * resource that does not exist.
 *
 * <p>This is a cross-application contract, not an internal detail. `apps/web`
 * shows "not built yet" on `/app/devices` and `/app/subscription` instead of
 * claiming an outage, and it tells the two apart with one rule: <b>a 404
 * carrying the generic {@code NOT_FOUND} code came from the router; a 404
 * carrying a specific one came from a controller.</b>
 *
 * <p>Nothing else pins that rule. The health suite only asserts
 * {@code isIn(NOT_FOUND, UNAUTHORIZED)} for an unimplemented path, which stays
 * true whatever the body looks like. And the rule is not obvious from either
 * side: the first version of the web's check looked for a 404 with <i>no</i>
 * code at all, on the assumption that an unrouted path never reaches the
 * application. It does — {@code GlobalExceptionHandler} answers
 * {@code NoResourceFoundException} with a full problem+json body — so that
 * check would never have fired and every unbuilt screen would have gone on
 * claiming an outage. This test is what found that, and what will find it again
 * if a controller ever starts using the bare {@code NOT_FOUND} code.
 *
 * <p>Both endpoints below are outside sprint 1 (auth, sources, catalogue) and
 * are deliberately unimplemented: the alternative was a stub returning invented
 * data, which is worse.
 */
@Import(PostgresContainerInitializer.class)
class NotImplementedEndpointsTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    @Test
    @DisplayName("an endpoint with no controller answers 404 with the generic NOT_FOUND code")
    void unimplementedEndpointsAnswerWithTheGenericCode() {
        String token = accessToken();

        for (String path : new String[] {"/v1/me/devices", "/v1/me/entitlement", "/v1/me/favorites"}) {
            Response response = get(path, token);

            assertThat(response.status())
                    .as("%s is in the contract but has no controller", path)
                    .isEqualTo(HttpStatus.NOT_FOUND);
            // The generic code, emitted only by the router-level handler in
            // GlobalExceptionHandler — no controller uses ErrorCode.NOT_FOUND.
            // This is what apps/web reads as "not built yet".
            assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
        }
    }

    @Test
    @DisplayName("a missing resource on an implemented endpoint answers 404 WITH a `code`")
    void missingResourcesAnswerWithACode() {
        String token = accessToken();

        Response response = get("/v1/sources/" + UUID.randomUUID(), token);

        assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND);
        // The other side of the rule: this 404 is an answer, not a missing
        // route, and a client must render "no such source" rather than
        // "this feature does not exist yet".
        assertThat(response.body()).contains("\"code\":\"SOURCE_NOT_FOUND\"");
    }

    private String accessToken() {
        UserRow user = users.insert(UUID.randomUUID(),
                "not-implemented-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Test", "en");
        UUID deviceId = devices.insert(user.id(), Platform.WEB.getValue(),
                "lumo.tv", null, "0.1.0");
        return sessions.openSession(user, deviceId).getAccessToken();
    }

    private Response get(String path, String token) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(path)
                .header("Authorization", "Bearer " + token)
                .exchange((request, response) -> new Response(
                        HttpStatus.valueOf(response.getStatusCode().value()),
                        new String(response.getBody().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8)),
                        false);
    }

    private record Response(HttpStatus status, String body) {
    }
}
