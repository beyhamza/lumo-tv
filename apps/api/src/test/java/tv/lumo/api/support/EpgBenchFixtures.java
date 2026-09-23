package tv.lumo.api.support;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/** Synthetic XMLTV generated relative to the test clock; no provider data or logos. */
public final class EpgBenchFixtures {
    private static final DateTimeFormatter XMLTV = DateTimeFormatter.ofPattern("yyyyMMddHHmmss xx");

    private EpgBenchFixtures() {}

    public static String playlist(int channels) {
        var out = new StringBuilder("#EXTM3U\n");
        for (int i = 1; i <= channels; i++) {
            out.append("#EXTINF:-1 tvg-id=\"epg-bench-").append(i)
                    .append("\",EPG test ").append(i).append('\n')
                    // Mux's public test asset, never fetched by these ingestion tests.
                    .append("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8\n");
        }
        return out.toString();
    }

    public static byte[] xml(OffsetDateTime anchor, int channels, int minutes,
                            int descriptionChars, boolean broken, boolean variableDurations) {
        var out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv>");
        for (int channel = 1; channel <= channels; channel++) {
            int sequence = 0;
            for (var start = anchor.minusDays(1); start.isBefore(anchor.plusDays(3));) {
                int duration = variableDurations ? (sequence % 2 == 0 ? 15 : 45) : minutes;
                var end = start.plusMinutes(duration);
                if (end.isAfter(anchor.plusDays(3))) end = anchor.plusDays(3);
                out.append("<programme channel=\"epg-bench-").append(channel)
                        .append("\" start=\"").append(XMLTV.format(start))
                        .append("\" stop=\"").append(XMLTV.format(end))
                        .append("\"><title>Test programme ").append(sequence)
                        .append("</title><desc>").append("x".repeat(descriptionChars))
                        .append("</desc><category>Test</category></programme>");
                start = end;
                sequence++;
            }
        }
        out.append(broken ? "<broken" : "</tv>");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
