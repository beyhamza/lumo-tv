package tv.lumo.android.core.player.ui

import java.util.Locale
import tv.lumo.android.core.designsystem.component.LumoAudioTrackChoice
import tv.lumo.android.core.player.AudioTrack
import tv.lumo.android.core.player.channelLayout
import tv.lumo.android.core.player.codecName
import tv.lumo.android.core.player.spokenName

/**
 * Turns the tracks of a stream into rows a picker can draw.
 *
 * <h2>Why it is here and not in the three screens that need it</h2>
 *
 * Three features play something — live, vod, series — and each has two surfaces.
 * The rule in `settings.gradle.kts` is that a feature never depends on another
 * feature and that shared behaviour moves **down** into `core/`; six copies of
 * the same label logic is exactly what that rule exists to prevent, and this is
 * the module that already knows what an [AudioTrack] is.
 *
 * <h2>The words come from the caller</h2>
 *
 * [unnamed] and [unsupported] are passed in rather than read here, because
 * reading them means string resources and a feature's own `R`. What this
 * function owns is the *decision* about which of them applies; what it never
 * owns is the sentence.
 *
 * @param unnamed a name for a track that declares neither language nor label —
 *                the argument is its position in the list, counted from one,
 *                which is the only thing left to call it by
 * @param unsupported what to put on the second line of a track this device
 *                cannot decode. It replaces the codec rather than joining it: a
 *                row saying "Dolby Digital · this device cannot decode it" reads
 *                as a contradiction, and the second half is the half that matters
 */
fun List<AudioTrack>.asChoices(
    unnamed: (position: Int) -> String,
    unsupported: String,
    locale: Locale = Locale.getDefault(),
): List<LumoAudioTrackChoice> = mapIndexed { index, track ->
    LumoAudioTrackChoice(
        id = track.id,
        label = track.spokenName(locale) ?: unnamed(index + 1),
        detail = if (track.playable) track.describe() else unsupported,
        selected = track.selected,
        enabled = track.playable,
    )
}

/**
 * "Dolby Digital 5.1", "AAC", or nothing at all.
 *
 * <p>Nothing at all is the common case and it is drawn as no second line rather
 * than as an empty one — a track about which nothing is known should look like a
 * plain choice, not like a choice with something missing.
 */
private fun AudioTrack.describe(): String? =
    listOfNotNull(codecName(), channelLayout())
        .joinToString(" ")
        .takeIf { it.isNotEmpty() }
