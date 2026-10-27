package com.inputleaf.android.privilege

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrivilegeSelectorTest {

    @Test
    fun `prefers Shizuku when both Shizuku and root are ready`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = true,
            root = RootAvailability.GRANTED,
        )
        assertThat(PrivilegeSelector.select(snapshot)).isEqualTo(PrivilegeKind.SHIZUKU)
        assertThat(InjectorMethodResolver.resolve("auto", snapshot, accessibilityAvailable = true))
            .isEqualTo(ResolvedInjector.PRIVILEGED_SHIZUKU)
        assertThat(InjectorMethodResolver.resolve("shizuku", snapshot, accessibilityAvailable = false))
            .isEqualTo(ResolvedInjector.PRIVILEGED_SHIZUKU)
    }

    @Test
    fun `uses root when Shizuku is missing and su is granted`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.GRANTED,
        )
        assertThat(PrivilegeSelector.select(snapshot)).isEqualTo(PrivilegeKind.ROOT)
        assertThat(InjectorMethodResolver.resolve("auto", snapshot, accessibilityAvailable = true))
            .isEqualTo(ResolvedInjector.PRIVILEGED_ROOT)
    }

    @Test
    fun `uses root when Shizuku is missing and su has not been prompted yet`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.UNKNOWN,
        )
        assertThat(PrivilegeSelector.select(snapshot)).isEqualTo(PrivilegeKind.ROOT)
        assertThat(InjectorMethodResolver.resolve("auto", snapshot, accessibilityAvailable = false))
            .isEqualTo(ResolvedInjector.PRIVILEGED_ROOT)
    }

    @Test
    fun `falls back to accessibility when neither privilege path is usable`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.MISSING,
        )
        assertThat(PrivilegeSelector.select(snapshot)).isEqualTo(PrivilegeKind.NONE)
        assertThat(InjectorMethodResolver.resolve("auto", snapshot, accessibilityAvailable = true))
            .isEqualTo(ResolvedInjector.ACCESSIBILITY)
    }

    @Test
    fun `returns none when no injector can attach`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.DENIED,
        )
        assertThat(PrivilegeSelector.select(snapshot)).isEqualTo(PrivilegeKind.NONE)
        assertThat(InjectorMethodResolver.resolve("auto", snapshot, accessibilityAvailable = false))
            .isEqualTo(ResolvedInjector.NONE)
    }

    @Test
    fun `explicit Shizuku does not silently fall back to root`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.GRANTED,
        )
        assertThat(InjectorMethodResolver.resolve("shizuku", snapshot, accessibilityAvailable = true))
            .isEqualTo(ResolvedInjector.NONE)
    }

    @Test
    fun `explicit root does not require Shizuku`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.GRANTED,
        )
        assertThat(InjectorMethodResolver.resolve("root", snapshot, accessibilityAvailable = false))
            .isEqualTo(ResolvedInjector.PRIVILEGED_ROOT)
    }

    @Test
    fun `explicit root is used even when Shizuku is ready`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = true,
            root = RootAvailability.GRANTED,
        )
        assertThat(InjectorMethodResolver.resolve("root", snapshot, accessibilityAvailable = false))
            .isEqualTo(ResolvedInjector.PRIVILEGED_ROOT)
        assertThat(PrivilegedInjectorFactory.kindFor("root", snapshot))
            .isEqualTo(PrivilegeKind.ROOT)
    }

    @Test
    fun `kindFor auto prefers Shizuku then root and never invents enter leave`() {
        val both = PrivilegeSnapshot(shizukuReady = true, root = RootAvailability.GRANTED)
        val rootOnly = PrivilegeSnapshot(shizukuReady = false, root = RootAvailability.GRANTED)
        assertThat(PrivilegedInjectorFactory.kindFor("auto", both)).isEqualTo(PrivilegeKind.SHIZUKU)
        assertThat(PrivilegedInjectorFactory.kindFor("auto", rootOnly)).isEqualTo(PrivilegeKind.ROOT)
        assertThat(PrivilegeKind.entries.toList()).containsExactly(
            PrivilegeKind.SHIZUKU,
            PrivilegeKind.ROOT,
            PrivilegeKind.NONE,
        ).inOrder()
    }

    @Test
    fun `an unusable explicit preference never silently becomes root`() {
        val rooted = PrivilegeSnapshot(shizukuReady = false, root = RootAvailability.GRANTED)
        // "shizuku" with Shizuku down must not pop an su prompt the user never asked for.
        assertThat(PrivilegedInjectorFactory.kindFor("shizuku", rooted))
            .isEqualTo(PrivilegeKind.NONE)
        assertThat(PrivilegedInjectorFactory.kindFor("accessibility", rooted))
            .isEqualTo(PrivilegeKind.NONE)
    }

    @Test
    fun `explicit root or accessibility that cannot attach resolves to none`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.MISSING,
        )
        assertThat(InjectorMethodResolver.resolve("root", snapshot, accessibilityAvailable = true))
            .isEqualTo(ResolvedInjector.NONE)
        assertThat(
            InjectorMethodResolver.resolve("accessibility", snapshot, accessibilityAvailable = false),
        ).isEqualTo(ResolvedInjector.NONE)
    }

    @Test
    fun `explicit accessibility keeps overlay path even when root is present`() {
        val snapshot = PrivilegeSnapshot(
            shizukuReady = false,
            root = RootAvailability.GRANTED,
        )
        assertThat(
            InjectorMethodResolver.resolve("accessibility", snapshot, accessibilityAvailable = true),
        ).isEqualTo(ResolvedInjector.ACCESSIBILITY)
    }
}
