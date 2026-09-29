package com.cirno9half.touhoupet

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.cirno9half.touhoupet.component.DialogBubble
import kotlinx.coroutines.Runnable
import org.json.JSONObject
import kotlin.io.path.Path
import kotlin.io.path.div


class Pet(var context: FloatingService?, val info: PetInfo) {
    /**
     * pet目录
     */
    val path: String = Path(info.path).toString()

    var data: DataManager
    var floating: Int
    var floatingParams = WindowManager.LayoutParams(
        dp2px(info.size).toInt(), dp2px(info.size).toInt(),
        if (Build.VERSION.SDK_INT >= 26) 2038 else 2002,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT)

    val view: PetView = PetView(context!!)

    private var mainHandler: Handler? = Handler(Looper.getMainLooper())


    val animation = AnimationController(this)
    val action = ActionController(this)

    inner class Component() {
        val dialog = DialogBubble(this@Pet)
    }
    val component = Component()

    init {
        floating = context!!.create(view, floatingParams) // 创建悬浮窗并保存编号
        // 默认在屏幕中间
        floatingParams.apply {
            val dm = context!!.resources.displayMetrics
            x = (dm.widthPixels - width) / 2
            y = (dm.heightPixels - height) / 2
        }

        floating.let {
            context?.update(it, floatingParams)
            context?.setDraggable(it, true)
        }

        // 获取数据存储路径, 优先尝试Extra路径
        data = DataManager(info.dataPath(context!!))

        context?.onDrag(floating) { state ->
            component.dialog.updatePosition()
        }
    }

    fun show() {
        view.visibility = View.VISIBLE
    }
    fun hide() {
        view.visibility = View.INVISIBLE
    }

    suspend fun loadResource() {
        context ?: return
        val motionMap = info.motions
        for (key in motionMap.keys()) {
            val motionFile = motionMap.getString(key)
            val motionPath = Path(this.path) / "motions" / motionFile
            val metaPath = motionPath / "meta.json"
            val inputStream = makeStream(context ?: return, info.fromAsset, metaPath.toString())
            val motionJson = inputStream.bufferedReader().use { JSONObject(it.readText()) }
            animation.loadAnimation(AnimationData(motionPath.toString(), motionJson))
        }
        val actionMap = info.actions
        for (key in actionMap.keys()) {
            if (key == "default") continue
            val actionFile = actionMap.getString(key)
            val actionPath = Path(this.path) / "actions" / actionFile
            val iptStr = makeStream(context ?: return, info.fromAsset, actionPath.toString())
            val code = iptStr.bufferedReader().use { it.readText() }
            action.load(key, code)
        }
    }

    var floatingParamsDirty: Boolean = false

    fun updateParams() {
        if (!floatingParamsDirty) return
        // 向主线程发送ui更新
        mainHandler?.post {
//            component.dialog.setContent("x: ${floatingParams.x}, y: ${floatingParams.y}")
            component.dialog.apply {
                updatePosition()
            }
            context?.update(floating, floatingParams)
        }
        floatingParamsDirty = false
    }

    fun inMain(block: Runnable) {
        mainHandler?.post(block)
    }

    fun isDragging(): Boolean {
        return context?.isDragging(floating) ?: false
    }

    /**
     * 销毁 Pet 并释放资源。
     *
     * 必须在主线程调用: DialogBubble.hidden() 内部通过 Handler 回到主线程,
     * 且它在主线程时是直接执行, 这样才能在 context 被置空前拿到 service 去销毁对话框悬浮窗。
     */
    fun dispose() {
        context?.destroy(floating)
        // 组件销毁/隐藏, 必须在 context 置空前调用, 否则对话框悬浮窗不会被销毁
        component.dialog.hidden()
        (view.parent as? ViewGroup)?.removeView(view)
        context = null
        view.dispose()

        animation.dispose()
        action.dispose()
        data.dispose()
    }
}

class PetInfo(// pet加载字段
    val path: String, val fromAsset: Boolean, info: JSONObject) {

    // pet信息字段
    val id: String = info.getString("id")
    val uuid: String = info.getString("uuid")
    var name: String = info.optString("name", "Unnamed")
    var desc: String = info.optString("desc", "...")
    var size: String = info.optString("size", "15dp")

    var motions: JSONObject = info.optJSONObject("motions") ?: JSONObject()

    var actions: JSONObject = info.optJSONObject("actions") ?: JSONObject()
    var defaultAction: String = actions.getString("default")

    /**
     * 该 Pet 的 data.json 存储路径, 优先外部存储。
     *
     * 不构造 Pet 实例也能定位数据文件(例如 PetManager 读取 enable 记录),
     * 与 [Pet] 构造时使用的路径保持一致。
     */
    fun dataPath(context: Context): String {
        val dir: String = context.getExternalFilesDir(null)?.path ?: context.filesDir.path
        return (Path(dir) / "data" / "pets" / uuid / "data.json").toString()
    }
}
