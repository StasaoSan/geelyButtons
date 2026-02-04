package com.stasao.geelybuttons

import android.content.Context
import android.content.Intent
import android.util.Log

class GibApi(private val ctx: Context) {

    companion object {
        private const val TAG = "GibApi"
        private const val GIB_PACKAGE = "com.salat.gbinder"

        // set
        private const val SET_INT_ACTION   = "com.salat.gbinder.SET_INT_PROPERTY"
        private const val SET_FLOAT_ACTION = "com.salat.gbinder.SET_FLOAT_PROPERTY"

        // listen
        private const val LISTEN_CHANGES_ACTION = "com.salat.gbinder.LISTEN_PROPERTY_CHANGES"

        // get (request current)
        private const val GET_INT_ACTION   = "com.salat.gbinder.GET_INT_PROPERTY"
        private const val GET_FLOAT_ACTION = "com.salat.gbinder.GET_FLOAT_PROPERTY"
    }

    fun setFloat(id: Int, value: Float, area: Int? = null) {
        val i = Intent(SET_FLOAT_ACTION).apply {
            setPackage(GIB_PACKAGE)
            putExtra("id", id)
            putExtra("value", value)
            if (area != null) putExtra("area", area)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        ctx.sendBroadcast(i)
        Log.i(TAG, "SET_FLOAT id=$id value=$value area=$area")
    }

    fun setInt(id: Int, value: Int, area: Int? = null) {
        val i = Intent(SET_INT_ACTION).apply {
            setPackage(GIB_PACKAGE)
            putExtra("id", id)
            putExtra("value", value)
            if (area != null) putExtra("area", area)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        ctx.sendBroadcast(i)
        Log.i(TAG, "SET_INT id=$id value=$value area=$area")
    }

    // === subscribe changes (INT/FLOAT одинаково) ===
    fun listenIntProperty(id: Int, area: Int? = null) = listenProperty(id, area)
    fun listenFloatProperty(id: Int, area: Int? = null) = listenProperty(id, area)

    private fun listenProperty(id: Int, area: Int?) {
        val i = Intent(LISTEN_CHANGES_ACTION).apply {
            setPackage(GIB_PACKAGE)
            putExtra("id", id)
            if (area != null) putExtra("area", area)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        ctx.sendBroadcast(i)
        Log.i(TAG, "LISTEN id=$id area=$area")
    }

    // === request current value ===
    fun requestIntProperty(id: Int, area: Int? = null) {
        val i = Intent(GET_INT_ACTION).apply {
            setPackage(GIB_PACKAGE)
            putExtra("id", id)
            if (area != null) putExtra("area", area)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        ctx.sendBroadcast(i)
        Log.i(TAG, "GET_INT id=$id area=$area")
    }

    fun requestFloatProperty(id: Int, area: Int? = null) {
        val i = Intent(GET_FLOAT_ACTION).apply {
            setPackage(GIB_PACKAGE)
            putExtra("id", id)
            if (area != null) putExtra("area", area)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        ctx.sendBroadcast(i)
        Log.i(TAG, "GET_FLOAT id=$id area=$area")
    }
}