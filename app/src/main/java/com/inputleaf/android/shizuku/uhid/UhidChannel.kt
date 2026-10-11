package com.inputleaf.android.shizuku.uhid

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
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
 * Timeouts for UHID device readiness.
 *
 * The gate is [UhidProtocol.UHID_OPEN] — a consumer opened the evdev node — followed by
 * a bounded sysfs presence probe. [UhidProtocol.UHID_START] only says hid-core created
 * the device, which is too early to write INPUT2. ColorOS never emits OPEN, so the wait
 * is bounded and creation continues regardless.
 */
internal data class UhidReadinessConfig(
    val openTimeoutMs: Long = 300L,
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
    private val readPfd: ParcelFileDescriptor?,
) : HidReportSink, Closeable {

    private val closed = AtomicBoolean(false)
    private val readerStop = AtomicBoolean(false)
    private val writeLock = Any()
    private val input2Scratch = ByteArray(UhidProtocol.INPUT2_PACKET_SIZE)

    @Volatile
    private var readerThread: Thread? = null
    private val readiness = AtomicReference<ReadinessWait?>()

    private data class ReadinessWait(
        val sawStart: AtomicBoolean = AtomicBoolean(false),
        val sawOpen: AtomicBoolean = AtomicBoolean(false),
    )

    override fun sendReport(report: ByteArray): Boolean {
        if (closed.get()) return false
        return try {
            synchronized(writeLock) {
                if (closed.get()) return false
                writeInput2Locked(report)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Dropping HID report after write failure", e)
            false
        }
    }

    private fun writeInput2Locked(report: ByteArray) {
        UhidProtocol.writeInput2Into(input2Scratch, report)
        output.write(input2Scratch)
        output.flush()
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
        synchronized(writeLock) {
            output.write(UhidProtocol.create2Packet(name, descriptor, vendor, product, uniq))
            output.flush()
        }
        if (wait != null) {
            awaitReady(wait, name, uniq)
            readiness.compareAndSet(wait, null)
            stopReader()
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(readinessConfig.nanoTime() - startedAt)
            when {
                wait.sawOpen.get() ->
                    Log.i(TAG, "UHID device '$name' ready (OPEN) in ${elapsedMs}ms")
                wait.sawStart.get() ->
                    Log.i(
                        TAG,
                        "UHID device '$name' ready (START) in ${elapsedMs}ms",
                    )
                else ->
                    Log.w(TAG, "No UHID_START/OPEN for '$name' after ${elapsedMs}ms; continuing")
            }
        }
    }

    /**
     * Drop the readiness reader so INPUT2 and DESTROY are not serialised behind
     * a pending read on `/dev/uhid`.
     */
    private fun stopReader() {
        readerStop.set(true)
        val reader = readerThread
        readerThread = null
        reader?.interrupt()
        runCatching { readPfd?.close() }
        if (reader != null && reader != Thread.currentThread()) {
            runCatching { reader.join(READER_JOIN_MS) }
        }
    }

    /**
     * Wait for START or OPEN, then confirm the node through sysfs.
     *
     * Both windows are bounded and creation proceeds either way: ColorOS never emits
     * OPEN, so blocking on OPEN indefinitely would stall every attach on that OEM.
     * UHID_START indicates the kernel driver bound successfully, allowing early exit.
     */
    private fun awaitReady(wait: ReadinessWait, name: String, uniq: String) {
        val openDeadline = readinessConfig.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(readinessConfig.openTimeoutMs)
        while (readinessConfig.nanoTime() < openDeadline &&
            !wait.sawStart.get() &&
            !wait.sawOpen.get()) {
            readinessConfig.sleeper(readinessConfig.pollIntervalMs)
        }
        if (!wait.sawStart.get() && !wait.sawOpen.get()) return

        val presenceDeadline = readinessConfig.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(readinessConfig.presenceTimeoutMs)
        while (readinessConfig.nanoTime() < presenceDeadline) {
            if (readinessConfig.presence(name, uniq)) return
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
        stopReader()
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
            while (!closed.get() && !readerStop.get() && !Thread.currentThread().isInterrupted) {
                try {
                    if (readPfd != null) {
                        try {
                            val pollFd = StructPollfd().apply {
                                fd = readPfd.fileDescriptor
                                events = OsConstants.POLLIN.toShort()
                            }
                            val pollRet = Os.poll(arrayOf(pollFd), POLL_TIMEOUT_MS)
                            if (pollRet == 0) continue
                            if ((pollFd.revents.toInt() and OsConstants.POLLIN) == 0) break
                        } catch (_: ErrnoException) {
                            break
                        } catch (_: LinkageError) {
                            if (input.available() <= 0) {
                                try {
                                    Thread.sleep(POLL_INTERVAL_MS)
                                } catch (_: InterruptedException) {
                                    break
                                }
                                continue
                            }
                        }
                    } else {
                        if (input.available() <= 0) {
                            try {
                                Thread.sleep(POLL_INTERVAL_MS)
                            } catch (_: InterruptedException) {
                                break
                            }
                            continue
                        }
                    }

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
        private const val READER_JOIN_MS = 100L
        private const val POLL_TIMEOUT_MS = 50
        private const val POLL_INTERVAL_MS = 10L
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
            var readPfd: ParcelFileDescriptor? = null
            var readStream: FileInputStream? = null
            return try {
                readPfd = try {
                    ParcelFileDescriptor.dup(file.fd)
                } catch (_: Exception) {
                    null
                }
                // Without a dup there is no descriptor we can close to unblock read(), so
                // a reader would pin the shared fd and serialise every later write. Skip
                // readiness detection instead and let reports flush immediately.
                if (readPfd == null) {
                    Log.w(TAG, "Cannot dup $path; skipping UHID readiness reader")
                }
                readStream = readPfd?.let { FileInputStream(it.fileDescriptor) }
                val channel = UhidChannel(
                    FileOutputStream(file.fd),
                    readStream,
                    file,
                    readinessConfig,
                    readPfd,
                )
                Log.i(TAG, "Opened $path")
                channel
            } catch (e: Exception) {
                Log.w(TAG, "Failed to wrap $path", e)
                runCatching { readStream?.close() }
                runCatching { readPfd?.close() }
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
        ): UhidChannel = UhidChannel(output, input, null, readinessConfig, readPfd = null)
    }
}
