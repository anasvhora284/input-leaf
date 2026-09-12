package com.inputleaf.android.shizuku.uhid

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A relative HID mouse — the only kind of pointing device Android draws a cursor for.
 *
 * Verified on device: a relative `REL_X`/`REL_Y` device is classified
 * `Classes: CURSOR | EXTERNAL` and gets a `MousePointerController`, whose sprite sits far
 * above `NotificationShade`. An absolute `ABS_X`/`ABS_Y` device is ignored by InputReader
 * entirely, and injecting absolute events moves the touch point but NOT the drawn cursor
 * (measured: injected tap landed at 900,1800 while the sprite stayed at 204).
 *
 * ### Why counts are divided by a constant
 *
 * Android scales mouse movement by a velocity curve. The `PointerVelocityControlParameters`
 * shown in `dumpsys input` are NOT it — AOSP's own comment says those are "ignored" for
 * mice. The real curve is `AccelerationCurve.cpp`, whose first segment has a zero
 * reciprocal term, making the gain **constant** below ~1008 counts/s:
 *
 *     gain = 0.64 * kSensitivityFactors[pointer_speed + 7] / 10 * 3.19
 *          = 0.64 * 1.0 * 3.19 = 2.0416   (at pointer_speed 0)
 *
 * Measured on device with the app stopped: 400 counts travelled 818.7px (gain 2.047) and
 * 200 counts travelled 408.4px (gain 2.0420). Both match the formula to better than 0.3%.
 *
 * So there is nothing to learn, estimate, or correct — earlier versions carried a gain
 * model, an acceleration model, a `dumpsys` position read-back and a drift-correction
 * pacer, and were *less* accurate (mean error 113px) than this arithmetic.
 *
 * That constant is only correct at low speed. Measured on the same device, 100 counts
 * emitted as a fast burst travelled 922px (gain 9.22), not 204px. So the gain is taken
 * from [AccelerationCurve], evaluated at the rate this pointer is actually emitting.
 *
 * Two earlier designs failed here and are worth not repeating:
 *  - Capping how far a report could move limited the cursor to ~1430 px/s. A hand moves
 *    faster, so the server's pointer left the screen while this one crawled and the
 *    session bounced straight back to the laptop.
 *  - Holding a large move until it could go out as an isolated report kept the position
 *    exact but dropped updates to ~20/s, which looked visibly steppy while dragging.
 *
 * Evaluating the curve avoids both: every move goes out immediately, at whatever rate the
 * server is driving, with the counts scaled for the gain that rate will actually produce.
 */
internal class RelativePointer(
    private val sink: HidReportSink,
    private val curve: AccelerationCurve = AccelerationCurve(),
    private val clock: () -> Long = System::nanoTime,
    private val diag: (String) -> Unit = {},
) {

    /**
     * Counts emitted recently, as (timestamp, magnitude). Android estimates speed over a
     * 100ms horizon, so the gain is evaluated over the same window.
     */
    private val recent = ArrayDeque<Pair<Long, Double>>()

    /** When the resync slam went out, while entry is still waiting to be positioned. */
    private var slamNanos: Long? = null

    private var boundsX = 0
    private var boundsY = 0


    // Where the server says the cursor should be.
    private var targetX = 0
    private var targetY = 0
    // Where our emitted counts should have put it, in pixels.
    private var placedX = 0.0
    private var placedY = 0.0
    private var synced = false

    // Sub-count remainders, so repeated small moves are not rounded away.
    private var carryX = 0.0
    private var carryY = 0.0

    private var buttons = 0
    // Null until the first motion report; a 0 sentinel would be indistinguishable
    // from a report emitted at clock() == 0.
    private var lastEmitNanos: Long? = null

    /**
     * Forget the cursor position. The next move slams to the screen origin first, where
     * Android's edge clamping gives a known reference point regardless of any scaling.
     * Call on enter/leave.
     */
    fun resync() {
        synced = false
        carryX = 0.0
        carryY = 0.0
        // The slam is enormous; leaving it in the window would make the next report look
        // like a 30,000 count/s sweep and scale it for a gain it will never see.
        recent.clear()
        slamNanos = null
    }

    fun moveTo(x: Int, y: Int, screenWidth: Int, screenHeight: Int) {
        targetX = x.coerceIn(0, screenWidth)
        targetY = y.coerceIn(0, screenHeight)
        boundsX = screenWidth
        boundsY = screenHeight
        if (!synced) {
            // Deliberately uncompensated: far beyond the screen, so Android clamps it at
            // the edge and no amount of scaling changes where it lands.
            slamNanos = clock()
            emitSlam(-SLAM_COUNTS, -SLAM_COUNTS)
            placedX = 0.0
            placedY = 0.0
            synced = true
            carryX = 0.0
            carryY = 0.0
            diag("resync: slam to origin, target=($targetX,$targetY)")
        }
        drain()
    }

    /**
     * Emits one report toward the target, rate-limited so the gain stays constant.
     * Safe to call repeatedly; does nothing once the cursor is where it should be.
     */
    fun drain() {
        if (!synced) return
        val gapX = targetX - placedX
        val gapY = targetY - placedY
        val gap = hypot(gapX, gapY)
        if (gap < MIN_GAP_PX) return

        val now = clock()
        val sinceSlam = slamNanos?.let { now - it }
        if (sinceSlam != null) {
            // The slam leaves Android's velocity estimate saturated, and its decay is not
            // modelled well enough to scale against. Entry is positioned once, after the
            // estimate has been forgotten, where the gain is the measured resting value.
            // This costs ~50ms per border crossing and nothing during movement.
            if (sinceSlam < ISOLATION_NANOS) return
            slamNanos = null
            val counts = gap / AccelerationCurve.RESTING_GAIN
            val scale = counts / gap
            val sx = (gapX * scale).roundToInt()
            val sy = (gapY * scale).roundToInt()
            if (sx != 0 || sy != 0) {
                emit(sx, sy)
                placedX += sx * AccelerationCurve.RESTING_GAIN
                placedY += sy * AccelerationCurve.RESTING_GAIN
            }
            carryX = 0.0
            carryY = 0.0
            return
        }
        // Against a screen edge, pin exactly. The server clamps its own coordinates, so a
        // target of 0 or the screen bound means "hard against the edge" -- and Android
        // clamps an oversized delta to precisely that edge, which a computed delta cannot
        // guarantee. Without this the cursor sat a few pixels short of, or past, the edge,
        // so the phone and the server disagreed about when the pointer had left and a
        // small dead gap appeared on the way back.
        if (pinToEdge(gapX, gapY)) return

        val (recentCounts, windowSeconds) = recentEmission(now)
        // Counts that will travel `gap` pixels at the gain this emission rate produces.
        val counts = curve.countsFor(gap, recentCounts, windowSeconds)
        val scale = counts / gap
        val countsX = gapX * scale + carryX
        val countsY = gapY * scale + carryY

        val dx = countsX.roundToInt()
        val dy = countsY.roundToInt()
        // Keep the fraction we could not send; without this a slow drag of less than half
        // a count per report would never move at all.
        carryX = countsX - dx
        carryY = countsY - dy
        if (dx == 0 && dy == 0) return

        emit(dx, dy)
        val gain = if (counts != 0.0) gap / counts else AccelerationCurve.RESTING_GAIN
        placedX += dx * gain
        placedY += dy * gain
    }

    /**
     * Pins an axis that is hard against a screen edge, one axis per report so a huge
     * delta on one never distorts the speed estimate for the other.
     * @return true if a pinning report was emitted
     */
    private fun pinToEdge(gapX: Double, gapY: Double): Boolean {
        val edgeX = edgeDirection(targetX, placedX, gapX, boundsX)
        if (edgeX != 0) {
            emitSlam(edgeX * SLAM_COUNTS, 0)
            placedX = targetX.toDouble()
            carryX = 0.0
            recent.clear()   // a clamped push is not travel, and must not skew the window
            return true
        }
        val edgeY = edgeDirection(targetY, placedY, gapY, boundsY)
        if (edgeY != 0) {
            emitSlam(0, edgeY * SLAM_COUNTS)
            placedY = targetY.toDouble()
            carryY = 0.0
            recent.clear()
            return true
        }
        return false
    }

    /** -1 or +1 when this axis is against an edge and not already there, else 0. */
    private fun edgeDirection(target: Int, placed: Double, gap: Double, bound: Int): Int {
        if (bound <= 0 || abs(gap) < EDGE_TOLERANCE_PX) return 0
        return when {
            target <= 0 -> -1
            target >= bound -> 1
            else -> 0
        }
    }

    /** Counts emitted inside the curve's horizon, and how long that window actually is. */
    private fun recentEmission(now: Long): Pair<Double, Double> {
        while (recent.isNotEmpty() && now - recent.first().first > HORIZON_NANOS) {
            recent.removeFirst()
        }
        if (recent.isEmpty()) return 0.0 to HORIZON_SECONDS
        var total = 0.0
        for ((_, magnitude) in recent) total += magnitude
        val span = (now - recent.first().first).coerceAtLeast(1L) / 1e9
        return total to span
    }

    fun button(buttonId: Int, pressed: Boolean) {
        val mask = buttonMask(buttonId) ?: return
        // The cursor must be where the server thinks it is BEFORE the button changes.
        // Movement can be held briefly waiting for its quiet gap, and a click emitted
        // meanwhile lands at the previous position: double-clicks end up too far apart
        // for Android to pair them, and a drag starts from the wrong point.
        flushMotion()
        buttons = if (pressed) buttons or mask else buttons and mask.inv()
        emit(0, 0)
    }

    /**
     * Delivers any held movement so the cursor is on target. Bounded: a click is worth a
     * few milliseconds of delay, never an unbounded stall on the caller's thread.
     */
    private fun flushMotion() {
        if (isSettled()) return
        // Bounded by attempts, not wall time: a clock that does not advance (a test fake,
        // or a suspended process) made the earlier time-based loop spin forever.
        repeat(FLUSH_ATTEMPTS) {
            drain()
            if (isSettled()) return
            try {
                Thread.sleep(FLUSH_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /**
     * @param horizontal InputLeap x delta, 120 units per notch
     * @param vertical InputLeap y delta, 120 units per notch
     */
    fun wheel(horizontal: Int, vertical: Int) {
        val pan = (horizontal / WHEEL_UNITS_PER_NOTCH).coerceIn(-127, 127)
        val notches = (vertical / WHEEL_UNITS_PER_NOTCH).coerceIn(-127, 127)
        if (pan == 0 && notches == 0) return
        // Scroll targets whatever is under the cursor, so it has the same ordering
        // requirement as a click.
        flushMotion()
        sendReport(0, 0, notches, pan)
    }

    /** True when the cursor has reached the target, within [MIN_GAP_PX]. */
    internal fun isSettled(): Boolean =
        !synced || hypot(targetX - placedX, targetY - placedY) < MIN_GAP_PX

    /** Where the emitted counts should have put the cursor, for tests and diagnostics. */
    internal fun placedPosition(): Pair<Int, Int> = placedX.roundToInt() to placedY.roundToInt()

    /** The slam is clamped at the screen edge, so it is not real travel to model. */
    private fun emitSlam(dx: Int, dy: Int) = sendReport(dx, dy, 0, 0)

    private fun emit(dx: Int, dy: Int) {
        sendReport(dx, dy, 0, 0)
        if (dx != 0 || dy != 0) {
            recent.addLast(clock() to hypot(dx.toDouble(), dy.toDouble()))
            while (recent.size > HORIZON_MAX_REPORTS) recent.removeFirst()
        }
        // Zero-motion reports (button, wheel) do not feed Android's velocity estimator,
        // so only real movement restarts the isolation window. The slam counts: a report
        // 8ms after it is NOT isolated, and treating it as such is what made earlier
        // builds overshoot into the far edge on entry.
        if (dx != 0 || dy != 0) lastEmitNanos = clock()
    }

    private fun sendReport(dx: Int, dy: Int, wheel: Int, pan: Int) {
        sink.sendReport(
            byteArrayOf(
                buttons.toByte(),
                (dx and 0xFF).toByte(),
                ((dx shr 8) and 0xFF).toByte(),
                (dy and 0xFF).toByte(),
                ((dy shr 8) and 0xFF).toByte(),
                wheel.toByte(),
                pan.toByte(),
            )
        )
    }

    /** InputLeap button ids: 1 = left, 2 = middle, 3 = right. HID bit order is L/R/M. */
    private fun buttonMask(buttonId: Int): Int? = when (buttonId) {
        1 -> 0x01
        2 -> 0x04
        3 -> 0x02
        else -> null
    }

    companion object {
        /** Android estimates pointer speed over a 100ms horizon; match it. */
        private const val HORIZON_NANOS = 100_000_000L
        private const val HORIZON_SECONDS = 0.1
        private const val HORIZON_MAX_REPORTS = 64

        /** Attempts a button or scroll makes to let held movement land. */
        private const val FLUSH_ATTEMPTS = 24
        private const val FLUSH_POLL_MS = 4L

        private const val SLAM_COUNTS = 32767

        /**
         * Quiet period after the slam before entry is positioned. Android forgets its
         * velocity history after 40ms; a margin covers scheduling jitter.
         */
        private const val ISOLATION_NANOS = 50L * 1_000_000
        private const val MIN_GAP_PX = 1.0

        /** Below this the cursor is already on the edge and re-pinning would just spam. */
        private const val EDGE_TOLERANCE_PX = 2.0

        private const val WHEEL_UNITS_PER_NOTCH = 120

        const val REPORT_SIZE = 7

        /** Report: buttons(8) | X(16 rel) | Y(16 rel) | wheel(8 rel) | AC Pan(8 rel). */
        val DESCRIPTOR: ByteArray = byteArrayOf(
            0x05, 0x01,                     // Usage Page (Generic Desktop)
            0x09, 0x02,                     // Usage (Mouse)
            0xA1.toByte(), 0x01,            // Collection (Application)
            0x09, 0x01,                     //   Usage (Pointer)
            0xA1.toByte(), 0x00,            //   Collection (Physical)
            0x05, 0x09,                     //     Usage Page (Button)
            0x19, 0x01,                     //     Usage Minimum (Button 1)
            0x29, 0x03,                     //     Usage Maximum (Button 3)
            0x15, 0x00,                     //     Logical Minimum (0)
            0x25, 0x01,                     //     Logical Maximum (1)
            0x95.toByte(), 0x03,            //     Report Count (3)
            0x75, 0x01,                     //     Report Size (1)
            0x81.toByte(), 0x02,            //     Input (Data,Var,Abs)
            0x95.toByte(), 0x01,            //     Report Count (1)
            0x75, 0x05,                     //     Report Size (5)
            0x81.toByte(), 0x03,            //     Input (Cnst,Var,Abs) - padding
            0x05, 0x01,                     //     Usage Page (Generic Desktop)
            0x09, 0x30,                     //     Usage (X)
            0x09, 0x31,                     //     Usage (Y)
            0x16, 0x01, 0x80.toByte(),      //     Logical Minimum (-32767)
            0x26, 0xFF.toByte(), 0x7F,      //     Logical Maximum (32767)
            0x75, 0x10,                     //     Report Size (16)
            0x95.toByte(), 0x02,            //     Report Count (2)
            0x81.toByte(), 0x06,            //     Input (Data,Var,Rel) - RELATIVE
            0x09, 0x38,                     //     Usage (Wheel)
            0x15, 0x81.toByte(),            //     Logical Minimum (-127)
            0x25, 0x7F,                     //     Logical Maximum (127)
            0x75, 0x08,                     //     Report Size (8)
            0x95.toByte(), 0x01,            //     Report Count (1)
            0x81.toByte(), 0x06,            //     Input (Data,Var,Rel)
            0x05, 0x0C,                     //     Usage Page (Consumer)
            0x0A, 0x38, 0x02,               //     Usage (AC Pan)
            0x81.toByte(), 0x06,            //     Input (Data,Var,Rel)
            0xC0.toByte(),                  //   End Collection
            0xC0.toByte(),                  // End Collection
        )
    }
}
