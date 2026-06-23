/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cast.CastScreenState
import moe.rukamori.archivetune.cast.CastViewModel
import moe.rukamori.archivetune.ui.component.NewAction
import timber.log.Timber

@Composable
fun rememberCastPlayerMenuAction(): NewAction? {
    val viewModel: CastViewModel = viewModel()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val castState = (screenState as? CastScreenState.Success)?.uiState ?: return null
    if (!castState.isAvailable) return null

    val context = LocalContext.current
    val text = stringResource(R.string.cast)
    val onClick = remember(context) { { showCastRouteDialog(context) } }

    return NewAction(
        icon = {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        text = text,
        onClick = onClick,
    )
}

private fun showCastRouteDialog(context: Context) {
    val appContext = context.applicationContext
    val castContext =
        runCatching { CastContext.getSharedInstance(appContext) }
            .onFailure { Timber.tag("Cast").w(it, "Unable to open Cast route picker") }
            .getOrNull() ?: return
    val selector = castContext.mergedSelector ?: return
    val router = MediaRouter.getInstance(context)
    val selectedRoute = router.selectedRoute
    if (selectedRoute.isDefault || selectedRoute.isBluetooth) {
        MediaRouteChooserDialog(context, androidx.media3.cast.R.style.AppThemeDialog)
            .apply {
                routeSelector = selector
            }.show()
    } else {
        MediaRouteControllerDialog(context, androidx.media3.cast.R.style.AppThemeDialog).show()
    }
}
