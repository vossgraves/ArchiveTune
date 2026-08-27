/*
 * ArchiveTune (2026)
 * © ArchiveTuneFork contributors — github.com/vossgraves/ArchiveTune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Shared circular avatar for Telegram chats (channels + bots). Shows the inline minithumbnail
 * immediately (it's embedded in the chat object, so zero network latency), then upgrades to the
 * full-resolution small photo once TDLib finishes downloading it. Falls back to a generic chat
 * icon when the chat has no photo at all.
 */

package app.atf.media.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.atf.media.R
import app.atf.media.telegram.TelegramClient
import java.io.File

/**
 * Circular Telegram chat avatar.
 *
 * @param photoMinithumbnail Inline JPEG bytes (typically ~40×40). Shown instantly while the
 *   full photo downloads. May be null.
 * @param photoFileId TDLib file id of the small photo. When > 0, the full photo is downloaded
 *   via [TelegramClient.downloadFileBlocking] and replaces the minithumbnail. 0 = no photo.
 * @param size Diameter of the avatar circle. Defaults to 48.dp.
 */
@Composable
fun TelegramChatAvatar(
    photoMinithumbnail: ByteArray?,
    photoFileId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    var fullPhotoPath by remember(photoFileId) { mutableStateOf<String?>(null) }

    // Download the full-resolution small photo in the background. TDLib caches it, so repeat
    // calls with the same fileId are cheap (return the cached path immediately).
    LaunchedEffect(photoFileId) {
        if (photoFileId > 0) {
            val path = runCatching { TelegramClient.downloadFileBlocking(photoFileId) }.getOrNull()
            if (path != null && File(path).exists()) {
                fullPhotoPath = path
            }
        }
    }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val fullPhoto = fullPhotoPath
        when {
            // Full photo downloaded — best quality.
            fullPhoto != null -> {
                AsyncImage(
                    model = File(fullPhoto),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Minithumbnail available — show it as a placeholder while the full photo downloads
            // (or permanently if the chat has no full photo).
            photoMinithumbnail != null -> {
                AsyncImage(
                    model = photoMinithumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // No photo at all — generic chat icon.
            else -> {
                Icon(
                    painter = painterResource(R.drawable.solar_chat_round_linear),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
