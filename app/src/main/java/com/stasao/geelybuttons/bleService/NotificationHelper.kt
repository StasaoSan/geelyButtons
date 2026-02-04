package com.stasao.geelybuttons.bleService

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

class NotificationHelper(
    private val ctx: Context,
    private val channelId: String = "ble_channel",
    private val notifId: Int = 1
) {
    fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "BLE Listener",
            NotificationManager.IMPORTANCE_LOW
        )
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(content: String): Notification {
        return NotificationCompat.Builder(ctx, channelId)
            .setContentTitle("GeelyButtons")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    fun update(content: String) {
        ctx.getSystemService(NotificationManager::class.java).notify(notifId, build(content))
    }
}