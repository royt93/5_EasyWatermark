package com.mckimquyen.watermark.ui.widget

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.core.animation.doOnEnd
import androidx.core.graphics.withSave
import androidx.palette.graphics.Palette
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.Companion.DEFAULT_TEXT_SIZE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.Companion.MAX_TEXT_SIZE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.Companion.MIN_TEXT_SIZE
import com.mckimquyen.watermark.ui.widget.utils.WaterMarkShader
import com.mckimquyen.watermark.utils.bitmap.decodeSampledBitmapFromResource
import com.mckimquyen.watermark.utils.ktx.applyConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class WaterMarkImageView : androidx.appcompat.widget.AppCompatImageView, CoroutineScope {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    @Volatile
    private var curImageInfo: ImageInfo = ImageInfo(Uri.EMPTY)

    private var decodedUri: Uri = Uri.EMPTY

    @Volatile
    private var localIconUri: Uri = Uri.EMPTY

    @Volatile
    private var iconBitmap: Bitmap? = null

    private var enableWaterMark = AtomicBoolean(false)

    private val drawableBounds = RectF()

    private var onBgReady: (palette: Palette) -> Unit = {}

    private var onOffsetChanged: (info: ImageInfo) -> Unit = { _ -> }

    private var onScaleEnd: (textSize: Float) -> Unit = { _ -> }

    private var exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
            Log.e(
                this::class.simpleName,
                "Throw Exception in WaterMarkImageView ${throwable.message}"
            )
            throwable.printStackTrace()
            generateBitmapJob?.cancel()
        }

    /**
     * Using single thread to making all building bitmap working serially. Avoiding concurrency problem about [Bitmap.recycle].
     * 使用额外的单线程上下文来避免 [buildIconBitmapShader] 方法因并发导致的问题。因为 Bitmap 需要适时回收。
     */
    private val generateBitmapCoroutineCtx = Dispatchers.Default
    private val generateBitmapMutex = Mutex()

    private var generateBitmapJob: Job? = null

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        generateBitmapJob?.cancel()
    }

    fun updateUri(init: Boolean, imageInfo: ImageInfo) {
        config?.let {
            applyNewConfig(init, it, imageInfo)
        } ?: kotlin.run {
            curImageInfo = imageInfo
        }
    }

    var config: WaterMark? = null
        set(value) {
            Log.d("roy93~", "[WMIV] config setter called: value?.markMode=${value?.markMode} iconUri=${value?.iconUri}")
            if (field == value) {
                Log.d("roy93~", "[WMIV] config setter: value == field, SKIP (no change)")
                return
            }
            field = value
            val uriBlank = curImageInfo.uri.toString().isBlank()
            Log.d("roy93~", "[WMIV] config setter: curImageInfo.uri='${curImageInfo.uri}'  blank=$uriBlank")
            if (uriBlank) {
                Log.d("roy93~", "[WMIV] config setter: curImageInfo uri is BLANK → applyNewConfig skipped (no image loaded yet)")
                return
            }
            Log.d("roy93~", "[WMIV] config setter: calling applyNewConfig")
            field?.let { applyNewConfig(false, it, curImageInfo) }
        }

    private val drawableAlphaAnimator by lazy {
        ObjectAnimator.ofInt(0, 255).apply {
            addUpdateListener {
                val alpha = it.animatedValue as Int
                this@WaterMarkImageView.drawable?.alpha = alpha
            }
            duration = ANIMATION_DURATION
        }
    }

    private fun applyNewConfig(
        isInit: Boolean,
        newConfig: WaterMark,
        imageInfo: ImageInfo,
    ) {
        val uri = imageInfo.uri
        Log.d("roy93~", "[WMIV] applyNewConfig: markMode=${newConfig.markMode} iconUri=${newConfig.iconUri} imageUri=$uri")
        generateBitmapJob?.cancel()
        generateBitmapJob = launch(exceptionHandler) {
            // quick check is the same image
            if (decodedUri != uri) {
                Log.d("roy93~", "[WMIV] applyNewConfig: decodedUri($decodedUri) != uri($uri), decoding main image...")
                // hide iv
                this@WaterMarkImageView.drawable?.alpha = 0
                drawableAlphaAnimator.cancel()
                // decode with inSample
                val decodeResult = decodeSampledBitmapFromResource(
                    context.contentResolver,
                    uri,
                    calculateDrawLimitWidth(
                        this@WaterMarkImageView.measuredWidth,
                        this@WaterMarkImageView.paddingStart
                    ),
                    calculateDrawLimitHeight(
                        this@WaterMarkImageView.measuredHeight,
                        this@WaterMarkImageView.paddingTop
                    )
                )
                val bitmapValue = decodeResult.data
                Log.d("roy93~", "[WMIV] applyNewConfig: main image decode isFailure=${decodeResult.isFailure()} bitmapNull=${bitmapValue == null}")
                if (decodeResult.isFailure() || bitmapValue == null) {
                    Log.d("roy93~", "[WMIV] applyNewConfig: main image decode FAILED → return")
                    return@launch
                }
                // setting the bitmap of image
                val imageBitmap = bitmapValue.bitmap ?: return@launch
                // adjust bitmap via matrix
                setImageBitmap(imageBitmap)
                val matrix = adjustMatrix(
                    srcMatrix = imageMatrix,
                    viewWidth = measuredWidth,
                    viewHeight = measuredHeight,
                    paddingLeft = paddingLeft,
                    paddingTop = paddingTop,
                    bitmapWidth = imageBitmap.width,
                    bitmapHeight = imageBitmap.height
                )
                imageMatrix = matrix
                // setting background color via Palette
                applyBg(imageBitmap)
                // animate to show
                // when showing first bitmap we need to wait the imageview prepared.
                if (isInit) {
                    enableWaterMark.set(false)
                }
                drawableAlphaAnimator.start()
                if (isInit) {
                    invalidate()
                    delay(drawableAlphaAnimator.duration - 30)
                    enableWaterMark.set(true)
                }
                // collect the drawable of new image in ImageView
                generateDrawableBounds()
                // the scale factor which of real image and render bitmap
                imageInfo.inSample = bitmapValue.inSampleSize
                curImageInfo = imageInfo
                curImageInfo.width = drawableBounds.width().toInt()
                curImageInfo.height = drawableBounds.height().toInt()
                decodedUri = uri
            } else {
                Log.d("roy93~", "[WMIV] applyNewConfig: decodedUri == uri, skip main image decode")
            }
            curImageInfo = imageInfo
            // apply new config to paint
            textPaint.applyConfig(curImageInfo, newConfig)
            Log.d("roy93~", "[WMIV] applyNewConfig: building shader for mode=${newConfig.markMode}")
            layoutShader = when (newConfig.markMode) {
                WaterMarkRepository.MarkMode.Text -> {
                    generateBitmapMutex.withLock {
                        buildTextBitmapShader(
                            imageInfo = curImageInfo,
                            config = newConfig,
                            textPaint = textPaint,
                            coroutineContext = generateBitmapCoroutineCtx
                        )
                    }
                }

                WaterMarkRepository.MarkMode.Image -> {
                    Log.d("roy93~", "[WMIV] Image mode: iconUri=${newConfig.iconUri}  localIconUri=$localIconUri")
                    Log.d("roy93~", "[WMIV] Image mode: iconBitmap null=${iconBitmap == null}")
                    if (iconBitmap == null
                        || localIconUri != newConfig.iconUri
                        || (iconBitmap!!.width != newConfig.textSize.toInt() && iconBitmap!!.height != newConfig.textSize.toInt())
                    ) {
                        Log.d("roy93~", "[WMIV] Image mode: will decode icon bitmap from uri=${newConfig.iconUri}")
                        val iconBitmapRect = decodeSampledBitmapFromResource(
                            resolver = context.contentResolver,
                            uri = newConfig.iconUri,
                            reqWidth = measuredWidth,
                            reqHeight = measuredHeight,
                        )
                        Log.d("roy93~", "[WMIV] Image mode: icon decode isFailure=${iconBitmapRect.isFailure()} dataNull=${iconBitmapRect.data == null} bitmapNull=${iconBitmapRect.data?.bitmap == null}")
                        if (iconBitmapRect.isFailure() || iconBitmapRect.data == null) {
                            Log.d("roy93~", "[WMIV] Image mode: icon decode FAILED → return (watermark will NOT render)")
                            return@launch
                        }
                        iconBitmap = iconBitmapRect.data!!.bitmap
                        Log.d("roy93~", "[WMIV] Image mode: iconBitmap set: ${iconBitmap?.width}x${iconBitmap?.height}")
                    } else {
                        Log.d("roy93~", "[WMIV] Image mode: reusing cached iconBitmap")
                    }
                    localIconUri = newConfig.iconUri
                    layoutPaint.shader = null
                    generateBitmapMutex.withLock {
                        buildIconBitmapShader(
                            imageInfo = curImageInfo,
                            srcBitmap = iconBitmap!!,
                            config = newConfig,
                            textPaint = textPaint,
                            scale = false,
                            coroutineContext = generateBitmapCoroutineCtx
                        )
                    }
                }
            }
            Log.d("roy93~", "[WMIV] applyNewConfig done: layoutShader null=${layoutShader == null}, calling postInvalidate")
            postInvalidate()
        }
    }

    private fun applyBg(imageBitmap: Bitmap?) {
        launch {
            generatePalette(imageBitmap)?.let { palette ->
                setBackgroundColor(Color.TRANSPARENT)
                this@WaterMarkImageView.onBgReady.invoke(palette)
            }
        }
    }

    private suspend fun generatePalette(imageBitmap: Bitmap?): Palette? =
        withContext(Dispatchers.Default) {
            return@withContext imageBitmap?.let { Palette.Builder(it).generate() }
        }

    private val textPaint: TextPaint by lazy {
        TextPaint().applyConfig(curImageInfo, config)
    }

    private val layoutPaint: Paint by lazy {
        Paint()
    }

    private var layoutShader: WaterMarkShader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.i("onSizeChanged", "$w, $h, $oldh, $oldh")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentConfig = config
        // In Text mode: skip if there's no text content to render.
        // In Image mode (icon / signature): text is irrelevant – never skip based on it.
        val isTextModeWithNoContent =
            currentConfig?.markMode == WaterMarkRepository.MarkMode.Text &&
                    currentConfig.text.isNullOrEmpty()
        val skipReason = when {
            isTextModeWithNoContent -> "TextMode+noContent"
            decodedUri.toString().isEmpty() -> "decodedUri empty"
            layoutShader == null -> "layoutShader null"
            drawableAlphaAnimator.isRunning -> "alphaAnim running"
            else -> null
        }
        if (skipReason != null) {
            Log.d("roy93~", "[WMIV] onDraw SKIP: $skipReason  mode=${currentConfig?.markMode}")
            return
        }
        Log.d("roy93~", "[WMIV] onDraw DRAWING: mode=${currentConfig?.markMode} shader=${layoutShader != null} tileMode=${curImageInfo.obtainTileMode()}")
        Log.d("roy93~", "[WMIV] onDraw: layoutShader sizes=${layoutShader?.width}x${layoutShader?.height} drawableBounds=$drawableBounds offsetX=${curImageInfo.offsetX} offsetY=${curImageInfo.offsetY}")
        Log.d("roy93~", "[WMIV] onDraw: layoutPaint alpha=${layoutPaint.alpha}")
        layoutPaint.shader = layoutShader?.bitmapShader
        canvas?.withSave {
            if (curImageInfo.obtainTileMode() == Shader.TileMode.CLAMP) {
                val dx = drawableBounds.left + curImageInfo.offsetX * drawableBounds.width()
                val dy = drawableBounds.top + curImageInfo.offsetY * drawableBounds.height()
                Log.d("roy93~", "[WMIV] onDraw CLAMP: translate($dx, $dy)")
                translate(
                    /* dx = */ dx,
                    /* dy = */ dy
                )
                drawRect(
                    /* left = */ 0f,
                    /* top = */ 0f,
                    /* right = */ (layoutShader?.width ?: 0).toFloat(),
                    /* bottom = */ (layoutShader?.height ?: 0).toFloat(),
                    /* paint = */ layoutPaint
                )
            } else {
                Log.d("roy93~", "[WMIV] onDraw REPEAT: translate(${drawableBounds.left}, ${drawableBounds.top})")
                translate(drawableBounds.left, drawableBounds.top)
                drawRect(
                    /* left = */ 0f,
                    /* top = */ 0f,
                    /* right = */ drawableBounds.right - drawableBounds.left,
                    /* bottom = */ drawableBounds.bottom - drawableBounds.top,
                    /* paint = */ layoutPaint
                )
            }
        }
    }

    private fun generateDrawableBounds() {
        val bounds = drawableBounds
        imageMatrix.mapRect(bounds, RectF(drawable.bounds))
        bounds.set(
            /* left = */ bounds.left + paddingLeft,
            /* top = */ bounds.top + paddingTop,
            /* right = */ bounds.right + paddingRight,
            /* bottom = */ bounds.bottom + paddingBottom,
        )
    }

    fun onBgReady(block: (palette: Palette) -> Unit) {
        this.onBgReady = block
    }

    fun onOffsetChanged(block: (info: ImageInfo) -> Unit) {
        this.onOffsetChanged = block
    }

    fun onScaleEnd(block: (textSize: Float) -> Unit) {
        this.onScaleEnd = block
    }

    fun reset() {
        curImageInfo = ImageInfo(Uri.EMPTY)
        localIconUri = Uri.EMPTY
        setImageBitmap(null)
        setBackgroundColor(Color.TRANSPARENT)
        decodedUri = Uri.EMPTY
    }

    private fun updateWaterMarkOffset(deltaX: Float, deltaY: Float): ImageInfo {
        if (curImageInfo.obtainTileMode() != Shader.TileMode.CLAMP) {
            return curImageInfo
        }
        val newOffsetX = (curImageInfo.offsetX + deltaX / drawableBounds.width())
        val newOffsetY = (curImageInfo.offsetY + deltaY / drawableBounds.height())
        curImageInfo = curImageInfo.copy(
            offsetX = newOffsetX,
            offsetY = newOffsetY
        )
        invalidate()
        return curImageInfo
    }

    private var touchRect = RectF()

    private fun isOutOfDrawable(deltaX: Float, deltaY: Float): Boolean {
        if (curImageInfo.obtainTileMode() != Shader.TileMode.CLAMP) {
            return false
        }
        val newOffsetX = (curImageInfo.offsetX + deltaX / drawableBounds.width())
        val newOffsetY = (curImageInfo.offsetY + deltaY / drawableBounds.height())
        val newX = drawableBounds.left + newOffsetX * drawableBounds.width()
        val newY = drawableBounds.top + newOffsetY * drawableBounds.height()
        val bitmapWith = layoutShader?.width ?: 0
        val bitmapHeight = layoutShader?.height ?: 0
        touchRect.set(
            /* left = */ newX,
            /* top = */ newY,
            /* right = */ newX + bitmapWith,
            /* bottom = */ newY + bitmapHeight
        )
        Log.i(TAG, "isOutOfDrawable $touchRect, drawableBounds: $drawableBounds")
        return touchRect.right < drawableBounds.left
                || touchRect.left > drawableBounds.right
                || touchRect.top > drawableBounds.bottom
                || touchRect.bottom < drawableBounds.top
    }

    private fun isTouchWaterMark(event: MotionEvent): Boolean {
        if (curImageInfo.obtainTileMode() != Shader.TileMode.CLAMP) {
            return false
        }
        val shader = layoutShader ?: return false
        val bounds = drawableBounds
        val waterMarkX = bounds.left + curImageInfo.offsetX * bounds.width()
        val waterMarkY = bounds.top + curImageInfo.offsetY * bounds.height()
        return event.x > waterMarkX
                && event.x < waterMarkX + shader.width
                && event.y > waterMarkY
                && event.y < waterMarkY + shader.height
    }

    private var animator: Animator? = null

    private fun backToCenter(
        post: (info: ImageInfo, x: Float, y: Float) -> Unit,
    ) {
        animator?.cancel()
        val curOffsetX = curImageInfo.offsetX
        val curOffsetY = curImageInfo.offsetY
        val centerOffsetX = ((drawableBounds.width() - (layoutShader?.width ?: 0)) / 2)
        val centerOffsetY = ((drawableBounds.height() - (layoutShader?.height ?: 0)) / 2)
        val centerOffsetXP = (centerOffsetX) / drawableBounds.width()
        val centerOffsetYP = (centerOffsetY) / drawableBounds.height()
        val info = curImageInfo.copy(
            offsetX = centerOffsetXP,
            offsetY = centerOffsetYP
        )
        curImageInfo = info
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener {
                val value = it.animatedValue as Float
                val offsetX = curOffsetX + (centerOffsetXP - curOffsetX) * value
                val offsetY = curOffsetY + (centerOffsetYP - curOffsetY) * value
                curImageInfo = curImageInfo.copy(
                    offsetX = offsetX,
                    offsetY = offsetY
                )
                invalidate()
            }
            doOnEnd {
                post(info, centerOffsetX, centerOffsetY)
            }
            start()
        }
    }

    private var mScaleFactor = 1f

    private val mScaleDetector by lazy {
        ScaleGestureDetector(/* context = */ context, /* listener = */ scaleListener)
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            mScaleFactor *= detector.scaleFactor
            mScaleFactor = mScaleFactor.coerceAtLeast(0.1f).coerceAtMost(5.0f)
            // Don't let the object get too small or too large.
            val textSize = (config?.textSize ?: DEFAULT_TEXT_SIZE) * if (mScaleFactor > 1f) {
                ((1 - mScaleFactor).absoluteValue * 0.1f + 1f)
            } else {
                1 - (1 - mScaleFactor).absoluteValue * 0.1f
            }
            if (textSize > MAX_TEXT_SIZE && mScaleFactor > 1f) {
                Log.i(TAG, "onScale: $textSize, $mScaleFactor, to max")
                return true
            }
            if (textSize < MIN_TEXT_SIZE && mScaleFactor < 1f) {
                Log.i(TAG, "onScale: $textSize, $mScaleFactor, to min")
                return true
            }
            Log.i(TAG, "onScale $mScaleFactor, textSize: ${config?.textSize} ==> $textSize")
            config = config?.copy(textSize = textSize)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            Log.i(TAG, "onScaleEnd $mScaleFactor")
            val textSize = (config?.textSize ?: DEFAULT_TEXT_SIZE)
//            config = config?.copy(textSize = textSize)
//            mScaleFactor = 1f
            this@WaterMarkImageView.onScaleEnd(textSize)
        }
    }

    private var startX = 0f
    private var startY = 0f
    private var enableTouch = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let the ScaleGestureDetector inspect all events.
        Log.i(TAG, "onTouch $event")
        if (enableTouch.not()) {
            return false
        }
//        mScaleDetector.onTouchEvent(event)
//        if (mScaleDetector.isInProgress) {
//            return true
//        }
        if (isTouchWaterMark(event).not() || event.pointerCount > 1) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                val endX = event.x
                val endY = event.y
                val deltaX = endX - startX
                val deltaY = endY - startY
                updateWaterMarkOffset(deltaX, deltaY)
                this.startX = endX
                this.startY = endY
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val endX = event.x
                val endY = event.y
                val deltaX = endX - startX
                val deltaY = endY - startY
                if (isOutOfDrawable(deltaX, deltaY)) {
                    // disable touch
                    enableTouch = false
                    // begin back to center animation
                    backToCenter { info, x, y ->
                        this.startX = x
                        this.startY = y
                        enableTouch = true
                        onOffsetChanged(info)
                    }
                } else {
                    this.startX = endX
                    this.startY = endY
                    onOffsetChanged(curImageInfo)
                }
            }
        }
        return true
    }

    companion object {

        private const val TAG = "WatermarkImageView"

        const val ANIMATION_DURATION = 450L

        /**
         * Very simple way to fit the image into the canvas
         */
        fun adjustMatrix(
            srcMatrix: Matrix,
            viewWidth: Int,
            viewHeight: Int,
            paddingLeft: Int,
            paddingTop: Int,
            bitmapWidth: Int,
            bitmapHeight: Int,
        ): Matrix {
            Log.i(
                TAG,
                "width = $viewWidth, height = $viewHeight, bitmapWidth = $bitmapWidth, bitmapHeight = $bitmapHeight"
            )
            val matrix = Matrix(srcMatrix)
            matrix.reset()
            val canvasWidth = calculateDrawLimitWidth(viewWidth, paddingLeft).toFloat()
            val canvasHeight = calculateDrawLimitHeight(viewHeight, paddingTop).toFloat()
            val scaleX = canvasWidth / bitmapWidth
            val scaleY = canvasHeight / bitmapHeight
            val scale = min(scaleX, scaleY)
            matrix.postScale(scale, scale)
            matrix.postTranslate(
                (canvasWidth - bitmapWidth * scale) / 2,
                (canvasHeight - bitmapHeight * scale) / 2
            )
            return matrix
        }

        private fun adjustHorizontalGap(config: WaterMark, maxSize: Int): Int {
            return (maxSize * ((config.hGap / 100f) + 1)).toInt()
        }

        private fun adjustVerticalGap(config: WaterMark, maxSize: Int): Int {
            return (maxSize * ((config.vGap / 100f) + 1)).toInt()
        }

        private fun calculateMaxSize(w: Float, h: Float): Int {
            return sqrt(w.pow(2) + h.pow(2)).toInt()
        }

        fun calculateDrawLimitWidth(w: Int, ps: Int) = (w - ps * 2)

        fun calculateDrawLimitHeight(h: Int, pt: Int) = (h - pt * 2)

        suspend fun buildIconBitmapShader(
            imageInfo: ImageInfo,
            srcBitmap: Bitmap,
            config: WaterMark,
            textPaint: Paint,
            scale: Boolean,
            coroutineContext: CoroutineContext,
        ): WaterMarkShader? = withContext(coroutineContext) {
            if (srcBitmap.isRecycled) {
                return@withContext null
            }
            val tileMode = imageInfo.obtainTileMode()
            val showDebugRect = config.enableBounds
            val rawWidth = srcBitmap.width.toFloat().coerceAtLeast(1f)
            val rawHeight = srcBitmap.height.toFloat().coerceAtLeast(1f)

            // Normalize icon size based on the target text size (which already handles scaleX)
            // A multiplier of 3.5f makes the icon/signature relatively sized to normal text.
            val targetMaxDim = textPaint.textSize * 3.5f
            val maxRaw = max(rawWidth, rawHeight)
            val scaleRatio = if (maxRaw > 0) targetMaxDim / maxRaw else 1f

            val scaledW = (rawWidth * scaleRatio).toInt().coerceAtLeast(1)
            val scaledH = (rawHeight * scaleRatio).toInt().coerceAtLeast(1)
            
            val scaleBitmap = Bitmap.createScaledBitmap(
                srcBitmap,
                scaledW, scaledH,
                true
            )!!

            val maxSize = calculateMaxSize(scaledW.toFloat(), scaledH.toFloat())
            val targetW = adjustHorizontalGap(config, maxSize).coerceAtLeast(1)
            val targetH = adjustVerticalGap(config, maxSize).coerceAtLeast(1)

            val targetBitmap = Bitmap.createBitmap(
                /* width = */ targetW,
                /* height = */ targetH,
                /* config = */ Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(targetBitmap)

            if (showDebugRect) {
                val tmpPaint = Paint().apply {
                    color = Color.RED
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), tmpPaint)
                canvas.save()
            }
            canvas.rotate(
                config.degree,
                (targetW / 2).toFloat(),
                (targetH / 2).toFloat()
            )

            if (config.iconUri.toString().contains("signature") && config.iconUri.toString().contains(".webp")) {
                textPaint.colorFilter = android.graphics.PorterDuffColorFilter(config.textColor, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                textPaint.colorFilter = null
            }
            
            val drawLeft = (targetW - scaledW) / 2.toFloat()
            val drawTop = (targetH - scaledH) / 2.toFloat()

            canvas.drawBitmap(
                /* bitmap = */ scaleBitmap,
                /* left = */ drawLeft,
                /* top = */ drawTop,
                /* paint = */ textPaint
            )
            if (showDebugRect) {
                canvas.restore()
            }
            val bitmapShader = BitmapShader(
                /* bitmap = */ targetBitmap,
                /* tileX = */ tileMode,
                /* tileY = */ tileMode
            )
            return@withContext WaterMarkShader(
                bitmapShader = bitmapShader,
                width = targetBitmap.width,
                height = targetBitmap.height
            )
        }

        /**
         * Generate bitmap shader from input text.
         * Text watermark implemented by bitmap shader.
         * Using [StaticLayout] to draw multi line text.
         * @author roy.mobile.dev@gmail.com
         */
        suspend fun buildTextBitmapShader(
            imageInfo: ImageInfo,
            config: WaterMark,
            textPaint: TextPaint,
            coroutineContext: CoroutineContext,
        ): WaterMarkShader? = withContext(coroutineContext) {
            if (config.text.isBlank()) {
                return@withContext null
            }
            val showDebugRect = config.enableBounds
            var maxLineWidth = 0
            val tileMode = imageInfo.obtainTileMode()
            // calculate the max width of all lines
            config.text.split("\n").forEach {
                val startIndex = config.text.indexOf(it).coerceAtLeast(0)
                val lineWidth = textPaint.measureText(
                    /* text = */ config.text,
                    /* start = */ startIndex,
                    /* end = */ (startIndex + it.length).coerceAtMost(config.text.length)
                ).toInt()
                maxLineWidth = max(maxLineWidth, lineWidth)
            }

            val staticLayout =
                StaticLayout.Builder.obtain(
                    config.text,
                    0,
                    config.text.length,
                    textPaint,
                    maxLineWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()

            val textWidth = staticLayout.width.toFloat().coerceAtLeast(1f)
            val textHeight = staticLayout.height.toFloat().coerceAtLeast(1f)

            val radians = Math.toRadians(
                when (config.degree) {
                    in 0.0..90.0 -> config.degree.toDouble()
                    in 90.0..270.0 -> {
                        abs(180 - config.degree.toDouble())
                    }

                    else -> 360 - config.degree.toDouble()
                }
            )
            // Generate tmp size from rotation degree, all degree have it's own size.
            val fixWidth = textWidth * cos(radians) + textHeight * sin(radians)
            val fixHeight = textWidth * sin(radians) + textHeight * cos(radians)

            val finalWidth = adjustHorizontalGap(config, fixWidth.toInt())
            val finalHeight = adjustVerticalGap(config, fixHeight.toInt())
            val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (showDebugRect) {
                val tmpPaint = Paint().apply {
                    color = Color.RED
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                canvas.drawRect(0f, 0f, finalWidth.toFloat(), finalHeight.toFloat(), tmpPaint)
                canvas.save()
            }
            // rotate by user input
            canvas.rotate(
                config.degree,
                (finalWidth / 2).toFloat(),
                (finalHeight / 2).toFloat()
            )
            // draw text
            canvas.withSave {
                this.translate(
                    ((finalWidth) / 2).toFloat(),
                    ((finalHeight - staticLayout.getLineBottom(0) - staticLayout.getLineTop(0)) / 2).toFloat()
                )
                staticLayout.draw(canvas)
            }

            if (showDebugRect) {
                canvas.restore()
            }
            val bitmapShader = BitmapShader(
                /* bitmap = */ bitmap,
                /* tileX = */ tileMode,
                /* tileY = */ tileMode
            )
            return@withContext WaterMarkShader(
                bitmapShader = bitmapShader,
                width = bitmap.width,
                height = bitmap.height
            )
        }
    }
}
