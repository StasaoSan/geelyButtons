package com.stasao.geelybuttons

sealed class BleEvent {
    data object FanUp : BleEvent()
    data object FanDown : BleEvent()

    data object MainTempUp : BleEvent()
    data object MainTempDown : BleEvent()
    data object Enc1Click : BleEvent()
    data object Enc1Long : BleEvent()

    data object PassTempUp : BleEvent()
    data object PassTempDown : BleEvent()
    data object Enc2Click : BleEvent()
    data object Enc2Long : BleEvent()
    data object ClimateBody : BleEvent()
    data object ClimateLegs : BleEvent()
    data object ClimateWindows : BleEvent()
    data object Thunk : BleEvent() // not released in actions
    data object DrvHeatStep : BleEvent()
    data object DrvFanStep : BleEvent()
    data object WheelHeatStep : BleEvent() // not released in actions
    data object PassHeatStep : BleEvent()
    data object PassFanStep : BleEvent()

    data object DrvHeatOff : BleEvent()
    data object DrvFanOff : BleEvent()
    data object WheelHeatOff : BleEvent()
    data object PassHeatOff : BleEvent()
    data object PassFanOff : BleEvent()
    data class Unknown(val raw: String) : BleEvent()
}


object BleProtocol {
    fun parse(raw: String): BleEvent = when (raw.trim()) {
        // fan buttons
        "EVT:FAN:+1" -> BleEvent.FanUp
        "EVT:FAN:-1" -> BleEvent.FanDown

        // temps from encoders rotation
        "EVT:TEMP_MAIN:+1" -> BleEvent.MainTempUp     // enc 1 up
        "EVT:TEMP_MAIN:-1" -> BleEvent.MainTempDown   // enc 1 down
        "EVT:TEMP_PASS:+1" -> BleEvent.PassTempUp     // enc 2 up
        "EVT:TEMP_PASS:-1" -> BleEvent.PassTempDown   // enc 2 down

        // enc1 buttons
        "EVT:CLIMATE_SW" -> BleEvent.Enc1Click         // click - climate on/off
        "EVT:DUAL_SW" -> BleEvent.Enc1Long         // long - dual on/off
        // enc2 buttons
        "EVT:REAR_DEFROST" -> BleEvent.Enc2Click    // click - defrost rear
        "EVT:ELECTRIC_DEFROST" -> BleEvent.Enc2Long // long - defrost front

        "EVT:CLIMATE_BODY" -> BleEvent.ClimateBody
        "EVT:CLIMATE_LEGS" -> BleEvent.ClimateLegs
        "EVT:CLIMATE_WINDOWS" -> BleEvent.ClimateWindows
        "EVT:THUNK" -> BleEvent.Thunk
        "EVT:DRIVER_HEAT" -> BleEvent.DrvHeatStep
        "EVT:DRIVER_FAN" -> BleEvent.DrvFanStep
        "EVT:WHEEL_HEAT" -> BleEvent.WheelHeatStep
        "EVT:PASS_HEAT" -> BleEvent.PassHeatStep
        "EVT:PASS_FAN" -> BleEvent.PassFanStep
        "EVT:DRIVER_HEAT_OFF" -> BleEvent.DrvHeatOff
        "EVT:DRIVER_FAN_OFF" -> BleEvent.DrvFanOff
        "EVT:WHEEL_HEAT_OFF" -> BleEvent.WheelHeatOff
        "EVT:PASS_HEAT_OFF" -> BleEvent.PassHeatOff
        "EVT:PASS_FAN_OFF" -> BleEvent.PassFanOff

        else -> BleEvent.Unknown(raw)
    }
}