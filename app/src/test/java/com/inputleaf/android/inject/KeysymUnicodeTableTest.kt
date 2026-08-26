package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeysymUnicodeTableTest {
    @Test fun `maps English Latin keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x61)).isEqualTo("a")
        assertThat(KeysymUnicodeTable.lookup(0x01000061)).isEqualTo("a")
    }

    @Test fun `maps Russian Cyrillic keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x06e1)).isEqualTo("А")
        assertThat(KeysymUnicodeTable.lookup(0x06e2)).isEqualTo("Б")
    }

    @Test fun `maps Arabic keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x05c7)).isEqualTo("ا")
    }

    @Test fun `maps Chinese via XK_Unicode`() {
        assertThat(KeysymUnicodeTable.lookup(0x01004e2d)).isEqualTo("中")
    }

    @Test fun `maps Hindi Devanagari via direct Unicode codepoint`() {
        assertThat(KeysymUnicodeTable.lookup(0x0905)).isEqualTo("अ")
    }

    @Test fun `maps Spanish Latin-1 accented keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x00e1)).isEqualTo("á")
    }

    @Test fun `maps French Latin-1 accented keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x00e9)).isEqualTo("é")
    }

    @Test fun `maps German umlaut keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x00fc)).isEqualTo("ü")
    }

    @Test fun `maps Japanese kana keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x04b1)).isEqualTo("ア")
    }

    @Test fun `maps Korean via XK_Unicode`() {
        assertThat(KeysymUnicodeTable.lookup(0x0100d55c)).isEqualTo("한")
    }

    @Test fun `maps Portuguese Latin-1 accented keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x00e3)).isEqualTo("ã")
    }

    @Test fun `maps Gujarati via direct Unicode codepoint`() {
        assertThat(KeysymUnicodeTable.lookup(0x0a85)).isEqualTo("અ")
        assertThat(KeysymUnicodeTable.lookup(0x0a95)).isEqualTo("ક")
    }

    @Test fun `maps Cyrillic via direct Unicode KeyID wire format`() {
        assertThat(KeysymUnicodeTable.lookup(0x0410)).isEqualTo("А")
    }

    @Test fun `does not treat Input Leap control KeyIDs as text`() {
        assertThat(KeysymUnicodeTable.lookup(0xEF08)).isNull()
    }

    @Test fun `maps Greek keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x07e1)).isEqualTo("α")
    }

    @Test fun `maps Thai keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x0da1)).isEqualTo("ก")
    }

    @Test fun `maps XK_Unicode keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x01000430)).isEqualTo("а")
    }

    @Test fun `returns null for unknown keysyms`() {
        assertThat(KeysymUnicodeTable.lookup(0x123456)).isNull()
    }
}
