package com.inputleaf.android.shizuku.uhid

/** Destination for HID input reports, so pointer logic is testable without `/dev/uhid`. */
internal interface HidReportSink {
    /** @return false when the report could not be written. */
    fun sendReport(report: ByteArray): Boolean
}
