/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.network.NetworkBannerUiState

private data class NetworkBannerVisuals(
    val message: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

/**
 * Compact pill-shaped popup that surfaces network state changes.
 *
 * Replaces the previous full-width red "No internet connection" banner.
 * Auto-dismisses after a few seconds (controlled by the use case). Includes
 * an inline dismiss button so the user can dismiss the popup manually.
 *
 * States:
 *  - Offline: amber pill labelled "Offline mode" with cloud-off icon
 *  - BackOnline: green pill labelled "Back online" with cloud-done icon
 *  - Hidden: not rendered
 */
@Composable
fun NetworkStatusBanner(
    state: NetworkBannerUiState,
    modifier: Modifier = Modifier,
) {
    var lastVisibleState by remember { mutableStateOf<NetworkBannerUiState>(NetworkBannerUiState.Offline) }
    // Track user-initiated dismissal so a tap on the X immediately hides the
    // popup. Reset whenever the underlying state changes (so the next network
    // event will surface the popup again).
    var userDismissed by remember { mutableStateOf(false) }

    if (state != NetworkBannerUiState.Hidden) {
        lastVisibleState = state
        // If the state has changed since the user dismissed, allow it to show
        // again. Using `state` here means a new Offline→BackOnline transition
        // will re-display the back-online popup even if the user dismissed
        // the offline one.
        LaunchedEffect(state) { userDismissed = false }
    }

    val visuals =
        when (lastVisibleState) {
            NetworkBannerUiState.Hidden,
            NetworkBannerUiState.Offline,
            -> {
                NetworkBannerVisuals(
                    message = "Offline mode",
                    icon = Icons.Default.CloudOff,
                    containerColor = Color(0xFF8A6D1F),
                    contentColor = Color.White,
                )
            }

            NetworkBannerUiState.BackOnline -> {
                NetworkBannerVisuals(
                    message = "Back online",
                    icon = Icons.Default.CloudDone,
                    containerColor = Color(0xFF1E8E3E),
                    contentColor = Color.White,
                )
            }
        }

    val visible = state != NetworkBannerUiState.Hidden && !userDismissed

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            scaleIn(animationSpec = tween(durationMillis = 180), initialScale = 0.85f) +
                fadeIn(animationSpec = tween(durationMillis = 160)),
        exit =
            scaleOut(animationSpec = tween(durationMillis = 160), targetScale = 0.85f) +
                fadeOut(animationSpec = tween(durationMillis = 160)),
        label = "networkStatusBanner",
    ) {
        Surface(
            // Pill shape — rounded full corners, compact height.
            shape = RoundedCornerShape(percent = 50),
            color = visuals.containerColor,
            contentColor = visuals.contentColor,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = visuals.contentColor,
                )
                Text(
                    text = visuals.message,
                    color = visuals.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.width(2.dp))
                // Compact dismiss (X) button inside the pill.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { userDismissed = true }
                        .padding(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close_dialog),
                        modifier = Modifier.size(14.dp),
                        tint = visuals.contentColor,
                    )
                }
            }
        }
    }
}
