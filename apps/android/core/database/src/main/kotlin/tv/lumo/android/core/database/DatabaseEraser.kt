package tv.lumo.android.core.database

import javax.inject.Inject

/**
 * Empties every table of [LumoDatabase].
 *
 * The one operation on the database as a whole that another module needs:
 * removing an account's data when its session ends (`BUG-R020-01-01`). Exposed as
 * this class rather than as the database itself so that Room stays an
 * implementation detail of this module.
 *
 * Blocking, and Room refuses it on the main thread: call it from an IO dispatcher.
 */
class DatabaseEraser @Inject internal constructor(
    private val database: LumoDatabase,
) {
    fun eraseAll() = database.clearAllTables()
}
