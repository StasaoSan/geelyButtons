package com.stasao.geelybuttons.bleService

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

@SuppressLint("MissingPermission")
class BleClient(
    private val ctx: Context,
    private val adapter: BluetoothAdapter,
    private val espLink: EspLink,
    private val tag: String = "BleClient",
    private val onConnectedChanged: (Boolean) -> Unit,
    private val onBleTextEvent: (String) -> Unit,
    private val onScanResult: (name: String, address: String, rssi: Int) -> Unit
) {
    private var gatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(address: String) {
        disconnect()
        val device = adapter.getRemoteDevice(address)
        Log.i(tag, "connect $address")
        gatt = device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        espLink.unbind()
        onConnectedChanged(false)
    }

    fun startScan(durationMs: Long = 2000L) {
        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(tag, "scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        Log.i(tag, "scan started")

        mainHandler.postDelayed({
            try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
            Log.i(tag, "scan stopped")
        }, durationMs)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            onScanResult(d.name ?: "NoName", d.address, result.rssi)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val connected = newState == BluetoothProfile.STATE_CONNECTED
            Log.i(tag, "conn state=$newState status=$status")
            onConnectedChanged(connected)

            if (connected) {
                mainHandler.postDelayed({ g.discoverServices() }, 800)
            } else {
                try { g.disconnect(); g.close() } catch (_: Exception) {}
                if (gatt === g) gatt = null
                espLink.unbind()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(tag, "discover failed $status")
                return
            }

            val service = g.getService(BleUuids.ESP_SERVICE_UUID) ?: run {
                Log.e(tag, "ESP service not found")
                return
            }

            val ch = service.getCharacteristic(BleUuids.ESP_CHAR_UUID) ?: run {
                Log.e(tag, "ESP char not found")
                return
            }

            // bind link for writes
            espLink.bind(g, ch)

            // subscribe notifications
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(BleUuids.CCCD_UUID) ?: run {
                Log.e(tag, "CCCD not found")
                return
            }
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(cccd)

            Log.i(tag, "subscribed")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val raw = characteristic.value?.toString(Charsets.UTF_8)?.trim() ?: return
            onBleTextEvent(raw)
        }
    }
}