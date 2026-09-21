/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Listen Together chat building blocks: avatars, typing indicator, message
 * bubbles with swipe-to-reply, reactions, the Instagram-style anchored action
 * popup (morph animation + liquid glass) and the full emoji picker.
 */

package moe.rukamori.archivetune.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalListenTogetherManager
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.listentogether.ChatMessagePayload
import moe.rukamori.archivetune.listentogether.RepliedMessage
import moe.rukamori.archivetune.listentogether.TypingUser
import moe.rukamori.archivetune.ui.component.LocalLiquidGlassBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

/** Quick-reaction strip shown first in the anchored popup. */
internal val QuickReactionEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

/** Full emoji catalogue for the picker sheet, grouped for headers. */
internal val EmojiCategories: List<Pair<String, List<String>>> = listOf(
    "Smileys" to listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇",
        "🥰", "😍", "🤩", "😘", "😗", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪", "😝",
        "🤗", "🤭", "🤫", "🤔", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😌",
        "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🥵", "🥶", "🥴", "😵", "🤯", "🤠",
        "🥳", "🥸", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺",
        "😢", "😭", "😱", "😖", "😣", "😞", "😩", "😫", "🥱",
    ),
    "Gestures" to listOf(
        "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👋", "🤚", "🖐", "✋", "🖖",
        "👏", "🙌", "👐", "🤲", "🤝", "🙏", "💪", "🦾", "✍️", "🤳", "👀", "🧠", "🫶",
    ),
    "Hearts" to listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞",
        "💓", "💗", "💖", "💘", "💝", "💟", "🌈",
    ),
    "Fun" to listOf(
        "🔥", "⭐", "✨", "💫", "⚡", "☄️", "💥", "🌸", "🎉", "🎊", "🎈", "🎁", "🏆",
        "🥇", "👑", "💎", "🎵", "🎶", "🎤", "🎸", "🥁", "🎧", "💿", "🕺", "💃",
    ),
    "Food & Nature" to listOf(
        "🍕", "🍔", "🍟", "🌭", "🍿", "🧂", "🥓", "🥚", "🍳", "🥞", "🧇", "🥨", "🍰",
        "🎂", "🍫", "🍩", "🍪", "☕", "🍵", "🧋", "🥤", "🍺", "🍷", "🥂", "🌺", "🌻",
        "🌹", "🥀", "🍀", "🌿", "🌙", "🌞", "🌝", "🌜",
    ),
    "Objects" to listOf(
        "📱", "💻", "⌨️", "🖥", "🖨", "🖱", "💾", "💿", "📀", "📷", "🎥", "📞", "☎️",
        "📟", "📠", "📺", "📻", "⏰", "⌛", "⏳", "🔋", "🔌", "💡", "🔦", "🕯", "🧸",
        "🎁", "🎯", "🎲", "🧩", "♠️", "♥️", "♦️", "♣️", "🃏", "🀄",
    ),
)

// Direct emoji glyphs throughout; no substitutions needed.
private fun emojiGlyph(raw: String): String = raw

private val ChatAvatarOptions = listOf(
    R.drawable.person, R.drawable.man, R.drawable.woman, R.drawable.man_1, R.drawable.man_2,
    R.drawable.man_3, R.drawable.man_4, R.drawable.man_5, R.drawable.man_6, R.drawable.woman_1,
    R.drawable.woman_2, R.drawable.woman_3, R.drawable.woman_4, R.drawable.luxury_women,
)

/**
 * Circular room-member avatar for the chat: custom broadcast picture first,
 * then the picked avatar-index asset, then the username initial.
 */
@Composable
internal fun ChatAvatar(
    userId: String,
    fallbackName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    val manager = LocalListenTogetherManager.current
    val context = LocalContext.current
    val roomState = manager?.roomState?.collectAsState()?.value
    val currentUserId = manager?.userId?.collectAsState()?.value
    val customAvatars = manager?.customAvatars?.collectAsState()?.value ?: emptyMap()

    val bytes =
        if (manager == null) null
        else if (userId == currentUserId) remember(manager, currentUserId) { manager.customAvatarFor(currentUserId) }
        else customAvatars[userId]
    val bitmap: Bitmap? =
        produceState<Bitmap?>(initialValue = null, bytes) {
            value =
                bytes?.let { encoded ->
                    withContext(Dispatchers.Default) {
                        runCatching { BitmapFactory.decodeByteArray(encoded, 0, encoded.size) }.getOrNull()
                    }
                }
        }.value

    val user = roomState?.users?.find { it.userId == userId }
    val avatarIndex = user?.avatarIndex ?: 0
    val isHost = user?.isHost == true
    val initial = (user?.username?.takeIf { it.isNotBlank() } ?: fallbackName).take(1).uppercase()

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    when {
                        isHost -> MaterialTheme.colorScheme.primary
                        userId == currentUserId -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                )
                .then(
                    if (outlined) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            avatarIndex == 0 -> {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color =
                        when {
                            isHost -> MaterialTheme.colorScheme.onPrimary
                            userId == currentUserId -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        },
                )
            }
            else -> {
                Image(
                    painter = painterResource(ChatAvatarOptions.getOrElse(avatarIndex) { R.drawable.person }),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
    }
}

/**
 * Typing indicator: the typing members' avatars layered on top of each other
 * (later members draw above earlier ones, separated by a surface outline),
 * followed by bouncing dots and the member names.
 */
@Composable
internal fun TypingIndicatorRow(
    typingUsers: List<TypingUser>,
    modifier: Modifier = Modifier,
) {
    if (typingUsers.isEmpty()) return
    val transition = rememberInfiniteTransition(label = "typing-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "typing-phase",
    )

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(((typingUsers.size - 1) * 13 + 20).dp).height(20.dp)) {
            typingUsers.take(5).forEachIndexed { index, typing ->
                ChatAvatar(
                    userId = typing.userId,
                    fallbackName = typing.username,
                    size = 20.dp,
                    outlined = index > 0,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (index * 13).dp)
                            .zIndex(index.toFloat()),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                val step = (phase + i * 0.33f) % 1f
                val bounce = sin(step * Math.PI).toFloat()
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 2.dp)
                            .offset(y = (-bounce * 3).dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * bounce)),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        val names =
            when {
                typingUsers.size == 1 -> typingUsers.first().username
                typingUsers.size == 2 -> "${typingUsers[0].username} & ${typingUsers[1].username}"
                else -> "${typingUsers.take(2).joinToString { it.username }}…"
            }
        Text(
            text = stringResource(R.string.listen_together_chat_typing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = names,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Data of the message whose anchored action popup is open. */
internal data class MessageActionTarget(
    val message: ChatMessagePayload,
    val bounds: Rect,
    val isMe: Boolean,
)

/**
 * Instagram-style action popup anchored to a long-pressed message bubble:
 * quick reactions, the full-emoji entry and reply/copy/edit/pin/delete, opening
 * with the lyrics-popup morph (spring scale + fade from the bubble edge) over a
 * liquid-glass backdrop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsPopup(
    target: MessageActionTarget,
    myUsername: String?,
    onReact: (String) -> Unit,
    onOpenEmojiPicker: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val backdrop: LayerBackdrop? = LocalLiquidGlassBackdrop.current

    var dismissed by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(0.35f) }
    val alphaAnim = remember { Animatable(0f) }

    var overlayWidthPx by remember { mutableIntStateOf(0) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    var popupWidthPx by remember { mutableIntStateOf(0) }
    var popupHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (dismissed) return@LaunchedEffect
        val scaleJob = scope.launch {
            scaleAnim.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
        }
        val alphaJob = scope.launch { alphaAnim.animateTo(1f, tween(180)) }
        scaleJob.join()
        alphaJob.join()
    }

    LaunchedEffect(dismissed) {
        if (!dismissed) return@LaunchedEffect
        val scaleJob = scope.launch {
            scaleAnim.animateTo(0.35f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
        }
        val alphaJob = scope.launch { alphaAnim.animateTo(0f, tween(160)) }
        scaleJob.join()
        alphaJob.join()
        onDismiss()
    }

    fun popupPlacement(): Pair<IntOffset, Boolean> {
        val marginPx = with(density) { 12.dp.toPx() }.toInt()
        val estimatedHeight =
            if (popupHeightPx > 0) popupHeightPx else with(density) { 112.dp.toPx() }.toInt()
        val estimatedWidth =
            if (popupWidthPx > 0) popupWidthPx else with(density) { 300.dp.toPx() }.toInt()
        val screenWidthPx = if (overlayWidthPx > 0) overlayWidthPx else estimatedWidth + 2 * marginPx
        val x =
            if (target.isMe) {
                (target.bounds.right.toInt() - estimatedWidth - 8).coerceAtLeast(marginPx)
            } else {
                (target.bounds.left.toInt() + 8).coerceAtMost(screenWidthPx - estimatedWidth - marginPx)
            }.coerceIn(marginPx, (screenWidthPx - estimatedWidth - marginPx).coerceAtLeast(marginPx))
        val below = target.bounds.bottom + 6 < overlayHeightPx - estimatedHeight - marginPx
        val y =
            if (below) {
                target.bounds.bottom.toInt() + 6
            } else {
                (target.bounds.top.toInt() - estimatedHeight - 6).coerceAtLeast(marginPx)
            }
        return IntOffset(x, y.coerceAtLeast(marginPx)) to below
    }

    val scrimAlpha = 0.25f * alphaAnim.value

    val frostedBlurModifier =
        remember(backdrop) {
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    effects = {
                        colorControls(saturation = 1.7f)
                        blur(20f.dp.toPx())
                        lens(
                            refractionHeight = 16f.dp.toPx(),
                            refractionAmount = 40f.dp.toPx(),
                        )
                    },
                    onDrawBackdrop = { drawBackdrop -> drawBackdrop() },
                    shape = { RoundedCornerShape(18.dp) },
                )
            } else {
                null
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    overlayWidthPx = size.width
                    overlayHeightPx = size.height
                }
                .background(Color.Black.copy(alpha = scrimAlpha))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!dismissed) dismissed = true },
                ),
    ) {
        val (offset, opensBelow) = popupPlacement()

        Column(
            modifier =
                Modifier
                    .offset { offset }
                    .onSizeChanged { size ->
                        popupWidthPx = size.width
                        popupHeightPx = size.height
                    }
                    .widthIn(max = 320.dp)
                    .graphicsLayer {
                        this.alpha = alphaAnim.value
                        this.scaleX = scaleAnim.value
                        this.scaleY = scaleAnim.value
                        this.transformOrigin =
                            TransformOrigin(
                                if (target.isMe) 0.92f else 0.08f,
                                if (opensBelow) 0f else 1f,
                            )
                        this.shadowElevation = 18.dp.toPx()
                        this.shape = RoundedCornerShape(18.dp)
                        this.clip = false
                    }
                    .then(
                        frostedBlurModifier
                            ?: Modifier.background(Color(0xFF1C1C1E).copy(alpha = alphaAnim.value)),
                    )
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Quick reactions row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                QuickReactionEmojis.forEach { emoji ->
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onReact(emoji)
                                        if (!dismissed) dismissed = true
                                    },
                                )
                                .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenEmojiPicker,
                            )
                            .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.listen_together_chat_all_emojis),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
            )

            // Action row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PopupActionChip(icon = R.drawable.reply, label = stringResource(R.string.listen_together_chat_reply_label)) {
                    onReply()
                    if (!dismissed) dismissed = true
                }
                PopupActionChip(icon = R.drawable.copy, label = stringResource(R.string.listen_together_chat_copy)) {
                    onCopy()
                    if (!dismissed) dismissed = true
                }
                if (target.isMe) {
                    PopupActionChip(icon = R.drawable.edit, label = stringResource(R.string.edit)) {
                        onEdit()
                        if (!dismissed) dismissed = true
                    }
                }
                PopupActionChip(
                    icon = R.drawable.push_pin,
                    label = stringResource(
                        if (target.message.pinned) R.string.listen_together_chat_unpin
                        else R.string.listen_together_chat_pin
                    ),
                ) {
                    onPinToggle()
                    if (!dismissed) dismissed = true
                }
                if (target.isMe) {
                    PopupActionChip(icon = R.drawable.delete, label = stringResource(R.string.delete)) {
                        onDelete()
                        if (!dismissed) dismissed = true
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupActionChip(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Full emoji picker sheet opened from the anchored popup's "All emojis" chip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiPickerSheet(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            modifier = Modifier.fillMaxWidth().height(420.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            EmojiCategories.forEach { (category, emojis) ->
                item(key = "header_$category") {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                gridItems(
                    items = emojis.map(::emojiGlyph),
                    key = { "$category$_" },
                ) { emoji ->
                    Box(
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onPick(emoji) },
                                )
                                .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

/** Horizontal chip row of the reactions already applied to a message. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionsRow(
    message: ChatMessagePayload,
    myUsername: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.reactions.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        message.reactions.forEach { (emoji, users) ->
            val mine = myUsername != null && users.contains(myUsername)
            Surface(
                onClick = { onToggle(emoji) },
                shape = RoundedCornerShape(999.dp),
                color =
                    if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor =
                    if (mine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                border =
                    androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color =
                            if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = emoji, fontSize = 14.sp)
                    if (users.size > 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = users.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One chat message: the sender's avatar before their name (incoming), a
 * swipe-to-reply bubble with a long-press action popup trigger, reply preview,
 * edited/pinned marks and the reaction chips.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    message: ChatMessagePayload,
    isMe: Boolean,
    myUsername: String?,
    onReply: (ChatMessagePayload) -> Unit,
    onLongPress: (ChatMessagePayload, Rect) -> Unit,
    onToggleReaction: (ChatMessagePayload, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bubbleBounds = remember { mutableStateOf(Rect.Zero) }
    val swipeOffset = remember { Animatable(0f) }
    val replyThresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipePx = with(density) { 130.dp.toPx() }

    val bubbleColor =
        if (isMe) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val textColor =
        if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    val replyBgColor =
        if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        if (!isMe) {
            ChatAvatar(
                userId = message.userId,
                fallbackName = message.username,
                size = 28.dp,
                modifier = Modifier.align(Alignment.Top),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (!isMe) {
                Text(
                    text = message.username,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                )
            }

            Box {
                // Reply hint revealed behind the bubble while swiping.
                val swipeProgress = (swipeOffset.value / replyThresholdPx).coerceIn(0f, 1f)
                Icon(
                    painter = painterResource(R.drawable.reply),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.75f * swipeProgress),
                    modifier =
                        Modifier
                            .align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)
                            .offset {
                                val padPx = with(this) { 6.dp.toPx() }
                                IntOffset(
                                    x = if (isMe) (swipeOffset.value + padPx).toInt() else -(swipeOffset.value + padPx).toInt(),
                                    y = 0,
                                )
                            }
                            .size(20.dp)
                            .graphicsLayer { alpha = swipeProgress },
                )

                Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp,
                    ),
                    tonalElevation = 2.dp,
                    modifier =
                        Modifier
                            .onGloballyPositioned { coordinates ->
                                bubbleBounds.value = coordinates.boundsInRoot()
                            }
                            .graphicsLayer {
                                translationX = if (isMe) -swipeOffset.value else swipeOffset.value
                            }
                            .pointerInput(isMe) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val target =
                                            (swipeOffset.value + if (isMe) -dragAmount else dragAmount)
                                                .coerceIn(0f, maxSwipePx)
                                        scope.launch { swipeOffset.snapTo(target) }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            if (swipeOffset.value > replyThresholdPx) {
                                                onReply(message)
                                            }
                                            swipeOffset.animateTo(
                                                0f,
                                                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                                            )
                                        }
                                    },
                                )
                            }
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                onLongClick = {
                                    onLongPress(message, bubbleBounds.value)
                                },
                            ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        message.replyTo?.let { reply ->
                            RepliedMessagePreview(reply, replyBgColor, textColor)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Text(
                            text = formatMessageWithLinks(message.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .align(Alignment.End)
                                    .padding(top = 2.dp),
                        ) {
                            if (message.pinned) {
                                Icon(
                                    painter = painterResource(R.drawable.push_pin),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (message.edited) {
                                Text(
                                    text = stringResource(R.string.listen_together_chat_edited),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = textColor.copy(alpha = 0.55f),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = formatTime(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }

            ReactionsRow(
                message = message,
                myUsername = myUsername,
                onToggle = { emoji -> onToggleReaction(message, emoji) },
                modifier =
                    Modifier.padding(
                        top = 3.dp,
                        start = if (isMe) 0.dp else 4.dp,
                        end = if (isMe) 4.dp else 0.dp,
                    ),
            )
        }
    }
}

@Composable
internal fun RepliedMessagePreview(
    reply: RepliedMessage,
    replyBgColor: Color,
    textColor: Color,
) {
    Surface(
        color = replyBgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.primary)
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = reply.username,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = reply.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = textColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Banner over the chat list showing the most recent pinned message. */
@Composable
internal fun PinnedBanner(
    message: ChatMessagePayload,
    onUnpin: () -> Unit,
    onJumpTo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .clickable(onClick = onJumpTo)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.push_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.listen_together_chat_pinned_banner),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${message.username}: ${message.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.listen_together_chat_unpin),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun formatMessageWithLinks(text: String): AnnotatedString {
    val context = LocalContext.current
    val ytMusicRegex = Regex("(https?://music\\.youtube\\.com/[\\w\\-\\.\\?&=\\%/]*)")
    val matches = ytMusicRegex.findAll(text)

    if (matches.none()) return AnnotatedString(text)

    return buildAnnotatedString {
        var lastIdx = 0
        for (match in matches) {
            append(text.substring(lastIdx, match.range.first))

            val url = match.value
            val linkAnnotation = LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold,
                    )
                ),
                linkInteractionListener = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            `package` = context.packageName
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Playing now", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            )

            withLink(linkAnnotation) {
                append(url)
            }

            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}

internal fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
