package com.inputleaf.android.storage

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.network.TlsFingerprintManager
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// internal so instrumented tests can reset app state through the app's own singleton
internal val Context.dataStore by preferencesDataStore("inputleaf_prefs")

class AppPreferences private constructor(
    private val dataStore: DataStore<Preferences>,
    private val defaultScreenName: () -> String,
) {

    constructor(context: Context) : this(context.dataStore, { getDefaultScreenName() })

    constructor(
        dataStore: DataStore<Preferences>,
        defaultDeviceName: String,
    ) : this(dataStore, { getDefaultScreenName(defaultDeviceName) })

    companion object {
        private val KEY_LEAF_ONBOARDING_DONE = booleanPreferencesKey("leaf_onboarding_complete")
        private val KEY_LAST_SERVER_IP = stringPreferencesKey("last_server_ip")
        private val KEY_SCREEN_NAME = stringPreferencesKey("screen_name")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val KEY_SHOW_CURSOR = booleanPreferencesKey("show_cursor")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")
        private val KEY_MOUSE_ENABLED = booleanPreferencesKey("mouse_enabled")
        private val KEY_KEYBOARD_ENABLED = booleanPreferencesKey("keyboard_enabled")
        private val KEY_FAVORITE_SERVERS = stringPreferencesKey("favorite_servers")
        private val KEY_FINGERPRINTS = stringPreferencesKey("tls_fingerprints")
        private val KEY_TRANSPORT_MODES = stringPreferencesKey("server_transport_modes")
        private val KEY_CONNECTION_TRANSPORT_POLICY =
            stringPreferencesKey("connection_transport_policy")
        private val KEY_LEGACY_TLS_ENABLED = booleanPreferencesKey("tls_enabled")
        private val KEY_INPUT_METHOD = stringPreferencesKey("input_method")
        private val KEY_CURSOR_STYLE = stringPreferencesKey("cursor_style")

        /** Returns a protocol-safe screen name without depending on Android in tests. */
        fun getDefaultScreenName(deviceName: String? = Build.MODEL): String =
            deviceName
                .orEmpty()
                .trim()
                .replace(Regex("\\s+"), "-")
                .replace(Regex("[^a-zA-Z0-9\\-]"), "")
                .lowercase()
                .ifEmpty { "android-phone" }
    }

    val lastServerIp: Flow<String?> = dataStore.data.map { it[KEY_LAST_SERVER_IP] }

    val screenName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SCREEN_NAME]?.trim()?.takeIf(String::isNotEmpty) ?: defaultScreenName()
    }

    val autoConnect: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_CONNECT] ?: true }

    val showCursor: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_CURSOR] ?: true }

    val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }

    val leafOnboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LEAF_ONBOARDING_DONE] ?: prefs[KEY_ONBOARDING_DONE] ?: false
    }

    val onboardingComplete: Flow<Boolean> = leafOnboardingComplete

    val mouseEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MOUSE_ENABLED] ?: true }

    val keyboardEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_KEYBOARD_ENABLED] ?: true }

    val inputMethod: Flow<String> = dataStore.data.map { it[KEY_INPUT_METHOD] ?: "auto" }

    val cursorStyle: Flow<String> = dataStore.data.map { it[KEY_CURSOR_STYLE] ?: "default" }

    val connectionTransportPolicy: Flow<ConnectionTransportPolicy> = dataStore.data.map { prefs ->
        val storedPolicy = prefs[KEY_CONNECTION_TRANSPORT_POLICY]
        if (storedPolicy == null && prefs[KEY_LEGACY_TLS_ENABLED] == true) {
            ConnectionTransportPolicy.TLS_ONLY
        } else {
            ConnectionTransportPolicy.fromStorage(storedPolicy)
        }
    }

    val favoriteServers: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_FAVORITE_SERVERS]
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            ?: emptySet()
    }

    suspend fun saveLastServer(ip: String) = dataStore.edit { it[KEY_LAST_SERVER_IP] = ip }

    suspend fun saveScreenName(name: String) = dataStore.edit { it[KEY_SCREEN_NAME] = name.trim() }

    suspend fun saveAutoConnect(enabled: Boolean) = dataStore.edit { it[KEY_AUTO_CONNECT] = enabled }

    suspend fun saveShowCursor(enabled: Boolean) = dataStore.edit { it[KEY_SHOW_CURSOR] = enabled }

    suspend fun saveThemeMode(mode: String) = dataStore.edit { it[KEY_THEME_MODE] = mode }

    suspend fun saveLeafOnboardingComplete() = dataStore.edit {
        it[KEY_LEAF_ONBOARDING_DONE] = true
        it[KEY_ONBOARDING_DONE] = true
    }

    suspend fun saveOnboardingComplete() = saveLeafOnboardingComplete()

    suspend fun saveMouseEnabled(enabled: Boolean) = dataStore.edit { it[KEY_MOUSE_ENABLED] = enabled }

    suspend fun saveKeyboardEnabled(enabled: Boolean) = dataStore.edit {
        it[KEY_KEYBOARD_ENABLED] = enabled
    }

    suspend fun saveInputMethod(method: String) = dataStore.edit { it[KEY_INPUT_METHOD] = method }

    suspend fun saveCursorStyle(style: String) = dataStore.edit { it[KEY_CURSOR_STYLE] = style }

    suspend fun saveConnectionTransportPolicy(policy: ConnectionTransportPolicy) = dataStore.edit {
        it[KEY_CONNECTION_TRANSPORT_POLICY] = policy.storageValue
        it.remove(KEY_LEGACY_TLS_ENABLED)
    }

    suspend fun toggleFavoriteServer(ip: String) = dataStore.edit { prefs ->
        val server = ip.trim()
        if (server.isEmpty()) return@edit
        val current = prefs[KEY_FAVORITE_SERVERS]
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toMutableSet()
            ?: mutableSetOf()
        if (!current.add(server)) current.remove(server)
        prefs[KEY_FAVORITE_SERVERS] = current.joinToString("\n")
    }

    fun fingerprintFor(ip: String): Flow<String?> = dataStore.data.map { prefs ->
        decodeFingerprints(prefs[KEY_FINGERPRINTS])[ip.trim()]
    }

    suspend fun saveFingerprint(ip: String, fingerprint: String) = dataStore.edit { prefs ->
        val records = decodeFingerprints(prefs[KEY_FINGERPRINTS]).toMutableMap()
        records[ip.trim()] = TlsFingerprintManager.normalizeFingerprint(fingerprint)
        prefs[KEY_FINGERPRINTS] = encodeRecords(records)
    }

    suspend fun removeFingerprint(ip: String) = dataStore.edit { prefs ->
        val records = decodeFingerprints(prefs[KEY_FINGERPRINTS]).toMutableMap()
        records.remove(ip.trim())
        prefs[KEY_FINGERPRINTS] = encodeRecords(records)
    }

    fun allFingerprints(): Flow<Map<String, String>> = dataStore.data.map { prefs ->
        decodeFingerprints(prefs[KEY_FINGERPRINTS])
    }

    fun transportFor(ip: String): Flow<String?> = dataStore.data.map { prefs ->
        decodeTransports(prefs[KEY_TRANSPORT_MODES])[ip.trim()]
    }

    suspend fun saveTransport(ip: String, mode: String) = dataStore.edit { prefs ->
        val records = decodeTransports(prefs[KEY_TRANSPORT_MODES]).toMutableMap()
        val normalizedMode = normalizeTransport(mode)
            ?: throw IllegalArgumentException("Unsupported transport mode: $mode")
        records[ip.trim()] = normalizedMode
        prefs[KEY_TRANSPORT_MODES] = encodeRecords(records)
    }

    suspend fun clearTransport(ip: String) = dataStore.edit { prefs ->
        val records = decodeTransports(prefs[KEY_TRANSPORT_MODES]).toMutableMap()
        records.remove(ip.trim())
        prefs[KEY_TRANSPORT_MODES] = encodeRecords(records)
    }

    private fun decodeFingerprints(raw: String?): Map<String, String> =
        decodeRecords(raw, ::normalizeFingerprint, ::splitLegacyFingerprint)

    private fun decodeTransports(raw: String?): Map<String, String> =
        decodeRecords(raw, ::normalizeTransport) { line ->
            val separator = line.lastIndexOf(':')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }

    private fun splitLegacyFingerprint(line: String): Pair<String, String>? {
        val candidates = line.indices
            .filter { line[it] == ':' && it > 0 }
            .mapNotNull { separator ->
                val server = line.substring(0, separator).trim()
                val fingerprint = normalizeFingerprint(line.substring(separator + 1))
                if (server.isEmpty() || fingerprint == null) null else server to fingerprint
            }
        return candidates.singleOrNull()
    }

    private fun normalizeFingerprint(value: String): String? =
        runCatching { TlsFingerprintManager.normalizeFingerprint(value) }.getOrNull()

    private fun normalizeTransport(value: String): String? =
        value.trim().lowercase().takeIf { it == "tls" || it == "plain" }

    private fun decodeRecords(
        raw: String?,
        normalizeValue: (String) -> String?,
        splitLegacy: (String) -> Pair<String, String>?,
    ): Map<String, String> {
        val records = linkedMapOf<String, String>()
        raw?.lineSequence()?.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val decoded = if (line.startsWith("v2|")) decodeCanonical(line) else splitLegacy(line)
            val server = decoded?.first?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
            val value = normalizeValue(decoded.second) ?: return@forEach
            records[server] = value
        }
        return records
    }

    private fun decodeCanonical(line: String): Pair<String, String>? {
        val fields = line.split('|')
        if (fields.size != 3 || fields[0] != "v2") return null
        return runCatching {
            decodeField(fields[1]) to decodeField(fields[2])
        }.getOrNull()
    }

    private fun encodeRecords(records: Map<String, String>): String = records.entries
        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        .joinToString("\n") { (server, value) ->
            "v2|${encodeField(server.trim())}|${encodeField(value.trim())}"
        }

    private fun encodeField(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String {
        require(value.isNotEmpty() && value.matches(Regex("[A-Za-z0-9_-]+")))
        return String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }
}
