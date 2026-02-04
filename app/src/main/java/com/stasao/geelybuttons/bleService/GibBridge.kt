package com.stasao.geelybuttons.bleService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.stasao.geelybuttons.GibApi

class GibBridge(
    private val ctx: Context,
    private val gib: GibApi,
    private val tag: String = "GibBridge",
    private val onInt: (id: Int, area: Int, v: Int) -> Unit,
    private val onFloat: (id: Int, area: Int, v: Float) -> Unit
) {
    private var registered = false

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction("com.salat.gbinder.PROPERTY_INT_CHANGED")
            addAction("com.salat.gbinder.PROPERTY_FLOAT_CHANGED")
            // we really need this actions???
            addAction("com.salat.gbinder.PROPERTY_INT_RESULT")
            addAction("com.salat.gbinder.PROPERTY_FLOAT_RESULT")
        }
        ctx.registerReceiver(receiver, filter)
        registered = true
        Log.i(tag, "registered")
    }

    fun unregister() {
        if (!registered) return
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
        registered = false
    }

    fun listenInt(id: Int, area: Int? = null) = gib.listenIntProperty(id, area)
    fun listenFloat(id: Int, area: Int? = null) = gib.listenFloatProperty(id, area)

    fun requestInt(id: Int, area: Int? = null) = gib.requestIntProperty(id, area)
    fun requestFloat(id: Int, area: Int? = null) = gib.requestFloatProperty(id, area)

    fun requestInitialState() {
        requestInt(268698368, area = 8)     // rear
        requestInt(269027328, area = 8)     // electric/front
        requestInt(268566784, area = 8)     // fan speed
        requestInt(268829952, area = 8)     // single / dual
        requestFloat(268828928, area = 1)   // main temp
        requestFloat(268828928, area = 4)   // pass temp
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val id = intent.getIntExtra("id", -1)
            if (id == -1) return

            val area = when {
                intent.hasExtra("area") -> intent.getIntExtra("area", 0)
                intent.hasExtra("zone") -> intent.getIntExtra("zone", 0)
                else -> 0
            }

            // твой кейс: приходит "result"
            val any = intent.extras?.get("result")
            if (any == null) {
                Log.i(tag, "no result; extras=${intent.extras?.keySet()}")
                return
            }

            when (any) {
                is Int -> onInt(id, area, any)
                is Long -> onInt(id, area, any.toInt())
                is Float -> onFloat(id, area, any)
                is Double -> onFloat(id, area, any.toFloat())
                else -> Log.i(tag, "unknown result type=${any::class.java} any=$any")
            }
        }
    }
}