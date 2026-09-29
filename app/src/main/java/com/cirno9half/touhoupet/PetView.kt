package com.cirno9half.touhoupet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.LruCache
import android.view.View

class PetView(
    context: Context
): View(context) {
    private val cache = LruCache<String, Bitmap>(72)
    private var bitmap: Bitmap? = null
    private var paint = Paint()

    init {
        paint.isFilterBitmap = false
        paint.isAntiAlias = false
    }

    fun updateBitmap(path: String, asset: Boolean) {
        var bmp = cache.get(path)
        if (bmp == null || bmp.isRecycled) {
            try {
                if (!asset) { bmp = BitmapFactory.decodeFile(path) }
                else { // 读取assets
                    try {
                        context.assets.open(path).use { i ->
                            bmp = BitmapFactory.decodeStream(i)
                        }
                    } catch (e: Exception) {
                        Log.e("PetView", "无法将流转换为Bitmap: ${e.message}")
                    }
                }
                if (bmp != null) cache.put(path, bmp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        bitmap = bmp
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val bmp = bitmap ?: return
        if (bmp.isRecycled) return

        val scale = (width.toFloat() / bmp.width)
            .coerceAtMost(height.toFloat() / bmp.height)

        val dstW = bmp.width * scale
        val dstH = bmp.height * scale

        val dx = (width - dstW) / 2f
        val dy = (height - dstH) / 2f

        val srcRect = Rect(0, 0, bmp.width, bmp.height)
        val dstRect = RectF(dx, dy, dx + dstW, dy + dstH)

        canvas.drawBitmap(bmp, srcRect, dstRect, paint)
    }

    fun dispose() {
        bitmap = null
        cache.snapshot().values.forEach { bmp ->
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
        }
        cache.evictAll()

    }
}
