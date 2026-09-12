package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

private class RecordingSink : HidReportSink {
    val reports = mutableListOf<ByteArray>()
    override fun sendReport(report: ByteArray) {
        reports += report.copyOf()
    }
}

private fun signed(report: ByteArray, offset: Int): Int {
    val raw = (report[offset].toInt() and 0xFF) or ((report[offset + 1].toInt() and 0xFF) shl 8)
    return if (raw >= 0x8000) raw - 0x10000 else raw
}

private fun deltaOf(report: ByteArray): Pair<Int, Int> = signed(report, 1) to signed(report, 3)

private fun totalCounts(reports: List<ByteArray>): Pair<Int, Int> =
    reports.fold(0 to 0) { acc, r ->
        val d = deltaOf(r)
        (acc.first + d.first) to (acc.second + d.second)
    }

/** Drives the pacer far enough that any rate-limited move completes. */
private fun settle(pointer: RelativePointer, ticks: Int = 400, advance: () -> Unit) {
    repeat(ticks) {
        advance()
        pointer.drain()
        if (pointer.isSettled()) return
    }
}

class RelativePointerDescriptorTest {

    @Test
    fun `descriptor declares X and Y as 16-bit relative`() {
        val descriptor = RelativePointer.DESCRIPTOR
        // Logical Minimum (-32767) = 16 01 80, Report Size 16 (75 10), Input(Data,Var,Rel) = 81 06.
        // An absolute pair would end 81 02 -- and Android ignores absolute pointing devices.
        val min = descriptor.toList().windowed(3)
            .indexOfFirst { it == listOf(0x16.toByte(), 0x01.toByte(), 0x80.toByte()) }
        assertThat(min).isAtLeast(0)

        val tail = descriptor.copyOfRange(min, descriptor.size).toList()
        assertThat(tail.windowed(2).indexOfFirst { it == listOf(0x75.toByte(), 0x10.toByte()) })
            .isAtLeast(0)
        assertThat(tail.windowed(2).indexOfFirst { it == listOf(0x81.toByte(), 0x06.toByte()) })
            .isAtLeast(0)
    }

    @Test
    fun `descriptor is a Generic Desktop Mouse application collection`() {
        assertThat(RelativePointer.DESCRIPTOR.take(6).toList()).containsExactly(
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x02.toByte(),
            0xA1.toByte(), 0x01.toByte(),
        ).inOrder()
    }

    @Test
    fun `a resting pointer uses the segment zero constant`() {
        // Measured on device: isolated reports of 200/400/500 counts all gave 2.042.
        assertThat(AccelerationCurve().gainFor(0.0)).isWithin(0.001).of(2.0416)
    }
}

class RelativePointerMotionTest {

    @Test
    fun `the first move slams to the origin before positioning`() {
        val sink = RecordingSink()
        var now = 0L

        RelativePointer(sink, clock = { now }).moveTo(300, 200, 1920, 1080)

        assertThat(deltaOf(sink.reports.first())).isEqualTo(-32767 to -32767)
    }

    @Test
    fun `counts sent equal the requested pixels divided by the gain`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })

        pointer.moveTo(0, 0, 1080, 2414)          // slam, now at origin
        sink.reports.clear()
        pointer.moveTo(500, 0, 1080, 2414)
        settle(pointer) { now += 8_000_000L }

        val (counts, _) = totalCounts(sink.reports)
        // Emitted after a quiet gap, so the resting gain applies: 500px / 2.0416.
        assertThat(counts).isWithin(3).of(245)
    }

    @Test
    fun `repeated small moves accumulate exactly, with no rounding loss`() {
        // A 1px move is 0.49 counts. Without a carried remainder these would all round
        // to zero and a slow drag would never move at all.
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 0, 1080, 2414)
        sink.reports.clear()

        var x = 0
        repeat(200) {
            x += 1
            now += 8_000_000L
            pointer.moveTo(x, 0, 1080, 2414)
        }
        settle(pointer) { now += 8_000_000L }

        val (counts, _) = totalCounts(sink.reports)
        // Slow steady movement stays in segment 0: 200px / 2.0416.
        assertThat(counts).isWithin(4).of(98)
    }

    @Test
    fun `steady slow movement stays in the constant-gain segment`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 0, 1080, 2414)
        // Let entry finish first; its catch-up report is deliberately larger.
        now += 60_000_000L
        pointer.drain()
        sink.reports.clear()

        // Small steady movement: every report goes out promptly at the 8ms cadence.
        var x = 0
        repeat(30) {
            x += 4
            now += 8_000_000L
            pointer.moveTo(x, 0, 1080, 2414)
        }

        // 4px every 8ms is 500 px/s, well inside segment 0, so each report is ~2 counts.
        assertThat(sink.reports).isNotEmpty()
        sink.reports.forEach {
            val (dx, dy) = deltaOf(it)
            assertThat(kotlin.math.hypot(dx.toDouble(), dy.toDouble())).isAtMost(4.0)
        }
    }

    @Test
    fun `entry is positioned after the slam settles, then movement flows freely`() {
        // Earlier builds held a large move for ~50ms so it could leave as an isolated
        // report. That kept position exact but dropped updates to ~20/s, which looked
        // steppy while dragging. It now leaves immediately, scaled for the curve.
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 0, 1080, 2414)
        sink.reports.clear()

        pointer.moveTo(1000, 0, 1080, 2414)

        // Immediately after the slam Android's speed estimate is saturated, so entry
        // waits for it to be forgotten and then places the cursor at the resting gain.
        now += 8_000_000L
        pointer.drain()
        assertThat(sink.reports).isEmpty()

        now += 50_000_000L
        pointer.drain()
        assertThat(sink.reports).hasSize(1)
        assertThat(deltaOf(sink.reports.single()).first).isWithin(3).of(490)  // 1000 / 2.0416
    }

    @Test
    fun `a fast target is followed at full speed, not throttled`() {
        // The regression this replaces: capping magnitude limited the cursor to ~1430 px/s,
        // so the server's pointer left the screen while this one crawled.
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 0, 1080, 2414)
        sink.reports.clear()

        // Hand moving ~6000 px/s: 60px every 10ms.
        var x = 0
        repeat(15) {
            x += 60
            now += 10_000_000L
            pointer.moveTo(x, 0, 1080, 2414)
            pointer.drain()
        }
        settle(pointer) { now += 10_000_000L }

        assertThat(pointer.placedPosition().first).isWithin(2).of(900)
    }

    @Test
    fun `a large move eventually lands exactly on target`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 0, 1080, 2414)
        sink.reports.clear()

        pointer.moveTo(1000, 2000, 1080, 2414)
        settle(pointer) { now += 8_000_000L }

        val (x, y) = pointer.placedPosition()
        assertThat(abs(x - 1000)).isAtMost(1)
        assertThat(abs(y - 2000)).isAtMost(1)
    }

    @Test
    fun `negative moves are encoded correctly`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 0, 1920, 1080)
        pointer.moveTo(900, 800, 1920, 1080)
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        pointer.moveTo(100, 50, 1920, 1080)
        settle(pointer) { now += 8_000_000L }

        val (dx, dy) = totalCounts(sink.reports)
        assertThat(dx).isLessThan(0)
        assertThat(dy).isLessThan(0)
        assertThat(dx).isLessThan(-80)    // ~-800px, counts depend on the speed reached
    }

    @Test
    fun `moveTo clamps to the screen`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 0, 1920, 1080)
        pointer.moveTo(99_999, -50, 1920, 1080)
        settle(pointer) { now += 8_000_000L }

        assertThat(pointer.placedPosition().first).isWithin(1).of(1920)
        assertThat(pointer.placedPosition().second).isWithin(1).of(0)
    }

    @Test
    fun `resync slams again on the next move`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(300, 300, 1080, 2414)
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        pointer.resync()
        pointer.moveTo(100, 100, 1080, 2414)

        assertThat(deltaOf(sink.reports.first())).isEqualTo(-32767 to -32767)
    }

    @Test
    fun `drain does nothing before the first move`() {
        val sink = RecordingSink()

        RelativePointer(sink, clock = { 0L }).drain()

        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `drain goes quiet once the target is reached`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(200, 0, 1080, 2414)
        settle(pointer) { now += 8_000_000L }
        val settledCount = sink.reports.size

        repeat(20) { now += 8_000_000L; pointer.drain() }

        assertThat(sink.reports.size).isEqualTo(settledCount)
    }
}

class RelativePointerButtonTest {

    @Test
    fun `buttons accumulate and release without moving the pointer`() {
        val sink = RecordingSink()
        val pointer = RelativePointer(sink, clock = { 0L })
        pointer.moveTo(10, 10, 1920, 1080)
        sink.reports.clear()

        pointer.button(1, pressed = true)
        pointer.button(3, pressed = true)
        pointer.button(1, pressed = false)

        assertThat(sink.reports.map { it[0].toInt() }).containsExactly(0x01, 0x03, 0x02).inOrder()
        assertThat(sink.reports.all { deltaOf(it) == 0 to 0 }).isTrue()
    }

    @Test
    fun `unknown button ids are ignored`() {
        val sink = RecordingSink()

        RelativePointer(sink, clock = { 0L }).button(9, pressed = true)

        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `wheel converts protocol units to notches`() {
        val sink = RecordingSink()

        RelativePointer(sink, clock = { 0L }).wheel(horizontal = -120, vertical = 240)

        val report = sink.reports.single()
        assertThat(report[5].toInt()).isEqualTo(2)   // vertical wheel
        assertThat(report[6].toInt()).isEqualTo(-1)  // AC Pan
    }

    @Test
    fun `wheel writes nothing for a sub-notch delta`() {
        val sink = RecordingSink()

        RelativePointer(sink, clock = { 0L }).wheel(horizontal = 0, vertical = 60)

        assertThat(sink.reports).isEmpty()
    }
}

class PointerOrderingTest {

    @Test
    fun `a click waits for held movement so both land at the same place`() {
        // The bug this covers: movement can be held ~50ms waiting for its quiet gap while
        // a click goes out immediately, so the click lands at the OLD position. Two such
        // clicks end up too far apart for Android to pair them as a double-click, and a
        // drag begins from the wrong point.
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now += 4_000_000L; now })
        pointer.moveTo(0, 0, 1080, 2414)
        settle(pointer) { }
        sink.reports.clear()

        pointer.moveTo(900, 0, 1080, 2414)   // big jump: held for the isolation window
        pointer.button(1, pressed = true)

        // Movement was flushed before the button report went out.
        assertThat(pointer.isSettled()).isTrue()
        val buttonReport = sink.reports.last()
        assertThat(buttonReport[0].toInt()).isEqualTo(0x01)
        assertThat(deltaOf(buttonReport)).isEqualTo(0 to 0)
        val motion = sink.reports.dropLast(1)
        assertThat(totalCounts(motion).first).isGreaterThan(0)
    }

    @Test
    fun `a click with nothing pending is emitted without delay`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(10, 10, 1080, 2414)
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        pointer.button(1, pressed = true)

        assertThat(sink.reports).hasSize(1)
        assertThat(sink.reports.single()[0].toInt()).isEqualTo(0x01)
    }

    @Test
    fun `scroll also waits for held movement`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now += 4_000_000L; now })
        pointer.moveTo(0, 0, 1080, 2414)
        settle(pointer) { }
        sink.reports.clear()

        pointer.moveTo(800, 0, 1080, 2414)
        pointer.wheel(horizontal = 0, vertical = 240)

        assertThat(pointer.isSettled()).isTrue()
        assertThat(sink.reports.last()[5].toInt()).isEqualTo(2)   // the wheel report is last
    }

    @Test
    fun `a drag keeps the button held across the whole movement`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(100, 100, 1080, 2414)
        settle(pointer) { now += 8_000_000L }
        pointer.button(1, pressed = true)
        sink.reports.clear()

        var y = 100
        repeat(20) {
            y += 8
            now += 8_000_000L
            pointer.moveTo(100, y, 1080, 2414)
        }
        settle(pointer) { now += 8_000_000L }

        // Every report during the drag carries the held button.
        assertThat(sink.reports).isNotEmpty()
        assertThat(sink.reports.all { it[0].toInt() == 0x01 }).isTrue()
        pointer.button(1, pressed = false)
        assertThat(sink.reports.last()[0].toInt()).isEqualTo(0x00)
    }
}

class EdgePinningTest {

    @Test
    fun `a target hard against an edge is pinned exactly`() {
        // Android clamps an oversized delta to the screen edge, so this lands exactly on
        // it. A computed delta cannot, and being a few px off made the phone and the
        // server disagree about when the pointer had left -- the gap on the way back.
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(500, 500, 1080, 2414)
        now += 60_000_000L
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        pointer.moveTo(0, 500, 1080, 2414)      // hard against the left edge
        now += 8_000_000L
        pointer.drain()

        assertThat(deltaOf(sink.reports.first()).first).isEqualTo(-32767)
        assertThat(pointer.placedPosition().first).isEqualTo(0)
    }

    @Test
    fun `the far edge pins in the positive direction`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(500, 500, 1080, 2414)
        now += 60_000_000L
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        pointer.moveTo(1080, 500, 1080, 2414)
        now += 8_000_000L
        pointer.drain()

        assertThat(deltaOf(sink.reports.first()).first).isEqualTo(32767)
        assertThat(pointer.placedPosition().first).isEqualTo(1080)
    }

    @Test
    fun `pinning one axis leaves the other untouched in that report`() {
        // A huge delta on both axes at once would distort the speed estimate for both.
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(500, 500, 1080, 2414)
        now += 60_000_000L
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        pointer.moveTo(0, 0, 1080, 2414)        // both axes against an edge; moveTo drains once

        // First report pins X only.
        assertThat(deltaOf(sink.reports.first())).isEqualTo(-32767 to 0)

        // The next pins Y, so neither report carries an oversized delta on both axes.
        now += 8_000_000L
        pointer.drain()
        assertThat(deltaOf(sink.reports.last())).isEqualTo(0 to -32767)
        assertThat(sink.reports).hasSize(2)
        assertThat(pointer.placedPosition()).isEqualTo(0 to 0)
    }

    @Test
    fun `an interior target is never pinned`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(500, 500, 1080, 2414)
        now += 60_000_000L
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        pointer.moveTo(540, 600, 1080, 2414)
        settle(pointer) { now += 8_000_000L }

        assertThat(sink.reports.none { kotlin.math.abs(deltaOf(it).first) == 32767 }).isTrue()
        assertThat(sink.reports.none { kotlin.math.abs(deltaOf(it).second) == 32767 }).isTrue()
    }

    @Test
    fun `sitting on the edge does not re-pin every tick`() {
        val sink = RecordingSink()
        var now = 0L
        val pointer = RelativePointer(sink, clock = { now })
        pointer.moveTo(0, 500, 1080, 2414)
        now += 60_000_000L
        settle(pointer) { now += 8_000_000L }
        sink.reports.clear()

        repeat(10) { now += 8_000_000L; pointer.moveTo(0, 500, 1080, 2414) }

        assertThat(sink.reports).isEmpty()
    }
}
