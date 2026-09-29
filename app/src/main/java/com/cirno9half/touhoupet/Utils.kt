package com.cirno9half.touhoupet

import android.content.Context
import android.content.res.Resources
import java.io.File
import java.io.InputStream

fun makeStream(context: Context, fromAsset: Boolean, path: String): InputStream {
    return  if (fromAsset) {
        context.assets.open(path)
    } else {
        File(path).inputStream()
    }
}

fun dp2px(size: String): Number {
    val reg = Regex("""(\d+)([a-z]+)""")
    val match = reg.find(size) ?: return 0
    val (valueStr, unit) = match.destructured
    val value = valueStr.toFloat()
    return when (unit) {
        "dp", "dip" -> value * Resources.getSystem().displayMetrics.density
        "sp" -> value * Resources.getSystem().displayMetrics.scaledDensity
        "px" -> value
        else -> value
    }
}