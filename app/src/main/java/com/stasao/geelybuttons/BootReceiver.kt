package com.stasao.geelybuttons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "BOOT_COMPLETED -> starting BleListenerService")

        val svc = Intent(context, BleListenerService::class.java).apply {
            putExtra(BleListenerService.EXTRA_AUTO_MODE, true)
        }
        context.startForegroundService(svc)
    }
}