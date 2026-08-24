package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class KeysymToHidTest {
    @Test public void mapsEveryLetterInBothCases() {
        for (int index = 0; index < 26; index++) {
            int usage = 0x04 + index;
            assertThat(KeysymToHid.lookup('a' + index)).isEqualTo(usage);
            assertThat(KeysymToHid.lookup('A' + index)).isEqualTo(usage);
        }
    }

    @Test public void mapsEveryDigit() {
        for (int digit = 1; digit <= 9; digit++) {
            assertThat(KeysymToHid.lookup('0' + digit)).isEqualTo(0x1D + digit);
        }
        assertThat(KeysymToHid.lookup('0')).isEqualTo(0x27);
    }

    @Test public void mapsEverySupportedControlAndNavigationKey() {
        assertMappings(new int[][] {
            {0xFF0D, 0x28}, {0xFF1B, 0x29}, {0xFF08, 0x2A}, {0xFF09, 0x2B},
            {0x0020, 0x2C}, {0xFF50, 0x4A}, {0xFF57, 0x4D}, {0xFF55, 0x4B},
            {0xFF56, 0x4E}, {0xFF63, 0x49}, {0xFFFF, 0x4C},
            {0xFF51, 0x50}, {0xFF52, 0x52}, {0xFF53, 0x4F}, {0xFF54, 0x51}
        });
    }

    @Test public void mapsEverySupportedFunctionKey() {
        for (int index = 0; index < 12; index++) {
            assertThat(KeysymToHid.lookup(0xFFBE + index)).isEqualTo(0x3A + index);
        }
    }

    @Test public void mapsEverySupportedModifierKey() {
        assertMappings(new int[][] {
            {0xFFE1, 0xE1}, {0xFFE2, 0xE5}, {0xFFE3, 0xE0},
            {0xFFE4, 0xE4}, {0xFFE9, 0xE2}, {0xFFEA, 0xE6}
        });
    }

    @Test public void mapsEverySupportedMediaKey() {
        assertMappings(new int[][] {
            {0x1008FF14, 0xCD}, {0x1008FF11, 0xEA},
            {0x1008FF13, 0xE9}, {0x1008FF12, 0xE2}
        });
    }

    @Test public void returnsNullForUnsupportedKeysyms() {
        assertThat(KeysymToHid.lookup(0x123456)).isNull();
        assertThat(KeysymToHid.lookup(0xFFEB)).isNull();
        assertThat(KeysymToHid.lookup('/')).isNull();
    }

    private void assertMappings(int[][] mappings) {
        for (int[] mapping : mappings) {
            assertThat(KeysymToHid.lookup(mapping[0])).isEqualTo(mapping[1]);
        }
    }
}
