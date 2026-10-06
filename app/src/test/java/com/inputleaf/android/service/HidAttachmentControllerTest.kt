package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HidAttachmentControllerTest {

    @Test
    fun `rapid leave then enter applies the last wanted state`() = runTest {
        val gate = HidAttachmentController()
        val applied = mutableListOf<Boolean>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        gate.setWanted(true)
        val first = async {
            gate.applyLatest { wanted ->
                if (wanted && applied.isEmpty()) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                applied += wanted
            }
        }
        firstStarted.await()
        gate.setWanted(false)
        gate.setWanted(true)
        val second = async {
            gate.applyLatest { wanted -> applied += wanted }
        }
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertThat(gate.wanted()).isTrue()
        assertThat(applied.last()).isTrue()
        assertThat(applied).contains(true)
    }

    @Test
    fun `injector replacement reapplies wanted attach`() = runTest {
        val gate = HidAttachmentController()
        val applied = mutableListOf<Boolean>()

        gate.setWanted(true)
        gate.applyLatest { applied += it }
        assertThat(applied).containsExactly(true)

        gate.noteInjectorChanged()
        gate.applyLatest { applied += it }
        assertThat(applied).containsExactly(true, true)
    }

    @Test
    fun `stable leave applies detach`() = runTest {
        val gate = HidAttachmentController()
        val applied = mutableListOf<Boolean>()
        gate.setWanted(true)
        gate.applyLatest { applied += it }
        gate.setWanted(false)
        gate.applyLatest { applied += it }
        assertThat(applied).containsExactly(true, false).inOrder()
    }
}
