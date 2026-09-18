package com.inputleaf.android.shizuku.uhid

/**
 * Serialized HID mouse attach/target state. Thread-safe for nonblocking pending updates;
 * callers must not hold this lock across Binder I/O.
 */
internal class HidMouseState(
    private var maxX: Int,
    private var maxY: Int,
) {
    enum class AttachPhase {
        DETACHED,
        ATTACHING,
        ATTACHED,
    }

    private val lock = Any()

    var phase: AttachPhase = AttachPhase.DETACHED
        private set

    /** True only after a successful attach; false while attaching or after detach/failure. */
    var attached: Boolean = false
        private set

    var usable: Boolean = true
        private set

    var buttons: Int = 0
        private set

    /** Protocol-authoritative target from InputLeap. */
    var protocolX: Int = 0
        private set
    var protocolY: Int = 0
        private set

    /** Open-loop estimate of Android's cooked pointer position. */
    var cookedX: Int = centerX()
        private set
    var cookedY: Int = centerY()
        private set

    /** Latest enter/DMMV target retained while attach is in flight. */
    var pendingSnapX: Int? = null
        private set
    var pendingSnapY: Int? = null
        private set

    /**
     * Monotonic generation of [protocolX]/[protocolY] stores. [markLeave] snapshots this
     * so a later detach cannot drop an Enter/DMMV that arrived after Leave was received.
     */
    private var targetEpoch: Int = 0
    private var leaveEpoch: Int = -1

    /** True when the next snap should assume AOSP seeded the pointer at display center. */
    var needsCenterSeed: Boolean = false
        private set

    var lastHidMoveAt: Long = 0L
        private set

    var edgeFlags: MouseEdgeAnchor.Flags = MouseEdgeAnchor.Flags()
        private set

    var lastWasEdgePulse: Boolean = false
        private set

    fun maxXExclusive(): Int = maxX
    fun maxYExclusive(): Int = maxY

    fun centerX(): Int = maxX / 2
    fun centerY(): Int = maxY / 2

    fun clampX(x: Int): Int = x.coerceIn(0, maxX)
    fun clampY(y: Int): Int = y.coerceIn(0, maxY)

    fun markUnusable() {
        synchronized(lock) {
            usable = false
            attached = false
            phase = AttachPhase.DETACHED
        }
    }

    fun beginAttach() {
        synchronized(lock) {
            if (phase == AttachPhase.ATTACHED) return
            phase = AttachPhase.ATTACHING
        }
    }

    /**
     * @param newDevice true when the UHID device was actually created (not idempotent reopen).
     */
    fun completeAttach(newDevice: Boolean) {
        synchronized(lock) {
            attached = true
            usable = true
            phase = AttachPhase.ATTACHED
            if (newDevice) {
                cookedX = centerX()
                cookedY = centerY()
                needsCenterSeed = true
                lastHidMoveAt = 0L
                lastWasEdgePulse = false
                edgeFlags = MouseEdgeAnchor.Flags()
            }
        }
    }

    fun detach() {
        synchronized(lock) {
            attached = false
            phase = AttachPhase.DETACHED
            buttons = 0
            needsCenterSeed = false
            lastWasEdgePulse = false
            edgeFlags = MouseEdgeAnchor.Flags()
            // Drop the pre-leave target only. A newer Enter/DMMV (targetEpoch > leaveEpoch)
            // must survive so attach can snap to it after a drain-ordered close.
            if (targetEpoch == leaveEpoch) {
                pendingSnapX = null
                pendingSnapY = null
            }
        }
    }

    /**
     * Snapshot the current target generation when InputLeap Leave is received.
     * Must run at Leave-receive, not when the 300ms debounce later detaches, so a
     * subsequent Enter cannot be marked stale.
     */
    fun markLeave() {
        synchronized(lock) {
            leaveEpoch = targetEpoch
        }
    }

    fun resetOnDisconnect() {
        synchronized(lock) {
            attached = false
            usable = true
            phase = AttachPhase.DETACHED
            buttons = 0
            pendingSnapX = null
            pendingSnapY = null
            targetEpoch = 0
            leaveEpoch = -1
            needsCenterSeed = false
            lastHidMoveAt = 0L
            lastWasEdgePulse = false
            edgeFlags = MouseEdgeAnchor.Flags()
        }
    }

    /**
     * Authoritative InputLeap Enter. Always last-write-wins for the pending snap,
     * and re-arms edge saturation so a new edge can pulse after a prior session.
     */
    fun onEnter(x: Int, y: Int) {
        val tx = clampX(x)
        val ty = clampY(y)
        synchronized(lock) {
            storeTargetLocked(tx, ty)
            lastWasEdgePulse = false
            edgeFlags = MouseEdgeAnchor.Flags()
        }
    }

    fun updatePointerTarget(x: Int, y: Int) {
        val tx = clampX(x)
        val ty = clampY(y)
        synchronized(lock) {
            storeTargetLocked(tx, ty)
        }
    }

    /** Retain latest protocol coords during attach without emitting HID yet. */
    fun queueTargetWhileAttaching(x: Int, y: Int): Boolean {
        synchronized(lock) {
            if (phase != AttachPhase.ATTACHING) return false
            storeTargetLocked(clampX(x), clampY(y))
            return true
        }
    }

    private fun storeTargetLocked(x: Int, y: Int) {
        protocolX = x
        protocolY = y
        pendingSnapX = x
        pendingSnapY = y
        targetEpoch++
    }

    fun consumePendingSnap(): Pair<Int, Int>? {
        synchronized(lock) {
            val x = pendingSnapX ?: return null
            val y = pendingSnapY ?: return null
            pendingSnapX = null
            pendingSnapY = null
            return Pair(x, y)
        }
    }

    fun peekPendingSnap(): Pair<Int, Int>? {
        synchronized(lock) {
            val x = pendingSnapX ?: return null
            val y = pendingSnapY ?: return null
            return Pair(x, y)
        }
    }

    fun clearCenterSeed() {
        synchronized(lock) {
            needsCenterSeed = false
        }
    }

    fun needsCenterSeed(): Boolean = synchronized(lock) { needsCenterSeed }

    fun applySuccessfulMove(plan: MousePointerCompensation.HidMovePlan, nowMs: Long) {
        val (dx, dy) = plan.cookedDelta()
        synchronized(lock) {
            cookedX = (cookedX + dx).coerceIn(0, maxX)
            cookedY = (cookedY + dy).coerceIn(0, maxY)
            lastHidMoveAt = nowMs
            lastWasEdgePulse = false
            needsCenterSeed = false
        }
    }

    fun applySuccessfulPlan(plan: MouseEdgeAnchor.Plan, nowMs: Long) {
        synchronized(lock) {
            cookedX = plan.cookedXAfter
            cookedY = plan.cookedYAfter
            edgeFlags = plan.flagsAfter
            lastHidMoveAt = nowMs
            lastWasEdgePulse = plan.isEdgePulse
            needsCenterSeed = false
        }
    }

    fun plannerInput(nowMs: Long): MouseEdgeAnchor.Input {
        synchronized(lock) {
            return MouseEdgeAnchor.Input(
                targetX = protocolX,
                targetY = protocolY,
                cookedX = cookedX,
                cookedY = cookedY,
                maxX = maxX,
                maxY = maxY,
                flags = edgeFlags,
                preferTangentialFirst = needsCenterSeed,
                lastWasEdgePulse = lastWasEdgePulse,
                dtMs = if (lastHidMoveAt == 0L) 0L else nowMs - lastHidMoveAt,
            )
        }
    }

    fun movementDtMs(nowMs: Long): Long {
        synchronized(lock) {
            return if (lastHidMoveAt == 0L) 0L else nowMs - lastHidMoveAt
        }
    }

    fun setButtons(value: Int) {
        synchronized(lock) {
            buttons = value
        }
    }

    fun buttons(): Int = synchronized(lock) { buttons }

    fun errorToTarget(targetX: Int, targetY: Int): Pair<Int, Int> {
        synchronized(lock) {
            return Pair(targetX - cookedX, targetY - cookedY)
        }
    }

    fun cookedPosition(): Pair<Int, Int> = synchronized(lock) { Pair(cookedX, cookedY) }

    fun isAttachedOrAttaching(): Boolean = synchronized(lock) {
        phase == AttachPhase.ATTACHED || phase == AttachPhase.ATTACHING
    }

    /**
     * Resize bounds without re-seeding center when the HID device stays attached.
     * Scales cooked/protocol estimates proportionally.
     */
    fun resizeDisplay(newMaxX: Int, newMaxY: Int) {
        synchronized(lock) {
            if (newMaxX == maxX && newMaxY == maxY) return
            val oldMaxX = maxX
            val oldMaxY = maxY
            protocolX = MousePointerCompensation.scaleCoord(protocolX, oldMaxX, newMaxX)
            protocolY = MousePointerCompensation.scaleCoord(protocolY, oldMaxY, newMaxY)
            pendingSnapX = pendingSnapX?.let { MousePointerCompensation.scaleCoord(it, oldMaxX, newMaxX) }
            pendingSnapY = pendingSnapY?.let { MousePointerCompensation.scaleCoord(it, oldMaxY, newMaxY) }
            if (phase == AttachPhase.ATTACHED) {
                cookedX = MousePointerCompensation.scaleCoord(cookedX, oldMaxX, newMaxX)
                cookedY = MousePointerCompensation.scaleCoord(cookedY, oldMaxY, newMaxY)
            } else {
                cookedX = newMaxX / 2
                cookedY = newMaxY / 2
            }
            maxX = newMaxX
            maxY = newMaxY
            edgeFlags = MouseEdgeAnchor.Flags()
            lastWasEdgePulse = false
        }
    }
}
