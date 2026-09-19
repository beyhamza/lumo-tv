package tv.lumo.android.feature.source.switcher

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import tv.lumo.android.core.data.ActiveSourceState
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceKind
import tv.lumo.android.network.generated.model.SourceStatus

/**
 * What the switcher draws, and when it tells the shell that something moved
 * (US-018, design `S8-E04`).
 *
 * <h2>What would be invisible until it was wrong</h2>
 *
 * **The single source.** It is the account nearly everybody has, and the
 * criterion for it is an absence: no affordance. A switcher that always drew its
 * chevron would look finished on every screenshot.
 *
 * **The required choice ticks nothing.** Preselecting the first source "to be
 * helpful" is one line, reads as polish, and is the exact thing the story rules
 * out — one among several is never picked on somebody's behalf.
 *
 * **The two events.** A shell that popped a screen on every emission of the
 * repository would navigate by itself on a refresh; one that never did would
 * leave a film of the old source on screen. Neither shows with one source.
 */
class SourceSwitcherStateTest {

    private val a = source("Source A")
    private val b = source("Source B", SourceStatus.SYNCING)
    private val c = source("Source C", SourceStatus.ERROR)

    @Test
    fun `a single source is a name, with nothing to open`() {
        val state = switcherStateOf(selected(a, listOf(a)))

        assertThat(state.activeLabel).isEqualTo("Source A")
        assertThat(state.canSwitch).isFalse()
        assertThat(state.mustChoose).isFalse()
    }

    @Test
    fun `several sources list their name, their state and a tick on the active one`() {
        val state = switcherStateOf(selected(b, listOf(a, b, c)))

        assertThat(state.activeLabel).isEqualTo("Source B")
        assertThat(state.canSwitch).isTrue()
        assertThat(state.choices).containsExactly(
            SourceChoice(a.key, "Source A", SourceChoiceStatus.Ready, active = false),
            SourceChoice(b.key, "Source B", SourceChoiceStatus.Refreshing, active = true),
            SourceChoice(c.key, "Source C", SourceChoiceStatus.Failed, active = false),
        ).inOrder()
    }

    @Test
    fun `a source accepted and a source started are the same state to a viewer`() {
        val pending = source("Source D", SourceStatus.PENDING)

        val state = switcherStateOf(selected(a, listOf(a, pending)))

        assertThat(state.choices.last().status).isEqualTo(SourceChoiceStatus.Refreshing)
    }

    @Test
    fun `a required choice preselects nothing`() {
        val state = switcherStateOf(ActiveSourceState.NeedsChoice(listOf(a, b)))

        assertThat(state.mustChoose).isTrue()
        assertThat(state.activeLabel).isNull()
        assertThat(state.choices.map { it.active }).containsExactly(false, false)
    }

    @Test
    fun `nothing to name draws nothing`() {
        val offline = ActiveSourceState.Selected(a.key, source = null, sources = emptyList())

        listOf(
            ActiveSourceState.Loading,
            ActiveSourceState.None,
            ActiveSourceState.Unavailable,
            // The choice is known, its name is the server's: no strip rather than
            // an identifier nobody can read.
            offline,
        ).forEach { active ->
            assertThat(switcherStateOf(active)).isEqualTo(SourceSwitcherState())
        }
    }

    // ---- a change of source, as the shell hears it ----------------------------

    @Test
    fun `opening the application is not a switch, and neither is a refresh`() = runTest {
        val states = flowOf(
            ActiveSourceState.Loading,
            selected(a, listOf(a, b)),
            // The same source, re-read: a sibling finished importing.
            selected(a, listOf(a, b.copy(status = SourceStatus.READY))),
        )

        assertThat(states.sourceSwitches().toList()).isEmpty()
    }

    @Test
    fun `choosing another source is one switch`() = runTest {
        val states = MutableStateFlow<ActiveSourceState>(selected(a, listOf(a, b)))

        states.sourceSwitches().test {
            states.value = selected(b, listOf(a, b))
            assertThat(awaitItem()).isEqualTo(b.key)

            states.value = selected(a, listOf(a, b))
            assertThat(awaitItem()).isEqualTo(a.key)
        }
    }

    @Test
    fun `a deletion elsewhere is a switch when the choice lands, not while it is asked`() = runTest {
        val states = MutableStateFlow<ActiveSourceState>(selected(a, listOf(a, b, c)))

        states.sourceSwitches().test {
            states.value = ActiveSourceState.NeedsChoice(listOf(b, c))
            expectNoEvents()

            states.value = selected(c, listOf(b, c))
            assertThat(awaitItem()).isEqualTo(c.key)
        }
    }

    @Test
    fun `losing the last source is said once, and only to an account that had one`() = runTest {
        val never = flowOf(ActiveSourceState.Loading, ActiveSourceState.None)
        assertThat(never.lastSourceLosses().toList()).isEmpty()

        val lost = flowOf(
            ActiveSourceState.Loading,
            selected(a, listOf(a)),
            ActiveSourceState.None,
            // Re-read, still empty: the user is already on the form.
            ActiveSourceState.None,
        )
        assertThat(lost.lastSourceLosses().toList()).hasSize(1)
    }

    @Test
    fun `an unreachable server is not a lost source, and another account starts over`() = runTest {
        val unreachable = flowOf(selected(a, listOf(a)), ActiveSourceState.Unavailable)
        assertThat(unreachable.lastSourceLosses().toList()).isEmpty()

        // Somebody else signs in on the same television with an empty account.
        // That is a launch on "no source", which `AppStartDecision` already
        // handles — not the previous person's last source disappearing.
        val nextAccount = flowOf(
            selected(a, listOf(a)),
            ActiveSourceState.Loading,
            ActiveSourceState.None,
        )
        assertThat(nextAccount.lastSourceLosses().toList()).isEmpty()
    }

    // ---- helpers -----------------------------------------------------------

    private fun selected(source: Source, all: List<Source>) =
        ActiveSourceState.Selected(source.key, source, all)

    private val Source.key: String get() = id.toString()

    private fun source(label: String, status: SourceStatus = SourceStatus.READY) = Source(
        id = UUID.nameUUIDFromBytes(label.toByteArray()),
        label = label,
        kind = SourceKind.M3U_URL,
        status = status,
        autoSync = true,
    )
}
