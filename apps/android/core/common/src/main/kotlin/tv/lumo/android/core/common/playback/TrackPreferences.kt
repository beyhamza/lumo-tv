package tv.lumo.android.core.common.playback

/**
 * What somebody wants to hear and read, in languages — never in track positions (S13-02).
 *
 * Here rather than in `core:player` or `core:data` because both need it and
 * neither may depend on the other: `core:data` stores it per account and per
 * device, `core:player` applies it to whatever stream opens next.
 *
 * **Languages, not identifiers.** A track id names a slot in one container and
 * means nothing in the next one (PS-06); a language is the only thing that can
 * follow somebody from one film to another.
 *
 * @property audioLanguage the preferred spoken language, as a BCP 47 or ISO 639
 *   code. Null when nothing is known yet — the stream's default track plays.
 * @property subtitlesEnabled whether subtitles are wanted at all. Off by default
 *   (Q6). Off means **no** text track, a forced one included (PS-05).
 * @property subtitleLanguage the language subtitles are wanted in. Kept while
 *   subtitles are off, so turning them back on finds it again.
 */
data class TrackPreferences(
    val audioLanguage: String? = null,
    val subtitlesEnabled: Boolean = false,
    val subtitleLanguage: String? = null,
) {
    companion object {
        /** No preference at all: the stream's own defaults, subtitles off. */
        val NONE = TrackPreferences()
    }
}
