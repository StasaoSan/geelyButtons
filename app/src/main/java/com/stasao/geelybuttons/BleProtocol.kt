package com.stasao.geelybuttons

sealed class BleEvent {
    data object FanUp : BleEvent()
    data object FanDown : BleEvent()
    data object Enc1Up : BleEvent()
    data object Enc1Down : BleEvent()
    data object Enc1Click : BleEvent()
    data object Enc1Long : BleEvent()
    data object Enc2Up : BleEvent()
    data object Enc2Down : BleEvent()
    data object Enc2Click : BleEvent()
    data object Enc2Long : BleEvent()
    data class Unknown(val raw: String) : BleEvent()
}

object BleProtocol {
    fun parse(raw: String): BleEvent = when (raw.trim()) { // добавить событий когда будет понятно каких
        "EVT:FAN:+1" -> BleEvent.FanUp
        "EVT:FAN:-1" -> BleEvent.FanDown
        "EVT:BTN:CLICK" -> BleEvent.Enc1Click
        "EVT:BTN:LONG" -> BleEvent.Enc1Long
        else -> BleEvent.Unknown(raw)
    }
}