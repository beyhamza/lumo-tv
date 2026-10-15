package tv.lumo.android.core.data

/**
 * Why a film or series sheet has nothing to show (BUG-S11-06-04).
 *
 * A sheet renders from the cache and fetches the content behind it. When the
 * cache has no row — a sheet opened from My list, a deep link, a rail — and the
 * fetch fails, the screen has no title, no poster and no source to read the
 * list membership from. Leaving it as an empty skeleton says nothing; this says
 * which of the two explanations applies, and both come with a retry.
 */
enum class SheetUnavailable {
    /** No network: it will load once the connection is back. */
    Offline,

    /** The server or the provider did not give it. */
    Failed,
    ;

    companion object {
        /**
         * The state a sheet shows, or null when it has something to show.
         *
         * @param hasContent whether the cached row is there — a sheet with a row
         *   is a sheet, whatever the last fetch did (offline, it stays readable).
         * @param lastLoad the outcome of the last fetch, or null while none has
         *   finished — a fetch in flight is not a failure.
         */
        fun of(hasContent: Boolean, lastLoad: LumoResult<*>?): SheetUnavailable? = when {
            hasContent -> null
            lastLoad !is LumoResult.Failure -> null
            lastLoad.error is LumoError.Offline -> Offline
            else -> Failed
        }
    }
}
