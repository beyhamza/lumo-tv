package tv.lumo.android.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.lumo.android.core.data.model.VodItem
import tv.lumo.android.core.designsystem.component.LumoPoster
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * One film (US-13).
 *
 * <h2>What is on it, and what is deliberately not</h2>
 *
 * Poster, title, year, running time, rating as the source wrote it, synopsis when
 * the server has fetched one — and a play button. **Nothing else in v1**: no
 * cast, no recommendations, no trailer. Each of those needs data no IPTV panel
 * reliably carries, and a screen that shows an empty "Cast" heading on nine films
 * out of ten reads as broken rather than as sparse.
 *
 * <h2>The play button is the first thing, in every sense</h2>
 *
 * First in the layout after the poster, and first in the reading order. Somebody
 * who opened a film has already decided; making them scroll past a synopsis to
 * reach the one control they came for is the kind of thing that only happens
 * because the synopsis was written first.
 *
 * <h2>Resuming is offered, never imposed (S5-11)</h2>
 *
 * A film with a saved position shows **two** buttons — "Resume at 20:14" first,
 * "Start over" under it — and neither is pressed on anybody's behalf. Automatic
 * resume is a good idea right up until the day somebody wants to see the
 * beginning again, and then it is a feature with no way out.
 *
 * A **finished** film shows one button. Offering to carry on from the credits is
 * not an offer.
 *
 * <h2>The screen renders before the network answers</h2>
 *
 * Everything except the synopsis comes out of the cache, so the poster and the
 * title are up immediately, and offline they stay up. The synopsis is the one
 * field fetched on opening — on an Xtream panel it costs a request per film — and
 * it appears underneath when it arrives, or never, which is an ordinary film.
 */
@Composable
fun VodDetailMobileScreen(
    filmId: String,
    onPlay: (filmId: String, sourceId: String, title: String?, atMs: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VodDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(filmId) { viewModel.start(filmId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(LumoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
    ) {
        val film = state.film

        Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.md)) {
            LumoPoster(
                posterUrl = film?.posterUrl,
                title = film?.name.orEmpty(),
                modifier = Modifier.width(POSTER_WIDTH),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
            ) {
                Text(
                    text = film?.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                film?.facts()?.takeIf { it.isNotEmpty() }?.let { facts ->
                    Text(
                        // Joined rather than laid out in labelled rows: three
                        // short values, most of them absent most of the time, and
                        // a table of empty labels says less than a line that
                        // simply omits what the source did not give.
                        text = facts.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val resumeAt = state.resumeFrom?.positionMs

                // Resume first, and start-over under it. Both visible, and that
                // is the whole ruling of S5-11: a player that resumed on its own
                // is a good idea right up until somebody wants the beginning.
                if (resumeAt != null) {
                    Button(
                        onClick = { film?.let { onPlay(it.id, it.sourceId, it.name, resumeAt) } },
                        enabled = film != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.feature_vod_resume_at, resumeAt.asClock()))
                    }
                }

                Button(
                    onClick = { film?.let { onPlay(it.id, it.sourceId, it.name, 0L) } },
                    enabled = film != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (resumeAt != null) {
                                R.string.feature_vod_start_over
                            } else {
                                R.string.feature_vod_play
                            },
                        ),
                    )
                }
            }
        }

        Synopsis(film = film, loading = state.loadingSynopsis)

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.feature_vod_back))
        }
    }
}

/**
 * The synopsis, or an honest statement about its absence.
 *
 * Three states rather than two, and the third is the one that matters: a film
 * whose synopsis is on its way is not a film that has none. Saying "no synopsis"
 * for the second it takes to fetch one is a sentence the user reads and believes.
 */
@Composable
private fun Synopsis(film: VodItem?, loading: Boolean) {
    val plot = film?.plot

    Text(
        text = when {
            plot != null -> plot
            loading -> stringResource(R.string.feature_vod_synopsis_loading)
            film != null -> stringResource(R.string.feature_vod_synopsis_none)
            else -> ""
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (plot != null) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/**
 * Year, running time and rating, in that order, with the absent ones left out.
 *
 * The rating is echoed exactly as the source wrote it — `7.4`, `PG-13` and `★★★★`
 * all occur. Normalising it here would be this layer deciding what the provider
 * meant, which is the decision already refused for a channel's quality badge.
 */
@Composable
private fun VodItem.facts(): List<String> = buildList {
    year?.let { add(it.toString()) }
    durationSeconds?.let { seconds ->
        val minutes = seconds / SECONDS_PER_MINUTE
        // Minutes and not `1 h 47`: a running time is compared far more often
        // than it is read aloud, and the formats a source states are all over the
        // place. One unit, stated once.
        add(stringResource(R.string.feature_vod_minutes, minutes))
    }
    rating?.let(::add)
}

/**
 * Milliseconds as a clock, the hour only when there is one.
 *
 * The player's formatter, and it is the same value being spoken about: "resume
 * at 20:14" has to read as the number the scrubber will show.
 */
private fun Long.asClock(): String {
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
 * Wide enough to recognise a film, narrow enough to leave the title and the play
 * button a column of their own on a phone.
 */
private val POSTER_WIDTH = 140.dp

private const val SECONDS_PER_MINUTE = 60
