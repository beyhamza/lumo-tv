package tv.lumo.api.source;

import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.Source;

/**
 * {@link SourceRepository.SourceRow} to the contract's {@code Source}.
 *
 * <p><b>There is no password here and there never will be.</b> The row type does
 * not carry one — {@code password_encrypted} is only ever read by
 * {@link SourceRepository#findSealedPassword}, on the ingestion path — and the
 * generated {@code Source} has no such property. Two independent barriers, so
 * adding one would take a deliberate change to the contract itself
 * (docs/domain-model.md §2).
 */
@Component
public class SourceMapper {

    /**
     * @param channelCount  null until the first successful ingestion
     * @param categoryCount null on the same terms; the two are rendered side by
     *                      side and one of them alone is half a sentence
     */
    public Source toApi(SourceRepository.SourceRow row, Integer channelCount, Integer categoryCount) {
        Source source = new Source(row.id(), row.label(), row.kind(), row.status(), row.autoSync());
        source.setHost(row.host());
        // Returned so an edit form can keep the host and username while the user
        // corrects only what was wrong (US-06).
        source.setUsername(row.username());
        source.setM3uUrl(row.m3uUrl());
        source.setEpgUrl(row.epgUrl());
        // Null unless the row is SYNCING, which the database guarantees rather
        // than this mapper: see the CHECK in 0010-source-sync-diagnostics.sql.
        source.setSyncStep(row.syncStep());
        source.setErrorCode(row.errorCode());
        source.setLastErrorAt(row.lastErrorAt());
        source.setLastSyncedAt(row.lastSyncedAt());
        source.setExpiresAt(row.expiresAt());
        source.setMaxConnections(row.maxConnections());
        source.setChannelCount(channelCount);
        source.setCategoryCount(categoryCount);
        return source;
    }
}
