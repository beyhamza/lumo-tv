package tv.lumo.api.ingest;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tv.lumo.api.catalog.CatalogWriteRepository;
import tv.lumo.api.catalog.VodPlotSource;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.ingest.xtream.XtreamClient;
import tv.lumo.api.shared.crypto.CredentialCipher;
import tv.lumo.api.source.SourceRepository;

/**
 * Fetches one film's synopsis from an Xtream panel, and caches it (S5-04).
 *
 * <h2>Why this is not part of the ingestion</h2>
 *
 * {@code get_vod_info} takes one identifier and answers for one film. Calling it
 * while walking a catalogue would mean one HTTP request per film, against the
 * user's own provider, at every synchronisation — thirty thousand of them on an
 * ordinary panel. That is not slow, it is the kind of traffic that gets an address
 * banned; and {@link HostConcurrencyLimiter} protects <em>our</em> infrastructure,
 * not theirs.
 *
 * <p>So the synopsis is fetched the first time somebody opens a film, and cached
 * from then on. The catalogue walk stays a walk.
 *
 * <h2>It never fails an open</h2>
 *
 * Every path here returns null rather than throwing. A provider that is down, that
 * answers an array where an object belongs, or that simply has no synopsis, must
 * not stop a film from playing.
 *
 * <h2>What gets stamped, and what does not</h2>
 *
 * The distinction is the whole value of {@code plot_fetched_at} being a column of
 * its own:
 *
 * <ul>
 *   <li><b>The provider answered and had nothing</b> — stamped. That answer is not
 *       going to change, and asking again on every open would spend somebody's
 *       panel on a question already settled.
 *   <li><b>The provider could not be reached</b> — <em>not</em> stamped. That is a
 *       moment, not a fact, and the next open tries again.
 * </ul>
 */
@Component
public class XtreamVodPlotSource implements VodPlotSource {

    private static final Logger log = LoggerFactory.getLogger(XtreamVodPlotSource.class);

    private final SourceRepository sources;
    private final CatalogWriteRepository catalogWrites;
    private final CredentialCipher cipher;
    private final XtreamClient xtream;

    public XtreamVodPlotSource(SourceRepository sources,
                               CatalogWriteRepository catalogWrites,
                               CredentialCipher cipher,
                               XtreamClient xtream) {
        this.sources = sources;
        this.catalogWrites = catalogWrites;
        this.cipher = cipher;
        this.xtream = xtream;
    }

    @Override
    public String fetchAndCachePlot(UUID sourceId, UUID vodItemId, String externalId) {
        if (externalId == null) {
            return null;
        }

        SourceRepository.SourceRow source = sources.findForIngestion(sourceId).orElse(null);
        // An M3U film has no panel to ask: its playlist is the whole of what is
        // known about it. Stamping the fetch anyway is what stops every open from
        // walking this method again for an answer that will never exist.
        if (source == null || source.kind() != SourceKind.XTREAM) {
            catalogWrites.updateVodPlot(vodItemId, null);
            return null;
        }

        try {
            byte[] sealed = sources.findSealedPassword(sourceId).orElse(null);
            if (sealed == null) {
                catalogWrites.updateVodPlot(vodItemId, null);
                return null;
            }

            String plot = xtream.fetchVodPlot(
                    source.host(), source.username(), cipher.open(sealed), externalId);
            catalogWrites.updateVodPlot(vodItemId, plot);
            return plot;
        } catch (IngestionException e) {
            // The provider is unreachable or refused. Named at info level, and the
            // film opens regardless.
            log.info("Could not fetch the synopsis of film {}: {}", vodItemId, e.code());
            return null;
        } catch (RuntimeException e) {
            log.warn("Unexpected failure fetching the synopsis of film {}", vodItemId, e);
            return null;
        }
    }
}
