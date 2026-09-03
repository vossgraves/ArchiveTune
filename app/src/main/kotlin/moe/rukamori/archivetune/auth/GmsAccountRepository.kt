/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.GmsAccountNameKey
import moe.rukamori.archivetune.constants.InnerTubeOAuthExpiresAtKey
import moe.rukamori.archivetune.constants.InnerTubeOAuthTokenKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

/**
 * YouTube sign-in through the Google account already on the device, served by microG (GmsCore) or
 * by real Play Services — whichever provides the `com.google` account authenticator.
 *
 * This is the third way in, alongside the WebView cookie and the device code, and the least work
 * for the user: the account is already signed in at the system level, so signing in here is one
 * tap on a picker and one consent prompt. microG has allowed third-party apps to request tokens
 * this way, behind an explicit consent prompt, since 0.3.3.
 *
 * **What this is not.** ReVanced's "GmsCore support" is APK patching: a repackaged Google app is
 * rewritten to call `app.revanced.android.gms` instead of `com.google.android.gms`. ArchiveTune is
 * not a repackaged Google app, so there is nothing to redirect — the useful half of microG here is
 * the account authenticator, which is what this uses.
 *
 * The token is the same shape the device flow produces (an OAuth2 access token for the YouTube
 * scope), so it feeds [InnerTubeOAuthTokenKey] and reaches InnerTube down exactly the same
 * ANDROID_VR Bearer path — see [YouTubeOAuthRepository] for why only that client sends it.
 */
object GmsAccountRepository {
    private const val TAG = "GmsAccount"

    /** The account type microG and Play Services both register. */
    const val ACCOUNT_TYPE = "com.google"

    /**
     * Matches [YouTubeOAuthRepository.SCOPE]. The `oauth2:` prefix is what tells AccountManager to
     * return an access token for that scope rather than an authenticator-specific blob.
     */
    private const val AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/youtube"

    /**
     * AccountManager gives no expiry. Google's access tokens last an hour; re-fetching a little
     * early costs one cheap local call, since the authenticator caches and only goes to the
     * network when its own copy has expired.
     */
    private const val ASSUMED_TOKEN_LIFETIME_MS = 50 * 60 * 1000L

    /**
     * True when something on the device can serve Google accounts. False on a clean AOSP install
     * with neither microG nor Play Services, where the sign-in option should not be offered at all.
     */
    fun isAvailable(context: Context): Boolean =
        runCatching {
            AccountManager.get(context).authenticatorTypes.any { it.type == ACCOUNT_TYPE }
        }.getOrDefault(false)

    /**
     * Intent for the system account picker.
     *
     * Deliberately the picker rather than `getAccountsByType`: since Android O an app only sees
     * accounts it has been made visible to, and choosing one through this intent is what grants
     * that visibility. It also means no GET_ACCOUNTS permission — the app never enumerates
     * accounts, the user hands it exactly one.
     */
    fun accountPickerIntent(): Intent =
        AccountManager.newChooseAccountIntent(null, null, arrayOf(ACCOUNT_TYPE), null, null, null, null)

    /** Account name from the picker's result, or null when the user backed out. */
    fun accountNameFrom(resultCode: Int, data: Intent?): String? =
        data
            ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            ?.takeIf { resultCode == Activity.RESULT_OK && it.isNotBlank() }

    /** True once an account has been chosen and a token stored. */
    fun isSignedIn(context: Context): Boolean =
        context.dataStore.get(GmsAccountNameKey, "").isNotBlank()

    /**
     * Fetches a token for [accountName] and persists it, running the consent prompt inside
     * [activity] the first time.
     *
     * [activity] is what makes the prompt appear instead of the call failing: without it,
     * AccountManager can only return a token it is already authorised to give. Returns null when
     * the user declines or the authenticator refuses the scope.
     */
    suspend fun signIn(activity: Activity, accountName: String): String? =
        withContext(Dispatchers.IO) {
            val token = fetchToken(activity, activity, accountName) ?: return@withContext null
            activity.dataStore.edit { prefs -> prefs[GmsAccountNameKey] = accountName }
            persist(activity, token)
            token
        }

    /**
     * A currently valid token for the chosen account, or null when there is no microG session.
     *
     * Called off the sign-in path, so it passes no Activity: consent was granted at sign-in and
     * the authenticator can refresh silently from here. If it ever cannot, the session is dropped
     * rather than left to 401 on every request.
     */
    suspend fun validAccessToken(context: Context): String? =
        withContext(Dispatchers.IO) {
            val accountName = context.dataStore.get(GmsAccountNameKey, "")
            if (accountName.isBlank()) return@withContext null

            val stored = context.dataStore.get(InnerTubeOAuthTokenKey, "")
            val expiresAt = context.dataStore.get(InnerTubeOAuthExpiresAtKey, 0L)
            if (stored.isNotBlank() && expiresAt > System.currentTimeMillis()) return@withContext stored

            // Invalidate first: AccountManager hands back its cached copy otherwise, including the
            // stale one that just expired.
            if (stored.isNotBlank()) {
                runCatching { AccountManager.get(context).invalidateAuthToken(ACCOUNT_TYPE, stored) }
            }
            val token = fetchToken(context, null, accountName)
            if (token == null) {
                Timber.tag(TAG).w("Silent token refresh failed; clearing the microG session")
                signOut(context)
                return@withContext null
            }
            persist(context, token)
            token
        }

    /** Drops the local session. The account stays signed in at the system level. */
    suspend fun signOut(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(GmsAccountNameKey)
            prefs.remove(InnerTubeOAuthTokenKey)
            prefs.remove(InnerTubeOAuthExpiresAtKey)
        }
    }

    private fun fetchToken(context: Context, activity: Activity?, accountName: String): String? =
        runCatching {
            val manager = AccountManager.get(context)
            val account =
                manager
                    .getAccountsByType(ACCOUNT_TYPE)
                    .firstOrNull { it.name.equals(accountName, ignoreCase = true) }
                    ?: Account(accountName, ACCOUNT_TYPE)
            // Blocking on purpose — the whole object runs on Dispatchers.IO. getResult() is what
            // surfaces the authenticator's exceptions rather than swallowing them in a callback.
            val future =
                if (activity != null) {
                    manager.getAuthToken(account, AUTH_TOKEN_TYPE, Bundle(), activity, null, null)
                } else {
                    manager.getAuthToken(account, AUTH_TOKEN_TYPE, Bundle(), false, null, null)
                }
            future.result?.getString(AccountManager.KEY_AUTHTOKEN)?.takeIf { it.isNotBlank() }
        }.getOrElse {
            Timber.tag(TAG).w(it, "Could not get a token for %s", accountName)
            null
        }

    private suspend fun persist(context: Context, token: String) {
        context.dataStore.edit { prefs ->
            prefs[InnerTubeOAuthTokenKey] = token
            prefs[InnerTubeOAuthExpiresAtKey] = System.currentTimeMillis() + ASSUMED_TOKEN_LIFETIME_MS
        }
    }
}
