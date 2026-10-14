package tv.lumo.android.core.auth

/**
 * Something on the device that belongs to the account whose session just ended.
 *
 * `BUG-R020-01-01`: signing out dropped the session and nothing else. The next
 * account to activate the same television found the previous one's favourites,
 * history and cached catalogue still on disk — hidden by the screens, but there.
 * "No data of A left once B is on the device" is the release lock `R020-01`.
 *
 * `core:auth` does not know what is cached where, and should not: the catalogue
 * lives in `core:database`, the active source in `core:data`. So each module that
 * keeps account data contributes one of these to a Hilt set, and [SessionManager]
 * runs them all whenever a session ends — on a sign-out, and when the server
 * refuses the refresh token, which ends the session just as surely.
 */
fun interface SessionEndCleaner {
    suspend fun onSessionEnded()
}
