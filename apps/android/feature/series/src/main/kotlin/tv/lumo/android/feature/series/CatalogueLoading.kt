package tv.lumo.android.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.lumo.android.core.data.model.CatalogueLoadProgress

/**
 * What an empty grid says while the catalogue downloads (S16B-10): a count once
 * the server has reported its total, a plain sentence before.
 */
@Composable
internal fun loadingBody(progress: CatalogueLoadProgress?): String =
    if (progress == null || progress.total <= 0) {
        stringResource(R.string.feature_series_loading_body)
    } else {
        stringResource(R.string.feature_series_loading_progress, progress.loaded, progress.total)
    }
