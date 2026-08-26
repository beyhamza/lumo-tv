package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * {@code GET /me/devices} and {@code DELETE /me/devices/&#123;id&#125;}, over HTTP
 * because the answer depends on which token made the call.
 *
 * <p>{@code is_current} is computed against the presented access token, which is
 * exactly why the contract makes it a property of the response rather than
 * something a client works out. A test that called the controller directly would
 * be testing something else.
 *
 * <p>The listing rule under test — <b>a device is listed while it holds a live
 * session</b> — is the same rule the plan limit counts on, and it is what keeps
 * the list from growing by one entry every time somebody signs in.
 */
@Import(PostgresContainerInitializer.class)
class AccountDevicesIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionService sessions;

    @Autowired
    private UserRepository users;

    @Autowired
    private DeviceRepository devices;

    private UserRow user;

    @BeforeEach
    void createAccount() {
        user = users.insert(UUID.randomUUID(),
                "devices-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Devices Test", "en");
    }

    @Test
    @DisplayName("exactly one device is is_current, and it is the caller's")
    void oneDeviceIsCurrent() {
        UUID phone = devices.insert(user.id(), Platform.ANDROID_MOBILE.getValue(),
                "Phone", "Model", "0.1.0");
        UUID television = devices.insert(user.id(), Platform.ANDROID_TV.getValue(),
                "Living room", "Model", "0.1.0");
        AuthSession phoneSession = sessions.openSession(user, phone);
        sessions.openSession(user, television);

        Response response = get("/v1/me/devices", phoneSession.getAccessToken());

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).contains(phone.toString(), television.toString());
        // One true, one false. This is the row the user must not revoke by
        // accident, and the television just linked is by definition not the caller.
        assertThat(countOf(response.body(), "\"is_current\":true")).isEqualTo(1);
        assertThat(countOf(response.body(), "\"is_current\":false")).isEqualTo(1);
    }

    @Test
    @DisplayName("a device with no live session is not listed")
    void signedOutDevicesDisappear() {
        UUID phone = devices.insert(user.id(), Platform.ANDROID_MOBILE.getValue(),
                "Phone", "Model", "0.1.0");
        UUID laptop = devices.insert(user.id(), Platform.WEB.getValue(), "lumo.tv", null, "0.1.0");
        AuthSession phoneSession = sessions.openSession(user, phone);
        AuthSession laptopSession = sessions.openSession(user, laptop);

        sessions.signOut(laptopSession.getRefreshToken(), user.id());

        String body = get("/v1/me/devices", phoneSession.getAccessToken()).body();
        assertThat(body).contains(phone.toString());
        // Signing out unlinks. Otherwise the list grows by one every time the user
        // opens the site in a private window.
        assertThat(body).doesNotContain(laptop.toString());
    }

    @Test
    @DisplayName("revoking a device unlinks it; revoking a stranger's is a 404 with a code")
    void revokingIsScopedToTheCaller() {
        UUID phone = devices.insert(user.id(), Platform.ANDROID_MOBILE.getValue(),
                "Phone", "Model", "0.1.0");
        UUID television = devices.insert(user.id(), Platform.ANDROID_TV.getValue(),
                "Living room", "Model", "0.1.0");
        AuthSession phoneSession = sessions.openSession(user, phone);
        sessions.openSession(user, television);

        assertThat(delete("/v1/me/devices/" + television, phoneSession.getAccessToken()).status())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/v1/me/devices", phoneSession.getAccessToken()).body())
                .doesNotContain(television.toString());

        Response missing = delete("/v1/me/devices/" + UUID.randomUUID(), phoneSession.getAccessToken());
        assertThat(missing.status()).isEqualTo(HttpStatus.NOT_FOUND);
        // A specific code, so the web renders "no such device" rather than
        // "this feature does not exist yet".
        assertThat(missing.body()).contains("\"code\":\"DEVICE_NOT_FOUND\"");
    }

    // ---- helpers ------------------------------------------------------------

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private Response get(String path, String token) {
        return exchange(RestClient.create("http://localhost:" + port).get().uri(path), token);
    }

    private Response delete(String path, String token) {
        return exchange(RestClient.create("http://localhost:" + port).delete().uri(path), token);
    }

    private Response exchange(RestClient.RequestHeadersSpec<?> spec, String token) {
        return spec.header("Authorization", "Bearer " + token)
                .exchange((request, response) -> new Response(
                        HttpStatus.valueOf(response.getStatusCode().value()),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)),
                        false);
    }

    private record Response(HttpStatus status, String body) {
    }
}
