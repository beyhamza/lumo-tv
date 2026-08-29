package tv.lumo.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import tv.lumo.android.core.designsystem.theme.LumoColors
import tv.lumo.android.core.designsystem.theme.LumoShapes
import tv.lumo.android.core.designsystem.theme.LumoSpacing

/**
 * A film poster, at the size of the card that holds it.
 *
 * <h2>The size comes from the layout, and that is the whole point</h2>
 *
 * Coil measures this composable and asks the network for an image at *those*
 * pixels. On a poster grid that is the difference between decoding a 1000×1500
 * source eighteen times and decoding it at 180×270 eighteen times — roughly
 * thirty times the memory, on the device least able to afford it.
 *
 * It only works if this composable is given **bounded constraints**. That is why
 * [LumoPoster] applies its own [aspectRatio] rather than accepting whatever it is
 * put in: a poster dropped into a scrolling column with no height would measure
 * as unbounded, Coil would fall back to the source resolution, and nothing would
 * look wrong until a TV box ran out of heap. The caller gives it a width; this
 * gives it a height.
 *
 * <h2>No fallback image, ever</h2>
 *
 * A film with no poster shows its title on a flat colour, and a poster that
 * failed to load shows the same thing. Lumo ships no artwork of its own
 * (CLAUDE.md, règle 2) — a generic silhouette would be a picture we invented for
 * content we do not have.
 *
 * It is also the truthful rendering rather than a compromise. Many sources
 * advertise their posters over `http`, which this application does not permit, so
 * *"the source gave no poster"* and *"the poster did not load"* are genuinely the
 * same outcome from where the user is sitting.
 *
 * @param title rendered when there is no poster to show. Never elided to nothing:
 *   it is the only thing identifying the card in that case.
 * @param overlay drawn on top of the poster — a resume bar, a badge. Empty by
 *   default, because most cards have nothing to say beyond the picture.
 */
@Composable
fun LumoPoster(
    posterUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = LumoShapes.medium,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .aspectRatio(POSTER_RATIO)
            .clip(shape),
    ) {
        if (posterUrl == null) {
            TitlePlate(title)
        } else {
            SubcomposeAsyncImage(
                model = posterUrl,
                // Decorative: the title is either on the card or read out beside
                // it, and a screen reader announcing "poster of X" before "X" is
                // noise.
                contentDescription = null,
                // Crop rather than Fit. Sources do not agree on a poster ratio,
                // and a Fit leaves letterbox bars of our own colour around
                // somebody's artwork — which looks like a rendering bug on a grid
                // where the neighbouring card has none.
                contentScale = ContentScale.Crop,
                // Both states are the title plate. Not laziness: see above — the
                // two outcomes are the same one from the user's side, and a
                // spinner that resolves into a title is a flicker for nothing.
                loading = { TitlePlate(title) },
                error = { TitlePlate(title) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay()
    }
}

/**
 * What a card with no poster is: its title, on a flat colour.
 *
 * The colour is a fixed token rather than a theme surface, because this
 * plate stands where a picture would and has to read as full on both the phone's
 * light scheme and the television's dark one. A theme-following plate turns white
 * on a phone, which reads as a card that failed rather than a film with no
 * poster.
 */
@Composable
private fun TitlePlate(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumoColors.SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = LumoColors.OnDarkMuted,
            textAlign = TextAlign.Center,
            // Four lines, then elided. A very long title cropped to two words is
            // a card nobody can identify, which is the one thing this plate exists
            // to prevent.
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(LumoSpacing.sm),
        )
    }
}

/**
 * 2:3, the ratio the film industry prints and every panel advertises.
 *
 * Fixed rather than read from the image: the grid has to lay out before any
 * poster has arrived, and cards that resized as their pictures loaded would make
 * the whole grid jump under a D-pad that is already moving.
 */
private const val POSTER_RATIO = 2f / 3f
