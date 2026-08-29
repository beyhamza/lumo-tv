package tv.lumo.android.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import tv.lumo.android.core.data.repository.SourceRepository
import tv.lumo.android.core.data.repository.VodRepository
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * Which parts of the catalogue this account's source actually has.
 *
 * <h2>Why a shell needs this at all</h2>
 *
 * US-13 asks for something an application usually gets wrong: **a source with no
 * films must not show a films tab.** Most M3U playlists carry only channels, and
 * a bar that offers a door onto an empty room is a promise nobody can keep —
 * worse than an absence, because the user goes looking for what they were
 * offered.
 *
 * A screen cannot answer this: the tab is drawn above it, by the shell, before
 * that screen exists. So the question is asked here, once, in the same shape
 * [AppStartDecision] uses — a flow assembled from repositories and collected by
 * the application — and each application maps it onto the navigation it owns.
 *
 * <h2>One request, not a synchronisation</h2>
 *
 * `GET /sources/{id}/categories?contentType=VOD` and nothing else. Films
 * themselves are pulled when somebody opens the tab; deciding whether to *draw*
 * it must not cost a walk through thirty thousand rows at every launch.
 *
 * After that first probe the answer comes out of Room, so a second launch
 * decides before the network answers — and an offline device with a
 * synchronised source keeps its tab.
 */
@Singleton
class CatalogueSections @Inject internal constructor(
    private val sources: SourceRepository,
    private val vod: VodRepository,
) {

    /**
     * Whether to offer films.
     *
     * False while the source is still importing, and false on a device that has
     * never reached the server. Both are the honest answer: nothing has said
     * there are films, and inventing a tab on the strength of a hope is the
     * empty promise this exists to avoid.
     */
    val hasFilms: Flow<Boolean> = flow {
        // False first, so the bar draws immediately with what is certain and
        // gains the tab a moment later. The other way round — offering it and
        // taking it away — moves a target under somebody's thumb.
        emit(false)

        val source = sources.sources().valueOrNull()?.firstOrNull()
        if (source == null || source.status != SourceStatus.READY) {
            emitAll(flowOf(false))
            return@flow
        }

        val sourceId = source.id.toString()
        // Failure is not handled and does not need to be: it leaves the cache as
        // it was, and the flow below reports that cache. A source that has films
        // and a phone that cannot reach the server keeps the tab it earned on a
        // previous run.
        vod.probeFilmCategories(sourceId)
        emitAll(vod.hasFilms(sourceId))
    }.distinctUntilChanged()
}
