/*
 * Copyright (C) 2024 Rukamori
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package moe.rukamori.archivetune.utils

import android.util.Base64
import moe.rukamori.archivetune.BuildConfig
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts sensitive credential values shared by the community Source Pool website. The site
 * encrypts fields (Tidal/Qobuz tokens, app IDs, …) end-to-end with AES-256-GCM before returning
 * them from `/api/sources`, so the raw JSON contains ciphertext rather than usable tokens. This
 * decrypts them locally using [BuildConfig.POOL_CLIENT_KEY], which must match the site's
 * `POOL_CLIENT_KEY`.
 *
 * Wire format produced by the site (colon-delimited, all base64):
 *   `enc:1:<iv>:<ciphertext+authTag>`
 * The 16-byte GCM auth tag is appended to the ciphertext, which is exactly what
 * `AES/GCM/NoPadding` expects here.
 *
 * All methods are best-effort: a blank key, malformed blob, or auth failure returns the input
 * unchanged (for [maybeDecrypt]) or null (for [decrypt]), so a misconfiguration never crashes
 * playback — it simply leaves the value as-is.
 */
object PoolCrypto {
    private const val PREFIX = "enc:1:"
    private const val GCM_TAG_BITS = 128

    private val key: ByteArray? by lazy {
        val raw = BuildConfig.POOL_CLIENT_KEY.trim()
        if (raw.isEmpty()) return@lazy null
        runCatching { Base64.decode(raw, Base64.DEFAULT) }
            .getOrNull()
            ?.takeIf { it.size == 32 }
    }

    /** True when a valid client key is configured and decryption is possible. */
    val isConfigured: Boolean
        get() = key != null

    /** Returns true when [value] looks like an encrypted pool blob. */
    fun isEncrypted(value: String?): Boolean = value != null && value.startsWith(PREFIX)

    /**
     * Decrypts a single `enc:1:…` blob. Returns null when the key is missing, the blob is malformed,
     * or authentication fails.
     */
    fun decrypt(blob: String): String? {
        val secret = key ?: return null
        if (!blob.startsWith(PREFIX)) return null
        return runCatching {
            val body = blob.substring(PREFIX.length)
            val parts = body.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val data = Base64.decode(parts[1], Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(secret, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            // `data` is ciphertext followed by the 16-byte tag, which is the layout GCM expects.
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * Decrypts [value] if it is an encrypted blob; otherwise returns it unchanged. Useful for
     * fields that may or may not be encrypted depending on whether the pool has E2E enabled. On a
     * decryption failure the original value is returned so callers degrade gracefully.
     */
    fun maybeDecrypt(value: String?): String? {
        if (value == null) return null
        if (!value.startsWith(PREFIX)) return value
        return decrypt(value) ?: value
    }
}
