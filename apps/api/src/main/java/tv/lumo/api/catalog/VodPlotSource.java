package tv.lumo.api.catalog;

import java.util.UUID;

/**
 * Fetches the synopsis of one film from the user's own provider.
 *
 * <p><b>An interface here, implemented in {@code ingest}, and that is about
 * dependency direction rather than taste.</b> {@code ingest} already depends on
 * this package — it writes the catalogue — so a controller reaching the other way
 * for {@code XtreamClient} would close the loop. The port is declared where it is
 * needed and the adapter lives where the outbound calls belong.
 *
 * <p>It is also the honest shape of the thing: reading a catalogue is this
 * package's job, and calling somebody else's panel is not.
 */
public interface VodPlotSource {

    /**
     * Asks the source for a film's synopsis, and caches whatever comes back.
     *
     * <p><b>Never throws.</b> A provider that is down, slow, or answering nonsense
     * must not stop a film from opening: the synopsis is a comfort and the film is
     * the product. The caller renders what it has.
     *
     * <p>Implementations must stamp the fetch even when the answer is null, so
     * that a film the provider has no synopsis for does not cost a call every time
     * somebody opens it.
     *
     * @param sourceId   the source the film belongs to, already checked as owned
     * @param externalId the film's identifier on the provider's side
     * @return the synopsis, or null when there is none to be had
     */
    String fetchAndCachePlot(UUID sourceId, UUID vodItemId, String externalId);
}
