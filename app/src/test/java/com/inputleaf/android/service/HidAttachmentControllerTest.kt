package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HidAttachmentControllerTest {

    @Test
    fun `rapid leave then enter applies the last wanted state`() {
        runBlocking {
            val gate = HidAttachmentController()
            val applied = mutableListOf<Boolean>()

            gate.setWanted(true)
            val first = async {
                gate.applyLatest { wanted ->
                    if (wanted && applied.isEmpty()) {
                        Thread.sleep(40)
                    }
                    applied += wanted
                }
            }
            delay(10)
            gate.setWanted(false)
            gate.setWanted(true)
            val second = async {
                gate.applyLatest { wanted -> applied += wanted }
            }
            first.await()
            second.await()

            assertThat(gate.wanted()).isTrue()
            assertThat(applied.last()).isTrue()
            assertThat(applied).contains(true)
        }
    }

    @Test
    fun `injector replacement reapplies wanted attach`() {
        runBlocking {
            val gate = HidAttachmentController()
            val applied = mutableListOf<Boolean>()

            gate.setWanted(true)
            gate.applyLatest { applied += it }
            assertThat(applied).containsExactly(true)

            gate.noteInjectorChanged()
            gate.applyLatest { applied += it }
            assertThat(applied).containsExactly(true, true)
        }
    }

    @Test
    fun `stable leave applies detach`() {
        runBlocking {
            val gate = HidAttachmentController()
            val applied = mutableListOf<Boolean>()
            gate.setWanted(true)
            gate.applyLatest { applied += it }
            gate.setWanted(false)
            gate.applyLatest { applied += it }
            assertThat(applied).containsExactly(true, false).inOrder()
        }
    }
}
