package tv.lumo.android.core.data

import androidx.annotation.StringRes
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceStatus
import tv.lumo.android.network.generated.model.SyncStep

/**
 * What a screen says about the source **over** what it shows, or nothing.
 *
 * <h2>Why it moved down from `feature:home`</h2>
 *
 * It was `HomeNotice`, and the home screen was the only one that kept its content
 * on display while a source refreshed. Since lot C4 the three catalogue grids do
 * too (US-024): a catalogue that exists is browsable in every status, and what
 * the status has to say becomes a notice above the grid. Four screens saying the
 * same thing about the same source is one type, and a feature may not import
 * another — so it lives here, beside the wording of the enums it carries.
 *
 * <h2>A notice and never a step</h2>
 *
 * The decisions are explicit that a source which is refreshing, or whose last
 * attempt failed, keeps showing what is already there. Neither case replaces the
 * content; both sit over it.
 */
sealed interface SourceNotice {

    /**
     * `PENDING` or `SYNCING`.
     *
     * @param step the phase the server reports, null when it has accepted the
     * source and not started. Worded by [labelRes]: the real step, never a
     * percentage (US-024).
     * @param hasCatalogue whether a previous ingestion succeeded. It decides the
     * line under the step: with a catalogue, that it stays browsable and that
     * playback waits for the end of the refresh (C4, D2); without one, nothing
     * about a catalogue that does not exist.
     */
    data class Refreshing(val step: SyncStep?, val hasCatalogue: Boolean = false) : SourceNotice

    /**
     * `ERROR`.
     *
     * @param code why, as the server states it. Null and unknown codes both get
     * the generic sentence from [messageRes] — a code newer than this build must
     * degrade, not crash.
     * @param hasCatalogue whether a previous ingestion succeeded, in which case
     * what is on screen **may be out of date** and the notice says so.
     */
    data class Failed(val code: IngestionErrorCode?, val hasCatalogue: Boolean = false) : SourceNotice

    /**
     * The server could not be reached, and the source on screen is a choice this
     * device remembered on its own (US-024, "Indisponibilité et hors ligne").
     *
     * Nothing is said about the source itself — an outage proves nothing about
     * it (US-018) — only that what is on screen may be older than the server's
     * copy. Its two ways on are the two the story names: try again, which reads
     * the list once more, and change source.
     */
    data object Unreached : SourceNotice
}

/**
 * The notice for one source, or null when there is nothing to say.
 *
 * `READY`, and a status newer than this build, say nothing. An unknown source —
 * a choice remembered offline — is null here too: what it has to say is not
 * about the source but about the server, and that is [ActiveSourceState.notice].
 */
fun Source?.notice(): SourceNotice? = when (this?.status) {
    SourceStatus.PENDING, SourceStatus.SYNCING ->
        SourceNotice.Refreshing(step = syncStep, hasCatalogue = lastSyncedAt != null)

    SourceStatus.ERROR -> SourceNotice.Failed(code = errorCode, hasCatalogue = lastSyncedAt != null)

    else -> null
}

/**
 * The notice of the source being browsed. See [notice].
 *
 * A selection whose `Source` is null is the one shape that says something the
 * source cannot: the list could not be fetched and this device is browsing from
 * its own memory. That is [SourceNotice.Unreached], and it is decided here so the
 * home screen and the three grids cannot come to disagree about it.
 */
fun ActiveSourceState.notice(): SourceNotice? {
    val selected = this as? ActiveSourceState.Selected ?: return null
    return if (selected.source == null) SourceNotice.Unreached else selected.source.notice()
}

/**
 * The strings of a notice, as resources.
 *
 * Resolved here rather than in each screen so that the home screen and the three
 * grids cannot word one state four ways. The composables that draw it live in
 * `core:designsystem`, which knows nothing about sources and takes plain strings.
 *
 * @param hint the quieter line under the message, or null.
 * @param action the way to the screen where a source is looked after — "My
 * sources", or "Change source" during an outage, which leads to the same place.
 * @param failed whether the notice is an error — it colours the title.
 * @param retry the label of a second control that reads the list of sources
 * again, or null. Only an outage has one: retrying a refresh that is running,
 * or an ingestion the provider refused, would be a button that changes nothing.
 */
data class SourceNoticeWording(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val hint: Int?,
    @StringRes val action: Int,
    val failed: Boolean,
    @StringRes val retry: Int? = null,
) {

    /**
     * Whether the notice carries a control at all on a television, where a stop
     * with nothing behind it is a dead end on the way `UP`. A refresh in progress
     * is text; a failure and an outage each have something to press.
     */
    val actionable: Boolean
        get() = failed || retry != null
}

fun SourceNotice.wording(): SourceNoticeWording = when (this) {
    is SourceNotice.Refreshing -> SourceNoticeWording(
        title = R.string.core_data_notice_refreshing_title,
        message = step.labelRes(),
        hint = if (hasCatalogue) {
            R.string.core_data_notice_refreshing_hint
        } else {
            R.string.core_data_notice_importing_hint
        },
        action = R.string.core_data_notice_open_sources,
        failed = false,
    )

    is SourceNotice.Failed -> SourceNoticeWording(
        title = R.string.core_data_notice_failed_title,
        message = code.messageRes(),
        hint = if (hasCatalogue) R.string.core_data_notice_failed_stale else null,
        action = R.string.core_data_notice_open_sources,
        failed = true,
    )

    // Not an error of the source, so not in the error colour: nothing is wrong
    // with what is on screen except its age, and red over a working catalogue
    // would say otherwise.
    SourceNotice.Unreached -> SourceNoticeWording(
        title = R.string.core_data_notice_unreached_title,
        message = R.string.core_data_notice_unreached_message,
        hint = null,
        action = R.string.core_data_notice_change_source,
        failed = false,
        retry = R.string.core_data_catalogue_retry,
    )
}
