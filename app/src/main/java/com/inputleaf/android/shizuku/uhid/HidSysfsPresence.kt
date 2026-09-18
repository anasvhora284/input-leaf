package com.inputleaf.android.shizuku.uhid

import java.io.File

/**
 * EventHub/evdev appearance check via sysfs. `/proc/bus/input/devices` is denied
 * to shell on ColorOS; sysfs `eventN/device/name` and `uniq` are the fallback
 * the privileged injector can still read on AOSP.
 */
internal object HidSysfsPresence {

    fun present(name: String, uniq: String, root: File = File("/sys/class/input")): Boolean {
        val nodes = root.listFiles() ?: return false
        for (node in nodes) {
            if (!node.name.startsWith("event")) continue
            val device = File(node, "device")
            if (uniq.isNotEmpty()) {
                val deviceUniq = readTrim(File(device, "uniq"))
                if (deviceUniq == uniq) return true
            }
            val deviceName = readTrim(File(device, "name"))
            if (deviceName == name) return true
        }
        return false
    }

    private fun readTrim(file: File): String =
        runCatching { file.readText().trim() }.getOrDefault("")
}
