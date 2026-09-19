package tv.lumo.android.core.data

import tv.lumo.android.network.generated.model.Source

/**
 * Which of the account's sources this device is browsing (US-018).
 *
 * <h2>A device's answer, not the account's</h2>
 *
 * The choice is stored on the device and nowhere else. Changing source on the
 * television must not move the phone, so there is nothing to synchronise and the
 * contract carries no such field — which is also why this type lives here and
 * not in `openapi.yaml`.
 *
 * <h2>Four answers, and none of them is a guess</h2>
 *
 * The one thing this type refuses to express is "some source or other". With
 * several sources and no recorded choice the answer is [NeedsChoice], and the
 * person is asked: picking the first of the list on their behalf is how somebody
 * ends up looking at the wrong subscription's channels without ever having been
 * told there was a decision.
 */
sealed interface ActiveSourceState {

    /** Nothing is known yet: the session or the list is still being read. */
    data object Loading : ActiveSourceState

    /** The account has no source at all. The way forward is adding one. */
    data object None : ActiveSourceState

    /**
     * Several sources, and no valid choice recorded on this device.
     *
     * Reached on a second device that has never chosen, and after the active
     * source was deleted with more than one left (US-024).
     */
    data class NeedsChoice(val sources: List<Source>) : ActiveSourceState

    /**
     * The source this device browses.
     *
     * @param source null when the choice is known but the list could not be
     * fetched — offline, typically. The identifier comes from the device, so the
     * cached catalogue can still be read; the name and the status come from the
     * server, so they cannot be shown. That asymmetry is the point: an
     * unreachable server proves nothing about the source (US-024), and dropping
     * the choice over it would send somebody on a train to "add a source".
     * @param sources every source of the account, for the switcher. Empty when
     * [source] is null, for the same reason.
     */
    data class Selected(
        val sourceId: String,
        val source: Source?,
        val sources: List<Source>,
    ) : ActiveSourceState

    /**
     * The list could not be fetched and this device holds no choice.
     *
     * Kept apart from [None] on purpose. [None] is a fact — the server said the
     * account has no source — and it sends the user to a form. This is an
     * absence of facts, and the only honest thing to do with it is to try again.
     */
    data object Unavailable : ActiveSourceState
}

/** The identifier the catalogue screens read, or null when there is none to read. */
val ActiveSourceState.selectedSourceId: String?
    get() = (this as? ActiveSourceState.Selected)?.sourceId

/** Every source the state knows about, for a screen that lists them. */
val ActiveSourceState.knownSources: List<Source>
    get() = when (this) {
        is ActiveSourceState.NeedsChoice -> sources
        is ActiveSourceState.Selected -> sources
        else -> emptyList()
    }

/**
 * Decides [ActiveSourceState], as a pure function of what is known (US-018).
 *
 * Kept free of coroutines, storage and Android so that every branch of the rule
 * can be pinned by a test that reads like the acceptance criteria — the
 * repository around it only fetches, stores and calls this.
 */
object ActiveSourceResolver {

    /**
     * What to browse, given a list the server **successfully** returned.
     *
     * - no source: [ActiveSourceState.None];
     * - the recorded choice is in the list: that one;
     * - no valid choice and exactly one source: that one — there is nothing to
     *   ask, and the caller records it so the answer survives a second source
     *   being added later (US-024: an additional source does not steal the
     *   selection);
     * - no valid choice and several sources: [ActiveSourceState.NeedsChoice].
     *
     * A recorded choice that is absent from a successful list is a deleted
     * source. That is one of the only two proofs of deletion the product accepts
     * (c4-previous-catalogue.md §P6), and it is why this function must never be
     * called with a list that was not actually fetched.
     */
    fun resolve(sources: List<Source>, storedId: String?): ActiveSourceState {
        if (sources.isEmpty()) return ActiveSourceState.None

        val chosen = sources.firstOrNull { it.id.toString() == storedId }
            ?: sources.singleOrNull()

        return if (chosen != null) {
            ActiveSourceState.Selected(chosen.id.toString(), chosen, sources)
        } else {
            ActiveSourceState.NeedsChoice(sources)
        }
    }

    /**
     * What to browse when the list could **not** be fetched.
     *
     * A network error, a timeout or a `5xx` proves nothing (US-018), so the
     * recorded choice is kept exactly as it is. What was already resolved for
     * this account stays on screen — it is richer than anything that can be
     * rebuilt from an identifier alone — and only a state that knew nothing falls
     * back to the bare identifier, or to [ActiveSourceState.Unavailable] when
     * there is not even that.
     *
     * @param previous what was resolved earlier **for the same account**, or
     * [ActiveSourceState.Loading] when nothing was.
     */
    fun withoutList(previous: ActiveSourceState, storedId: String?): ActiveSourceState = when {
        previous !is ActiveSourceState.Loading && previous !is ActiveSourceState.Unavailable -> previous
        storedId != null -> ActiveSourceState.Selected(storedId, source = null, sources = emptyList())
        else -> ActiveSourceState.Unavailable
    }

    /**
     * What to browse once the server has said, by name, that a source is gone.
     *
     * `404 SOURCE_NOT_FOUND` is the other proof of deletion, and it can arrive
     * while the list itself is unreachable. So the source is removed from what
     * was known and the rule of [resolve] is applied to the rest: one left is
     * selected, several ask, none sends to "add a source" (US-024).
     *
     * [known] is the freshest list available — fetched again when the server
     * answers, otherwise the one already on display. A list that still carries
     * the deleted source, which a read a moment after the deletion can, is
     * filtered here rather than trusted.
     */
    fun withoutSource(
        known: List<Source>,
        storedId: String?,
        goneId: String,
    ): ActiveSourceState = resolve(
        sources = known.filterNot { it.id.toString() == goneId },
        storedId = storedId.takeUnless { it == goneId },
    )
}
