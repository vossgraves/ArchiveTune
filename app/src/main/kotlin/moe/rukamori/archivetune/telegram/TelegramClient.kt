/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Singleton wrapper around TDLib (org.drinkless.tdlib) that powers the Telegram channel streaming
 * integration: account login (phone → code → optional 2FA password), public channel search,
 * paging through a channel's audio/document messages, and partial-file access for streaming
 * playback (see TelegramDataSource).
 *
 * The user supplies their own api_id/api_hash from https://my.telegram.org (stored in DataStore);
 * the actual session lives in TDLib's own database under filesDir/telegram and survives restarts,
 * so login is a one-time flow.
 */

package moe.rukamori.archivetune.telegram

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.rukamori.archivetune.BuildConfig
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

    /** Chats TDLib has pushed via UpdateNewChat, so channel lookups avoid extra round trips. */
    private val chatCache = ConcurrentHashMap<Long, TdApi.Chat>()

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
     * Searches public chats and keeps only channels/supergroups. Accepts plain queries, @usernames
     * and t.me links.
     */
    suspend fun searchChannels(query: String): List<TelegramChannel> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val chatIds = linkedSetOf<Long>()

        // Exact username / t.me link lookup first so pasting a link always works.
        extractUsername(trimmed)?.let { username ->
            runCatching { send(TdApi.SearchPublicChat(username)) }
                .onSuccess { chatIds += it.id }
        }
        runCatching { send(TdApi.SearchPublicChats(trimmed)) }
            .onSuccess { chatIds += it.chatIds.toList() }

        return chatIds.mapNotNull { chatId ->
            runCatching { toChannel(getChat(chatId)) }.getOrNull()
        }
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
    suspend fun resolveTrackFile(
        chatId: Long,
        messageId: Long,
    ): TdApi.File? {
        runCatching { getChat(chatId) }
        val message = runCatching { send(TdApi.GetMessage(chatId, messageId)) }.getOrNull() ?: return null
        return when (val content = message.content) {
            is TdApi.MessageAudio -> content.audio.audio
            is TdApi.MessageDocument -> content.document.document
            else -> null
        }
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

    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T {
        val currentClient =
            client ?: throw IOException("Telegram client is not running")
        return suspendCancellableCoroutine { continuation ->
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

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewChat -> chatCache[update.chat.id] = update.chat
            is TdApi.UpdateChatTitle -> chatCache[update.chatId]?.title = update.title
            is TdApi.UpdateChatPhoto -> chatCache[update.chatId]?.photo = update.photo
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

    private suspend fun toChannel(chat: TdApi.Chat): TelegramChannel? {
        val type = chat.type as? TdApi.ChatTypeSupergroup ?: return null
        val supergroup = runCatching { chatSupergroup(type.supergroupId) }.getOrNull()
        return TelegramChannel(
            chatId = chat.id,
            title = chat.title,
            username = supergroup?.username,
            memberCount = supergroup?.memberCount ?: 0,
            isBroadcastChannel = type.isChannel,
            photoMinithumbnail = chat.photo?.minithumbnail?.data,
            photoFileId = chat.photo?.small?.id ?: 0,
        )
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
