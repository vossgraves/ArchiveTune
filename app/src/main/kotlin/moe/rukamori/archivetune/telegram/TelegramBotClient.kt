/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * TDLib wrapper for the "Telegram bots" feature. Bots are private 1:1 chats with a Telegram bot
 * account. The user pastes a song link, the app sends it as a text message to the bot via
 * [TdApi.SendMessage], and then listens on a per-chat SharedFlow of incoming messages
 * (driven by [TdApi.UpdateNewMessage] in TelegramClient.onUpdate) until the bot replies with an
 * audio/document message — that reply becomes a playable [TelegramTrack].
 *
 * Forwarding to the user's own channel uses [TdApi.ForwardMessages] so the audio bytes aren't
 * re-uploaded (Telegram copies the file server-side) — this matches the user's spec: "if I add it
 * to my telegram playlist the song should also get forwarded to my own channel automatically".
 *
 * A 60s timeout caps how long we wait for a bot reply. Bots that stream "a lot of files" (e.g.
 * a Spotify-album link returns one message per track) all arrive on the same SharedFlow and are
 * collected into the result list.
 *
 * Inline-keyboard support: many music bots reply to a song link with a message that has a
 * [TdApi.ReplyMarkupInlineKeyboard] ("Choose quality: ALAC / AAC / Cancel") instead of the audio
 * directly. The user must tap one of the buttons to actually trigger the audio download. This
 * file exposes [collectBotReplies] which returns BOTH audio tracks and inline-keyboard prompts,
 * and [clickInlineButton] which sends a [TdApi.GetCallbackQueryAnswer] to simulate tapping a
 * button — after which the bot sends the actual audio file, which the caller collects with
 * another [collectBotReplies] cycle.
 */

package moe.rukamori.archivetune.telegram

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

object TelegramBotClient {
    private const val TAG = "TelegramBotClient"

    /** How long to wait for a bot's first reply before giving up. */
    val BOT_REPLY_TIMEOUT = 60.seconds

    /**
     * Once we've received the first reply (track or inline prompt), how long to keep listening for
     * additional replies before returning. Each new reply extends the window by this amount, so a
     * bot that streams a 10-track album in 2-second bursts will capture all of them as long as no
     * two replies are more than this far apart.
     */
    private const val POST_REPLY_GRACE_MS = 5_000L

    /**
     * Per-chat-id flow of incoming messages. Listeners register by calling [messagesForChat];
     * TelegramClient.onUpdate pushes new messages here via [onNewMessage].
     *
     * We use a SharedFlow with replay=0 and extraBufferCapacity=64 so a fast burst of bot replies
     * (e.g. an album returning 10 tracks) is not lost while the collector is processing the first.
     */
    private val chatMessageFlows = ConcurrentHashMap<Long, MutableSharedFlow<TdApi.Message>>()

    fun messagesForChat(chatId: Long): SharedFlow<TdApi.Message> =
        chatMessageFlows.getOrPut(chatId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }.asSharedFlow()

    /** Called from TelegramClient.onUpdate for every UpdateNewMessage — routes to the per-chat flow. */
    internal fun onNewMessage(message: TdApi.Message) {
        chatMessageFlows[message.chatId]?.tryEmit(message)
    }

    /** Drops the in-memory flow for a chat. Safe to call when leaving a bot chat screen. */
    fun forgetChat(chatId: Long) {
        chatMessageFlows.remove(chatId)
    }

    // ------------------------------------------------------------------
    // Bot resolution + message sending
    // ------------------------------------------------------------------

    /**
     * Resolves a bot by its public @username to a TDLib chat. The first call hits Telegram's
     * SearchPublicChat; subsequent calls return the cached chat. Returns null if the username
     * doesn't resolve or isn't a bot.
     */
    suspend fun resolveBot(username: String): TdApi.Chat? {
        val cleaned = username.removePrefix("@").trim().lowercase()
        if (cleaned.isEmpty()) return null
        val chat = runCatching {
            TelegramClient.send(TdApi.SearchPublicChat(cleaned))
        }.getOrNull() ?: return null
        // Verify it's a bot chat — refuse to silently DM a real user.
        if (chat.type !is TdApi.ChatTypePrivate) {
            Timber.tag(TAG).w("resolveBot: %s is not a private/bot chat (type=%s)", cleaned, chat.type)
            return null
        }
        // TDLib's ChatTypePrivate doesn't expose is-bot directly; fetch the user to confirm.
        val userId = (chat.type as TdApi.ChatTypePrivate).userId
        val user = runCatching { TelegramClient.send(TdApi.GetUser(userId)) }.getOrNull()
        if (user != null && user.type !is TdApi.UserTypeBot) {
            Timber.tag(TAG).w("resolveBot: @%s is a user, not a bot — refusing", cleaned)
            return null
        }
        return chat
    }

    /**
     * Sends the given text to [chatId] and returns the sent message. The bot's reply arrives
     * asynchronously via [messagesForChat].
     */
    suspend fun sendTextMessage(chatId: Long, text: String): TdApi.Message {
        val input = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()),
            // null = use the user's default link_preview setting; the bot reads the URL from the
            // message text directly, so preview-on/off is purely a cosmetic concern here.
            null,
            false,
        )
        // TDLib 1.8.30+ SendMessage signature: (chatId, MessageTopic topicId, InputMessageReplyTo,
        // MessageSendOptions, ReplyMarkup, InputMessageContent). Pass null topicId for the default
        // (non-threaded) topic of a 1:1 bot chat.
        return TelegramClient.send(
            TdApi.SendMessage(chatId, null, null, null, null, input),
        )
    }

    // ------------------------------------------------------------------
    // Reply collection
    // ------------------------------------------------------------------

    /**
     * Collects audio-carrying messages that arrive on [chatId] within [BOT_REPLY_TIMEOUT]. Stops
     * early once [expectedCount] audio messages have been collected, OR — when expectedCount is 0
     * — once no new audio has arrived for [POST_REPLY_GRACE_MS] after the most-recent track (so
     * single-track bots don't make the user wait the full 60s, while multi-track bots that burst-
     * send a handful of files within a few seconds still capture all of them).
     *
     * - [afterMessageId] excludes messages with id <= this value (so we don't re-process messages
     *   that existed before the user pressed Send).
     * - Non-audio messages (text/typing/photo) are ignored — bots often send a "Searching…" text
     *   first, then the audio.
     *
     * Implementation: subscribes to the SharedFlow once and pipes emissions into an unlimited
     * Channel. A first-channel-read is gated by [BOT_REPLY_TIMEOUT]; subsequent reads are gated
     * by [POST_REPLY_GRACE_MS], which extends on each new arrival so a fast burst is fully
     * captured. The collector job is always cancelled in a finally block before returning.
     *
     * NOTE: this method IGNORES inline-keyboard prompts. Use [collectBotReplies] for the full
     * flow that also surfaces quality-picker prompts.
     */
    suspend fun collectAudioReplies(
        chatId: Long,
        afterMessageId: Long,
        expectedCount: Int,
    ): List<TelegramTrack> {
        val channel = Channel<TelegramTrack>(Channel.UNLIMITED)
        val collectJob = CoroutineScope(coroutineContext).launch {
            try {
                messagesForChat(chatId).collect { message ->
                    if (message.id <= afterMessageId) return@collect
                    val track = TelegramClient.messageToTrack(message) ?: return@collect
                    channel.send(track)
                }
            } finally {
                channel.close()
            }
        }

        try {
            // Step 1: wait for the first track (up to BOT_REPLY_TIMEOUT).
            val first = withTimeoutOrNull(BOT_REPLY_TIMEOUT) { channel.receive() }
                ?: return emptyList()
            if (expectedCount == 1) return listOf(first)

            // Step 2: collect additional tracks, each one extending the grace window.
            val results = mutableListOf(first)
            var graceDeadlineMs = System.currentTimeMillis() + POST_REPLY_GRACE_MS
            while (true) {
                val remainingMs = (graceDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
                val next = withTimeoutOrNull(remainingMs) { channel.receive() } ?: break
                results += next
                if (expectedCount > 0 && results.size >= expectedCount) break
                graceDeadlineMs = System.currentTimeMillis() + POST_REPLY_GRACE_MS
            }
            return results
        } finally {
            collectJob.cancel()
        }
    }

    /**
     * Full bot-reply collector — surfaces BOTH audio tracks AND inline-keyboard prompts (so the
     * UI can react when the bot asks the user to pick a quality / format).
     *
     * Stops when:
     *  - No reply of any kind arrives within [BOT_REPLY_TIMEOUT] → returns emptyList()
     *  - At least one reply arrived, then no new reply for [POST_REPLY_GRACE_MS] → returns all
     *    collected replies in arrival order
     *
     * Each [BotReply.Track] wraps a [TelegramTrack]; each [BotReply.Prompt] wraps a
     * [TelegramBotPrompt] (with the inline keyboard buttons). The caller typically:
     *   1. Calls [collectBotReplies] after sending the song link.
     *   2. Renders each prompt's buttons.
     *   3. When the user taps a button, calls [clickInlineButton] and then calls
     *      [collectBotReplies] again with `afterMessageId = prompt.messageId` to collect the
     *      audio that the bot sends in response to the button click.
     */
    suspend fun collectBotReplies(
        chatId: Long,
        afterMessageId: Long,
    ): List<BotReply> {
        val channel = Channel<BotReply>(Channel.UNLIMITED)
        val collectJob = CoroutineScope(coroutineContext).launch {
            try {
                messagesForChat(chatId).collect { message ->
                    if (message.id <= afterMessageId) return@collect
                    val reply = messageToBotReply(message) ?: return@collect
                    channel.send(reply)
                }
            } finally {
                channel.close()
            }
        }

        try {
            // Step 1: wait for the first reply (up to BOT_REPLY_TIMEOUT).
            val first = withTimeoutOrNull(BOT_REPLY_TIMEOUT) { channel.receive() }
                ?: return emptyList()

            // Step 2: collect additional replies, each one extending the grace window.
            val results = mutableListOf(first)
            var graceDeadlineMs = System.currentTimeMillis() + POST_REPLY_GRACE_MS
            while (true) {
                val remainingMs = (graceDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
                val next = withTimeoutOrNull(remainingMs) { channel.receive() } ?: break
                results += next
                graceDeadlineMs = System.currentTimeMillis() + POST_REPLY_GRACE_MS
            }
            return results
        } finally {
            collectJob.cancel()
        }
    }

    /**
     * Converts a TDLib [TdApi.Message] into either a [BotReply.Track] (audio/document) or a
     * [BotReply.Prompt] (any message with an inline keyboard, e.g. "Choose quality: ALAC / AAC").
     * Returns null for messages that are neither (e.g. "Searching…" text replies, typing
     * indicators, photos).
     *
     * Inline-keyboard prompts are detected via [TdApi.Message.replyMarkup] being a
     * [TdApi.ReplyMarkupInlineKeyboard] with at least one row. Each button is mapped to a
     * [TelegramBotPromptButton] carrying either a callback payload (the common case — tapping it
     * triggers a [TdApi.GetCallbackQueryAnswer]) or a URL (e.g. "HQ Artwork" buttons that open
     * an external link).
     */
    private fun messageToBotReply(message: TdApi.Message): BotReply? {
        // Audio/document message → track.
        val track = TelegramClient.messageToTrack(message)
        if (track != null) return BotReply.Track(track)

        // Anything with an inline keyboard → prompt.
        val markup = message.replyMarkup as? TdApi.ReplyMarkupInlineKeyboard ?: return null
        if (markup.rows.isEmpty()) return null
        val rows = markup.rows.mapNotNull { row ->
            val buttons = row.mapNotNull { button -> button.toPromptButton() }
            if (buttons.isEmpty()) null else buttons
        }
        if (rows.isEmpty()) return null

        val text = (message.content as? TdApi.MessageText)?.text?.text.orEmpty()
        return BotReply.Prompt(
            TelegramBotPrompt(
                chatId = message.chatId,
                messageId = message.id,
                text = text,
                rows = rows,
            ),
        )
    }

    private fun TdApi.InlineKeyboardButton.toPromptButton(): TelegramBotPromptButton? {
        val label = text.takeIf { it.isNotBlank() } ?: return null
        return when (val type = type) {
            is TdApi.InlineKeyboardButtonTypeCallback ->
                TelegramBotPromptButton(text = label, callbackData = type.data)
            is TdApi.InlineKeyboardButtonTypeUrl ->
                TelegramBotPromptButton(text = label, url = type.url)
            else -> null
        }
    }

    /**
     * Simulates tapping an inline-keyboard button. Sends [TdApi.GetCallbackQueryAnswer] to the
     * bot, which causes it to process the chosen option and (typically) replies with the audio file
     * the user actually wanted. The caller should then call [collectBotReplies] with
     * `afterMessageId = promptMessageId` to collect the resulting audio.
     *
     * TDLib's GetCallbackQueryAnswer takes a [TdApi.CallbackQueryPayload] (an abstract class) —
     * for the standard inline-keyboard callback (the kind music bots use), the concrete subclass
     * is [TdApi.CallbackQueryPayloadData] which wraps the raw [ByteArray] payload that came from
     * [TdApi.InlineKeyboardButtonTypeCallback.data].
     *
     * Returns the [TdApi.CallbackQueryAnswer] the bot returned (may carry a toast text, an alert,
     * or a URL — typically empty for music bots). Returns null if the request failed.
     */
    suspend fun clickInlineButton(
        chatId: Long,
        messageId: Long,
        callbackData: ByteArray,
    ): TdApi.CallbackQueryAnswer? = runCatching {
        TelegramClient.send(
            TdApi.GetCallbackQueryAnswer(
                chatId,
                messageId,
                TdApi.CallbackQueryPayloadData(callbackData),
            ),
        )
    }.onFailure { e ->
        Timber.tag(TAG).w(e, "clickInlineButton: callback query failed")
    }.getOrNull()

    // ------------------------------------------------------------------
    // Forwarding
    // ------------------------------------------------------------------

    /**
     * Forwards [messageIds] (originally received in [fromChatId]) to [toChatId]. Returns the list
     * of newly created forwarded messages. Used to push a bot's audio reply into the user's own
     * Telegram channel so the user's library stays in sync.
     *
     * Implementation note: TdApi.ForwardMessages in TDLib 1.8.30+ has the signature
     *   ForwardMessages(chatId, MessageTopic topicId, fromChatId, messageIds, options, sendCopy, removeCaption)
     * topicId=null targets the default topic (regular non-threaded chat).
     * sendCopy=false keeps the original sender attribution (matches Telegram's "Forward" UI).
     */
    suspend fun forwardMessages(
        toChatId: Long,
        fromChatId: Long,
        messageIds: LongArray,
    ): List<TdApi.Message> {
        if (messageIds.isEmpty()) return emptyList()
        val result = TelegramClient.send(
            TdApi.ForwardMessages(
                toChatId,
                null,
                fromChatId,
                messageIds,
                null,
                false,
                false,
            ),
        )
        // TDLib returns ForwardMessages{messages: Array<Message>}
        return result.messages?.toList() ?: emptyList()
    }

    /**
     * Convenience: forwards a single message. Returns the new message id, or 0 on failure.
     */
    suspend fun forwardMessage(
        toChatId: Long,
        fromChatId: Long,
        messageId: Long,
    ): Long {
        val forwarded = forwardMessages(toChatId, fromChatId, longArrayOf(messageId))
        return forwarded.firstOrNull()?.id ?: 0L
    }
}

// ------------------------------------------------------------------
// Models for inline-keyboard prompts (quality pickers, format pickers, etc.)
// ------------------------------------------------------------------

/** A single button on a bot's inline keyboard. */
data class TelegramBotPromptButton(
    val text: String,
    /** Bytes from [TdApi.InlineKeyboardButtonTypeCallback.data] — pass to [TelegramBotClient.clickInlineButton]. */
    val callbackData: ByteArray? = null,
    /** URL for [TdApi.InlineKeyboardButtonTypeUrl] buttons (e.g. "HQ Artwork"). */
    val url: String? = null,
) {
    val isCallback: Boolean get() = callbackData != null
    val isUrl: Boolean get() = url != null

    override fun equals(other: Any?): Boolean =
        other is TelegramBotPromptButton &&
            other.text == text &&
            other.url == url &&
            ((other.callbackData == null) == (callbackData == null)) &&
            ((callbackData == null) || callbackData!!.contentEquals(other.callbackData))

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (callbackData?.contentHashCode() ?: 0)
        return result
    }
}

/** A bot message that has an inline keyboard (e.g. "Choose quality: ALAC / AAC / Cancel"). */
data class TelegramBotPrompt(
    val chatId: Long,
    val messageId: Long,
    /** The message's text body, if any (often empty for photo + button layouts). */
    val text: String,
    /** Rows of buttons, matching the original layout the bot sent. */
    val rows: List<List<TelegramBotPromptButton>>,
) {
    /** Flat list of all buttons across all rows — convenient for single-row pickers. */
    val allButtons: List<TelegramBotPromptButton> get() = rows.flatten()

    /** Heuristic: a button whose label looks like a "cancel" action (so the UI can render it last). */
    fun isCancelButton(button: TelegramBotPromptButton): Boolean {
        val t = button.text.lowercase()
        return t == "cancel" || t == "✕" || t == "x" || t.contains("cancel")
    }
}

/** Either a playable audio track or an inline-keyboard prompt from the bot. */
sealed interface BotReply {
    data class Track(val track: TelegramTrack) : BotReply
    data class Prompt(val prompt: TelegramBotPrompt) : BotReply
}
