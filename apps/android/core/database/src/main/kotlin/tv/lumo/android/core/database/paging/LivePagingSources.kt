package tv.lumo.android.core.database.paging

import androidx.paging.PagingSource
import java.util.Collections
import java.util.WeakHashMap

/**
 * The paging sources a pager has handed out, so that the code that has just
 * rewritten a catalogue can invalidate them itself (BUG-R020-16-02).
 *
 * <h2>Why Room's own invalidation is not enough</h2>
 *
 * Room's `LimitOffsetPagingSource` (room-paging 2.8.4, `CommonLimitOffsetImpl`)
 * subscribes to table changes from a coroutine it launches, and drops every
 * change notified **before its first load has completed**. A refresh that
 * writes the catalogue while a grid is making that first load — the case on a
 * television opened on a source it has never synchronised — is therefore never
 * seen: the first page was read empty, an empty page asks for no next one, and
 * the grid stays empty until something else replaces the pager. Reproduced
 * three times out of three on the emulator; leaving the screen does not help,
 * because the view model and its cached pages survive the drawer.
 *
 * The repository knows exactly when it replaced a catalogue, so it says so. An
 * extra invalidation after Room's is harmless: an invalidated source is simply
 * replaced, at the same position.
 *
 * Held weakly: a grid that went away takes its sources with it, and nothing
 * here grows with the number of pagers ever built.
 */
internal class LivePagingSources<Value : Any> {

    private val sources: MutableSet<PagingSource<Int, Value>> =
        Collections.newSetFromMap(WeakHashMap())

    fun track(source: PagingSource<Int, Value>): PagingSource<Int, Value> {
        synchronized(sources) { sources += source }
        return source
    }

    fun invalidateAll() {
        val current = synchronized(sources) { sources.toList().also { sources.clear() } }
        current.forEach { it.invalidate() }
    }
}
