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

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val connectedDevices = mutableMapOf<String, BluetoothGatt>()

    private var targetDeviceNames: List<String> = listOf("RoundRemote")
    private var upIntentAction: String = ""
    private var downIntentAction: String = ""

    private val TAG = "BleListenerService"

    private val ESP_SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val ESP_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val GIB_PACKAGE = "com.salat.gbinder"
    private val GIB_SET_INT_ACTION = "com.salat.gbinder.SET_INT_PROPERTY"

    private val FAN_SPEED_ID = 268566784
    private val FAN_SPEED_AREA = 8

    private val FAN_SPEED_VALUES = intArrayOf(
        0,
        268566785, 268566786, 268566787, 268566788, 268566789,
        268566790, 268566791, 268566792, 268566793,
        268566794
    )

    private var fanSpeedIndex: Int = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetDeviceNames = intent?.getStringExtra("device_names")?.split(",")?.map { it.trim() } ?: listOf("RoundRemote")
        upIntentAction = intent?.getStringExtra("up_intent") ?: ""
        downIntentAction = intent?.getStringExtra("down_intent") ?: ""

        val connectAddress = intent?.getStringExtra("connect_address")

        Log.i(TAG, "Сервис запущен. Ищем: $targetDeviceNames | Запрос подключения: $connectAddress")

        createNotificationChannel()
        startForeground(1, getNotification("Запуск..."))

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth выключен")
            updateNotification("Bluetooth выключен")
            return START_NOT_STICKY
        }

        if (connectAddress != null) {
            Log.i(TAG, "Запрос на подключение к $connectAddress")
            val device = bluetoothAdapter.getRemoteDevice(connectAddress)

            connectedDevices.values.forEach { it.close() }
            connectedDevices.clear()

            var attempt = 0
            val handler = Handler(Looper.getMainLooper())
            val retryRunnable = object : Runnable {
                override fun run() {
                    attempt++
                    Log.i(TAG, "Попытка подключения $attempt к $connectAddress")
                    device.connectGatt(this@BleListenerService, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    if (attempt < 3) {
                        handler.postDelayed(this, 3000)
                    }
                }
            }
            handler.post(retryRunnable)
        } else {
            startBleScan()
        }

        return START_STICKY
    }

    private fun startBleScan() {
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE сканер не доступен")
            updateNotification("BLE не поддерживается")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            Log.i(TAG, "Сканирование запущено (все устройства) на 2 секунды")
            updateNotification("Сканирую все BLE 2 сек...")

            Handler(Looper.getMainLooper()).postDelayed({
                scanner.stopScan(scanCallback)
                Log.i(TAG, "Сканирование остановлено по таймауту (2 сек)")
                updateNotification("Сканирование завершено")
            }, 2000)

        } catch (e: SecurityException) {
            Log.e(TAG, "Нет разрешения на сканирование", e)
            updateNotification("Нет разрешения BLE")
        }
    }

    private fun sendGibSetInt(id: Int, value: Int, area: Int? = null) {
        try {
            val i = Intent(GIB_SET_INT_ACTION).apply {
                setPackage(GIB_PACKAGE)
                putExtra("id", id)
                putExtra("value", value)
                if (area != null) putExtra("area", area)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            sendBroadcast(i)
            Log.i(TAG, "GIB SET_INT sent: id=$id value=$value area=$area")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send GIB SET_INT", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Без имени"
            val address = device.address
            val rssi = result.rssi

            Log.i(TAG, "Обнаружено: $name | $address | RSSI: $rssi dBm")

            val broadcastIntent = Intent("com.stasao.geelybuttons.BLE_SCAN_RESULT")
            broadcastIntent.putExtra("device_name", name)
            broadcastIntent.putExtra("device_address", address)
            broadcastIntent.putExtra("rssi", rssi)
            sendBroadcast(broadcastIntent)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Ошибка сканирования: код $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Подключено к $address (status: $status)")
                connectedDevices[address] = gatt
                updateNotification("Подключено к ${gatt.device.name ?: address}")

                Handler(Looper.getMainLooper()).postDelayed({
                    if (connectedDevices.containsKey(address)) {
                        val success = gatt.discoverServices()
                        Log.i(TAG, "discoverServices запущен: $success")
                    }
                }, 1500)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Отключено от $address (status: $status)")
                val oldGatt = connectedDevices.remove(address)
                oldGatt?.disconnect()
                oldGatt?.close()
                gatt.disconnect()
                gatt.close()
                updateNotification("Отключено (status $status)")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Ошибка обнаружения сервисов: $status")
                return
            }

            Log.i(TAG, "Сервисы обнаружены на ${gatt.device.address}")

            val service = gatt.getService(ESP_SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Не найден ESP service: $ESP_SERVICE_UUID")
                return
            }

            val ch = service.getCharacteristic(ESP_CHAR_UUID)
            if (ch == null) {
                Log.e(TAG, "Не найдена ESP characteristic: $ESP_CHAR_UUID")
                return
            }

            Log.i(TAG, "Подписка на notify: ${ch.uuid}")
            gatt.setCharacteristicNotification(ch, true)

            val descriptor = ch.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                Log.e(TAG, "CCCD descriptor не найден: $CCCD_UUID")
                return
            }

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val wrote = gatt.writeDescriptor(descriptor)
            Log.i(TAG, "writeDescriptor(CCCD) called: $wrote")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value?.toString(Charset.defaultCharset())?.trim() ?: return
            Log.i(TAG, "Получено от ${gatt.device.address}: $data")

            when (data) {
                "ENC:+1" -> {
                    fanSpeedIndex = (fanSpeedIndex + 1).coerceAtMost(FAN_SPEED_VALUES.lastIndex)
                    val value = FAN_SPEED_VALUES[fanSpeedIndex]
                    sendGibSetInt(FAN_SPEED_ID, value, FAN_SPEED_AREA)
                }
                "ENC:-1" -> {
                    fanSpeedIndex = (fanSpeedIndex - 1).coerceAtLeast(0)
                    val value = FAN_SPEED_VALUES[fanSpeedIndex]
                    sendGibSetInt(FAN_SPEED_ID, value, FAN_SPEED_AREA)
                }
                else -> {
                    Log.w(TAG, "Неизвестное событие BLE: $data")
                }
            }

            val broadcastIntent = Intent("com.stasao.geelybuttons.BLE_EVENT")
            broadcastIntent.putExtra("last_event", data)
            sendBroadcast(broadcastIntent)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("ble_channel", "BLE Listener", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun getNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "ble_channel")
            .setContentTitle("BLE Listener")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = getNotification(content)
        getSystemService(NotificationManager::class.java).notify(1, notification)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        connectedDevices.values.forEach { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        super.onDestroy()
        Log.i(TAG, "Сервис остановлен")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
