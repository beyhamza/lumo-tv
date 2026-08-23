package tv.lumo.api.ingest.xmltv;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.ingest.IngestionException;

/**
 * Streaming XMLTV parser, built on StAX.
 *
 * <p><b>Pull parsing, never DOM.</b> An XMLTV guide is routinely 200 MB once
 * un-gzipped; a DOM of it is several times that in heap. Each {@code <programme>}
 * is emitted as soon as its closing tag is read and then becomes garbage, so peak
 * memory is one programme.
 *
 * <p>Programmes outside the D-1 to D+3 retention window are dropped here rather
 * than inserted and purged later — the cheapest row is the one never written.
 *
 * <p>The factory is hardened against XXE and entity expansion. The input is an
 * arbitrary third-party URL the user pointed us at, so it is hostile until
 * proven otherwise: without these two settings, a crafted guide reads local files
 * off this server or exhausts its memory with nested entities.
 */
@Component
public class XmltvStreamParser {

    private static final Logger log = LoggerFactory.getLogger(XmltvStreamParser.class);

    /** XMLTV timestamps: {@code 20260823120000 +0200}, sometimes with no offset. */
    private static final DateTimeFormatter WITH_OFFSET =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss[ ]Z");
    private static final DateTimeFormatter WITHOUT_OFFSET =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int RETENTION_DAYS_BACK = 1;
    private static final int RETENTION_DAYS_FORWARD = 3;

    private final XMLInputFactory factory;

    public XmltvStreamParser() {
        this.factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    /** @return how many programmes were emitted, after the retention window filter */
    public int parse(InputStream input, Consumer<ParsedProgramme> consumer) {
        OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS_BACK);
        OffsetDateTime windowEnd = OffsetDateTime.now(ZoneOffset.UTC).plusDays(RETENTION_DAYS_FORWARD);

        int emitted = 0;
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(input);

            String channel = null;
            OffsetDateTime start = null;
            OffsetDateTime stop = null;
            String title = null;
            String description = null;
            String category = null;
            String currentElement = null;
            boolean insideProgramme = false;

            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        currentElement = reader.getLocalName();
                        if ("programme".equals(currentElement)) {
                            insideProgramme = true;
                            channel = reader.getAttributeValue(null, "channel");
                            start = parseTimestamp(reader.getAttributeValue(null, "start"));
                            stop = parseTimestamp(reader.getAttributeValue(null, "stop"));
                            title = null;
                            description = null;
                            category = null;
                        }
                    }
                    case XMLStreamConstants.CHARACTERS -> {
                        if (insideProgramme && currentElement != null && !reader.isWhiteSpace()) {
                            String text = reader.getText().trim();
                            switch (currentElement) {
                                // First occurrence wins: guides repeat these
                                // elements once per language.
                                case "title" -> title = title == null ? text : title;
                                case "desc" -> description = description == null ? text : description;
                                case "category" -> category = category == null ? text : category;
                                default -> { /* icon, credits, rating: not modelled */ }
                            }
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        if ("programme".equals(reader.getLocalName())) {
                            if (isUsable(channel, start, stop, title)
                                    && stop.isAfter(windowStart) && start.isBefore(windowEnd)) {
                                consumer.accept(new ParsedProgramme(
                                        channel, start, stop, title, description, category));
                                emitted++;
                            }
                            insideProgramme = false;
                        }
                        currentElement = null;
                    }
                    default -> { /* comments, processing instructions */ }
                }
            }
        } catch (IngestionException e) {
            throw e;
        } catch (XMLStreamException e) {
            throw new IngestionException(IngestionErrorCode.SOURCE_INVALID_FORMAT,
                    "The guide is not valid XMLTV", e);
        } finally {
            closeQuietly(reader);
        }

        log.debug("Parsed {} programme(s) inside the retention window", emitted);
        return emitted;
    }

    /**
     * A programme with no channel, no title or an unparseable or inverted time
     * range is skipped. One bad entry in a guide of half a million should not cost
     * the user their whole EPG.
     */
    private static boolean isUsable(String channel, OffsetDateTime start, OffsetDateTime stop, String title) {
        return channel != null && !channel.isBlank()
                && start != null && stop != null && stop.isAfter(start)
                && title != null && !title.isBlank();
    }

    private static OffsetDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value, WITH_OFFSET);
        } catch (DateTimeParseException ignored) {
            try {
                // No offset: XMLTV says such a timestamp is UTC.
                return java.time.LocalDateTime.parse(value, WITHOUT_OFFSET).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                log.debug("Failed to close the XMLTV reader: {}", e.getClass().getSimpleName());
            }
        }
    }

    /** @param channelId the XMLTV {@code channel} attribute, joined to {@code channel.tvg_id} */
    public record ParsedProgramme(String channelId, OffsetDateTime startsAt, OffsetDateTime endsAt,
                                  String title, String description, String category) {
    }
}
