package tv.lumo.android.feature.series

import tv.lumo.android.core.data.WatchlistMembership
import tv.lumo.android.core.data.WatchlistToggleState
import tv.lumo.android.core.designsystem.component.LumoWatchlistButtonState
import tv.lumo.android.core.designsystem.component.LumoWatchlistNote

/**
 * The sheet's "My list" state as the shared button draws it.
 *
 * One sentence at a time, the most pressing first: an unconfirmed change outranks
 * being offline, which outranks a refusal or a failed check.
 */
internal fun WatchlistToggleState.asButtonState() = LumoWatchlistButtonState(
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
        else -> null
    },
    retryable = retryable,
)
