package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HidMouseEnterWarpTest {

    private val sleeps = mutableListOf<Long>()
    private fun warp() = HidMouseEnterWarp { sleeps += it }

    @Test
    fun `enter is retained until apply on the injector that owns the mouse`() {
        val warp = warp()
        val sink = RecordingSink()
        warp.onEnter(x = 0, y = 100, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        assertThat(warp.pending).isNotNull()
        assertThat(sink.reports).isEmpty()

        val result = warp.applyIfPending(HidMouse(sink))
        assertThat(result.complete).isTrue()
        assertThat(warp.pending).isNull()
        assertThat(sink.reports.any { shortLe(it, 1) == HidMouse.MIN_DELTA }).isTrue()
    }

    @Test
    fun `apply without enter does not emit HID`() {
        val sink = RecordingSink()
        assertThat(warp().applyIfPending(HidMouse(sink)).written).isEqualTo(0)
        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `warp pulses into the nearest corner, pauses, then moves off it once (88)`() {
        val warp = warp()
        val sink = RecordingSink()
        warp.onEnter(x = 310, y = 0, maxX = 1239, maxY = 2771, pointerSpeed = 0)

        warp.applyIfPending(HidMouse(sink))

        assertThat(sink.reports).hasSize(2)
        assertThat(shortLe(sink.reports[0], 1)).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(shortLe(sink.reports[0], 3)).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(sleeps).containsExactly(HidMouseEnterWarp.SETTLE_MS)
        assertThat(HidMouseEnterWarp.SETTLE_MS).isGreaterThan(100L)
        assertThat(shortLe(sink.reports[1], 1)).isGreaterThan(0)
        assertThat(shortLe(sink.reports[1], 3)).isEqualTo(0)
    }

    @Test
    fun `a corner enter is a single pulse`() {
        val warp = warp()
        val sink = RecordingSink()
        warp.onEnter(x = 1079, y = 2413, maxX = 1079, maxY = 2413, pointerSpeed = 0)

        warp.applyIfPending(HidMouse(sink))

        assertThat(sink.reports).hasSize(1)
        assertThat(shortLe(sink.reports[0], 1)).isEqualTo(HidMouse.MAX_DELTA)
        assertThat(shortLe(sink.reports[0], 3)).isEqualTo(HidMouse.MAX_DELTA)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `the warp lands on the enter wherever the pointer started`() {
        val maxX = 1239
        val maxY = 2771
        for (speed in listOf(-1, 0, 3)) {
            val gain = MousePointerCompensation.firstSegmentGain(speed)
            for ((tx, ty) in listOf(310 to 0, 1239 to 900, 600 to 2771, 0 to 0, 500 to 1400)) {
                val plan = HidMouseEnterWarp.plan(HidMouseEnterWarp.Pending(tx, ty, maxX, maxY, speed))
                for ((sx, sy) in listOf(0 to 0, 619 to 1385, 1239 to 2771, 310 to 0)) {
                    // Android: the pulse saturates at the display edge; a lone report after
                    // the pause moves at the base gain.
                    var x = sx.toDouble()
                    var y = sy.toDouble()
                    for ((index, report) in plan.withIndex()) {
                        val g = if (index == 0) 1.0 else gain
                        x = (x + report.first * g).coerceIn(0.0, maxX.toDouble())
                        y = (y + report.second * g).coerceIn(0.0, maxY.toDouble())
                    }
                    // One HID count moves `gain` pixels, so rounding leaves up to half of one.
                    assertThat(x).isWithin(gain / 2 + 0.01).of(tx.toDouble())
                    assertThat(y).isWithin(gain / 2 + 0.01).of(ty.toDouble())
                }
            }
        }
    }

    @Test
    fun `failed HID write reports an incomplete warp and clears pending to prevent double snap`() {
        val warp = warp()
        warp.onEnter(x = 1079, y = 0, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        val mouse = HidMouse(object : HidReportSink {
            override fun sendReport(report: ByteArray): Boolean = false
        })

        val result = warp.applyIfPending(mouse)
        assertThat(result.complete).isFalse()
        assertThat(result.written).isEqualTo(0)
        assertThat(warp.pending).isNull()
    }

    @Test
    fun `partial HID write is not reported as a complete warp`() {
        val warp = warp()
        warp.onEnter(x = 500, y = 0, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        var callCount = 0
        val mouse = HidMouse(object : HidReportSink {
            override fun sendReport(report: ByteArray): Boolean {
                callCount++
                return callCount == 1
            }
        })

        val result = warp.applyIfPending(mouse)
        assertThat(result.written).isEqualTo(1)
        assertThat(result.complete).isFalse()
        assertThat(warp.pending).isNull()
    }

    @Test
    fun `leave drops stored enter so a later attach cannot reuse it`() {
        val warp = warp()
        val sink = RecordingSink()
        warp.onEnter(x = 0, y = 0, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        warp.onLeave()
        assertThat(warp.applyIfPending(HidMouse(sink)).written).isEqualTo(0)
        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `later enter replaces coords before attach`() {
        val warp = warp()
        warp.onEnter(x = 0, y = 0, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        warp.onEnter(x = 1079, y = 2413, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        val sink = RecordingSink()
        warp.applyIfPending(HidMouse(sink))
        assertThat(sink.reports.any { shortLe(it, 1) == HidMouse.MAX_DELTA }).isTrue()
        assertThat(sink.reports.any { shortLe(it, 3) == HidMouse.MAX_DELTA }).isTrue()
    }

    private fun shortLe(report: ByteArray, offset: Int): Int {
        val lo = report[offset].toInt() and 0xFF
        val hi = report[offset + 1].toInt()
        return (hi shl 8) or lo
    }

    private class RecordingSink : HidReportSink {
        val reports = mutableListOf<ByteArray>()
        override fun sendReport(report: ByteArray): Boolean {
            reports += report.copyOf()
            return true
        }
    }
}
