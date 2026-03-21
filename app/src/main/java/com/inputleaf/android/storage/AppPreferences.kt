package com.inputleaf.android.storage

import android.content.Context
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
        // Fingerprints stored as "ip:fingerprint" joined by newline
        private val KEY_FINGERPRINTS     = stringPreferencesKey("tls_fingerprints")
    }

    val lastServerIp: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_SERVER_IP] }

    val screenName: Flow<String> =
        context.dataStore.data.map { it[KEY_SCREEN_NAME] ?: "android-phone" }

    val autoConnect: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_CONNECT] ?: true }
    
    val showCursor: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_CURSOR] ?: true }

    val themeMode: Flow<String> =
        context.dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }

    suspend fun saveLastServer(ip: String) = context.dataStore.edit {
        it[KEY_LAST_SERVER_IP] = ip
    }

    suspend fun saveScreenName(name: String) = context.dataStore.edit {
        it[KEY_SCREEN_NAME] = name
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
