package tv.lumo.android.core.data.repository

import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.LumoError
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.Answered
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.ConnectivityMonitor
import tv.lumo.android.core.data.model.PendingIntention
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistEntry
import tv.lumo.android.core.data.model.WatchlistSlice
import tv.lumo.android.core.data.model.WatchlistTarget
import tv.lumo.android.core.data.model.WatchlistType
import tv.lumo.android.core.data.model.WatchlistWriteOutcome
import tv.lumo.android.network.generated.api.UserdataApi
import tv.lumo.android.network.generated.model.AddWatchlistItemRequest
import tv.lumo.android.network.generated.model.ErrorCode
import tv.lumo.android.network.generated.model.WatchlistContentType
import tv.lumo.android.network.generated.model.WatchlistItem
import tv.lumo.android.network.generated.model.WatchlistPage

/**
 * "My list": films and series an account saved to watch later (US-022, ADR 0011).
 *
 * <h2>Network only, on purpose</h2>
 *
 * No Room table and no persistent cache. ADR 0011 ruling 4 allows reading **the
 * list already held** while offline and nothing more; a durable offline copy is a
 * new caching strategy and needs its own ADR. What a screen loaded stays in its
 * view model's memory, and that is the list it can show offline.
 *
 * <h2>Writes carry the intention's key, and never pick one</h2>
 *
 * [write] sends the [PendingIntention] it is handed, key included. Choosing the key
 * is the caller's job ([tv.lumo.android.core.data.WatchlistIntentions]), because
 * only the caller knows whether a press is a retry or a new intention.
 */
interface WatchlistRepository {

    /**
     * One page of the list for [sourceId], most recently added first, in the
     * server's order.
     *
     * @param type null for both types. Never narrows [WatchlistSlice.types].
     */
    suspend fun page(
        sourceId: String,
        type: WatchlistType? = null,
        page: Int = 0,
        size: Int = PAGE_SIZE,
    ): LumoResult<WatchlistSlice>

    /**
     * The item that holds [contentId], or null when it is not in the list.
     *
     * The contract has no lookup by content, so this walks [sourceId]'s pages of
     * [type] at the largest size the server accepts until the content is found or
     * the list ends.
     */
    suspend fun find(sourceId: String, type: WatchlistType, contentId: String): LumoResult<WatchlistEntry?>

    /** Sends [intention] with its own key, and says what became of it. */
    suspend fun write(intention: PendingIntention): WatchlistWriteOutcome

    /** One emission each time the device gets a network back. */
    val reconnections: Flow<Unit>

    companion object {
        /** A screen's page. */
        const val PAGE_SIZE = 50

        /** The contract's ceiling for `size`, used to walk the list for membership. */
        const val MAX_PAGE_SIZE = 200
    }
}

internal class DefaultWatchlistRepository @Inject constructor(
    private val api: UserdataApi,
    private val calls: ApiCaller,
    connectivity: ConnectivityMonitor,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) : WatchlistRepository {

    override val reconnections: Flow<Unit> = connectivity.regained

    override suspend fun page(
        sourceId: String,
        type: WatchlistType?,
        page: Int,
        size: Int,
    ): LumoResult<WatchlistSlice> = withContext(io) {
        when (
            val result = calls.call {
                api.listWatchlist(
                    sourceId = UUID.fromString(sourceId),
                    contentType = type?.asApi(),
                    page = page,
                    size = size,
                )
            }
        ) {
            is LumoResult.Failure -> result
            is LumoResult.Success -> LumoResult.Success(result.value.asSlice())
        }
    }

    override suspend fun find(
        sourceId: String,
        type: WatchlistType,
        contentId: String,
    ): LumoResult<WatchlistEntry?> = withContext(io) {
        var index = 0
        var found: WatchlistEntry? = null
        var more = true
        while (found == null && more) {
            val slice = when (val result = page(sourceId, type, index, WatchlistRepository.MAX_PAGE_SIZE)) {
                is LumoResult.Failure -> return@withContext result
                is LumoResult.Success -> result.value
            }
            found = slice.items.firstOrNull { it.contentId == contentId }
            // An empty page ends the walk too: a server that miscounted must not
            // turn this into an endless loop.
            more = slice.hasMore && slice.items.isNotEmpty()
            index++
        }
        LumoResult.Success(found)
    }

    override suspend fun write(intention: PendingIntention): WatchlistWriteOutcome = withContext(io) {
        when (intention.action) {
            WatchlistAction.Add -> {
                val target = intention.target as? WatchlistTarget.Content
                    ?: error("An add names the content, not an item: $intention")
                val request = AddWatchlistItemRequest(
                    contentType = target.type.asApi(),
                    contentId = UUID.fromString(target.contentId),
                )
                classifyAdd(calls.answered { api.addWatchlistItem(intention.key, request) })
            }

            WatchlistAction.Remove -> {
                val target = intention.target as? WatchlistTarget.Item
                    ?: error("A removal names the item, not the content: $intention")
                classifyRemove(
                    calls.empty { api.removeWatchlistItem(UUID.fromString(target.itemId), intention.key) },
                )
            }
        }
    }
}

// ---- outcome classification -------------------------------------------------

/**
 * What an add's answer means (ADR 0011, `S11-00` G6–G8).
 *
 * `201` added, `200` it was already there. Everything that does not prove a
 * refusal is **unknown**: the request may have been accepted.
 */
internal fun classifyAdd(result: LumoResult<Answered<WatchlistItem>>): WatchlistWriteOutcome =
    when (result) {
        is LumoResult.Success -> {
            val item = result.value.body.asEntry()
            if (result.value.status == HTTP_CREATED) {
                WatchlistWriteOutcome.Confirmed(item)
            } else {
                WatchlistWriteOutcome.AlreadyInState(item)
            }
        }
        is LumoResult.Failure -> classifyFailure(result.error, alreadyInState = null)
    }

/**
 * What a removal's answer means. `204` removed; `404 WATCHLIST_ITEM_NOT_FOUND`
 * means it is already gone — somebody else removed it, or this is a new removal of
 * an item already removed — which is the state that was asked for.
 */
internal fun classifyRemove(result: LumoResult<Unit>): WatchlistWriteOutcome = when (result) {
    is LumoResult.Success -> WatchlistWriteOutcome.Confirmed(null)
    is LumoResult.Failure -> classifyFailure(result.error, alreadyInState = ErrorCode.WATCHLIST_ITEM_NOT_FOUND)
}

private fun classifyFailure(error: LumoError, alreadyInState: ErrorCode?): WatchlistWriteOutcome =
    when (error) {
        // No answer at all: the request may have reached the server.
        is LumoError.Offline -> WatchlistWriteOutcome.Unknown(error)

        is LumoError.Api -> when (error.code) {
            alreadyInState -> WatchlistWriteOutcome.AlreadyInState(null)
            // A client bug, not a conflict with another device. Treated as unknown
            // so the list is re-read; nothing is logged (no key in a log line).
            ErrorCode.IDEMPOTENCY_KEY_REUSED -> WatchlistWriteOutcome.Unknown(error)
            // A 5xx dressed as a problem document.
            ErrorCode.INTERNAL_ERROR -> WatchlistWriteOutcome.Unknown(error)
            else -> WatchlistWriteOutcome.Rejected(error)
        }

        // A code this build does not know: the server answered and refused.
        is LumoError.UnknownCode -> WatchlistWriteOutcome.Rejected(error)

        // An unreadable 5xx, or a success body that failed to parse (status null,
        // or 2xx): the server may well have accepted. A readable 4xx without a
        // problem document is a refusal.
        is LumoError.Unreadable -> {
            val status = error.status
            if (status == null || status >= HTTP_SERVER_ERROR || status in HTTP_SUCCESS) {
                WatchlistWriteOutcome.Unknown(error)
            } else {
                WatchlistWriteOutcome.Rejected(error)
            }
        }
    }

private const val HTTP_CREATED = 201
private const val HTTP_SERVER_ERROR = 500
private val HTTP_SUCCESS = 200..299

// ---- mapping ----------------------------------------------------------------

internal fun WatchlistType.asApi(): WatchlistContentType = when (this) {
    WatchlistType.Film -> WatchlistContentType.VOD
    WatchlistType.Series -> WatchlistContentType.SERIES
}

internal fun WatchlistContentType.asType(): WatchlistType = when (this) {
    WatchlistContentType.VOD -> WatchlistType.Film
    WatchlistContentType.SERIES -> WatchlistType.Series
}

internal fun WatchlistItem.asEntry() = WatchlistEntry(
    id = id.toString(),
    sourceId = sourceId.toString(),
    type = contentType.asType(),
    // The contract says null exactly when unavailable; an available item without
    // an id would open nothing, so the two are kept consistent here.
    contentId = contentId?.toString()?.takeIf { available },
    available = available && contentId != null,
    title = title,
    posterUrl = posterUrl?.takeIf { it.isNotBlank() },
    addedAt = addedAt.toInstant(),
)

internal fun WatchlistPage.asSlice() = WatchlistSlice(
    items = items.map(WatchlistItem::asEntry),
    page = page,
    totalPages = totalPages,
    totalElements = totalElements,
    types = contentTypes.map(WatchlistContentType::asType).toSet(),
)
