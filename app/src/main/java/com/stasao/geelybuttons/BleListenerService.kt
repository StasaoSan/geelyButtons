package com.stasao.geelybuttons

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.stasao.geelybuttons.bleService.*

@SuppressLint("MissingPermission")
class BleListenerService : Service() {

    companion object {
        const val EXTRA_CONNECT_ADDRESS = "connect_address"
        const val EXTRA_FORCE_SCAN = "force_scan"
    }

    private val TAG = "BleListenerService"

    private lateinit var notif: NotificationHelper
    private lateinit var adapter: BluetoothAdapter

    private lateinit var gib: GibApi
    private lateinit var router: EventRouting
    private lateinit var esp: EspLink
    private lateinit var ble: BleClient
    private lateinit var gibBridge: GibBridge

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notif = NotificationHelper(this).also { it.createChannel() }
        startForeground(1, notif.build("Starting..."))

        // BLE init
        val bm = getSystemService(BluetoothManager::class.java)
        adapter = bm.adapter
        if (!adapter.isEnabled) {
            notif.update("Bluetooth OFF")
            return START_NOT_STICKY
        }

        // GIB + controllers
        gib = GibApi(this)
        val fan = FanSpeedController(gib)
        val hvacPower = HvacPowerController(gib)
        val rearDefrost = RearDefrostController(gib)
        val electricDefrost = ElectricDefrostController(gib)
        val tempDual = TempDualController(gib)
        val climateZone = ClimateZoneController(gib)
        val tempMain = TempController(gib, area = 1)
        val tempPass = TempController(gib, area = 4)
        val airflow = AirflowController(gib)
        val heatDrv = SeatHeatingController(gib, area = 1)
        val heatPsg = SeatHeatingController(gib, area = 4)
        val fanDrv = SeatFanController(gib, area = 1)
        val fanPsg = SeatFanController(gib, area = 4)

        esp = EspLink()

        router = EventRouting(
            esp = esp,
            fan = fan,
            hvacPower = hvacPower,
            rearDefrost = rearDefrost,
            electricDefrost = electricDefrost,
            tempDual = tempDual,
            tempMain = tempMain,
            tempPass = tempPass,
            airflow = airflow,
            heatDrv = heatDrv,
            heatPsg = heatPsg,
            fanDrv = fanDrv,
            fanPsg = fanPsg
        )

        gibBridge = GibBridge(
            ctx = this,
            gib = gib,
            onInt = { id, area, v -> router.onGibInt(id, area, v) },
            onFloat = { id, area, v -> router.onGibFloat(id, area, v) }
        ).also { it.register() }

        // listen properties
        gibBridge.listenInt(268698368, area = 8) // rear
        gibBridge.listenInt(269027328, area = 8) // front
        gibBridge.listenInt(268566784, area = 8) // fan speed
        gibBridge.listenInt(268829952, area = 8) // TEMP_DUAL

        gibBridge.listenFloat(268828928, area = 1) // main temp
        gibBridge.listenFloat(268828928, area = 4) // pass temp

        ble = BleClient(
            ctx = this,
            adapter = adapter,
            espLink = esp,
            onConnectedChanged = { connected ->
                sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
                    putExtra(OverlayService.EXTRA_CONNECTED, connected)
                })
                notif.update(if (connected) "Connected" else "Disconnected")
                if (connected) gibBridge.requestInitialState()
            },
            onBleTextEvent = { raw ->
                Log.i(TAG, "BLE event: $raw")
                router.onBle(raw)
                sendBroadcast(Intent(Actions.UI_LAST_EVENT).apply {
                    putExtra("last_event", raw)
                })
            },
            onScanResult = { name, address, rssi ->
                sendBroadcast(Intent(Actions.UI_SCAN_RESULT).apply {
                    putExtra("device_name", name)
                    putExtra("device_address", address)
                    putExtra("rssi", rssi)
                })
            }
        )

        val forceScan = intent?.getBooleanExtra(EXTRA_FORCE_SCAN, false) ?: false
        val addr = intent?.getStringExtra(EXTRA_CONNECT_ADDRESS)
            ?: getSharedPreferences("prefs", MODE_PRIVATE).getString("saved_mac", null)

        if (forceScan) ble.startScan()
        else if (addr != null) ble.connect(addr) else ble.startScan()

        return START_STICKY
    }

    override fun onDestroy() {
        try { ble.disconnect() } catch (_: Exception) {}
        try { gibBridge.unregister() } catch (_: Exception) {}
        sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
            putExtra(OverlayService.EXTRA_CONNECTED, false)
        })
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}