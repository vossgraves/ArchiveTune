package moe.rukamori.archivetune.echo.utils.cipher

import android.content.Context
import android.util.Base64
import moe.rukamori.archivetune.innertube.YouTube
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object PlayerDatesStore {
    private const val TAG = "Metrolist_CipherDates"

    private val REMOTE_URL by lazy {
        val encoded = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL01ldHJvbGlzdEdyb3VwL01ldHJvbGlzdC9tYWluL2FwcC9zcmMvbWFpbi9hc3NldHMvcGxheWVyX2RhdGVzLmpzb24="
        String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
    }

    private const val CACHE_DIR = "cipher_dates"
    private const val CACHE_FILE = "player_dates.json"

    @Volatile
    private var dates: Map<String, String> = emptyMap()

    internal fun parse(text: String): Map<String, String> =
        runCatching {
            val root = Json.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
            buildMap {
                for ((hash, value) in root) {
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { put(hash, it) }
                }
            }
        }.getOrDefault(emptyMap())

    fun initialize(context: Context) {
        val cache = File(File(context.filesDir, CACHE_DIR).apply { mkdirs() }, CACHE_FILE)

        dates = runCatching {
            if (cache.exists()) parse(cache.readText()) else emptyMap()
        }.getOrDefault(emptyMap())

        Thread {
            runCatching {
                val body = fetchRemote()
                val remote = parse(body)
                if (remote.isNotEmpty()) {
                    dates = remote
                    runCatching { cache.writeText(body) }
                }
            }.onFailure { Timber.tag(TAG).d("dates refresh skipped: ${it.message}") }
        }.apply { isDaemon = true; name = "PlayerDatesRefresh" }.start()
    }

    fun get(hash: String?): String? = hash?.let { dates[it] }

    private fun fetchRemote(): String {
        val url = URL(REMOTE_URL)
        val conn = (YouTube.proxy?.let { url.openConnection(it) } ?: url.openConnection()) as HttpURLConnection
        return try {
            conn.run {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            conn.disconnect()
        }
    }
}
