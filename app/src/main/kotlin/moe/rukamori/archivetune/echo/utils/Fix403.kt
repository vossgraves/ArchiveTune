/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package moe.rukamori.archivetune.echo.utils

import moe.rukamori.archivetune.BuildConfig
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

object Fix403 {
    const val TAG = "fix403"

    @JvmField
    val ENABLED: Boolean = BuildConfig.DEBUG

    private val seq = AtomicLong(0)

    fun nextId(prefix: String): String = "$prefix-${seq.incrementAndGet()}"

    fun d(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).d(line(id, op, details))
    }

    fun i(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).i(line(id, op, details))
    }

    fun w(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).w(line(id, op, details))
    }

    fun e(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).e(line(id, op, details))
    }

    fun fail(id: String, op: String, t: Throwable, details: String = "") {
        if (!ENABLED) return
        Timber.tag(TAG).e(line(id, op, "$details chain=${chain(t)}"))
        Timber.tag(TAG).e(t, line(id, "$op.stack", ""))
    }

    private fun line(id: String, op: String, details: String): String =
        if (details.isEmpty()) "$id $op" else "$id $op $details"

    fun kv(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(" ") { (k, v) -> "$k=${render(v)}" }

    private fun render(v: Any?): String = when (v) {
        null -> "-"
        is String -> if (v.isEmpty()) "\"\"" else if (' ' in v) "\"$v\"" else v
        else -> v.toString()
    }

    fun chain(t: Throwable?): String =
        generateSequence(t) { it.cause.takeIf { c -> c !== it } }
            .take(12)
            .joinToString(" <- ") { "${it.javaClass.simpleName}:${it.message ?: "-"}" }

    fun redact(secret: String?): String {
        if (secret == null) return "absent"
        if (secret.isEmpty()) return "EMPTY(len=0)"
        return try {
            val digest = MessageDigest.getInstance("SHA-1").digest(secret.toByteArray())
            val hex = digest.take(4).joinToString("") { "%02x".format(it) }
            "len=${secret.length},sha1=$hex"
        } catch (t: Throwable) {
            "len=${secret.length},sha1=unavailable"
        }
    }

    inline fun <T> trap(id: String, op: String, block: () -> T): T? =
        try {
            block()
        } catch (t: Throwable) {
            fail(id, op, t)
            null
        }

    inline fun <T> trapRethrow(id: String, op: String, block: () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            fail(id, op, t)
            throw t
        }

    inline fun <T> timed(id: String, op: String, block: () -> T): T {
        val startedAt = System.nanoTime()
        try {
            val result = block()
            d(id, "$op.done", kv("ms" to (System.nanoTime() - startedAt) / 1_000_000))
            return result
        } catch (t: Throwable) {
            fail(id, "$op.threw", t, kv("ms" to (System.nanoTime() - startedAt) / 1_000_000))
            throw t
        }
    }
}
