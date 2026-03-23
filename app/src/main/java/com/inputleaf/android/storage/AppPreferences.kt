package com.inputleaf.android.storage

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("inputleaf_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_LAST_SERVER_IP   = stringPreferencesKey("last_server_ip")
        private val KEY_SCREEN_NAME      = stringPreferencesKey("screen_name")
        private val KEY_AUTO_CONNECT     = booleanPreferencesKey("auto_connect")
        private val KEY_SHOW_CURSOR      = booleanPreferencesKey("show_cursor")
        private val KEY_THEME_MODE       = stringPreferencesKey("theme_mode")
        private val KEY_ONBOARDING_DONE  = booleanPreferencesKey("onboarding_complete")
        private val KEY_MOUSE_ENABLED    = booleanPreferencesKey("mouse_enabled")
        private val KEY_KEYBOARD_ENABLED = booleanPreferencesKey("keyboard_enabled")
        private val KEY_FAVORITE_SERVERS = stringPreferencesKey("favorite_servers")
        // Fingerprints stored as "ip:fingerprint" joined by newline
        private val KEY_FINGERPRINTS     = stringPreferencesKey("tls_fingerprints")
        
        /**
         * Get a sanitized device name suitable for use as screen name.
         * Removes trailing spaces and special characters that might cause issues.
         */
        fun getDefaultScreenName(): String {
            val deviceName = Build.MODEL.trim()
            // Replace spaces with hyphens and remove any characters that aren't alphanumeric or hyphen
            return deviceName
                .replace(Regex("\\s+"), "-")
                .replace(Regex("[^a-zA-Z0-9\\-]"), "")
                .lowercase()
                .ifEmpty { "android-phone" }
        }
    }

    val lastServerIp: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_SERVER_IP] }

    val screenName: Flow<String> =
        context.dataStore.data.map { (it[KEY_SCREEN_NAME] ?: getDefaultScreenName()).trim() }

    val autoConnect: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_CONNECT] ?: true }
    
    val showCursor: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_CURSOR] ?: true }

    val themeMode: Flow<String> =
        context.dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }

    val onboardingComplete: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    val mouseEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MOUSE_ENABLED] ?: true }

    val keyboardEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_KEYBOARD_ENABLED] ?: true }

    val favoriteServers: Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_FAVORITE_SERVERS]?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        }

    suspend fun saveLastServer(ip: String) = context.dataStore.edit {
        it[KEY_LAST_SERVER_IP] = ip
    }

    suspend fun saveScreenName(name: String) = context.dataStore.edit {
        it[KEY_SCREEN_NAME] = name.trim()
    }

    suspend fun saveAutoConnect(enabled: Boolean) = context.dataStore.edit {
        it[KEY_AUTO_CONNECT] = enabled
    }
    
    suspend fun saveShowCursor(enabled: Boolean) = context.dataStore.edit {
        it[KEY_SHOW_CURSOR] = enabled
    }

    suspend fun saveThemeMode(mode: String) = context.dataStore.edit {
        it[KEY_THEME_MODE] = mode
    }

    suspend fun saveOnboardingComplete() = context.dataStore.edit {
        it[KEY_ONBOARDING_DONE] = true
    }

    suspend fun saveMouseEnabled(enabled: Boolean) = context.dataStore.edit {
        it[KEY_MOUSE_ENABLED] = enabled
    }

    suspend fun saveKeyboardEnabled(enabled: Boolean) = context.dataStore.edit {
        it[KEY_KEYBOARD_ENABLED] = enabled
    }

    suspend fun toggleFavoriteServer(ip: String) = context.dataStore.edit { prefs ->
        val current = prefs[KEY_FAVORITE_SERVERS]?.split("\n")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        if (current.contains(ip)) current.remove(ip) else current.add(ip)
        prefs[KEY_FAVORITE_SERVERS] = current.joinToString("\n")
    }

    fun fingerprintFor(ip: String): Flow<String?> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_FINGERPRINTS]?.lines()
                ?.firstOrNull { it.startsWith("$ip:") }
                ?.substringAfter(":")
        }

    suspend fun saveFingerprint(ip: String, fingerprint: String) =
        context.dataStore.edit { prefs ->
            val lines = prefs[KEY_FINGERPRINTS]?.lines()?.toMutableList() ?: mutableListOf()
            lines.removeAll { it.startsWith("$ip:") }
            lines.add("$ip:$fingerprint")
            prefs[KEY_FINGERPRINTS] = lines.joinToString("\n")
        }

    suspend fun removeFingerprint(ip: String) = context.dataStore.edit { prefs ->
        val lines = prefs[KEY_FINGERPRINTS]?.lines()?.toMutableList() ?: return@edit
        lines.removeAll { it.startsWith("$ip:") }
        prefs[KEY_FINGERPRINTS] = lines.joinToString("\n")
    }

    fun allFingerprints(): Flow<Map<String, String>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_FINGERPRINTS]?.lines()
                ?.filter { it.contains(":") }
                ?.associate { it.substringBefore(":") to it.substringAfter(":") }
                ?: emptyMap()
        }
}
