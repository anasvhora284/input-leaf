package com.inputleaf.android.testutil

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Binds a service with `BIND_AUTO_CREATE` and blocks until `onServiceConnected` fires.
 *
 * Callers must [close] the binding (or use `use`) so the service is unbound before the
 * next test starts, mirroring the explicit-cleanup convention of the JVM test fixtures.
 */
class ServiceBinding(
    private val context: Context,
    serviceClass: Class<*>,
) : ServiceConnection, AutoCloseable {

    private val connected = CountDownLatch(1)
    private var bound = false

    /** Binder received in [onServiceConnected], or null after [onServiceDisconnected]. */
    var binder: IBinder? = null
        private set

    init {
        context.bindService(Intent(context, serviceClass), this, Context.BIND_AUTO_CREATE)
        bound = true
    }

    /** Blocks until the service connects, then returns its binder. */
    fun awaitBinder(): IBinder {
        check(connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "Service did not connect within $BIND_TIMEOUT_MS ms"
        }
        return checkNotNull(binder)
    }

    override fun close() {
        if (bound) {
            context.unbindService(this)
            bound = false
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        binder = service
        connected.countDown()
    }

    override fun onServiceDisconnected(name: ComponentName) {
        binder = null
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
