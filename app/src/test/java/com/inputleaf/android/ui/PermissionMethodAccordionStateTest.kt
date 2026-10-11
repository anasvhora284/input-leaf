package com.inputleaf.android.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissionMethodAccordionStateTest {

    @Test
    fun `defaults to Shizuku expanded`() {
        val state = PermissionMethodAccordionState()
        assertThat(state.expanded).isEqualTo(PermissionMethod.SHIZUKU)
        assertThat(state.isExpanded(PermissionMethod.SHIZUKU)).isTrue()
        assertThat(state.isExpanded(PermissionMethod.ROOT)).isFalse()
        assertThat(state.isExpanded(PermissionMethod.ACCESSIBILITY)).isFalse()
    }

    @Test
    fun `expanding Root collapses Shizuku`() {
        val state = PermissionMethodAccordionState()
            .onHeaderClick(PermissionMethod.ROOT)

        assertThat(state.expanded).isEqualTo(PermissionMethod.ROOT)
        assertThat(state.isExpanded(PermissionMethod.SHIZUKU)).isFalse()
        assertThat(state.isExpanded(PermissionMethod.ROOT)).isTrue()
        assertThat(state.isExpanded(PermissionMethod.ACCESSIBILITY)).isFalse()
    }

    @Test
    fun `tapping the open header keeps that method expanded`() {
        val state = PermissionMethodAccordionState()
            .onHeaderClick(PermissionMethod.SHIZUKU)

        assertThat(state.expanded).isEqualTo(PermissionMethod.SHIZUKU)
        assertThat(state.isExpanded(PermissionMethod.ROOT)).isFalse()
        assertThat(state.isExpanded(PermissionMethod.ACCESSIBILITY)).isFalse()
    }

    @Test
    fun `switching methods never leaves zero expanded`() {
        val afterRoot = PermissionMethodAccordionState()
            .onHeaderClick(PermissionMethod.ROOT)
        val afterAccessibility = afterRoot.onHeaderClick(PermissionMethod.ACCESSIBILITY)
        val afterShizuku = afterAccessibility.onHeaderClick(PermissionMethod.SHIZUKU)

        assertThat(afterRoot.expanded).isEqualTo(PermissionMethod.ROOT)
        assertThat(afterAccessibility.expanded).isEqualTo(PermissionMethod.ACCESSIBILITY)
        assertThat(afterShizuku.expanded).isEqualTo(PermissionMethod.SHIZUKU)
    }

    @Test
    fun `rooted-device start still expands Shizuku first`() {
        val state = PermissionMethodAccordionState()
        assertThat(state.expanded).isEqualTo(PermissionMethod.SHIZUKU)
    }

    @Test
    fun `opens on the method a live session is using`() {
        val state = PermissionMethodAccordionState()
            .onActiveMethodChanged(PermissionMethod.ROOT)

        assertThat(state.expanded).isEqualTo(PermissionMethod.ROOT)
        assertThat(state.isExpanded(PermissionMethod.SHIZUKU)).isFalse()
    }

    @Test
    fun `a user tap wins over any later active-method change`() {
        val state = PermissionMethodAccordionState()
            .onHeaderClick(PermissionMethod.ACCESSIBILITY)
            .onActiveMethodChanged(PermissionMethod.ROOT)

        assertThat(state.expanded).isEqualTo(PermissionMethod.ACCESSIBILITY)
    }

    @Test
    fun `no in-use method leaves the current card alone`() {
        val state = PermissionMethodAccordionState(expanded = PermissionMethod.ROOT)
            .onActiveMethodChanged(null)

        assertThat(state.expanded).isEqualTo(PermissionMethod.ROOT)
    }

    @Test
    fun `falls back to what would be picked when nothing is connected`() {
        // Same order PrivilegeSelector uses: Shizuku, then root, then accessibility.
        assertThat(
            activePermissionMethod(null, shizukuReady = true, rootGranted = true, accessibilityAvailable = true),
        ).isEqualTo(PermissionMethod.SHIZUKU)
        assertThat(
            activePermissionMethod(null, shizukuReady = false, rootGranted = true, accessibilityAvailable = true),
        ).isEqualTo(PermissionMethod.ROOT)
        assertThat(
            activePermissionMethod(null, shizukuReady = false, rootGranted = false, accessibilityAvailable = true),
        ).isEqualTo(PermissionMethod.ACCESSIBILITY)
        assertThat(
            activePermissionMethod(null, shizukuReady = false, rootGranted = false, accessibilityAvailable = false),
        ).isNull()
    }

    @Test
    fun `a live session outranks what would otherwise be picked`() {
        // Connected over accessibility while root is granted: show accessibility.
        assertThat(
            activePermissionMethod(
                PermissionMethod.ACCESSIBILITY,
                shizukuReady = true,
                rootGranted = true,
                accessibilityAvailable = true,
            ),
        ).isEqualTo(PermissionMethod.ACCESSIBILITY)
    }

    @Test
    fun `root granted but only detected later still opens Root`() {
        // The silent re-probe resolves UNKNOWN after first paint; the card must follow.
        val atFirstPaint = PermissionMethodAccordionState()
            .onActiveMethodChanged(
                activePermissionMethod(null, shizukuReady = false, rootGranted = false, accessibilityAvailable = false),
            )
        assertThat(atFirstPaint.expanded).isEqualTo(PermissionMethod.SHIZUKU)

        val afterProbe = atFirstPaint.onActiveMethodChanged(
            activePermissionMethod(null, shizukuReady = false, rootGranted = true, accessibilityAvailable = false),
        )
        assertThat(afterProbe.expanded).isEqualTo(PermissionMethod.ROOT)
    }
}
