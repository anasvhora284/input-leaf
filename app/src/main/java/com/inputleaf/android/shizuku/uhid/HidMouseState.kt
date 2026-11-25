package com.inputleaf.android.shizuku.uhid

import kotlin.math.roundToInt

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

    /** Open-loop estimate of Android's cooked pointer position, in fractional pixels. */
    var cookedX: Double = centerX().toDouble()
        private set
    var cookedY: Double = centerY().toDouble()
        private set

    /** Android's acceleration state for the reports this device has sent. */
    private val velocity = PointerVelocityModel()

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

    var edgeFlags: MouseEdgeAnchor.Flags = MouseEdgeAnchor.Flags()
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

    /** The last Enter; where the injector's own warp puts a new device ([HidMouseEnterWarp]). */
    private var enterX: Int = 0
    private var enterY: Int = 0

    /**
     * @param newDevice true when the UHID device was actually created (not idempotent reopen).
     * @param warpedToEnter true when the injector already warped it to the last Enter. The
     * model then starts there, not at centre: the attached phase is published in the same
     * step, so a move racing in plans from where the pointer really is (#88).
     */
    fun completeAttach(newDevice: Boolean, warpedToEnter: Boolean = false) {
        synchronized(lock) {
            // The warp ends with a pause that clears Android's tracker; a new device has none.
            if (warpedToEnter || newDevice) velocity.reset()
            if (newDevice) resetRelativeRemainder()
            if (warpedToEnter) {
                cookedX = enterX.toDouble()
                cookedY = enterY.toDouble()
                needsCenterSeed = false
                // The warp ended against every edge the Enter lies on.
                edgeFlags = MouseEdgeAnchor.Flags(
                    minX = enterX == 0,
                    maxX = enterX == maxX,
                    minY = enterY == 0,
                    maxY = enterY == maxY,
                )
            } else if (newDevice) {
                cookedX = centerX().toDouble()
                cookedY = centerY().toDouble()
                needsCenterSeed = true
                edgeFlags = MouseEdgeAnchor.Flags()
            }
            attached = true
            usable = true
            phase = AttachPhase.ATTACHED
        }
    }

    fun detach() {
        synchronized(lock) {
            attached = false
            phase = AttachPhase.DETACHED
            buttons = 0
            needsCenterSeed = false
            edgeFlags = MouseEdgeAnchor.Flags()
            resetRelativeRemainder()
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
            edgeFlags = MouseEdgeAnchor.Flags()
            resetRelativeRemainder()
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
            enterX = tx
            enterY = ty
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

    /**
     * Books a sent report. With [settingsSpeed], a move is re-predicted at its real write
     * time: the plan guessed that time, and on fast moves a millisecond changes Android's
     * gain, so the next report then corrects what this one missed (#88).
     */
    fun applySuccessfulPlan(plan: MouseEdgeAnchor.Plan, sentAtNs: Long, settingsSpeed: Int? = null) {
        synchronized(lock) {
            if (settingsSpeed != null && !plan.isEdgePulse) {
                val (dx, dy) = velocity.predict(sentAtNs, plan.hidX.toDouble(), plan.hidY.toDouble(), settingsSpeed)
                cookedX = (cookedX + dx).coerceIn(0.0, maxX.toDouble())
                cookedY = (cookedY + dy).coerceIn(0.0, maxY.toDouble())
            } else {
                cookedX = plan.cookedXAfter
                cookedY = plan.cookedYAfter
            }
            velocity.record(sentAtNs, plan.hidX, plan.hidY)
            edgeFlags = plan.flagsAfter
            needsCenterSeed = false
        }
    }

    /** Fractions of a count not yet sent by [planRelative]. */
    private var remX = 0.0
    private var remY = 0.0

    private fun resetRelativeRemainder() {
        remX = 0.0
        remY = 0.0
    }

    /**
     * A relative move (DMRM) as raw counts, for games that capture the pointer: Android
     * then reads counts * [scale] with no clamp and no acceleration. No screen clamp or edge
     * pulse here; the cooked estimate still follows through [applySuccessfulPlan]. Null
     * when under half a count, which is carried to the next report.
     */
    fun planRelative(dx: Int, dy: Int, scale: Double): MouseEdgeAnchor.Plan? {
        synchronized(lock) {
            val vx = dx / scale + remX
            val vy = dy / scale + remY
            val cx = vx.roundToInt()
            val cy = vy.roundToInt()
            remX = vx - cx
            remY = vy - cy
            if (cx == 0 && cy == 0) return null
            return MouseEdgeAnchor.Plan(cx, cy, MouseEdgeAnchor.Flags(), cookedX, cookedY, isEdgePulse = false)
        }
    }

    fun plannerInput(nowNs: Long): MouseEdgeAnchor.Input {
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
                velocity = velocity,
                nowNs = nowNs,
            )
        }
    }

    fun setButtons(value: Int) {
        synchronized(lock) {
            buttons = value
        }
    }

    fun buttons(): Int = synchronized(lock) { buttons }

    fun errorToTarget(targetX: Int, targetY: Int): Pair<Double, Double> {
        synchronized(lock) {
            return Pair(targetX - cookedX, targetY - cookedY)
        }
    }

    fun cookedPosition(): Pair<Double, Double> = synchronized(lock) { Pair(cookedX, cookedY) }

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
                if (oldMaxX > 0) cookedX = (cookedX * newMaxX / oldMaxX).coerceIn(0.0, newMaxX.toDouble())
                if (oldMaxY > 0) cookedY = (cookedY * newMaxY / oldMaxY).coerceIn(0.0, newMaxY.toDouble())
            } else {
                cookedX = (newMaxX / 2).toDouble()
                cookedY = (newMaxY / 2).toDouble()
            }
            maxX = newMaxX
            maxY = newMaxY
            edgeFlags = MouseEdgeAnchor.Flags()
        }
    }
}
