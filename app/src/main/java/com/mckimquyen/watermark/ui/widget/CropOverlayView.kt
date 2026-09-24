package com.mckimquyen.watermark.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.mckimquyen.watermark.utils.bitmap.applyCropAndRotate
import kotlin.math.max
import kotlin.math.min

/**
 * FEAT-16: canvas crop/straighten — thiết kế "pan/zoom ẢNH sau khung crop CỐ ĐỊNH theo tỉ lệ"
 * (chip Free/1:1/4:5/16:9/9:16/3:4), KHÔNG kéo-góc tự do — tái dùng cấu trúc
 * [ScaleGestureDetector] + pan theo delta chạm y hệt [WaterMarkImageView], chỉ khác đối tượng
 * biến đổi (ma trận hiển thị ảnh thay vì offset watermark).
 *
 * [ratio] null = "Free" = không crop (frame/scrim ẩn, không cho pan/zoom, chỉ hiển thị xem trước
 * ảnh đã xoay). Đổi tỉ lệ hoặc góc xoay RESET pan/zoom về vị trí center-crop mặc định — đơn giản
 * hoá có chủ đích, không cố giữ pan cũ qua các lần đổi khung (ponytail: đủ dùng cho AC hiện tại,
 * nâng cấp giữ pan nếu sau này cần UX mượt hơn).
 */
class CropOverlayView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var sourceBitmap: Bitmap? = null
    private var rotatedBitmap: Bitmap? = null
    private var rotationDegrees: Float = 0f
    private var ratio: Float? = null

    private val displayMatrix = Matrix()
    private val frameRect = RectF()

    /** Scale nhỏ nhất để [rotatedBitmap] phủ kín [frameRect] (center-crop fit) — mốc clamp zoom. */
    private var coverScale = 1f

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
    private val frameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 2f
        color = Color.WHITE
    }
    private val gridPaint = Paint().apply {
        strokeWidth = context.resources.displayMetrics.density
        color = Color.argb(90, 255, 255, 255)
    }

    // BUG phát hiện qua unit test: View.postDelayed()/removeCallbacks() khi View CHƯA attach vào
    // window (vd test đo lường/layout thủ công, không add vào Activity thật) chỉ xếp hàng chờ
    // attach rồi mới chạy — không bao giờ tới nếu View không thực sự attach. Dùng Handler riêng
    // gắn thẳng Looper.getMainLooper() để debounce hoạt động nhất quán dù đã attach hay chưa.
    private val rotateHandler = Handler(Looper.getMainLooper())
    private var rotateRunnable: Runnable? = null

    fun setImageBitmap(bitmap: Bitmap) {
        sourceBitmap = bitmap
        rotationDegrees = 0f
        rotatedBitmap = bitmap
        if (width > 0 && height > 0) resetFrameAndMatrix()
        invalidate()
    }

    fun setAspectRatio(newRatio: Float?) {
        ratio = newRatio
        resetFrameAndMatrix()
        invalidate()
    }

    /** Debounce nhẹ vì mỗi lần đổi góc phải tạo lại bitmap đã xoay (không rẻ như đổi 1 con số). */
    fun setRotationDegrees(degrees: Float) {
        rotateRunnable?.let { rotateHandler.removeCallbacks(it) }
        val runnable = Runnable { applyRotation(degrees) }
        rotateRunnable = runnable
        rotateHandler.postDelayed(runnable, ROTATE_DEBOUNCE_MS)
    }

    fun computeCropRect(): RectF? {
        val bmp = rotatedBitmap ?: return null
        if (ratio == null) return null
        val inverse = Matrix()
        if (!displayMatrix.invert(inverse)) return null
        val bitmapSpaceFrame = RectF(frameRect)
        inverse.mapRect(bitmapSpaceFrame)
        return RectF(
            (bitmapSpaceFrame.left / bmp.width).coerceIn(0f, 1f),
            (bitmapSpaceFrame.top / bmp.height).coerceIn(0f, 1f),
            (bitmapSpaceFrame.right / bmp.width).coerceIn(0f, 1f),
            (bitmapSpaceFrame.bottom / bmp.height).coerceIn(0f, 1f)
        )
    }

    fun computeRotationDegrees(): Float = rotationDegrees

    private fun applyRotation(degrees: Float) {
        val src = sourceBitmap ?: return
        rotationDegrees = degrees
        val previousRotated = rotatedBitmap
        val newRotated = applyCropAndRotate(src, degrees, null)
        rotatedBitmap = newRotated
        if (previousRotated != null && previousRotated !== src && previousRotated !== newRotated && !previousRotated.isRecycled) {
            previousRotated.recycle()
        }
        resetFrameAndMatrix()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetFrameAndMatrix()
    }

    private fun resetFrameAndMatrix() {
        val bmp = rotatedBitmap ?: return
        if (width == 0 || height == 0) return
        val currentRatio = ratio
        displayMatrix.reset()
        if (currentRatio == null) {
            frameRect.setEmpty()
            val fitScale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            coverScale = fitScale
            displayMatrix.postScale(fitScale, fitScale)
            displayMatrix.postTranslate(
                (width - bmp.width * fitScale) / 2f,
                (height - bmp.height * fitScale) / 2f
            )
            return
        }
        val padding = FRAME_PADDING_DP * context.resources.displayMetrics.density
        val maxFrameWidth = width - padding * 2
        val maxFrameHeight = height - padding * 2
        var frameWidth = maxFrameWidth
        var frameHeight = frameWidth / currentRatio
        if (frameHeight > maxFrameHeight) {
            frameHeight = maxFrameHeight
            frameWidth = frameHeight * currentRatio
        }
        val left = (width - frameWidth) / 2f
        val top = (height - frameHeight) / 2f
        frameRect.set(left, top, left + frameWidth, top + frameHeight)

        val scale = coverScaleForRotatedContent(frameRect.width(), frameRect.height())
        coverScale = scale
        displayMatrix.postScale(scale, scale)
        displayMatrix.postTranslate(
            frameRect.left - (bmp.width * scale - frameRect.width()) / 2f,
            frameRect.top - (bmp.height * scale - frameRect.height()) / 2f
        )
    }

    /**
     * BUG phát hiện qua smoke test thật: dùng `max(frameW/bmp.width, frameH/bmp.height)` với
     * `bmp` = [rotatedBitmap] (bounding-box ĐÃ PAD của [Bitmap.createBitmap] khi xoay) làm khung
     * hiện ra góc trong suốt/hở ở tỉ lệ xoay lớn (thấy rõ nhất ở 45°) — bounding box luôn LỚN HƠN
     * nội dung ảnh thật sau khi xoay, nên coverScale bị tính THIẾU.
     *
     * Công thức đúng: coi khung [frameWidth]x[frameHeight] là hình chữ nhật trục-thẳng cần nội
     * tiếp GỌN (không rơi ra ngoài) bên trong ảnh gốc W×H đã xoay góc θ quanh tâm — suy ra từ điều
     * kiện 4 góc khung (ánh xạ ngược về hệ toạ độ ảnh gốc qua ma trận xoay nghịch đảo) phải nằm
     * trong [-W/2,W/2]×[-H/2,H/2]:
     *   scale = max( (Fw·|cosθ| + Fh·|sinθ|) / W , (Fw·|sinθ| + Fh·|cosθ|) / H )
     * Tại θ=0 rút gọn đúng về công thức center-crop cũ `max(Fw/W, Fh/H)`.
     */
    private fun coverScaleForRotatedContent(frameWidth: Float, frameHeight: Float): Float {
        val src = sourceBitmap ?: return 1f
        return computeCoverScale(frameWidth, frameHeight, src.width, src.height, rotationDegrees)
    }

    private fun clampMatrix() {
        val bmp = rotatedBitmap ?: return
        if (ratio == null) return
        val values = FloatArray(9)
        displayMatrix.getValues(values)
        val scale = values[Matrix.MSCALE_X]
        if (scale < coverScale) {
            val factor = coverScale / scale
            displayMatrix.postScale(factor, factor, frameRect.centerX(), frameRect.centerY())
        } else if (scale > coverScale * MAX_USER_ZOOM) {
            val factor = (coverScale * MAX_USER_ZOOM) / scale
            displayMatrix.postScale(factor, factor, frameRect.centerX(), frameRect.centerY())
        }

        val bmpRect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        displayMatrix.mapRect(bmpRect)
        var dx = 0f
        var dy = 0f
        if (bmpRect.left > frameRect.left) dx = frameRect.left - bmpRect.left
        if (bmpRect.right < frameRect.right) dx = frameRect.right - bmpRect.right
        if (bmpRect.top > frameRect.top) dy = frameRect.top - bmpRect.top
        if (bmpRect.bottom < frameRect.bottom) dy = frameRect.bottom - bmpRect.bottom
        if (dx != 0f || dy != 0f) displayMatrix.postTranslate(dx, dy)
    }

    private val scaleDetector by lazy { ScaleGestureDetector(context, scaleListener) }
    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (ratio == null) return true
            displayMatrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
            clampMatrix()
            invalidate()
            return true
        }
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (rotatedBitmap == null || ratio == null) return true
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    displayMatrix.postTranslate(dx, dy)
                    clampMatrix()
                    invalidate()
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = rotatedBitmap ?: return
        canvas.drawBitmap(bmp, displayMatrix, bitmapPaint)
        if (ratio == null) return

        canvas.drawRect(0f, 0f, width.toFloat(), frameRect.top, scrimPaint)
        canvas.drawRect(0f, frameRect.bottom, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRect(0f, frameRect.top, frameRect.left, frameRect.bottom, scrimPaint)
        canvas.drawRect(frameRect.right, frameRect.top, width.toFloat(), frameRect.bottom, scrimPaint)

        val thirdWidth = frameRect.width() / 3f
        val thirdHeight = frameRect.height() / 3f
        for (i in 1..2) {
            val x = frameRect.left + thirdWidth * i
            canvas.drawLine(x, frameRect.top, x, frameRect.bottom, gridPaint)
            val y = frameRect.top + thirdHeight * i
            canvas.drawLine(frameRect.left, y, frameRect.right, y, gridPaint)
        }
        canvas.drawRect(frameRect, frameStrokePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotateRunnable?.let { rotateHandler.removeCallbacks(it) }
        val rotated = rotatedBitmap
        val source = sourceBitmap
        if (rotated != null && rotated !== source && !rotated.isRecycled) {
            rotated.recycle()
        }
        if (source != null && !source.isRecycled) {
            source.recycle()
        }
        rotatedBitmap = null
        sourceBitmap = null
    }

    companion object {
        private const val FRAME_PADDING_DP = 24f
        private const val MAX_USER_ZOOM = 4f
        private const val ROTATE_DEBOUNCE_MS = 80L

        /**
         * Hàm thuần (không đụng Bitmap thật) — dễ unit test trực tiếp trên JVM, tránh phụ thuộc
         * `Bitmap.createBitmap(src,...,matrix,filter)` cho góc xoay KHÔNG phải bội số 90°: đã xác
         * nhận qua debug trực tiếp shadow Bitmap của Robolectric trả về `width=0` sai cho trường
         * hợp xoay 45° (môi trường test giới hạn, không phải bug code) — xem
         * [CropOverlayView]/`doc/task` ghi chú tương tự (Robolectric không rasterize pixel thật).
         *
         * scale = max( (Fw·|cosθ| + Fh·|sinθ|) / W , (Fw·|sinθ| + Fh·|cosθ|) / H )
         * — xem doc-comment ở [coverScaleForRotatedContent] cho suy luận đầy đủ.
         */
        internal fun computeCoverScale(
            frameWidth: Float,
            frameHeight: Float,
            contentWidth: Int,
            contentHeight: Int,
            rotationDegrees: Float
        ): Float {
            val radians = Math.toRadians(rotationDegrees.toDouble())
            val cosTheta = kotlin.math.abs(kotlin.math.cos(radians)).toFloat()
            val sinTheta = kotlin.math.abs(kotlin.math.sin(radians)).toFloat()
            val scaleForWidth = (frameWidth * cosTheta + frameHeight * sinTheta) / contentWidth
            val scaleForHeight = (frameWidth * sinTheta + frameHeight * cosTheta) / contentHeight
            return max(scaleForWidth, scaleForHeight)
        }
    }
}
