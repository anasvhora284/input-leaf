package com.inputleaf.android.service

import android.content.Context
import android.content.ServiceConnection
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.inject.AccessibilityInputInjector
import com.inputleaf.android.privilege.PrivilegeKind
import com.inputleaf.android.privilege.PrivilegeSnapshot
import com.inputleaf.android.privilege.PrivilegedUserServiceHost
import com.inputleaf.android.privilege.RootAvailability
import com.inputleaf.android.shizuku.ShizukuInputInjector
import org.junit.Test
import org.mockito.Mockito.mock

class ShizukuActiveSessionPolicyTest {

    @Test
    fun shouldClearActiveSession_keepsSessionOnSuccessAndRetrying() {
        assertThat(shouldClearActiveSession(ConnectAttemptOutcome.Success)).isFalse()
        assertThat(shouldClearActiveSession(ConnectAttemptOutcome.Retrying)).isFalse()
    }

    @Test
    fun shouldClearActiveSession_clearsSessionOnRejectedAndTerminalFailure() {
        assertThat(shouldClearActiveSession(ConnectAttemptOutcome.Rejected)).isTrue()
        assertThat(shouldClearActiveSession(ConnectAttemptOutcome.TerminalFailure)).isTrue()
    }

    @Test
    fun `shizuku restart is ignored only for the root injector itself`() {
        val root = ShizukuInputInjector(10, 10, StubHost(PrivilegeKind.ROOT))
        val shizuku = ShizukuInputInjector(10, 10, StubHost(PrivilegeKind.SHIZUKU))
        val accessibility = AccessibilityInputInjector(
            mock(Context::class.java),
            10,
            10,
            hidKeyboard = root,
        )

        assertThat(shouldIgnoreShizukuRestart(root)).isTrue()
        assertThat(shouldIgnoreShizukuRestart(shizuku)).isFalse()
        assertThat(accessibility.privilegeKind()).isEqualTo(PrivilegeKind.ROOT)
        assertThat(shouldIgnoreShizukuRestart(accessibility)).isFalse()
        assertThat(shouldIgnoreShizukuRestart(null)).isFalse()
    }

    @Test
    fun `explicit root recovery does not reconnect as Shizuku`() {
        val shizukuUpRootDenied = PrivilegeSnapshot(
            shizukuReady = true,
            root = RootAvailability.DENIED,
        )
        assertThat(privilegedRecoveryKind("root", shizukuUpRootDenied)).isNull()
        assertThat(privilegedRecoveryKind("auto", shizukuUpRootDenied)).isEqualTo(PrivilegeKind.SHIZUKU)
        assertThat(
            privilegedRecoveryKind(
                "root",
                PrivilegeSnapshot(shizukuReady = false, root = RootAvailability.GRANTED),
            ),
        ).isEqualTo(PrivilegeKind.ROOT)
    }
}

private class StubHost(override val kind: PrivilegeKind) : PrivilegedUserServiceHost {
    override val displayName: String = "stub"
    override val bindTimeoutMs: Long = 1L
    override fun isAvailable(): Boolean = false
    override fun bind(connection: ServiceConnection): Boolean = false
    override fun unbind(connection: ServiceConnection, destroy: Boolean) = Unit
}
