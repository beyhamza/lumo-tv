package tv.lumo.android.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import tv.lumo.android.core.data.model.EpgProgramme
import tv.lumo.android.core.designsystem.format.formatTimeOfDay
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing
import tv.lumo.android.core.designsystem.theme.LumoTvShapes
import tv.lumo.android.core.designsystem.tv.lumoTvFocus

/**
 * The programme detail sheet (US-16, S9-06-01, GD-07/08).
 *
 * <h2>What it is, and where it opens</h2>
 *
 * Selecting a programme **in the Guide** opens a compact sheet without leaving
 * the guide (`docs/design/0.2.0/direct-guide.md`, "Fiche de programme"): a side
 * panel on the television, a panel from the bottom on the phone. The Chaînes
 * view keeps its immediate playback — only the Guide goes through information
 * first. The sheet is the [ProgrammeSheet] value [LiveState.programmeSheet]
 * holds, so Back and the source switch close it with no separate state.
 *
 * <h2>Three moments, and the action that follows</h2>
 *
 * A programme is [ProgrammeMoment.Current] from its start **inclusive** to its
 * end **exclusive** — the same half-open rule the grid uses. Only a current
 * programme offers "Regarder en direct"; a future or past one is information
 * only. Two acceptances are pinned here rather than left to a thumb:
 *
 * - **GD-07** — when the end is reached while the sheet is open, the action is
 *   removed and a focus that was on it joins **Fermer**; the sheet stays open on
 *   the same programme, whose title never changes underneath the viewer.
 * - **GD-08** — a future programme that becomes current gains the action without
 *   taking the focus, and the moment is read **again** when the action is
 *   pressed ([watchAllowed]), so a sheet left open past the end cannot play.
 *
 * Everything below [ProgrammeSheet] as a value is a function of instants, so the
 * two rules are plain unit tests ([ProgrammeSheetTest]).
 */

/** Which of the three moments a programme is in at an instant (GD-07/08). */
enum class ProgrammeMoment {

    /** Not started yet. Information only. */
    Future,

    /** Between its start (inclusive) and its end (exclusive). */
    Current,

    /** Over. Information only: the direct never replays (guide-interactions.md). */
    Past,
}

/**
 * The moment of [programme] at [now].
 *
 * Start inclusive, end exclusive: at 21:00 exactly a `20:00–21:00` programme is
 * over, and the `21:00–` one is current. Instants only, never local hours.
 */
fun momentOf(programme: EpgProgramme, now: Instant): ProgrammeMoment = when {
    now.isBefore(programme.startsAt) -> ProgrammeMoment.Future
    now.isBefore(programme.endsAt) -> ProgrammeMoment.Current
    else -> ProgrammeMoment.Past
}

/** The action "Regarder en direct" is offered for a current programme and no other. */
fun watchAvailable(moment: ProgrammeMoment): Boolean = moment == ProgrammeMoment.Current

/**
 * GD-08: whether the watch action may run when it is pressed.
 *
 * The moment is read **again** at that instant, from the real clock, so a sheet
 * left open across the programme's end refuses the action even if the panel has
 * not recomposed yet.
 */
fun watchAllowed(programme: EpgProgramme, now: Instant): Boolean =
    watchAvailable(momentOf(programme, now))

/** Where the television's focus sits inside the sheet. */
enum class ProgrammeSheetFocus { Watch, Close }

/**
 * GD-07: where the focus goes when the moment changes.
 *
 * A focus on the action that has just disappeared moves to **Fermer**; any other
 * place stays where it is, and the sheet is never closed for the viewer.
 */
fun sheetFocusAfter(
    moment: ProgrammeMoment,
    focused: ProgrammeSheetFocus,
): ProgrammeSheetFocus =
    if (focused == ProgrammeSheetFocus.Watch && !watchAvailable(moment)) {
        ProgrammeSheetFocus.Close
    } else {
        focused
    }

/** The programme whose sheet is open, and the channel it is being shown on. */
data class ProgrammeSheet(
    val channelId: String,
    val channelName: String?,
    val programme: EpgProgramme,
)

/** The programme's full range, both labels printed in the device's zone. */
@Composable
internal fun programmeTimeRange(programme: EpgProgramme): String =
    "${formatTimeOfDay(programme.startsAt)} – ${formatTimeOfDay(programme.endsAt)}"

/**
 * The television's side panel (S9-06-01).
 *
 * A panel rather than a replacement: the guide stays drawn underneath, so
 * **Fermer** can restore the exact cell (S9-06-02 owns the focus restitution;
 * this story owns the panel and its action).
 */
@Composable
internal fun ProgrammeSheetTv(
    sheet: ProgrammeSheet,
    now: Instant,
    onClose: () -> Unit,
    onWatch: (ProgrammeSheet) -> Unit,
    onTimePassed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moment = momentOf(sheet.programme, now)
    val watchRequester = remember { FocusRequester() }
    val closeRequester = remember { FocusRequester() }
    var watchFocused by remember(sheet.programme.id) { mutableStateOf(false) }

    SheetEndTick(sheet.programme, now, onTimePassed)

    // GD-07: the action is gone, the focus joins Fermer, the sheet stays open.
    LaunchedEffect(moment, watchFocused) {
        if (!watchAvailable(moment) && watchFocused) {
            withFrameNanos { }
            runCatching { closeRequester.requestFocus() }
        }
    }

    // Arrival: the action when there is one, Fermer otherwise. Never the focus
    // of the grid, which stays where the viewer left it (S9-06-02).
    LaunchedEffect(sheet.programme.id) {
        withFrameNanos { }
        runCatching {
            if (watchAvailable(momentOf(sheet.programme, Instant.now()))) {
                watchRequester.requestFocus()
            } else {
                closeRequester.requestFocus()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The guide stays visible: the panel is a side sheet over it, not a
        // replacement. A tap outside closes, exactly like Fermer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LumoColors.Ink.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(SHEET_PANEL_WIDTH)
                .background(LumoColors.SurfaceRaised)
                .onPreviewKeyEvent { event -> sheetKey(event, watchAvailable(moment), watchRequester, closeRequester) }
                .padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            sheet.channelName?.takeIf { it.isNotBlank() }?.let { channel ->
                TvText(
                    text = channel,
                    style = TvMaterialTheme.typography.labelLarge,
                    color = LumoColors.OnDarkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvText(
                text = sheet.programme.title,
                style = TvMaterialTheme.typography.titleLarge,
                color = LumoColors.OnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TvText(
                text = programmeTimeRange(sheet.programme),
                style = TvMaterialTheme.typography.bodyLarge,
                color = LumoColors.OnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sheet.programme.description?.takeIf { it.isNotBlank() }?.let { description ->
                TvText(
                    text = description,
                    style = TvMaterialTheme.typography.bodyMedium,
                    color = LumoColors.OnDarkMuted,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (watchAvailable(moment)) {
                TvSheetButton(
                    label = stringResource(R.string.feature_live_programme_watch),
                    focusRequester = watchRequester,
                    onFocused = { watchFocused = it },
                    onClick = { onWatch(sheet) },
                )
            }
            TvSheetButton(
                label = stringResource(R.string.feature_live_programme_close),
                focusRequester = closeRequester,
                onClick = onClose,
            )
        }
    }
}

/**
 * The phone's bottom panel (S9-06-01).
 *
 * On a phone the selection happens inside the channel's day
 * (`direct-guide.md`), and Back closes this panel before it climbs the Guide.
 */
@Composable
internal fun ProgrammeSheetMobile(
    sheet: ProgrammeSheet,
    now: Instant,
    onClose: () -> Unit,
    onWatch: (ProgrammeSheet) -> Unit,
    onTimePassed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moment = momentOf(sheet.programme, now)

    SheetEndTick(sheet.programme, now, onTimePassed)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(LumoShapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .padding(LumoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md),
        ) {
            sheet.channelName?.takeIf { it.isNotBlank() }?.let { channel ->
                Text(
                    text = channel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sheet.programme.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = programmeTimeRange(sheet.programme),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sheet.programme.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (watchAvailable(moment)) {
                Text(
                    text = stringResource(R.string.feature_live_programme_watch),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(LumoShapes.medium)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onWatch(sheet) }
                        .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
                )
            }
            Text(
                text = stringResource(R.string.feature_live_programme_close),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(LumoShapes.medium)
                    .clickable(onClick = onClose)
                    .padding(horizontal = LumoSpacing.md, vertical = LumoSpacing.sm),
            )
        }
    }
}

/**
 * "Le temps qui passe" (S9-06-01): wake exactly when [programme] ends, so the
 * sheet re-reads its moment without polling.
 *
 * Keyed on `now` too: once the caller has advanced its clock past the end, the
 * remaining delay is negative and the effect completes instead of rescheduling.
 */
@Composable
private fun SheetEndTick(programme: EpgProgramme, now: Instant, onTimePassed: () -> Unit) {
    LaunchedEffect(programme.id, programme.endsAt, now) {
        val remaining = Duration.between(now, programme.endsAt).toMillis()
        if (remaining > 0) {
            delay(remaining + SHEET_TICK_GRACE_MILLIS)
            onTimePassed()
        }
    }
}

/**
 * The remote inside the panel: Up reaches the action, Down reaches Fermer, and
 * the horizontal keys are swallowed so the focus never escapes to the grid
 * behind a modal panel. Back is left through on purpose — the screen closes the
 * sheet on it.
 */
private fun sheetKey(
    event: KeyEvent,
    watchAvailable: Boolean,
    watchRequester: FocusRequester,
    closeRequester: FocusRequester,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionUp -> {
            if (watchAvailable) runCatching { watchRequester.requestFocus() }
            true
        }

        Key.DirectionDown -> {
            runCatching { closeRequester.requestFocus() }
            true
        }

        Key.DirectionLeft, Key.DirectionRight -> true

        else -> false
    }
}

/** One named action of the television's panel, focusable from the D-pad. */
@Composable
private fun TvSheetButton(
    label: String,
    focusRequester: FocusRequester,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(LumoTvShapes.pill)
            .lumoTvFocus(focused = focused, shape = LumoTvShapes.pill)
            .background(LumoColors.Surface)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = LumoSpacing.lg, vertical = LumoSpacing.sm),
    ) {
        TvText(
            text = label,
            style = TvMaterialTheme.typography.labelLarge,
            color = LumoColors.OnDark,
            maxLines = 1,
        )
    }
}

/** The panel's width on a television: a side sheet, not a full-width screen. */
private val SHEET_PANEL_WIDTH = 360.dp

/** A short grace so "the end" is unambiguously past when the clock is re-read. */
private const val SHEET_TICK_GRACE_MILLIS = 50L
