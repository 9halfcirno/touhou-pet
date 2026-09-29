package com.cirno9half.touhoupet

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.div

object PetManager {
    private const val TAG = "PetManager"

    // 必须是 Compose 可观察集合：loadPetList 在 ServiceConnection 回调里异步写入，
    // 普通 MutableMap 不会触发重组，会导致首次启动时列表停在空快照上。
    val petList = mutableStateMapOf<String, PetInfo>()

    /**
     * 活跃的Pet的实例
     *
     * 必须是 Compose 可观察集合: UI 的开关状态直接由 containsKey(id) 派生,
     * start/stop 后要能触发重组。
     */
    val pets = mutableStateMapOf<String, Pet>()
    private var assetPet = emptyArray<String>()
    private var externalPet = emptyArray<String>()

    /**
     * 当前连接的悬浮窗服务。
     *
     * Pet 的悬浮窗/拖动/对话框都由 FloatingService 承载, 因此 start 必须拿到它才能构造 Pet。
     * 由 MainActivity 在 ServiceConnection 建立后通过 [attach] 注入。
     */
    private var service: FloatingService? = null

    /**
     * 加载 Pet 资源的协程域。
     *
     * start 的签名不是 suspend(MainUI 的开关回调是普通 lambda), 所以这里自带作用域异步加载。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 注入悬浮窗服务, 由 MainActivity 在 onServiceConnected 中调用
     */
    fun attach(service: FloatingService) {
        this.service = service
    }

    fun loadPetList(context: Context) {
        // 先读assets目录的pet
        assetPet = try {
            context.assets.list("pets") ?: emptyArray()
        } catch (e: java.io.IOException) {
            emptyArray()
        }
        for (file in assetPet) {
            val petInfo = readInfo(context, true, ("pets/$file"))
            petList[petInfo.id] = petInfo
        }

        // 读外部目录的pet
        val extraPath = context.getExternalFilesDir(null)?.path
        if (extraPath != null) {
            val petPath = (Path(extraPath) / "pets")
            // 适配API 24的写法
            externalPet = try {
                File(petPath.toString()).listFiles { it.isDirectory }
                    ?.map { it.name }
                    ?.toTypedArray()
                    ?: emptyArray()
            } catch (e: IOException) {
                emptyArray()
            }

            for (file in externalPet) {
                val filePath = (petPath / file)
                val petInfo = readInfo(context, false, filePath.toString())
                petList[petInfo.id] = petInfo
            }
        }
    }

    /**
     * 读取持久化的 enable 状态。没有记录(文件不存在/键不存在)时默认启用。
     */
    private fun isEnabled(context: Context, info: PetInfo): Boolean {
        val data = DataManager(info.dataPath(context))
        data.load()
        return data.getBoolean("enable", true)
    }

    /**
     * 启动上一次处于启用状态的 Pet。
     *
     * 由 MainActivity 在服务连接、列表加载完成后调用。首次运行没有 enable 记录,
     * 此时所有 Pet 都视为启用(与之前 hardcode 启动 cirno 的行为一致)。
     */
    fun restoreEnabled() {
        val context = service ?: run {
            Log.e(TAG, "恢复 Pet 失败: 尚未连接 FloatingService")
            return
        }
        for ((id, info) in petList) {
            if (isEnabled(context, info)) start(id)
        }
    }

    /**
     * 启动一个Pet
     *
     * 已启动的 Pet 直接返回 true(幂等); 同时把 enable 标记写入 pet.data 持久化。
     * 资源加载在后台进行, 函数返回时只保证实例已创建。
     * @param id Pet的json定义中的id
     * @return 是否处于已启动状态
     */
    fun start(id: String): Boolean {
        if (pets.containsKey(id)) return true // 已经在跑, 幂等

        val service = this.service
        if (service == null) {
            Log.e(TAG, "启动 Pet \"$id\" 失败: 尚未连接 FloatingService")
            return false
        }
        val info = petList[id]
        if (info == null) {
            Log.e(TAG, "启动 Pet \"$id\" 失败: 未找到该 Pet")
            return false
        }

        // 构造时会创建悬浮窗, 需要主线程
        val pet = try {
            Pet(service, info)
        } catch (e: Exception) {
            Log.e(TAG, "创建 Pet \"$id\" 失败", e)
            return false
        }

        // 先 load 再写 enable, 否则 save 会把 data.json 里已有的键整个覆盖掉
        pet.data.load()
        pet.data.set("enable", true)
        pet.data.save()
        pets[id] = pet

        scope.launch {
            try {
                pet.loadResource()
                if (pets[id] !== pet) return@launch // 加载期间被 stop
                pet.show()
                pet.animation.switchTo(info.defaultAction)
                pet.animation.startLoop()
                pet.action.loadAPI()
                pet.action.switchTo(info.defaultAction)
                pet.action.startLoop()
            } catch (e: Exception) {
                Log.e(TAG, "启动 Pet \"$id\" 失败", e)
            }
        }
        return true
    }

    /**
     * 关闭一个Pet
     *
     * 写入 enable = false 后释放悬浮窗/动画/动作等资源, 并把实例从 [pets] 中移除。
     * 需要在主线程调用(会销毁悬浮窗)。
     * @param id 要关闭的pet的id
     * @return 是否真的关闭了一个运行中的 Pet
     */
    fun stop(id: String): Boolean {
        val pet = pets.remove(id) ?: return false // 未在运行的 Pet 没有实例可写, 直接忽略
        pet.data.set("enable", false)
        // dispose 内部会 data.save() 落盘, 并销毁悬浮窗与控制器
        pet.dispose()
        return true
    }

    /**
     * 释放所有pet, 在退出时调用, 不走stop避免污染enable状态
     */
    fun disposeAll() {
        for (id in pets.keys.toList()) {
            val pet = pets.remove(id) ?: continue
            pet.dispose()
        }
    }

    private fun readInfo(context: Context, fromAsset: Boolean, path: String): PetInfo {
        val ins = makeStream(context, fromAsset, "$path/pet.json")
        val json = ins.bufferedReader().use { JSONObject(it.readText()) }
        return PetInfo(path, fromAsset, json)
    }
}