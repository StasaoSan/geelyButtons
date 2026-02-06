package com.stasao.geelybuttons

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.nio.charset.Charset
import java.util.UUID

@SuppressLint("MissingPermission")
class BleListenerService : Service() {

    companion object {
        const val EXTRA_CONNECT_ADDRESS = "connect_address"
        const val EXTRA_AUTO_MODE = "auto_mode"
        const val EXTRA_FORCE_SCAN = "force_scan"
    }

    private val TAG = "BleListenerService"

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var gatt: BluetoothGatt? = null

    // === BLE UUIDs (must match ESP firmware) ===
    private val ESP_SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val ESP_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ESP write-back (Android -> ESP)
    private var espGatt: BluetoothGatt? = null
    private var espTxChar: BluetoothGattCharacteristic? = null

    // === Business logic ===
    private lateinit var gib: GibApi
    private lateinit var fan: FanSpeedController
    private lateinit var hvacPower: HvacPowerController
    private lateinit var rearDefrost: RearDefrostController
    private lateinit var electricDefrost: ElectricDefrostController
    private lateinit var tempDual: TempDualController
    private lateinit var climateZone: ClimateZoneController
    private lateinit var tempMain: TempController
    private lateinit var tempPass: TempController
    private lateinit var airflow: AirflowController

    private lateinit var heatDrv: SeatHeatingController
    private lateinit var heatPsg: SeatHeatingController
    private lateinit var heatRearL: SeatHeatingController
    private lateinit var heatRearR: SeatHeatingController
    private lateinit var fanDrv: SeatFanController
    private lateinit var fanPsg: SeatFanController

    private var gibReceiverRegistered: Boolean = false

    private fun requestInitialStateToEsp() {
        gib.requestIntProperty(268698368, area = 8)  // REAR
        gib.requestIntProperty(269027328, area = 8)  // front

        gib.requestIntProperty(268566784, area = 8) // fanspeed

        gib.requestFloatProperty(268828928, area = 1) // main temp
        gib.requestFloatProperty(268828928, area = 4) // pass temp

        // here add intents to get in init state
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, getNotification("Starting..."))

        // --- Init GIB ---
        gib = GibApi(this)

        // Recive to ESP
        gib.listenIntProperty(268698368, area = 8) // rear window
        gib.listenIntProperty(269027328, area = 8) // front window
        gib.listenIntProperty(268566784, area = 8) // fan speed
        gib.listenFloatProperty(268828928, area = 1) // main temp
        gib.listenFloatProperty(268828928, area = 4) // pass temp

        // Controllers
        fan = FanSpeedController(gib)
        hvacPower = HvacPowerController(gib)
        rearDefrost = RearDefrostController(gib)
        electricDefrost = ElectricDefrostController(gib)
        tempDual = TempDualController(gib)
        climateZone = ClimateZoneController(gib)
        tempMain = TempController(gib, area = 1)
        tempPass = TempController(gib, area = 4)
        airflow = AirflowController(gib)

        heatDrv = SeatHeatingController(gib, area = 1)
        heatPsg = SeatHeatingController(gib, area = 4)
        heatRearL = SeatHeatingController(gib, area = 16)
        heatRearR = SeatHeatingController(gib, area = 64)
        fanDrv = SeatFanController(gib, area = 1)
        fanPsg = SeatFanController(gib, area = 4)

        // Receiver that listens GIB broadcasts -> forward to ESP
        registerGibReceiverIfNeeded()

        // --- Init BLE ---
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is OFF")
            updateNotification("Bluetooth OFF")
            return START_NOT_STICKY
        }

        val forceScan = intent?.getBooleanExtra(EXTRA_FORCE_SCAN, false) ?: false
        val connectAddressFromIntent = intent?.getStringExtra(EXTRA_CONNECT_ADDRESS)

        if (forceScan) {
            startBleScan()
            return START_STICKY
        }

        val connectAddress = connectAddressFromIntent
            ?: getSharedPreferences("prefs", MODE_PRIVATE).getString("saved_mac", null)

        if (connectAddress != null) {
            connectToAddress(connectAddress)
        } else {
            startBleScan()
        }

        return START_STICKY
    }

    private var isConnecting = false

    private fun connectToAddress(address: String) {
        if (isConnecting) return
        isConnecting = true

        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        espGatt = null
        espTxChar = null

        Handler(Looper.getMainLooper()).postDelayed({
            val device = bluetoothAdapter.getRemoteDevice(address)
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            isConnecting = false
        }, 250)
    }

    private fun startBleScan() {
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available")
            updateNotification("BLE scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            Log.i(TAG, "Scan started (2 sec)")
            updateNotification("Scanning BLE (2 sec)...")

            Handler(Looper.getMainLooper()).postDelayed({
                try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
                Log.i(TAG, "Scan stopped")
                updateNotification("Scan finished")
            }, 2000)

        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            updateNotification("Scan failed: ${e.javaClass.simpleName}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "NoName"
            val address = device.address
            val rssi = result.rssi

            Log.i(TAG, "Found: $name | $address | RSSI: $rssi")

            sendBroadcast(Intent(Actions.UI_SCAN_RESULT).apply {
                putExtra("device_name", name)
                putExtra("device_address", address)
                putExtra("rssi", rssi)
            })
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val address = g.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to $address (status=$status)")
                updateNotification("Connected: ${g.device.name ?: address}")

                Handler(Looper.getMainLooper()).postDelayed({
                    val ok = g.discoverServices()
                    Log.i(TAG, "discoverServices(): $ok")
                }, 800)

                // overlay green
                sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
                    putExtra(OverlayService.EXTRA_CONNECTED, true)
                })

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from $address (status=$status)")
                updateNotification("Disconnected (status=$status)")

                try { g.disconnect(); g.close() } catch (_: Exception) {}
                if (gatt === g) gatt = null

                espGatt = null
                espTxChar = null

                // overlay red
                sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
                    putExtra(OverlayService.EXTRA_CONNECTED, false)
                })

                Handler(Looper.getMainLooper()).postDelayed({
                    val saved = getSharedPreferences("prefs", MODE_PRIVATE).getString("saved_mac", null)
                    if (saved != null) connectToAddress(saved) else startBleScan()
                }, 1500)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Services discovery failed: $status")
                return
            }

            val service = g.getService(ESP_SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "ESP service not found: $ESP_SERVICE_UUID")
                return
            }

            val ch = service.getCharacteristic(ESP_CHAR_UUID)
            if (ch == null) {
                Log.e(TAG, "ESP characteristic not found: $ESP_CHAR_UUID")
                return
            }

            // store for write-back
            espGatt = g
            espTxChar = ch

            // use no-response if supported (faster)
            if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

            // subscribe notify
            val notifOk = g.setCharacteristicNotification(ch, true)
            Log.i(TAG, "setCharacteristicNotification: $notifOk")

            val cccd = ch.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                Log.e(TAG, "CCCD not found: $CCCD_UUID")
                return
            }

            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeOk = g.writeDescriptor(cccd)
            Log.i(TAG, "writeDescriptor(CCCD): $writeOk")

            updateNotification("Listening events...")
            Handler(Looper.getMainLooper()).postDelayed({
                requestInitialStateToEsp()
            }, 400)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val raw = characteristic.value?.toString(Charsets.UTF_8)?.trim() ?: return
            Log.i(TAG, "BLE event: $raw")

            when (BleProtocol.parse(raw)) {
                BleEvent.FanUp -> fan.inc()
                BleEvent.FanDown -> fan.dec()

                BleEvent.Enc1Click -> hvacPower.toggle()
                BleEvent.Enc1Long  -> tempDual.toggle()

                BleEvent.Enc2Click -> rearDefrost.toggle()
                BleEvent.Enc2Long  -> electricDefrost.toggle()

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

                else -> Log.i(TAG, "Unhandled BLE event: $raw")
            }

            sendBroadcast(Intent(Actions.UI_LAST_EVENT).apply {
                putExtra("last_event", raw)
            })
        }
    }

    // ===================== GIB -> Android -> ESP =====================

    private fun Any?.asIntOrNull(): Int? = when (this) {
        is Int -> this
        is Long -> this.toInt()
        is Float -> this.toInt()
        is Double -> this.toInt()
        is String -> this.toIntOrNull()
        else -> null
    }

    private fun Any?.asFloatOrNull(): Float? = when (this) {
        is Float -> this
        is Double -> this.toFloat()
        is Int -> this.toFloat()
        is Long -> this.toFloat()
        is String -> this.toFloatOrNull()
        else -> null
    }

    private val gibReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return

            val id = intent.extras?.get("id").asIntOrNull() ?: intent.getIntExtra("id", -1)
            if (id == -1) return

            val zone = when {
                intent.hasExtra("zone") -> intent.extras?.get("zone").asIntOrNull() ?: intent.getIntExtra("zone", 0)
                intent.hasExtra("area") -> intent.extras?.get("area").asIntOrNull() ?: intent.getIntExtra("area", 0)
                else -> 0
            }

            val rawAny =
                intent.extras?.get("result")
                    ?: intent.extras?.get("value")

            val vInt = rawAny.asIntOrNull()
            val vFloat = rawAny.asFloatOrNull()

            when {
                vInt != null -> {
                    Log.i(TAG, "GIB INT: action=$action id=$id zone=$zone v=$vInt extras=${intent.extras?.keySet()}")
                    sendToEspFeedback(id, zone, vInt)
                }
                vFloat != null -> {
                    Log.i(TAG, "GIB FLOAT: action=$action id=$id zone=$zone v=$vFloat extras=${intent.extras?.keySet()}")

                    when (id) {
                        268828928 -> { // IHvac.HVAC_FUNC_TEMP
                            when (zone) {
                                1 -> {
                                    tempMain.syncFromGib(vFloat)
                                    sendToEsp("FB:TEMP:MAIN:${tempMain.current()}")
                                }
                                4 -> {
                                    tempPass.syncFromGib(vFloat)
                                    sendToEsp("FB:TEMP:PASS:${tempPass.current()}")
                                }
                                else -> sendToEsp("GIB:FLOAT:$id:$zone:$vFloat")
                            }
                        }
                        else -> sendToEsp("GIB:FLOAT:$id:$zone:$vFloat")
                    }
                }
                else -> {
                    Log.i(TAG, "GIB unknown: action=$action id=$id zone=$zone extras=${intent.extras}")
                }
            }
        }
    }

    private fun sendToEspFeedback(id: Int, zone: Int, v: Int) {
        when (id) {
            268698368 -> {       // rear defrost
                rearDefrost.syncFromGib(v)
                sendToEsp("FB:REAR:$v")
            }
            269027328 -> {       // electric defrost
                electricDefrost.syncFromGib(v)
                sendToEsp("FB:ELECTRIC:$v")
            }
            268566784 -> {       // fan speed
                val level = fanLevelFromResult(v)
                sendToEsp("FB:FAN:$zone:$level")
                fan.syncFromGib(v)
            }
            else -> sendToEsp("GIB:INT:$id:$zone:$v")
        }
    }

    private fun registerGibReceiverIfNeeded() {
        if (gibReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction("com.salat.gbinder.PROPERTY_INT_CHANGED")
            addAction("com.salat.gbinder.PROPERTY_FLOAT_CHANGED")
            // если у тебя другой action — добавь сюда
        }

        registerReceiver(gibReceiver, filter)
        gibReceiverRegistered = true
        Log.i(TAG, "GIB receiver registered")
    }

    private fun sendToEsp(msg: String) {
        val g = espGatt ?: return
        val ch = espTxChar ?: return

        val canWrite =
            (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

        if (!canWrite) {
            Log.w(TAG, "ESP characteristic not writable, msg=$msg")
            return
        }

        ch.value = msg.toByteArray(Charsets.UTF_8)
        val ok = g.writeCharacteristic(ch)
        Log.i(TAG, "Sent to ESP: ok=$ok msg=$msg")
    }

    // ===================== Notification helpers =====================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "ble_channel",
            "BLE Listener",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun getNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "ble_channel")
            .setContentTitle("GeelyButtons")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(1, getNotification(content))
    }

    override fun onDestroy() {
        try { bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}

        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        espGatt = null
        espTxChar = null

        if (gibReceiverRegistered) {
            try { unregisterReceiver(gibReceiver) } catch (_: Exception) {}
            gibReceiverRegistered = false
        }

        // overlay red
        sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
            putExtra(OverlayService.EXTRA_CONNECTED, false)
        })

        super.onDestroy()
        Log.i(TAG, "Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fanLevelFromResult(v: Int): String {
        return when (v) {
            0 -> "OFF"
            268566794 -> "AUTO"
            in 268566785..268566793 -> "L${v - 268566784}" // 1..9
            else -> "UNK($v)"
        }
    }
}