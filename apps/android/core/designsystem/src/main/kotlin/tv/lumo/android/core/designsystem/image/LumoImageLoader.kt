package tv.lumo.android.core.designsystem.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade

/**
 * The one image loader both applications use.
 *
 * <h2>Why this exists at all, and why it arrived with the films</h2>
 *
 * Until now every picture on a screen was a channel logo: 32 dp, a few kilobytes,
 * a handful visible at once. Coil's defaults were fine and nobody had to think
 * about them.
 *
 * A poster grid is a different machine. A television shows around eighteen
 * posters at once, each one hundreds of times the decoded bitmap of a logo, on a
 * box that often has **one gigabyte of usable memory** and a partition somebody
 * cannot easily clear. Coil's default disk cache is 250 MB and its default memory
 * cache is a quarter of the heap; neither number was chosen against this grid, and
 * the first one is enough on its own to fill a small TV box's storage with our
 * cache of somebody else's artwork.
 *
 * So the numbers are stated here, once, where they can be argued with — rather
 * than left as whatever the library ships this version.
 *
 * <h2>What is deliberately not here</h2>
 *
 * **No placeholder and no error drawable.** A card with no poster renders its
 * title on a flat colour, and a poster that fails to load renders the same thing.
 * Lumo ships no artwork of its own (CLAUDE.md, règle 2), and a generic film
 * silhouette standing in for somebody's missing poster is an invented picture of
 * content we do not have. It also happens to be the truthful rendering: on a real
 * source many posters are advertised over `http` and this application does not
 * permit cleartext, so *"no poster"* and *"the poster did not load"* really are
 * the same outcome from the user's side.
 *
 * See [tv.lumo.android.core.designsystem.component.LumoPoster], which is where
 * that is drawn and where the request gets its size.
 */
fun lumoImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                // A fraction of the heap rather than a fixed number of bytes: the
                // same code runs on a phone with 512 MB to play with and a TV box
                // with 96. Below Coil's default of 0.25 because Paging is holding
                // the visible pages' bitmaps too — see `VodPager.maxSize`, which
                // is the other half of this ceiling.
                .maxSizePercent(context, 0.18)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                // The application cache directory, so the system can reclaim it
                // under storage pressure and so "clear cache" clears it.
                .directory(context.cacheDir.resolve("lumo_image_cache"))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        // Off, and that is a television decision. A crossfade on eighteen posters
        // arriving at once is eighteen simultaneous animations while the D-pad is
        // moving, on the device least able to afford them.
        .crossfade(false)
        .build()

/**
 * 64 MB.
 *
 * Room for a few hundred posters at card size, which covers browsing a category
 * and coming back to it. Not room for a catalogue: a cache of thirty thousand
 * posters is us filling somebody's television with somebody else's artwork, and
 * the request that refills it is one they can afford.
 */
private const val DISK_CACHE_BYTES = 64L * 1024 * 1024
