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
import androidx.core.content.edit
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {
    private lateinit var tvLastEvent: TextView
    private lateinit var btnStartScan: Button
    private lateinit var listDevices: ListView

    private val discoveredDevices = mutableListOf<String>()
    private val adapter by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDevices)
    }

    private val lastEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent?.getStringExtra("last_event") ?: return
            tvLastEvent.text = "Последнее событие: $event"
        }
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val name = intent?.getStringExtra("device_name") ?: "Без имени"
            val address = intent?.getStringExtra("device_address") ?: return
            val rssi = intent.getIntExtra("rssi", 0) ?: 0

            val entry = "$name | $address | RSSI: $rssi dBm"
            if (!discoveredDevices.contains(entry)) {
                discoveredDevices.add(entry)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val asked = prefs.getBoolean("asked_overlay", false)

        if (!Settings.canDrawOverlays(this)) {
            if (!asked) {
                prefs.edit { putBoolean("asked_overlay", true) }
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri()
                    )
                )
            }
        } else {
            startService(Intent(this, OverlayService::class.java))
        }

        tvLastEvent = findViewById(R.id.tv_last_event)
        btnStartScan = findViewById(R.id.btn_start_scan)
        listDevices = findViewById(R.id.list_devices)

        listDevices.adapter = adapter

        btnStartScan.setOnClickListener {
            discoveredDevices.clear()
            adapter.notifyDataSetChanged()
            startForegroundService(
                Intent(this, BleListenerService::class.java)
                    .putExtra(BleListenerService.EXTRA_FORCE_SCAN, true)
            )
        }

        listDevices.setOnItemClickListener { _, _, position, _ ->
            val selected = discoveredDevices[position]
            val parts = selected.split("|")
            val address = parts.getOrNull(1)?.trim() ?: return@setOnItemClickListener

            val intent = Intent(this, BleListenerService::class.java)
            intent.putExtra("connect_address", address)

            getSharedPreferences("prefs", MODE_PRIVATE)
                .edit {
                    putString("saved_mac", address)
                }

            startForegroundService(
                Intent(this, BleListenerService::class.java)
                    .putExtra(BleListenerService.EXTRA_CONNECT_ADDRESS, address)
            )
        }

    }

    override fun onResume() {
        super.onResume()
        registerReceiver(lastEventReceiver, IntentFilter(Actions.UI_LAST_EVENT))
        registerReceiver(scanReceiver, IntentFilter(Actions.UI_SCAN_RESULT))

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(lastEventReceiver)
        unregisterReceiver(scanReceiver)
    }
}