package com.inputleaf.android.storage

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.inputleaf.android.inject.PointerDevice
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.update.UpdateChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// internal so instrumented tests can reset app state through the app's own singleton
internal val Context.dataStore by preferencesDataStore("inputleaf_prefs")

class AppPreferences internal constructor(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        private val KEY_LEAF_ONBOARDING_DONE = booleanPreferencesKey("leaf_onboarding_complete")
        private val KEY_LAST_SERVER_IP   = stringPreferencesKey("last_server_ip")
        private val KEY_SAVED_SERVERS    = stringSetPreferencesKey("saved_servers")
        private val KEY_SCREEN_NAME      = stringPreferencesKey("screen_name")
        private val KEY_AUTO_CONNECT     = booleanPreferencesKey("auto_connect")
        private val KEY_SHOW_CURSOR      = booleanPreferencesKey("show_cursor")
        private val KEY_UPDATE_CHANNEL   = stringPreferencesKey("update_channel")
        private val KEY_THEME_MODE       = stringPreferencesKey("theme_mode")
        private val KEY_ONBOARDING_DONE  = booleanPreferencesKey("onboarding_complete")
        private val KEY_MOUSE_ENABLED    = booleanPreferencesKey("mouse_enabled")
        private val KEY_KEYBOARD_ENABLED = booleanPreferencesKey("keyboard_enabled")
        private val KEY_FAVORITE_SERVERS = stringPreferencesKey("favorite_servers")
        // Fingerprints stored as "ip:fingerprint" joined by newline
        private val KEY_FINGERPRINTS     = stringPreferencesKey("tls_fingerprints")
        private val KEY_TRANSPORT_MODES  = stringPreferencesKey("server_transport_modes")
        private val KEY_CONNECTION_TRANSPORT_POLICY =
            stringPreferencesKey("connection_transport_policy")
        private val KEY_LEGACY_TLS_ENABLED = booleanPreferencesKey("tls_enabled")
        private val KEY_INPUT_METHOD     = stringPreferencesKey("input_method")
        private val KEY_CURSOR_STYLE     = stringPreferencesKey("cursor_style")
        private val KEY_POINTER_DEVICE   = stringPreferencesKey("pointer_device")
        private val KEY_SERVER_NAMES     = stringPreferencesKey("server_display_names")
        private val KEY_LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")

        // Set once su has actually been granted. libsu cannot tell "granted" from
        // "never asked" until a shell exists, so this is what lets the app re-probe
        // silently at startup instead of showing a false "Setup Required".
        private val KEY_ROOT_GRANT_REMEMBERED = booleanPreferencesKey("root_grant_remembered")

        /**
         * Get a sanitized device name suitable for use as screen name.
         * Removes trailing spaces and special characters that might cause issues.
         */
        fun getDefaultScreenName(model: String? = Build.MODEL): String {
            val deviceName = (model ?: "android-phone").trim()
            // Replace spaces with hyphens and remove any characters that aren't alphanumeric or hyphen
            return deviceName
                .replace(Regex("\\s+"), "-")
                .replace(Regex("[^a-zA-Z0-9\\-]"), "")
                .lowercase()
                .ifEmpty { "android-phone" }
        }
    }

    val lastServerIp: Flow<String?> =
        dataStore.data.map { it[KEY_LAST_SERVER_IP] }

    val screenName: Flow<String> =
        dataStore.data.map { (it[KEY_SCREEN_NAME] ?: getDefaultScreenName()).trim() }

    val autoConnect: Flow<Boolean> =
        dataStore.data.map { it[KEY_AUTO_CONNECT] ?: true }
    
    val showCursor: Flow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_CURSOR] ?: true }

    /** Null until the user picks one; callers fall back to UpdateService.defaultChannel. */
    val updateChannel: Flow<UpdateChannel?> =
        dataStore.data.map { UpdateChannel.fromStorage(it[KEY_UPDATE_CHANNEL]) }

    val themeMode: Flow<String> =
        dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }

    val leafOnboardingComplete: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_LEAF_ONBOARDING_DONE]
                ?: prefs[KEY_ONBOARDING_DONE]
                ?: false
        }

    val onboardingComplete: Flow<Boolean> = leafOnboardingComplete

    val mouseEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_MOUSE_ENABLED] ?: true }

    val keyboardEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_KEYBOARD_ENABLED] ?: true }

    val inputMethod: Flow<String> =
        dataStore.data.map { it[KEY_INPUT_METHOD] ?: "auto" }

    val rootGrantRemembered: Flow<Boolean> =
        dataStore.data.map { it[KEY_ROOT_GRANT_REMEMBERED] ?: false }

    val cursorStyle: Flow<String> =
        dataStore.data.map { it[KEY_CURSOR_STYLE] ?: "default" }

    val pointerDevice: Flow<PointerDevice> =
        dataStore.data.map { PointerDevice.fromStorage(it[KEY_POINTER_DEVICE]) }

    val connectionTransportPolicy: Flow<ConnectionTransportPolicy> =
        dataStore.data.map { prefs ->
            val storedPolicy = prefs[KEY_CONNECTION_TRANSPORT_POLICY]
            if (storedPolicy == null && prefs[KEY_LEGACY_TLS_ENABLED] == true) {
                ConnectionTransportPolicy.TLS_ONLY
            } else {
                ConnectionTransportPolicy.fromStorage(storedPolicy)
            }
        }

    val favoriteServers: Flow<Set<String>> =
        dataStore.data.map { prefs ->
            prefs[KEY_FAVORITE_SERVERS]?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }

    val savedServers: Flow<Set<String>> = dataStore.data.map(::savedServerAddresses)

    val serverNames: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_NAMES]?.lineSequence()
            ?.mapNotNull { ServerRecordCodec.decode(it) }
            ?.toMap() ?: emptyMap()
    }

    private fun savedServerAddresses(prefs: Preferences): Set<String> = buildSet {
        if (prefs.contains(KEY_SAVED_SERVERS)) {
            addAll(prefs[KEY_SAVED_SERVERS].orEmpty())
        } else {
            // Recover addresses stored by older builds, even before the first new write.
            addAll(
                prefs[KEY_FAVORITE_SERVERS].orEmpty().lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
            prefs[KEY_LAST_SERVER_IP]?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            for (key in listOf(KEY_FINGERPRINTS, KEY_TRANSPORT_MODES)) {
                prefs[key].orEmpty().lineSequence().forEach { line ->
                    ServerRecordCodec.decode(line)?.first?.let { add(it) }
                }
            }
        }
    }

    private fun rememberServer(prefs: MutablePreferences, address: String) {
        val trimmed = address.trim()
        if (trimmed.isNotEmpty()) {
            prefs[KEY_SAVED_SERVERS] = savedServerAddresses(prefs) + trimmed
        }
    }

    suspend fun saveServer(address: String) = dataStore.edit { rememberServer(it, address) }

    suspend fun removeServer(address: String) = dataStore.edit { prefs ->
        val trimmed = address.trim()
        val currentSaved = savedServerAddresses(prefs).toMutableSet()
        currentSaved.remove(trimmed)
        prefs[KEY_SAVED_SERVERS] = currentSaved

        val favorites = prefs[KEY_FAVORITE_SERVERS]?.split("\n")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        if (favorites.remove(trimmed)) {
            prefs[KEY_FAVORITE_SERVERS] = favorites.joinToString("\n")
        }

        if (prefs[KEY_LAST_SERVER_IP]?.trim() == trimmed) {
            prefs.remove(KEY_LAST_SERVER_IP)
        }

        val currentNames = prefs[KEY_SERVER_NAMES]?.lineSequence()
            ?.mapNotNull { ServerRecordCodec.decode(it) }
            ?.toMap()?.toMutableMap() ?: mutableMapOf()
        if (currentNames.remove(trimmed) != null) {
            prefs[KEY_SERVER_NAMES] = currentNames.entries.joinToString("\n") { (k, v) ->
                ServerRecordCodec.encode(k, v)
            }
        }
    }

    suspend fun saveServerName(ip: String, name: String) = dataStore.edit { prefs ->
        val trimmedName = name.trim()
        val trimmedIp = ip.trim()
        if (trimmedName.isEmpty() || trimmedIp.isEmpty() || trimmedName == trimmedIp) return@edit
        rememberServer(prefs, trimmedIp)
        val current = prefs[KEY_SERVER_NAMES]?.lineSequence()
            ?.mapNotNull { ServerRecordCodec.decode(it) }
            ?.toMap()?.toMutableMap() ?: mutableMapOf()
        current[trimmedIp] = trimmedName
        prefs[KEY_SERVER_NAMES] = current.entries.joinToString("\n") { (k, v) ->
            ServerRecordCodec.encode(k, v)
        }
    }

    suspend fun saveLastServer(ip: String) = dataStore.edit {
        rememberServer(it, ip)
        it[KEY_LAST_SERVER_IP] = ip
    }

    suspend fun saveScreenName(name: String) = dataStore.edit {
        it[KEY_SCREEN_NAME] = name.trim()
    }

    suspend fun saveAutoConnect(enabled: Boolean) = dataStore.edit {
        it[KEY_AUTO_CONNECT] = enabled
    }
    
    suspend fun saveUpdateChannel(channel: UpdateChannel) = dataStore.edit {
        it[KEY_UPDATE_CHANNEL] = channel.storageValue
    }

    suspend fun saveShowCursor(enabled: Boolean) = dataStore.edit {
        it[KEY_SHOW_CURSOR] = enabled
    }

    suspend fun saveThemeMode(mode: String) = dataStore.edit {
        it[KEY_THEME_MODE] = mode
    }

    suspend fun saveLeafOnboardingComplete() = dataStore.edit {
        it[KEY_LEAF_ONBOARDING_DONE] = true
        it[KEY_ONBOARDING_DONE] = true
    }

    suspend fun saveOnboardingComplete() = saveLeafOnboardingComplete()

    suspend fun saveMouseEnabled(enabled: Boolean) = dataStore.edit {
        it[KEY_MOUSE_ENABLED] = enabled
    }

    suspend fun saveKeyboardEnabled(enabled: Boolean) = dataStore.edit {
        it[KEY_KEYBOARD_ENABLED] = enabled
    }

    suspend fun saveInputMethod(method: String) = dataStore.edit {
        it[KEY_INPUT_METHOD] = method
    }

    suspend fun saveRootGrantRemembered(remembered: Boolean) = dataStore.edit {
        it[KEY_ROOT_GRANT_REMEMBERED] = remembered
    }

    suspend fun saveCursorStyle(style: String) = dataStore.edit {
        it[KEY_CURSOR_STYLE] = style
    }

    suspend fun savePointerDevice(device: PointerDevice) = dataStore.edit {
        it[KEY_POINTER_DEVICE] = device.storageValue
    }

    suspend fun saveConnectionTransportPolicy(policy: ConnectionTransportPolicy) =
        dataStore.edit {
            it[KEY_CONNECTION_TRANSPORT_POLICY] = policy.storageValue
            it.remove(KEY_LEGACY_TLS_ENABLED)
        }

    suspend fun toggleFavoriteServer(ip: String) = dataStore.edit { prefs ->
        rememberServer(prefs, ip)
        val current = prefs[KEY_FAVORITE_SERVERS]?.split("\n")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        if (current.contains(ip)) current.remove(ip) else current.add(ip)
        prefs[KEY_FAVORITE_SERVERS] = current.joinToString("\n")
    }

    fun fingerprintFor(ip: String): Flow<String?> =
        dataStore.data.map { prefs ->
            prefs[KEY_FINGERPRINTS]?.lineSequence()
                ?.mapNotNull { ServerRecordCodec.decode(it) }
                ?.firstOrNull { it.first == ip }
                ?.second
        }

    suspend fun saveFingerprint(ip: String, fingerprint: String) =
        dataStore.edit { prefs ->
            val records = prefs[KEY_FINGERPRINTS]?.lines()
                ?.mapNotNull { ServerRecordCodec.decode(it) }
                ?.filterNot { it.first == ip }
                ?.toMutableList() ?: mutableListOf()
            records.add(ip to fingerprint)
            prefs[KEY_FINGERPRINTS] = records.joinToString("\n") { (srv, value) ->
                ServerRecordCodec.encode(srv, value)
            }
        }

    suspend fun removeFingerprint(ip: String) = dataStore.edit { prefs ->
        // Forgetting trust should not remove the saved server itself.
        rememberServer(prefs, ip)
        val raw = prefs[KEY_FINGERPRINTS] ?: return@edit
        val records = raw.lines()
            .mapNotNull { ServerRecordCodec.decode(it) }
            .filterNot { it.first == ip }
        prefs[KEY_FINGERPRINTS] = records.joinToString("\n") { (srv, value) ->
            ServerRecordCodec.encode(srv, value)
        }
    }

    fun allFingerprints(): Flow<Map<String, String>> =
        dataStore.data.map { prefs ->
            prefs[KEY_FINGERPRINTS]?.lineSequence()
                ?.mapNotNull { ServerRecordCodec.decode(it) }
                ?.associate { it.first to it.second }
                ?: emptyMap()
        }

    fun transportFor(ip: String): Flow<String?> =
        dataStore.data.map { prefs ->
            prefs[KEY_TRANSPORT_MODES]?.lineSequence()
                ?.mapNotNull { ServerRecordCodec.decode(it) }
                ?.firstOrNull { it.first == ip }
                ?.second
        }

    suspend fun saveTransport(ip: String, mode: String) = dataStore.edit { prefs ->
        val records = prefs[KEY_TRANSPORT_MODES]?.lines()
            ?.mapNotNull { ServerRecordCodec.decode(it) }
            ?.filterNot { it.first == ip }
            ?.toMutableList() ?: mutableListOf()
        records.add(ip to mode)
        prefs[KEY_TRANSPORT_MODES] = records.joinToString("\n") { (srv, value) ->
            ServerRecordCodec.encode(srv, value)
        }
    }

    suspend fun clearTransport(ip: String) = dataStore.edit { prefs ->
        val raw = prefs[KEY_TRANSPORT_MODES] ?: return@edit
        val records = raw.lines()
            .mapNotNull { ServerRecordCodec.decode(it) }
            .filterNot { it.first == ip }
        prefs[KEY_TRANSPORT_MODES] = records.joinToString("\n") { (srv, value) ->
            ServerRecordCodec.encode(srv, value)
        }
    }

    val lastSeenVersionCode: Flow<Int?> =
        dataStore.data.map { it[KEY_LAST_SEEN_VERSION_CODE] }

    suspend fun saveLastSeenVersionCode(versionCode: Int) = dataStore.edit {
        it[KEY_LAST_SEEN_VERSION_CODE] = versionCode
    }
}
