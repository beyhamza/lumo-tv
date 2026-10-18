package tv.lumo.android.core.player.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Locale
import tv.lumo.android.core.designsystem.component.LumoAudioTrackChoice
import tv.lumo.android.core.designsystem.component.LumoOptionSection
import tv.lumo.android.core.designsystem.component.LumoPlayerOptionsSheet
import tv.lumo.android.core.designsystem.component.LumoTvPlayerOptionsSheet
import tv.lumo.android.core.player.R
import tv.lumo.android.core.player.SubtitleTrack
import tv.lumo.android.core.player.TrackChoices
import tv.lumo.android.core.player.TrackOptions
import tv.lumo.android.core.player.VideoQuality
import tv.lumo.android.core.player.spokenName

/**
 * The player's "audio, subtitles and quality" menu (S13-01), for the three
 * features that play something.
 *
 * <h2>Only what the stream offers, and nothing made up</h2>
 *
 * - **Audio**: every declared track; one this device cannot decode is shown
 *   dimmed with the reason. A single track says "no other track available".
 * - **Subtitles**: *Off*, then every declared text track; a bitmap format this
 *   product cannot draw is dimmed with the reason. *Forced* is written only when
 *   the container flags it.
 * - **Quality**: *Automatic*, then the variants **only when at least two can be
 *   played** — a badge in the catalogue is not a choice (US-023).
 *
 * The menu opens from the screens only once the stream has declared its tracks
 * ([TrackOptions.known]): before that, "no subtitles" would be a guess.
 */
enum class PlayerOptionGroup { AUDIO, SUBTITLES, QUALITY }

/** Every group: the television's one menu. */
val ALL_OPTION_GROUPS: Set<PlayerOptionGroup> = PlayerOptionGroup.entries.toSet()

/** The phone's first button. Quality is its second. */
val AUDIO_AND_SUBTITLES: Set<PlayerOptionGroup> = setOf(PlayerOptionGroup.AUDIO, PlayerOptionGroup.SUBTITLES)

@Composable
fun PlayerOptionsSheet(
    options: TrackOptions,
    choices: TrackChoices,
    notSaved: Boolean,
    groups: Set<PlayerOptionGroup>,
    onDismiss: () -> Unit,
) {
    LumoPlayerOptionsSheet(
        title = stringResource(
            if (groups == setOf(PlayerOptionGroup.QUALITY)) R.string.core_player_quality else R.string.core_player_audio_subtitles,
        ),
        sections = optionSections(options, groups),
        onSelect = { section, id -> choices.onSelect(section, id) },
        onDismiss = onDismiss,
        banner = bannerOf(notSaved),
    )
}

@Composable
fun TvPlayerOptionsSheet(
    options: TrackOptions,
    choices: TrackChoices,
    notSaved: Boolean,
    onDismiss: () -> Unit,
) {
    LumoTvPlayerOptionsSheet(
        title = stringResource(R.string.core_player_options_title),
        sections = optionSections(options, ALL_OPTION_GROUPS),
        onSelect = { section, id -> choices.onSelect(section, id) },
        onDismiss = onDismiss,
        banner = bannerOf(notSaved),
    )
}

@Composable
private fun bannerOf(notSaved: Boolean): String =
    if (notSaved) {
        stringResource(R.string.core_player_preference_not_saved)
    } else {
        stringResource(R.string.core_player_options_scope)
    }

private fun TrackChoices.onSelect(section: String, id: String) {
    when (section) {
        SECTION_AUDIO -> selectAudio(id)
        SECTION_SUBTITLES -> selectSubtitle(id.takeUnless { it == OFF_ID })
        SECTION_QUALITY -> selectQuality(id.takeUnless { it == AUTO_ID })
    }
}

@Composable
private fun optionSections(options: TrackOptions, groups: Set<PlayerOptionGroup>): List<LumoOptionSection> {
    val resources = LocalContext.current.resources
    val numbered: (Int) -> String = { resources.getString(R.string.core_player_track_number, it) }
    val unsupported = stringResource(R.string.core_player_track_unsupported)
    val sections = ArrayList<LumoOptionSection>()

    if (PlayerOptionGroup.AUDIO in groups) {
        val audio = options.audio.asChoices(unnamed = numbered, unsupported = unsupported).withDistinctLabels(numbered)
        sections += LumoOptionSection(
            key = SECTION_AUDIO,
            title = stringResource(R.string.core_player_audio),
            choices = audio,
            note = when {
                audio.isEmpty() -> stringResource(R.string.core_player_no_audio)
                audio.count { it.enabled } <= 1 -> stringResource(R.string.core_player_no_other_audio)
                else -> null
            },
        )
    }

    if (PlayerOptionGroup.SUBTITLES in groups) {
        val forced = stringResource(R.string.core_player_subtitle_forced)
        val off = LumoAudioTrackChoice(
            id = OFF_ID,
            label = stringResource(R.string.core_player_subtitles_off),
            detail = null,
            selected = options.subtitlesOff,
            enabled = true,
        )
        val tracks = options.subtitles.mapIndexed { index, track ->
            track.asChoice(index, numbered, unsupported, forced)
        }.withDistinctLabels(numbered)
        sections += LumoOptionSection(
            key = SECTION_SUBTITLES,
            title = stringResource(R.string.core_player_subtitles),
            choices = listOf(off) + tracks,
            note = when {
                options.subtitleLanguageUnavailable -> stringResource(R.string.core_player_subtitle_language_unavailable)
                tracks.isEmpty() -> stringResource(R.string.core_player_no_subtitles)
                else -> null
            },
        )
    }

    if (PlayerOptionGroup.QUALITY in groups) {
        val auto = LumoAudioTrackChoice(
            id = AUTO_ID,
            label = stringResource(R.string.core_player_quality_auto),
            detail = null,
            selected = options.qualityAuto,
            enabled = true,
        )
        val variants = if (options.offersQualityChoice) {
            options.qualities
                .sortedWith(compareByDescending<VideoQuality> { it.height ?: 0 }.thenByDescending { it.bitrate ?: 0 })
                .mapIndexed { index, quality ->
                    LumoAudioTrackChoice(
                        id = quality.id,
                        label = quality.label(resources, index, numbered),
                        detail = if (quality.playable) null else unsupported,
                        selected = quality.selected,
                        enabled = quality.playable,
                    )
                }
        } else {
            emptyList()
        }
        sections += LumoOptionSection(
            key = SECTION_QUALITY,
            title = stringResource(R.string.core_player_quality),
            choices = listOf(auto) + variants,
            note = if (variants.isEmpty()) stringResource(R.string.core_player_no_other_quality) else null,
        )
    }

    return sections
}

private fun SubtitleTrack.asChoice(
    index: Int,
    numbered: (Int) -> String,
    unsupported: String,
    forcedWord: String,
) = LumoAudioTrackChoice(
    id = id,
    label = spokenName(Locale.getDefault()) ?: numbered(index + 1),
    detail = when {
        !playable -> unsupported
        forced -> forcedWord
        else -> null
    },
    selected = selected,
    enabled = playable,
)

/** "1080p · 4.5 Mb/s", whichever halves the stream declares, or "Track n". */
private fun VideoQuality.label(
    resources: Resources,
    index: Int,
    numbered: (Int) -> String,
): String {
    val height = height?.let { resources.getString(R.string.core_player_quality_height, it) }
    val rate = bitrate?.let {
        resources.getString(
            R.string.core_player_quality_bitrate,
            String.format(Locale.getDefault(), "%.1f", it / BITS_PER_MEGABIT),
        )
    }
    return listOfNotNull(height, rate).joinToString(" · ").ifEmpty { numbered(index + 1) }
}

/**
 * Two rows reading the same are told apart by their position (PS-06): two
 * French tracks become "French", "French · Track 2".
 */
private fun List<LumoAudioTrackChoice>.withDistinctLabels(numbered: (Int) -> String): List<LumoAudioTrackChoice> {
    val counts = groupingBy { it.label }.eachCount()
    val seen = HashMap<String, Int>()
    return mapIndexed { index, choice ->
        if ((counts[choice.label] ?: 0) < 2) return@mapIndexed choice
        val occurrence = seen.merge(choice.label, 1, Int::plus) ?: 1
        if (occurrence == 1) choice else choice.copy(label = "${choice.label} · ${numbered(index + 1)}")
    }
}

private const val SECTION_AUDIO = "audio"
private const val SECTION_SUBTITLES = "subtitles"
private const val SECTION_QUALITY = "quality"

/** Not a track id: ids are `group:index`, never these words. */
private const val OFF_ID = "off"
private const val AUTO_ID = "auto"

private const val BITS_PER_MEGABIT = 1_000_000.0
