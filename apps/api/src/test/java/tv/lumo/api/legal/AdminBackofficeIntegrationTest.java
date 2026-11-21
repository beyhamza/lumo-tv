package tv.lumo.api.legal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.PasswordHasher;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * The administration surface (S16B-08, ADR 0015), over HTTP with real tokens:
 * what matters is who gets through the security filter, and what the public sees.
 */
@Import(PostgresContainerInitializer.class)
class AdminBackofficeIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct horse battery";
    private static final String VERSIONS = "/v1/admin/legal/versions";
    private static final String COMPLETE = """
            {"title_fr":"Titre","title_en":"Title","body_fr":"Texte %1$s","body_en":"Text %1$s"}
            """;

    @LocalServerPort
    private int port;

    @Autowired private JdbcClient jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionService sessions;
    @Autowired private PasswordHasher passwords;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void accounts() {
        jdbc.sql("DELETE FROM legal_document_version").update();
        jdbc.sql("UPDATE app_settings SET support_email = NULL, registration_open = true").update();

        UserRow admin = account();
        jdbc.sql("UPDATE \"user\" SET is_admin = true WHERE id = :id")
                .param("id", admin.id())
                .update();
        adminToken = token(admin);
        userToken = token(account());
    }

    @Test
    @DisplayName("sans jeton 401, compte ordinaire 403, administrateur 200, sur chaque route /admin")
    void adminRoutesAreClosed() {
        for (String path : new String[] {VERSIONS, "/v1/admin/settings"}) {
            assertThat(send(HttpMethod.GET, path, null, null).status()).as(path).isEqualTo(401);

            Response refused = send(HttpMethod.GET, path, userToken, null);
            assertThat(refused.status()).as(path).isEqualTo(403);
            assertThat(refused.body()).as(path).contains("\"FORBIDDEN\"");

            assertThat(send(HttpMethod.GET, path, adminToken, null).status()).as(path).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("un compte ordinaire ne crée ni ne publie rien")
    void ordinaryAccountCannotWrite() {
        assertThat(send(HttpMethod.POST, VERSIONS, userToken, draft("PRIVACY_POLICY", "x")).status()).isEqualTo(403);
        assertThat(send(HttpMethod.POST, VERSIONS + "/" + UUID.randomUUID() + "/publish", userToken, null).status())
                .isEqualTo(403);
        assertThat(send(HttpMethod.PUT, "/v1/admin/settings", userToken,
                "{\"support_email\":null,\"registration_open\":false}").status()).isEqualTo(403);
        assertThat(jdbc.sql("SELECT count(*) FROM legal_document_version").query(Integer.class).single()).isZero();
    }

    @Test
    @DisplayName("retirer le droit vaut dès l'appel suivant, avec le même jeton")
    void revokingTakesEffectAtOnce() {
        assertThat(send(HttpMethod.GET, VERSIONS, adminToken, null).status()).isEqualTo(200);

        jdbc.sql("UPDATE \"user\" SET is_admin = false").update();

        assertThat(send(HttpMethod.GET, VERSIONS, adminToken, null).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("/me annonce le droit, et PATCH /me ne l'accorde pas")
    void meReportsTheFlag() throws Exception {
        assertThat(body(send(HttpMethod.GET, "/v1/me", adminToken, null)).get("is_admin").asBoolean()).isTrue();
        assertThat(body(send(HttpMethod.GET, "/v1/me", userToken, null)).get("is_admin").asBoolean()).isFalse();

        send(HttpMethod.PATCH, "/v1/me", userToken, "{\"is_admin\":true}");

        assertThat(body(send(HttpMethod.GET, "/v1/me", userToken, null)).get("is_admin").asBoolean()).isFalse();
        assertThat(send(HttpMethod.GET, VERSIONS, userToken, null).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("un brouillon reste invisible du public jusqu'à sa publication")
    void draftIsNotPublic() throws Exception {
        String id = createDraft("PRIVACY_POLICY", "secret");

        Response before = send(HttpMethod.GET, "/v1/legal/PRIVACY_POLICY", null, null);
        assertThat(before.status()).isEqualTo(404);
        assertThat(before.body()).contains("LEGAL_DOCUMENT_NOT_PUBLISHED").doesNotContain("secret");

        assertThat(send(HttpMethod.POST, VERSIONS + "/" + id + "/publish", adminToken, null).status()).isEqualTo(200);

        JsonNode published = body(send(HttpMethod.GET, "/v1/legal/PRIVACY_POLICY", null, null));
        assertThat(published.get("version").asInt()).isEqualTo(1);
        assertThat(published.get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(published.get("body_fr").asString()).contains("secret");
        assertThat(published.get("published_at").isNull()).isFalse();
    }

    @Test
    @DisplayName("publier archive la version précédente et numérote sans trou")
    void publishingArchivesThePrevious() throws Exception {
        String first = createDraft("TERMS_OF_USE", "un");
        send(HttpMethod.POST, VERSIONS + "/" + first + "/publish", adminToken, null);

        String discarded = createDraft("TERMS_OF_USE", "jeté");
        assertThat(send(HttpMethod.DELETE, VERSIONS + "/" + discarded, adminToken, null).status()).isEqualTo(204);

        String second = createDraft("TERMS_OF_USE", "deux");
        send(HttpMethod.POST, VERSIONS + "/" + second + "/publish", adminToken, null);

        JsonNode current = body(send(HttpMethod.GET, "/v1/legal/TERMS_OF_USE", null, null));
        assertThat(current.get("version").asInt()).isEqualTo(2);
        assertThat(current.get("body_en").asString()).contains("deux");

        JsonNode archived = body(send(HttpMethod.GET, VERSIONS + "/" + first, adminToken, null));
        assertThat(archived.get("status").asString()).isEqualTo("ARCHIVED");
        assertThat(archived.get("version").asInt()).isEqualTo(1);
        assertThat(archived.get("body_fr").asString()).contains("un");
    }

    @Test
    @DisplayName("une version publiée ne change plus et ne se supprime pas")
    void publishedIsImmutable() throws Exception {
        String id = createDraft("PRIVACY_POLICY", "original");
        send(HttpMethod.POST, VERSIONS + "/" + id + "/publish", adminToken, null);
        String path = VERSIONS + "/" + id;

        for (Response refused : new Response[] {
                send(HttpMethod.PUT, path, adminToken, COMPLETE.formatted("modifié")),
                send(HttpMethod.DELETE, path, adminToken, null),
                send(HttpMethod.POST, path + "/publish", adminToken, null)}) {
            assertThat(refused.status()).isEqualTo(409);
            assertThat(refused.body()).contains("LEGAL_VERSION_NOT_DRAFT");
        }
        assertThat(body(send(HttpMethod.GET, path, adminToken, null)).get("body_fr").asString()).contains("original");
    }

    @Test
    @DisplayName("un seul brouillon par document ; un autre document reste libre")
    void oneDraftPerDocument() {
        createDraft("PRIVACY_POLICY", "un");

        Response second = send(HttpMethod.POST, VERSIONS, adminToken, draft("PRIVACY_POLICY", "deux"));
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body()).contains("LEGAL_DRAFT_ALREADY_EXISTS");

        assertThat(send(HttpMethod.POST, VERSIONS, adminToken, draft("TERMS_OF_SALE", "un")).status()).isEqualTo(201);
    }

    @Test
    @DisplayName("un brouillon incomplet s'enregistre mais ne se publie pas")
    void incompleteDraftCannotBePublished() throws Exception {
        Response created = send(HttpMethod.POST, VERSIONS, adminToken, """
                {"document_type":"PRIVACY_POLICY","title_fr":"Titre","title_en":"","body_fr":"Texte","body_en":"  "}
                """);
        assertThat(created.status()).isEqualTo(201);

        Response refused = send(HttpMethod.POST,
                VERSIONS + "/" + body(created).get("id").asString() + "/publish", adminToken, null);

        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body()).contains("LEGAL_VERSION_INCOMPLETE");
        assertThat(send(HttpMethod.GET, "/v1/legal/PRIVACY_POLICY", null, null).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("un identifiant inconnu répond 404, un type inconnu 400")
    void unknownTargets() {
        String unknown = VERSIONS + "/" + UUID.randomUUID();
        for (Response response : new Response[] {
                send(HttpMethod.GET, unknown, adminToken, null),
                send(HttpMethod.PUT, unknown, adminToken, COMPLETE.formatted("x")),
                send(HttpMethod.DELETE, unknown, adminToken, null),
                send(HttpMethod.POST, unknown + "/publish", adminToken, null)}) {
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body()).contains("LEGAL_VERSION_NOT_FOUND");
        }
        assertThat(send(HttpMethod.GET, "/v1/legal/NOPE", null, null).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("l'adresse de contact est publique, le reste de la configuration non")
    void publicConfigIsNarrow() throws Exception {
        Response updated = send(HttpMethod.PUT, "/v1/admin/settings", adminToken,
                "{\"support_email\":\"support@test.example\",\"registration_open\":true}");
        assertThat(updated.status()).isEqualTo(200);

        Response config = send(HttpMethod.GET, "/v1/config", null, null);
        assertThat(config.status()).isEqualTo(200);
        assertThat(body(config).get("support_email").asString()).isEqualTo("support@test.example");
        assertThat(config.body()).doesNotContain("registration_open");

        assertThat(send(HttpMethod.PUT, "/v1/admin/settings", adminToken,
                "{\"support_email\":\"pas-une-adresse\",\"registration_open\":true}").status()).isEqualTo(400);
    }

    @Test
    @DisplayName("inscriptions fermées : 403 à l'inscription, la connexion continue")
    void closingRegistration() {
        String existing = account().email();

        assertThat(send(HttpMethod.PUT, "/v1/admin/settings", adminToken,
                "{\"support_email\":null,\"registration_open\":false}").status()).isEqualTo(200);

        Response refused = send(HttpMethod.POST, "/v1/auth/register", null, registration(uniqueEmail()));
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.body()).contains("REGISTRATION_CLOSED");

        assertThat(send(HttpMethod.POST, "/v1/auth/login", null, """
                {"email":"%s","password":"%s","device":{"platform":"WEB"}}
                """.formatted(existing, PASSWORD)).status()).isEqualTo(200);

        send(HttpMethod.PUT, "/v1/admin/settings", adminToken,
                "{\"support_email\":null,\"registration_open\":true}");
        assertThat(send(HttpMethod.POST, "/v1/auth/register", null, registration(uniqueEmail())).status())
                .isEqualTo(201);
    }

    // ---- helpers ------------------------------------------------------------

    private String createDraft(String type, String marker) {
        Response created = send(HttpMethod.POST, VERSIONS, adminToken, draft(type, marker));
        assertThat(created.status()).isEqualTo(201);
        return body(created).get("id").asString();
    }

    private static String draft(String type, String marker) {
        return """
                {"document_type":"%s","title_fr":"Titre","title_en":"Title","body_fr":"Texte %s","body_en":"Text %s"}
                """.formatted(type, marker, marker);
    }

    private static String uniqueEmail() {
        return "admin-" + UUID.randomUUID() + "@test.example";
    }

    private static String registration(String email) {
        return """
                {"email":"%s","password":"%s","device":{"platform":"WEB"}}
                """.formatted(email, PASSWORD);
    }

    /** Through the repositories: {@code POST /auth/register} allows five calls a minute per address. */
    private UserRow account() {
        return users.insert(UUID.randomUUID(), uniqueEmail(), passwords.hash(PASSWORD), null, "fr");
    }

    private String token(UserRow user) {
        UUID deviceId = devices.insert(user.id(), Platform.WEB.getValue(), null, null, null);
        return sessions.openSession(user, deviceId).getAccessToken();
    }

    private JsonNode body(Response response) {
        return json.readTree(response.body());
    }

    private record Response(int status, String body) {
    }

    private Response send(HttpMethod method, String path, String token, String payload) {
        RestClient.RequestBodySpec spec = RestClient.create("http://localhost:" + port)
                .method(method).uri(path);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        if (payload != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(payload);
        }
        return spec.exchange((request, response) -> new Response(
                response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }
}
