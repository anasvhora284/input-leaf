import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.network.ConnectionTransportPolicy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferencesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var preferences: AppPreferences

    @Before fun setUp() {
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(temporaryFolder.root, "preferences.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { file }
        preferences = AppPreferences(dataStore, "  Pixel XL!  ")
    }

    @After fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test fun `defaults are deterministic and screen name falls back when blank`() = runBlocking<Unit> {
        assertThat(preferences.lastServerIp.first()).isNull()
        assertThat(preferences.screenName.first()).isEqualTo("pixel-xl")
        assertThat(preferences.autoConnect.first()).isTrue()
        assertThat(preferences.showCursor.first()).isTrue()
        assertThat(preferences.themeMode.first()).isEqualTo("SYSTEM")
        assertThat(preferences.mouseEnabled.first()).isTrue()
        assertThat(preferences.keyboardEnabled.first()).isTrue()
        assertThat(preferences.inputMethod.first()).isEqualTo("auto")
        assertThat(preferences.cursorStyle.first()).isEqualTo("default")
        assertThat(preferences.favoriteServers.first()).isEmpty()
        assertThat(preferences.allFingerprints().first()).isEmpty()
        assertThat(preferences.leafOnboardingComplete.first()).isFalse()
        assertThat(preferences.onboardingComplete.first()).isFalse()
        assertThat(preferences.connectionTransportPolicy.first())
            .isEqualTo(ConnectionTransportPolicy.AUTO)

        preferences.saveScreenName("   ")

        assertThat(preferences.screenName.first()).isEqualTo("pixel-xl")
    }

    @Test fun `preference updates are persisted`() = runBlocking<Unit> {
        preferences.saveLastServer("192.168.1.10")
        preferences.saveScreenName("  desk phone  ")
        preferences.saveAutoConnect(false)
        preferences.saveShowCursor(false)
        preferences.saveThemeMode("DARK")
        preferences.saveMouseEnabled(false)
        preferences.saveKeyboardEnabled(false)
        preferences.saveInputMethod("uhid")
        preferences.saveCursorStyle("dot")
        preferences.saveConnectionTransportPolicy(ConnectionTransportPolicy.TLS_ONLY)

        assertThat(preferences.lastServerIp.first()).isEqualTo("192.168.1.10")
        assertThat(preferences.screenName.first()).isEqualTo("desk phone")
        assertThat(preferences.autoConnect.first()).isFalse()
        assertThat(preferences.showCursor.first()).isFalse()
        assertThat(preferences.themeMode.first()).isEqualTo("DARK")
        assertThat(preferences.mouseEnabled.first()).isFalse()
        assertThat(preferences.keyboardEnabled.first()).isFalse()
        assertThat(preferences.inputMethod.first()).isEqualTo("uhid")
        assertThat(preferences.cursorStyle.first()).isEqualTo("dot")
        assertThat(preferences.connectionTransportPolicy.first())
            .isEqualTo(ConnectionTransportPolicy.TLS_ONLY)
    }

    @Test fun `favorites are trimmed deduplicated added and removed`() = runBlocking<Unit> {
        dataStore.edit {
            it[stringPreferencesKey("favorite_servers")] = " server-a \nserver-a\n\nserver-b"
        }

        assertThat(preferences.favoriteServers.first()).containsExactly("server-a", "server-b")

        preferences.toggleFavoriteServer("   ")
        assertThat(preferences.favoriteServers.first()).containsExactly("server-a", "server-b")

        preferences.toggleFavoriteServer(" server-c ")
        assertThat(preferences.favoriteServers.first())
            .containsExactly("server-a", "server-b", "server-c")

        preferences.toggleFavoriteServer("server-a")
        assertThat(preferences.favoriteServers.first()).containsExactly("server-b", "server-c")
    }

    @Test fun `fingerprints read legacy host and IPv6 records and ignore malformed data`() = runBlocking<Unit> {
        val first = "ab".repeat(32)
        val replacement = "CD".repeat(32).chunked(2).joinToString(":")
        dataStore.edit {
            it[stringPreferencesKey("tls_fingerprints")] = listOf(
                "server-a:$first",
                "malformed",
                "server-a:$replacement",
                "2001:db8::1:$first",
                "server-b:not-a-fingerprint",
                "v2|bad!|bad!",
                "",
            ).joinToString("\n")
        }

        assertThat(preferences.allFingerprints().first()).containsExactly(
            "server-a", "cd".repeat(32),
            "2001:db8::1", first,
        )
        assertThat(preferences.fingerprintFor("2001:db8::1").first()).isEqualTo(first)
    }

    @Test fun `fingerprint updates migrate records to canonical format and support removal`() = runBlocking<Unit> {
        val first = "ab".repeat(32)
        val second = "cd".repeat(32)
        val key = stringPreferencesKey("tls_fingerprints")
        dataStore.edit { it[key] = "server-a:$first\ninvalid" }

        preferences.saveFingerprint("2001:db8::2", second.uppercase())

        val stored = dataStore.data.first()[key]
        assertThat(stored).contains("v2|")
        assertThat(stored).doesNotContain("server-a")
        assertThat(preferences.allFingerprints().first()).containsExactly(
            "server-a", first,
            "2001:db8::2", second,
        )

        preferences.removeFingerprint("server-a")
        assertThat(preferences.allFingerprints().first()).containsExactly("2001:db8::2", second)
    }

    @Test fun `transport records migrate replace and remove IPv6 values`() = runBlocking<Unit> {
        val key = stringPreferencesKey("server_transport_modes")
        dataStore.edit {
            it[key] = "server-a:TLS\n2001:db8::1:plain\nbad:mode\nserver-a:plain"
        }

        assertThat(preferences.transportFor("server-a").first()).isEqualTo("plain")
        assertThat(preferences.transportFor("2001:db8::1").first()).isEqualTo("plain")

        preferences.saveTransport("2001:db8::1", "TLS")
        assertThat(preferences.transportFor("2001:db8::1").first()).isEqualTo("tls")
        assertThat(dataStore.data.first()[key]).contains("v2|")

        preferences.clearTransport("2001:db8::1")
        assertThat(preferences.transportFor("2001:db8::1").first()).isNull()
        assertThat(preferences.transportFor("server-a").first()).isEqualTo("plain")
    }

    @Test fun `transport policy honors legacy migration and rejects unsupported modes`() = runBlocking<Unit> {
        val legacyKey = booleanPreferencesKey("tls_enabled")
        val policyKey = stringPreferencesKey("connection_transport_policy")
        dataStore.edit { it[legacyKey] = true }

        assertThat(preferences.connectionTransportPolicy.first())
            .isEqualTo(ConnectionTransportPolicy.TLS_ONLY)

        dataStore.edit {
            it[legacyKey] = false
            it[policyKey] = "plain_only"
        }
        assertThat(preferences.connectionTransportPolicy.first())
            .isEqualTo(ConnectionTransportPolicy.PLAIN_ONLY)

        preferences.saveConnectionTransportPolicy(ConnectionTransportPolicy.AUTO)
        assertThat(dataStore.data.first()[legacyKey]).isNull()
        assertThat(preferences.connectionTransportPolicy.first())
            .isEqualTo(ConnectionTransportPolicy.AUTO)

        val error = runCatching { preferences.saveTransport("server", "ssh") }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `canonical records discard blank invalid and malformed fields`() = runBlocking<Unit> {
        val fingerprint = "ab".repeat(32)
        val fingerprintKey = stringPreferencesKey("tls_fingerprints")
        val transportKey = stringPreferencesKey("server_transport_modes")
        dataStore.edit {
            it[fingerprintKey] = listOf(
                "v2|bad!|bad!",
                "v2|${java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(" ".toByteArray())}|${java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(fingerprint.toByteArray())}",
                "v2|only-two-fields",
            ).joinToString("\n")
            it[transportKey] = "v2|only-two-fields\n:tls"
        }

        assertThat(preferences.allFingerprints().first()).isEmpty()
        assertThat(preferences.transportFor("server").first()).isNull()

        preferences.saveFingerprint("   ", fingerprint)
        preferences.saveTransport("   ", "tls")
        assertThat(preferences.allFingerprints().first()).isEmpty()
        assertThat(preferences.transportFor("server").first()).isNull()
    }

    @Test fun `default screen name normalizes whitespace special characters and blanks`() {
        assertThat(AppPreferences.getDefaultScreenName("  Pixel   XL!  ")).isEqualTo("pixel-xl")
        assertThat(AppPreferences.getDefaultScreenName(" !@# ")).isEqualTo("android-phone")
    }

    @Test fun `onboarding reads legacy value and writes both keys`() = runBlocking<Unit> {
        val legacyKey = booleanPreferencesKey("onboarding_complete")
        val leafKey = booleanPreferencesKey("leaf_onboarding_complete")
        dataStore.edit { it[legacyKey] = true }

        assertThat(preferences.leafOnboardingComplete.first()).isTrue()

        dataStore.edit {
            it[legacyKey] = false
            it.remove(leafKey)
        }
        preferences.saveOnboardingComplete()

        val stored = dataStore.data.first()
        assertThat(stored[legacyKey]).isTrue()
        assertThat(stored[leafKey]).isTrue()
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
