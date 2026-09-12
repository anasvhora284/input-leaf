package com.inputleaf.android.shizuku.uhid

import android.util.Log
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An open `/dev/uhid` handle with a registered HID device.
 *
 * Opened read-write through a single [RandomAccessFile] so the same descriptor both
 * writes events and reads the kernel's `UHID_START`. Reading matters: the kernel queues
 * events back to the device and the queue must be drained, so a daemon reader thread runs
 * for the life of the channel.
 */
internal class UhidChannel private constructor(
    private val output: OutputStream,
    private val input: InputStream?,
    private val underlying: Closeable?,
) : HidReportSink, Closeable {

    private val closed = AtomicBoolean(false)
    private var readerThread: Thread? = null

    override fun sendReport(report: ByteArray) {
        if (closed.get()) return
        try {
            output.write(UhidProtocol.input2Packet(report))
        } catch (e: Exception) {
            Log.w(TAG, "Dropping HID report after write failure", e)
        }
    }

    fun createDevice(name: String, descriptor: ByteArray) {
        output.write(UhidProtocol.create2Packet(name, descriptor))
        output.flush()
        if (input != null) {
            val started = UhidProtocol.waitForStart(input, START_WAIT_MS)
            if (!started) Log.w(TAG, "No UHID_START within ${START_WAIT_MS}ms; continuing")
            startReader()
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
            val buffer = ByteArray(UhidProtocol.INPUT2_PACKET_SIZE)
            while (!closed.get() && !Thread.currentThread().isInterrupted) {
                try {
                    if (input.read(buffer) < 0) break
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
        private const val START_WAIT_MS = 500L
        private const val DEVICE_PATH = "/dev/uhid"

        /**
         * Registers a HID device, or returns null on any failure — typically an SELinux
         * denial on [DEVICE_PATH]. Callers treat null as "degrade to the existing
         * injection path", so no device ends up worse off than before.
         */
        fun open(name: String, descriptor: ByteArray, path: String = DEVICE_PATH): UhidChannel? {
            val file = try {
                RandomAccessFile(path, "rw")
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open $path (SELinux or permissions?)", e)
                UhidDiagnostics.log("open('$name'): cannot open $path", e)
                return null
            }
            return try {
                val channel = UhidChannel(
                    FileOutputStream(file.fd),
                    FileInputStream(file.fd),
                    file,
                )
                channel.createDevice(name, descriptor)
                Log.i(TAG, "Registered HID device '$name'")
                UhidDiagnostics.log("open('$name'): registered, ${descriptor.size}-byte descriptor")
                channel
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register HID device '$name'", e)
                UhidDiagnostics.log("open('$name'): registration failed", e)
                runCatching { file.close() }
                null
            }
        }

        /** Test seam: a channel that only writes, with no fd and no reader thread. */
        fun forTesting(output: OutputStream): UhidChannel = UhidChannel(output, null, null)
    }
}
