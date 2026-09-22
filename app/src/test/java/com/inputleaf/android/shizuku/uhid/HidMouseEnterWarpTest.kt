package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HidMouseEnterWarpTest {

    @Test
    fun `enter is retained until apply on the injector that owns the mouse`() {
        val warp = HidMouseEnterWarp { 1L }
        val sink = RecordingSink()
        val mouse = HidMouse(sink)

        warp.onEnter(x = 0, y = 100, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        assertThat(warp.pending).isNotNull()
        assertThat(sink.reports).isEmpty()

        val plans = warp.applyIfPending(mouse)
        assertThat(plans.any { !it.isNoOp }).isTrue()
        assertThat(sink.reports).isNotEmpty()
        assertThat(warp.pending).isNull()
        assertThat(sink.reports.any { shortLe(it, 1) == HidMouse.MIN_DELTA }).isTrue()
    }

    @Test
    fun `apply without enter does not emit HID`() {
        val warp = HidMouseEnterWarp { 1L }
        val sink = RecordingSink()
        assertThat(warp.applyIfPending(HidMouse(sink))).isEmpty()
        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `failed HID write keeps enter coords`() {
        val warp = HidMouseEnterWarp { 1L }
        warp.onEnter(x = 1079, y = 0, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        val mouse = HidMouse(object : HidReportSink {
            override fun sendReport(report: ByteArray): Boolean = false
        })

        val plans = warp.applyIfPending(mouse)
        assertThat(plans.any { !it.isNoOp }).isTrue()
        assertThat(warp.pending).isNotNull()
        assertThat(warp.pending!!.x).isEqualTo(1079)
    }

    @Test
    fun `leave drops stored enter so a later attach cannot reuse it`() {
        val warp = HidMouseEnterWarp { 1L }
        val sink = RecordingSink()
        warp.onEnter(x = 0, y = 0, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        warp.onLeave()
        assertThat(warp.applyIfPending(HidMouse(sink))).isEmpty()
        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `later enter replaces coords before attach`() {
        val warp = HidMouseEnterWarp { 1L }
        warp.onEnter(x = 0, y = 0, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        warp.onEnter(x = 1079, y = 2413, maxX = 1079, maxY = 2413, pointerSpeed = 0)
        val sink = RecordingSink()
        warp.applyIfPending(HidMouse(sink))
        assertThat(sink.reports.any { shortLe(it, 1) == HidMouse.MAX_DELTA }).isTrue()
        assertThat(sink.reports.any { shortLe(it, 3) == HidMouse.MAX_DELTA }).isTrue()
    }

    @Test
    fun `new HID mouse origin is display center so interior enter snap is non-zero`() {
        val warp = HidMouseEnterWarp { 1L }
        val sink = RecordingSink()
        warp.onEnter(x = 100, y = 200, maxX = 1079, maxY = 2413, pointerSpeed = 0)

        val plans = warp.applyIfPending(HidMouse(sink))

        assertThat(plans.any { !it.isNoOp }).isTrue()
        assertThat(sink.reports).isNotEmpty()
        val cookedIfSeededAtEnter = MouseEdgeAnchor.planSnap(
            MouseEdgeAnchor.Input(
                targetX = 100,
                targetY = 200,
                cookedX = 100,
                cookedY = 200,
                maxX = 1079,
                maxY = 2413,
                flags = MouseEdgeAnchor.Flags(),
                preferTangentialFirst = true,
                lastWasEdgePulse = false,
                dtMs = 0L,
            ),
            settingsSpeed = 0,
        )
        assertThat(cookedIfSeededAtEnter.all { it.isNoOp }).isTrue()
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
