package tv.lumo.api.ingest.m3u;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.ingest.IngestionException;

/**
 * M3U parsing. Domain logic, so it comes with its tests (AGENTS.md §5).
 *
 * <p>Every fixture here is synthetic and every URL points at a rights-free test
 * asset. No real channel, logo or provider URL appears in this repository, test
 * fixtures included (AGENTS.md §1).
 */
class M3uStreamParserTest {

    private static final String BBB = "https://test.example/big-buck-bunny.m3u8";

    private final M3uStreamParser parser = new M3uStreamParser();

    @Test
    @DisplayName("reads name, attributes and URL from a well-formed entry")
    void parsesAWellFormedEntry() {
        List<M3uStreamParser.ParsedChannel> channels = parse("""
                #EXTM3U
                #EXTINF:-1 tvg-id="test.1" tvg-name="Test One" tvg-logo="https://test.example/a.png" group-title="Demo",Test Channel One
                %s
                """.formatted(BBB));

        assertThat(channels).hasSize(1);
        M3uStreamParser.ParsedChannel channel = channels.getFirst();
        assertThat(channel.tvgId()).isEqualTo("test.1");
        // The text after the comma is what the user sees in their own player, so
        // it wins over tvg-name.
        assertThat(channel.name()).isEqualTo("Test Channel One");
        assertThat(channel.logoUrl()).isEqualTo("https://test.example/a.png");
        assertThat(channel.categoryName()).isEqualTo("Demo");
        assertThat(channel.streamUrl()).isEqualTo(BBB);
    }

    @Test
    @DisplayName("reads tvg-chno as the channel number, and the quality out of the name")
    void readsNumberAndQuality() {
        List<M3uStreamParser.ParsedChannel> channels = parse("""
                #EXTM3U
                #EXTINF:-1 tvg-chno="42" group-title="Demo",Test Channel FHD
                %s
                #EXTINF:-1 group-title="Demo",Test Channel Two
                %s
                """.formatted(BBB, BBB));

        assertThat(channels).hasSize(2);
        assertThat(channels.getFirst().number()).isEqualTo(42);
        assertThat(channels.getFirst().quality()).isEqualTo("FHD");
        // The name is read, never rewritten: it is the string the user sees in
        // every other player they own.
        assertThat(channels.getFirst().name()).isEqualTo("Test Channel FHD");

        // The common case, and the one that must not be invented: no number, no
        // badge.
        assertThat(channels.get(1).number()).isNull();
        assertThat(channels.get(1).quality()).isNull();
    }

    @Test
    @DisplayName("an entry with no group-title lands in Unclassified rather than being dropped")
    void ungroupedEntriesAreKept() {
        List<M3uStreamParser.ParsedChannel> channels = parse("""
                #EXTM3U
                #EXTINF:-1,No Group Here
                %s
                """.formatted(BBB));

        // US-07: channels with no group-title fall into "Unclassified".
        assertThat(channels).singleElement()
                .extracting(M3uStreamParser.ParsedChannel::categoryName)
                .isEqualTo("Unclassified");
    }

    @Test
    @DisplayName("#EXTGRP is honoured when group-title is absent")
    void extgrpIsHonoured() {
        List<M3uStreamParser.ParsedChannel> channels = parse("""
                #EXTM3U
                #EXTINF:-1,Legacy Grouped
                #EXTGRP:Legacy Group
                %s
                """.formatted(BBB));

        assertThat(channels).singleElement()
                .extracting(M3uStreamParser.ParsedChannel::categoryName)
                .isEqualTo("Legacy Group");
    }

    @Test
    @DisplayName("tolerates a BOM, blank lines, CRLF and unquoted attributes")
    void tolerdatesRealWorldMess() {
        String playlist = "﻿#EXTM3U\r\n"
                + "\r\n"
                + "#EXTINF:-1 tvg-id=test.2 group-title=Messy,Second Channel\r\n"
                + BBB + "\r\n"
                + "\r\n";

        List<M3uStreamParser.ParsedChannel> channels = parse(playlist);

        assertThat(channels).singleElement().satisfies(channel -> {
            assertThat(channel.tvgId()).isEqualTo("test.2");
            assertThat(channel.categoryName()).isEqualTo("Messy");
        });
    }

    @Test
    @DisplayName("skips a malformed entry instead of failing the whole import")
    void skipsMalformedEntriesWithoutFailing() {
        List<M3uStreamParser.ParsedChannel> channels = parse("""
                #EXTM3U
                %s
                #EXTINF:-1,Good Channel
                %s
                """.formatted("https://test.example/orphan-url-with-no-extinf.m3u8", BBB));

        // The orphan URL has no name and no group; one bad line out of fifteen
        // thousand must not cost the user their catalogue.
        assertThat(channels).singleElement()
                .extracting(M3uStreamParser.ParsedChannel::name)
                .isEqualTo("Good Channel");
    }

    @Test
    @DisplayName("content that is not a playlist is SOURCE_INVALID_FORMAT, not SOURCE_EMPTY")
    void rejectsNonPlaylistContent() {
        // Without the #EXTM3U check an HTML error page parses as a playlist with
        // zero channels, and the user is told their playlist is empty when the
        // real problem is a wrong URL (US-07).
        assertThatThrownBy(() -> parse("<html><body>404 Not Found</body></html>"))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).code())
                .isEqualTo(IngestionErrorCode.SOURCE_INVALID_FORMAT);
    }

    @Test
    @DisplayName("a playlist that parses but holds no channel is SOURCE_EMPTY")
    void reportsEmptyPlaylist() {
        // Never a success screen over an empty list (US-07).
        assertThatThrownBy(() -> parse("#EXTM3U\n"))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).code())
                .isEqualTo(IngestionErrorCode.SOURCE_EMPTY);
    }

    @Test
    @DisplayName("emits each channel as it is read rather than collecting them")
    void streamsRatherThanBuffers() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < 500; i++) {
            playlist.append("#EXTINF:-1 group-title=\"Bulk\",Channel ").append(i).append('\n')
                    .append(BBB).append('?').append(i).append('\n');
        }

        List<Integer> sizeAtEachCallback = new ArrayList<>();
        List<M3uStreamParser.ParsedChannel> seen = new ArrayList<>();
        int count = parser.parse(stream(playlist.toString()), channel -> {
            seen.add(channel);
            sizeAtEachCallback.add(seen.size());
        });

        assertThat(count).isEqualTo(500);
        // The consumer was invoked once per channel while parsing, which is what
        // "streaming, never the whole file in memory" means in practice.
        assertThat(sizeAtEachCallback).hasSize(500).first().isEqualTo(1);
    }

    private List<M3uStreamParser.ParsedChannel> parse(String playlist) {
        List<M3uStreamParser.ParsedChannel> channels = new ArrayList<>();
        parser.parse(stream(playlist), channels::add);
        return channels;
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
