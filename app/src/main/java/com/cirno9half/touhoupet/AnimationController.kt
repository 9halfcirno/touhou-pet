package com.cirno9half.touhoupet

import android.util.Log
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

class AnimationController(pet: Pet) {
    private val handler = Handler(Looper.getMainLooper())
    private val animations = mutableMapOf<String, AnimationData>()
    private var currentAnim: AnimationData? = null
    var nowFrame: Int = 0

    var finished: Boolean = false

    private var pet: Pet? = pet
    var paused: Boolean = false
        set(value) {
            field = value
            if (value) {
                stopLoop() // 暂停时清除时钟
            } else if (pet != null) {
                startLoop() // 恢复时重新启动循环
            }
        }

    fun startLoop() {
        if (pet == null || paused || currentAnim == null) return
        handler.removeCallbacks(loopRunnable) // 先防重复提交
        handler.post(loopRunnable)
    }

    fun stopLoop() {
        handler.removeCallbacks(loopRunnable)
    }

    fun onCreate() {
        startLoop()
    }

    fun dispose() {
        stopLoop()
        pet = null
    }

    fun loadAnimation(anim: AnimationData) {
        animations[anim.id] = anim
        if (currentAnim == null) {
            switchTo(anim.id)
        }

        if (anim.loop && anim.next != null) {
            Log.w("AnimationController", "动画\"${anim.id}\"具有冲突的属性 loop 和 next, 将使用next")
        }
    }

    /**
     * 当前动画是否已播放到最后一帧
     * 供 ActionController 的 JS 动作脚本通过 pet.animation.isLastFrame 只读
     */
    val isLastFrame: Boolean
        get() {
            val anim = currentAnim ?: return false
            return nowFrame >= anim.frame_num
        }

    fun switchTo(id: String) {
        val anim = animations[id]
        anim ?: run {
            Log.e("AnimationController", "未找到指定动画数据: \"$id\"喵")
            return
        }
//        pet?.action?.execCallback("animation_end_$") // 执行回调, 以进行清理
        this.currentAnim = anim
        this.nowFrame = 0
        finished = false

        startLoop() // 切换动画后，立马根据新动画开始
    }

    /**
     * 抽离出来的动画核心数据更新和渲染逻辑
     * @return 返回当前帧的停留间隔时间（ms），供 Handler 决定下一帧延迟多久
     */
    private fun update(): Int {
        val pet = pet ?: return -1
        var anim = currentAnim ?: return -1

        // 1. 检查越界：判定当前帧是否已经播完了当前动画的所有帧喵
        if (nowFrame >= anim.frame_num) {
            val next = anim.next
            finished = true
            pet.action.execCallback("animation_end_${anim.id}") // 执行回调
            if (next != null) {
                switchTo(next)
                anim = currentAnim ?: return -1 // 成功切换到下一个串联动画喵
            } else if (anim.loop) {
                nowFrame = 0 // 循环播放，回到第一帧喵
            } else {
                return -1 // 动画播完了，也没有后续了，告诉循环可以歇着了喵
            }
        }

        // 2. 核心渲染驱动：通知宠物 View 去读取并更新图片喵！
        // 这里的 path 我们用更加规范的路径拼接，去掉结尾可能多余的斜杠喵
        val imagePath = "${anim.path.removeSuffix("/")}/${nowFrame}.png"
        pet.view.updateBitmap(imagePath, pet.info.fromAsset)

        // 3. 递增到下一帧喵
        nowFrame++

        // 4. 返回当前动画定义的帧间隔时间喵
        return anim.interval
    }

    // 驱动时钟的匿名 Runnable 喵
    private val loopRunnable = object : Runnable {
        override fun run() {
            if (paused || this@AnimationController.pet == null) return

            // 🌟 调用你要求的 update 函数，驱动状态改变并获取当前帧耗时
            val interval = update()

            // 如果返回有效的间隔毫秒数，就继续定时下一次循环喵！
            if (interval >= 0) {
                handler.postDelayed(this, interval.toLong())
            }
        }
    }
}

class AnimationData(var path: String, json: JSONObject) {
    var id: String = json.getString("id")
    // 使用 optString 允许这些次要属性在 JSON 里不配置而不崩溃喵
    var name: String? = json.optString("name", "Unnamed")
    var frame_num: Int = json.optInt("frame_num", 1)
    var interval: Int = json.optInt("interval", 100)
    var loop: Boolean = json.optBoolean("loop", true)
    // 只有当 JSON 确实有 "next" 键且不是 null 的时候才捞它，否则给 null 喵
    var next: String? = if (json.has("next") && !json.isNull("next")) json.getString("next") else null
}