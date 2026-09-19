package tv.lumo.android.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which source a device browses (US-018), one branch per acceptance criterion.
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * **Every one of these looks fine with a single source.** That is the account
 * most people have and the one every manual test uses, and with it "the first of
 * the list", "the stored one" and "the only one" are the same source. The rule
 * only shows on the second source, the second device and the deletion — which
 * are exactly the situations nobody reproduces by hand before a release.
 *
 * **The failed fetch.** Clearing the choice when the list cannot be read is the
 * natural thing to write — the stored id "was not found" — and it sends somebody
 * whose Wi-Fi blinked to a chooser, or to "add a source". An unreachable server
 * proves nothing; only a successful list or a `404 SOURCE_NOT_FOUND` does.
 */
class ActiveSourceResolverTest {

    private val a = benchSource("Source A")
    private val b = benchSource("Source B")
    private val c = benchSource("Source C")

    @Test
    fun `an account with no source has nothing to select`() {
        assertThat(ActiveSourceResolver.resolve(emptyList(), storedId = null))
            .isEqualTo(ActiveSourceState.None)

        // A choice recorded for a source that no longer exists changes nothing:
        // the way forward is still the form that adds one.
        assertThat(ActiveSourceResolver.resolve(emptyList(), storedId = a.key))
            .isEqualTo(ActiveSourceState.None)
    }

    @Test
    fun `the recorded choice wins, wherever it sits in the list`() {
        val state = ActiveSourceResolver.resolve(listOf(a, b, c), storedId = c.key)

        // Not the first of the list. That was the behaviour this replaces, and
        // with the choice last in the list the two answers differ.
        assertThat(state).isEqualTo(ActiveSourceState.Selected(c.key, c, listOf(a, b, c)))
    }

    @Test
    fun `no recorded choice and a single source selects it without asking`() {
        assertThat(ActiveSourceResolver.resolve(listOf(a), storedId = null))
            .isEqualTo(ActiveSourceState.Selected(a.key, a, listOf(a)))
    }

    @Test
    fun `a recorded choice that is gone and one source left selects what is left`() {
        // The active source was deleted elsewhere and the list came back without
        // it: that absence is a proof, and one source left is not a question.
        assertThat(ActiveSourceResolver.resolve(listOf(b), storedId = a.key))
            .isEqualTo(ActiveSourceState.Selected(b.key, b, listOf(b)))
    }

    @Test
    fun `no recorded choice and several sources asks, and preselects nothing`() {
        val state = ActiveSourceResolver.resolve(listOf(a, b), storedId = null)

        assertThat(state).isEqualTo(ActiveSourceState.NeedsChoice(listOf(a, b)))
        assertThat(state.selectedSourceId).isNull()
    }

    @Test
    fun `a recorded choice that is gone and several sources left asks`() {
        assertThat(ActiveSourceResolver.resolve(listOf(b, c), storedId = a.key))
            .isEqualTo(ActiveSourceState.NeedsChoice(listOf(b, c)))
    }

    // ---- a list that could not be fetched ------------------------------------

    @Test
    fun `a failed fetch keeps what was already resolved`() {
        val resolved = ActiveSourceState.Selected(a.key, a, listOf(a, b))

        assertThat(ActiveSourceResolver.withoutList(resolved, storedId = a.key))
            .isSameInstanceAs(resolved)
    }

    @Test
    fun `a failed fetch at launch keeps the stored choice, without a name to show`() {
        val state = ActiveSourceResolver.withoutList(ActiveSourceState.Loading, storedId = a.key)

        // The identifier is enough to read the cached catalogue. The name and the
        // status are the server's, so they are absent rather than invented.
        assertThat(state)
            .isEqualTo(ActiveSourceState.Selected(a.key, source = null, sources = emptyList()))
    }

    @Test
    fun `a failed fetch with nothing stored is unknown, not an empty account`() {
        // `None` would send a user who may well have three sources to the form
        // that adds one, because a request timed out.
        assertThat(ActiveSourceResolver.withoutList(ActiveSourceState.Loading, storedId = null))
            .isEqualTo(ActiveSourceState.Unavailable)
    }

    @Test
    fun `a failed fetch does not answer a pending question either`() {
        val asking = ActiveSourceState.NeedsChoice(listOf(a, b))

        assertThat(ActiveSourceResolver.withoutList(asking, storedId = null))
            .isSameInstanceAs(asking)
    }

    // ---- a source the server says is gone ------------------------------------

    @Test
    fun `the active source is gone and one is left, which becomes active`() {
        val state = ActiveSourceResolver.withoutSource(listOf(a, b), storedId = a.key, goneId = a.key)

        assertThat(state).isEqualTo(ActiveSourceState.Selected(b.key, b, listOf(b)))
    }

    @Test
    fun `the active source is gone and several are left, so the user is asked`() {
        val state = ActiveSourceResolver.withoutSource(listOf(a, b, c), storedId = a.key, goneId = a.key)

        assertThat(state).isEqualTo(ActiveSourceState.NeedsChoice(listOf(b, c)))
    }

    @Test
    fun `the active source is gone and none is left, which leads to adding one`() {
        val state = ActiveSourceResolver.withoutSource(listOf(a), storedId = a.key, goneId = a.key)

        assertThat(state).isEqualTo(ActiveSourceState.None)
    }

    @Test
    fun `another source being gone leaves the active one alone`() {
        val state = ActiveSourceResolver.withoutSource(listOf(a, b, c), storedId = a.key, goneId = b.key)

        assertThat(state).isEqualTo(ActiveSourceState.Selected(a.key, a, listOf(a, c)))
    }

    // ---- adding a source (US-024, "Après ajout") ------------------------------

    @Test
    fun `the first source an account adds becomes active on this device`() {
        // Before: nothing. After the creation, the list holds exactly the new one.
        assertThat(ActiveSourceResolver.resolve(emptyList(), storedId = null))
            .isEqualTo(ActiveSourceState.None)
        assertThat(ActiveSourceResolver.resolve(listOf(a), storedId = null).selectedSourceId)
            .isEqualTo(a.key)
    }

    @Test
    fun `an additional source does not steal the selection`() {
        // Whatever the order the server lists them in: a new source sorted first
        // is precisely the case "take the first of the list" got wrong.
        assertThat(ActiveSourceResolver.resolve(listOf(b, a), storedId = a.key).selectedSourceId)
            .isEqualTo(a.key)
    }
}
