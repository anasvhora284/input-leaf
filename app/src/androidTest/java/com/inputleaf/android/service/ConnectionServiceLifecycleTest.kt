package com.inputleaf.android.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.storage.dataStore
import com.inputleaf.android.testutil.LoopbackServer
import com.inputleaf.android.testutil.ServiceBinding
import com.inputleaf.android.testutil.TlsLoopbackServer
import com.inputleaf.android.testutil.boundLoopbackSocket
import com.inputleaf.android.testutil.enterFrame
import com.inputleaf.android.testutil.isExpectedPlainProbeTermination
import com.inputleaf.android.testutil.keepAliveFrame
import com.inputleaf.android.testutil.keyDownFrame
import com.inputleaf.android.testutil.mouseMoveAbsFrame
import com.inputleaf.android.testutil.mouseMoveRelFrame
import com.inputleaf.android.testutil.performServerHandshake
import com.inputleaf.android.testutil.readFrame
import com.inputleaf.android.testutil.writeFrame
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.net.ssl.SSLException
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Connected lifecycle tests that drive the real [ConnectionService] against a loopback Input
 * Leap server on an emulator: full handshake, input routing, abrupt-disconnect retry, failure
 * reporting, TLS certificate rejection, and keepalive timeout.
 */
@RunWith(AndroidJUnit4::class)
class ConnectionServiceLifecycleTest {

    // The service reads preferences when bound, so the reset must complete before binding.
    // Clearing through the app's own DataStore singleton also covers reused local emulators.
    @get:Rule
    val resetAppDataRule: ExternalResource = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            runBlocking { context.dataStore.edit { it.clear() } }
        }
    }

    private fun boundService(): Pair<ServiceBinding, ConnectionService> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val binding = ServiceBinding(context, ConnectionService::class.java)
        val service = (binding.awaitBinder() as ConnectionService.LocalBinder).getService()
        return binding to service
    }

    private fun awaitState(
        service: ConnectionService,
        timeoutMs: Long,
        predicate: (ConnectionState) -> Boolean,
    ): ConnectionState {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = service.state.value
        while (System.currentTimeMillis() < deadline) {
            last = service.state.value
            if (predicate(last)) return last
            Thread.sleep(50)
        }
        throw AssertionError("State did not satisfy predicate within ${timeoutMs} ms; last=$last")
    }

    private fun setTransportPolicyTlsOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            context.dataStore.edit {
                it[stringPreferencesKey("connection_transport_policy")] = "tls_only"
            }
        }
    }

    @Test
    fun connectReachesActiveThenUnexpectedDisconnectRetries() {
        LoopbackServer(
            connectionCount = 11,
            serverSocket = boundLoopbackSocket(),
        ) { socket, _ ->
            try {
                performServerHandshake(socket)
                // Let the service observe onConnected (Idle) before Enter arrives.
                Thread.sleep(500)
                val output = DataOutputStream(socket.outputStream)
                writeFrame(output, enterFrame())
                Thread.sleep(150)
                writeFrame(output, keepAliveFrame())
                writeFrame(output, mouseMoveAbsFrame(120, 80))
                writeFrame(output, mouseMoveRelFrame(4, -6))
                writeFrame(output, keyDownFrame(key = 97))
                Thread.sleep(150)
                socket.close() // abrupt end → unexpected-disconnect → retry
            } catch (failure: Exception) {
                if (!isExpectedPlainProbeTermination(failure)) throw failure
            } finally {
                runCatching { socket.close() }
            }
        }.use { server ->
            assertThat(server.port).isEqualTo(24800)
            val (binding, service) = boundService()
            binding.use {
                service.connect(serverIp = "127.0.0.1", screenName = "smoke", force = true)
                awaitState(service, 20_000) { it is ConnectionState.Active }

                service.setCursorOverlayEnabled(true)

                awaitState(service, 20_000) { it is ConnectionState.Disconnected }
                // The retry fires after ~1s and reconnects.
                awaitState(service, 15_000) { it !is ConnectionState.Disconnected }
                service.disconnect()
                awaitState(service, 10_000) { it is ConnectionState.Disconnected }
            }
        }
    }

    @Test
    fun handshakeFailureIsReportedAndRetriedThenCancelledByDisconnect() {
        LoopbackServer(
            connectionCount = 12,
            serverSocket = boundLoopbackSocket(),
        ) { socket, _ ->
            try {
                // TLS probes desynchronize here; the hello probe and the real attempt
                // both send HELO, then the server stalls and closes mid-handshake.
                readFrame(DataInputStream(socket.inputStream))
                Thread.sleep(2_000)
            } catch (failure: Exception) {
                if (!isExpectedPlainProbeTermination(failure)) throw failure
            } finally {
                runCatching { socket.close() }
            }
        }.use { _ ->
            val (binding, service) = boundService()
            binding.use {
                service.connect(serverIp = "127.0.0.1", screenName = "smoke", force = true)
                awaitState(service, 20_000) { it !is ConnectionState.Disconnected }
                awaitState(service, 25_000) { it is ConnectionState.Disconnected }
                awaitState(service, 15_000) { it !is ConnectionState.Disconnected }
                service.disconnect()
                awaitState(service, 10_000) { it is ConnectionState.Disconnected }
            }
        }
    }

    @Test
    fun transportFailureRunsCallbackAndRetryPathsWithoutSchedulingRetry() {
        setTransportPolicyTlsOnly()
        val (binding, service) = boundService()
        binding.use {
            // Nothing listens on the fixed port: the TLS attempt fails immediately.
            val callbackCalls = java.util.concurrent.atomic.AtomicInteger()
            service.onConnectionFailed = { _, _ ->
                if (callbackCalls.incrementAndGet() == 1) {
                    throw IllegalStateException("synthetic observer failure")
                }
            }

            service.connect(serverIp = "127.0.0.1", screenName = "smoke", force = true)

            // The first callback invocation throws inside the service, exercising the
            // defensive retry path, which invokes the callback a second time.
            awaitState(service, 20_000) {
                it is ConnectionState.Disconnected && callbackCalls.get() >= 2
            }
            // TLS_ONLY never schedules retries: the state must remain Disconnected.
            Thread.sleep(2_500)
            assertThat(service.state.value).isEqualTo(ConnectionState.Disconnected)
            assertThat(callbackCalls.get()).isEqualTo(2)
        }
    }

    @Test
    fun untrustedTlsCertificateIsRejectedWithoutRetry() {
        setTransportPolicyTlsOnly()
        TlsLoopbackServer(connectionCount = 1) { socket, _ ->
            try {
                // Hold the TCP connect so the test can observe the attempt in flight
                // before the TLS handshake completes.
                Thread.sleep(700)
                performServerHandshake(socket)
            } catch (failure: Exception) {
                if (!isExpectedPlainProbeTermination(failure) && failure !is SSLException) {
                    throw failure
                }
            } finally {
                runCatching { socket.close() }
            }
        }.use { _ ->
            val (binding, service) = boundService()
            binding.use {
                service.connect(serverIp = "127.0.0.1", screenName = "smoke", force = true)
                awaitState(service, 20_000) { it !is ConnectionState.Disconnected }
                // No confirmation callback is registered → the certificate is rejected.
                awaitState(service, 20_000) { it is ConnectionState.Disconnected }
                Thread.sleep(2_500)
                assertThat(service.state.value).isEqualTo(ConnectionState.Disconnected)
            }
        }
    }

    @Test
    fun serverSilenceTriggersKeepaliveTimeoutDisconnect() {
        LoopbackServer(
            connectionCount = 11,
            serverSocket = boundLoopbackSocket(),
        ) { socket, _ ->
            try {
                performServerHandshake(socket)
                // Stay silent: four missed keepalive polls (~20s) close the connection.
                runCatching { socket.inputStream.read(ByteArray(1)) }
            } catch (failure: Exception) {
                if (!isExpectedPlainProbeTermination(failure)) throw failure
            } finally {
                runCatching { socket.close() }
            }
        }.use { _ ->
            val (binding, service) = boundService()
            binding.use {
                service.connect(serverIp = "127.0.0.1", screenName = "smoke", force = true)
                awaitState(service, 20_000) { it is ConnectionState.Idle }
                // The server stays silent: after four missed keepalive polls (~20s) the
                // monitor must close the connection client-side, which always passes
                // through Disconnected. Require that real transition — the state is Idle
                // right now, so matching Idle would exit without any keepalive activity.
                // A close race may schedule a retry that reconnects; a later cycle then
                // disconnects again, still within the extended deadline.
                val idleAt = System.currentTimeMillis()
                val deadline = idleAt + 60_000
                var keepaliveClosed = false
                while (System.currentTimeMillis() < deadline) {
                    if (service.state.value is ConnectionState.Disconnected &&
                        System.currentTimeMillis() - idleAt > 10_000
                    ) {
                        keepaliveClosed = true
                        break
                    }
                    Thread.sleep(50)
                }
                check(keepaliveClosed) {
                    "Keepalive timeout did not end the idle connection; state=${service.state.value}"
                }
                // A retry may have been scheduled by the close race; a user disconnect
                // must still end everything quietly.
                service.disconnect()
                Thread.sleep(2_500)
                assertThat(service.state.value).isEqualTo(ConnectionState.Disconnected)
            }
        }
    }
}
