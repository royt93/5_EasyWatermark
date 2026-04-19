package com.mckimquyen.watermark.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Capture handwritten signature via Path and output as Bitmap.
 * Supports multiple strokes, colors, sizes, and neon glow effects.
 * @author roy.mobile.dev@gmail.com
 */
class SignatureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentPaint: Paint? = null
    
    var drawColor: Int = Color.WHITE
    var drawSize: Float = 10f
    var isGlowEnabled: Boolean = false
    var gradientColors: IntArray? = null // if non-null, size must be >= 2

    private var lastX = 0f
    private var lastY = 0f

    private fun generateCurrentPaint(): Paint {
        return Paint().apply {
            isAntiAlias = true
            color = drawColor
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = drawSize
            
            if (isGlowEnabled) {
                // simple blur glow effect
                maskFilter = BlurMaskFilter(drawSize * 1.5f, BlurMaskFilter.Blur.NORMAL)
            }
            if (gradientColors != null && gradientColors!!.size >= 2) {
                // simple top-to-bottom linear gradient (assuming signature height ~ 500f)
                // A better approach would map it dynamically or just diagonally
                shader = LinearGradient(0f, 0f, 1000f, 1000f, gradientColors!!, null, Shader.TileMode.CLAMP)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Prevent parent from scrolling when user is drawing
        parent?.requestDisallowInterceptTouchEvent(true)
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val newPath = Path()
                newPath.moveTo(x, y)
                currentPath = newPath
                currentPaint = generateCurrentPaint()
                strokes.add(Stroke(newPath, currentPaint!!))
                lastX = x
                lastY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(x - lastX)
                val dy = Math.abs(y - lastY)
                if (dx >= 4f || dy >= 4f) {
                    currentPath?.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y
                }
            }
            MotionEvent.ACTION_UP -> {
                currentPath?.lineTo(lastX, lastY)
                currentPath = null
                currentPaint = null
            }
            else -> return false
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in strokes) {
            canvas.drawPath(stroke.path, stroke.paint)
        }
    }

    fun clear() {
        strokes.clear()
        currentPath = null
        currentPaint = null
        invalidate()
    }

    /** Undo the last drawn stroke. Returns true if there was a stroke to remove. */
    fun undo(): Boolean {
        if (strokes.isEmpty()) return false
        strokes.removeAt(strokes.lastIndex)
        currentPath = null
        currentPaint = null
        invalidate()
        return true
    }

    fun hasStrokes(): Boolean = strokes.isNotEmpty()

    fun getSignatureBitmap(): Bitmap? {
        if (strokes.isEmpty()) return null
        
        val bounds = RectF()
        val pathBounds = RectF()
        var first = true
        for (stroke in strokes) {
            stroke.path.computeBounds(pathBounds, true)
            val padding = stroke.paint.strokeWidth * 2f
            pathBounds.inset(-padding, -padding)
            if (first) {
                bounds.set(pathBounds)
                first = false
            } else {
                bounds.union(pathBounds)
            }
        }
        
        bounds.intersect(0f, 0f, width.toFloat(), height.toFloat())
        val cropWidth = bounds.width().toInt()
        val cropHeight = bounds.height().toInt()
        
        if (cropWidth <= 0 || cropHeight <= 0) return null

        val maxEdge = Math.max(cropWidth, cropHeight)
        val targetEdge = 500f
        val scale = if (maxEdge < targetEdge) targetEdge / maxEdge else 1f

        val finalWidth = (cropWidth * scale).toInt()
        val finalHeight = (cropHeight * scale).toInt()

        val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        
        canvas.scale(scale, scale)
        canvas.translate(-bounds.left, -bounds.top)
        
        for (stroke in strokes) {
            canvas.drawPath(stroke.path, stroke.paint)
        }
        return bitmap
    }
}
