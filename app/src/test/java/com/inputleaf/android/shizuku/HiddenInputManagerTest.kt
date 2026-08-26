package com.inputleaf.android.shizuku

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HiddenInputManagerTest {

    @Test
    fun `resolve does not throw`() {
        HiddenInputManager.resolve()
    }

    @Test
    fun `findInjectMethod prefers two-arg injectInputEvent`() {
        val method = HiddenInputManager.findInjectMethod(TwoAndThreeArgInjector::class.java)
        assertThat(method).isNotNull()
        assertThat(method!!.name).isEqualTo("injectInputEvent")
        assertThat(method.parameterCount).isEqualTo(2)
    }

    @Test
    fun `findInjectMethod accepts three-arg injectInputEvent`() {
        val method = HiddenInputManager.findInjectMethod(ThreeArgInjector::class.java)
        assertThat(method).isNotNull()
        assertThat(method!!.name).isEqualTo("injectInputEvent")
        assertThat(method.parameterCount).isEqualTo(3)
    }

    @Test
    fun `findInjectMethod falls back to injectInputEventToTarget`() {
        val method = HiddenInputManager.findInjectMethod(TargetUidInjector::class.java)
        assertThat(method).isNotNull()
        assertThat(method!!.name).isEqualTo("injectInputEventToTarget")
    }

    @Test
    fun `invokeInject fills extra int parameters with invalid uid`() {
        val injector = RecordingInjector()
        val method = HiddenInputManager.findInjectMethod(RecordingInjector::class.java)!!
        method.isAccessible = true
        val result = HiddenInputManager.invokeInject(
            HiddenInputManager.Target(injector, method),
            event = "down",
            mode = 0
        )
        assertThat(result).isEqualTo(true)
        assertThat(injector.event).isEqualTo("down")
        assertThat(injector.mode).isEqualTo(0)
        assertThat(injector.targetUid).isEqualTo(-1)
    }

    @Suppress("unused")
    private class TwoAndThreeArgInjector {
        fun injectInputEvent(event: Any, mode: Int): Boolean = true
        fun injectInputEvent(event: Any, mode: Int, targetUid: Int): Boolean = false
    }

    @Suppress("unused")
    private class ThreeArgInjector {
        fun injectInputEvent(event: Any, mode: Int, targetUid: Int): Boolean = true
    }

    @Suppress("unused")
    private class TargetUidInjector {
        fun injectInputEventToTarget(event: Any, mode: Int, targetUid: Int): Boolean = true
    }

    private class RecordingInjector {
        var event: Any? = null
        var mode: Int? = null
        var targetUid: Int? = null

        @Suppress("unused")
        fun injectInputEvent(event: Any, mode: Int, targetUid: Int): Boolean {
            this.event = event
            this.mode = mode
            this.targetUid = targetUid
            return true
        }
    }
}
