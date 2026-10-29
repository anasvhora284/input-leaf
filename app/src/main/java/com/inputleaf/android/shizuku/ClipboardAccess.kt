package com.inputleaf.android.shizuku

import android.content.ClipData
import android.content.ClipDescription
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.lang.reflect.Method

/**
 * Reads the device clipboard from the injector process, which runs as shell or root.
 * Android 10+ blocks background apps from reading it, but shell holds
 * READ_CLIPBOARD_IN_BACKGROUND, so this is how the phone's clipboard reaches the server
 * in Shizuku and root mode (#56).
 *
 * Root is refused the clip itself, and asking for it as shell makes Android clear the
 * clipboard (#77). Its description is safe to read either way, so root checks that and
 * leaves the reading to [ShellClipboardReader].
 *
 * IClipboard's signatures changed across releases (calling package only on 8-9, then a
 * user id, then an attribution tag, then a device id), so arguments are filled by type.
 */
internal object ClipboardAccess {
    private val clipboard: Any? by lazy {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "clipboard") as IBinder
            Class.forName("android.content.IClipboard\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        }.getOrNull()
    }

    private var lastTimestamp = Long.MIN_VALUE

    /** @return the clipboard text if it changed since the last call, else null. */
    @Synchronized
    fun readTextIfChanged(): String? {
        val service = clipboard ?: return null
        val description = call(service, "getPrimaryClipDescription") as? ClipDescription ?: return null
        if (description.timestamp == lastTimestamp) return null
        lastTimestamp = description.timestamp
        val types = (0 until description.mimeTypeCount).map(description::getMimeType)
        if (!isPlainText(types)) {
            Log.i(TAG, "Not reading a non-text clip $types")
            return null
        }
        return if (Process.myUid() == 0) ShellClipboardReader.read() else readClipText()
    }

    /** The clip's text. Never call this as root: Android would clear the clipboard. */
    fun readClipText(): String? {
        val service = clipboard ?: return null
        val clip = call(service, "getPrimaryClip") as? ClipData ?: return null
        return clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }

    /**
     * Reading a clip makes Android grant its content:// links to the reader, and if that
     * fails it clears the whole clipboard (seen with Gboard's image clips). Only text goes
     * to the server anyway, so other clips are left alone.
     */
    internal fun isPlainText(mimeTypes: List<String>): Boolean =
        mimeTypes.isNotEmpty() && mimeTypes.all {
            it == ClipDescription.MIMETYPE_TEXT_PLAIN || it == ClipDescription.MIMETYPE_TEXT_HTML
        }

    private fun call(service: Any, name: String): Any? = runCatching {
        val method = service.javaClass.methods.first { it.name == name }
        method.invoke(service, *arguments(method))
    }.getOrNull()

    internal fun arguments(method: Method): Array<Any?> {
        var strings = 0
        var ints = 0
        return method.parameterTypes.map { type ->
            when (type) {
                String::class.java -> if (strings++ == 0) SHELL_PACKAGE else null
                Int::class.javaPrimitiveType -> if (ints++ == 0) Process.myUid() / PER_USER_RANGE else 0
                else -> null
            }
        }.toTypedArray()
    }

    private const val TAG = "ClipboardAccess"

    // Android checks the background-read permission by package; as "root" the description
    // was refused too. Naming shell is safe for the description, never for the clip.
    private const val SHELL_PACKAGE = "com.android.shell"

    private const val PER_USER_RANGE = 100_000
}
