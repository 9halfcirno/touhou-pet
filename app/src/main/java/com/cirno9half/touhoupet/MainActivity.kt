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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import com.cirno9half.touhoupet.ui.PetManagementScreen
import kotlinx.coroutines.android.HandlerDispatcher
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

            PetManager.loadPetList(floatingService)

            // 如果你的 Pet 构造函数需要 Context 来创建 View，可以直接把服务传过去（因为 Service 本身就是 Context）
            // 读取pet.json
            val assetPath = "pets/cirno"

            val stream = this@MainActivity.assets.open("$assetPath/pet.json")
            val reader = BufferedReader(InputStreamReader(stream))
            val json = JSONObject(reader.use { it.readText() })
            val cirno = Pet(
                floatingService,
                PetInfo("pets/cirno", true, json)
            )
            lifecycleScope.launch {
                cirno.loadResource()

                cirno.show()

                cirno.animation.switchTo("idle")
                cirno.animation.startLoop()

                cirno.action.loadAPI()

                cirno.action.switchTo("idle")
                cirno.action.startLoop()
            }

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