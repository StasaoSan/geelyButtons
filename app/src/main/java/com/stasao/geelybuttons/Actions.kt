package com.stasao.geelybuttons

object Actions {
    const val UI_LAST_EVENT = "com.stasao.geelybuttons.BLE_EVENT"
    const val UI_SCAN_RESULT = "com.stasao.geelybuttons.BLE_SCAN_RESULT"
}

class FanSpeedController(private val gib: GibApi) {
    private val FAN_SPEED_ID = 268566784
    private val FAN_SPEED_AREA = 8
    private val VALUES = intArrayOf(
        0,
        268566785, 268566786, 268566787, 268566788, 268566789,
        268566790, 268566791, 268566792, 268566793,
        268566794
    )
    private var idx = 0

    fun inc() {
        idx = (idx + 1).coerceAtMost(VALUES.lastIndex)
        gib.setInt(FAN_SPEED_ID, VALUES[idx], FAN_SPEED_AREA)
    }

    fun dec() {
        idx = (idx - 1).coerceAtLeast(0)
        gib.setInt(FAN_SPEED_ID, VALUES[idx], FAN_SPEED_AREA)
    }
}