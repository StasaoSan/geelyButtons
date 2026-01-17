package com.stasao.geelybuttons

import android.content.Context
import android.content.Intent
import android.util.Log

class GibApi(private val ctx: Context) {

    private val GIB_PACKAGE = "com.salat.gbinder"
    private val SET_INT_ACTION = "com.salat.gbinder.SET_INT_PROPERTY"

    fun setInt(id: Int, value: Int, area: Int? = null) {
        try {
            val i = Intent(SET_INT_ACTION).apply {
                setPackage(GIB_PACKAGE)
                putExtra("id", id)
                putExtra("value", value)
                if (area != null) putExtra("area", area)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            ctx.sendBroadcast(i)
            Log.i("GibApi", "SET_INT id=$id value=$value area=$area")
        } catch (e: Exception) {
            Log.e("GibApi", "Failed to send SET_INT", e)
        }
    }
}