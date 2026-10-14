package tv.lumo.api.support;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.AddFavoriteRequest;
import tv.lumo.api.generated.model.AuthSession;
import tv.lumo.api.generated.model.CreateFavoriteGroupRequest;
import tv.lumo.api.generated.model.Favorite;
import tv.lumo.api.generated.model.Platform;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.userdata.UserdataService;

/**
 * The shared two-account, multi-source fixture for S11 and {@code R020-01}.
 *
 * <p>Contract agreed with {@code @QA} on 2026-10-08
 * ({@code docs/releases/0.2.0/r020-01-isolation-matrix.md} §2). It is a plain
 * object rather than a Spring bean on purpose: a test constructs it with the
 * repositories it already autowires, so the fixture can be reused by a unit test,
 * an integration test and QA's HTTP isolation suite without a second context.
 *
 * <p>What it lays down: two independent accounts (A, B); two sources under A —
 * {@code A1} carrying the channels that form the interleaved group and {@code A2}
 * the other source — and one source under B; a named group whose favourites
 * alternate sources ({@code A1, B1, A2, B2, A3} in FO-02's shape); a default group;
 * a watched channel for history; an {@code ANDROID_TV} device per account; and the
 * access/refresh token pair minted for each device.
 *
 * <p>It deliberately stops at the API seam. The device activation flow, the
 * {@code pm clear} replay and the write counter belong to the QA extension, not
 * here.
 */
public final class MultiSourceAccountsFixture {

    /** An account with the device and tokens a client would carry. */
    public record Account(UserRow user, UUID deviceId, String accessToken, String refreshToken) {
    }

    /** Everything a test needs to talk about the two accounts. */
    public record Dataset(Account a,
                          Account b,
                          UUID sourceA1,
                          UUID sourceA2,
                          UUID sourceB1,
                          List<UUID> channelsA1,
                          List<UUID> channelsA2,
                          UUID channelB1,
                          UUID defaultGroupId,
                          UUID namedGroupId,
                          List<Favorite> interleaved) {

        /** The active-source predicate the visible-order helpers expect. */
        public boolean belongsToA1(Favorite favorite) {
            return sourceA1.equals(favorite.getSourceId());
        }
    }

    private final UserRepository users;
    private final SourceRepository sources;
    private final DeviceRepository devices;
    private final SessionService sessions;
    private final UserdataService userdata;
    private final JdbcClient jdbc;

    public MultiSourceAccountsFixture(UserRepository users,
                                      SourceRepository sources,
                                      DeviceRepository devices,
                                      SessionService sessions,
                                      UserdataService userdata,
                                      JdbcClient jdbc) {
        this.users = users;
        this.sources = sources;
        this.devices = devices;
        this.sessions = sessions;
        this.userdata = userdata;
        this.jdbc = jdbc;
    }

    /** Creates the accounts, sources, catalogue, favourites and tokens. */
    public Dataset seed() {
        Account a = account("a");
        Account b = account("b");

        UUID sourceA1 = source(a.user().id(), "Source A1 — guide + VOD");
        UUID sourceA2 = source(a.user().id(), "Source A2");
        UUID sourceB1 = source(b.user().id(), "Source B1");

        List<UUID> channelsA1 = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            channelsA1.add(channel(sourceA1, "A1 Chaîne %02d".formatted(i)));
        }
        List<UUID> channelsA2 = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            channelsA2.add(channel(sourceA2, "A2 Chaîne %02d".formatted(i)));
        }
        UUID channelB1 = channel(sourceB1, "B1 Chaîne 01");

        // A watched channel, so history is part of the surface that must not leak.
        userdata.recordRecentChannel(a.user().id(), channelsA1.get(0));

        UUID defaultGroupId = userdata
                .addFavorite(a.user().id(), new AddFavoriteRequest(channelsA1.get(0)))
                .getGroupId();

        // FO-02's shape: two sources alternating inside one group.
        UUID namedGroupId = userdata.createGroup(a.user().id(),
                new CreateFavoriteGroupRequest("Entrelacé")).getId();
        add(namedGroupId, a.user().id(), channelsA1.get(1));
        add(namedGroupId, a.user().id(), channelsA2.get(0));
        add(namedGroupId, a.user().id(), channelsA1.get(2));
        add(namedGroupId, a.user().id(), channelsA2.get(1));
        add(namedGroupId, a.user().id(), channelsA1.get(3));

        List<Favorite> interleaved = userdata.listFavorites(a.user().id(), namedGroupId);

        return new Dataset(a, b, sourceA1, sourceA2, sourceB1, channelsA1, channelsA2,
                channelB1, defaultGroupId, namedGroupId, interleaved);
    }

    // ---- helpers ------------------------------------------------------------

    private Account account(String label) {
        UserRow user = users.insert(UUID.randomUUID(),
                "multisource-%s-%s@test.example".formatted(label, UUID.randomUUID()),
                "$argon2id$irrelevant", "Multi-source " + label, "en");
        UUID deviceId = devices.insert(user.id(), Platform.ANDROID_TV.getValue(),
                "TV " + label, "Test Model", "1.0.0");
        AuthSession session = sessions.openSession(user, deviceId);
        return new Account(user, deviceId, session.getAccessToken(), session.getRefreshToken());
    }

    private UUID source(UUID userId, String label) {
        UUID id = UUID.randomUUID();
        sources.insert(id, userId, label, SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/%s.m3u".formatted(id), null, null, null);
        return id;
    }

    private void add(UUID groupId, UUID userId, UUID channelId) {
        userdata.addFavorite(userId, new AddFavoriteRequest(channelId).groupId(groupId));
    }

    /**
     * Inserts a channel directly: ingestion would mean an outbound HTTP request to
     * somebody's playlist, and what these tests exercise starts once the channel
     * exists.
     */
    private UUID channel(UUID sourceId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, external_id, name, stream_url, position)
                VALUES (:id, :sourceId, :externalId, :name, 'https://stream.example/x.m3u8', 0)
                """)
                .param("id", id)
                .param("sourceId", sourceId)
                .param("externalId", "fixture:" + id)
                .param("name", name)
                .update();
        return id;
    }
}
