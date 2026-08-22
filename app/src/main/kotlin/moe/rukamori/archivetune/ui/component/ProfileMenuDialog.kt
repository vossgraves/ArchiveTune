/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuDialog(
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
            dismissOnClickOutside = true,
        ),
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                    ) { onDismiss() },
            )

            Surface(
                modifier = Modifier
                    .widthIn(max = ProfilePopupDefaults.MaxWidth)
                    .padding(horizontal = 24.dp),
                shape = ProfilePopupDefaults.ContainerShape,
                color = ProfilePopupDefaults.containerColor(),
                tonalElevation = ProfilePopupDefaults.TonalElevationDp,
                shadowElevation = ProfilePopupDefaults.ShadowElevationDp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    // Top row: account header (left) + dismiss button (right).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Account header — leading avatar + name/subtitle, takes
                        // available space, leaves room for the dismiss button.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (accountName.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
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
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = accountName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.account),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Dismiss button (X) — compact 32dp circle.
                        Box(
                            modifier = Modifier
                                .size(ProfilePopupDefaults.DismissButtonSize)
                                .clip(CircleShape)
                                .background(ProfilePopupDefaults.dismissButtonBackground())
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close_dialog),
                                modifier = Modifier.size(ProfilePopupDefaults.DismissIconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (accountName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                    }

                    // Menu items — each rendered as a pill (rounded container)
                    // styled like the rounded "song pills" on the history page.
                    items.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ProfilePopupDefaults.ItemSpacing),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ProfilePopupDefaults.ItemShape)
                                .background(ProfilePopupDefaults.itemBackground())
                                .clickable { item.onClick() }
                                .padding(
                                    horizontal = ProfilePopupDefaults.ItemPaddingHorizontal,
                                    vertical = ProfilePopupDefaults.ItemPaddingVertical,
                                ),
                        ) {
                            BadgedBox(badge = {
                                if (item.showBadge) Badge()
                            }) {
                                Icon(
                                    painter = painterResource(item.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(ProfilePopupDefaults.ItemIconSize),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

data class ProfileMenuItem(
    val icon: Int,
    val label: String,
    val showBadge: Boolean = false,
    val onClick: () -> Unit,
)
