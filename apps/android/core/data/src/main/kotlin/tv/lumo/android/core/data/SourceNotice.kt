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
}

/**
 * The notice for one source, or null when there is nothing to say.
 *
 * `READY`, a source that is unknown — a choice remembered offline — and a status
 * newer than this build all say nothing, and saying nothing is the safe one of
 * the three: an unreachable server proves nothing about the source (US-018).
 */
fun Source?.notice(): SourceNotice? = when (this?.status) {
    SourceStatus.PENDING, SourceStatus.SYNCING ->
        SourceNotice.Refreshing(step = syncStep, hasCatalogue = lastSyncedAt != null)

    SourceStatus.ERROR -> SourceNotice.Failed(code = errorCode, hasCatalogue = lastSyncedAt != null)

    else -> null
}

/** The notice of the source being browsed. See [notice]. */
fun ActiveSourceState.notice(): SourceNotice? =
    (this as? ActiveSourceState.Selected)?.source.notice()

/**
 * The four strings of a notice, as resources.
 *
 * Resolved here rather than in each screen so that the home screen and the three
 * grids cannot word one state four ways. The composables that draw it live in
 * `core:designsystem`, which knows nothing about sources and takes plain strings.
 *
 * @param hint the quieter line under the message, or null.
 * @param failed whether the notice is an error — it colours the title and, on a
 * television, decides whether the notice carries a focus stop at all.
 */
data class SourceNoticeWording(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val hint: Int?,
    @StringRes val action: Int,
    val failed: Boolean,
)

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
}
