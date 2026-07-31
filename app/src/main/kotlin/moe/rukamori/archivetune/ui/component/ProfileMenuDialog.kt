/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R

/**
 * Centered, modal profile menu — replaces the previous anchored [androidx.compose.material3.DropdownMenu].
 *
 * Renders as a separate [Dialog] (in its own window) centered on screen, with a
 * real backdrop blur on Android 12+ (via a captured [GraphicsLayer] passed in
 * from the host) and a dim scrim on older devices.
 *
 * Material 3 Expressive styling:
 *   - 28.dp corner radius (M3 Expressive large surface)
 *   - `surfaceContainerHigh` tonal elevation
 *   - 24.dp horizontal padding, 8.dp vertical
 *   - Header with avatar (or initials fallback) + display name + "View profile"
 *     subtitle row
 *   - ListItem rows with `leadingContent` icons, BadgedBox for unread badges
 *
 * @param layer GraphicsLayer captured from the host's main content (used for
 *   the blurred backdrop on Android 12+). Pass `null` on pre-S or if no
 *   capture is available — the dialog falls back to a dim scrim.
 * @param accountName Display name shown in the header (may be blank).
 * @param accountImageUrl Avatar URL shown in the header (may be null).
 * @param items The menu items to render (icon, label, badge, onClick).
 * @param onDismiss Called when the dialog is dismissed (tap outside, back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuDialog(
    layer: GraphicsLayer?,
    accountName: String,
    accountImageUrl: String?,
    items: List<ProfileMenuItem>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Backdrop: blurred captured content on S+, dim scrim elsewhere.
            if (layer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Draw the captured screen content (recorded by the host's
                // drawWithContent{layer.record{}} modifier) with a BlurEffect
                // applied via graphicsLayer. The semi-transparent black scrim
                // on top keeps the dialog content readable.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect = BlurEffect(
                                radiusX = 32f,
                                radiusY = 32f,
                                edgeTreatment = TileMode.Decal,
                            )
                        }
                        .drawBehind { drawLayer(layer) },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                )
            } else {
                // Pre-S or no layer — fall back to a semi-transparent scrim.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                )
            }

            // Modal surface with the actual menu content.
            Surface(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    // Header: avatar + account name
                    if (accountName.isNotBlank()) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = accountName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.account),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    if (!accountImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = accountImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.account),
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }

                    items.forEach { item ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            leadingContent = {
                                BadgedBox(badge = {
                                    if (item.showBadge) Badge()
                                }) {
                                    Icon(
                                        painter = painterResource(item.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = item.onClick),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

/** A single profile menu item. */
data class ProfileMenuItem(
    val icon: Int,
    val label: String,
    val showBadge: Boolean = false,
    val onClick: () -> Unit,
)
