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
    fun parse(raw: String): BleEvent = when (raw.trim()) {
        // fan buttons
        "EVT:FAN:+1" -> BleEvent.FanUp
        "EVT:FAN:-1" -> BleEvent.FanDown

        // temps from encoders rotation
        "EVT:TEMP_MAIN:+1" -> BleEvent.Enc1Up
        "EVT:TEMP_MAIN:-1" -> BleEvent.Enc1Down
        "EVT:TEMP_PASS:+1" -> BleEvent.Enc2Up
        "EVT:TEMP_PASS:-1" -> BleEvent.Enc2Down

        // enc1 buttons
        "EVT:ENC1CLK" -> BleEvent.Enc1Click         // click - climate on/off
        "EVT:ENC1LONG" -> BleEvent.Enc1Long         // long - dual on/off
        // enc2 buttons
        "EVT:REAR_DEFROST" -> BleEvent.Enc2Click    // click - defrost rear
        "EVT:ELECTRIC_DEFROST" -> BleEvent.Enc2Long // long - defrost front

        else -> BleEvent.Unknown(raw)
    }
}