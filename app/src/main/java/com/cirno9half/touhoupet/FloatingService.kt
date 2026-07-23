package com.cirno9half.touhoupet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import androidx.core.app.NotificationCompat
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

class FloatingService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_service"
    }

    private val LayoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private val floatings = mutableMapOf<Int, View>()
    private val draggableMap = mutableMapOf<Int, Boolean>()
    private val dragLastPosMap = mutableMapOf<Int, Pair<Float, Float>>()
    private val draggingMap = mutableMapOf<Int, Boolean>()

    private var floatingIds: Int = 0

    val statusBarHeight by lazy {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    val navigationBarHeight by lazy {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private lateinit var windowManager: WindowManager

    inner class LocalBinder : Binder() {
        fun getService(): FloatingService = this@FloatingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundService()
    }

    /**
     * 启动前台服务，在通知栏显示常驻通知
     */
    private fun startForegroundService() {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TouHou Pet")
            .setContentText("桌面宠物正在运行")
            .setSmallIcon(R.drawable.info_24px)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 创建通知渠道 (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于显示桌面宠物的悬浮窗"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun create(view: View, params: WindowManager.LayoutParams? = null): Int {
        val id = ++floatingIds
        // 不论传不传 params，都强制使用服务自己的 LayoutType 和 gravity
        val finalParams = (params ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            LayoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.type = LayoutType
        }
        windowManager.addView(view, finalParams)
        floatings[id] = view
        draggableMap[id] = true
        setupDragListener(id, view)
        return id
    }

    /**
     * 为悬浮窗设置拖动监听
     */
    private fun setupDragListener(id: Int, view: View) {
        view.setOnTouchListener(fun(_: View, event: MotionEvent): Boolean {
            if (draggableMap[id] != true) return false

            val layoutParams = view.layoutParams as? WindowManager.LayoutParams
                ?: return false

            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragLastPosMap[id] = Pair(event.rawX, event.rawY)
                    draggingMap[id] = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val lastPos = dragLastPosMap[id] ?: return true
                    val dx = event.rawX - lastPos.first
                    val dy = event.rawY - lastPos.second
                    dragLastPosMap[id] = Pair(event.rawX, event.rawY)
                    layoutParams.x += dx.toInt()
                    layoutParams.y += dy.toInt()
                    update(id, layoutParams)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    draggingMap[id] = false
                    dragLastPosMap.remove(id)
                    update(id, layoutParams)
                    true
                }

                else -> false
            }
        })
    }

    // 监听旋转事件, 将所有悬浮窗x, y互换
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        for ((id, view) in floatings) {
            val layoutParams = view.layoutParams as? WindowManager.LayoutParams ?: continue
            // 先换成内容坐标再交换坐标
            val (locX, locY) = toLocalPos(layoutParams.x, layoutParams.y)
            val (newX, newY) = fixPos(view, locY, locX) // 修正转换后的坐标
            layoutParams.x = newX
            layoutParams.y = newY
            update(id, layoutParams)
        }
    }

    /**
     * 设置悬浮窗是否可拖动
     * @param id 悬浮窗编号
     * @param draggable true 可拖动, false 不可拖动
     */
    fun setDraggable(id: Int, draggable: Boolean) {
        draggableMap[id] = draggable
    }

    fun isDragging(id: Int): Boolean {
        return draggingMap[id] ?: false
    }

    /**
     * 获取悬浮窗是否可拖动
     */
    fun isDraggable(id: Int): Boolean {
        return draggableMap[id] ?: false
    }

    fun update(id: Int, params: WindowManager.LayoutParams) {
        floatings[id]?.let { view ->
            if (isDragging(id)) {
                // 拖动时, 如果传入的 params 不是 view 自身的 layoutParams (即外部更新), 则保留当前拖动的坐标
                val currentParams = view.layoutParams as? WindowManager.LayoutParams
                if (currentParams != null && params !== currentParams) {
                    params.x = currentParams.x
                    params.y = currentParams.y
                    windowManager.updateViewLayout(view, params)
                    return@let
                }
            }
            val x = params.x
            val y = params.y // 也就是, params的坐标应作为内容坐标审视
            var pos = toScreenPos(x, y)
            pos = fixPos(view, pos.first, pos.second)
            val (localX, localY) = toLocalPos(pos.first, pos.second)
            // 用屏幕坐标fix后再换成内容坐标写回params
            params.x = localX
            params.y = localY
            windowManager.updateViewLayout(view, params)
        }
    }

    // 内容转为屏幕坐标(包含状态栏)
    fun toScreenPos(x: Int, y: Int): Pair<Int, Int> {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return Pair(x, if (isLandscape) y else y + statusBarHeight)
    }

    // 屏幕转为内容坐标(原点在状态栏下)
    fun toLocalPos(x: Int, y: Int): Pair<Int, Int> {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return Pair(x, if (isLandscape) y else y - statusBarHeight)
    }

    // 将屏幕坐标限制在合理坐标内, x对应0~屏幕宽度-view.width, y对应状态栏~屏幕高 - view.height - 导航栏
    // 横屏时导航栏在侧边, 不限制y方向
    fun fixPos(view: View, x: Int, y: Int): Pair<Int, Int> {
        val displayMetrics = resources.displayMetrics
        // 使用 WindowManager.LayoutParams 的尺寸(构造时固定的像素值),
        // 而非 view.width/height(测量值) —— onConfigurationChanged 时 view 尚未为新方向重新布局, 测量值为 0
        // takeIf { it > 0 } 过滤 WRAP_CONTENT(-2) 和 MATCH_PARENT(-1) 等特殊值
        val winW = (view.layoutParams?.width?.takeIf { it > 0 } ?: view.width).coerceAtLeast(1)
        val winH = (view.layoutParams?.height?.takeIf { it > 0 } ?: view.height).coerceAtLeast(1)
        val maxX = displayMetrics.widthPixels - winW
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val maxY = if (isLandscape) {
            displayMetrics.heightPixels - winH
        } else {
            displayMetrics.heightPixels - winH - navigationBarHeight
        }
        return Pair(
            x.coerceIn(0, maxX),
            if (isLandscape) y.coerceIn(0, maxY) else y.coerceIn(statusBarHeight, maxY)
        )
    }

    fun exit() {
        stopSelf()
    }

    fun destroy(id: Int) {
        floatings.remove(id)?.let { view ->
            windowManager.removeView(view)
        }
        draggableMap.remove(id)
        dragLastPosMap.remove(id)
        draggingMap.remove(id)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        for (view in floatings.values) {
            windowManager.removeView(view)
        }
        floatings.clear()
        draggableMap.clear()
        dragLastPosMap.clear()
        draggingMap.clear()
        super.onDestroy()
    }
}
