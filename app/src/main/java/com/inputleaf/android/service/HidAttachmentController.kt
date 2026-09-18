package com.inputleaf.android.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Last-write-wins HID attach/detach gate.
 *
 * [wanted] is the latest UI/session intent. A generation token drains stale binder
 * calls so rapid Leave→Enter cannot finish in the wrong order, and injector
 * replacement always re-applies even when the wanted flag is unchanged.
 */
internal class HidAttachmentController {
    private val mutex = Mutex()

    @Volatile
    private var wanted = false

    @Volatile
    private var generation = 0

    fun wanted(): Boolean = wanted

    fun setWanted(attached: Boolean) {
        wanted = attached
        generation++
    }

    fun noteInjectorChanged() {
        generation++
    }

    suspend fun applyLatest(apply: (Boolean) -> Unit) {
        mutex.withLock {
            while (true) {
                val seen = generation
                apply(wanted)
                if (seen == generation) return
            }
        }
    }
}
