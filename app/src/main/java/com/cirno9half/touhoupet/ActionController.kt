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

    // 预编译字节码缓存。只在对应 quickJs 实例存活期间有效:
    // QuickJS 字节码与运行时/引擎版本绑定, 既不能跨实例复用, 也不能持久化到磁盘
    private val bytecodeCache = mutableMapOf<String, ByteArray>()

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
     *
     * [asModule] 默认 true: 按 ES module 编译。本类中三处高频脚本都在顶层使用 await,
     * 只有 module 语义下 await 才是关键字(普通 script 下会被当成一个普通标识符调用)。
     */
    private suspend fun executeJs(code: String, filename: String, asModule: Boolean = true): Boolean {
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
     * 执行预编译好的字节码。与 [executeJs] 语义一致, 但省掉了 QuickJS 侧每帧重新 parse 源码。
     */
    private suspend fun executeBytecode(bytecode: ByteArray, filename: String): Boolean {
        return jsEvalMutex.withLock {
            val js = quickJs ?: return@withLock false
            try {
                js.evaluate<Any?>(bytecode)
                pet.updateParams()
                true
            } catch (e: Exception) {
                Log.e("Action-${pet.info.id}", "JS Bytecode Error in $filename: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 把源码编译为 QuickJS 字节码, 编译参数必须与 [executeJs] 保持一致。
     *
     * 只能在 [dispatcher] 线程上调用: QuickJs 的 runtime 不是线程安全的, 编译与执行共用同一实例。
     * 编译失败返回 null, 调用方需回退到源码执行路径。
     */
    private fun compileBytecode(code: String, filename: String, asModule: Boolean = true): ByteArray? {
        val js = quickJs ?: return null
        return try {
            js.compile(code, filename, asModule).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w("Action-${pet.info.id}", "编译字节码失败, 回退到源码执行 ($filename): ${e.message}")
            null
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

        if (executeJs(code, "ActionSwitcher", asModule = true)) {
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
            // 循环体脚本只和 petId 有关, 是常量:
            // 提到循环外构造并预编译成字节码, 避免每帧重拼字符串(含 trimIndent)和重新 parse
            val tickCode = """
                await (async () => {
                    try { 
                        globalThis.Action["${pet.info.id}"][globalThis.Pet["${pet.info.id}"].currentAction || globalThis.Pet["${pet.info.id}"].defaultAction]
                            ?.update?.(globalThis.Pet["${pet.info.id}"].api)
                    } catch(e) { 
                        console.error("Action " + globalThis.Pet["${pet.info.id}"].currentAction + " Update Error: " + e.message) 
                    }
                })();
            """.trimIndent()
            // 编译失败时回退到源码路径, 不让循环停摆
            val tickBytecode = compileBytecode(tickCode, TICK_FILENAME)

            // 循环守卫：只要协程处于活动状态且未被 dispose
            while (isActive) {
                // 这一步会挂起当前协程，直到 QuickJs 把里面的异步任务全部消化完
                if (tickBytecode != null) {
                    executeBytecode(tickBytecode, TICK_FILENAME)
                } else {
                    executeJs(tickCode, TICK_FILENAME, asModule = true)
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * 执行事件回调（例如点击、触摸事件触发的 JS 回调）
     */
    fun execCallback(key: String) {
        scope.launch {
            // 回调脚本只由 key 决定, 按 key 缓存字节码, 避免每次事件都重新拼模板 + 重新 parse
            bytecodeCache[key]?.let {
                executeBytecode(it, CALLBACK_FILENAME)
                return@launch
            }

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

            val bytecode = compileBytecode(code, CALLBACK_FILENAME)
            if (bytecode == null) {
                // 编译失败, 回退到源码执行
                executeJs(code, CALLBACK_FILENAME, asModule = true)
                return@launch
            }
            if (bytecodeCache.size >= BYTECODE_CACHE_LIMIT) bytecodeCache.clear()
            bytecodeCache[key] = bytecode
            executeBytecode(bytecode, CALLBACK_FILENAME)
        }
    }

    /**
     * 彻底销毁并释放资源
     */
    fun dispose() {
        isLooping = false
        actions.clear()
        // 字节码与 quickJs 实例同生共死, 实例销毁后必须丢弃
        bytecodeCache.clear()
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

    private companion object {
        /** 事件循环的 tick 间隔(ms) */
        const val TICK_INTERVAL_MS = 30L

        const val TICK_FILENAME = "ActionUpdate"
        const val CALLBACK_FILENAME = "ActionCallback"

        /** 回调字节码缓存上限。回调 key 数量本身有限(click / animation_end_*), 这里仅作防御 */
        const val BYTECODE_CACHE_LIMIT = 128
    }
}