package tv.lumo.android.core.data.internal

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.database.dao.ChannelDao
import tv.lumo.android.core.database.model.ChannelEntity
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.model.Channel as ApiChannel

/**
 * Turns channel identifiers this device does not hold into cached channels.
 *
 * <h2>Why this is a class and not two copies of a loop</h2>
 *
 * Favourites and recently watched channels have the same shape — a list of
 * identifiers and nothing else, deliberately, because a name copied onto them
 * would be a name the next ingestion has already changed — so both need the same
 * resolution against `GET /sources/{id}/channels?ids=`. Written twice it would be
 * the same trap twice, and the trap is not obvious:
 *
 * - **`ids` is capped at 100** by the contract. Three hundred identifiers is three
 *   calls; sending them in one is a `400`.
 * - **`size` defaults to 50** on that same operation. A chunk of a hundred sent
 *   without it comes back half answered, with a `200` and nothing to say the rest
 *   was dropped — a catalogue that reads as a smaller catalogue rather than as a
 *   bug. This is the one that would have been written correctly once and wrongly
 *   the second time.
 *
 * <h2>Failing to resolve is not failing</h2>
 *
 * The caller has already written its own rows by the time this runs; what is
 * missing is the readable name of an entry whose source has never been
 * synchronised on this device. The next refresh, or the next catalogue
 * synchronisation, fixes it. Reporting an error here would put an error screen in
 * front of somebody whose favourites are perfectly fine.
 */
@Singleton
internal class ChannelResolver @Inject constructor(
    private val catalog: CatalogApi,
    private val calls: ApiCaller,
    private val channelDao: ChannelDao,
) {

    /**
     * Fetches and caches the named channels, grouped by source and chunked.
     *
     * @param missing source id to channel id, as the DAOs report it.
     */
    suspend fun resolve(missing: List<Pair<String, String>>) {
        if (missing.isEmpty()) return

        val resolved = mutableListOf<ChannelEntity>()
        for ((sourceId, channelIds) in missing.groupBy({ it.first }, { it.second })) {
            for (chunk in channelIds.chunked(ID_CHUNK)) {
                val page = calls.call {
                    catalog.listChannels(
                        id = UUID.fromString(sourceId),
                        ids = chunk.map(UUID::fromString),
                        size = ID_CHUNK,
                    )
                }
                if (page is LumoResult.Failure) return
                resolved += (page as LumoResult.Success).value.items.map(ApiChannel::asEntity)
            }
        }

        // Upserted, not replaced: these are genuine channels of that source, and
        // wiping the source's cache to add them would empty a catalogue in order
        // to name a favourite.
        channelDao.upsert(resolved)
    }

    private companion object {
        /**
         * The contract's cap on `?ids=`, and the reason this chunks at all.
         * Sending more is a `400`; sending exactly this and forgetting `size` is
         * worse, because it succeeds.
         */
        const val ID_CHUNK = 100
    }
}

private fun ApiChannel.asEntity() = ChannelEntity(
    id = id.toString(),
    sourceId = sourceId.toString(),
    categoryId = categoryId?.toString(),
    externalId = externalId,
    name = name,
    logoUrl = logoUrl,
    tvgId = tvgId,
    number = number,
    quality = quality,
    position = position,
    isAdult = isAdult,
)
