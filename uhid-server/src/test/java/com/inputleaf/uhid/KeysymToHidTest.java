package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class KeysymToHidTest {
    @Test public void mapsLettersAndDigits() {
        assertThat(KeysymToHid.lookup('a')).isEqualTo(0x04);
        assertThat(KeysymToHid.lookup('Z')).isEqualTo(0x1D);
        assertThat(KeysymToHid.lookup('1')).isEqualTo(0x1E);
        assertThat(KeysymToHid.lookup('0')).isEqualTo(0x27);
    }

    @Test public void mapsNavigationFunctionModifierAndMediaKeys() {
        assertThat(KeysymToHid.lookup(0xFF51)).isEqualTo(0x50);
        assertThat(KeysymToHid.lookup(0xFFC9)).isEqualTo(0x45);
        assertThat(KeysymToHid.lookup(0xFFE9)).isEqualTo(0xE2);
        assertThat(KeysymToHid.lookup(0x1008FF14)).isEqualTo(0xCD);
    }

    @Test public void returnsNullForUnsupportedKeysym() {
        assertThat(KeysymToHid.lookup(0x123456)).isNull();
    }
}
