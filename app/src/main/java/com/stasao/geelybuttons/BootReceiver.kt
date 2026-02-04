package com.stasao.geelybuttons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i("BootReceiver", "BOOT_COMPLETED -> starting BleListenerService")

        val svc = Intent(context, BleListenerService::class.java)

        try {
            context.startForegroundService(svc)
        } catch (e: Exception) {
            Log.e("BootReceiver", "startForegroundService failed", e)
            try { context.startService(svc) } catch (_: Exception) {}
        }
    }
}