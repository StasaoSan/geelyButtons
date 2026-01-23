package com.stasao.geelybuttons

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var dotView: View? = null
    private var root: FrameLayout? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val connected = intent?.getBooleanExtra(EXTRA_CONNECTED, false) ?: false
            setConnected(connected)
        }
    }

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val size = dp(14f)
        val pad = dp(6f)

        val dot = View(this).apply {
            background = circleDrawable(connected = false)
            layoutParams = FrameLayout.LayoutParams(size, size)
        }

        val container = FrameLayout(this).apply {
            setPadding(pad, pad, pad, pad)
            addView(dot)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10f)
            y = dp(60f)
        }

        root = container
        dotView = dot
        wm.addView(container, params)

        registerReceiver(receiver, IntentFilter(ACTION_CONN_STATE))
    }

    private fun setConnected(connected: Boolean) {
        dotView?.background = circleDrawable(connected)
    }

    private fun circleDrawable(connected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            // green / red
            setColor(if (connected) 0xFF00C853.toInt() else 0xFFD50000.toInt())
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try {
            root?.let { wm.removeView(it) }
        } catch (_: Exception) {}
        root = null
        dotView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_CONN_STATE = "com.stasao.geelybuttons.CONN_STATE"
        const val EXTRA_CONNECTED = "connected"
    }
}