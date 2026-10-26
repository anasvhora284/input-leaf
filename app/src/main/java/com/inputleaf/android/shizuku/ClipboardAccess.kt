package com.inputleaf.android.shizuku

import android.content.ClipData
import android.content.ClipDescription
import android.os.IBinder
import android.os.Process
import java.lang.reflect.Method

/**
 * Reads the device clipboard from the injector process, which runs as shell or root.
 * Android 10+ blocks background apps from reading it, but shell holds
 * READ_CLIPBOARD_IN_BACKGROUND and root passes every check, so this is how the phone's
 * clipboard reaches the server in Shizuku and root mode (#56).
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
        val clip = call(service, "getPrimaryClip") as? ClipData ?: return null
        return clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
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
                String::class.java -> if (strings++ == 0) callingPackage() else null
                Int::class.javaPrimitiveType -> if (ints++ == 0) Process.myUid() / PER_USER_RANGE else 0
                else -> null
            }
        }.toTypedArray()
    }

    private fun callingPackage() = if (Process.myUid() == 0) "root" else "com.android.shell"

    private const val PER_USER_RANGE = 100_000
}
