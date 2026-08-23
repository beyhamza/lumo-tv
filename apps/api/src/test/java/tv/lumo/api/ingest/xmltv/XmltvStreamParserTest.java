package tv.lumo.api.ingest.xmltv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.ingest.IngestionException;

/**
 * XMLTV parsing. Domain logic, so it comes with its tests (AGENTS.md §5).
 *
 * <p>Every fixture is synthetic. No real channel, provider or guide URL appears
 * in this repository, test fixtures included (AGENTS.md §1).
 */
class XmltvStreamParserTest {

    private static final DateTimeFormatter XMLTV = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z");

    private final XmltvStreamParser parser = new XmltvStreamParser();

    @Test
    @DisplayName("reads channel, times, title, description and category")
    void parsesAProgramme() {
        List<XmltvStreamParser.ParsedProgramme> programmes = parse(guide("""
                <programme start="%s" stop="%s" channel="test.1">
                  <title>Test Programme</title>
                  <desc>A synthetic description.</desc>
                  <category>Documentary</category>
                </programme>
                """.formatted(inHours(1), inHours(2))));

        assertThat(programmes).singleElement().satisfies(programme -> {
            assertThat(programme.channelId()).isEqualTo("test.1");
            assertThat(programme.title()).isEqualTo("Test Programme");
            assertThat(programme.description()).isEqualTo("A synthetic description.");
            assertThat(programme.category()).isEqualTo("Documentary");
            assertThat(programme.endsAt()).isAfter(programme.startsAt());
        });
    }

    @Test
    @DisplayName("keeps the first title when a guide repeats it per language")
    void firstTitleWins() {
        List<XmltvStreamParser.ParsedProgramme> programmes = parse(guide("""
                <programme start="%s" stop="%s" channel="test.1">
                  <title lang="en">English Title</title>
                  <title lang="fr">Titre alternatif</title>
                </programme>
                """.formatted(inHours(1), inHours(2))));

        assertThat(programmes).singleElement()
                .extracting(XmltvStreamParser.ParsedProgramme::title)
                .isEqualTo("English Title");
    }

    @Test
    @DisplayName("drops programmes outside the D-1 / D+3 retention window")
    void appliesRetentionWindow() {
        List<XmltvStreamParser.ParsedProgramme> programmes = parse(guide("""
                <programme start="%s" stop="%s" channel="test.1">
                  <title>Ancient History</title>
                </programme>
                <programme start="%s" stop="%s" channel="test.1">
                  <title>Far Future</title>
                </programme>
                <programme start="%s" stop="%s" channel="test.1">
                  <title>Inside Window</title>
                </programme>
                """.formatted(inHours(-96), inHours(-95),
                              inHours(240), inHours(241),
                              inHours(1), inHours(2))));

        // Filtered at parse time: the cheapest row is the one never written.
        assertThat(programmes).singleElement()
                .extracting(XmltvStreamParser.ParsedProgramme::title)
                .isEqualTo("Inside Window");
    }

    @Test
    @DisplayName("skips an entry with no title, no channel or an inverted range")
    void skipsUnusableEntries() {
        List<XmltvStreamParser.ParsedProgramme> programmes = parse(guide("""
                <programme start="%s" stop="%s" channel="test.1"></programme>
                <programme start="%s" stop="%s" channel=""><title>No Channel</title></programme>
                <programme start="%s" stop="%s" channel="test.1"><title>Inverted</title></programme>
                <programme start="%s" stop="%s" channel="test.1"><title>Good One</title></programme>
                """.formatted(inHours(1), inHours(2),
                              inHours(1), inHours(2),
                              inHours(3), inHours(2),
                              inHours(1), inHours(2))));

        // One bad entry in a guide of half a million must not cost the user
        // their whole EPG.
        assertThat(programmes).singleElement()
                .extracting(XmltvStreamParser.ParsedProgramme::title)
                .isEqualTo("Good One");
    }

    @Test
    @DisplayName("rejects an external entity instead of resolving it")
    void isHardenedAgainstXxe() {
        // The guide URL is one the user pointed us at, so it is hostile until
        // proven otherwise. Resolving this would read a file off this server.
        String malicious = """
                <?xml version="1.0"?>
                <!DOCTYPE tv [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <tv><programme start="%s" stop="%s" channel="test.1">
                  <title>&xxe;</title>
                </programme></tv>
                """.formatted(inHours(1), inHours(2));

        assertThatThrownBy(() -> parse(malicious)).isInstanceOf(IngestionException.class);
    }

    @Test
    @DisplayName("content that is not XML at all is SOURCE_INVALID_FORMAT")
    void rejectsNonXml() {
        assertThatThrownBy(() -> parse("this is not a guide"))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).code())
                .isEqualTo(IngestionErrorCode.SOURCE_INVALID_FORMAT);
    }

    @Test
    @DisplayName("emits each programme as it is read rather than collecting them")
    void streamsRatherThanBuffers() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            body.append("<programme start=\"").append(inHours(1))
                    .append("\" stop=\"").append(inHours(2))
                    .append("\" channel=\"test.").append(i).append("\">")
                    .append("<title>Programme ").append(i).append("</title></programme>\n");
        }

        List<Integer> sizeAtEachCallback = new ArrayList<>();
        List<XmltvStreamParser.ParsedProgramme> seen = new ArrayList<>();
        int count = parser.parse(stream(guide(body.toString())), programme -> {
            seen.add(programme);
            sizeAtEachCallback.add(seen.size());
        });

        assertThat(count).isEqualTo(300);
        // The consumer fired once per programme during the parse, which is what
        // "StaX pull parsing, never DOM" means in practice.
        assertThat(sizeAtEachCallback).hasSize(300).first().isEqualTo(1);
    }

    private List<XmltvStreamParser.ParsedProgramme> parse(String xml) {
        List<XmltvStreamParser.ParsedProgramme> programmes = new ArrayList<>();
        parser.parse(stream(xml), programmes::add);
        return programmes;
    }

    private static String guide(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n" + body + "\n</tv>";
    }

    private static String inHours(int hours) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(hours).format(XMLTV);
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
