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

    @Test fun `ipv6 server addresses store and retrieve fingerprints and transport modes accurately`() = runBlocking {
        val ipv6 = "2001:db8::1"
        val fp = "ef".repeat(32)
        withPreferences { prefs, _ ->
            prefs.saveFingerprint(ipv6, fp)
            prefs.saveTransport(ipv6, "tls")
        }
        withPreferences { prefs, _ ->
            assertThat(prefs.savedServers.first()).contains(ipv6)
            assertThat(prefs.fingerprintFor(ipv6).first()).isEqualTo(fp)
            assertThat(prefs.transportFor(ipv6).first()).isEqualTo("tls")
            assertThat(prefs.allFingerprints().first()).containsEntry(ipv6, fp)

            prefs.removeFingerprint(ipv6)
            prefs.clearTransport(ipv6)
        }
        withPreferences { prefs, _ ->
            assertThat(prefs.savedServers.first()).contains(ipv6)
            assertThat(prefs.fingerprintFor(ipv6).first()).isNull()
            assertThat(prefs.transportFor(ipv6).first()).isNull()
        }
    }

    @Test fun `legacy ipv6 records are recovered and migrated to v2 format on edit`() = runBlocking {
        val ipv6 = "2001:db8::1"
        val legacyFp = "12".repeat(32)
        withPreferences { prefs, store ->
            store.edit {
                it[stringPreferencesKey("tls_fingerprints")] = "$ipv6:$legacyFp"
                it[stringPreferencesKey("server_transport_modes")] = "$ipv6:tls"
            }
            assertThat(prefs.savedServers.first()).contains(ipv6)
            assertThat(prefs.fingerprintFor(ipv6).first()).isEqualTo(legacyFp)
            assertThat(prefs.transportFor(ipv6).first()).isEqualTo("tls")

            val newFp = "34".repeat(32)
            prefs.saveFingerprint(ipv6, newFp)
            prefs.saveTransport(ipv6, "plain")
        }
        withPreferences { prefs, store ->
            val rawFp = store.data.first()[stringPreferencesKey("tls_fingerprints")]
            val rawMode = store.data.first()[stringPreferencesKey("server_transport_modes")]
            assertThat(rawFp).startsWith("v2|")
            assertThat(rawMode).startsWith("v2|")
            assertThat(prefs.fingerprintFor(ipv6).first()).isEqualTo("34".repeat(32))
            assertThat(prefs.transportFor(ipv6).first()).isEqualTo("plain")
        }
    }

    @Test fun `saveServerName persists server screen names across sessions`() = runBlocking {
        withPreferences { prefs, _ ->
            prefs.saveServerName("192.168.1.10", "Anas-MacBook")
            prefs.saveServerName("192.168.1.20", "Workstation-Linux")
        }
        withPreferences { prefs, _ ->
            val names = prefs.serverNames.first()
            assertThat(names).containsEntry("192.168.1.10", "Anas-MacBook")
            assertThat(names).containsEntry("192.168.1.20", "Workstation-Linux")
            assertThat(prefs.savedServers.first()).contains("192.168.1.10")
        }
    }

    @Test fun `removeServer deletes saved server and clears favorite and display name`() = runBlocking {
        withPreferences { prefs, _ ->
            prefs.saveServer("192.168.1.10")
            prefs.saveServer("192.168.1.20")
            prefs.toggleFavoriteServer("192.168.1.10")
            prefs.saveServerName("192.168.1.10", "Old-Mac")
        }
        withPreferences { prefs, _ ->
            prefs.removeServer("192.168.1.10")
        }
        withPreferences { prefs, _ ->
            assertThat(prefs.savedServers.first()).containsExactly("192.168.1.20")
            assertThat(prefs.favoriteServers.first()).isEmpty()
            assertThat(prefs.serverNames.first()).doesNotContainKey("192.168.1.10")
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
