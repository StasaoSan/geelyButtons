package com.stasao.geelybuttons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etDeviceNames: EditText
    private lateinit var etUpIntent: EditText
    private lateinit var etDownIntent: EditText
    private lateinit var tvLastEvent: TextView
    private lateinit var btnStartScan: Button
    private lateinit var listDevices: ListView

    private val discoveredDevices = mutableListOf<String>()
    private val adapter: ArrayAdapter<String> by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDevices)
    }

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent?.getStringExtra("last_event") ?: return
            tvLastEvent.text = "Последнее событие: $event"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etDeviceNames = findViewById(R.id.et_device_names)
        etUpIntent = findViewById(R.id.et_up_intent)
        etDownIntent = findViewById(R.id.et_down_intent)
        tvLastEvent = findViewById(R.id.tv_last_event)
        btnStartScan = findViewById(R.id.btn_start_scan)
        listDevices = findViewById(R.id.list_devices)

        listDevices.adapter = adapter

        etDeviceNames.setText("DialRemote (connect by MAC from the list)")
        etUpIntent.setText("ENC:+1 / ENC:-1")
        etDownIntent.setText("BTN:CLICK / BTN:LONG")

        btnStartScan.setOnClickListener {
            startBleService()
        }

        listDevices.setOnItemClickListener { _, _, position, _ ->
            val selected = discoveredDevices[position]
            val parts = selected.split("|")
            val address = parts.getOrNull(1)?.trim() ?: return@setOnItemClickListener
            val intent = Intent(this, BleListenerService::class.java)
            intent.putExtra("connect_address", address)
            intent.putExtra("device_names", etDeviceNames.text.toString())
            intent.putExtra("up_intent", etUpIntent.text.toString())
            intent.putExtra("down_intent", etDownIntent.text.toString())
            startForegroundService(intent)
        }
    }

    private fun startBleService() {
        val intent = Intent(this, BleListenerService::class.java)
        intent.putExtra("device_names", etDeviceNames.text.toString())
        intent.putExtra("up_intent", etUpIntent.text.toString())
        intent.putExtra("down_intent", etDownIntent.text.toString())
        startForegroundService(intent)
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val name = intent?.getStringExtra("device_name") ?: "Без имени"
            val address = intent?.getStringExtra("device_address") ?: ""
            val rssi = intent?.getIntExtra("rssi", 0) ?: 0

            val entry = "$name | $address | RSSI: $rssi dBm"
            if (!discoveredDevices.contains(entry)) {
                discoveredDevices.add(entry)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(eventReceiver, IntentFilter("com.stasao.geelybuttons.BLE_EVENT"))
        registerReceiver(scanReceiver, IntentFilter("com.stasao.geelybuttons.BLE_SCAN_RESULT"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(eventReceiver)
        unregisterReceiver(scanReceiver)
    }
}
