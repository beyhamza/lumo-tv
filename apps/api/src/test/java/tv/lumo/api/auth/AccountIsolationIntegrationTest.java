package tv.lumo.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.MultiSourceAccountsFixture;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;
import tv.lumo.api.userdata.UserdataService;

/**
 * {@code R020-01} §3.1 — no data crosses from account A to account B, over HTTP
 * with each account's own bearer token.
 *
 * <p>The existing isolation tests call services with a {@code userId} passed in
 * the clear. The only real path of a leak is a token: these go through the
 * security filter, the controllers and the repositories exactly as a client
 * would (docs/releases/0.2.0/r020-01-isolation-matrix.md, cases A-1 to A-9).
 */
@Import(PostgresContainerInitializer.class)
class AccountIsolationIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository users;
    @Autowired private SourceRepository sources;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionService sessions;
    @Autowired private UserdataService userdata;
    @Autowired private JdbcClient jdbc;

    private MultiSourceAccountsFixture.Dataset data;

    @BeforeEach
    void seed() {
        data = new MultiSourceAccountsFixture(users, sources, devices, sessions, userdata, jdbc).seed();
    }

    @Test
    @DisplayName("A-1 : le jeton de A lit les données de A")
    void tokenOfAReadsA() {
        String a = data.a().accessToken();

        assertThat(get("/v1/me", a).body()).contains(data.a().user().email());
        assertThat(get("/v1/me/favorites", a).body()).contains(data.channelsA1().get(0).toString());
        assertThat(get("/v1/me/recent-channels", a).body()).contains(data.channelsA1().get(0).toString());
        assertThat(get("/v1/sources", a).body())
                .contains(data.sourceA1().toString(), data.sourceA2().toString())
                .doesNotContain(data.sourceB1().toString());
    }

    @Test
    @DisplayName("A-2 : le jeton de B ne voit rien de A sur les mêmes routes")
    void tokenOfBSeesNothingOfA() {
        String b = data.b().accessToken();

        assertThat(get("/v1/me", b).body())
                .contains(data.b().user().email())
                .doesNotContain(data.a().user().email());
        for (String path : new String[] {"/v1/me/favorites", "/v1/me/recent-channels",
                "/v1/me/favorite-groups", "/v1/sources", "/v1/me/devices"}) {
            Response response = get(path, b);
            assertThat(response.status()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path)
                    .doesNotContain(data.sourceA1().toString())
                    .doesNotContain(data.sourceA2().toString())
                    .doesNotContain(data.channelsA1().get(0).toString())
                    .doesNotContain(data.namedGroupId().toString())
                    .doesNotContain(data.a().deviceId().toString());
        }
    }

    @Test
    @DisplayName("A-3/A-4 : une source de A est introuvable avec le jeton de B (404, pas 403)")
    void sourceOfAIsNotFoundForB() {
        String b = data.b().accessToken();
        String sourceA1 = data.sourceA1().toString();

        for (String path : new String[] {"/v1/sources/" + sourceA1, "/v1/sources/" + sourceA1 + "/channels",
                "/v1/sources/" + sourceA1 + "/categories"}) {
            Response response = get(path, b);
            assertThat(response.status()).as(path).isEqualTo(404);
            assertThat(response.body()).as(path).doesNotContain("A1 Chaîne");
        }
        assertThat(get("/v1/channels/" + data.channelsA1().get(0) + "/playback", b).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("A-5 : B ne peut ni déplacer ni retirer un favori de A")
    void favouriteOfAIsUntouchableForB() {
        String b = data.b().accessToken();
        UUID favoriteOfA = data.interleaved().getFirst().getId();
        String before = get("/v1/me/favorites", data.a().accessToken()).body();

        Response patch = send(HttpMethod.PATCH, "/v1/me/favorites/" + favoriteOfA, b, "{\"position\":0}");
        Response delete = send(HttpMethod.DELETE, "/v1/me/favorites/" + favoriteOfA, b, null);

        assertThat(patch.status()).isEqualTo(404);
        assertThat(delete.status()).isEqualTo(404);
        assertThat(get("/v1/me/favorites", data.a().accessToken()).body()).isEqualTo(before);
    }

    @Test
    @DisplayName("A-6 : le refresh token de A présenté avec le jeton de B ne donne pas la session de A")
    void refreshTokenOfAGivesBNothing() {
        // /auth/refresh is anonymous: the refresh token is the credential. What
        // must hold is that presenting A's token, from wherever, never yields a
        // session for anybody but A — and never one that B's access token turns
        // into A's data.
        Response refreshed = send(HttpMethod.POST, "/v1/auth/refresh", data.b().accessToken(),
                "{\"refresh_token\":\"" + data.a().refreshToken() + "\"}");

        // Bound to A, as the token always was: B's bearer changes nothing.
        assertThat(refreshed.status()).isEqualTo(200);
        String newAccess = extract(refreshed.body(), "access_token");
        assertThat(get("/v1/me", newAccess).body()).contains(data.a().user().email());
        assertThat(get("/v1/me", data.b().accessToken()).body()).doesNotContain(data.a().user().email());
    }

    @Test
    @DisplayName("A-7 : la déconnexion de A révoque la chaîne de A, B reste intact")
    void logoutOfARevokesAOnly() {
        Response logout = send(HttpMethod.POST, "/v1/auth/logout", data.a().accessToken(),
                "{\"refresh_token\":\"" + data.a().refreshToken() + "\"}");
        assertThat(logout.status()).isEqualTo(204);

        Response refreshA = send(HttpMethod.POST, "/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + data.a().refreshToken() + "\"}");
        assertThat(refreshA.status()).isEqualTo(401);

        assertThat(get("/v1/me", data.b().accessToken()).status()).isEqualTo(200);
        assertThat(send(HttpMethod.POST, "/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + data.b().refreshToken() + "\"}").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("A-7 bis : B ne peut pas déconnecter A avec le refresh token de A")
    void bCannotSignAOut() {
        Response logout = send(HttpMethod.POST, "/v1/auth/logout", data.b().accessToken(),
                "{\"refresh_token\":\"" + data.a().refreshToken() + "\"}");

        assertThat(logout.status()).isEqualTo(401);
        assertThat(send(HttpMethod.POST, "/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + data.a().refreshToken() + "\"}").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("A-8 : rejouer un refresh de A déjà consommé révoque A, pas B")
    void replayRevokesAOnly() {
        String body = "{\"refresh_token\":\"" + data.a().refreshToken() + "\"}";
        Response first = send(HttpMethod.POST, "/v1/auth/refresh", null, body);
        assertThat(first.status()).isEqualTo(200);
        String rotatedA = extract(first.body(), "refresh_token");

        Response replay = send(HttpMethod.POST, "/v1/auth/refresh", null, body);
        assertThat(replay.status()).isEqualTo(401);
        assertThat(replay.body()).contains("REFRESH_TOKEN_REUSED");

        // The whole chain of A's device is gone, the rotated token included…
        assertThat(send(HttpMethod.POST, "/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + rotatedA + "\"}").status()).isEqualTo(401);
        // …and B never noticed.
        assertThat(send(HttpMethod.POST, "/v1/auth/refresh", null,
                "{\"refresh_token\":\"" + data.b().refreshToken() + "\"}").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("A-9 : B ne liste ni ne retire l'appareil de A")
    void devicesAreIsolated() {
        String b = data.b().accessToken();

        assertThat(get("/v1/me/devices", b).body()).doesNotContain(data.a().deviceId().toString());
        assertThat(send(HttpMethod.DELETE, "/v1/me/devices/" + data.a().deviceId(), b, null).status())
                .isEqualTo(404);
        assertThat(get("/v1/me/devices", data.a().accessToken()).body()).contains(data.a().deviceId().toString());
    }

    // ---- helpers ------------------------------------------------------------

    private record Response(int status, String body) {
    }

    private Response get(String path, String token) {
        return send(HttpMethod.GET, path, token, null);
    }

    private Response send(HttpMethod method, String path, String token, String json) {
        RestClient.RequestBodySpec spec = RestClient.create("http://localhost:" + port)
                .method(method).uri(path);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return spec.exchange((request, response) -> new Response(
                response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String extract(String json, String field) {
        int start = json.indexOf("\"" + field + "\":\"") + field.length() + 4;
        return json.substring(start, json.indexOf('"', start));
    }
}
