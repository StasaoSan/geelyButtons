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

    fun syncFromGib(value: Int) {
        val i = VALUES.indexOf(value)
        if (i >= 0) idx = i
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

    fun syncFromGib(value: Int) {
        on = (value == 1)
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

    fun syncFromGib(value: Int) {
        on = (value == 1)
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

    fun syncFromGib(value: Int) {
        on = (value == 1)
    }
}

// --- Enum-like value set ------------------------------------------
class ClimateZoneController(private val gib: GibApi) {
    // IHvac.HVAC_FUNC_CLIMATE_ZONE
    private val ID = 268502272

    companion object {
        const val SINGLE = 268502273
        const val DUAL   = 268502274
    }

    private val VALUES = intArrayOf(SINGLE, DUAL)

    private var idx: Int = -1              // неизвестно до синка
    private var lastValue: Int? = null     // реальное значение из GIB

    fun next() {
        ensureKnownOrDefault()
        idx = (idx + 1) % VALUES.size
        gib.setInt(ID, VALUES[idx], area = null)
    }

    fun prev() {
        ensureKnownOrDefault()
        idx = (idx - 1 + VALUES.size) % VALUES.size
        gib.setInt(ID, VALUES[idx], area = null)
    }

    fun setSingle() { setValue(SINGLE) }
    fun setDual()   { setValue(DUAL) }

    fun toggleDual() {
        val current = lastValue
        if (current == DUAL) setSingle() else setDual()
    }

    fun syncFromGib(v: Int) {
        lastValue = v
        idx = VALUES.indexOf(v)
    }

    fun currentLabel(): String = when (lastValue) {
        SINGLE -> "SINGLE"
        DUAL -> "DUAL"
        null -> "?"
        else -> "UNK($lastValue)"
    }

    private fun setValue(v: Int) {
        lastValue = v
        idx = VALUES.indexOf(v)
        gib.setInt(ID, v, area = null)
    }

    private fun ensureKnownOrDefault() {
        if (idx >= 0) return
        idx = 0
        lastValue = VALUES[idx]
    }
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
    private var value: Float? = null

    private val step = 0.5f
    private val min = 16.0f
    private val max = 30.0f

    fun inc() {
        val v = (value ?: return) // пока не синкнулись — не делаем прыжков
        val next = quantize((v + step).coerceAtMost(max))
        value = next
        gib.setFloat(ID, next, area)
    }

    fun dec() {
        val v = (value ?: return)
        val next = quantize((v - step).coerceAtLeast(min))
        value = next
        gib.setFloat(ID, next, area)
    }

    fun set(v: Float) {
        val next = quantize(v.coerceIn(min, max))
        value = next
        gib.setFloat(ID, next, area)
    }

    fun syncFromGib(v: Float) {
        value = quantize(v.coerceIn(min, max))
    }

    fun current(): Float? = value

    private fun quantize(v: Float): Float {
        val k = (v / step).toInt()
        val snapped = k * step

        return kotlin.math.round(v / step) * step
    }
}

class SeatHeatingController(private val gib: GibApi, private val area: Int) {
    private val ID = 268763648

    private val VALUES = intArrayOf(
        0,          // OFF
        268763649,  // L1
        268763650,  // L2
        268763651,  // L3
    )

    private var idx = 0

    fun step() {
        idx = (idx + 1) % VALUES.size
        gib.setInt(ID, VALUES[idx], area)
    }

    fun off() {
        idx = 0
        gib.setInt(ID, 0, area)
    }
}

class SeatFanController(private val gib: GibApi, private val area: Int) {
    // IHvac.HVAC_FUNC_AUTO_SEAT_VENTILATION_TIME
    private val ID = 268764160

    private val VALUES = intArrayOf(
        0,          // OFF
        268764161,  // 1
        268764162,  // 2
        268764163,  // 3
    )

    private var idx = 0

    fun step() {
        idx = (idx + 1) % VALUES.size
        gib.setInt(ID, VALUES[idx], area)
    }

    fun off() {
        idx = 0
        gib.setInt(ID, 0, area)
    }
}

class AirflowController(private val gib: GibApi) {
    private val ID = 268894464
    private val AREA = 8

    private var bodyOn = false
    private var legsOn = false
    private var winOn  = false

    private val BODY_ON = 268894471
    private val BODY_OFF = 268894466

    private val LEGS_ON = 268894470
    private val LEGS_OFF = 268894465

    private val WIN_ON = 268894470
    private val WIN_OFF = 268894466

    fun toggleBody() {
        bodyOn = !bodyOn
        gib.setInt(ID, if (bodyOn) BODY_ON else BODY_OFF, AREA)
    }

    fun toggleLegs() {
        legsOn = !legsOn
        gib.setInt(ID, if (legsOn) LEGS_ON else LEGS_OFF, AREA)
    }

    fun toggleWindows() {
        winOn = !winOn
        gib.setInt(ID, if (winOn) WIN_ON else WIN_OFF, AREA)
    }
}