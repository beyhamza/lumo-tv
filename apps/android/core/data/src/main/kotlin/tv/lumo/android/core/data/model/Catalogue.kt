package tv.lumo.android.core.data.model

import tv.lumo.android.core.data.LumoError

/**
 * A channel, as a screen wants one.
 *
 * <h2>Why this is not the generated model, and not the Room entity either</h2>
 *
 * It is both of them. A channel reaches a screen from the network on the first
 * run and from the cache on every train journey after it, and those two arrive
 * as different Kotlin types — `network.generated.model.Channel`, with `UUID`
 * identifiers, and `ChannelEntity`, with `String` ones. A screen that had to
 * know which of the two it was holding would branch on the origin of its data in
 * order to render a name, which is the branch this module exists to remove.
 *
 * So there is one type at the boundary, and the two mappings live next to each
 * other where a missing field is visible in a diff.
 *
 * **No `streamUrl`.** Not here, not in the cache, not in any list. It is
 * credential-bearing and the contract emits it from exactly one operation, one
 * channel at a time — see [PlaybackTarget].
 */
data class Channel(
    val id: String,
    val sourceId: String,
    val categoryId: String?,
    val name: String,
    /**
     * The logo the user's own playlist advertises, or null. Lumo ships no
     * bundled artwork and no fallback of its own (AGENTS.md §1).
     */
    val logoUrl: String?,
    /**
     * The number the provider assigns, and **not** `position`.
     *
     * `position` is a display index reassigned at every ingestion; this is the
     * number the user knows by heart and types on a remote control. Null for the
     * many playlists that carry none.
     */
    val number: Int?,
    /** Definition as the source advertises it — `HD`, `FHD`, `4K` — verbatim. */
    val quality: String?,
    val isAdult: Boolean,
)

/** A category, as a screen wants one. */
data class Category(
    val id: String,
    val name: String,
    /** Rendered next to the name; null when the server did not count. */
    val channelCount: Int?,
)

/**
 * Where an answer came from.
 *
 * US-08 requires the offline case to be *said*, not inferred: a catalogue that
 * silently shows week-old data is the failure a user cannot diagnose, because it
 * looks exactly like a catalogue that is up to date.
 */
enum class DataOrigin {
    /** Straight from the server, this call. */
    Network,

    /** From the device's cache, because the server could not be reached. */
    Cache,
}

/**
 * A value, and whether it is fresh.
 *
 * The pairing is deliberate rather than two separate flows: a screen that reads
 * the data and the freshness from different places will render one against the
 * other at least once, and the banner will be wrong for exactly one frame — the
 * kind of bug that is only ever seen by users.
 */
data class Cached<out T>(
    val value: T,
    val origin: DataOrigin,
    /**
     * Why the cache is being served, when it is. Null when [origin] is
     * [DataOrigin.Network].
     *
     * Kept because "you are offline" and "your session expired" are different
     * sentences, and both end up serving the cache.
     */
    val staleReason: LumoError? = null,
)
