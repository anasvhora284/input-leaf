package com.inputleaf.android.inject

import com.inputleaf.android.model.InputLeapEvent

interface InputInjector {
    suspend fun connect(): Boolean
    fun send(event: InputLeapEvent)
    fun disconnect()
    fun isAvailable(): Boolean
    val name: String
}
