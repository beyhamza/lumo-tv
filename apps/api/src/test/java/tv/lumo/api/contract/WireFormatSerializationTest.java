package tv.lumo.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.Channel;
import tv.lumo.api.generated.model.CreateSourceRequest;
import tv.lumo.api.generated.model.DeviceRegistration;
import tv.lumo.api.generated.model.DeviceTokenRequest;
import tv.lumo.api.generated.model.GoogleSignInRequest;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.RefreshRequest;
import tv.lumo.api.generated.model.ResetPasswordRequest;
import tv.lumo.api.generated.model.DeviceCodeResponse;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.Locale;
import tv.lumo.api.generated.model.PlaybackInfo;
import tv.lumo.api.generated.model.Problem;
import tv.lumo.api.generated.model.Source;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SourceStatus;
import tv.lumo.api.generated.model.User;

/**
 * Pins the JSON wire format against the contract.
 *
 * <p><b>Why this exists.</b> Spring Boot 4 ships Jackson 3, whose serialisation
 * behaviour differs from Jackson 2 in small ways that are easy to assume and
 * expensive to get wrong — three client applications are generated from the same
 * contract and will not forgive a renamed or missing property. These tests assert
 * what actually goes on the wire instead of trusting that it matches.
 *
 * <p>It uses the application's own configured {@link ObjectMapper}, not a
 * hand-built one, so it tests the mapper that really serves requests.
 *
 * <p>Two of these assertions are security invariants rather than formatting
 * checks: no password ever appears in a serialised {@code Source}, and
 * {@code stream_url} appears only in {@code PlaybackInfo}.
 */
@Import(PostgresContainerInitializer.class)
class WireFormatSerializationTest extends PostgresIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    // ---- property naming ----------------------------------------------------

    @Test
    @DisplayName("payload properties are snake_case, exactly as docs/domain-model.md §2 names them")
    void propertiesAreSnakeCase() {
        JsonNode json = objectMapper.valueToTree(readySource());

        // Not lastSyncedAt, not last-synced-at. The contract chose the domain
        // model's field names verbatim, "aucune variante de casse".
        assertThat(fieldNames(json)).contains(
                "id", "label", "kind", "host", "username", "m3u_url", "epg_url",
                "status", "error_code", "last_synced_at", "expires_at",
                "max_connections", "channel_count",
                // Required, so always on the wire — unlike sync_step and
                // category_count, which are absent on a READY source and are
                // deliberately not asserted here.
                "auto_sync");
    }

    @Test
    @DisplayName("the RFC 8628 device response keeps the RFC's property names verbatim")
    void deviceCodeResponseUsesRfcNames() {
        DeviceCodeResponse response = new DeviceCodeResponse(
                "secret-device-code", "K7RM4XPQ",
                java.net.URI.create("http://localhost:3000/activate"),
                java.net.URI.create("http://localhost:3000/activate?code=K7RM4XPQ"),
                600, 5);

        assertThat(fieldNames(objectMapper.valueToTree(response))).contains(
                "device_code", "user_code", "verification_uri",
                "verification_uri_complete", "expires_in", "interval");
    }

    @Test
    @DisplayName("a session serialises the token pair and the user together")
    void authSessionIsFlat() {
        AuthSession session = new AuthSession("jwt", AuthSession.TokenTypeEnum.BEARER,
                900, "opaque-refresh", user(), UUID.randomUUID());

        assertThat(fieldNames(objectMapper.valueToTree(session)))
                .contains("access_token", "token_type", "expires_in", "refresh_token",
                        "user", "device_id");
    }

    // ---- enum wire values ---------------------------------------------------

    @Test
    @DisplayName("enums serialise to their CONTRACT values, not their Java constant names")
    void enumsSerialiseToContractValues() {
        JsonNode json = objectMapper.valueToTree(m3uSource());

        // The constant and the wire value read the same today only because the
        // contract says so: x-enum-varnames on SourceKind is what stops
        // openapi-generator's camelizer turning M3U_URL into M3_U_URL. That is a
        // naming fix, and this is the guard that it stayed one - if a rename ever
        // reached serialisation, all three clients would break at once.
        assertThat(json.get("kind").stringValue()).isEqualTo("M3U_URL");
        assertThat(json.get("status").stringValue()).isEqualTo("PENDING");
        assertThat(objectMapper.valueToTree(SourceKind.M3U_FILE).stringValue()).isEqualTo("M3U_FILE");
        assertThat(objectMapper.valueToTree(Locale.FR).stringValue()).isEqualTo("fr");
        assertThat(objectMapper.valueToTree(ErrorCode.SOURCE_AUTH_FAILED).stringValue())
                .isEqualTo("SOURCE_AUTH_FAILED");
    }

    @Test
    @DisplayName("enums deserialise from their contract values")
    void enumsDeserialiseFromContractValues() {
        CreateSourceRequest request = objectMapper.readValue("""
                {"label":"Test","kind":"M3U_URL","m3u_url":"https://test.example/p.m3u"}
                """, CreateSourceRequest.class);

        assertThat(request.getKind()).isEqualTo(SourceKind.M3U_URL);
        assertThat(request.getM3uUrl()).isEqualTo("https://test.example/p.m3u");
    }

    // ---- security invariants on the wire ------------------------------------

    @Test
    @DisplayName("a serialised Source carries no password under any name")
    void serialisedSourceHasNoPassword() {
        String json = objectMapper.writeValueAsString(readySource());

        // The Xtream password must not appear in a response schema anywhere,
        // not even for its owner (docs/domain-model.md §2).
        assertThat(json.toLowerCase(java.util.Locale.ROOT))
                .doesNotContain("password")
                .doesNotContain("secret");
    }

    @Test
    @DisplayName("every writeOnly secret deserialises in but never serialises back out")
    void writeOnlySecretsNeverSerialiseOut() {
        // openapi-generator does NOT translate the contract's writeOnly into
        // @JsonProperty(access = WRITE_ONLY) — SecretSerializationConfig applies
        // it with mix-ins. This test is the guard on that: delete the config and
        // it fails.
        CreateSourceRequest source = new CreateSourceRequest("Test", SourceKind.XTREAM);
        source.setHost("http://panel.example.org:8080");
        source.setUsername("someone");
        source.setPassword("xtream-secret-value");

        RefreshRequest refresh = new RefreshRequest("refresh-secret-value");
        GoogleSignInRequest google =
                new GoogleSignInRequest("id-token-secret-value", new DeviceRegistration(Platform.WEB));
        DeviceTokenRequest deviceToken = new DeviceTokenRequest("device-code-secret-value");
        ResetPasswordRequest reset =
                new ResetPasswordRequest("reset-token-secret-value", "new-password-secret-value");

        assertThat(objectMapper.writeValueAsString(source)).doesNotContain("xtream-secret-value");
        assertThat(objectMapper.writeValueAsString(refresh)).doesNotContain("refresh-secret-value");
        assertThat(objectMapper.writeValueAsString(google)).doesNotContain("id-token-secret-value");
        assertThat(objectMapper.writeValueAsString(deviceToken)).doesNotContain("device-code-secret-value");
        assertThat(objectMapper.writeValueAsString(reset))
                .doesNotContain("reset-token-secret-value")
                .doesNotContain("new-password-secret-value");

        // Inbound is unaffected: WRITE_ONLY means accept in, never emit out.
        assertThat(objectMapper.readValue(
                "{\"refresh_token\":\"arriving-value\"}", RefreshRequest.class).getRefreshToken())
                .isEqualTo("arriving-value");
    }

    @Test
    @DisplayName("generated toString() masks secrets so they cannot reach a log line")
    void toStringMasksSecrets() {
        CreateSourceRequest request = new CreateSourceRequest("Test", SourceKind.XTREAM);
        request.setPassword("xtream-secret-value");

        // A second, independent barrier: even a stray log.debug("{}", request)
        // cannot leak it (AGENTS.md §5).
        assertThat(request.toString()).doesNotContain("xtream-secret-value").contains("password: *");
    }

    @Test
    @DisplayName("a serialised Channel carries no stream_url")
    void serialisedChannelHasNoStreamUrl() {
        Channel channel = new Channel(UUID.randomUUID(), UUID.randomUUID(), "Test Channel", 0, false);
        channel.setLogoUrl("https://test.example/logo.png");
        channel.setTvgId("test.1");

        // A list of a thousand channels does not carry a thousand stream URLs.
        assertThat(fieldNames(objectMapper.valueToTree(channel))).doesNotContain("stream_url");
    }

    @Test
    @DisplayName("PlaybackInfo is the one model that does carry stream_url")
    void playbackInfoCarriesStreamUrl() {
        PlaybackInfo playback = new PlaybackInfo(UUID.randomUUID(), "https://test.example/stream.m3u8");
        playback.setMaxConnections(2);

        JsonNode json = objectMapper.valueToTree(playback);
        assertThat(json.get("stream_url").stringValue()).isEqualTo("https://test.example/stream.m3u8");
        assertThat(json.get("channel_id")).isNotNull();
    }

    // ---- problem+json -------------------------------------------------------

    @Test
    @DisplayName("a Problem serialises with the stable code clients branch on")
    void problemCarriesStableCode() {
        Problem problem = new Problem(
                java.net.URI.create("https://lumo.tv/errors/source-auth-failed"),
                "Source auth failed", 422, ErrorCode.SOURCE_AUTH_FAILED);
        problem.setDetail("The panel rejected the credentials");
        problem.setInstance("/v1/sources");

        JsonNode json = objectMapper.valueToTree(problem);

        assertThat(json.get("code").stringValue()).isEqualTo("SOURCE_AUTH_FAILED");
        assertThat(json.get("status").intValue()).isEqualTo(422);
        assertThat(json.get("type").stringValue()).isEqualTo("https://lumo.tv/errors/source-auth-failed");
        assertThat(json.get("instance").stringValue()).isEqualTo("/v1/sources");
    }

    @Test
    @DisplayName("every ingestion error code round-trips through JSON")
    void everyIngestionCodeRoundTrips() {
        // The complete v1 enumeration. Each one reaches a user during onboarding
        // and each must survive the wire intact.
        for (IngestionErrorCode code : IngestionErrorCode.values()) {
            String json = objectMapper.writeValueAsString(code);
            assertThat(objectMapper.readValue(json, IngestionErrorCode.class)).isEqualTo(code);
            assertThat(json).isEqualTo("\"" + code.getValue() + "\"");
        }
        assertThat(IngestionErrorCode.values()).hasSize(7);
    }

    // ---- null and absence handling -----------------------------------------

    @Test
    @DisplayName("an unset nullable property serialises as null rather than vanishing")
    void nullablePropertiesSerialiseAsNull() {
        Source source = new Source(UUID.randomUUID(), "Playlist", SourceKind.M3U_URL, SourceStatus.PENDING, true);
        JsonNode json = objectMapper.valueToTree(source);

        // Pinned rather than assumed: openapi-typescript types these as `string |
        // null`, so a property that disappeared instead of being null would make
        // the web client's types a lie. Jackson 3 does not change this default,
        // and this test is what will notice if a future upgrade does.
        assertThat(json.has("error_code")).isTrue();
        assertThat(json.get("error_code").isNull()).isTrue();
        assertThat(json.get("channel_count").isNull()).isTrue();
    }

    @Test
    @DisplayName("timestamps serialise as ISO-8601 strings, not epoch numbers")
    void timestampsAreIso8601() {
        JsonNode json = objectMapper.valueToTree(user());

        // The contract declares format: date-time. Jackson's raw default for
        // java.time is epoch numerics, so this is exactly the kind of subtle
        // behaviour worth pinning rather than assuming.
        assertThat(json.get("created_at").isString()).isTrue();
        assertThat(json.get("created_at").stringValue()).contains("T");
    }

    @Test
    @DisplayName("an unknown property in a request body does not blow up deserialisation")
    void unknownPropertiesAreTolerated() {
        // A newer client sending a field this server does not know yet must not
        // get a 400: three applications ship on independent schedules.
        CreateSourceRequest request = objectMapper.readValue("""
                {"label":"Test","kind":"M3U_URL","m3u_url":"https://test.example/p.m3u",
                 "some_future_field":"ignored"}
                """, CreateSourceRequest.class);

        assertThat(request.getLabel()).isEqualTo("Test");
    }

    // ---- fixtures -----------------------------------------------------------

    private static List<String> fieldNames(JsonNode node) {
        return java.util.stream.StreamSupport
                .stream(java.util.Spliterators.spliteratorUnknownSize(node.propertyNames().iterator(), 0), false)
                .toList();
    }

    private static Source readySource() {
        Source source = new Source(UUID.randomUUID(), "My Panel", SourceKind.XTREAM, SourceStatus.READY, true);
        source.setHost("http://panel.example.org:8080");
        source.setUsername("someone");
        source.setEpgUrl("https://test.example/guide.xml.gz");
        source.setLastSyncedAt(OffsetDateTime.now());
        source.setExpiresAt(OffsetDateTime.now().plusDays(30));
        source.setMaxConnections(2);
        source.setChannelCount(1234);
        return source;
    }

    private static Source m3uSource() {
        Source source = new Source(UUID.randomUUID(), "Playlist", SourceKind.M3U_URL, SourceStatus.PENDING, true);
        source.setM3uUrl("https://test.example/playlist.m3u");
        return source;
    }

    private static User user() {
        User user = new User(UUID.randomUUID(), "someone@test.example", Locale.FR,
                OffsetDateTime.now(), OffsetDateTime.now());
        user.setDisplayName("Someone");
        return user;
    }
}
