package tv.lumo.android.core.data.repository

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.lumo.android.core.common.di.Dispatcher
import tv.lumo.android.core.common.di.LumoDispatcher
import tv.lumo.android.core.data.LumoResult
import tv.lumo.android.core.data.internal.ApiCaller
import tv.lumo.android.core.data.internal.EpgBatchLoader
import tv.lumo.android.core.data.model.Cached
import tv.lumo.android.core.data.model.DataOrigin
import tv.lumo.android.core.data.model.EpgChannelWindow
import tv.lumo.android.core.data.model.EpgImportStatus
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.data.model.EpgWindow
import tv.lumo.android.core.database.dao.EpgDao
import tv.lumo.android.core.database.model.EpgChannelProgrammeRow
import tv.lumo.android.core.database.model.EpgImportStatusEntity
import tv.lumo.android.core.database.model.EpgProgrammeEntity
import tv.lumo.android.network.generated.api.CatalogApi
import tv.lumo.android.network.generated.model.EpgAttemptStatus
import tv.lumo.android.network.generated.model.EpgChannelProgrammes as ApiEpgChannelProgrammes
import tv.lumo.android.network.generated.model.EpgGrid
import tv.lumo.android.network.generated.model.EpgProgramme as ApiEpgProgramme

/**
 * The programme guide, offline first (US-16, S9-03, taking up S7-02).
 *
 * An interface, as [ActiveSourceRepository] is, so that the screens' own logic
 * — which page to ask for, what to show when the answer is empty — is held by
 * tests that need neither Room nor a server.
 */
interface EpgRepository {

    /**
     * The guide of [channelIds] over `[from, to)`, cache first.
     *
     * <h3>Two emissions, and the first one is the cache</h3>
     *
     * The rule of every cache in this module (S7-02: "ce qui est en cache
     * gagne"): what Room holds is emitted at once, as [DataOrigin.Cache], and
     * only then is the server asked. A screen draws the first answer and
     * redraws on the second; on a train the second never comes and nothing is
     * different on screen — that is the offline case being ordinary.
     *
     * The second emission is the server's answer, written to the cache first,
     * as [DataOrigin.Network]. When the server could not be reached, or refused,
     * it is the cache again with [Cached.staleReason] naming why, so that a
     * screen with somewhere to say it can.
     *
     * The flow completes after the second emission. It is a read, not a
     * subscription: "now" moves on the screen's own clock over what it holds
     * ([tv.lumo.android.core.data.currentAndNext]), not by asking again.
     *
     * <h3>One request for the batch, and what happens when it is too large</h3>
     *
     * All [channelIds] go in one `GET /sources/{id}/epg`. On
     * `422 EPG_WINDOW_TOO_LARGE` the request is split — channels first, then the
     * window, at most two in flight and three levels deep — and merged back; past
     * three levels the `422` is the failure returned, with its own code, and the
     * cache stays on screen. See `EpgBatchLoader`.
     *
     * <h3>Never the provider</h3>
     *
     * Every read is of what the server has stored. Nothing here, retried or not,
     * asks the server to ingest anything (C1 D1).
     *
     * @param channelIds 1 to 100, distinct. More are asked for in chunks of 100.
     */
    fun window(sourceId: String, channelIds: List<String>, from: Instant, to: Instant): Flow<Cached<EpgWindow>>

    /** Drops one source's guide, for a source the user just deleted. */
    suspend fun forget(sourceId: String)
}

/**
 * <h2>The cache holds windows, and is purged</h2>
 *
 * Unlike the catalogue this data goes stale on its own, so it is not replaced
 * whole at a refresh: each read writes the programmes it fetched, and once a
 * day — on the first read of the process, then every 24 hours — what ended
 * before D−1 is dropped, calqued on the server's retention (S7-02). The
 * clock is injected so the purge, like the ages, is a thing a test can move.
 */
@Singleton
internal class DefaultEpgRepository @Inject constructor(
    private val api: CatalogApi,
    private val calls: ApiCaller,
    private val epgDao: EpgDao,
    private val clock: Clock,
    @Dispatcher(LumoDispatcher.IO) private val io: CoroutineDispatcher,
) : EpgRepository {

    private val loader = EpgBatchLoader { sourceId, channelIds, from, to ->
        calls.call {
            api.getSourceEpg(
                id = UUID.fromString(sourceId),
                channelIds = channelIds.map(UUID::fromString),
                from = from.atOffset(ZoneOffset.UTC),
                to = to.atOffset(ZoneOffset.UTC),
            )
        }
    }

    private val purge = Mutex()
    private var lastPurgeAt: Instant? = null

    override fun window(
        sourceId: String,
        channelIds: List<String>,
        from: Instant,
        to: Instant,
    ): Flow<Cached<EpgWindow>> = flow {
        require(channelIds.isNotEmpty()) { "A guide read names at least one channel" }
        require(from < to) { "A guide window has a strictly positive duration" }
        val ids = channelIds.distinct()

        purgeIfDue()

        emit(Cached(readCache(sourceId, ids, from, to), DataOrigin.Cache))

        // Chunks of the contract's cap, one after the other: a screen never
        // asks for more than a page, so this loop runs once in practice.
        val fetched = ids.chunked(MAX_BATCH).map { chunk -> loader.load(sourceId, chunk, from, to) }

        val failure = fetched.firstOrNull { it is LumoResult.Failure } as LumoResult.Failure?
        if (failure != null) {
            emit(Cached(readCache(sourceId, ids, from, to), DataOrigin.Cache, staleReason = failure.error))
            return@flow
        }

        val grids = fetched.map { (it as LumoResult.Success).value }
        val fetchedAt = clock.instant()
        val status = grids.first().asStatusEntity(sourceId, fetchedAt)
        epgDao.store(grids.flatMap { it.programmeEntities() }, status)

        emit(
            Cached(
                EpgWindow(
                    sourceId = sourceId,
                    from = from,
                    to = to,
                    fetchedAt = fetchedAt,
                    generatedAt = status.generatedAt.asInstant(),
                    importStatus = status.asImportStatus(),
                    channels = grids.flatMap { grid -> grid.channels.map { it.asWindow() } },
                ),
                DataOrigin.Network,
            ),
        )
    }.flowOn(io)

    override suspend fun forget(sourceId: String) = withContext(io) {
        epgDao.forget(sourceId)
    }

    private suspend fun readCache(
        sourceId: String,
        ids: List<String>,
        from: Instant,
        to: Instant,
    ): EpgWindow {
        val status = epgDao.importStatus(sourceId)
        val rows = epgDao.window(ids, from.toEpochMilli(), to.toEpochMilli())
            .groupBy(EpgChannelProgrammeRow::channelId)

        return EpgWindow(
            sourceId = sourceId,
            from = from,
            to = to,
            fetchedAt = status?.fetchedAt?.asInstant(),
            generatedAt = status?.generatedAt?.asInstant(),
            importStatus = status?.asImportStatus(),
            channels = ids.map { id ->
                EpgChannelWindow(
                    channelId = id,
                    // The cache does not know whether a `tvg_id` is set on the
                    // server's copy of the channel; only the server says.
                    mappingStatus = null,
                    programmes = rows[id].orEmpty().map { it.programme.asProgramme() },
                )
            },
        )
    }

    /**
     * Once per process, then once a day. On the read path rather than on a
     * timer of its own: a repository that woke up at 3 a.m. to delete rows on a
     * television nobody is watching would be a radio kept awake for a cache.
     */
    private suspend fun purgeIfDue() = purge.withLock {
        val now = clock.instant()
        val last = lastPurgeAt
        if (last != null && Duration.between(last, now) < PURGE_EVERY) return@withLock

        epgDao.purgeEndedBefore(now.minus(RETENTION).toEpochMilli())
        lastPurgeAt = now
    }

    private companion object {
        /** The contract's cap on `channelIds`. */
        const val MAX_BATCH = 100

        /** The server's own retention: what ended before D−1 goes (C1 §1). */
        val RETENTION: Duration = Duration.ofDays(1)
        val PURGE_EVERY: Duration = Duration.ofDays(1)
    }
}

// ---- mapping ---------------------------------------------------------------
//
// The two directions live next to each other on purpose, as in
// CatalogueRepository: a field added to the contract and forgotten in the cache
// is visible here, in one screenful.

private fun Long.asInstant(): Instant = Instant.ofEpochMilli(this)

private fun EpgGrid.programmeEntities(): List<EpgProgrammeEntity> =
    channels.flatMap { it.programmes }
        // The same programme under two channels is one row: one UUID, one key.
        .distinctBy { it.id }
        .map { it.asEntity() }

private fun ApiEpgProgramme.asEntity() = EpgProgrammeEntity(
    id = id.toString(),
    sourceId = sourceId.toString(),
    tvgId = tvgId,
    startsAt = startsAt.toInstant().toEpochMilli(),
    endsAt = endsAt.toInstant().toEpochMilli(),
    title = title,
    description = description,
    category = category,
)

private fun ApiEpgProgramme.asProgramme() = EpgProgramme(
    id = id.toString(),
    startsAt = startsAt.toInstant(),
    endsAt = endsAt.toInstant(),
    title = title,
    description = description,
    category = category,
)

private fun EpgProgrammeEntity.asProgramme() = EpgProgramme(
    id = id,
    startsAt = startsAt.asInstant(),
    endsAt = endsAt.asInstant(),
    title = title,
    description = description,
    category = category,
)

private fun ApiEpgChannelProgrammes.asWindow() = EpgChannelWindow(
    channelId = channelId.toString(),
    mappingStatus = mappingStatus,
    programmes = programmes.map { it.asProgramme() },
)

private fun EpgGrid.asStatusEntity(sourceId: String, fetchedAt: Instant) = EpgImportStatusEntity(
    sourceId = sourceId,
    configured = epg.configured,
    lastSuccessfulImportAt = epg.lastSuccessfulImportAt?.toInstant()?.toEpochMilli(),
    lastAttemptStartedAt = epg.lastAttemptStartedAt?.toInstant()?.toEpochMilli(),
    lastAttemptFinishedAt = epg.lastAttemptFinishedAt?.toInstant()?.toEpochMilli(),
    lastAttemptStatus = epg.lastAttemptStatus.value,
    generatedAt = generatedAt.toInstant().toEpochMilli(),
    fetchedAt = fetchedAt.toEpochMilli(),
)

private fun EpgImportStatusEntity.asImportStatus() = EpgImportStatus(
    configured = configured,
    lastSuccessfulImportAt = lastSuccessfulImportAt?.asInstant(),
    lastAttemptStartedAt = lastAttemptStartedAt?.asInstant(),
    lastAttemptFinishedAt = lastAttemptFinishedAt?.asInstant(),
    // A status text this build does not know — a value added to the contract
    // since — reads as UNKNOWN, which is what it is to this build.
    lastAttemptStatus = EpgAttemptStatus.decode(lastAttemptStatus) ?: EpgAttemptStatus.UNKNOWN,
)
