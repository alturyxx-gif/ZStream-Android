package com.zstream.android.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun todayIsoDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
