/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Persisted model + JSON codec for the "Telegram bots" feature. Bots are added by the user via
 * a paste-a-link field on the bots management screen; once added they can be reopened directly
 * (no need to re-resolve the @username each time). We store them as a JSON array under a single
 * DataStore preference key so backups and migrations come for free.
 *
 * The chat id is resolved lazily from the username on first use (see TelegramBotClient.resolveBot)
 * and then cached, so a stored bot keeps working even if Telegram's @-lookup is rate-limited at
 * boot. The model intentionally has no Android imports so it can be unit-tested in pure JVM.
 */

package moe.rukamori.archivetune.telegram

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A user-saved Telegram bot entry. */
data class TelegramBot(
    /** Stable client-side id (UUID). Used as the nav arg when opening a bot's chat screen. */
    val id: String,
    /** Bot username without the leading @. Lower-cased to dedupe case variants. */
    val username: String,
    /** TDLib chat id of the resolved bot, 0 until first resolution. */
    val chatId: Long,
    /** Display title (the bot's first name from TDLib, falls back to @username). */
    val title: String,
    /** Epoch millis when the user added the bot. */
    val addedAtMs: Long,
) {
    val displayHandle: String
        get() = "@$username"
}

/** Encodes/decodes the bot list to/from the JSON shape persisted under [moe.rukamori.archivetune.constants.TelegramBotsKey]. */
object TelegramBotCodec {
    private const val KEY_ID = "id"
    private const val KEY_USERNAME = "username"
    private const val KEY_CHAT_ID = "chatId"
    private const val KEY_TITLE = "title"
    private const val KEY_ADDED_AT = "addedAtMs"

    fun encode(bots: List<TelegramBot>): String {
        val arr = JSONArray()
        for (bot in bots) {
            arr.put(
                JSONObject().apply {
                    put(KEY_ID, bot.id)
                    put(KEY_USERNAME, bot.username)
                    put(KEY_CHAT_ID, bot.chatId)
                    put(KEY_TITLE, bot.title)
                    put(KEY_ADDED_AT, bot.addedAtMs)
                },
            )
        }
        return arr.toString()
    }

    fun decode(raw: String): List<TelegramBot> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                TelegramBot(
                    id = obj.optString(KEY_ID).ifBlank { UUID.randomUUID().toString() },
                    username = obj.optString(KEY_USERNAME).trim(),
                    chatId = obj.optLong(KEY_CHAT_ID, 0L),
                    title = obj.optString(KEY_TITLE).ifBlank { "" },
                    addedAtMs = obj.optLong(KEY_ADDED_AT, 0L),
                )
            }.filter { it.username.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}

/**
 * Parses raw user input ("@BotFather", "https://t.me/BotFather", "t.me/BotFather", "BotFather")
 * down to a clean username, or null when the input doesn't look like a Telegram bot reference.
 *
 * Bots cannot be added via invite links (they don't have those); this only accepts the public
 * @-handle forms. Anything else returns null and the caller can show an error.
 */
fun parseBotUsername(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    // t.me/<username> or https://t.me/<username>
    val linkMatch = Regex(
        "(?:https?://)?t(?:elegram)?\\.me/([A-Za-z][A-Za-z0-9_]{3,})",
        RegexOption.IGNORE_CASE,
    ).find(trimmed)
    val fromLink = linkMatch?.groupValues?.get(1)
    if (fromLink != null) return fromLink.lowercase()
    // @<username>
    if (trimmed.startsWith("@")) {
        val u = trimmed.removePrefix("@")
        return u.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{3,}")) }?.lowercase()
    }
    // Bare username
    return trimmed
        .takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{3,}")) }
        ?.lowercase()
}
