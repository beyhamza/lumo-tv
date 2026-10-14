package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test
import tv.lumo.android.core.data.model.WatchlistAction
import tv.lumo.android.core.data.model.WatchlistTarget
import tv.lumo.android.core.data.model.WatchlistType

/**
 * One idempotency key per intention (ADR 0011, ruling 6): a retry re-sends the
 * same key, a new intention gets a new one. If this is wrong, a lost answer
 * followed by "Retry" adds the film twice — or, worse, a delayed retry undoes a
 * newer action from another device.
 */
class WatchlistIntentionsTest {

    private val film = WatchlistTarget.Content(WatchlistType.Film, "film-1")
    private val keys = generateSequence(1) { it + 1 }
        .map { UUID(0L, it.toLong()) }
        .iterator()
    private val intentions = WatchlistIntentions(newKey = { keys.next() })

    @Test
    fun `a retry of the pending intention reuses its key`() {
        val first = intentions.intend(WatchlistAction.Add, film)
        val retry = intentions.intend(WatchlistAction.Add, film)

        assertThat(retry.key).isEqualTo(first.key)
        assertThat(intentions.pendingFor(film)).isEqualTo(first)
    }

    @Test
    fun `after the state is confirmed, the next press is a new intention with a new key`() {
        val first = intentions.intend(WatchlistAction.Add, film)
        intentions.settle(film)

        val again = intentions.intend(WatchlistAction.Add, film)

        assertThat(again.key).isNotEqualTo(first.key)
        assertThat(intentions.pendingFor(film)).isEqualTo(again)
    }

    @Test
    fun `the opposite change while one is pending is a new intention that replaces it`() {
        val add = intentions.intend(WatchlistAction.Add, film)
        val remove = intentions.intend(WatchlistAction.Remove, WatchlistTarget.Item("item-1"), subject = film)

        assertThat(remove.key).isNotEqualTo(add.key)
        assertThat(intentions.pendingFor(film)).isEqualTo(remove)
        // And going back to the add is new again: the first key is never re-sent.
        val addAgain = intentions.intend(WatchlistAction.Add, film)
        assertThat(addAgain.key).isNotIn(listOf(add.key, remove.key))
    }

    @Test
    fun `two subjects keep two keys`() {
        val other = WatchlistTarget.Content(WatchlistType.Series, "series-1")

        val a = intentions.intend(WatchlistAction.Add, film)
        val b = intentions.intend(WatchlistAction.Add, other)
        intentions.settle(other)

        assertThat(a.key).isNotEqualTo(b.key)
        assertThat(intentions.intend(WatchlistAction.Add, film).key).isEqualTo(a.key)
    }
}
