package com.inputleaf.android.clipboard

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardReceiverTest {
    @Test fun `slow conversion never publishes after a newer clipboard and only latest queued clip is processed`() = runTest {
        val ready = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val applied = mutableListOf<String>()
        val processed = mutableListOf<String>()
        val receiver = ClipboardReceiver(backgroundScope, ClipboardSync(), { it == 1 }, { clip, current ->
            processed += clip.text!!
            if (clip.text == "slow") { ready.complete(Unit); finish.await() }
            if (current()) applied += clip.text
        }, { throw it }, StandardTestDispatcher(testScheduler))
        fun send(text: String) = ClipboardFixtures.events(ClipboardCodec.marshallText(text)).forEach { receiver.accept(it, 1) }
        send("slow")
        runCurrent()
        assertThat(ready.isCompleted).isTrue()
        send("middle")
        send("latest")
        finish.complete(Unit)
        runCurrent()
        assertThat(processed).containsExactly("slow", "latest").inOrder()
        assertThat(applied).containsExactly("latest")
    }

    @Test fun `a new transfer invalidates a previous conversion before its own completion`() = runTest {
        val finish = CompletableDeferred<Unit>()
        var applied = false
        val receiver = ClipboardReceiver(backgroundScope, ClipboardSync(), { true }, { _, current ->
            finish.await(); applied = current()
        }, { throw it }, StandardTestDispatcher(testScheduler))
        ClipboardFixtures.events(ClipboardCodec.marshallText("old")).forEach { receiver.accept(it, 1) }
        runCurrent()
        receiver.accept(InputLeapEvent.ClipboardData(0, 8, 1, "100".toByteArray()), 1)
        finish.complete(Unit)
        runCurrent()
        assertThat(applied).isFalse()
    }

    @Test fun `disconnect reset and a changed generation invalidate in-flight work`() = runTest {
        for (reset in listOf(false, true)) {
            var generation = 1
            val finish = CompletableDeferred<Unit>()
            var applied = false
            val receiver = ClipboardReceiver(backgroundScope, ClipboardSync(), { it == generation }, { _, current ->
                finish.await(); applied = current()
            }, { throw it }, StandardTestDispatcher(testScheduler))
            ClipboardFixtures.events(ClipboardCodec.marshallText("old")).forEach { receiver.accept(it, 1) }
            runCurrent()
            if (reset) receiver.reset() else generation++
            finish.complete(Unit)
            runCurrent()
            assertThat(applied).isFalse()
        }
    }

    @Test fun `a failed clipboard conversion does not stop subsequent text delivery`() = runTest {
        val failures = mutableListOf<Exception>()
        val applied = mutableListOf<String>()
        val receiver = ClipboardReceiver(backgroundScope, ClipboardSync(), { true }, { clip, _ ->
            if (clip.bitmap != null) throw IOException("test disk failure")
            applied += clip.text!!
        }, failures::add, StandardTestDispatcher(testScheduler))
        ClipboardFixtures.events(ClipboardFixtures.blob(2 to ClipboardFixtures.dib())).forEach { receiver.accept(it, 1) }
        runCurrent()
        ClipboardFixtures.events(ClipboardCodec.marshallText("next")).forEach { receiver.accept(it, 1) }
        runCurrent()
        assertThat(failures).hasSize(1)
        assertThat(applied).containsExactly("next")
    }
    @Test fun `allocation failure is reported once and subsequent text and images recover`() = runTest {
        val oom = OutOfMemoryError("injected allocation failure")
        var allocations = 0
        val assembler = ClipboardAssembler(ClipboardAssembler.MAX_BYTES, { size ->
            if (++allocations == 1) throw oom
            ByteArray(size)
        }, {})
        val failures = mutableListOf<Exception>()
        val applied = mutableListOf<String>()
        val receiver = ClipboardReceiver(backgroundScope, ClipboardSync(assembler), { true }, { clip, _ ->
            applied += clip.text ?: "image"
        }, failures::add, StandardTestDispatcher(testScheduler))
        fun send(blob: ByteArray) = ClipboardFixtures.events(blob).forEach { receiver.accept(it, 1) }
        send(ClipboardFixtures.blob(2 to ClipboardFixtures.dib()))
        runCurrent()
        assertThat(failures).hasSize(1)
        assertThat(failures.single()).isInstanceOf(IOException::class.java)
        assertThat(failures.single().cause).isSameInstanceAs(oom)
        assertThat(applied).isEmpty()
        send(ClipboardCodec.marshallText("after failure"))
        runCurrent()
        send(ClipboardFixtures.blob(2 to ClipboardFixtures.dib()))
        runCurrent()
        assertThat(applied).containsExactly("after failure", "image").inOrder()
        assertThat(failures).hasSize(1)
    }

}
