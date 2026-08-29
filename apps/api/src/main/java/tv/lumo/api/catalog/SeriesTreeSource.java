package tv.lumo.api.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Makes sure a series has a tree, fetching it from the user's own provider when it
 * does not — or when the one it has is old.
 *
 * <p><b>An interface here, implemented in {@code ingest}</b>, for the reason
 * written on {@link VodPlotSource}: {@code ingest} already depends on this package
 * because it writes the catalogue, so a controller reaching the other way would
 * close the loop.
 *
 * <p>The film port and this one are separate rather than one generic
 * "fetch-on-demand" abstraction. They differ in the two things that matter: a
 * film's synopsis never expires and this cache does, and a film's fetch either
 * works or is silently skipped while this one has a failure a client must be told
 * about.
 */
public interface SeriesTreeSource {

    /**
     * Ensures a tree is available to read, and says whether it is.
     *
     * <p><b>Three paths, and they are the whole design.</b>
     *
     * <ul>
     *   <li><b>Fresh</b> — nothing happens. No call, no wait.
     *   <li><b>Stale</b> — {@link Availability#AVAILABLE} comes back at once and
     *       the refresh runs behind it. The viewer sees the episodes they know
     *       while the new one is being fetched; making them wait on data already
     *       held would be a regression dressed as correctness.
     *   <li><b>Absent</b> — the fetch is made and waited for, because there is
     *       nothing to show otherwise. If it fails,
     *       {@link Availability#UNAVAILABLE}, and the caller answers {@code 503}
     *       rather than {@code 404}: the series exists, the call to fill it
     *       failed, and telling somebody their series is gone would send them
     *       looking in the wrong place.
     * </ul>
     *
     * <p><b>Never throws.</b> Every failure comes back as
     * {@link Availability#UNAVAILABLE}, so the caller has one thing to branch on.
     *
     * @param treeFetchedAt when the tree was last fetched, or null if it never was
     */
    Availability ensureTree(UUID sourceId, UUID seriesId, String externalId,
                            OffsetDateTime treeFetchedAt);

    /** Whether there is a tree to read, whatever its age. */
    enum Availability {

        /** A tree is in the table. It may be refreshing behind this answer. */
        AVAILABLE,

        /** Nothing is cached and the provider could not supply one. */
        UNAVAILABLE
    }
}
