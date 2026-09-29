package com.cirno9half.touhoupet.component

import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.cirno9half.touhoupet.Pet
import com.cirno9half.touhoupet.dp2px
import androidx.core.graphics.toColorInt

class DialogBubble(pet: Pet) : FloatingComponent(pet) {
    override val view = ConstraintLayout(pet.context!!).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    var maxHeight: Int = 400
    var maxWidth: Int = dp2px("200dp").toInt().coerceAtLeast((pet.view.width * 1.5).toInt())

    val params = WindowManager.LayoutParams(
        maxWidth, maxHeight,
//        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,//maxWidth, maxHeight,
        // 神秘AI硬编码常量
        if (Build.VERSION.SDK_INT >= 26) 2038 else 2002,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )
    var floating: Int? = null

    lateinit var scrollView: ScrollView
    lateinit var textView: TextView

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 文本镜像。
     *
     * TextView 只允许在主线程读写。而 JS 桥接 (PetAPI.dialog_*) 跑在 QuickJS 的
     * HandlerThread 上, 跨线程直接 append/setText 会与主线程的 layout/draw 抢同一个
     * Spannable, 导致 DynamicLayout 行表越界崩溃:
     *   PackedIntVector.getValue <- DynamicLayout.getLineTop <- Layout.getLineBottom
     *   <- Editor.drawHardwareAcceleratedInner
     * 因此: 所有 TextView 写入统一 post 到主线程, 其它线程一律读这个镜像。
     */
    @Volatile
    private var textMirror: String = ""

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    init {
        pet.context?.let {
            scrollView = ScrollView(it)
//            scrollView.setPadding(5, 5, 5, 5)
            scrollView.background = GradientDrawable().apply {
                setColor("#88ffffff".toColorInt())
//                alpha = 128
                cornerRadius = 10f
                setStroke(2, "#aaffaa".toColorInt())
            }
            val scrollParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            ).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID

                matchConstraintDefaultHeight = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_WRAP
                matchConstraintMaxHeight = maxHeight
            }
            scrollView.layoutParams = scrollParams
            textView = TextView(it) // 文本
            textView.apply {
                text = "Test"
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                gravity = Gravity.CENTER_HORIZONTAL
                maxWidth = this@DialogBubble.maxWidth
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(20, 20, 20, 20)
                setTextColor("#aaaaaa".toColorInt())

            }
            textMirror = "Test" // 与上面 textView 的初始文本保持一致
            scrollView.addView(textView)
            view.addView(scrollView)
        }
    }

    override fun show() {
        onMain {
            pet.context?.let {
                floating = it.create(view, params)
                visibility = true
                floating?.let { id -> it.setDraggable(id, false) }
            }
            updatePosition()
        }
    }

    override fun hidden() {
        onMain {
            floating?.let {
                pet.context?.destroy(it)
                visibility = false
                floating = null
            }
        }
    }

    private fun setupFloatingSize() {

    }

    /**
     * 向对话框追加文本
     */
    fun appendContent(str: String) {
        val newText = textMirror + str
        textMirror = newText
        onMain {
            textView.text = newText
            scrollBottom()
        }
    }

    fun clearContent() {
        textMirror = ""
        onMain { textView.text = "" }
    }

    var length: Int
        get() = textMirror.length
        set(value) {
            // 原实现把 take 的结果丢掉了 (`?: ""` 从未赋回), 是空操作, 这里让它真正生效
            content = textMirror.take(value)
        }

    var content: String
        get() = textMirror
        set(value) {
            textMirror = value
            onMain {
                textView.text = value
                scrollBottom()
            }
        }

    private fun scrollBottom() {
        val c = scrollView.getChildAt(0)
        scrollView.smoothScrollTo(0, c.height - scrollView.height)
    }

    override fun updateParams(params: WindowManager.LayoutParams) {
        floating?.let { pet.context?.update(it, params) }
    }

    override fun updatePosition() {
        val petX = pet.floatingParams.x
        val petY = pet.floatingParams.y
        params.x = petX - (view.layoutParams.width - pet.floatingParams.width) / 2
        val height = view.layoutParams.height + 15
        params.y = petY + if (petY < height) 15 + pet.floatingParams.height else -height
//        params.y = petY - height
        floating?.let { pet.context?.update(it, params) }
    }
}