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
import android.os.SystemClock
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
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.Companion.DEFAULT_TEXT_SIZE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.Companion.MAX_TEXT_SIZE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.Companion.MIN_TEXT_SIZE
import com.mckimquyen.watermark.ui.widget.utils.WaterMarkShader
import com.mckimquyen.watermark.utils.TextEffectRenderer
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import com.mckimquyen.watermark.utils.bitmap.applyCropAndRotate
import com.mckimquyen.watermark.utils.bitmap.decodeSampledBitmapFromResource
import com.mckimquyen.watermark.utils.ktx.applyConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    // ENH-15: theo dõi BitmapValue thật sự lấy từ BitmapCache cho ảnh chính/icon đang hiển thị —
    // retain() khi bắt đầu dùng, release() khi thay bằng bitmap khác hoặc View bị detach. Cho
    // phép BitmapCache tự recycle() bitmap bị evict an toàn (không đụng bitmap đang được vẽ).
    private var mainImageBitmapValue: BitmapCache.BitmapValue? = null
    private var iconBitmapValue: BitmapCache.BitmapValue? = null

    // FEAT-16: bitmap SAU khi áp crop/rotate — KHÔNG qua BitmapCache (mỗi tổ hợp crop/rotate là
    // duy nhất, không đáng cache dùng chung), View tự sở hữu + recycle bitmap này.
    private var transformedMainBitmap: Bitmap? = null

    private var enableWaterMark = AtomicBoolean(false)

    /**
     * FEAT-18: 0f..1f — 1f (mặc định) = watermark hiện ĐẦY ĐỦ (hành vi cũ, không đổi gì khi tính
     * năng so sánh trước/sau không dùng). Giá trị nhỏ hơn giới hạn vùng vẽ watermark ở BÊN TRÁI
     * theo tỉ lệ này (tính từ mép trái View), để lộ ảnh gốc (đã vẽ sẵn bởi `super.onDraw()`) ở
     * phần còn lại bên phải — không cần layer/bitmap riêng, chỉ clip canvas lúc vẽ watermark.
     */
    var compareRevealFraction: Float = 1f
        set(value) {
            val coerced = value.coerceIn(0f, 1f)
            if (field == coerced) return
            field = coerced
            invalidate()
        }

    private val drawableBounds = RectF()

    private var onBgReady: (palette: Palette) -> Unit = {}

    private var onOffsetChanged: (info: ImageInfo) -> Unit = { _ -> }

    private var onScaleEnd: (textSize: Float) -> Unit = { _ -> }

    /** FEAT-23: báo ra ngoài NGAY khi biết tỉ lệ khung ảnh thật (mỗi lần decode 1 ảnh MỚI). */
    private var onImageOrientationKnown: (isPortrait: Boolean) -> Unit = { _ -> }

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
        // ENH-15: View không còn dùng 2 bitmap này nữa — release() để BitmapCache được phép
        // recycle() nếu chúng đã (hoặc sẽ) bị evict.
        mainImageBitmapValue?.release()
        mainImageBitmapValue = null
        iconBitmapValue?.release()
        iconBitmapValue = null
        // FEAT-03: bitmap trong shader layer phụ không qua BitmapCache (giống layoutShader layer
        // chính) — tự GC được, chỉ cần bỏ tham chiếu.
        extraLayerShaders = emptyList()
        transformedMainBitmap?.let { if (!it.isRecycled) it.recycle() }
        transformedMainBitmap = null
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
            AppLog.d(LOG_TAG, "[WMIV] config setter called: value?.markMode=${value?.markMode} iconUri=${value?.iconUri}")
            if (field == value) {
                AppLog.d(LOG_TAG, "[WMIV] config setter: value == field, SKIP (no change)")
                return
            }
            field = value
            val uriBlank = curImageInfo.uri.toString().isBlank()
            AppLog.d(LOG_TAG, "[WMIV] config setter: curImageInfo.uri='${curImageInfo.uri}'  blank=$uriBlank")
            if (uriBlank) {
                AppLog.d(LOG_TAG, "[WMIV] config setter: curImageInfo uri is BLANK → applyNewConfig skipped (no image loaded yet)")
                return
            }
            AppLog.d(LOG_TAG, "[WMIV] config setter: calling applyNewConfig")
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
        imageInfo: ImageInfo
    ) {
        val uri = imageInfo.uri
        AppLog.d(LOG_TAG, "[WMIV] applyNewConfig: markMode=${newConfig.markMode} iconUri=${newConfig.iconUri} imageUri=$uri")
        generateBitmapJob?.cancel()
        generateBitmapJob = launch(exceptionHandler) {
            // FEAT-16: crop/rotate đổi trên CÙNG uri (đang mở lại từ CropActivity) cũng phải
            // decode lại — decodeSampledBitmapFromResource đã cache theo uri nên chi phí decode
            // lại gần như 0, chỉ tốn lại bước applyCropAndRotate.
            val geometryChanged = curImageInfo.cropRect != imageInfo.cropRect ||
                curImageInfo.rotationDegrees != imageInfo.rotationDegrees
            // quick check is the same image
            if (decodedUri != uri || geometryChanged) {
                AppLog.d(LOG_TAG, "[WMIV] applyNewConfig: decodedUri($decodedUri) != uri($uri) or geometryChanged=$geometryChanged, decoding main image...")
                // hide iv
                this@WaterMarkImageView.drawable?.alpha = 0
                drawableAlphaAnimator.cancel()
                // decode with inSample
                val decodeResult = decodeSampledBitmapFromResource(
                    context,
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
                AppLog.d(LOG_TAG, "[WMIV] applyNewConfig: main image decode isFailure=${decodeResult.isFailure()} bitmapNull=${bitmapValue == null}")
                if (decodeResult.isFailure() || bitmapValue == null) {
                    AppLog.d(LOG_TAG, "[WMIV] applyNewConfig: main image decode FAILED → return")
                    return@launch
                }
                // setting the bitmap of image
                val imageBitmap = bitmapValue.bitmap ?: return@launch
                // ENH-15: retain bitmap MỚI trước, release bitmap CŨ sau (không đảo thứ tự) —
                // nếu 2 BitmapInfo trùng nhau (refCount>0 do đang được giữ ở nơi khác), release
                // trước rồi retain sau có thể để lọt qua đúng lúc refCount chạm 0 và bị recycle.
                bitmapValue.retain()
                mainImageBitmapValue?.release()
                mainImageBitmapValue = bitmapValue
                // FEAT-16: crop/rotate áp lên bitmap TRƯỚC khi hiển thị — imageBitmap gốc vẫn
                // thuộc sở hữu BitmapCache (retain/release ở trên), applyCropAndRotate không bao
                // giờ recycle nó. Nếu có transform thật, kết quả là bitmap MỚI do View tự sở hữu.
                val previousTransformed = transformedMainBitmap
                val displayBitmap = applyCropAndRotate(imageBitmap, imageInfo.rotationDegrees, imageInfo.cropRect)
                transformedMainBitmap = if (displayBitmap !== imageBitmap) displayBitmap else null
                previousTransformed?.let { if (!it.isRecycled) it.recycle() }
                // adjust bitmap via matrix
                setImageBitmap(displayBitmap)
                val matrix = adjustMatrix(
                    srcMatrix = imageMatrix,
                    viewWidth = measuredWidth,
                    viewHeight = measuredHeight,
                    paddingLeft = paddingLeft,
                    paddingTop = paddingTop,
                    bitmapWidth = displayBitmap.width,
                    bitmapHeight = displayBitmap.height
                )
                imageMatrix = matrix
                // setting background color via Palette
                applyBg(displayBitmap)
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
                // ENH-08: imageInfo bất biến — copy() thay vì mutate object truyền vào từ ngoài
                // (repository/ViewModel có thể đang giữ cùng tham chiếu này), theo đúng pattern
                // curImageInfo.copy(...) đã dùng ở các nơi khác trong file này (offsetX/offsetY).
                curImageInfo = imageInfo.copy(
                    inSample = bitmapValue.inSampleSize,
                    width = drawableBounds.width().toInt(),
                    height = drawableBounds.height().toInt()
                )
                decodedUri = uri
                // FEAT-23: đúng lúc biết tỉ lệ khung ảnh THẬT (kích thước bitmap gốc, không phụ
                // thuộc scale-to-fit) — bằng nhau (ảnh vuông) coi là dọc (tie-break tuỳ ý, ghi rõ).
                this@WaterMarkImageView.onImageOrientationKnown(displayBitmap.height >= displayBitmap.width)
            } else {
                AppLog.d(LOG_TAG, "[WMIV] applyNewConfig: decodedUri == uri, skip main image decode")
            }
            curImageInfo = imageInfo
            // IDEA-06: chỉ ảnh hưởng bản render hiện tại — KHÔNG ghi đè newConfig/DataStore gốc.
            val effectiveConfig = applyAutoContrastIfEnabled(newConfig)
            // apply new config to paint
            textPaint.applyConfig(curImageInfo, effectiveConfig)
            AppLog.d(LOG_TAG, "[WMIV] applyNewConfig: building shader for mode=${newConfig.markMode}")
            layoutShader = when (newConfig.markMode) {
                WaterMarkRepository.MarkMode.Text -> {
                    generateBitmapMutex.withLock {
                        buildTextBitmapShader(
                            imageInfo = curImageInfo,
                            config = effectiveConfig,
                            textPaint = textPaint,
                            coroutineContext = generateBitmapCoroutineCtx
                        )
                    }
                }

                WaterMarkRepository.MarkMode.Image -> {
                    AppLog.d(LOG_TAG, "[WMIV] Image mode: iconUri=${newConfig.iconUri}  localIconUri=$localIconUri")
                    AppLog.d(LOG_TAG, "[WMIV] Image mode: iconBitmap null=${iconBitmap == null}")
                    // Check reuse-cache + decode + gán iconBitmap + build shader đều nằm trong
                    // CÙNG 1 mutex để tránh race giữa 2 job applyNewConfig chồng lấn (pinch nhanh
                    // liên tục) cùng đọc/ghi iconBitmap/localIconUri (BUG-06).
                    generateBitmapMutex.withLock {
                        if (iconBitmap == null || localIconUri != newConfig.iconUri) {
                            AppLog.d(LOG_TAG, "[WMIV] Image mode: will decode icon bitmap from uri=${newConfig.iconUri}")
                            val iconBitmapRect = decodeSampledBitmapFromResource(
                                context = context,
                                resolver = context.contentResolver,
                                uri = newConfig.iconUri,
                                reqWidth = measuredWidth,
                                reqHeight = measuredHeight
                            )
                            AppLog.d(LOG_TAG, "[WMIV] Image mode: icon decode isFailure=${iconBitmapRect.isFailure()} dataNull=${iconBitmapRect.data == null} bitmapNull=${iconBitmapRect.data?.bitmap == null}")
                            if (iconBitmapRect.isFailure() || iconBitmapRect.data == null) {
                                AppLog.d(LOG_TAG, "[WMIV] Image mode: icon decode FAILED → return (watermark will NOT render)")
                                return@launch
                            }
                            // ENH-15: retain mới trước, release cũ sau (xem lý do ở nhánh main image).
                            val newIconBitmapValue = iconBitmapRect.data!!
                            newIconBitmapValue.retain()
                            iconBitmapValue?.release()
                            iconBitmapValue = newIconBitmapValue
                            iconBitmap = newIconBitmapValue.bitmap
                            AppLog.d(LOG_TAG, "[WMIV] Image mode: iconBitmap set: ${iconBitmap?.width}x${iconBitmap?.height}")
                        } else {
                            AppLog.d(LOG_TAG, "[WMIV] Image mode: reusing cached iconBitmap")
                        }
                        localIconUri = newConfig.iconUri
                        layoutPaint.shader = null
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
            // FEAT-03: build shader cho từng layer PHỤ, tuần tự, trong CÙNG mutex với layer chính
            // để tránh race giữa 2 job applyNewConfig chồng lấn — layer chính giữ nguyên hoàn
            // toàn phía trên, đây là bước THÊM MỚI, không thay thế gì.
            extraLayerShaders = generateBitmapMutex.withLock {
                newConfig.extraLayers.mapNotNull { layer -> buildExtraLayerShader(layer, coroutineContext = generateBitmapCoroutineCtx) }
            }
            AppLog.d(LOG_TAG, "[WMIV] applyNewConfig done: layoutShader null=${layoutShader == null}, extraLayers=${extraLayerShaders.size}, calling postInvalidate")
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

    /**
     * IDEA-06: khi [WaterMark.autoContrastEnabled] bật (layer chính, chế độ Text), tự đảo
     * [WaterMark.textColor] đen/trắng theo độ sáng vùng ảnh NGAY DƯỚI watermark + nâng sàn
     * [WaterMark.alpha] để chữ không bị chìm vào nền. Trả về bản copy chỉ dùng để RENDER —
     * không ghi lại DataStore/[config] gốc.
     *
     * ponytail: vùng lấy mẫu là 1 ô vuông ước lượng quanh vị trí neo watermark (quy đổi
     * [WaterMark.textSize] sang không gian pixel bitmap gốc qua tỉ lệ [drawableBounds]/bitmap),
     * không phải đúng hình chữ nhật thật của watermark — đo chính xác cần build shader 2 lần
     * (kích thước shader phụ thuộc text/gap/rotation, biết được SAU khi build). Đủ tốt cho mục
     * đích chọn màu tương phản; nâng cấp nếu sau này cần chính xác pixel-perfect.
     */
    private suspend fun applyAutoContrastIfEnabled(newConfig: WaterMark): WaterMark {
        if (!shouldApplyAutoContrast(newConfig)) return newConfig
        val bitmap = transformedMainBitmap ?: mainImageBitmapValue?.bitmap
        if (bitmap == null || bitmap.isRecycled) return newConfig
        return withContext(Dispatchers.Default) {
            val scaleToBitmap = if (drawableBounds.width() > 0f) {
                bitmap.width / drawableBounds.width()
            } else {
                1f
            }
            val sampleSizePx = (newConfig.textSize * scaleToBitmap * AUTO_CONTRAST_SAMPLE_SIZE_MULTIPLIER)
                .toInt()
                .coerceAtLeast(1)
            val region = computeAutoContrastSampleRegion(
                tileMode = curImageInfo.obtainTileMode(),
                offsetX = curImageInfo.offsetX,
                offsetY = curImageInfo.offsetY,
                sampleSizePx = sampleSizePx,
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height
            )
            if (!region.isValid) return@withContext newConfig
            val dominantColor = try {
                Palette.Builder(bitmap)
                    .setRegion(region.left, region.top, region.right, region.bottom)
                    .generate()
                    .getDominantColor(Color.GRAY)
            } catch (e: IllegalArgumentException) {
                AppLog.d(LOG_TAG, "[WMIV] applyAutoContrastIfEnabled: invalid region $region, skip")
                return@withContext newConfig
            }
            newConfig.copy(
                textColor = TextEffectRenderer.contrastingColor(dominantColor, ALPHA_OPAQUE),
                alpha = readableAlpha(newConfig.alpha)
            )
        }
    }

    private val textPaint: TextPaint by lazy {
        TextPaint().applyConfig(curImageInfo, config)
    }

    private val layoutPaint: Paint by lazy {
        Paint()
    }

    private var layoutShader: WaterMarkShader? = null

    /** FEAT-03: 1 layer PHỤ đã build xong, sẵn sàng vẽ — [anchor]/[marginPercent] cần lại lúc vẽ vì vị trí layer phụ tính trực tiếp từ neo, không đi qua [curImageInfo]. */
    private data class ExtraLayerRender(
        val shader: WaterMarkShader,
        val paint: Paint,
        val anchor: Int,
        val marginPercent: Float
    )

    /** FEAT-03: theo đúng z-order (index 0 = dưới cùng). */
    private var extraLayerShaders: List<ExtraLayerRender> = emptyList()

    /**
     * FEAT-03: build shader cho 1 layer PHỤ — KHÔNG cache bitmap icon theo field như layer chính
     * (đơn giản hoá: layer phụ không có pinch/drag realtime nên không cần tối ưu cache, chỉ decode
     * lại mỗi lần [applyNewConfig] chạy, giống tần suất decode ảnh chính).
     */
    private suspend fun buildExtraLayerShader(
        layer: WatermarkLayer,
        coroutineContext: CoroutineContext
    ): ExtraLayerRender? {
        val layerConfig = layer.toWaterMark()
        val paint = TextPaint().applyConfig(curImageInfo, layerConfig)
        val shader = when (layer.markMode) {
            WaterMarkRepository.MarkMode.Text -> buildTextBitmapShader(
                imageInfo = curImageInfo,
                config = layerConfig,
                textPaint = paint,
                coroutineContext = coroutineContext
            )

            WaterMarkRepository.MarkMode.Image -> {
                val iconResult = decodeSampledBitmapFromResource(
                    context = context,
                    resolver = context.contentResolver,
                    uri = layer.iconUri,
                    reqWidth = measuredWidth,
                    reqHeight = measuredHeight
                )
                val iconValue = iconResult.data ?: return null
                iconValue.retain()
                try {
                    val srcBitmap = iconValue.bitmap ?: return null
                    buildIconBitmapShader(
                        imageInfo = curImageInfo,
                        srcBitmap = srcBitmap,
                        config = layerConfig,
                        textPaint = paint,
                        scale = false,
                        coroutineContext = coroutineContext
                    )
                } finally {
                    iconValue.release()
                }
            }
        } ?: return null
        return ExtraLayerRender(
            shader = shader,
            paint = Paint().apply { this.shader = shader.bitmapShader },
            anchor = layer.anchor,
            marginPercent = layer.marginPercent
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.i("onSizeChanged", "$w, $h, $oldh, $oldh")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentConfig = config
        // FEAT-03: 2 lý do dưới đây chặn TOÀN BỘ (kể cả layer phụ) — ảnh chưa decode xong/đang
        // animate thì drawableBounds chưa sẵn sàng cho bất kỳ layer nào.
        val hardSkipReason = when {
            decodedUri.toString().isEmpty() -> "decodedUri empty"
            drawableAlphaAnimator.isRunning -> "alphaAnim running"
            else -> null
        }
        if (hardSkipReason != null) {
            AppLog.d(LOG_TAG, "[WMIV] onDraw SKIP: $hardSkipReason  mode=${currentConfig?.markMode}")
            return
        }
        // In Text mode: skip if there's no text content to render.
        // In Image mode (icon / signature): text is irrelevant – never skip based on it.
        // FEAT-03: 2 lý do dưới đây chỉ chặn LAYER CHÍNH — layer phụ (nếu có) vẫn phải vẽ, vd user
        // để trống text watermark chính nhưng vẫn muốn hiện logo layer phụ.
        val isTextModeWithNoContent =
            currentConfig?.markMode == WaterMarkRepository.MarkMode.Text &&
                currentConfig.text.isNullOrEmpty()
        val skipPrimaryReason = when {
            isTextModeWithNoContent -> "TextMode+noContent"
            layoutShader == null -> "layoutShader null"
            else -> null
        }
        if (skipPrimaryReason == null) {
            AppLog.d(LOG_TAG, "[WMIV] onDraw DRAWING: mode=${currentConfig?.markMode} shader=${layoutShader != null} tileMode=${curImageInfo.obtainTileMode()}")
            AppLog.d(LOG_TAG, "[WMIV] onDraw: layoutShader sizes=${layoutShader?.width}x${layoutShader?.height} drawableBounds=$drawableBounds offsetX=${curImageInfo.offsetX} offsetY=${curImageInfo.offsetY}")
            AppLog.d(LOG_TAG, "[WMIV] onDraw: layoutPaint alpha=${layoutPaint.alpha}")
            layoutPaint.shader = layoutShader?.bitmapShader
            drawPrimaryLayer(canvas)
        } else {
            AppLog.d(LOG_TAG, "[WMIV] onDraw SKIP primary only: $skipPrimaryReason  mode=${currentConfig?.markMode}")
        }
        drawExtraLayers(canvas)
    }

    /** Layer CHÍNH — logic gốc, không đổi hành vi (chỉ tách thành hàm riêng để onDraw gọi có điều kiện, xem FEAT-03). */
    private fun drawPrimaryLayer(canvas: Canvas) {
        canvas?.withSave {
            // FEAT-18: clip TRƯỚC translate — toạ độ theo hệ View gốc (không bị dịch theo tile),
            // giữ đường phân cách thẳng đứng cố định theo tỉ lệ chiều rộng View.
            if (compareRevealFraction < 1f) {
                clipRect(0f, 0f, width * compareRevealFraction, height.toFloat())
            }
            if (curImageInfo.obtainTileMode() == Shader.TileMode.CLAMP) {
                val dx = drawableBounds.left + curImageInfo.offsetX * drawableBounds.width()
                val dy = drawableBounds.top + curImageInfo.offsetY * drawableBounds.height()
                AppLog.d(LOG_TAG, "[WMIV] onDraw CLAMP: translate($dx, $dy)")
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
                AppLog.d(LOG_TAG, "[WMIV] onDraw REPEAT: translate(${drawableBounds.left}, ${drawableBounds.top})")
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

    /**
     * FEAT-03: vẽ layer PHỤ theo đúng z-order (index 0 = dưới cùng), SAU layer chính — vị trí
     * tính trực tiếp từ neo 9-grid + margin% (không ghi vào curImageInfo, layer phụ không có
     * "vị trí đang kéo" như layer chính).
     */
    private fun drawExtraLayers(canvas: Canvas) {
        if (extraLayerShaders.isEmpty()) return
        val tileMode = curImageInfo.obtainTileMode()
        extraLayerShaders.forEach { render ->
            canvas?.withSave {
                if (tileMode == Shader.TileMode.CLAMP) {
                    val boundsW = drawableBounds.width()
                    val boundsH = drawableBounds.height()
                    if (boundsW <= 0f || boundsH <= 0f) return@withSave
                    val (offsetX, offsetY) = Anchor.obtain(render.anchor).toOffset(
                        render.marginPercent,
                        render.shader.width / boundsW,
                        render.shader.height / boundsH
                    )
                    translate(
                        drawableBounds.left + offsetX * boundsW,
                        drawableBounds.top + offsetY * boundsH
                    )
                    drawRect(0f, 0f, render.shader.width.toFloat(), render.shader.height.toFloat(), render.paint)
                } else {
                    translate(drawableBounds.left, drawableBounds.top)
                    drawRect(
                        0f,
                        0f,
                        drawableBounds.right - drawableBounds.left,
                        drawableBounds.bottom - drawableBounds.top,
                        render.paint
                    )
                }
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
            /* bottom = */ bounds.bottom + paddingBottom
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

    /** FEAT-23. */
    fun onImageOrientationKnown(block: (isPortrait: Boolean) -> Unit) {
        this.onImageOrientationKnown = block
    }

    fun reset() {
        // BUG-28: giống onDetachedFromWindow(), khi MainActivity.resetView() gọi reset() để quay về
        // LaunchMode (View không bị detach), phải release refcount của mainImageBitmapValue và
        // iconBitmapValue để BitmapCache không tích luỹ bitmap mồ côi giữ refCount > 0.
        generateBitmapJob?.cancel()
        mainImageBitmapValue?.release()
        mainImageBitmapValue = null
        iconBitmapValue?.release()
        iconBitmapValue = null
        transformedMainBitmap?.let { if (!it.isRecycled) it.recycle() }
        transformedMainBitmap = null
        iconBitmap = null
        layoutShader = null
        layoutPaint.shader = null
        extraLayerShaders = emptyList()
        curImageInfo = ImageInfo(Uri.EMPTY)
        localIconUri = Uri.EMPTY
        setImageBitmap(null)
        setBackgroundColor(Color.TRANSPARENT)
        decodedUri = Uri.EMPTY
    }

    @androidx.annotation.VisibleForTesting
    internal fun getMainImageBitmapValue(): BitmapCache.BitmapValue? = mainImageBitmapValue

    @androidx.annotation.VisibleForTesting
    internal fun getIconBitmapValue(): BitmapCache.BitmapValue? = iconBitmapValue

    @androidx.annotation.VisibleForTesting
    internal fun setMainImageBitmapValueForTesting(value: BitmapCache.BitmapValue?) {
        mainImageBitmapValue = value
    }

    @androidx.annotation.VisibleForTesting
    internal fun setIconBitmapValueForTesting(value: BitmapCache.BitmapValue?) {
        iconBitmapValue = value
    }

    /**
     * Áp preset neo 9-grid, tính offsetX/offsetY từ kích thước watermark thật ([layoutShader])
     * so với [drawableBounds] để cạnh phải/dưới không tràn mép, rồi báo ra ngoài qua đúng
     * callback [onOffsetChanged] mà luồng kéo thả tay đang dùng.
     */
    fun applyAnchor(anchor: Anchor, marginPercent: Float) {
        if (curImageInfo.obtainTileMode() != Shader.TileMode.CLAMP) {
            return
        }
        val boundsW = drawableBounds.width()
        val boundsH = drawableBounds.height()
        val wmW = (layoutShader?.width ?: 0).toFloat()
        val wmH = (layoutShader?.height ?: 0).toFloat()
        if (boundsW <= 0f || boundsH <= 0f) {
            return
        }
        val (offsetX, offsetY) = anchor.toOffset(marginPercent, wmW / boundsW, wmH / boundsH)
        curImageInfo = curImageInfo.copy(offsetX = offsetX, offsetY = offsetY)
        invalidate()
        onOffsetChanged(curImageInfo)
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
        return touchRect.right < drawableBounds.left ||
            touchRect.left > drawableBounds.right ||
            touchRect.top > drawableBounds.bottom ||
            touchRect.bottom < drawableBounds.top
    }

    private fun isTouchWaterMark(event: MotionEvent): Boolean {
        if (curImageInfo.obtainTileMode() != Shader.TileMode.CLAMP) {
            return false
        }
        val shader = layoutShader ?: return false
        val bounds = drawableBounds
        val waterMarkX = bounds.left + curImageInfo.offsetX * bounds.width()
        val waterMarkY = bounds.top + curImageInfo.offsetY * bounds.height()
        return event.x > waterMarkX &&
            event.x < waterMarkX + shader.width &&
            event.y > waterMarkY &&
            event.y < waterMarkY + shader.height
    }

    private var animator: Animator? = null

    private fun backToCenter(
        post: (info: ImageInfo, x: Float, y: Float) -> Unit
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

        // Chụp textSize hiện tại làm mốc NGAY KHI bắt đầu mỗi phiên pinch, và reset mScaleFactor
        // về 1f — tránh dùng mScaleFactor cũ (tích luỹ từ phiên pinch trước, có thể đã sát biên
        // 0.1/5.0) làm mốc cho phiên mới, nhất là sau khi textSize vừa bị đổi từ nguồn khác
        // (slider TextSizePbFragment) khiến pinch tiếp theo nhảy vọt/kẹt ở MAX_TEXT_SIZE ngay
        // lập tức và có cảm giác "không hoạt động".
        private var baselineTextSize = DEFAULT_TEXT_SIZE

        // ENH-16: giá trị textSize MỚI NHẤT tính được mỗi frame pinch, kể cả frame bị throttle bỏ
        // qua rebuild — dùng để áp dụng chính xác lúc nhả tay (onScaleEnd), không mất độ chính xác.
        private var pendingTextSize = DEFAULT_TEXT_SIZE
        private var lastShaderRebuildAtMs = 0L

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            baselineTextSize = config?.textSize ?: DEFAULT_TEXT_SIZE
            pendingTextSize = baselineTextSize
            mScaleFactor = 1f
            // Frame đầu tiên của phiên pinch mới luôn được rebuild ngay (không đợi throttle).
            lastShaderRebuildAtMs = 0L
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            mScaleFactor *= detector.scaleFactor
            mScaleFactor = mScaleFactor.coerceAtLeast(0.1f).coerceAtMost(5.0f)
            // Don't let the object get too small or too large.
            val textSize = baselineTextSize * if (mScaleFactor > 1f) {
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
            pendingTextSize = textSize
            val now = SystemClock.elapsedRealtime()
            if (!shouldRebuildShader(now, lastShaderRebuildAtMs)) {
                // ENH-16: bỏ qua rebuild shader ở frame này — pendingTextSize vẫn được cập nhật
                // để onScaleEnd áp dụng đúng giá trị cuối cùng.
                return true
            }
            lastShaderRebuildAtMs = now
            Log.i(TAG, "onScale $mScaleFactor, textSize: ${config?.textSize} ==> $textSize")
            config = config?.copy(textSize = textSize)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            Log.i(TAG, "onScaleEnd $mScaleFactor")
            // ENH-16: đảm bảo giá trị CUỐI CÙNG luôn phản ánh đúng lúc nhả tay, kể cả khi frame
            // cuối cùng của pinch bị throttle bỏ qua rebuild.
            if (config?.textSize != pendingTextSize) {
                config = config?.copy(textSize = pendingTextSize)
                invalidate()
            }
            val textSize = (config?.textSize ?: DEFAULT_TEXT_SIZE)
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
        mScaleDetector.onTouchEvent(event)
        if (mScaleDetector.isInProgress) {
            return true
        }
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
         * ENH-16: pinch bắn `onScale` 60-120 lần/giây, mỗi lần trước đây rebuild shader từ đầu
         * (2 lần cấp phát Bitmap trong `buildIconBitmapShader`/`buildTextBitmapShader`) — throttle
         * còn ~25fps (đủ mượt để mắt không thấy giật cục) để giảm hẳn tần suất cấp phát/GC thật
         * sự là nguyên nhân lag (không phải ghi DataStore, xem ENH-02).
         */
        const val SHADER_REBUILD_THROTTLE_MS = 40L

        /** Hàm thuần để test biên giới không cần dựng View/ScaleGestureDetector thật. */
        fun shouldRebuildShader(nowMs: Long, lastRebuildAtMs: Long, throttleMs: Long = SHADER_REBUILD_THROTTLE_MS): Boolean =
            nowMs - lastRebuildAtMs >= throttleMs

        /** IDEA-06: alpha đầy đủ (không trong suốt) — dùng khi chỉ cần lấy RGB đen/trắng thuần. */
        const val ALPHA_OPAQUE = 255

        /** IDEA-06: sàn alpha tối thiểu khi auto-contrast bật, đảm bảo chữ không quá mờ dù user để alpha thấp. */
        const val AUTO_CONTRAST_MIN_ALPHA = 160

        /** IDEA-06: hệ số quy đổi textSize → cạnh ô vuông lấy mẫu luminance (ước lượng, xem doc [applyAutoContrastIfEnabled]). */
        const val AUTO_CONTRAST_SAMPLE_SIZE_MULTIPLIER = 6f

        /** IDEA-06: alpha hiệu dụng khi auto-contrast bật — không bao giờ thấp hơn [AUTO_CONTRAST_MIN_ALPHA]. Hàm thuần, dễ test. */
        fun readableAlpha(configAlpha: Int): Int = configAlpha.coerceAtLeast(AUTO_CONTRAST_MIN_ALPHA)

        /** IDEA-06: auto-contrast chỉ áp dụng cho layer chính ở chế độ Text. Hàm thuần, dễ test. */
        fun shouldApplyAutoContrast(config: WaterMark): Boolean =
            config.autoContrastEnabled && config.markMode == WaterMarkRepository.MarkMode.Text

        /** IDEA-06: vùng lấy mẫu luminance, toạ độ theo bitmap gốc (không phải toạ độ View). */
        data class SampleRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
            val isValid: Boolean get() = right > left && bottom > top
        }

        /**
         * IDEA-06: tính vùng lấy mẫu — REPEAT/MIRROR/DECAL (mọi tileMode khác CLAMP) coi như
         * watermark phủ khắp ảnh nên lấy mẫu TOÀN BỘ bitmap; CLAMP (1 vị trí neo) lấy 1 ô vuông
         * [sampleSizePx] cạnh, gốc tại (offsetX, offsetY) — đúng quy ước "toạ độ theo tỉ lệ
         * drawableBounds" đã dùng ở [applyAnchor]/[updateWaterMarkOffset]. Hàm thuần, dễ test.
         */
        fun computeAutoContrastSampleRegion(
            tileMode: Shader.TileMode,
            offsetX: Float,
            offsetY: Float,
            sampleSizePx: Int,
            bitmapWidth: Int,
            bitmapHeight: Int
        ): SampleRegion {
            if (bitmapWidth <= 0 || bitmapHeight <= 0) return SampleRegion(0, 0, 0, 0)
            if (tileMode != Shader.TileMode.CLAMP) {
                return SampleRegion(0, 0, bitmapWidth, bitmapHeight)
            }
            val left = (offsetX * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
            val top = (offsetY * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
            val right = (left + sampleSizePx).coerceAtMost(bitmapWidth)
            val bottom = (top + sampleSizePx).coerceAtMost(bitmapHeight)
            return SampleRegion(left, top, right, bottom)
        }

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
            bitmapHeight: Int
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
            coroutineContext: CoroutineContext
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
                scaledW,
                scaledH,
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
            coroutineContext: CoroutineContext
        ): WaterMarkShader? = withContext(coroutineContext) {
            if (config.text.isBlank()) {
                return@withContext null
            }
            val showDebugRect = config.enableBounds
            var maxLineWidth = 0
            val tileMode = imageInfo.obtainTileMode()
            // calculate the max width of all lines — dùng offset tuyến tính thực tế (không phải
            // indexOf, vốn luôn trả vị trí lần xuất hiện ĐẦU TIÊN nên sai lệch khi có dòng trùng
            // nội dung hoặc dòng rỗng).
            var lineCursor = 0
            config.text.split("\n").forEach {
                val startIndex = lineCursor
                val endIndex = (startIndex + it.length).coerceAtMost(config.text.length)
                val lineWidth = textPaint.measureText(
                    /* text = */ config.text,
                    /* start = */ startIndex,
                    /* end = */ endIndex
                ).toInt()
                maxLineWidth = max(maxLineWidth, lineWidth)
                lineCursor = endIndex + 1 // +1 cho ký tự '\n' vừa tiêu thụ
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

            // FEAT-11: viền/bóng/nền pill vẽ RA NGOÀI biên chữ gốc — cộng thêm biên SAU khi đã
            // nhân hệ số hGap/vGap (không phải trước) để không bị co lại theo tỉ lệ khi user đặt
            // gap âm; effect tắt hết (mặc định) thì +0, không đổi hành vi cũ.
            val effectMarginPx = (TextEffectRenderer.marginPx(config) * 2).toInt()
            val finalWidth = (adjustHorizontalGap(config, fixWidth.toInt()) + effectMarginPx).coerceAtLeast(1)
            val finalHeight = (adjustVerticalGap(config, fixHeight.toInt()) + effectMarginPx).coerceAtLeast(1)
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
                    ((finalHeight - staticLayout.height) / 2).toFloat()
                )
                // FEAT-11: pill nền + viền vẽ TRƯỚC chữ chính (layer dưới cùng), cùng gốc toạ độ
                // (0,0)-(staticLayout.width, staticLayout.height) mà staticLayout.draw() dùng.
                if (config.textEffectPillBackground) {
                    TextEffectRenderer.drawPillBackground(
                        canvas,
                        config,
                        staticLayout.width.toFloat(),
                        staticLayout.height.toFloat()
                    )
                }
                if (config.textEffectStroke) {
                    TextEffectRenderer.drawStrokeOutline(canvas, config, textPaint, maxLineWidth)
                }
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
