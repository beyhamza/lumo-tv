package tv.lumo.android.feature.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.tv.lumoTvFocus
import tv.lumo.android.core.designsystem.tv.tvOverscanEdges

/**
 * One film, on a television (US-13).
 *
 * <h2>A new focus surface, and therefore a new section in the focus map</h2>
 *
 * `docs/design/tv-focus-map.md` has one, with the arrival focus and all four
 * directions from every zone. That is a deliverable of this task and not
 * paperwork: on a television the way in and out of a screen is the screen, and a
 * blank cell in that table is a defect rather than an omission in the writing.
 *
 * <h2>One focus target, or two when there is something to resume</h2>
 *
 * **Play**, and it has the focus on arrival. Somebody who pressed `OK` on a
 * poster has already decided; the screen exists to confirm what they chose, not
 * to make them travel through it. Everything else here is text, and text that
 * takes focus on a television is text somebody has to press past.
 *
 * A film with a saved position gets **two buttons — "Resume at 20:14" focused,
 * "Start over" one `DOWN` away** (S5-11). Both visible, and neither pressed on
 * anybody's behalf: resuming automatically is a good idea right up until somebody
 * wants the beginning, and then it is a feature with no way out. A **finished**
 * film is back to one button, because offering to carry on from the credits is
 * not an offer.
 *
 * `BACK` is the way out and it is a physical key, so there is no back control to
 * draw — one would be a second target for something the remote already does.
 *
 * <h2>The synopsis is capped, and that is a stated limit rather than an oversight</h2>
 *
 * Ten lines at the television body scale, then ellipsis. Almost every synopsis an
 * IPTV panel carries is shorter than that, and the alternative is a scrollable
 * block, which on a television means a **second focus zone whose only purpose is
 * to scroll text** — a zone the map would have to describe as one where `OK` does
 * nothing. A very long synopsis is therefore truncated here and complete on the
 * phone; that is the trade, and it is written down rather than discovered.
 */
@Composable
fun VodDetailTvScreen(
    filmId: String,
    onPlay: (filmId: String, sourceId: String, title: String?, atMs: Long) -> Unit,
    onBack: (filmId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VodDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(filmId) { viewModel.start(filmId) }

    // BACK returns to the grid **and says which film was being looked at**, so the
    // remote comes back on that card rather than at the head of thirty thousand.
    // The rule is US-10's, and it holds here for the reason it held there.
    BackHandler { onBack(filmId) }

    val play = remember { FocusRequester() }
    val film = state.film
    val resumeAt = state.resumeFrom?.positionMs

    // Keyed on the offer: the resume button does not exist on the frame this
    // screen first draws — the position arrives from the server a moment later —
    // and the focus has to move to it when it appears rather than stay on the
    // button below.
    LaunchedEffect(resumeAt) { runCatching { play.requestFocus() } }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(LumoColors.Ink)
            .tvOverscanEdges(top = true, end = true, bottom = true)
            .padding(LumoSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.xl),
    ) {
        LumoPoster(
            posterUrl = film?.posterUrl,
            title = film?.name.orEmpty(),
            modifier = Modifier.width(POSTER_WIDTH),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            Text(
                text = film?.name.orEmpty(),
                style = MaterialTheme.typography.displayMedium,
                color = LumoColors.OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            film?.tvFacts()?.takeIf { it.isNotEmpty() }?.let { facts ->
                Text(
                    text = facts.joinToString(" · "),
                    style = MaterialTheme.typography.titleLarge,
                    color = LumoColors.OnDarkMuted,
                )
            }

            if (resumeAt != null) {
                TvActionButton(
                    label = stringResource(R.string.feature_vod_resume_at, resumeAt.asTvClock()),
                    enabled = film != null,
                    focusRequester = play,
                    onClick = { film?.let { onPlay(it.id, it.sourceId, it.name, resumeAt) } },
                )
            }

            TvActionButton(
                label = stringResource(
                    if (resumeAt != null) {
                        R.string.feature_vod_start_over
                    } else {
                        R.string.feature_vod_play
                    },
                ),
                enabled = film != null,
                // The requester goes to whichever button is the arrival target:
                // resume when there is one, play otherwise. One requester, moved,
                // rather than two that could both fire.
                focusRequester = if (resumeAt == null) play else null,
                onClick = { film?.let { onPlay(it.id, it.sourceId, it.name, 0L) } },
            )

            Text(
                text = when {
                    film?.plot != null -> film.plot!!
                    state.loadingSynopsis -> stringResource(R.string.feature_vod_synopsis_loading)
                    film != null -> stringResource(R.string.feature_vod_synopsis_none)
                    else -> ""
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (film?.plot != null) LumoColors.OnDark else LumoColors.OnDarkMuted,
                maxLines = SYNOPSIS_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A button the remote can land on.
 *
 * `clickable` rather than `focusable()` plus a click handler: it does both, and
 * adding the second would put two focus targets on one control — rule 6 of the
 * focus map.
 *
 * @param focusRequester null for a button that is not the arrival target. One
 *   requester moves between the two rather than each holding its own, because
 *   two requesters both asking for focus is a race whose winner changes between
 *   runs.
 */
@Composable
private fun TvActionButton(
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .then(
                focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .lumoTvFocus(focused)
            .clip(LumoShapes.medium)
            .background(if (focused) LumoColors.Accent else LumoColors.SurfaceRaised)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = LumoSpacing.xl, vertical = LumoSpacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (focused) LumoColors.OnAccent else LumoColors.OnDark,
        )
    }
}

/** The player's formatter: "resume at 20:14" must read as the scrubber's number. */
private fun Long.asTvClock(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Year, running time and rating, absent ones left out.
 *
 * The phone's list, at the television scale. The rating is the source's own text
 * — `7.4`, `PG-13` and `★★★★` all occur — and normalising it would be this layer
 * deciding what the provider meant.
 */
@Composable
private fun VodItem.tvFacts(): List<String> = buildList {
    year?.let { add(it.toString()) }
    durationSeconds?.let {
        add(stringResource(R.string.feature_vod_minutes, it / SECONDS_PER_MINUTE))
    }
    rating?.let(::add)
}

/** Large enough to carry the picture on a panel seen from three metres. */
private val POSTER_WIDTH = 280.dp

/** See the class documentation: a cap, stated, rather than a second focus zone. */
private const val SYNOPSIS_LINES = 10

private const val SECONDS_PER_MINUTE = 60
