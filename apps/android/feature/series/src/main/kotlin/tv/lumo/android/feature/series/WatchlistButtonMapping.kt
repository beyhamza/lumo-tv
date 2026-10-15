package tv.lumo.android.feature.series

import tv.lumo.android.core.data.SheetUnavailable
import tv.lumo.android.core.data.WatchlistMembership
import tv.lumo.android.core.data.WatchlistToggleState
import tv.lumo.android.core.designsystem.component.LumoWatchlistButtonState
import tv.lumo.android.core.designsystem.component.LumoWatchlistNote

/**
 * The sheet's "My list" state as the shared button draws it.
 *
 * One sentence at a time, the most pressing first: an unconfirmed change outranks
 * being offline, which outranks a refusal or a failed check.
 *
 * @param sheet why the sheet itself could not be loaded, if it could not. The
 *   membership is read for the content's source, which comes from the content:
 *   without it the button never gets an answer, and must still say why it is
 *   disabled (WL-06, BUG-S11-06-04).
 */
internal fun WatchlistToggleState.asButtonState(sheet: SheetUnavailable? = null): LumoWatchlistButtonState {
    val unread = membership == WatchlistMembership.Unknown && !busy
    return LumoWatchlistButtonState(
        inList = when (val membership = membership) {
            WatchlistMembership.Unknown -> null
            WatchlistMembership.Out -> false
            is WatchlistMembership.In -> true
        },
        enabled = enabled,
        note = when {
            unconfirmed -> LumoWatchlistNote.Unconfirmed
            offline -> LumoWatchlistNote.Offline
            rejected -> LumoWatchlistNote.Rejected
            checkFailed -> LumoWatchlistNote.CheckFailed
            unread && sheet == SheetUnavailable.Offline -> LumoWatchlistNote.Offline
            unread && sheet == SheetUnavailable.Failed -> LumoWatchlistNote.CheckFailed
            else -> null
        },
        retryable = retryable || (unread && sheet != null),
    )
}
