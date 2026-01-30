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
import android.content.Intent
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
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var gatt: BluetoothGatt? = null
    private var lastConnectAddress: String? = null

    // === BLE UUIDs (must match ESP firmware) ===
    private val ESP_SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val ESP_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val TAG = "BleListenerService"

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

    private lateinit var heatDrv: SeatHeatingController
    private lateinit var heatPsg: SeatHeatingController
    private lateinit var heatRearL: SeatHeatingController
    private lateinit var heatRearR: SeatHeatingController
    private lateinit var fanDrv: SeatFanController
    private lateinit var fanPsg: SeatFanController

    // ESP write-back (Android -> ESP)
    private var espGatt: BluetoothGatt? = null
    private var espTxChar: BluetoothGattCharacteristic? = null

    // GIB broadcast receiver
    private var gibReceiverRegistered: Boolean = false

    private lateinit var airflow: AirflowController

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, getNotification("Starting..."))

        gib = GibApi(this)
        gib.listenIntProperty(268698368, area = 8)
        gib.listenIntProperty(269027328, area = 8)
        fan = FanSpeedController(gib)
        hvacPower = HvacPowerController(gib)
        rearDefrost = RearDefrostController(gib)
        electricDefrost = ElectricDefrostController(gib)
        tempDual = TempDualController(gib)
        climateZone = ClimateZoneController(gib)
        tempMain = TempController(gib, area = 1)
        tempPass = TempController(gib, area = 4)

        heatDrv = SeatHeatingController(gib, area = 1)
        heatPsg = SeatHeatingController(gib, area = 4)
        heatRearL = SeatHeatingController(gib, area = 16)
        heatRearR = SeatHeatingController(gib, area = 64)
        fanDrv = SeatFanController(gib, area = 1)
        fanPsg = SeatFanController(gib, area = 4)
        airflow = AirflowController(gib)

        registerGibReceiverIfNeeded()

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

        if (connectAddress != null) connectToAddress(connectAddress) else startBleScan()

        return START_STICKY
    }

    private fun connectToAddress(address: String) {
        Log.i(TAG, "Connect request: $address")
        updateNotification("Connecting: $address")

        // Cleanup old gatt
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null

        val device = bluetoothAdapter.getRemoteDevice(address)

        // True = autoConnect; but it may connect "later". For car headunits it can be flaky.
        // We'll keep it true for now as you had it, but you can try false if needed.
        gatt = device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
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
                try {
                    scanner.stopScan(scanCallback)
                } catch (_: Exception) {}
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

                sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
                    putExtra(OverlayService.EXTRA_CONNECTED, true)
                })
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from $address (status=$status)")
                updateNotification("Disconnected (status=$status)")

                try {
                    g.disconnect()
                    g.close()
                } catch (_: Exception) {}

                sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
                    putExtra(OverlayService.EXTRA_CONNECTED, false)
                })
                if (gatt === g) gatt = null
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Services discovery failed: $status")
                return
            }

            Log.i(TAG, "Services discovered on ${g.device.address}")

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

            // Store references for write-back
            espGatt = g
            espTxChar = ch

            Log.i(TAG, "Subscribing notify: ${ch.uuid}")
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
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val raw = characteristic.value?.toString(Charset.defaultCharset())?.trim() ?: return
            Log.i(TAG, "BLE event: $raw")

            when (BleProtocol.parse(raw)) {
                BleEvent.FanUp -> fan.inc()
                BleEvent.FanDown -> fan.dec()

                // enc1clk -> выключение климата
                BleEvent.Enc1Click -> hvacPower.toggle()

                // enc1long -> dual режим
                BleEvent.Enc1Long -> tempDual.toggle()

                // enc2clk -> rear defrost
                BleEvent.Enc2Click -> rearDefrost.toggle()

                // enc2long -> electric defrost
                BleEvent.Enc2Long -> electricDefrost.toggle()

                // температура водителя
                BleEvent.MainTempUp -> tempMain.inc()
                BleEvent.MainTempDown -> tempMain.dec()

                // температура пассажира
                BleEvent.PassTempUp -> tempPass.inc()
                BleEvent.PassTempDown -> tempPass.dec()

                // багажник (реализовать)
//                BleEvent.Thunk ->

                // переключение потоков воздуха
                BleEvent.ClimateBody -> airflow.toggleBody()
                BleEvent.ClimateLegs -> airflow.toggleLegs()
                BleEvent.ClimateWindows -> airflow.toggleWindows()

                // Подогрев водителя
                BleEvent.DrvHeatStep -> heatDrv.step()
                BleEvent.DrvHeatOff -> heatDrv.off()

                // Обдув водителя
                BleEvent.DrvFanStep -> fanDrv.step()
                BleEvent.DrvFanOff -> fanDrv.off()

                // Подогрев пассажира
                BleEvent.PassHeatStep -> heatPsg.step()
                BleEvent.PassHeatOff -> heatPsg.off()

                // Обдув пассажира
                BleEvent.PassFanStep -> fanPsg.step()
                BleEvent.PassFanOff -> fanPsg.off()


                else -> Log.i(TAG, "Unhandled BLE event: $raw")
            }

            // Update UI
            sendBroadcast(Intent(Actions.UI_LAST_EVENT).apply {
                putExtra("last_event", raw)
            })
        }
    }

    private val gibReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent == null) return
            val action = intent.action ?: return

            // Common extras used by GIB broadcasts
            val id = intent.getIntExtra("id", -1)
            val area = intent.getIntExtra("area", -1)

            // For INT updates
            val intValue = intent.getIntExtra("value", Int.MIN_VALUE)

            // For FLOAT updates
            val floatValue = intent.getFloatExtra("value", Float.NaN)

            if (id == -1) return

            Log.i(TAG, "GIB event: action=$action id=$id area=$area valueInt=$intValue valueFloat=$floatValue")

            // Forward a minimal feedback message to ESP (Android -> ESP)
            // Example formats:
            //  INT:  GIB:INT:<id>:<area>:<value>
            //  FLOAT:GIB:FLOAT:<id>:<area>:<value>
            if (intValue != Int.MIN_VALUE) {
                val a = if (area >= 0) area else 0
                sendToEsp("GIB:INT:$id:$a:$intValue")
            } else if (!floatValue.isNaN()) {
                val a = if (area >= 0) area else 0
                sendToEsp("GIB:FLOAT:$id:$a:$floatValue")
            }
        }
    }

    private fun registerGibReceiverIfNeeded() {
        if (gibReceiverRegistered) return
        val filter = android.content.IntentFilter().apply {
            addAction("com.salat.gbinder.PROPERTY_INT_CHANGED")
            addAction("com.salat.gbinder.PROPERTY_FLOAT_CHANGED")
            addAction("com.salat.gbinder.PROPERTY_VALUE_CHANGED")
        }
        registerReceiver(gibReceiver, filter)
        gibReceiverRegistered = true
    }

    private fun sendToEsp(msg: String) {
        val g = espGatt ?: return
        val ch = espTxChar ?: return
        // Only write if characteristic supports write
        val canWrite = (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
            (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        if (!canWrite) {
            Log.w(TAG, "ESP characteristic is not writable; cannot send: $msg")
            return
        }
        ch.value = msg.toByteArray(Charsets.UTF_8)
        val ok = g.writeCharacteristic(ch)
        Log.i(TAG, "Sent to ESP: ok=$ok msg=$msg")
    }

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
        try {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}

        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}

        gatt = null

        if (gibReceiverRegistered) {
            try { unregisterReceiver(gibReceiver) } catch (_: Exception) {}
            gibReceiverRegistered = false
        }

        // Ensure overlay turns red when service stops
        sendBroadcast(Intent(OverlayService.ACTION_CONN_STATE).apply {
            putExtra(OverlayService.EXTRA_CONNECTED, false)
        })

        super.onDestroy()
        Log.i(TAG, "Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}