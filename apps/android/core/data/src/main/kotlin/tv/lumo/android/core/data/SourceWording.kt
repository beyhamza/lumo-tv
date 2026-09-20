package tv.lumo.android.core.data

import androidx.annotation.StringRes
import tv.lumo.android.network.generated.model.IngestionErrorCode
import tv.lumo.android.network.generated.model.SyncStep

/**
 * The words for what a source is doing, and for why it failed.
 *
 * <h2>Why a data module maps an enum to a string</h2>
 *
 * These two functions were private to `feature:source`, which was the only screen
 * that spoke about a synchronisation. The home screen now does too (US-017): a
 * source that is importing shows its real step there, and one that failed shows
 * why. A feature may not import another, and the alternative — a second mapping
 * with a second set of sentences — is how the same failure ends up explained two
 * ways in one application. The enums are this module's vocabulary already
 * (`core:network` is its one `api` dependency), so their wording sits beside them.
 */

/**
 * The phases, as the server actually distinguishes them.
 *
 * Null is a source the server has accepted but not started, which is a real state
 * and gets its own line rather than an empty one.
 *
 * **The `else` is not laziness, and it is not the web's choice.** The web makes
 * its own mapping exhaustive so a new phase fails the build; here the branch is
 * kept, because a `when` over a generated enum can also meet a value from a
 * *server* newer than the installed application, which no compiler can catch. Two
 * different risks, two different answers — and every phase this build knows about
 * still gets its own line above.
 */
@StringRes
fun SyncStep?.labelRes(): Int = when (this) {
    SyncStep.CONNECTING -> R.string.core_data_sync_step_connecting
    SyncStep.AUTHENTICATED -> R.string.core_data_sync_step_authenticated
    SyncStep.PARSING_CHANNELS -> R.string.core_data_sync_step_parsing
    SyncStep.PARSING_VOD -> R.string.core_data_sync_step_parsing_vod
    SyncStep.PARSING_SERIES -> R.string.core_data_sync_step_parsing_series
    SyncStep.FETCHING_EPG -> R.string.core_data_sync_step_epg
    // Includes a phase newer than this build: the honest answer is that it
    // started, which is true of every phase there could be.
    else -> R.string.core_data_sync_step_pending
}

/**
 * One sentence per ingestion code, and never a shared one.
 *
 * The contract forbids a generic message on this surface, and the reason is
 * visible in the sentences themselves: they send the reader to different places.
 */
@StringRes
fun IngestionErrorCode?.messageRes(): Int = when (this) {
    IngestionErrorCode.SOURCE_AUTH_FAILED -> R.string.core_data_ingestion_auth_failed
    IngestionErrorCode.SOURCE_UNREACHABLE -> R.string.core_data_ingestion_unreachable
    IngestionErrorCode.SOURCE_INVALID_FORMAT -> R.string.core_data_ingestion_invalid_format
    IngestionErrorCode.SOURCE_EMPTY -> R.string.core_data_ingestion_empty
    IngestionErrorCode.SOURCE_TOO_LARGE -> R.string.core_data_ingestion_too_large
    IngestionErrorCode.SOURCE_MAX_CONNECTIONS -> R.string.core_data_ingestion_max_connections
    IngestionErrorCode.SOURCE_EXPIRED -> R.string.core_data_ingestion_expired
    else -> R.string.core_data_ingestion_unexpected
}
