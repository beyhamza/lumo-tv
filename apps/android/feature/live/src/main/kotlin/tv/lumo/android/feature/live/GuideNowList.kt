package tv.lumo.android.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import java.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.lumo.android.core.data.NowAndNext
import tv.lumo.android.core.designsystem.format.formatTimeOfDay
import tv.lumo.android.core.data.model.Channel
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The Guide's **« En ce moment »** list, shared by the Android surfaces
 * (US-16, S9-04-05).
 *
 * <h2>One line per channel of the filtered result, current then next</h2>
 *
 * [state] holds the programme windows the guide has already read
 * ([LiveState.guideProgrammes]); [now] is the screen's clock, applied with
 * `currentAndNext`, so a channel shows its current programme and the one after
 * it — or **nothing at all** for its programme line when the guide has none.
 * A channel whose provider never mapped a `tvg_id` stays in the list and stays
 * readable: it draws its name and no invented "programme unavailable" (S7-03,
 * "la chaîne reste lisible").
 *
 * <h2>One grouped request per page, never one per card</h2>
 *
 * The list watches which indices are on display, rounds them to a page of
 * [EPG_PAGE_SIZE] — the same page as the channel grid — and hands the page's
 * ids to [onPageVisible]. The view model asks the guide once for the channels
 * of that page it does not hold yet; scrolling within a page costs nothing and
 * scrolling back costs nothing. This is the trap S7-04 names, and the reason
 * this list is fed by Paging and never by a materialised `List<Channel>`.
 *
 * <h2>« Maintenant » is at the top, and is the screen's clock</h2>
 *
 * The button sits above the list, always reachable. Until S9-05's time grid it
 * scrolls back to the first row and asks the screen to recompute "now"; it never
 * promises an offset on a grid that does not exist yet.
 */
@Composable
fun GuideNowList(
    state: LiveState,
    channels: LazyPagingItems<Channel>,
    now: Instant,
    onNow: () -> Unit,
    onPlay: (channelId: String, name: String?) -> Unit,
    onPageVisible: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(modifier = modifier.fillMaxSize()) {
        Header(onNow = onNow)

        // The page on display, for the guide. `visibleItemsInfo` and Paging's
        // snapshot are both state, so this re-runs when a page loads or the list
        // scrolls, and `distinctUntilChanged` keeps a scroll within one page from
        // asking anything (S9-04-05).
        LaunchedEffect(listState, channels) {
            snapshotFlow {
                epgPageIds(
                    visible = listState.layoutInfo.visibleItemsInfo.map { it.index },
                    itemCount = channels.itemCount,
                    idAt = { index -> channels.itemSnapshotList.getOrNull(index)?.id },
                )
            }
                .distinctUntilChanged()
                .collect(onPageVisible)
        }

        if (channels.itemCount == 0) return@Column

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = LumoSpacing.lg,
                vertical = LumoSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        ) {
            items(
                count = channels.itemCount,
                key = channels.itemKey { it.id },
            ) { index ->
                // Null is a placeholder Paging has not loaded yet: it is drawn
                // as a row of the right height and carries no channel.
                val channel = channels[index]
                GuideNowRow(
                    channel = channel,
                    onAir = channel?.let { state.nowAndNext(it.id, now) },
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun Header(onNow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_live_guide_heading),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = stringResource(R.string.feature_live_guide_now),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onNow)
                .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
        )
    }
}

/**
 * One channel and its guide, or just the channel.
 *
 * The programme lines are independent: a gap in the guide has a next programme
 * and no current one, and the last programme of the window has the reverse.
 * Both absent draws only the channel name — no placeholder, no "unavailable"
 * (S7-03, S9-04).
 */
@Composable
private fun GuideNowRow(
    channel: Channel?,
    onAir: NowAndNext?,
    onPlay: (channelId: String, name: String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = channel != null) { channel?.let { onPlay(it.id, it.name) } }
            .padding(LumoSpacing.sm + LumoSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.xs),
    ) {
        Text(
            text = channel?.name.orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        onAir?.current?.let { programme ->
            ProgrammeLine(
                label = stringResource(R.string.feature_live_guide_now_label),
                programme = programme,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        onAir?.next?.let { programme ->
            ProgrammeLine(
                label = stringResource(R.string.feature_live_guide_next),
                programme = programme,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgrammeLine(
    label: String,
    programme: EpgProgramme,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.feature_live_guide_programme,
                label,
                formatTimeOfDay(programme.startsAt),
                programme.title,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The page of channel ids a set of visible indices covers.
 *
 * **One function for both surfaces** (S9-04-03): the Guide's "En ce moment"
 * list and the television's channel grid round what is visible to the same page
 * of [EPG_PAGE_SIZE], so that the grouped request they make against the guide is
 * bounded the same way and a scroll within a page costs nothing on either. It
 * lived twice — `visiblePageIds` in `LiveTvScreen` and `guidePageIds` here — and
 * the two were the same rule written twice.
 *
 * `visible` is empty when nothing is on screen — the list has not measured yet —
 * and no request is made for it. The last page is clamped to [itemCount] so a
 * half-filled page asks only for the channels that exist.
 */
internal fun epgPageIds(
    visible: List<Int>,
    itemCount: Int,
    idAt: (Int) -> String?,
): List<String> {
    if (visible.isEmpty() || itemCount == 0) return emptyList()
    val first = (visible.min() / EPG_PAGE_SIZE) * EPG_PAGE_SIZE
    val last = minOf((visible.max() / EPG_PAGE_SIZE + 1) * EPG_PAGE_SIZE, itemCount)
    return (first until last).mapNotNull(idAt)
}
