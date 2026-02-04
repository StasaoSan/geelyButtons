package com.stasao.geelybuttons.bleService

import android.util.Log
import com.stasao.geelybuttons.*

class EventRouting(
    private val esp: EspLink,
    private val tag: String = "EventRouting",
    private val fan: FanSpeedController,
    private val hvacPower: HvacPowerController,
    private val rearDefrost: RearDefrostController,
    private val electricDefrost: ElectricDefrostController,
    private val tempDual: TempDualController,
    private val tempMain: TempController,
    private val tempPass: TempController,
    private val airflow: AirflowController,
    private val heatDrv: SeatHeatingController,
    private val heatPsg: SeatHeatingController,
    private val fanDrv: SeatFanController,
    private val fanPsg: SeatFanController,
) {
    fun onBle(raw: String) {
        when (BleProtocol.parse(raw)) {
            BleEvent.FanUp -> fan.inc()
            BleEvent.FanDown -> fan.dec()

            BleEvent.Enc1Click -> hvacPower.toggle()
            BleEvent.Enc1Long -> tempDual.toggle()

            BleEvent.Enc2Click -> rearDefrost.toggle()
            BleEvent.Enc2Long -> electricDefrost.toggle()

            BleEvent.MainTempUp -> tempMain.inc()
            BleEvent.MainTempDown -> tempMain.dec()

            BleEvent.PassTempUp -> tempPass.inc()
            BleEvent.PassTempDown -> tempPass.dec()

            BleEvent.ClimateBody -> airflow.toggleBody()
            BleEvent.ClimateLegs -> airflow.toggleLegs()
            BleEvent.ClimateWindows -> airflow.toggleWindows()

            BleEvent.DrvHeatStep -> heatDrv.step()
            BleEvent.DrvHeatOff -> heatDrv.off()

            BleEvent.DrvFanStep -> fanDrv.step()
            BleEvent.DrvFanOff -> fanDrv.off()

            BleEvent.PassHeatStep -> heatPsg.step()
            BleEvent.PassHeatOff -> heatPsg.off()

            BleEvent.PassFanStep -> fanPsg.step()
            BleEvent.PassFanOff -> fanPsg.off()

            else -> Log.i(tag, "Unhandled BLE event: $raw")
        }
    }

    fun onGibInt(id: Int, area: Int, v: Int) {
        when (id) {
            268698368 -> { // REAR defrost
                rearDefrost.syncFromGib(v)
                esp.send("FB:REAR:$v")
            }
            269027328 -> { // ELECTRIC defrost
                electricDefrost.syncFromGib(v)
                esp.send("FB:ELECTRIC:$v")
            }
            268566784 -> { // FAN SPEED
                fan.syncFromGib(v)
                val level = fanLevelFromResult(v)
                esp.send("FB:FAN:$area:$level")
            }
            268829952 -> { // TEMP_DUAL
                tempDual.syncFromGib(v)
                esp.send("FB:TEMP_DUAL:$v")
            }
            else -> esp.send("GIB:INT:$id:$area:$v")
        }
    }

    fun onGibFloat(id: Int, area: Int, v: Float) {
        when (id) {
            268828928 -> { // TEMP
                if (area == 1) tempMain.syncFromGib(v)
                if (area == 4) tempPass.syncFromGib(v)
                esp.send("FB:TEMP:$area:${v}")
            }
            else -> esp.send("GIB:FLOAT:$id:$area:$v")
        }
    }

    private fun fanLevelFromResult(v: Int): String {
        return when (v) {
            0 -> "OFF"
            268566794 -> "AUTO"
            in 268566785..268566793 -> "L${v - 268566784}" // 1..9
            else -> "UNK($v)"
        }
    }
}