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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, getNotification("Starting..."))

        gib = GibApi(this)
        fan = FanSpeedController(gib)
        hvacPower = HvacPowerController(gib)
        rearDefrost = RearDefrostController(gib)
        electricDefrost = ElectricDefrostController(gib)
        tempDual = TempDualController(gib)
        climateZone = ClimateZoneController(gib)
        tempMain = TempController(gib, area = 1)
        tempPass = TempController(gib, area = 4)

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is OFF")
            updateNotification("Bluetooth OFF")
            return START_NOT_STICKY
        }

        val connectAddress = intent?.getStringExtra(EXTRA_CONNECT_ADDRESS)
            ?: getSharedPreferences("prefs", MODE_PRIVATE).getString("saved_mac", null)

        if (connectAddress != null) {
            connectToAddress(connectAddress)
        } else {
            startBleScan()
        }

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

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from $address (status=$status)")
                updateNotification("Disconnected (status=$status)")

                try {
                    g.disconnect()
                    g.close()
                } catch (_: Exception) {}

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
                BleEvent.Enc1Up -> tempMain.inc()
                BleEvent.Enc1Down -> tempMain.dec()

                // температура пассажира
                BleEvent.Enc2Up -> tempPass.inc()
                BleEvent.Enc2Down -> tempPass.dec()

                else -> Log.i(TAG, "Unhandled BLE event: $raw")
            }

            // Update UI
            sendBroadcast(Intent(Actions.UI_LAST_EVENT).apply {
                putExtra("last_event", raw)
            })
        }
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

        super.onDestroy()
        Log.i(TAG, "Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}