package com.stasao.geelybuttons

sealed class BleEvent {
    data object EncPlus : BleEvent()
    data object EncMinus : BleEvent()
    data object BtnClick : BleEvent()
    data object BtnLong : BleEvent()
    data class Unknown(val raw: String) : BleEvent()
}

object BleProtocol {
    fun parse(raw: String): BleEvent = when (raw.trim()) {
        "ENC:+1" -> BleEvent.EncPlus
        "ENC:-1" -> BleEvent.EncMinus
        "BTN:CLICK" -> BleEvent.BtnClick
        "BTN:LONG" -> BleEvent.BtnLong
        else -> BleEvent.Unknown(raw)
    }
}