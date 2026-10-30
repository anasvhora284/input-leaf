package com.inputleaf.android.shizuku

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClipboardHelperOutputTest {
    @Test
    fun `helper output round-trips any text`() {
        for (text in listOf("hello", "₹2,255.58", "two\nlines", "")) {
            val printed = "WARNING: linker noise\n" + ClipboardHelperOutput.encode(text) + "\n"
            assertThat(ClipboardHelperOutput.parse(printed)).isEqualTo(text.ifEmpty { null })
        }
    }

    @Test
    fun `nothing usable in the output gives null`() {
        assertThat(ClipboardHelperOutput.parse("")).isNull()
        assertThat(ClipboardHelperOutput.parse(ClipboardHelperOutput.MARKER)).isNull()
        assertThat(ClipboardHelperOutput.parse("${ClipboardHelperOutput.MARKER}not base64!")).isNull()
    }

    @Test
    fun `finds Input Leaf's own APK in the process maps`() {
        val apk = "/data/app/~~abc==/com.inputleaf.android-xyz==/base.apk"
        val maps = sequenceOf(
            "7000-8000 r--p 00000000 fe:2b 1 /data/user_de/0/com.inputleaf.android/cache/main.jar",
            "7100-8100 r--p 00000000 fe:2b 2 /data/app/~~q==/com.other.app-z==/base.apk",
            "7200-8200 r--p 00000000 fe:2b 3 $apk",
        )
        assertThat(ClipboardHelperOutput.apkIn(maps)).isEqualTo(apk)
        assertThat(ClipboardHelperOutput.apkIn(maps.take(2))).isNull()
    }

    @Test
    fun `only plain or html text clips are read`() {
        assertThat(ClipboardAccess.isPlainText(listOf("text/plain"))).isTrue()
        assertThat(ClipboardAccess.isPlainText(listOf("text/plain", "text/html"))).isTrue()
        assertThat(ClipboardAccess.isPlainText(listOf("text/plain", "image/png"))).isFalse()
        assertThat(ClipboardAccess.isPlainText(listOf("text/uri-list"))).isFalse()
        assertThat(ClipboardAccess.isPlainText(emptyList())).isFalse()
    }
}
