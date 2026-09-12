package com.inputleaf.android.shizuku.uhid

/** Destination for HID input reports, so pointer logic is testable without `/dev/uhid`. */
internal interface HidReportSink {
    fun sendReport(report: ByteArray)
}
