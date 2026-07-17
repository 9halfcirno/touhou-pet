package com.cirno9half.touhoupet

import android.content.Context
import java.io.File
import java.io.InputStream

fun makeStream(context: Context, fromAsset: Boolean, path: String): InputStream {
    return  if (fromAsset) {
        context.assets.open(path)
    } else {
        File(path).inputStream()
    }
}