package com.cirno9half.touhoupet

import android.content.Context
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function

class PetAPI(private val pet: Pet) {
    // 获取信息
    fun getName() = pet.info.name
    fun getDesc() = pet.info.desc
//    fun getPetPath() = pet.info.path

    fun getAlpha() = pet.alpha
    fun setAlpha(a: Float) { pet.alpha = a }
    fun getWidth() = pet.view.width
    fun getHeight() = pet.view.height
    fun getSize() = pet.view.width to pet.view.height

    suspend fun buildAPI(quickjs: QuickJs) {
        // 注入控制台
        quickjs.define("console") {
            function("log") { args -> Log.i("JS-${pet.info.id}", args.joinToString(" ")) }
            function("warn") { args -> Log.w("JS-${pet.info.id}", args.joinToString(" ")) }
            function("error") { args -> Log.e("JS-${pet.info.id}", args.joinToString(" ")) }
            function("info") { args -> Log.i("JS-${pet.info.id}", args.joinToString(" ")) }
            function("debug") { args -> Log.d("JS-${pet.info.id}", args.joinToString(" ")) }
        }

        quickjs.define("___tmpAPI") {
            function("device_getWidth") { pet.context?.resources?.displayMetrics?.widthPixels ?: 0 }
            function("device_getHeight") { pet.context?.resources?.displayMetrics?.heightPixels ?: 0}

            function("animation_switchTo") { args -> pet.animation.switchTo(args[0] as String) }
            function("animation_pause") { pet.animation.paused = true }
            function("animation_resume") { pet.animation.paused = false }
//            asyncFunction("action_switchTo") { args -> pet.action.switchTo(args[0] as String) }

            function("getName") { getName() }
            function("getId") { pet.info.id }
            function("getDesc") { getDesc() }
//            function("getPath") { getPetPath() }

            function("getX") { pet.x }
            function("setX") { args ->
                pet.x = (args[0] as Number).toInt()
            }
            function("getY") { pet.y }
            function("setY") { args ->
                pet.y = (args[0] as Number).toInt()
            }
            function("setPos") { args -> pet.pos = Pair((args[0] as Number).toInt(), (args[1] as Number).toInt()) }

            function("state_getDragging") {
                return@function pet.isDragging()
            }

            function("view_getAlpha") { getAlpha() }
            function("view_setAlpha") { args ->
                setAlpha((args[0] as Number).toFloat())
                pet.floatingParamsDirty = true
            }
            function("view_getWidth") { getWidth() }
            function("view_getHeight") { getHeight() }
            function("view_getSize") { getSize() }

            function("data_set") { args ->
                val k = args[0]
                if (k !is String) return@function false // 设置失败返回false
                val v = args[1]
                pet.data.set(k, v)
                pet.data.save()
            }
            function("data_get") { args ->
                val k = args[0]
                if (k !is String) return@function false
                val fb = args[1]
                return@function pet.data.get(k, fb)
            }
            function("data_has") { args ->
                val k = args[0]
                if (k !is String) return@function false
                return@function pet.data.has(k)
            }
            function("data_delete") { args ->
                val k = args[0]
                if (k !is String) return@function false
                return@function pet.data.delete(k)
            }

            function("sensor_touchEdge") {
                val ctx = pet.context ?: return@function "none"
                val dm = ctx.resources.displayMetrics
                val screenW = dm.widthPixels
                val statusH = ctx.statusBarHeight
                val navH = ctx.navigationBarHeight
                val viewW = pet.view.width
                val viewH = pet.view.height

                val px = pet.x
                val py = pet.y

                // 本地坐标系, 与 FloatingService.fixPos 一致:
                //   竖屏: y下边界 = screenH - navH - statusH
                //   横屏: y下边界 = screenH - statusH (导航栏在侧边)
                val isLandscape = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val bottomBoundary = if (isLandscape) {
                    dm.heightPixels
                } else {
                    dm.heightPixels - statusH - navH
                }

                val atLeft   = px <= 5
                val atRight  = px + viewW >= screenW - 5
                val atTop    = py <= 5
                val atBottom = py + viewH >= bottomBoundary - 5

                val edges = buildString {
                    if (atTop)    append("top-")
                    if (atBottom) append("bottom-")
                    if (atLeft)   append("left-")
                    if (atRight)  append("right-")
                }
                return@function edges.removeSuffix("-").ifEmpty { "none" }
            }

        }
        quickjs.evaluate<Any?>("""
            (function(bridge) {
                globalThis.Pet = globalThis.Pet ?? {};
                
                const petId = "${pet.info.id}";
                globalThis.Pet[petId] = globalThis.Pet[petId] ?? {
                    defaultAction: "${pet.info.defaultAction}",
                    currentAction: "${pet.info.defaultAction}",
                    callbacks: new Map([
                        ["click", []]
                    ])
                };
                
                
                globalThis.device = {
                    get width() { device_getWidth() },
                    get height() { device_getHeight() }
                };
                
                // 在局部用闭包安全的绑定属性
                globalThis.Pet[petId].api = {
                    get name() { return bridge.getName() },
                    get id() { return bridge.getId() },
                    get desc() { return bridge.getDesc() },
                
                    get x() { return bridge.getX(); },
                    set x(v) { bridge.setX(v); },
                    get y() { return bridge.getY(); },
                    set y(v) { bridge.setY(v); },
                    
                    move(x = 0, y = 0) { bridge.setPos(this.x + x, this.y + y); }, 
                                       
                    animation: {
                        switchTo(id) { 
                            bridge.animation_switchTo(id)
                            return {
                                onEnd(callback) { // 在切换到动画id后, 为该id添加回调
                                    if (!globalThis.Pet[petId].callbacks.has("animation_end_" + id)) {
                                        globalThis.Pet[petId].callbacks.set("animation_end_" + id, [])
                                    }
                                    let cbs = globalThis.Pet[petId].callbacks.get("animation_end_" + id)
                                    cbs.push({ func: callback, once: true }) // 只执行一次, 防止start中重复添加
                                }
                            }
                        }
                    },
                    action: {
                        async switchTo(id) {
                            let __petCtx = globalThis.Pet[petId];
                            let __actionMap = globalThis.Action[petId];
                            let __oldAction = __actionMap?.[__petCtx.currentAction];
                            let __newAction = __actionMap?.[id];
                            let __api = __petCtx.api;
                            if (!__newAction || !__api) return;
                            try { await __oldAction?.end?.(__api); } catch(e) { console.error("Action " + __petCtx.currentAction + " End Error: " + e.message); }
                            try { await __newAction?.start?.(__api); } catch(e) { console.error("Action " + id + " Start Error: " + e.message); }
                            __petCtx.currentAction = id;
                        }
                    },
                    sensor: {
                        touchEdge() { return bridge.sensor_touchEdge() }
                    },
                    state: {
                        get dragging() { bridge.state_getDragging() }
                    },
                    
                    view: {
                        get alpha() { return bridge.view_getAlpha() },
                        set alpha(v) { return bridge.view_setAlpha(v) },
                        get width() { return bridge.view_getWidth() },
                        get height() { return bridge.view_getHeight() }
                    },
                    data: {
                        get(k, c) { return bridge.data_get(k, c) },
                        set(k, v) { return bridge.data_set(k, v) },
                        has(k) { return bridge.data_has(k) },
                        delete(k) { return bridge.data_delete(k) }
                    }
                };
            })(globalThis.___tmpAPI);

            delete globalThis.___tmpAPI;
            """.trimIndent(), "PetAPIBuild"
        )
    }
}
