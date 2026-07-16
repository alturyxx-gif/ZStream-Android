package com.zstream.android.data.model

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithAbbreviation

@OptIn(ExperimentalForeignApi::class)
actual fun todayIsoDate(): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
        timeZone = NSTimeZone.timeZoneWithAbbreviation("UTC")!!
    }
    return formatter.stringFromDate(NSDate())
}
