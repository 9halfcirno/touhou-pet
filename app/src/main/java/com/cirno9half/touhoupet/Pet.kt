package com.cirno9half.touhoupet

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
        val dir: String = context!!.getExternalFilesDir(null)?.path ?: context!!.filesDir.path
        val dataPath = Path(dir) / "data" / "pets"  / info.uuid / "data.json"
        data = DataManager(dataPath.toString())

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

    fun dispose() {
        context?.destroy(floating)
        context = null
        animation.dispose()
        action.dispose()
        data.dispose()
        // 组件销毁/隐藏
        component.dialog.hidden()
        (view.parent as? ViewGroup)?.removeView(view)
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

}
