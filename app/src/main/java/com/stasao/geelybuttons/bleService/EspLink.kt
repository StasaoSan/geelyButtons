package com.stasao.geelybuttons.bleService

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import java.util.ArrayDeque

class EspLink(
    private val tag: String = "EspLink",
) {
    private var gatt: BluetoothGatt? = null
    private var ch: BluetoothGattCharacteristic? = null

    private val queue = ArrayDeque<String>()
    private var inFlight: Boolean = false
    private var lastSent: String? = null

    fun bind(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        gatt = g
        ch = c
        Log.i(tag, "bind(): ready, queued=${queue.size}")
        trySendNext()
    }

    fun unbind() {
        gatt = null
        ch = null
        inFlight = false
        lastSent = null
        // queue НЕ чистим — чтобы после реконнекта можно было дослать, если хочешь
        Log.i(tag, "unbind()")
    }

    fun send(msg: String) {
        queue.addLast(msg)
        trySendNext()
    }

    /**
     * Должен вызываться из BluetoothGattCallback.onCharacteristicWrite
     */
    fun onWriteComplete(success: Boolean) {
        val sent = lastSent
        inFlight = false
        lastSent = null

        if (!success && sent != null) {
            // вернём обратно в начало
            queue.addFirst(sent)
            Log.w(tag, "write failed -> requeue: $sent (queue=${queue.size})")
        }

        trySendNext()
    }

    private fun trySendNext() {
        val g = gatt ?: return
        val c = ch ?: return
        if (inFlight) return
        val msg = queue.pollFirst() ?: return

        val canWrite =
            (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

        if (!canWrite) {
            Log.w(tag, "Not writable; drop msg=$msg")
            return
        }

        c.writeType =
            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        c.value = msg.toByteArray(Charsets.UTF_8)

        // ВАЖНО: ставим inFlight ДО вызова writeCharacteristic
        inFlight = true
        lastSent = msg

        val ok = try {
            g.writeCharacteristic(c)
        } catch (se: SecurityException) {
            Log.e(tag, "SecurityException on writeCharacteristic()", se)
            false
        }

        if (!ok) {
            // writeCharacteristic вернул false: значит не стартануло — снимаем inFlight и requeue
            inFlight = false
            lastSent = null
            queue.addFirst(msg)
            Log.w(tag, "writeCharacteristic returned false -> requeue (queue=${queue.size})")
        } else {
            Log.i(tag, "write started: $msg")
            // дальше ждём onCharacteristicWrite -> onWriteComplete()
        }
    }
}