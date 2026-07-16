/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DiscordAvatarUrlKey
import moe.rukamori.archivetune.constants.DiscordNameKey
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.DiscordUsernameKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.LastFMSessionKey
import moe.rukamori.archivetune.constants.LastFMUsernameKey
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * "Accounts" section for the Integration screen.
 *
 * Shows a card per connected service. YouTube Music is always first;
 * Last.fm and Discord appear below it when connected.
 */
@Composable
fun IntegrationAccountCards(navController: NavController) {
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val (accountName) = rememberPreference(AccountNameKey, "")
    val (accountHandle) = rememberPreference(AccountChannelHandleKey, "")

    val (lastFmSession) = rememberPreference(LastFMSessionKey, "")
    val (lastFmUsername) = rememberPreference(LastFMUsernameKey, "")

    val (discordToken) = rememberPreference(DiscordTokenKey, "")
    val (discordName) = rememberPreference(DiscordNameKey, "")
    val (discordUsername) = rememberPreference(DiscordUsernameKey, "")
    val (discordAvatarUrl) = rememberPreference(DiscordAvatarUrlKey, "")

    val youtubeConnected = innerTubeCookie.isNotBlank()
    val lastFmConnected = lastFmSession.isNotBlank()
    val discordConnected = discordToken.isNotBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccountCard(
            iconRes = R.drawable.music_note,
            title = stringResource(R.string.youtube_music_account),
            connected = youtubeConnected,
            detail =
                when {
                    !youtubeConnected -> null
                    accountName.isNotBlank() -> stringResource(R.string.account_signed_in_as, accountName)
                    accountHandle.isNotBlank() -> stringResource(R.string.account_signed_in_as, accountHandle)
                    else -> null
                },
            onClick = { navController.navigate("settings/account") },
        )

        if (lastFmConnected) {
            AccountCard(
                iconRes = R.drawable.token,
                title = stringResource(R.string.lastfm_integration),
                connected = true,
                detail =
                    if (lastFmUsername.isNotBlank()) {
                        stringResource(R.string.account_signed_in_as, lastFmUsername)
                    } else {
                        null
                    },
                onClick = { navController.navigate("settings/lastfm") },
            )
        }

        if (discordConnected) {
            AccountCard(
                iconRes = R.drawable.discord,
                avatarUrl = discordAvatarUrl.takeIf { it.isNotBlank() },
                title = stringResource(R.string.discord_integration),
                connected = true,
                detail =
                    when {
                        discordName.isNotBlank() -> stringResource(R.string.account_signed_in_as, discordName)
                        discordUsername.isNotBlank() -> stringResource(R.string.account_signed_in_as, discordUsername)
                        else -> null
                    },
                onClick = { navController.navigate("settings/discord") },
            )
        }
    }
}

@Composable
private fun AccountCard(
    iconRes: Int,
    title: String,
    connected: Boolean,
    detail: String?,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        detail
                            ?: stringResource(
                                if (connected) R.string.account_connected else R.string.account_not_connected,
                            ),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
