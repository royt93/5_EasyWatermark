package com.mckimquyen.watermark.ui.widget

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toBitmap
import com.mckimquyen.watermark.utils.ktx.colorPrimary
import com.mckimquyen.watermark.utils.ktx.colorSecondary
import com.mckimquyen.watermark.utils.ktx.colorTertiary

class ColoredImageVIew : AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private var refreshRate: Float = 60F
    internal var sizeHasChanged: Boolean = true
    private val paint by lazy { Paint() }
    private var enable = true

    private val colorList = arrayOf(
        context.colorPrimary,
        context.colorSecondary,
        context.colorTertiary,
        context.colorTertiary
    ).toIntArray()

    private val posList = arrayOf(0f, 0.5f, 0.7f, 0.99f).toFloatArray()

    private val xfermode by lazy { PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }

    private val colorAnimator by lazy {
        ObjectAnimator.ofFloat(1f, 0.1f)
            .apply {
                addUpdateListener {
                    val pos = (it.animatedValue as Float)
                    val shader = LinearGradient(
                        (1.1f - pos) * width.toFloat() * 2f,
                        pos * height.toFloat(),
                        0f,
                        height.toFloat(),
                        colorList,
                        posList,
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = shader
                    postInvalidateDelayed((1000 / refreshRate).toLong())
                }
                duration = 2500
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
    }

    private var innerBitmap: Bitmap? = null

    init {
        val displayManager: DisplayManager =
            context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        refreshRate = displayManager.displays?.getOrNull(0)?.refreshRate ?: 60F
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // BUG-42: cùng lỗi copy-paste với CircleImageView — so w với oldh thay vì oldw.
        sizeHasChanged = w != oldw || h != oldh
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        if (innerBitmap == null || (sizeHasChanged && width > 0 && height > 0)) {
            super.onDraw(canvas)
        }
        if (measuredWidth + measuredHeight <= 0) {
            return
        }
        // BUG-AUDIT-2026-09-29: trước đây tạo bitmap mới MỖI lần onDraw kể cả khi chỉ shader
        // đổi màu (không đổi ảnh nguồn) — colorAnimator repeatCount=INFINITE gọi
        // postInvalidateDelayed liên tục nên onDraw chạy liên tục, mỗi lần cấp phát 1 bitmap
        // full-size mới và bỏ rơi bản cũ không recycle -> GC churn nặng/rủi ro OOM khi
        // animation chạy lâu. Chỉ tạo lại khi thật sự cần (chưa có hoặc đổi size).
        if (innerBitmap == null || sizeHasChanged) {
            val old = innerBitmap
            innerBitmap = drawable.toBitmap(measuredWidth, measuredHeight)
            if (old != null && !old.isRecycled && old !== innerBitmap) {
                old.recycle()
            }
            sizeHasChanged = false
        }
        innerBitmap?.let {
            val sc = canvas?.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) ?: return
            canvas.drawBitmap(it, 0f, 0f, paint)
            paint.xfermode = xfermode
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.xfermode = null
            canvas.restoreToCount(sc)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (enable) {
            colorAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // BUG-AUDIT-2026-09-29: pause() giữ animator INFINITE trong AnimationHandler (vẫn giữ
        // tham chiếu View/Context qua addUpdateListener) tới khi resume/cancel — nếu view không
        // bao giờ re-attach (Activity/Fragment host bị huỷ), đây là leak vĩnh viễn. cancel() giải
        // phóng hẳn; onAttachedToWindow đã tự start() lại từ đầu khi re-attach nên không mất gì.
        colorAnimator.cancel()
    }

    fun start() {
        enable = true
        colorAnimator.start()
    }

    fun stop() {
        enable = false
        colorAnimator.cancel()
    }
}
