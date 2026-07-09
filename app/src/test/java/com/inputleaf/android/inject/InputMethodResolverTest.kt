package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InputMethodResolverTest {

    @Test
    fun `resolve auto prefers shizuku when both are available`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "auto",
            isShizukuAvailable = true,
            isAccessibilityAvailable = true
        )
        assertThat(result).isEqualTo(ResolvedMethod.SHIZUKU)
    }

    @Test
    fun `resolve auto falls back to accessibility when shizuku unavailable`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "auto",
            isShizukuAvailable = false,
            isAccessibilityAvailable = true
        )
        assertThat(result).isEqualTo(ResolvedMethod.ACCESSIBILITY)
    }

    @Test
    fun `resolve auto returns none when neither is available`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "auto",
            isShizukuAvailable = false,
            isAccessibilityAvailable = false
        )
        assertThat(result).isEqualTo(ResolvedMethod.NONE)
    }

    @Test
    fun `resolve shizuku returns shizuku when available`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "shizuku",
            isShizukuAvailable = true,
            isAccessibilityAvailable = true
        )
        assertThat(result).isEqualTo(ResolvedMethod.SHIZUKU)
    }

    @Test
    fun `resolve shizuku returns none when unavailable`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "shizuku",
            isShizukuAvailable = false,
            isAccessibilityAvailable = true
        )
        assertThat(result).isEqualTo(ResolvedMethod.NONE)
    }

    @Test
    fun `resolve accessibility returns accessibility when available`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "accessibility",
            isShizukuAvailable = true,
            isAccessibilityAvailable = true
        )
        assertThat(result).isEqualTo(ResolvedMethod.ACCESSIBILITY)
    }

    @Test
    fun `resolve accessibility returns none when unavailable`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "accessibility",
            isShizukuAvailable = true,
            isAccessibilityAvailable = false
        )
        assertThat(result).isEqualTo(ResolvedMethod.NONE)
    }

    @Test
    fun `resolve invalid method returns none`() {
        val result = InputMethodResolver.resolve(
            preferredMethod = "unknown_method",
            isShizukuAvailable = true,
            isAccessibilityAvailable = true
        )
        assertThat(result).isEqualTo(ResolvedMethod.NONE)
    }
}
