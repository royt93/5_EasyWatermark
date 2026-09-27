package com.mckimquyen.watermark.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.mckimquyen.watermark.data.model.RedactionSuggestion
import kotlin.math.min

/**
 * IDEA-14: hiển thị bitmap fit-center + khoanh vùng đề xuất che (email/SĐT/mặt người), cho phép
 * user TAP để bật/tắt từng vùng — KHÔNG hỗ trợ pan/zoom/vẽ tay (quyết định đã chốt, xem plan ticket),
 * đơn giản hơn nhiều so với [CropOverlayView] (không cần [android.view.ScaleGestureDetector]).
 */
class RedactionOverlayView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var bitmap: Bitmap? = null
    private var suggestions: List<RedactionSuggestion> = emptyList()
    private val displayMatrix = Matrix()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val confirmedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 2f
        color = Color.RED
    }
    private val confirmedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 255, 0, 0)
    }
    private val dismissedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1.5f
        color = Color.argb(180, 200, 200, 200)
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    /** Danh sách hiện tại (đọc lúc bấm Apply) — bản COPY để caller không mutate ngược lại view. */
    val currentSuggestions: List<RedactionSuggestion> get() = suggestions

    fun setImageBitmap(newBitmap: Bitmap) {
        bitmap = newBitmap
        if (width > 0 && height > 0) resetMatrix()
        invalidate()
    }

    fun setSuggestions(newSuggestions: List<RedactionSuggestion>) {
        suggestions = newSuggestions
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetMatrix()
    }

    private fun resetMatrix() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        displayMatrix.reset()
        val fitScale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        displayMatrix.postScale(fitScale, fitScale)
        displayMatrix.postTranslate(
            (width - bmp.width * fitScale) / 2f,
            (height - bmp.height * fitScale) / 2f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, displayMatrix, bitmapPaint)

        val viewRect = RectF()
        for (suggestion in suggestions) {
            viewRect.set(
                suggestion.rect.left * bmp.width,
                suggestion.rect.top * bmp.height,
                suggestion.rect.right * bmp.width,
                suggestion.rect.bottom * bmp.height
            )
            displayMatrix.mapRect(viewRect)
            if (suggestion.confirmed) {
                canvas.drawRect(viewRect, confirmedFill)
                canvas.drawRect(viewRect, confirmedStroke)
            } else {
                canvas.drawRect(viewRect, dismissedStroke)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val bmp = bitmap ?: return true
        val inverse = Matrix()
        if (!displayMatrix.invert(inverse)) return true

        val point = floatArrayOf(event.x, event.y)
        inverse.mapPoints(point)
        val xNorm = point[0] / bmp.width
        val yNorm = point[1] / bmp.height

        val boxes = suggestions.map {
            HitBox(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom)
        }
        val hitIndex = findSuggestionAt(boxes, xNorm, yNorm)
        if (hitIndex >= 0) {
            suggestions = suggestions.toMutableList().apply {
                this[hitIndex] = this[hitIndex].copy(confirmed = !this[hitIndex].confirmed)
            }
            invalidate()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bitmap?.let { if (!it.isRecycled) it.recycle() }
        bitmap = null
    }

    /** Struct thuần (không [RectF]) — [RectF] là stub rỗng trong plain JUnit (không Robolectric), constructor không gán field thật. */
    internal data class HitBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    }

    companion object {
        /**
         * Hàm thuần, test trực tiếp không cần Robolectric. Duyệt NGƯỢC (cuối danh sách trước) để ưu
         * tiên vùng vẽ SAU CÙNG (trên cùng về mặt hiển thị) khi 2 vùng chồng lấn nhau.
         */
        internal fun findSuggestionAt(boxes: List<HitBox>, xNorm: Float, yNorm: Float): Int {
            for (i in boxes.indices.reversed()) {
                if (boxes[i].contains(xNorm, yNorm)) return i
            }
            return -1
        }
    }
}
