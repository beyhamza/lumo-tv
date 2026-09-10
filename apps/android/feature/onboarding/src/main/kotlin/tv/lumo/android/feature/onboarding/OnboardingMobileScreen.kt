package tv.lumo.android.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tv.lumo.android.core.designsystem.component.LumoWordmark
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * The three onboarding pages of the M1 mock-up (docs/design/canvas, mobile
 * artboards 01 to 03): what Lumo is, what a source is, and that the account
 * follows the user across screens.
 *
 * <h2>Not on the way in yet</h2>
 *
 * `mobileStartRoute` still opens a signed-out phone on the sign-in screen, and
 * the two halves of the way in link to each other directly. Moving the start
 * back here is the application's decision, taken the day this screen is
 * wired; until then the screen is complete but unreached. [onFinished] is what
 * that wiring will call — both "skip" and the last page's button end here.
 *
 * <h2>What is drawn instead of illustrations</h2>
 *
 * The mock-up shows the mark on the first page and two placeholder plates on
 * the others, labelled as placeholders. The mark is drawn; the plates are
 * plates. Lumo ships no artwork it does not own (AGENTS.md §1), and a plate
 * that says nothing is better than a picture that says the wrong thing.
 */
@Composable
fun OnboardingMobileScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    val pages = onboardingPages()
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { index ->
            Page(pages[index], index)
        }

        Column(
            modifier = Modifier.padding(
                start = LumoSpacing.lg,
                end = LumoSpacing.lg,
                bottom = LumoSpacing.xl + LumoSpacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(LumoSpacing.md + LumoSpacing.xs),
        ) {
            Dots(count = pages.size, current = pagerState.currentPage)

            Row(horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm + LumoSpacing.xs)) {
                if (!last) {
                    SecondaryPill(
                        label = stringResource(R.string.feature_onboarding_skip),
                        onClick = onFinished,
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryPill(
                    label = stringResource(
                        if (last) R.string.feature_onboarding_start else R.string.feature_onboarding_next,
                    ),
                    onClick = {
                        if (last) {
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.weight(if (last) 1f else 1.6f),
                )
            }
        }
    }
}

private data class OnboardingPage(val title: String, val body: String)

@Composable
private fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        stringResource(R.string.feature_onboarding_page1_title),
        stringResource(R.string.feature_onboarding_page1_body),
    ),
    OnboardingPage(
        stringResource(R.string.feature_onboarding_page2_title),
        stringResource(R.string.feature_onboarding_page2_body),
    ),
    OnboardingPage(
        stringResource(R.string.feature_onboarding_page3_title),
        stringResource(R.string.feature_onboarding_page3_body),
    ),
)

@Composable
private fun Page(page: OnboardingPage, index: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LumoSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(LumoSpacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (index) {
            0 -> BrandDisc()
            1 -> Plate(width = 240.dp, height = 150.dp) {
                LumoWordmark(height = 28.dp, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Screens()
        }

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The mark's disc alone, large — the first page of the mock-up. */
@Composable
private fun BrandDisc() {
    Box(
        modifier = Modifier
            .size(88.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(LumoColors.Accent, LumoColors.AccentViolet),
                    center = Offset(0.35f, 0.3f),
                    radius = 0.75f,
                ),
                shape = CircleShape,
            ),
    )
}

@Composable
private fun Plate(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    focused: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .then(
                if (focused) {
                    Modifier
                        .border(2.dp, LumoColors.Accent, LumoShapes.medium)
                        .padding(LumoSpacing.xs)
                } else {
                    Modifier
                },
            )
            .background(MaterialTheme.colorScheme.surface, LumoShapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, LumoShapes.small),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A phone, a television with the focus outline, a tablet — the third page. */
@Composable
private fun Screens() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm + LumoSpacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Plate(width = 44.dp, height = 76.dp)
        Plate(width = 120.dp, height = 76.dp, focused = true)
        Plate(width = 76.dp, height = 56.dp)
    }
}

/**
 * The page indicator: the current page as a short bar in the brand gradient —
 * one of the three uses the charter allows it, progression — and the others as
 * dots on the raised surface.
 */
@Composable
private fun Dots(count: Int, current: Int) {
    val description = stringResource(R.string.feature_onboarding_page_of, current + 1, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(LumoSpacing.sm, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                modifier = Modifier
                    .width(if (selected) LumoSpacing.lg else DOT)
                    .height(DOT)
                    .background(
                        brush = if (selected) {
                            Brush.horizontalGradient(listOf(LumoColors.Accent, LumoColors.AccentViolet))
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PrimaryPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.height(PILL_HEIGHT),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun SecondaryPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.height(PILL_HEIGHT),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private val PILL_HEIGHT = 52.dp
private val DOT = 6.dp
