package com.inputleaf.android.service

import android.content.ContentResolver
import android.provider.Settings

/** Android pointer speed setting range (Settings → Pointer speed). */
internal const val POINTER_SPEED_MIN = -7
internal const val POINTER_SPEED_MAX = 7

/** [Settings.System.POINTER_SPEED] is @TestApi; use the stable string at compileSdk 34. */
internal const val SETTINGS_POINTER_SPEED_KEY = "pointer_speed"

internal fun clampPointerSpeed(speed: Int): Int = speed.coerceIn(POINTER_SPEED_MIN, POINTER_SPEED_MAX)

internal fun readSystemPointerSpeed(contentResolver: ContentResolver): Int =
    clampPointerSpeed(
        Settings.System.getInt(contentResolver, SETTINGS_POINTER_SPEED_KEY, 0),
    )
