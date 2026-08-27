package tv.lumo.android.feature.source

import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.SyncStep

/**
 * What a source looks like on screen, derived from what the server says it is.
 *
 * A pure function of one `Source` (see [viewOf]), kept apart from the view model
 * so the interesting half of S2-09 can be tested without a coroutine, a clock or
 * a server: which of the four failures is which, and what each one offers as a
 * way out.
 */
sealed interface SourceView {

    /**
     * Ingestion is running, and [step] says how far.
     *
     * The step matters more than it looks. Onboarding waits here — a large
     * playlist takes up to a minute — and a minute of silence is where someone
     * concludes the application is broken and closes it. A named phase says two
     * things a spinner cannot: that it is moving, and how far it got if it stops.
     */
    data class Importing(val step: SyncStep?) : SourceView

    /**
     * Done, and worth counting.
     *
     * US-06 and US-07 both ask for the number of channels found — a success
     * screen that says only "ready" is indistinguishable from one that imported
     * nothing, which is the failure `SOURCE_EMPTY` exists to name.
     */
    data class Ready(
        val channels: Int?,
        val categories: Int?,
        /** Xtream only, and only when the panel gives one. */
        val expiresAt: String?,
        /** Xtream only. How many streams the user's own subscription allows. */
        val maxConnections: Int?,
    ) : SourceView

    /**
     * Failed, with the reason and the way out.
     *
     * [exit] is not decoration. S2-09 asks for four distinct messages **and four
     * distinct exits**, because a message with the wrong button is a message that
     * cannot be acted on: offering "retry" to somebody whose password is wrong
     * makes them press it until they give up.
     */
    data class Failed(val reason: IngestionErrorCode?, val exit: SourceExit) : SourceView
}

/** What the screen offers after a failure. One per kind of mistake. */
enum class SourceExit {

    /**
     * The credentials were refused, so the form comes back with the host and the
     * username still in it and the password empty (US-06).
     *
     * The password cannot be prefilled even in principle: it is write-only in the
     * contract and the API returns it to nobody.
     */
    FixCredentials,

    /**
     * The address was wrong or answered with something that is not a playlist, so
     * the form comes back with the address to correct.
     */
    FixAddress,

    /**
     * Nothing was wrong with what the user typed — the server did not answer, or
     * was busy. Trying the same thing again is the right offer, and the only case
     * where it is.
     */
    Retry,

    /**
     * Nothing on this screen can fix it: an expired subscription, a playlist past
     * the size cap, a playlist with no channel in it. The way out is elsewhere —
     * with the provider, or with a different source — and pretending otherwise
     * with a retry button wastes the user's time.
     */
    None,
}

/**
 * The server's answer, as one of the three things a screen can draw.
 *
 * `PENDING` and `SYNCING` are one state here on purpose. The distinction is the
 * server's — accepted versus started — and it is not a distinction a user can act
 * on; showing two different waiting screens for it would be describing our
 * implementation rather than their import.
 */
fun viewOf(source: Source): SourceView = when (source.status) {
    SourceStatus.PENDING, SourceStatus.SYNCING -> SourceView.Importing(source.syncStep)

    SourceStatus.READY -> SourceView.Ready(
        channels = source.channelCount,
        categories = source.categoryCount,
        expiresAt = source.expiresAt?.toLocalDate()?.toString(),
        maxConnections = source.maxConnections,
    )

    SourceStatus.ERROR -> SourceView.Failed(
        reason = source.errorCode,
        exit = exitFor(source.errorCode),
    )
}

/**
 * The way out for each failure.
 *
 * The `else` is required rather than tidy: `IngestionErrorCode` can gain a value
 * within v1, and an older application meeting a newer server should offer the
 * cautious answer — no button — rather than an action that cannot help.
 */
private fun exitFor(code: IngestionErrorCode?): SourceExit = when (code) {
    IngestionErrorCode.SOURCE_AUTH_FAILED -> SourceExit.FixCredentials
    IngestionErrorCode.SOURCE_INVALID_FORMAT -> SourceExit.FixAddress
    // Not the same as a refusal, and the message must not read like one. The
    // address may well be right; the server simply did not answer.
    IngestionErrorCode.SOURCE_UNREACHABLE -> SourceExit.Retry
    IngestionErrorCode.SOURCE_MAX_CONNECTIONS -> SourceExit.Retry
    IngestionErrorCode.SOURCE_EXPIRED -> SourceExit.None
    IngestionErrorCode.SOURCE_EMPTY -> SourceExit.None
    IngestionErrorCode.SOURCE_TOO_LARGE -> SourceExit.None
    else -> SourceExit.None
}
