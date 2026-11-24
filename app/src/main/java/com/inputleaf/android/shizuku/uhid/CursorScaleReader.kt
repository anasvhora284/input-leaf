package com.inputleaf.android.shizuku.uhid

/**
 * Reads the XScale Android applies to a mouse's counts from `dumpsys input` (#88). There is
 * no API for it; it is printed under the device's "Cursor Input Mapper".
 */
internal object CursorScaleReader {
    /** The XScale for the device named [deviceName], or null when it isn't listed. */
    fun parse(dump: String, deviceName: String): Double? {
        val start = Regex("""^\s*Device -?\d+: ${Regex.escape(deviceName)}\s*$""", RegexOption.MULTILINE)
            .find(dump) ?: return null
        val rest = dump.substring(start.range.last + 1)
        val section = rest.substring(0, Regex("""^\s*Device -?\d+: """, RegexOption.MULTILINE).find(rest)?.range?.first ?: rest.length)
        return Regex("""XScale: ([0-9.]+)""").find(section)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** Runs `dumpsys input`; needs shell or root, as the injector process has. */
    fun read(deviceName: String, command: List<String> = listOf("dumpsys", "input")): Double? = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val dump = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        parse(dump, deviceName)
    }.getOrNull()
}
