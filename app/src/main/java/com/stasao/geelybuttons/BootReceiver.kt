package com.stasao.geelybuttons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        Log.i("BootReceiver", "BOOT -> starting services (action=$action)")

        // 1) Overlay (если разрешено)
        tryStartOverlay(context)

        // 2) BLE listener как foreground
        val bleSvc = Intent(context, BleListenerService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(bleSvc)
            } else {
                context.startService(bleSvc)
            }
        } catch (e: Exception) {
            Log.e("BootReceiver", "start BleListenerService failed", e)
        }
    }

    private fun tryStartOverlay(context: Context) {
        // Если разрешения нет — не стартуем, иначе будет “тишина” и путаница
        val canDraw = try {
            Settings.canDrawOverlays(context)
        } catch (_: Exception) {
            false
        }

        if (!canDraw) {
            Log.w("BootReceiver", "Overlay permission missing, skip OverlayService")
            return
        }

        val overlaySvc = Intent(context, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_CONNECTED, false) // стартуем красным/неподключено
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(overlaySvc)
            } else {
                context.startService(overlaySvc)
            }
            Log.i("BootReceiver", "OverlayService started")
        } catch (e: Exception) {
            Log.e("BootReceiver", "start OverlayService failed", e)
        }
    }
}