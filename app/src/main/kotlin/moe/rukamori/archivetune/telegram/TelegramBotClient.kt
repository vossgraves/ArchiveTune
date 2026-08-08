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
import kotlin.time.Duration.Companion.seconds

object TelegramBotClient {
    private const val TAG = "TelegramBotClient"

    /** How long to wait for a bot's first audio reply before giving up. */
    val BOT_REPLY_TIMEOUT = 60.seconds

    /**
     * Once we've received the first track, how long to keep listening for additional tracks before
     * returning. Each new track extends the window by this amount, so a bot that streams a 10-track
     * album in 2-second bursts will capture all of them as long as no two tracks are more than this
     * far apart.
     */
    private const val POST_TRACK_GRACE_MS = 5_000L

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
        if (user != null && !user.isBot) {
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
            TdApi.LinkPreviewOptions(true, null, false, false, false),
            false,
        )
        return TelegramClient.send(
            TdApi.SendMessage(chatId, 0, null, null, null, input),
        )
    }

    // ------------------------------------------------------------------
    // Reply collection
    // ------------------------------------------------------------------

    /**
     * Collects audio-carrying messages that arrive on [chatId] within [BOT_REPLY_TIMEOUT]. Stops
     * early once [expectedCount] audio messages have been collected, OR — when expectedCount is 0
     * — once no new audio has arrived for [POST_TRACK_GRACE_MS] after the most-recent track (so
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
     * by [POST_TRACK_GRACE_MS], which extends on each new arrival so a fast burst is fully
     * captured. The collector job is always cancelled in a finally block before returning.
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
            var graceDeadlineMs = System.currentTimeMillis() + POST_TRACK_GRACE_MS
            while (true) {
                val remainingMs = (graceDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
                val next = withTimeoutOrNull(remainingMs) { channel.receive() } ?: break
                results += next
                if (expectedCount > 0 && results.size >= expectedCount) break
                graceDeadlineMs = System.currentTimeMillis() + POST_TRACK_GRACE_MS
            }
            return results
        } finally {
            collectJob.cancel()
        }
    }

    // ------------------------------------------------------------------
    // Forwarding
    // ------------------------------------------------------------------

    /**
     * Forwards [messageIds] (originally received in [fromChatId]) to [toChatId]. Returns the list
     * of newly created forwarded messages. Used to push a bot's audio reply into the user's own
     * Telegram channel so the user's library stays in sync.
     *
     * Implementation note: TdApi.ForwardMessages in TDLib 1.8+ has the signature
     *   ForwardMessages(chatId, messageThreadId, fromChatId, messageIds, options, sendCopy, removeCaption)
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
                0,
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
