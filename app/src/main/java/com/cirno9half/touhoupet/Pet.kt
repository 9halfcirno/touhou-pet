package com.cirno9half.touhoupet

import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
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
        cPx(info.size).toInt(), cPx(info.size).toInt(),
        if (Build.VERSION.SDK_INT >= 26) 2038 else 2002,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT)

    val view: PetView = PetView(context!!)

    private var mainHandler: Handler? = Handler(Looper.getMainLooper())


    val animation = AnimationController(this)
    val action = ActionController(this)

    init {
//        viewGroup.addView(view) // 在ViewGroup中添加PetView
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

        // 测试部分
        val textView = TextView(context).apply {
            text = "Test"
        }
        val params = WindowManager.LayoutParams(
            cPx(info.size).toInt(), cPx(info.size).toInt(),
            if (Build.VERSION.SDK_INT >= 26) 2038 else 2002,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)
        val textId = context!!.create(textView, params)
        params.apply {
            val dm = context!!.resources.displayMetrics
            x = (dm.widthPixels - width) / 2
            y = (dm.heightPixels - height) / 2
        }
        textId.let {
            context?.update(textId, params)
            context?.setDraggable(it, true)
        }
        // 测试部分

        // 获取数据存储路径, 优先尝试Extra路径
        val dir: String = context!!.getExternalFilesDir(null)?.path ?: context!!.filesDir.path
        val dataPath = Path(dir) / "data" / "pets"  / info.uuid / "data.json"
        data = DataManager(dataPath.toString())
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
            context?.update(floating, floatingParams)
        }
        floatingParamsDirty = false
    }

    // x, y, alpha获取方法
    var x: Int
        get() = floatingParams.x
        set(v) {
            floatingParams.x = v
            floatingParamsDirty = true
            updateParams()
        }
    var y: Int
        get() = floatingParams.y
        set(v) {
            floatingParams.y = v
            floatingParamsDirty = true
            updateParams()
        }
    var pos: Pair<Int, Int>
        get() = Pair(x, y)
        set(value) {
            floatingParams.x = value.first
            floatingParams.y = value.second
            floatingParamsDirty = true
            updateParams()
        }
    var alpha: Float
        get() = floatingParams.alpha
        set(v) {
            floatingParams.alpha = v
            floatingParamsDirty = true
            updateParams()
        }
    var size: String
        get() = info.size
        set(v) {
            info.size = v
            val px = cPx(v).toInt()
            floatingParams.width = px
            floatingParams.height = px
            floatingParamsDirty = true
            updateParams()
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

fun cPx(size: String): Number {
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

