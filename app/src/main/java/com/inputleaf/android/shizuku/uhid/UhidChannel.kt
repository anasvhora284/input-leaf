package com.inputleaf.android.shizuku.uhid

import android.util.Log
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Timeouts for UHID device readiness. ColorOS does not emit [UhidProtocol.UHID_OPEN];
 * [UhidProtocol.UHID_START] means hid-core created the device and is the wait target.
 */
internal data class UhidReadinessConfig(
    val startTimeoutMs: Long = 300L,
    val openGraceMs: Long = 80L,
    val presenceTimeoutMs: Long = 150L,
    val pollIntervalMs: Long = 10L,
    val presence: (name: String, uniq: String) -> Boolean =
        { name, uniq -> HidSysfsPresence.present(name, uniq) },
    val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    val nanoTime: () -> Long = System::nanoTime,
)

/**
 * An open `/dev/uhid` handle with a registered HID device.
 */
internal class UhidChannel private constructor(
    private val output: OutputStream,
    private val input: InputStream?,
    private val underlying: Closeable?,
    private val readinessConfig: UhidReadinessConfig,
) : HidReportSink, Closeable {

    private val closed = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private val readiness = AtomicReference<ReadinessWait?>()

    private data class ReadinessWait(
        val sawStart: AtomicBoolean = AtomicBoolean(false),
        val sawOpen: AtomicBoolean = AtomicBoolean(false),
    )

    override fun sendReport(report: ByteArray): Boolean {
        if (closed.get()) return false
        return try {
            output.write(UhidProtocol.input2Packet(report))
            output.flush()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Dropping HID report after write failure", e)
            false
        }
    }

    fun createDevice(
        name: String,
        descriptor: ByteArray,
        vendor: Int = 0,
        product: Int = 0,
        uniq: String = "",
    ) {
        val wait = if (input != null) ReadinessWait().also(readiness::set) else null
        if (input != null) startReader()
        val startedAt = readinessConfig.nanoTime()
        output.write(UhidProtocol.create2Packet(name, descriptor, vendor, product, uniq))
        output.flush()
        if (wait != null) {
            awaitReady(wait, name, uniq)
            readiness.compareAndSet(wait, null)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(readinessConfig.nanoTime() - startedAt)
            when {
                wait.sawOpen.get() ->
                    Log.i(TAG, "UHID device '$name' ready (START+OPEN) in ${elapsedMs}ms")
                wait.sawStart.get() ->
                    Log.i(TAG, "UHID device '$name' ready (START) in ${elapsedMs}ms")
                else ->
                    Log.w(TAG, "No UHID_START for '$name' after ${elapsedMs}ms; continuing")
            }
        }
    }

    /**
     * Wait for START (kernel hid created). OPEN is best-effort only: ColorOS never
     * sends it, so we must not block the full historical 1.5s OPEN timeout.
     */
    private fun awaitReady(wait: ReadinessWait, name: String, uniq: String) {
        val startDeadline = readinessConfig.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(readinessConfig.startTimeoutMs)
        while (readinessConfig.nanoTime() < startDeadline && !wait.sawStart.get()) {
            readinessConfig.sleeper(readinessConfig.pollIntervalMs)
        }
        if (!wait.sawStart.get()) return

        // OPEN and EventHub appearance are best-effort in the same bounded window.
        // ColorOS does not emit OPEN; do not wait a second timeout for it.
        val readyDeadline = readinessConfig.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(
                maxOf(readinessConfig.openGraceMs, readinessConfig.presenceTimeoutMs),
            )
        while (readinessConfig.nanoTime() < readyDeadline) {
            if (wait.sawOpen.get() || readinessConfig.presence(name, uniq)) return
            readinessConfig.sleeper(readinessConfig.pollIntervalMs)
        }
    }

    fun destroyDevice() {
        runCatching {
            output.write(UhidProtocol.destroyPacket())
            output.flush()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        readerThread?.interrupt()
        runCatching {
            output.write(UhidProtocol.destroyPacket())
            output.flush()
        }
        runCatching { underlying?.close() ?: output.close() }
    }

    private fun startReader() {
        if (readerThread != null || input == null) return
        readerThread = Thread({
            val buffer = ByteArray(UhidProtocol.EVENT_PACKET_SIZE)
            while (!closed.get() && !Thread.currentThread().isInterrupted) {
                try {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count < Int.SIZE_BYTES) continue
                    val type = ByteBuffer.wrap(buffer, 0, Int.SIZE_BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                    val wait = readiness.get() ?: continue
                    when (type) {
                        UhidProtocol.UHID_START -> {
                            if (wait.sawStart.compareAndSet(false, true)) {
                                Log.d(TAG, "UHID_START")
                            }
                        }
                        UhidProtocol.UHID_OPEN -> {
                            if (wait.sawOpen.compareAndSet(false, true)) {
                                Log.i(TAG, "UHID_OPEN")
                            }
                        }
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }, "uhid-reader").apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val TAG = "UhidChannel"
        private const val DEVICE_PATH = "/dev/uhid"

        fun openHandle(
            path: String = DEVICE_PATH,
            readinessConfig: UhidReadinessConfig = UhidReadinessConfig(),
        ): UhidChannel? {
            val file = try {
                RandomAccessFile(path, "rw")
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open $path (SELinux or permissions?)", e)
                return null
            }
            return try {
                val channel = UhidChannel(
                    FileOutputStream(file.fd),
                    FileInputStream(file.fd),
                    file,
                    readinessConfig,
                )
                Log.i(TAG, "Opened $path")
                channel
            } catch (e: Exception) {
                Log.w(TAG, "Failed to wrap $path", e)
                runCatching { file.close() }
                null
            }
        }

        fun open(
            name: String,
            descriptor: ByteArray,
            path: String = DEVICE_PATH,
            vendor: Int = 0,
            product: Int = 0,
            uniq: String = "",
            readinessConfig: UhidReadinessConfig = UhidReadinessConfig(),
        ): UhidChannel? {
            val channel = openHandle(path, readinessConfig) ?: return null
            return try {
                channel.createDevice(name, descriptor, vendor, product, uniq)
                Log.i(TAG, "Registered HID device '$name'")
                channel
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register HID device '$name'", e)
                runCatching { channel.close() }
                null
            }
        }

        fun forTesting(
            output: OutputStream,
            input: InputStream? = null,
            readinessConfig: UhidReadinessConfig = UhidReadinessConfig(
                presence = { _, _ -> false },
            ),
        ): UhidChannel = UhidChannel(output, input, null, readinessConfig)
    }
}
