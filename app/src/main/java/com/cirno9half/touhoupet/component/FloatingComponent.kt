package com.cirno9half.touhoupet.component

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.cirno9half.touhoupet.Pet

abstract class FloatingComponent(val pet: Pet) {
    abstract val view: View
    var visibility: Boolean = false

    abstract fun show(): Unit
    abstract fun hidden(): Unit
    abstract fun updateParams(params: WindowManager.LayoutParams)
    abstract fun updatePosition()
}