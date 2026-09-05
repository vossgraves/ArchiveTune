/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.HISTORY_DURATION_LEGACY_FLOAT_KEY
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MAX
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MIN
import moe.rukamori.archivetune.constants.HistoryDuration
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.constants.PlayerStreamClientKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.extensions.toEnum
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { _ ->
        listOf(
            object : DataMigration<Preferences> {
                override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                    currentData[HISTORY_DURATION_LEGACY_FLOAT_KEY] != null &&
                        currentData[HistoryDuration] == null

                override suspend fun migrate(currentData: Preferences): Preferences =
                    currentData.toMutablePreferences().apply {
                        val oldFloat = currentData[HISTORY_DURATION_LEGACY_FLOAT_KEY]
                        if (oldFloat != null) {
                            this[HistoryDuration] =
                                oldFloat
                                    .toInt()
                                    .coerceIn(HISTORY_DURATION_MIN, HISTORY_DURATION_MAX)
                            this.remove(HISTORY_DURATION_LEGACY_FLOAT_KEY)
                        }
                    }

                override suspend fun cleanUp() {}
            },
            object : DataMigration<Preferences> {
                override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                    when (currentData[UpdateChannelKey]) {
                        "NIGHTLY", "DAILY_NIGHTLY" -> true
                        else -> false
                    }

                override suspend fun migrate(currentData: Preferences): Preferences =
                    currentData.toMutablePreferences().apply {
                        this[UpdateChannelKey] = UpdateChannel.CANARY.name
                    }

                override suspend fun cleanUp() {}
            },
            // ARCHIVETUNE_EXTRACTOR resolved to ANDROID_MUSIC (with login) or WEB_REMIX
            // (without). The option has been removed along with the gatekeeper machinery
            // that conditioned it; rewrite stale values to WEB_REMIX so existing users
            // don't land on an unknown enum value.
            object : DataMigration<Preferences> {
                override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                    currentData[PlayerStreamClientKey] in
                        setOf("ARCHIVETUNE_EXTRACTOR", "HI_RES_LOSSLESS")

                override suspend fun migrate(currentData: Preferences): Preferences =
                    currentData.toMutablePreferences().apply {
                        this[PlayerStreamClientKey] = PlayerStreamClient.WEB_REMIX.name
                    }

                override suspend fun cleanUp() {}
            },
        )
    },
)

object PreferenceStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _prefs = MutableStateFlow<Preferences?>(null)
    private val initialSnapshot = kotlinx.coroutines.CompletableDeferred<Preferences>()

    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch {
                try {
                    context.applicationContext.dataStore.data.collect { preferences ->
                        _prefs.value = preferences
                        initialSnapshot.complete(preferences)
                    }
                } catch (error: Throwable) {
                    initialSnapshot.completeExceptionally(error)
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    reportException(error)
                }
            }
        }
    }

    val snapshot: Preferences?
        get() = _prefs.value

    suspend fun awaitSnapshot(): Preferences = snapshot ?: initialSnapshot.await()

    fun <T> get(key: Preferences.Key<T>): T? = snapshot?.get(key)

    fun launchEdit(
        dataStore: DataStore<Preferences>,
        block: MutablePreferences.() -> Unit,
    ) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs.block()
            }
        }
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? {
    val snapshot = PreferenceStore.snapshot
    if (snapshot != null) return snapshot[key]
    // UI startup awaits readiness without blocking. Keep the bounded fallback
    // for synchronous, headless Android entrypoints rather than silently using defaults.
    return runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(1500) { data.first()[key] }
    }
}

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = get(key) ?: defaultValue

suspend fun <T> DataStore<Preferences>.getAsync(key: Preferences.Key<T>): T? = data.first()[key]

suspend fun <T> DataStore<Preferences>.getAsync(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = data.first()[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val state =
        remember {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val state =
        remember {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value.name
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
