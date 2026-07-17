package com.cirno9half.touhoupet

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

class ActionController(val pet: Pet) {
    // 1. 创建单线程的 HandlerThread，确保所有 JS 操作和事件循环都在同一个线程（类似V8单线程）
    private val handlerThread = HandlerThread("Pet-ActionLoop-${pet.info.id}").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val dispatcher = handler.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private var quickJs: QuickJs? = null

    // 使用互斥锁保护 QuickJs 的并发调用（虽然都在同一个线程，但互斥锁能保证挂起函数的顺序安全）
    private val jsEvalMutex = Mutex()

    val actions = mutableSetOf<String>()
    var currentAction: String? = null

    // 控制事件循环是否正在运行的标志
    private var isLooping = false

    init {
        try {
            quickJs = QuickJs.create(dispatcher)
        } catch (e: Exception) {
            Log.e("Action-${pet.info.id}", "QuickJs初始化失败", e)
        }
    }

    suspend fun loadAPI() {
        val api = PetAPI(pet)
        quickJs?.let { api.buildAPI(it) }
    }

    /**
     * 执行一段 JS 代码，模仿 V8 引擎的 Microtask 检查机制。
     * 只有当 JS 代码及其内部所有的 Promise 全部 resolve 后，此挂起函数才会返回。
     */
    private suspend fun executeJs(code: String, filename: String, asModule: Boolean = false): Boolean {
        return jsEvalMutex.withLock {
            val js = quickJs ?: return@withLock false
            try {
                // 1. 投递并执行当前的 JS 脚本（宏任务/同步主干）
                js.evaluate<Any?>(code, filename, asModule)

                // 2. 核心模仿 V8：清空并执行由刚才的代码触发的所有 Promise 微任务队列
                // Dokar QuickJs 提供了内部的事件循环处理，使 await 能够真正等待完成
                // 注：根据具体底层的绑定库，部分版本在 evaluate 包含异步 IIFE 时会自动轮询，
                // 如果需要显式驱动异步任务，可在此处或通过绑定的 runtime 推进事件循环。
                pet.updateParams()

                true
            } catch (e: Exception) {
                Log.e("Action-${pet.info.id}", "JS Execution Error in $filename: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 加载 Action 模块
     */
    suspend fun load(id: String, code: String) {
        jsEvalMutex.withLock {
            quickJs?.addModule("action-$id", code)
        }
        val moduleCode = """
            import action from "action-$id";
            action.id = "$id";
            globalThis.Action = globalThis.Action ?? {};
            globalThis.Action["${pet.info.id}"] = globalThis.Action["${pet.info.id}"] ?? {};
            globalThis.Action["${pet.info.id}"]["$id"] = action;
        """.trimIndent()
        executeJs(moduleCode, filename = "ActionLoader", asModule = true)
        actions.add(id)
    }

    /**
     * 切换当前动作（支持异步 start 和 end 的等待）
     *
     * 由于有很大死锁风险, 因此不建议使用
     */
    suspend fun switchTo(id: String) {
        if (currentAction == id) return
        if (id !in this.actions) return

        val code = """await globalThis.Pet?.["${pet.info.id}"]?.api?.action?.switchTo?.("$id")"""

        if (executeJs(code, "ActionSwitcher", false)) {
            currentAction = id
            Log.i("Action-${pet.info.id}", "switch to $id")
        } else {
            Log.e("Action-${pet.info.id}", "failed to switch to $id")
        }
    }

    /**
     * 启动高效的事件循环
     */
    fun startLoop() {
        if (isLooping) return
        isLooping = true

        scope.launch {
            // 循环守卫：只要协程处于活动状态且未被 dispose
            while (isActive) {
                // 构造本次 Update 的执行代码
                val code = """
                    await (async () => {
                        try { 
                            globalThis.Action["${pet.info.id}"][globalThis.Pet["${pet.info.id}"].currentAction || globalThis.Pet["${pet.info.id}"].defaultAction]
                                ?.update?.(globalThis.Pet["${pet.info.id}"].api)
                        } catch(e) { 
                            console.error("Action " + globalThis.Pet["${pet.info.id}"].currentAction + " Update Error: " + e.message) 
                        }
                    })();
                """.trimIndent()

                // 这一步会挂起当前协程，直到 QuickJs 把里面的异步任务全部消化完
                executeJs(code, "ActionUpdate", false)
                delay(30)
            }
        }
    }

    /**
     * 执行事件回调（例如点击、触摸事件触发的 JS 回调）
     */
    fun execCallback(key: String) {
        val code = $$"""
            await (async () => {
                let callbacks = globalThis.Pet?.["$${pet.info.id}"].callbacks?.get("$$key");
                if (callbacks) {
                    for (let cb of callbacks) {
                        try {
                            if (cb && cb.func) {
                                await cb.func();
                                if (cb.once) {
                                    let idx = callbacks.indexOf(cb);
                                    callbacks.splice(idx, 1);
                                }
                            }
                        } catch(e) {
                            console.log(`Action Callback $$key Error: ${e.message}`)
                        }
                    }
                }
            })()
        """.trimIndent()

        scope.launch {
            executeJs(code, "ActionCallback", false)
        }
    }

    /**
     * 彻底销毁并释放资源
     */
    fun dispose() {
        isLooping = false
        actions.clear()
        // 1. 取消协程域，自动安全地中断 startLoop 里的 while(isActive) 循环
        scope.cancel()
        // 2. 清空队列中的所有未执行消息
        handler.removeCallbacksAndMessages(null)
        // 3. 在专属线程优雅关闭引擎并退出线程
        handler.post {
            try {
                quickJs?.close()
                quickJs = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handlerThread.quitSafely()
        }
    }
}