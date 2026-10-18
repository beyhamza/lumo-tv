package tv.lumo.android.core.player

import java.util.Locale
import java.util.MissingResourceException
import tv.lumo.android.core.common.playback.TrackPreferences

/**
 * Which tracks a stream starts with, and what a choice in the menu remembers (S13-02).
 *
 * Pure, and in one file, because these are the rules of
 * `docs/design/0.2.0/player-settings-cases.md` ("Pistes et sous-titres") and
 * they have to hold on every surface at once:
 *
 * - a missing audio language falls back to the default playable track, without
 *   touching the preference;
 * - subtitles off means no text track, a forced one included;
 * - subtitles on looks for the preferred language — a complete track first, then
 *   a forced one — and **never** turns on another language;
 * - between several tracks of one language, the one marked default, then the
 *   container's order;
 * - a track without a language is a local choice, never a preference;
 * - nothing here ever carries a track id from one stream to another.
 */

/**
 * The tracks to start a stream with.
 *
 * @property audioId the audio track to play, or null when there is no playable
 *   one to choose — the player keeps its own selection and the menu says the
 *   rest; nothing is invented (PS rules, "annoncer l'impossibilité").
 * @property subtitleId the text track to show, or null for none.
 * @property subtitleLanguageUnavailable subtitles were wanted and the preferred
 *   language is not in this stream (or not showable here).
 */
data class InitialTracks(
    val audioId: String?,
    val subtitleId: String?,
    val subtitleLanguageUnavailable: Boolean,
)

fun chooseInitialTracks(
    audio: List<AudioTrack>,
    subtitles: List<SubtitleTrack>,
    preferences: TrackPreferences,
): InitialTracks = InitialTracks(
    audioId = chooseAudio(audio, preferences.audioLanguage)?.id,
    subtitleId = chooseSubtitle(subtitles, preferences)?.id,
    subtitleLanguageUnavailable = preferences.subtitlesEnabled &&
        preferences.subtitleLanguage.normalizedLanguage() != null &&
        chooseSubtitle(subtitles, preferences) == null,
)

private fun chooseAudio(audio: List<AudioTrack>, language: String?): AudioTrack? {
    val playable = audio.filter { it.playable }
    if (playable.isEmpty()) return null

    val matching = playable.filter { sameLanguage(it.language, language) }
    return matching.firstOrNull { it.isDefault }
        ?: matching.firstOrNull()
        // The language is absent: the stream's own default, without touching the
        // preference — the next film may well have it.
        ?: playable.firstOrNull { it.isDefault }
        ?: playable.firstOrNull { it.selected }
        ?: playable.first()
}

private fun chooseSubtitle(subtitles: List<SubtitleTrack>, preferences: TrackPreferences): SubtitleTrack? {
    if (!preferences.subtitlesEnabled) return null
    val language = preferences.subtitleLanguage.normalizedLanguage() ?: return null

    val matching = subtitles.filter { it.playable && sameLanguage(it.language, language) }
    val complete = matching.filterNot { it.forced }
    val forced = matching.filter { it.forced }

    return complete.firstOrNull { it.isDefault }
        ?: complete.firstOrNull()
        ?: forced.firstOrNull { it.isDefault }
        ?: forced.firstOrNull()
}

/**
 * The preference after somebody picked [track] in the menu.
 *
 * A track with a language becomes the preferred language; one without stays a
 * choice for this stream only (PS-06), and the preference is returned unchanged.
 */
fun TrackPreferences.afterAudioChoice(track: AudioTrack): TrackPreferences {
    val language = track.language.normalizedLanguage() ?: return this
    return copy(audioLanguage = language)
}

/**
 * The preference after somebody picked [track] — or "Off", as null — in the menu.
 *
 * Off keeps the language, so turning subtitles back on finds it (settings.md,
 * "Lecture et réseau"). A track without a language is a local choice: it does
 * not even switch the preference on, because the next stream could only answer
 * it with another language, which the rules forbid.
 */
fun TrackPreferences.afterSubtitleChoice(track: SubtitleTrack?): TrackPreferences {
    if (track == null) return copy(subtitlesEnabled = false)
    val language = track.language.normalizedLanguage() ?: return this
    return copy(subtitlesEnabled = true, subtitleLanguage = language)
}

/** Whether two declared languages are the same language: `fr`, `fre`, `fra`, `fr-CA`. */
fun sameLanguage(a: String?, b: String?): Boolean {
    val left = a.normalizedLanguage() ?: return false
    val right = b.normalizedLanguage() ?: return false
    return left == right
}

/**
 * One spelling per language, or null when the code says "no language".
 *
 * ISO 639-2/T three-letter code, lower case. The bibliographic codes muxers
 * still write (`fre`, `ger`) are mapped by hand: the platform only knows the
 * terminology ones. `und`, `mul`, `zxx`, `mis` and the private-use range are no
 * language at all — a track declaring them is a track without a language.
 */
fun String?.normalizedLanguage(): String? {
    val primary = this?.trim()
        ?.replace('_', '-')
        ?.substringBefore('-')
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    if (primary in NO_LANGUAGE || primary.isPrivateUse()) return null

    BIBLIOGRAPHIC[primary]?.let { return it }
    if (primary.length == 3) return primary
    return try {
        Locale.forLanguageTag(primary).isO3Language.takeIf { it.isNotEmpty() } ?: primary
    } catch (_: MissingResourceException) {
        primary
    }
}

private fun String.isPrivateUse(): Boolean =
    length == 3 && this[0] == 'q' && this[1] in 'a'..'t'

private val NO_LANGUAGE = setOf("und", "mul", "zxx", "mis")

private val BIBLIOGRAPHIC = mapOf(
    "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
    "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
    "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
    "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
)
