package com.stasao.geelybuttons.bleService

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import java.util.ArrayDeque

class EspLink(
    private val tag: String = "EspLink"
) {
    private var gatt: BluetoothGatt? = null
    private var ch: BluetoothGattCharacteristic? = null

    private val queue: ArrayDeque<String> = ArrayDeque()
    private var ready: Boolean = false

    fun bind(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt = g
        ch = characteristic
        ready = true
        flush()
    }

    fun unbind() {
        ready = false
        gatt = null
        ch = null
        queue.clear()
    }

    fun send(msg: String) {
        if (!ready || gatt == null || ch == null) {
            if (queue.size < 80) queue.addLast(msg)
            Log.i(tag, "Queued: $msg")
            return
        }
        sendNow(msg)
    }

    private fun flush() {
        while (queue.isNotEmpty() && ready && gatt != null && ch != null) {
            sendNow(queue.removeFirst())
        }
    }
    @SuppressLint("MissingPermission")
    private fun sendNow(msg: String) {
        val g = gatt ?: return
        val c = ch ?: return

        val canWrite =
            (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

        if (!canWrite) {
            Log.w(tag, "Not writable; msg=$msg")
            return
        }

        c.writeType =
            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        c.value = msg.toByteArray(Charsets.UTF_8)
        val ok = g.writeCharacteristic(c)
        Log.i(tag, "Sent ok=$ok msg=$msg")
        if (!ok) {
            // если не ушло — вернём обратно в начало очереди
            queue.addFirst(msg)
        }
    }
}