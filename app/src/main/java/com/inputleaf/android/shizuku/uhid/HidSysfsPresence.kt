package com.inputleaf.android.shizuku.uhid

import java.io.File

/**
 * EventHub/evdev appearance check via sysfs. `/proc/bus/input/devices` is denied
 * to shell on ColorOS; sysfs `eventN/device/name` and `uniq` are the fallback
 * the privileged injector can still read on AOSP.
 */
internal object HidSysfsPresence {

    fun present(
        name: String,
        uniq: String,
        root: File = File("/sys/class/input"),
        procDevices: File = File("/proc/bus/input/devices"),
    ): Boolean {
        if (presentInSysfs(name, uniq, root)) return true
        return presentInProc(name, uniq, procDevices)
    }

    private fun presentInSysfs(name: String, uniq: String, root: File): Boolean {
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

    /**
     * Root can read `/proc/bus/input/devices`; ColorOS denies it to shell.
     * A matching Name/Uniq means EventHub can see the evdev node.
     */
    internal fun presentInProc(name: String, uniq: String, procDevices: File): Boolean {
        if (!procDevices.isFile) return false
        val text = runCatching { procDevices.readText() }.getOrDefault("")
        if (text.isEmpty()) return false
        for (block in text.split(Regex("\n\n+"))) {
            var blockName = ""
            var blockUniq = ""
            for (line in block.lineSequence()) {
                when {
                    line.startsWith("N: Name=") ->
                        blockName = unquote(line.removePrefix("N: Name="))
                    line.startsWith("U: Uniq=") ->
                        blockUniq = line.removePrefix("U: Uniq=").trim()
                }
            }
            if (uniq.isNotEmpty() && blockUniq == uniq) return true
            if (blockName == name) return true
        }
        return false
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun readTrim(file: File): String =
        runCatching { file.readText().trim() }.getOrDefault("")
}
