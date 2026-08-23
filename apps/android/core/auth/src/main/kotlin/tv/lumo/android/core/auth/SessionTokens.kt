package tv.lumo.android.core.auth

/**
 * What the device holds after signing in.
 *
 * Deliberately not the generated `AuthSession` model: that one belongs to the
 * contract and carries a `User`. This is the persistence shape, and the fewer
 * fields it has, the less there is to leak.
 *
 * [toString] is overridden because the default would print both tokens, and a
 * data class ends up in a crash report sooner or later (AGENTS.md §5).
 */
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch milliseconds. Used only as a hint; the server decides. */
    val accessTokenExpiresAt: Long,
    val userId: String,
    val deviceId: String,
) {
    override fun toString(): String =
        "SessionTokens(userId=$userId, deviceId=$deviceId, expiresAt=$accessTokenExpiresAt)"
}
