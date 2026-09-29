package com.cirno9half.touhoupet

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import com.cirno9half.touhoupet.ui.PetManagementScreen
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    private var isBound = false

    private val connection = object : ServiceConnection {
        var floatingService: FloatingService? = null
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FloatingService.LocalBinder
            val floatingService = binder.getService()
            this.floatingService = floatingService
            isBound = true

            // 把服务交给 PetManager: 由它加载 Pet 列表, 并恢复上次处于启用状态的 Pet
            PetManager.attach(floatingService)
            PetManager.loadPetList(floatingService)
            PetManager.restoreEnabled()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            floatingService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 按返回键时退回后台，不销毁 Activity
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
//        supportActionBar?.hide()

        // 2. 检查悬浮窗权限喵
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }



        setContent {
            PetManagementScreen(
                onCloseAllClick = {
                    exit() // 退出
                },
                onPetToggle = { info, checked ->
                    if (checked) PetManager.start(info.id) else PetManager.stop(info.id)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置界面返回且已获得权限时自动绑定服务喵
        if (Settings.canDrawOverlays(this)) {
            if (!isBound) {
                bindFloatingService()
            }
        } else {
            Toast(this).apply {
                setText("未获取到悬浮窗权限")
                show()
            }
        }
    }

    private fun bindFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    fun exit() {
        PetManager.disposeAll()
        val intent = Intent(this, FloatingService::class.java)
        stopService(intent)
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}