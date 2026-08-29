package tv.lumo.api.ingest.xtream;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tv.lumo.api.ingest.HostConcurrencyLimiter;
import tv.lumo.api.ingest.IngestionHttpClient;
import tv.lumo.api.shared.config.LumoProperties;

/**
 * The Xtream ingestion path, against a panel that behaves like a real one.
 *
 * <h2>Why this test did not exist, and what that cost</h2>
 *
 * Every ingestion test in this project was an M3U test. The Xtream path —
 * <b>the preferred format</b>, the one docs/domain-model.md recommends because
 * it carries categories, the guide and account information — had no coverage
 * beyond the parts a unit test of the parser reaches.
 *
 * <p>It was broken. {@code readArray} walked to the first {@code START_OBJECT}
 * and then asked the mapper to read a tree from the parser, which in this
 * Jackson version consumes to end-of-input instead of reading the current value:
 * every real panel answered {@code SOURCE_INVALID_FORMAT}, whatever it sent.
 * Nothing caught it until a user pointed a real subscription at it.
 *
 * <h2>The fixture is invented, and gzipped</h2>
 *
 * No real channel, no real bouquet, no real logo (AGENTS.md §1). Gzipped because
 * that is what panels actually send — the one under test sends
 * {@code Content-Encoding: gzip} on every call, including the small ones.
 */
class XtreamClientTest {

    private HttpServer server;
    private XtreamClient client;
    private String host;

    @BeforeEach
    void startPanel() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/player_api.php", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = query == null ? ACCOUNT
                    : query.contains("action=get_live_categories") ? CATEGORIES
                    : query.contains("action=get_live_streams") ? STREAMS
                    : query.contains("action=get_vod_categories") ? VOD_CATEGORIES
                    : query.contains("action=get_vod_streams") ? VOD_STREAMS
                    : query.contains("action=get_series_categories") ? SERIES_CATEGORIES
                    : query.contains("action=get_series_info") ? SERIES_INFO
                    : query.contains("action=get_series") ? SERIES
                    : ACCOUNT;
            respondGzipped(exchange, body);
        });
        server.start();

        host = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new XtreamClient(httpClient(), JsonMapper.builder().build());
    }

    @AfterEach
    void stopPanel() {
        server.stop(0);
    }

    @Test
    @DisplayName("les catégories d'un panel réel sont lues, une par une")
    void readsCategories() {
        List<XtreamClient.XtreamCategory> categories = new ArrayList<>();

        client.streamLiveCategories(host, "user", "secret", categories::add);

        // The regression in one assertion: this used to be zero, with the
        // ingestion reporting SOURCE_INVALID_FORMAT over a perfectly valid array.
        assertThat(categories).hasSize(3);
        assertThat(categories.getFirst().externalId()).isEqualTo("1");
        assertThat(categories.getFirst().name()).isEqualTo("Généralistes");
    }

    @Test
    @DisplayName("les chaînes sont lues avec leur numéro et leur qualité")
    void readsStreams() {
        List<XtreamClient.XtreamStream> streams = new ArrayList<>();

        client.streamLiveStreams(host, "user", "secret", streams::add);

        assertThat(streams).hasSize(2);

        XtreamClient.XtreamStream first = streams.getFirst();
        assertThat(first.name()).isEqualTo("Chaîne 01 FHD");
        // The panel's own number, not the order it happened to return rows in.
        assertThat(first.number()).isEqualTo(1);
        // Read off the name, echoed exactly as written.
        assertThat(first.quality()).isEqualTo("FHD");
        assertThat(first.adult()).isFalse();

        // A panel that reports neither reports neither: no zero, no invented badge.
        assertThat(streams.get(1).number()).isNull();
        assertThat(streams.get(1).quality()).isNull();
        assertThat(streams.get(1).adult()).isTrue();
    }

    @Test
    @DisplayName("l'authentification lit l'expiration et le nombre de connexions")
    void authenticates() {
        XtreamClient.XtreamAccount account = client.authenticate(host, "user", "secret");

        assertThat(account.maxConnections()).isEqualTo(2);
        assertThat(account.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("un film sans container_extension est ignoré, pas importé injouable")
    void skipsFilmsWithNoContainerExtension() {
        List<XtreamClient.XtreamVodStream> films = new ArrayList<>();

        client.streamVodStreams(host, "user", "secret", films::add);

        // Three entries in the fixture, one of them without an extension. Its
        // playback URL cannot be built at all — that fragment is the only part the
        // panel does not put in a path — so importing it would produce a catalogue
        // row that opens onto a failure. A catalogue of twelve thousand films of
        // which three hundred never start is worse than one of eleven thousand
        // seven hundred.
        assertThat(films).hasSize(2);
        assertThat(films).extracting(XtreamClient.XtreamVodStream::name)
                .containsExactly("Le Voyage", "La Traversée");
    }

    @Test
    @DisplayName("l'URL d'un film porte /movie/ et l'extension que le panel donne")
    void buildsTheFilmUrl() {
        List<XtreamClient.XtreamVodStream> films = new ArrayList<>();

        client.streamVodStreams(host, "user", "secret", films::add);

        // Not `/live/…/{id}.m3u8`: a different path segment *and* an extension the
        // panel chooses. Two differences, which is why this is a second method
        // rather than a boolean on the first.
        assertThat(films.getFirst().streamUrl())
                .isEqualTo(host + "/movie/user/secret/501.mkv");
        assertThat(films.get(1).streamUrl())
                .isEqualTo(host + "/movie/user/secret/502.mp4");
    }

    @Test
    @DisplayName("l'année, la durée et la note sont lues telles que les panels les envoient")
    void readsFilmMetadata() {
        List<XtreamClient.XtreamVodStream> films = new ArrayList<>();

        client.streamVodStreams(host, "user", "secret", films::add);

        XtreamClient.XtreamVodStream first = films.getFirst();
        assertThat(first.year()).isEqualTo(1998);
        // Xtream reports a run time in minutes; this layer stores seconds.
        assertThat(first.durationSeconds()).isEqualTo(94 * 60);
        // Echoed verbatim: deciding that `7.4` is a number and `PG-13` is not
        // would be this layer inventing a meaning the provider did not give.
        assertThat(first.rating()).isEqualTo("7.4");
        assertThat(first.posterUrl()).isEqualTo("https://poster.example/1.jpg");

        XtreamClient.XtreamVodStream second = films.get(1);
        // `N/A` is not a year, an empty run time is not a duration, and neither
        // becomes a zero.
        assertThat(second.year()).isNull();
        assertThat(second.durationSeconds()).isNull();
        // No `stream_icon`, but a `cover`: panels disagree on the key and often
        // serve both.
        assertThat(second.posterUrl()).isEqualTo("https://poster.example/2.jpg");
        assertThat(second.adult()).isTrue();
    }

    @Test
    @DisplayName("les catégories de films sont lues comme celles du direct")
    void readsFilmCategories() {
        List<XtreamClient.XtreamCategory> categories = new ArrayList<>();

        client.streamVodCategories(host, "user", "secret", categories::add);

        assertThat(categories).hasSize(2);
        assertThat(categories.getFirst().name()).isEqualTo("Action");
    }

    @Test
    @DisplayName("la liste des séries est plate : ni saison ni épisode")
    void readsSeriesList() {
        List<XtreamClient.XtreamSeries> series = new ArrayList<>();
        client.streamSeries(host, "user", "pass", series::add);

        assertThat(series).extracting(XtreamClient.XtreamSeries::name)
                .containsExactly("Les Falaises", "Le Phare");
        // `get_series` answers with the catalogue and no tree. Walking the trees
        // here would be one request per series at every synchronisation.
        assertThat(series.getFirst().episodeRunTimeMinutes()).isEqualTo(45);
        assertThat(series.getFirst().year()).isEqualTo(2019);
    }

    @Test
    @DisplayName("une année en `N/A` ou dans `releaseDate` est lue ou abandonnée sans casser")
    void toleratesTheYearKeysPanelsActuallySend() {
        List<XtreamClient.XtreamSeries> series = new ArrayList<>();
        client.streamSeries(host, "user", "pass", series::add);

        // The second has `year: "N/A"` and a usable `releaseDate`. A panel that
        // fills one, the other, or both with different values is ordinary.
        assertThat(series.get(1).year()).isEqualTo(2011);
    }

    @Test
    @DisplayName("l'arbre vient des épisodes, et une saison déclarée vide reste une saison")
    void readsTheTree() {
        List<XtreamClient.XtreamSeason> tree =
                client.fetchSeriesInfo(host, "user", "pass", "9001");

        assertThat(tree).extracting(XtreamClient.XtreamSeason::seasonNumber)
                .containsExactly(1, 2, 3);
        // Season 3 is declared in `seasons` and has no episode in `episodes`. A
        // viewer should see it as empty rather than not at all — dropping it
        // would be hiding something the panel said.
        assertThat(tree.get(2).episodes()).isEmpty();
        assertThat(tree.get(2).episodeCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("les trous de numérotation sont conservés tels quels")
    void keepsGapsInEpisodeNumbers() {
        List<XtreamClient.XtreamSeason> tree =
                client.fetchSeriesInfo(host, "user", "pass", "9001");

        // Episodes 1 and 3. Renumbering them to 1 and 2 would be inventing an
        // episode order the provider did not give, and it would break the
        // "next episode" of any client that trusted it.
        assertThat(tree.getFirst().episodes())
                .extracting(XtreamClient.XtreamEpisode::episodeNumber)
                .containsExactly(1, 3);
    }

    @Test
    @DisplayName("un épisode sans extension est écarté, comme un film sans extension")
    void dropsEpisodesWithNoContainerExtension() {
        List<XtreamClient.XtreamSeason> tree =
                client.fetchSeriesInfo(host, "user", "pass", "9001");

        // Season 2 has two entries and one of them cannot have a URL built. An
        // episode that opens onto a failure is worse than one that is absent.
        assertThat(tree.get(1).episodes())
                .extracting(XtreamClient.XtreamEpisode::externalId)
                .containsExactly("2001");
    }

    @Test
    @DisplayName("une saison absente de `seasons` est déduite des épisodes")
    void inventsNoSeasonButFindsThemAll() {
        List<XtreamClient.XtreamSeason> tree =
                client.fetchSeriesInfo(host, "user", "pass", "9001");

        // Season 2 is not in the `seasons` array — panels are inconsistent about
        // it — and it has episodes. `episodes` is what holds the content, so it
        // is what the tree is built from.
        assertThat(tree.get(1).seasonNumber()).isEqualTo(2);
        assertThat(tree.get(1).episodeCount()).isNull();
    }
    // ---- fixture ------------------------------------------------------------

    private static final String ACCOUNT = """
            {"user_info":{"username":"user","auth":1,"status":"Active",
             "exp_date":"1900000000","max_connections":"2","active_cons":"0"},
             "server_info":{"url":"127.0.0.1","port":"80"}}
            """;

    private static final String CATEGORIES = """
            [{"category_id":"1","category_name":"Généralistes","parent_id":0},
             {"category_id":"2","category_name":"Sport","parent_id":0},
             {"category_id":"3","category_name":"Découverte","parent_id":0}]
            """;

    /** `stream_id` as a number on one and a string on the other: panels do both. */
    private static final String STREAMS = """
            [{"num":1,"name":"Chaîne 01 FHD","stream_id":101,"stream_icon":"",
              "epg_channel_id":"c1","category_id":"1","is_adult":"0"},
             {"num":"","name":"Chaîne 02","stream_id":"102","stream_icon":null,
              "epg_channel_id":null,"category_id":"2","is_adult":"1"}]
            """;

    private static final String VOD_CATEGORIES = """
            [{"category_id":"10","category_name":"Action","parent_id":0},
             {"category_id":"11","category_name":"Documentaire","parent_id":0}]
            """;

    /**
     * Three films, and the third is the one that matters.
     *
     * <p>It has no {@code container_extension}, which happens on real panels and
     * makes the film unplayable: the URL cannot be built. The first two also cover
     * the two spellings of a poster key and the `N/A` a panel sends where a year
     * should be.
     */
    private static final String VOD_STREAMS = """
            [{"stream_id":501,"name":"Le Voyage","container_extension":"mkv",
              "stream_icon":"https://poster.example/1.jpg","category_id":"10",
              "year":"1998","episode_run_time":"94","rating":"7.4","is_adult":"0"},
             {"stream_id":"502","name":"La Traversée","container_extension":"mp4",
              "stream_icon":null,"cover":"https://poster.example/2.jpg","category_id":"11",
              "year":"N/A","episode_run_time":"","rating":"PG-13","is_adult":"1"},
             {"stream_id":"503","name":"Sans extension","stream_icon":null,
              "category_id":"10","year":"2001"}]
            """;

    private static final String SERIES_CATEGORIES = """
            [{"category_id":"20","category_name":"Drame","parent_id":0}]
            """;

    /**
     * Two series, and the second is the one that matters.
     *
     * <p>Its {@code year} is {@code N/A} and its {@code releaseDate} is usable.
     * Panels fill one, the other, or both with different values, and a reader
     * that only knew one key would lose a year on half a catalogue.
     */
    private static final String SERIES = """
            [{"series_id":9001,"name":"Les Falaises","cover":"https://poster.example/s.jpg",
              "category_id":"20","year":"2019","episode_run_time":"45","rating":"8.1",
              "plot":"Un synopsis."},
             {"series_id":"9002","name":"Le Phare","cover":null,
              "stream_icon":"https://poster.example/p.jpg","category_id":"20",
              "year":"N/A","releaseDate":"2011-09-04","episode_run_time":"","rating":""}]
            """;

    /**
     * One series sheet, carrying every shape this parser has to survive.
     *
     * <ul>
     *   <li>season 1: episodes 1 and 3 — <b>a gap</b>, kept as it is;
     *   <li>season 2: <b>absent from {@code seasons}</b>, present in
     *       {@code episodes}, with one entry that has no
     *       {@code container_extension} and is therefore unplayable;
     *   <li>season 3: <b>declared and empty</b> — a season a viewer should see
     *       rather than one that silently does not exist.
     * </ul>
     */
    private static final String SERIES_INFO = """
            {"info":{"name":"Les Falaises","plot":"Un synopsis."},
             "seasons":[{"season_number":1,"episode_count":"3","cover":null},
                        {"season_number":3,"episode_count":"8","cover":null}],
             "episodes":{
               "1":[{"id":"1001","episode_num":1,"title":"Le départ","season":1,
                     "container_extension":"mkv","info":{"duration_secs":"2700"}},
                    {"id":"1003","episode_num":"3","title":null,"season":1,
                     "container_extension":"mkv","info":{"duration_secs":""}}],
               "2":[{"id":"2001","episode_num":1,"title":"La côte",
                     "container_extension":"mp4","info":{"duration_secs":"2650"}},
                    {"id":"2002","episode_num":2,"title":"Sans extension",
                     "info":{}}]}}
            """;
    private static void respondGzipped(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = compressed.toByteArray();

        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Content-Encoding", "gzip");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * A client pointed at loopback, which {@code PrivateAddressGuard} refuses by
     * default and for good reason — so the test opens the one property that
     * exists to let it, and nothing else does.
     */
    private static IngestionHttpClient httpClient() {
        LumoProperties.Ingest ingest =
                new LumoProperties.Ingest(4, 50, Duration.ofSeconds(10), 200, true);
        LumoProperties properties = new LumoProperties(
                new LumoProperties.Jwt("x".repeat(40), Duration.ofMinutes(15), "https://api.lumo.tv"),
                new LumoProperties.Refresh(Duration.ofDays(30)),
                new LumoProperties.Encryption("dGVzdC1vbmx5LW1hc3Rlci1rZXktMzItYnl0ZXMhISE="),
                new LumoProperties.DeviceCode(Duration.ofMinutes(10), Duration.ofSeconds(5)),
                new LumoProperties.Web("http://localhost:3000"),
                new LumoProperties.Cors(List.of()),
                ingest,
                new LumoProperties.RateLimit(5, 5),
                new LumoProperties.AutoSync(false, Duration.ofHours(1), 12, 25),
                new LumoProperties.Plans(new LumoProperties.Limits(1, 2),
                        new LumoProperties.Limits(null, null)),
                new LumoProperties.Billing("", "", "https://api.stripe.com", 0, "/", "/", "/"));

        return new IngestionHttpClient(new HostConcurrencyLimiter(properties), properties);
    }
}
