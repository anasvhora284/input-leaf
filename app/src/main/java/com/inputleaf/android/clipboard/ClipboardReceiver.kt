package com.inputleaf.android.clipboard

import com.inputleaf.android.model.InputLeapEvent
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One conversion at a time; queued older clips and obsolete sessions never overwrite newer clips. */
internal class ClipboardReceiver(
    scope: CoroutineScope,
    private val sync: ClipboardSync,
    private val isCurrentSession: (Int) -> Boolean,
    private val apply: suspend (ClipboardContent, () -> Boolean) -> Unit,
    private val onFailure: (Exception) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private class Update(val content: ClipboardContent, val session: Int, val revision: Long)
    private val pending = Channel<Update>(Channel.CONFLATED)
    @Volatile private var revision = 0L

    init {
        scope.launch(dispatcher) {
            for (update in pending) {
                val current = { revision == update.revision && isCurrentSession(update.session) }
                if (current()) {
                    try {
                        apply(update.content, current)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        onFailure(error)
                    }
                }
            }
        }
    }

    @Synchronized
    fun accept(event: InputLeapEvent, session: Int) {
        if (!isCurrentSession(session)) return
        if ((event is InputLeapEvent.ClipboardGrab && event.id == ClipboardSync.PRIMARY) ||
            (event is InputLeapEvent.ClipboardData && event.id == ClipboardSync.PRIMARY &&
                event.mark == ClipboardAssembler.MARK_START)
        ) revision++
        try {
            sync.onServerEvent(event)?.let { pending.trySend(Update(it, session, revision)) }
        } catch (error: IOException) {
            sync.reset()
            onFailure(error)
        }
    }

    @Synchronized
    fun reset() {
        revision++
        sync.reset()
        while (pending.tryReceive().isSuccess) { /* Release queued image data. */ }
    }
}
