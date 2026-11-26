package tv.lumo.android.core.data.model

/**
 * How far a whole-catalogue download has got (S16B-10).
 *
 * A film or series catalogue is pulled whole and written once, so the grid of a
 * first download stays empty until the last page — seven minutes for a panel of
 * a hundred and fifty thousand films. Said as a count, that wait is a download
 * somebody can watch move; unsaid, it is a blank screen that looks broken.
 *
 * [total] is what the server reports, and can be zero before the first page.
 */
data class CatalogueLoadProgress(val loaded: Int, val total: Int)
