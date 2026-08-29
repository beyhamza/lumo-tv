package tv.lumo.android.core.data.model

import tv.lumo.android.core.common.log.Redact

/**
 * A film, as a screen wants one.
 *
 * The reasons this exists rather than the generated model or the Room entity are
 * the ones written on [Channel], and they are not repeated: one type at the
 * boundary, and the two mappings side by side where a forgotten field shows up in
 * a diff.
 *
 * **Not a [Channel] with extra fields.** A channel is played; a film is *chosen*,
 * and nobody chooses without a poster, a year and a running time. Six more
 * nullable properties on `Channel` would be six nulls on the fifteen thousand
 * rows of an ordinary channel list, and a `Channel` that a grid had to interrogate
 * to know which kind of card to draw.
 *
 * **No `streamUrl`**, here or in the cache or in any list — see
 * [VodPlaybackTarget].
 */
data class VodItem(
    val id: String,
    val sourceId: String,
    val categoryId: String?,
    val name: String,
    /**
     * The poster the user's own source advertises, or null. Lumo ships no bundled
     * artwork and no fallback poster of its own (CLAUDE.md, règle 2): a card with
     * no poster shows its title, never a picture of ours standing in for one of
     * theirs.
     */
    val posterUrl: String?,
    /** Release year, when the source states one. Many do not. */
    val year: Int?,
    /**
     * Running time in seconds, and null far more often than not. A screen that
     * needs it to decide whether a film was watched to the end has to cope
     * without it.
     */
    val durationSeconds: Int?,
    /**
     * Whatever the source calls a rating, verbatim — `7.4`, `PG-13` and `★★★★`
     * all occur. Text, because turning it into a number would mean deciding what
     * the provider meant.
     */
    val rating: String?,
    /**
     * The synopsis, and **null in every listing**.
     *
     * Not an oversight and not laziness: on an Xtream panel a synopsis is one
     * HTTP call *per film*, so it is fetched when somebody opens a film and never
     * when they scroll past a thousand. A grid renders without it; a detail
     * screen reads `VodRepository.film`, which fills it in.
     */
    val plot: String?,
    val isAdult: Boolean,
)

/**
 * What the player needs to open one film, and nothing that outlives it.
 *
 * A separate type from [PlaybackTarget] rather than a shared one with a renamed
 * identifier: the two point at two different tables, the contract keeps them
 * apart for that reason, and a player being handed "an id" it cannot name is how
 * a channel gets looked up in the film table.
 *
 * The [toString] is not decoration. The generated `VodPlaybackInfo` is a
 * `data class`, a `data class` prints every property it has, and a stream URL
 * carries the user's panel credentials in its path on most Xtream providers
 * (AGENTS.md §5). The whole argument is written on [PlaybackTarget]; it applies
 * here word for word.
 *
 * **What a player must do differently with this one.** It is a progressive file,
 * not an HLS manifest. Seeking works only if the user's server answers `Range`
 * requests, and many do not — so a player finds that out on the first attempt and
 * says so, rather than drawing a scrubber that does nothing.
 */
data class VodPlaybackTarget(
    val vodItemId: String,
    /** **Sensitive.** Opened by the player, never logged, never persisted. */
    val streamUrl: String,
    /** The User-Agent the source requires, or null for the player's default. */
    val userAgent: String?,
    /**
     * Simultaneous streams the *user's own* subscription allows. A film counts
     * against that ceiling exactly as a channel does, and the message that says
     * so has to name whose rule it is (US-09).
     */
    val maxConnections: Int?,
    /** Epoch milliseconds, or null when the panel issues no expiry. */
    val expiresAtMillis: Long?,
) {
    override fun toString(): String =
        "VodPlaybackTarget(vodItemId=$vodItemId, streamUrl=${Redact.url(streamUrl)}, " +
            "userAgent=$userAgent, maxConnections=$maxConnections, " +
            "expiresAtMillis=$expiresAtMillis)"
}
