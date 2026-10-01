package com.inputleaf.android.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppPreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `manual servers survive closing and reopening the preference store without auto connect`() = runBlocking {
        withPreferences { prefs, _ ->
            prefs.saveAutoConnect(false)
            prefs.saveServer(" 192.168.1.10 ")
            prefs.saveServer("macbook-b.local")
            prefs.saveServer("192.168.1.10")
            prefs.saveServer(" ")
        }
        withPreferences { prefs, _ ->
            assertThat(prefs.savedServers.first()).containsExactly("192.168.1.10", "macbook-b.local")
            assertThat(prefs.autoConnect.first()).isFalse()
            assertThat(prefs.lastServerIp.first()).isNull()
        }
    }

    @Test fun `connecting or favoriting remembers a server and removing its star does not forget it`() = runBlocking {
        withPreferences { prefs, _ ->
            prefs.saveLastServer("192.168.1.10")
            prefs.saveLastServer("192.168.1.68")
            prefs.toggleFavoriteServer("192.168.1.20")
            prefs.toggleFavoriteServer("192.168.1.20")
        }
        withPreferences { prefs, _ ->
            assertThat(prefs.savedServers.first()).containsExactly("192.168.1.10", "192.168.1.68", "192.168.1.20")
            assertThat(prefs.favoriteServers.first()).isEmpty()
            assertThat(prefs.lastServerIp.first()).isEqualTo("192.168.1.68")
        }
    }

    @Test fun `legacy favorites last server and connection records are recovered and retained after subsequent edits`() = runBlocking {
        withPreferences { prefs, store ->
            store.edit {
                it[stringPreferencesKey("last_server_ip")] = "192.168.1.10"
                it[stringPreferencesKey("favorite_servers")] = "192.168.1.68\n\n"
                it[stringPreferencesKey("tls_fingerprints")] = "old-mac.local:${"ab".repeat(32)}\nmalformed"
                it[stringPreferencesKey("server_transport_modes")] = "plain-mac.local:plain"
            }
            assertThat(prefs.savedServers.first())
                .containsExactly("192.168.1.10", "192.168.1.68", "old-mac.local", "plain-mac.local")
            prefs.saveServer("new-mac.local")
            prefs.saveLastServer("192.168.1.30")
            prefs.toggleFavoriteServer("192.168.1.68")
            prefs.removeFingerprint("old-mac.local")
            prefs.clearTransport("plain-mac.local")
        }
        withPreferences { prefs, _ ->
            assertThat(prefs.savedServers.first())
                .containsExactly("192.168.1.10", "192.168.1.68", "old-mac.local", "plain-mac.local", "new-mac.local", "192.168.1.30")
            assertThat(prefs.favoriteServers.first()).isEmpty()
            assertThat(prefs.fingerprintFor("old-mac.local").first()).isNull()
            assertThat(prefs.transportFor("plain-mac.local").first()).isNull()
        }
    }

    @Test fun `forgetting legacy certificate trust keeps the server address`() = runBlocking {
        withPreferences { prefs, store ->
            store.edit { it[stringPreferencesKey("tls_fingerprints")] = "mac.local:${"ab".repeat(32)}" }
            prefs.removeFingerprint("mac.local")
        }
        withPreferences { prefs, _ ->
            assertThat(prefs.savedServers.first()).containsExactly("mac.local")
            assertThat(prefs.fingerprintFor("mac.local").first()).isNull()
        }
    }

    private suspend fun withPreferences(block: suspend (AppPreferences, DataStore<Preferences>) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            File(folder.root, "servers.preferences_pb")
        }
        try {
            block(AppPreferences(store), store)
        } finally {
            // The next instance must reopen the actual on-disk preferences, not a cached flow.
            job.cancelAndJoin()
        }
    }

    @Test
    fun `getDefaultScreenName sanitizes model name properly`() {
        assertThat(AppPreferences.getDefaultScreenName("Pixel 7 Pro")).isEqualTo("pixel-7-pro")
        assertThat(AppPreferences.getDefaultScreenName("SM-G991B")).isEqualTo("sm-g991b")
        assertThat(AppPreferences.getDefaultScreenName("")).isEqualTo("android-phone")
        assertThat(AppPreferences.getDefaultScreenName("Special @#$ Name")).isEqualTo("special--name")
        assertThat(AppPreferences.getDefaultScreenName(null)).isEqualTo("android-phone")
    }
}
