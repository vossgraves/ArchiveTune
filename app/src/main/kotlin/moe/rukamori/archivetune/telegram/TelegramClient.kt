/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Singleton wrapper around TDLib (org.drinkless.tdlib) that powers the Telegram channel streaming
 * integration: account login (phone → code → optional 2FA password), channel search across both the
 * public directory and the user's joined (including private) chats, paging through a channel's
 * audio/document messages, and partial-file access for streaming playback (see TelegramDataSource).
 *
 * The api_id/api_hash are baked in at build time (BuildConfig, see app/build.gradle.kts), so users
 * sign in with just a phone number. The session lives in TDLib's own database under
 * filesDir/telegram and survives restarts, so login is a one-time flow — but note that a cold start
 * needs a moment to restore it, so playback paths must use [awaitReady] rather than [isReady].
 */

package moe.rukamori.archivetune.telegram

import android.content.Context
import android.os.Build
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.BuildConfig
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Collections
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Authorization progress of the Telegram session, driven by TDLib's UpdateAuthorizationState. */
sealed interface TelegramAuthState {
    /** No TDLib client running (missing API credentials, or the session was closed). */
    data object Idle : TelegramAuthState

    /** Client created, waiting for TDLib to report the first authorization state. */
    data object Connecting : TelegramAuthState

    data object WaitPhoneNumber : TelegramAuthState

    data class WaitCode(
        val phoneNumber: String,
        /** Where the current code was delivered (Telegram app, SMS or a phone call). */
        val codeType: TelegramCodeType,
        /** True when TDLib offers a resend path (codeInfo.nextType != null). */
        val canResend: Boolean,
        /** Seconds the user must wait before a resend is accepted. */
        val resendTimeoutSeconds: Int,
    ) : TelegramAuthState

    data class WaitPassword(
        val passwordHint: String?,
    ) : TelegramAuthState

    data object Ready : TelegramAuthState

    data object LoggingOut : TelegramAuthState

    /** A TDLib auth state this integration doesn't implement (QR login, registration, …). */
    data class Unsupported(
        val stateName: String,
    ) : TelegramAuthState
}

/** Delivery channel of the current login code, for user-facing copy. */
enum class TelegramCodeType {
    TELEGRAM_APP,
    SMS,
    CALL,
    OTHER,
}

class TelegramApiException(
    val code: Int,
    message: String,
) : IOException("Telegram error $code: $message")

object TelegramClient {
    private const val TAG = "TelegramClient"

    /** TDLib download priority for actively-playing streams (1..32, higher = sooner). */
    const val STREAM_DOWNLOAD_PRIORITY = 32

    private val lock = Any()

    @Volatile
    private var client: Client? = null

    @Volatile
    private var appContext: Context? = null

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    /** Default per-request timeout. TDLib can silently stall on a dead network. */
    private const val REQUEST_TIMEOUT_MS = 30_000L

    /** How long a cold start may take to reach an authorized session before callers give up. */
    private const val READY_TIMEOUT_MS = 20_000L

    /**
     * Upper bound on cached chats. TDLib pushes UpdateNewChat for every chat in the user's list, so
     * an unbounded map grows with the account's chat count (tens of thousands for heavy users) and
     * is never trimmed until logout. This is a synchronized access-ordered LinkedHashMap, so reads
     * count as uses and the least-recently-used chat is evicted past the cap; on a miss we simply
     * fall back to a GetChat round trip.
     */
    private const val CHAT_CACHE_MAX = 256

    /** Chats fetched per LoadChats call, and the page cap that bounds [ensureChatsLoaded]. */
    private const val CHAT_LIST_PAGE_SIZE = 500
    private const val CHAT_LIST_PAGES = 20

    /**
     * Whether the user's chat list has been loaded into TDLib this session. Reset on logout, since a
     * new account starts with nothing loaded.
     */
    @Volatile
    private var chatsLoaded = false

    /** Chats TDLib has pushed via UpdateNewChat, so channel lookups avoid extra round trips. */
    private val chatCache: MutableMap<Long, TdApi.Chat> =
        Collections.synchronizedMap(
            object : LinkedHashMap<Long, TdApi.Chat>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TdApi.Chat>): Boolean =
                    size > CHAT_CACHE_MAX
            },
        )

    /**
     * File-download progress pushed by TDLib's UpdateFile, keyed by file id. Streaming reads observe
     * this instead of polling GetFile on a fixed interval: TDLib already reports every change, so a
     * read wakes exactly when new bytes land rather than up to one poll interval later.
     */
    private val _fileUpdates = MutableSharedFlow<TdApi.File>(extraBufferCapacity = 64)
    val fileUpdates: SharedFlow<TdApi.File> = _fileUpdates.asSharedFlow()

    val isReady: Boolean
        get() = _authState.value is TelegramAuthState.Ready

    /**
     * Starts the TDLib client if it isn't running yet. Safe to call from any thread. The app's
     * Telegram api_id/api_hash are baked in at build time (BuildConfig), so no user credential
     * entry is needed — the authorization flow advances straight to the phone-number step via
     * [authState]. Returns false only when the build shipped without valid credentials.
     */
    fun ensureStarted(context: Context): Boolean {
        val ctx = context.applicationContext
        synchronized(lock) {
            if (client != null) return true
            if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) return false
            appContext = ctx
            runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }
            _authState.value = TelegramAuthState.Connecting
            client =
                Client.create(
                    { update -> onUpdate(update) },
                    { throwable -> Timber.tag(TAG).e(throwable, "TDLib update handler exception") },
                    { throwable -> Timber.tag(TAG).e(throwable, "TDLib exception") },
                )
            return true
        }
    }

    /**
     * Stops the client and wipes the on-device Telegram session (TDLib LogOut deletes its own
     * database).
     */
    suspend fun logOut() {
        runCatching { send(TdApi.LogOut()) }
            .onFailure { Timber.tag(TAG).w(it, "logOut failed") }
    }

    // ------------------------------------------------------------------
    // Auth flow
    // ------------------------------------------------------------------

    /**
     * Submits a phone number. TDLib accepts this both from the initial WaitPhoneNumber state and
     * while already in WaitCode, so it doubles as the "edit phone number" action — passing a new
     * number restarts the code flow.
     */
    suspend fun submitPhoneNumber(phoneNumber: String) {
        send(TdApi.SetAuthenticationPhoneNumber(phoneNumber.trim(), null))
    }

    suspend fun submitCode(code: String) {
        send(TdApi.CheckAuthenticationCode(code.trim()))
    }

    suspend fun submitPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password))
    }

    /**
     * Requests a new login code. TDLib only allows this once its resend timeout has elapsed and a
     * next delivery method exists; callers should gate on [TelegramAuthState.WaitCode.canResend]
     * and the countdown before invoking this.
     */
    suspend fun resendCode() {
        send(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest()))
    }

    suspend fun getMe(): TdApi.User = send(TdApi.GetMe())

    // ------------------------------------------------------------------
    // Channel search + audio listing
    // ------------------------------------------------------------------

    /**
     * Searches for channels the user can actually read, keeping only channels/supergroups. Accepts
     * plain queries, @usernames, t.me links (including private `t.me/c/...` and `t.me/+invite`
     * links) and matches against the user's own joined chats.
     *
     * Ordering matters: the user's joined chats come first, because private channels are invisible
     * to Telegram's public directory. A private channel has no username and is not indexed, so
     * SearchPublicChat(s) can never return it — searching only the public directory made every
     * private channel unreachable even when the user was a member. SearchChatsOnServer covers the
     * user's memberships (public and private alike) and needs no invite link.
     */
    suspend fun searchChannels(query: String): List<TelegramChannel> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val chatIds = linkedSetOf<Long>()

        // A t.me/c/<internalId> link points at a private channel by internal id; convert it to a
        // supergroup chat id directly, since no username lookup can resolve it.
        privateChannelChatId(trimmed)?.let { chatIds += it }

        // Exact username / t.me link lookup so pasting a public link always works.
        extractUsername(trimmed)?.let { username ->
            runCatching { send(TdApi.SearchPublicChat(username)) }
                .onSuccess { chatIds += it.id }
        }

        // Make sure TDLib knows the user's chats, otherwise the local search below has nothing to
        // match against and private channels stay invisible.
        ensureChatsLoaded()

        // The user's own joined chats — the only way to reach private channels.
        runCatching { send(TdApi.SearchChatsOnServer(trimmed, 50)) }
            .onSuccess { chatIds += it.chatIds.toList() }
        // Locally-known chats too, so results still appear when offline.
        runCatching { send(TdApi.SearchChats(trimmed, 50)) }
            .onSuccess { chatIds += it.chatIds.toList() }

        // Public directory last: least specific, and already-added ids keep their earlier position.
        runCatching { send(TdApi.SearchPublicChats(trimmed)) }
            .onSuccess { chatIds += it.chatIds.toList() }

        return chatIds.mapNotNull { chatId ->
            runCatching { toChannel(getChat(chatId)) }.getOrNull()
        }
    }

    /**
     * Resolves a private channel invite link to a chat id. `t.me/c/<internalId>/<messageId>` encodes
     * the supergroup's internal id, which maps to a chat id by the documented -100 prefix rule.
     * `t.me/+hash` / `t.me/joinchat/<hash>` links are resolved through TDLib, which returns the chat
     * id when the user has already joined.
     */
    private suspend fun privateChannelChatId(query: String): Long? {
        Regex("t(?:elegram)?\\.me/c/(\\d{1,19})", RegexOption.IGNORE_CASE)
            .find(query)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.let { internalId ->
                // Supergroup/channel chat ids are the internal id negated behind a -100 prefix.
                return "-100$internalId".toLongOrNull()
            }

        val inviteHash =
            Regex("t(?:elegram)?\\.me/(?:\\+|joinchat/)([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
                .find(query)
                ?.groupValues
                ?.get(1)
                ?: return null
        // Only inspects the invite link; it does not join on the user's behalf.
        return runCatching { send(TdApi.CheckChatInviteLink("https://t.me/+$inviteHash")) }
            .getOrNull()
            ?.chatId
            ?.takeIf { it != 0L }
    }

    suspend fun getChat(chatId: Long): TdApi.Chat = chatCache[chatId] ?: send(TdApi.GetChat(chatId))

    suspend fun channelInfo(chatId: Long): TelegramChannel? =
        runCatching { toChannel(getChat(chatId)) }.getOrNull()

    /**
     * Fetches one page of a channel's audio files. Audio messages and (optionally) audio-typed
     * document messages are two separate TDLib filters, so both are queried and merged newest
     * first; the cursors advance independently.
     */
    suspend fun fetchAudioPage(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
        filter: TdApi.SearchMessagesFilter,
    ): TelegramAudioPage {
        val found =
            send(
                TdApi.SearchChatMessages(
                    chatId,
                    null,
                    "",
                    null,
                    fromMessageId,
                    0,
                    limit,
                    filter,
                ),
            )
        return TelegramAudioPage(
            tracks = found.messages.mapNotNull(::messageToTrack),
            nextFromMessageId = found.nextFromMessageId,
        )
    }

    fun messageToTrack(message: TdApi.Message): TelegramTrack? =
        when (val content = message.content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                TelegramTrack(
                    chatId = message.chatId,
                    messageId = message.id,
                    fileId = audio.audio.id,
                    fileUniqueId = audio.audio.remote?.uniqueId.orEmpty(),
                    title = audio.title.orEmpty(),
                    performer = audio.performer?.takeIf(String::isNotBlank),
                    fileName = audio.fileName.orEmpty(),
                    mimeType = audio.mimeType.orEmpty(),
                    durationSeconds = audio.duration,
                    sizeBytes = audio.audio.size,
                    dateSeconds = message.date,
                    albumCoverMinithumbnail = audio.albumCoverMinithumbnail?.data,
                    thumbnailFileId = audio.albumCoverThumbnail?.file?.id ?: 0,
                )
            }

            is TdApi.MessageDocument -> {
                val document = content.document
                val fileName = document.fileName.orEmpty()
                val mimeType = document.mimeType.orEmpty()
                if (!isAudioDocument(mimeType, fileName)) {
                    null
                } else {
                    TelegramTrack(
                        chatId = message.chatId,
                        messageId = message.id,
                        fileId = document.document.id,
                        fileUniqueId = document.document.remote?.uniqueId.orEmpty(),
                        title = "",
                        performer = null,
                        fileName = fileName,
                        mimeType = mimeType,
                        durationSeconds = 0,
                        sizeBytes = document.document.size,
                        dateSeconds = message.date,
                        albumCoverMinithumbnail = document.minithumbnail?.data,
                        thumbnailFileId = document.thumbnail?.file?.id ?: 0,
                    )
                }
            }

            else -> null
        }

    // ------------------------------------------------------------------
    // File access (streaming)
    // ------------------------------------------------------------------

    suspend fun getFile(fileId: Int): TdApi.File = send(TdApi.GetFile(fileId))

    /**
     * Re-resolves a track's file id from its message, for when a stored file id has gone stale
     * (TDLib file ids are only valid per database generation).
     */
    /**
     * Resolves the audio file behind a channel message, throwing [IOException] with TDLib's actual
     * reason when it cannot be reached.
     *
     * TDLib only answers GetMessage for chats it currently knows. Chats are learned either implicitly
     * (a public username lookup) or explicitly via LoadChats — so a **private** channel, which has no
     * username and never appears in the public directory, was simply unknown, GetChat/GetMessage
     * failed with "Chat not found", the failure was swallowed into null, and playback surfaced only
     * "Telegram file unavailable" even though the user was a member of the channel.
     *
     * So on a miss we load the user's chat list and retry once, which teaches TDLib every joined
     * chat including private ones.
     */
    suspend fun resolveTrackFile(
        chatId: Long,
        messageId: Long,
    ): TdApi.File? {
        val message =
            runCatching { send(TdApi.GetMessage(chatId, messageId)) }
                .recoverCatching { firstError ->
                    Timber.tag(TAG).i(
                        "Telegram message %d in chat %d not immediately available (%s); loading chat list",
                        messageId,
                        chatId,
                        firstError.message,
                    )
                    ensureChatsLoaded()
                    // Force the chat into TDLib's memory before retrying the message lookup.
                    runCatching { getChat(chatId) }
                    send(TdApi.GetMessage(chatId, messageId))
                }.getOrElse { error ->
                    throw IOException(
                        "Telegram could not open message $messageId in chat $chatId: " +
                            (error.message ?: error::class.java.simpleName) +
                            ". If this is a private channel, make sure this account has joined it.",
                        error,
                    )
                }

        return when (val content = message.content) {
            is TdApi.MessageAudio -> content.audio.audio
            is TdApi.MessageDocument -> content.document.document
            else -> null
        }
    }

    /**
     * Asks TDLib to load the user's main chat list, which is what makes joined **private** chats
     * addressable by id. Idempotent and cheap after the first successful pass.
     *
     * TDLib signals "nothing left to load" with error 404, which is a success condition here, not a
     * failure. Capped by [CHAT_LIST_PAGES] so an unusually large account cannot spin here forever.
     */
    private suspend fun ensureChatsLoaded() {
        if (chatsLoaded) return
        repeat(CHAT_LIST_PAGES) {
            val result =
                runCatching {
                    send(TdApi.LoadChats(TdApi.ChatListMain(), CHAT_LIST_PAGE_SIZE))
                }
            val error = result.exceptionOrNull()
            if (error == null) return@repeat
            // 404 => the full list is loaded; anything else is a real failure worth reporting.
            if (error is TelegramApiException && error.code == 404) {
                chatsLoaded = true
                return
            }
            Timber.tag(TAG).w(error, "LoadChats failed")
            return
        }
        chatsLoaded = true
    }

    suspend fun startDownload(
        fileId: Int,
        offset: Long,
    ): TdApi.File =
        send(
            TdApi.DownloadFile(fileId, STREAM_DOWNLOAD_PRIORITY, offset, 0L, false),
        )

    suspend fun cancelDownload(fileId: Int) {
        runCatching { send(TdApi.CancelDownloadFile(fileId, false)) }
    }

    /**
     * Returns the on-disk path of a file once at least its leading [minPrefixBytes] are present (or
     * it is fully downloaded), else null. Used to extract audio properties (sample rate) from the
     * header of a streamed track without waiting for the whole file.
     */
    suspend fun readyFilePath(
        fileId: Int,
        minPrefixBytes: Long = 64 * 1024,
    ): String? {
        val file = runCatching { getFile(fileId) }.getOrNull() ?: return null
        val local = file.local
        val path = local.path
        if (path.isEmpty()) return null
        val headerReady =
            local.isDownloadingCompleted ||
                (local.downloadOffset == 0L && local.downloadedPrefixSize >= minPrefixBytes)
        return if (headerReady) path else null
    }

    suspend fun readFilePart(
        fileId: Int,
        offset: Long,
        count: Long,
    ): ByteArray = send(TdApi.ReadFilePart(fileId, offset, count)).data

    /**
     * Downloads a small file (album-cover / channel-photo thumbnail) to completion and returns its
     * on-disk path, or null when unavailable. Uses TDLib's synchronous download so the returned
     * File is fully present; thumbnails are only a few KB, so this is quick. Cheap to call
     * repeatedly — TDLib serves an already-downloaded file from its cache.
     */
    suspend fun downloadFileBlocking(fileId: Int): String? {
        if (fileId <= 0) return null
        val existing = runCatching { getFile(fileId) }.getOrNull()
        existing?.local?.takeIf { it.isDownloadingCompleted && it.path.isNotEmpty() }?.let { return it.path }
        val downloaded =
            runCatching {
                send(TdApi.DownloadFile(fileId, STREAM_DOWNLOAD_PRIORITY, 0L, 0L, true))
            }.getOrNull() ?: return null
        return downloaded.local.path.takeIf { it.isNotEmpty() }
    }

    /**
     * Writes an inline album-cover minithumbnail to the cache dir and returns a file:// URI usable
     * as artwork, or null when unavailable. Minithumbnails are tiny embedded JPEGs, so this is
     * cheap enough to run while building a play queue.
     */
    fun cacheArtwork(
        uniqueKey: String,
        data: ByteArray?,
    ): String? {
        if (data == null || data.isEmpty()) return null
        val context = appContext ?: return null
        return runCatching {
            val dir = File(context.cacheDir, "telegram_artwork").apply { mkdirs() }
            val safeKey = uniqueKey.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(dir, "$safeKey.jpg")
            if (!file.exists()) {
                file.writeBytes(data)
            }
            "file://${file.absolutePath}"
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Sends a TDLib request and awaits its reply, failing with [InterruptedIOException] after
     * [timeoutMs] instead of suspending forever. TDLib does not time out its own requests: if a
     * response is never delivered (dropped connection, killed network) the continuation would never
     * resume, permanently wedging the caller — for streaming reads that means playback hangs with no
     * error and no recovery.
     */
    suspend fun <T : TdApi.Object> send(
        function: TdApi.Function<T>,
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
    ): T {
        val currentClient =
            client ?: throw IOException("Telegram client is not running")
        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    currentClient.send(function) { result ->
                        when (result) {
                            is TdApi.Error ->
                                continuation.resumeWithException(
                                    TelegramApiException(result.code, result.message),
                                )

                            else -> {
                                @Suppress("UNCHECKED_CAST")
                                continuation.resume(result as T)
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw InterruptedIOException(
                "Telegram request ${function.javaClass.simpleName} timed out after ${timeoutMs}ms",
            ).apply { initCause(e) }
        }
    }

    /**
     * Starts the client if needed and suspends until the session is authorized, returning false if
     * the build has no credentials, the user is not logged in, or the session does not become ready
     * within [timeoutMs].
     *
     * Playback entry points must use this rather than reading [isReady]: after a cold start (process
     * death, or the first play after boot) TDLib needs a moment to load its database and reconnect,
     * so an already-logged-in user would otherwise get a spurious "not logged in" failure simply
     * because the check ran before the session finished restoring.
     */
    /**
     * [awaitReady] for callers with no Context of their own (e.g. a Media3 DataSource created by a
     * long-lived factory). Relies on the application Context captured by a previous [ensureStarted];
     * returns false when the client has never been started, since without a Context TDLib cannot be
     * initialized here. Deliberately does not retain a Context of its own, so nothing is leaked.
     */
    suspend fun awaitReady(timeoutMs: Long = READY_TIMEOUT_MS): Boolean {
        val ctx = appContext ?: return false
        return awaitReady(ctx, timeoutMs)
    }

    suspend fun awaitReady(
        context: Context,
        timeoutMs: Long = READY_TIMEOUT_MS,
    ): Boolean {
        if (!ensureStarted(context)) return false
        if (isReady) return true
        return runCatching {
            withTimeout(timeoutMs) {
                authState
                    .first { state ->
                        state is TelegramAuthState.Ready ||
                            // Terminal states: waiting on user input or an unusable session. Stop
                            // waiting rather than burn the full timeout on a session that cannot
                            // become ready without the user acting.
                            state is TelegramAuthState.WaitPhoneNumber ||
                            state is TelegramAuthState.WaitCode ||
                            state is TelegramAuthState.WaitPassword ||
                            state is TelegramAuthState.LoggingOut ||
                            state is TelegramAuthState.Unsupported
                    } is TelegramAuthState.Ready
            }
        }.getOrDefault(false)
    }

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewChat -> chatCache[update.chat.id] = update.chat
            is TdApi.UpdateChatTitle -> chatCache[update.chatId]?.title = update.title
            is TdApi.UpdateChatPhoto -> chatCache[update.chatId]?.photo = update.photo
            // Drives streaming reads. tryEmit (not emit) because this runs on TDLib's update
            // thread, which must never block; a dropped tick is harmless since readers re-check
            // the current file state on every wake and also time out independently.
            is TdApi.UpdateFile -> _fileUpdates.tryEmit(update.file)
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendTdlibParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = TelegramAuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> {
                val codeInfo = state.codeInfo
                _authState.value =
                    TelegramAuthState.WaitCode(
                        phoneNumber = codeInfo?.phoneNumber.orEmpty(),
                        codeType = codeTypeOf(codeInfo?.type),
                        canResend = codeInfo?.nextType != null,
                        resendTimeoutSeconds = codeInfo?.timeout ?: 0,
                    )
            }

            is TdApi.AuthorizationStateWaitPassword ->
                _authState.value =
                    TelegramAuthState.WaitPassword(state.passwordHint?.takeIf(String::isNotBlank))

            is TdApi.AuthorizationStateReady -> _authState.value = TelegramAuthState.Ready
            is TdApi.AuthorizationStateLoggingOut -> _authState.value = TelegramAuthState.LoggingOut
            is TdApi.AuthorizationStateClosed -> {
                synchronized(lock) { client = null }
                chatCache.clear()
                // A new session starts with nothing loaded, so the next lookup must re-load chats.
                chatsLoaded = false
                _authState.value = TelegramAuthState.Idle
            }

            else -> _authState.value = TelegramAuthState.Unsupported(state.javaClass.simpleName)
        }
    }

    private fun codeTypeOf(type: TdApi.AuthenticationCodeType?): TelegramCodeType =
        when (type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage -> TelegramCodeType.TELEGRAM_APP
            is TdApi.AuthenticationCodeTypeSms -> TelegramCodeType.SMS
            is TdApi.AuthenticationCodeTypeCall -> TelegramCodeType.CALL
            else -> TelegramCodeType.OTHER
        }

    private fun sendTdlibParameters() {
        val context = appContext ?: return
        val apiId = BuildConfig.TELEGRAM_API_ID
        val apiHash = BuildConfig.TELEGRAM_API_HASH
        val baseDir = File(context.filesDir, "telegram")
        val parameters =
            TdApi.SetTdlibParameters(
                false,
                File(baseDir, "db").absolutePath,
                File(baseDir, "files").absolutePath,
                ByteArray(0),
                true,
                true,
                true,
                false,
                apiId,
                apiHash,
                Locale.getDefault().language.ifBlank { "en" },
                Build.MODEL ?: "Android",
                Build.VERSION.RELEASE ?: "0",
                BuildConfig.VERSION_NAME,
            )
        client?.send(parameters) { result ->
            if (result is TdApi.Error) {
                Timber.tag(TAG).e("SetTdlibParameters failed: %s", result.message)
                _authState.value = TelegramAuthState.Unsupported("InvalidApiCredentials")
            }
        }
    }

    /**
     * Maps a TDLib chat to a channel entry, or null for chat types that cannot hold a browsable
     * audio archive (private 1:1 chats and secret chats).
     *
     * Basic groups are included: small private groups used to share music are a common source, and
     * rejecting every non-supergroup silently dropped them from results.
     */
    private suspend fun toChannel(chat: TdApi.Chat): TelegramChannel? =
        when (val type = chat.type) {
            is TdApi.ChatTypeSupergroup -> {
                val supergroup = runCatching { chatSupergroup(type.supergroupId) }.getOrNull()
                TelegramChannel(
                    chatId = chat.id,
                    title = chat.title,
                    // Null for private channels — they have no public username, which the UI uses
                    // to distinguish them rather than treating the absence as an error.
                    username = supergroup?.username,
                    memberCount = supergroup?.memberCount ?: 0,
                    isBroadcastChannel = type.isChannel,
                    photoMinithumbnail = chat.photo?.minithumbnail?.data,
                    photoFileId = chat.photo?.small?.id ?: 0,
                )
            }

            is TdApi.ChatTypeBasicGroup ->
                TelegramChannel(
                    chatId = chat.id,
                    title = chat.title,
                    username = null,
                    memberCount = 0,
                    isBroadcastChannel = false,
                    photoMinithumbnail = chat.photo?.minithumbnail?.data,
                    photoFileId = chat.photo?.small?.id ?: 0,
                )

            else -> null
        }

    private data class SupergroupInfo(
        val username: String?,
        val memberCount: Int,
    )

    private suspend fun chatSupergroup(supergroupId: Long): SupergroupInfo {
        val supergroup = send(TdApi.GetSupergroup(supergroupId))
        val username =
            supergroup.usernames
                ?.activeUsernames
                ?.firstOrNull()
                ?.takeIf(String::isNotBlank)
        return SupergroupInfo(username = username, memberCount = supergroup.memberCount)
    }

    private fun extractUsername(query: String): String? {
        val trimmed = query.trim()
        val fromLink =
            Regex("(?:https?://)?t(?:elegram)?\\.me/([A-Za-z0-9_]{3,})", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.get(1)
        if (fromLink != null) return fromLink
        if (trimmed.startsWith("@")) {
            return trimmed.removePrefix("@").takeIf { it.matches(Regex("[A-Za-z0-9_]{3,}")) }
        }
        return null
    }
}
