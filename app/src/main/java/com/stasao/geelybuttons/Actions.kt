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

class RearDefrostController(private val gib: GibApi) {
    // IHvac.HVAC_FUNC_DEFROST_REAR
    private val ID = 268698368
    private var on = false

    fun toggle() {
        on = !on
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }

    fun setEnabled(enabled: Boolean) {
        on = enabled
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }
}

class ElectricDefrostController(private val gib: GibApi) {
    // IHvac.HVAC_FUNC_ELECTRIC_DEFROST
    private val ID = 269027328
    private var on = false

    fun toggle() {
        on = !on
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }

    fun setEnabled(enabled: Boolean) {
        on = enabled
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }
}

class TempDualController(private val gib: GibApi) {
    // IHvac.HVAC_FUNC_TEMP_DUAL
    private val ID = 268829952
    private var on = false

    fun toggle() {
        on = !on
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }

    fun setEnabled(enabled: Boolean) {
        on = enabled
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }
}

// --- Enum-like value set ------------------------------------------

class ClimateZoneController(private val gib: GibApi) {
    // IHvac.HVAC_FUNC_CLIMATE_ZONE
    private val ID = 268502272

    private val VALUES = intArrayOf(
        268502273, // SINGLE
        268502274, // DUAL
        268502275, // TRIPLE
        268502276  // FOUR
    )

    private var idx = 1 // по умолчанию DUAL (как чаще всего в авто)

    fun next() {
        idx = (idx + 1) % VALUES.size
        gib.setInt(ID, VALUES[idx], area = null)
    }

    fun prev() {
        idx = (idx - 1 + VALUES.size) % VALUES.size
        gib.setInt(ID, VALUES[idx], area = null)
    }

    fun setSingle() { idx = 0; gib.setInt(ID, VALUES[idx], null) }
    fun setDual()   { idx = 1; gib.setInt(ID, VALUES[idx], null) }
    fun setTriple() { idx = 2; gib.setInt(ID, VALUES[idx], null) }
    fun setFour()   { idx = 3; gib.setInt(ID, VALUES[idx], null) }
}

class HvacPowerController(private val gib: GibApi) {
    // IHvac.HVAC_FUNC_POWER
    private val ID = 268501248
    private var on = true

    fun toggle() {
        on = !on
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }

    fun setEnabled(enabled: Boolean) {
        on = enabled
        gib.setInt(ID, if (on) 1 else 0, area = null)
    }
}

class TempController(private val gib: GibApi, private val area: Int) {
    // IHvac.HVAC_FUNC_TEMP
    private val ID = 268828928

    // По скрину текущее значение было "2" — стартуем с него.
    // Потом, если захочешь, научим читать реальное значение через GET_*.
    private var value = 2

    private val min = 0
    private val max = 10

    fun inc() {
        value = (value + 1).coerceAtMost(max)
        gib.setInt(ID, value, area)
    }

    fun dec() {
        value = (value - 1).coerceAtLeast(min)
        gib.setInt(ID, value, area)
    }
}