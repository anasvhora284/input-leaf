package com.inputleaf.android.shizuku

import android.os.Binder
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.shizuku.uhid.UhidChannel
import com.inputleaf.android.shizuku.uhid.UhidProtocol
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A client that dies must leave no HID device registered. The client's own disconnect()
 * cannot achieve this: by then its binder calls throw DeadObjectException and are
 * swallowed, so UHID_DESTROY is only written if the injector does it itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class InputInjectorServiceClientDeathTest {

    @Test
    fun `client death writes UHID_DESTROY for an attached mouse`() {
        val out = ByteArrayOutputStream()
        val service = InputInjectorService { UhidChannel.forTesting(out) }
        service.attachClient(Binder())

        assertThat(service.openVirtualMouse()).isTrue()
        assertThat(typesIn(out.toByteArray())).contains(UhidProtocol.UHID_CREATE2)
        assertThat(typesIn(out.toByteArray())).doesNotContain(UhidProtocol.UHID_DESTROY)

        service.deathRecipientForTest().binderDied()

        assertThat(typesIn(out.toByteArray())).contains(UhidProtocol.UHID_DESTROY)
    }

    @Test
    fun `client death leaves no device open so a later injection is refused`() {
        val service = InputInjectorService { UhidChannel.forTesting(ByteArrayOutputStream()) }
        service.attachClient(Binder())
        assertThat(service.openVirtualMouse()).isTrue()
        assertThat(service.injectHidMouse(1, 0, 0, 0)).isTrue()

        service.deathRecipientForTest().binderDied()

        assertThat(service.injectHidMouse(1, 0, 0, 0)).isFalse()
    }

    @Test
    fun `death handling is idempotent and safe with nothing attached`() {
        val service = InputInjectorService { UhidChannel.forTesting(ByteArrayOutputStream()) }
        service.attachClient(Binder())

        service.deathRecipientForTest().binderDied()
        service.deathRecipientForTest().binderDied()
        service.destroy()
    }

    @Test
    fun `a null token is ignored rather than crashing the injector`() {
        val service = InputInjectorService { UhidChannel.forTesting(ByteArrayOutputStream()) }
        service.attachClient(null)
        assertThat(service.openVirtualMouse()).isTrue()
        service.destroy()
    }

    @Test
    fun `re-attaching a client replaces the previous watch`() {
        val service = InputInjectorService { UhidChannel.forTesting(ByteArrayOutputStream()) }
        service.attachClient(Binder())
        service.attachClient(Binder())
        assertThat(service.openVirtualMouse()).isTrue()

        service.deathRecipientForTest().binderDied()

        assertThat(service.injectHidMouse(1, 0, 0, 0)).isFalse()
    }

    private fun typesIn(packet: ByteArray): List<Int> {
        val types = mutableListOf<Int>()
        var offset = 0
        while (offset + 4 <= packet.size) {
            val type = ByteBuffer.wrap(packet, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            types += type
            offset += when (type) {
                UhidProtocol.UHID_CREATE2 -> UhidProtocol.CREATE2_PACKET_SIZE
                UhidProtocol.UHID_INPUT2 -> UhidProtocol.INPUT2_PACKET_SIZE
                UhidProtocol.UHID_DESTROY -> UhidProtocol.EVENT_PACKET_SIZE
                else -> 4
            }
        }
        return types
    }
}
