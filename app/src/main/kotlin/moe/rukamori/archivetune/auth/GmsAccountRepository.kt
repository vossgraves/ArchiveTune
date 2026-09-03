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
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.GmsAccountNameKey
import moe.rukamori.archivetune.constants.GmsAccountTypeKey
import moe.rukamori.archivetune.constants.InnerTubeOAuthExpiresAtKey
import moe.rukamori.archivetune.constants.InnerTubeOAuthTokenKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

/**
 * YouTube sign-in through a Google account already on the device, served by microG (GmsCore).
 *
 * This is the third way in, alongside the WebView cookie and the device code, and the least work
 * for the user: the account is already signed in at the system level, so signing in here is one tap
 * on a picker and one consent prompt.
 *
 * **It needs microG specifically, not "a Google account".** Both microG and real Play Services
 * register the `com.google` account type, so asking for that type alone lands on whichever one the
 * system picked — on a device with real Play Services installed, that is Play Services, and it
 * refuses: an `oauth2:` token for a first-party scope only goes to apps registered as OAuth clients
 * in a Google Cloud project, which ArchiveTune is not and cannot become for a first-party scope.
 * microG's authenticator serves the same request after an explicit user consent prompt, which is
 * the whole reason this route exists. So the providers are enumerated and identified by package,
 * and a non-microG one is reported as such instead of being tried and failing opaquely.
 *
 * The token is the same shape the device flow produces, so it feeds [InnerTubeOAuthTokenKey] and
 * reaches InnerTube down exactly the same ANDROID_VR Bearer path — see [YouTubeOAuthRepository] for
 * why only that client sends it.
 */
object GmsAccountRepository {
    private const val TAG = "GmsAccount"

    /** Serialises token refresh and sign-out; see [validAccessToken]. */
    private val refreshMutex = Mutex()

    /** The account type microG and Play Services both register. */
    const val ACCOUNT_TYPE = "com.google"

    /**
     * The account types a Google account can arrive under.
     *
     * `com.google` is what real Play Services registers, and what microG registers when it is
     * installed *in its place* (a deGoogled ROM, signature spoofing) — only one of the two can own
     * it. The others are how a microG fork coexists with real Play Services on the same device:
     * ReVanced GmsCore declares `app.revanced` and Vanced-era microG declares `com.mgoogle`, each
     * under its own package, precisely so the system does not have to choose.
     *
     * Asking only for `com.google` — which this used to do — therefore lands on real Play Services
     * on any device that has it, no matter what else is installed. That is not a fallback; it is
     * the one authenticator that will refuse.
     */
    private val CANDIDATE_ACCOUNT_TYPES = listOf("app.revanced", "com.mgoogle", ACCOUNT_TYPE)

    /** microG forks that install beside real Play Services rather than replacing it. */
    private val KNOWN_MICROG_PACKAGES =
        setOf(
            "app.revanced.android.gms",
            "com.mgoogle.android.gms",
            "org.microg.gms",
        )

    /**
     * Matches [YouTubeOAuthRepository]'s scope. The `oauth2:` prefix is what tells AccountManager to
     * return an access token for that scope rather than an authenticator-specific blob.
     */
    private const val AUTH_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/youtube"

    /**
     * AccountManager gives no expiry. Google's access tokens last an hour; re-fetching a little
     * early costs one cheap local call, since the authenticator caches and only goes to the network
     * when its own copy has expired.
     */
    private const val ASSUMED_TOKEN_LIFETIME_MS = 50 * 60 * 1000L

    /** An account authenticator on this device that can serve Google accounts. */
    data class Provider(
        val accountType: String,
        val packageName: String,
        val isMicroG: Boolean,
    ) {
        /** What to call it in the UI. */
        val label: String get() = if (isMicroG) "microG" else "Google Play Services"
    }

    sealed interface Result {
        data class Success(val token: String) : Result

        /** The authenticator wants the user to approve something we cannot show from here. */
        data object NeedsConsent : Result

        /**
         * The authenticator's service binding died — `AuthenticatorException: disconnected`.
         *
         * Its own category because it says nothing about the account and everything about the
         * provider: the app that owns [accountType] either crashed handling the request or would
         * not serve this caller. Reporting it as a credential failure sends the user to re-check a
         * password that was never the problem.
         */
        data class Unreachable(val accountType: String) : Result

        data class Failed(val reason: String) : Result
    }

    /**
     * Every authenticator on the device that can serve a Google account, microG first.
     *
     * Ordered rather than filtered: a device with only real Play Services still gets a row that
     * explains why this will not work, which is more use than a row that silently disappears.
     */
    fun providers(context: Context): List<Provider> =
        runCatching {
            val manager = AccountManager.get(context)
            manager.authenticatorTypes
                .filter { it.type in CANDIDATE_ACCOUNT_TYPES }
                .map { Provider(it.type, it.packageName, isMicroG(context, it.type, it.packageName)) }
                // microG first: on a device carrying both, it is the only one that can serve this.
                .sortedByDescending { it.isMicroG }
        }.getOrElse {
            Timber.tag(TAG).w(it, "Could not enumerate account authenticators")
            emptyList()
        }

    /**
     * True when this authenticator is microG (or a fork) rather than real Play Services.
     *
     * Three checks, cheapest first. An account type other than `com.google` settles it outright —
     * real Play Services only ever registers that one, so anything else in [CANDIDATE_ACCOUNT_TYPES]
     * is a coexisting fork by construction. Otherwise the package name, for the forks that keep
     * `com.google`. Only then the declared permissions, which is the check that actually works for
     * microG installed in Play Services' place: it takes the *name* `com.google.android.gms` (that
     * is what signature spoofing is for), so the name proves nothing there, but it declares its own
     * `org.microg.*` permissions and Google's build declares none.
     *
     * The permission read needs the package to be visible to us — hence the `<queries>` entries in
     * the manifest. Without them this call throws on Android 11+ and a real microG would be
     * mistaken for Play Services.
     */
    private fun isMicroG(context: Context, accountType: String, packageName: String): Boolean {
        if (accountType != ACCOUNT_TYPE) return true
        if (packageName in KNOWN_MICROG_PACKAGES) return true
        return runCatching {
            context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                .permissions
                ?.any { it.name.startsWith("org.microg.") } == true
        }.getOrDefault(false)
    }

    /** True when something on the device can serve Google accounts at all. */
    fun isAvailable(context: Context): Boolean = providers(context).isNotEmpty()

    /**
     * Intent for the system account picker, restricted to [provider]'s account type.
     *
     * Deliberately the picker rather than `getAccountsByType`: since Android O an app only sees
     * accounts it has been made visible to, and choosing one through this intent is what grants
     * that visibility. It also means no GET_ACCOUNTS permission — the app never enumerates
     * accounts, the user hands it exactly one.
     */
    fun accountPickerIntent(provider: Provider): Intent =
        AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf(provider.accountType),
            null,
            null,
            null,
            null,
        )

    /** Account name from the picker's result, or null when the user backed out. */
    fun accountNameFrom(resultCode: Int, data: Intent?): String? =
        data
            ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            ?.takeIf { resultCode == Activity.RESULT_OK && it.isNotBlank() }

    /** True once an account has been chosen and a token stored. */
    fun isSignedIn(context: Context): Boolean = context.dataStore.get(GmsAccountNameKey, "").isNotBlank()

    /**
     * Fetches a token for [accountName] and persists it, running the consent prompt inside
     * [activity] the first time.
     *
     * [activity] is what makes the prompt appear instead of the call failing: without it,
     * AccountManager can only return a token it is already authorised to give.
     */
    suspend fun signIn(activity: Activity, provider: Provider, accountName: String): Result =
        withContext(Dispatchers.IO) {
            when (val result = fetchToken(activity, activity, provider.accountType, accountName)) {
                is Result.Success -> {
                    activity.dataStore.edit { prefs ->
                        prefs[GmsAccountNameKey] = accountName
                        prefs[GmsAccountTypeKey] = provider.accountType
                    }
                    persist(activity, result.token)
                    result
                }

                else -> result
            }
        }

    /**
     * A currently valid token for the chosen account, or null when there is no microG session.
     *
     * Called off the sign-in path, so it passes no Activity: consent was granted at sign-in and the
     * authenticator can refresh silently from here. If it ever cannot, the session is dropped rather
     * than left to 401 on every request.
     */
    suspend fun validAccessToken(context: Context): String? =
        withContext(Dispatchers.IO) {
            // One refresh at a time. Every authenticated InnerTube request calls through here, so
            // an expired token had every in-flight request racing into its own getAuthToken — the
            // log showed five identical failures 11ms apart, five bindings to the authenticator and
            // five session wipes for one expiry. Holding the lock means the losers re-read the
            // token the winner just wrote instead of asking again.
            refreshMutex.withLock {
                val accountName = context.dataStore.get(GmsAccountNameKey, "")
                if (accountName.isBlank()) return@withLock null
                val accountType = context.dataStore.get(GmsAccountTypeKey, ACCOUNT_TYPE)

                val stored = context.dataStore.get(InnerTubeOAuthTokenKey, "")
                val expiresAt = context.dataStore.get(InnerTubeOAuthExpiresAtKey, 0L)
                if (stored.isNotBlank() && expiresAt > System.currentTimeMillis()) return@withLock stored

                // Invalidate first: AccountManager hands back its cached copy otherwise, including
                // the stale one that just expired.
                if (stored.isNotBlank()) {
                    runCatching { AccountManager.get(context).invalidateAuthToken(accountType, stored) }
                }
                when (val result = fetchToken(context, null, accountType, accountName)) {
                    is Result.Success -> {
                        persist(context, result.token)
                        result.token
                    }

                    // The provider is down or refusing this caller — nothing about the session is
                    // known to be wrong, so it is kept. Wiping it here would silently sign the user
                    // out every time microG was killed in the background.
                    is Result.Unreachable -> {
                        Timber.tag(TAG).w("Authenticator for %s is unreachable; keeping the session", result.accountType)
                        null
                    }

                    else -> {
                        Timber.tag(TAG).w("Silent token refresh failed (%s); clearing the session", result)
                        signOutLocked(context)
                        null
                    }
                }
            }
        }

    /** Drops the local session. The account stays signed in at the system level. */
    suspend fun signOut(context: Context) = refreshMutex.withLock { signOutLocked(context) }

    /** [signOut]'s body, for callers already holding [refreshMutex] — the mutex is not reentrant. */
    private suspend fun signOutLocked(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(GmsAccountNameKey)
            prefs.remove(GmsAccountTypeKey)
            prefs.remove(InnerTubeOAuthTokenKey)
            prefs.remove(InnerTubeOAuthExpiresAtKey)
        }
    }

    private fun fetchToken(
        context: Context,
        activity: Activity?,
        accountType: String,
        accountName: String,
    ): Result =
        runCatching {
            val manager = AccountManager.get(context)
            val known = manager.getAccountsByType(accountType)
            val account =
                known.firstOrNull { it.name.equals(accountName, ignoreCase = true) }
                    ?: Account(accountName, accountType)
            if (known.none { it.name.equals(accountName, ignoreCase = true) }) {
                // Not necessarily wrong — before Android O visibility is granted we cannot see the
                // account we were just handed — but it is also exactly what a mismatched provider
                // looks like, so it goes in the log rather than being inferred later from a
                // failure that does not mention it.
                Timber.tag(TAG).w(
                    "Account %s is not visible under type %s (%d visible); requesting anyway",
                    accountName,
                    accountType,
                    known.size,
                )
            }
            // Blocking on purpose — the whole object runs on Dispatchers.IO. getResult() is what
            // surfaces the authenticator's exceptions rather than swallowing them in a callback.
            val future =
                if (activity != null) {
                    manager.getAuthToken(account, AUTH_TOKEN_TYPE, Bundle(), activity, null, null)
                } else {
                    manager.getAuthToken(account, AUTH_TOKEN_TYPE, Bundle(), false, null, null)
                }
            val bundle = future.result ?: return@runCatching Result.Failed("no response")

            bundle.getString(AccountManager.KEY_AUTHTOKEN)?.takeIf { it.isNotBlank() }?.let {
                return@runCatching Result.Success(it)
            }
            // A bundle with no token but an intent means the authenticator wants the user to
            // approve something. Returning null here — which this used to do — is what turned a
            // recoverable "tap approve" into a bare "could not get token".
            @Suppress("DEPRECATION")
            if (bundle.get(AccountManager.KEY_INTENT) != null) {
                return@runCatching Result.NeedsConsent
            }
            Result.Failed(
                bundle.getString(AccountManager.KEY_ERROR_MESSAGE)?.takeIf { it.isNotBlank() }
                    ?: "no token returned",
            )
        }.getOrElse { error ->
            // The account TYPE is the diagnostic that matters and the first version of this line
            // did not carry it: a log saying only "could not get a token for <email>" cannot tell
            // you whether the request went to microG or to Play Services, which is the entire
            // question when this fails.
            Timber.tag(TAG).w(error, "Token request failed: account=%s type=%s", accountName, accountType)
            // The authenticator's own message is the useful part — Play Services puts its real
            // refusal here (UNREGISTERED_ON_API_CONSOLE, INVALID_AUDIENCE, BadAuthentication), and
            // that is what tells the user whether this is fixable. "disconnected" is its own case:
            // AccountManagerService sends it when the authenticator's service binding dies, which
            // means the authenticator crashed or refused to serve this caller — not that the
            // credentials were wrong.
            val reason = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName.orEmpty()
            if (reason == "disconnected") Result.Unreachable(accountType) else Result.Failed(reason)
        }

    private suspend fun persist(context: Context, token: String) {
        context.dataStore.edit { prefs ->
            prefs[InnerTubeOAuthTokenKey] = token
            prefs[InnerTubeOAuthExpiresAtKey] = System.currentTimeMillis() + ASSUMED_TOKEN_LIFETIME_MS
        }
    }
}
