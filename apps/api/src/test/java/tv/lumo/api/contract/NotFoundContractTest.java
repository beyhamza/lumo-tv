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
 * The two kinds of 404 this API produces, and why a client must tell them apart.
 *
 * <p>This is a cross-application contract, not an internal detail. The rule:
 * <b>a 404 carrying the generic {@code NOT_FOUND} code came from the router; a
 * 404 carrying a specific one came from a controller.</b> {@code apps/web} reads
 * the first as "this feature does not exist here" and the second as "no such
 * resource", and those are different screens.
 *
 * <p>Nothing else pins that rule, and it is not obvious from either side: the
 * first version of the web's check looked for a 404 with <i>no</i> code at all,
 * on the assumption that an unrouted path never reaches the application. It does
 * — {@code GlobalExceptionHandler} answers {@code NoResourceFoundException} with
 * a full problem+json body — so that check would never have fired. This test is
 * what found that, and what will find it again if a controller ever starts using
 * the bare {@code NOT_FOUND} code.
 *
 * <h2>What changed</h2>
 *
 * <p>This test used to point at {@code /me/devices}, {@code /me/entitlement} and
 * {@code /me/favorites}, which had no controller. All three now answer, along
 * with the rest of the {@code account}, {@code userdata} and {@code billing}
 * tags, so the router half of the rule is exercised against a path that is not in
 * the contract at all — which is the only kind of path that can still reach the
 * router unrouted.
 */
@Import(PostgresContainerInitializer.class)
class NotFoundContractTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    @Test
    @DisplayName("a path with no route answers 404 with the generic NOT_FOUND code")
    void unroutedPathsAnswerWithTheGenericCode() {
        String token = accessToken();

        for (String path : new String[] {"/v1/me/nothing-here", "/v1/not-a-resource"}) {
            Response response = get(path, token);

            assertThat(response.status())
                    .as("%s is not in the contract and has no route", path)
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

    @Test
    @DisplayName("every tag in the contract now has a controller")
    void everyContractTagIsServed() {
        String token = accessToken();

        // One representative path per tag that had no controller before this
        // change. A 404 here means a whole tag lost its controller, which is the
        // regression the loop above can no longer catch.
        for (String path : new String[] {"/v1/me", "/v1/me/devices", "/v1/me/entitlement",
                "/v1/me/favorites", "/v1/me/favorite-groups", "/v1/me/progress",
                "/v1/me/recent-channels"}) {
            assertThat(get(path, token).status())
                    .as("%s is in the contract and must be served", path)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    private String accessToken() {
        UserRow user = users.insert(UUID.randomUUID(),
                "not-found-" + UUID.randomUUID() + "@test.example",
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
