package tv.lumo.android.core.player

import java.util.Locale

/**
 * How an audio track is named on screen.
 *
 * <h2>Why naming is separate from choosing</h2>
 *
 * [LumoPlayer.audioTracks] passes the container's declarations through
 * untouched, because a wrong reading there would be wrong for every screen at
 * once. But something has to turn `fr` and `audio/ac3` into words a person
 * recognises, and doing it in six screens would mean six versions of it.
 *
 * These are pure functions, so they can be tested without a device — which
 * matters more than usual here, since the inputs are whatever a stranger's
 * muxer wrote.
 */

/**
 * What the track is spoken in, in the reader's own language.
 *
 * <p>The declared language first, rendered by the platform: `fr` becomes
 * "français" for a French reader and "French" for an English one. Then the
 * container's own label, which is often the more useful of the two — muxers
 * write "VF", "VOSTFR", "Commentary" there, and none of that is expressible as a
 * language code.
 *
 * <p><b>Null when neither says anything</b>, and a caller supplies its own words
 * for that. A track called "Track 2" is a caller's sentence, not this module's:
 * the number is a position on a screen, and this file does not know about
 * screens.
 *
 * <p>A code the platform cannot resolve is returned as it was written rather
 * than dropped. `fre`, `qaa`, or a typo tell somebody more than nothing does,
 * and inventing a language for them would be worse than either.
 */
fun AudioTrack.spokenName(locale: Locale = Locale.getDefault()): String? {
    val code = language?.trim()?.takeIf { it.isNotEmpty() }
    if (code != null) {
        val display = Locale.forLanguageTag(code.replace('_', '-'))
            .getDisplayLanguage(locale)
            .takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        if (display != null) return display
    }
    return label?.trim()?.takeIf { it.isNotEmpty() } ?: code
}

/**
 * The trade name of the codec, for a line somebody can act on.
 *
 * <p>"Dolby Digital" rather than `audio/ac3`, because the mime type is an
 * identifier and the trade name is what appears on the box, in the panel's own
 * description and in the sentence that explains why a telephone has no sound for
 * it. This is naming, not deciding: the mapping is fixed, it comes from Media3's
 * own constants, and nothing branches on the result.
 *
 * <p><b>Null for anything unrecognised</b>, and the caller shows nothing rather
 * than a raw mime type. `audio/mp4a-latm` beside a language is noise to everyone
 * who did not write it.
 */
fun AudioTrack.codecName(): String? = when (mimeType?.lowercase(Locale.ROOT)) {
    "audio/ac3" -> "Dolby Digital"
    "audio/eac3", "audio/eac3-joc" -> "Dolby Digital Plus"
    "audio/true-hd" -> "Dolby TrueHD"
    "audio/ac4" -> "Dolby AC-4"
    "audio/vnd.dts" -> "DTS"
    "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr" -> "DTS-HD"
    "audio/mp4a-latm" -> "AAC"
    "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2" -> "MP3"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/flac" -> "FLAC"
    else -> null
}

/**
 * The channel count as people say it: `5.1`, `7.1`, `stéréo` is the caller's word.
 *
 * <p>Only for the layouts that have a conventional written form. Six channels is
 * "5.1" to everybody and eleven channels is not anything, so the second returns
 * null and the caller says nothing rather than "11 canaux", which no one has
 * ever wanted to read.
 */
fun AudioTrack.channelLayout(): String? = when (channelCount) {
    6 -> "5.1"
    8 -> "7.1"
    else -> null
}
