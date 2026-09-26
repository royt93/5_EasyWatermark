package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.withSave
import androidx.documentfile.provider.DocumentFile
import androidx.palette.graphics.Palette
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.JobStateResolver
import com.mckimquyen.watermark.data.model.MediaStoreInsertResolver
import com.mckimquyen.watermark.data.model.MediaStoreWriteResolver
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.widget.WaterMarkImageView
import com.mckimquyen.watermark.utils.FileUtils.Companion.outPutFolderName
import com.mckimquyen.watermark.utils.QrCodeGenerator
import com.mckimquyen.watermark.utils.bitmap.BitmapRecycleGuard
import com.mckimquyen.watermark.utils.bitmap.ExifBorderRenderer
import com.mckimquyen.watermark.utils.bitmap.ExifFramePalette
import com.mckimquyen.watermark.utils.bitmap.OutputImageUtils
import com.mckimquyen.watermark.utils.bitmap.applyCropAndRotate
import com.mckimquyen.watermark.utils.bitmap.calculateInSampleSize
import com.mckimquyen.watermark.utils.bitmap.decodeBitmapFromUri
import com.mckimquyen.watermark.utils.bitmap.decodeSampledBitmapFromResource
import com.mckimquyen.watermark.utils.ktx.applyConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * ENH-01: trích xuất nguyên vẹn từ `MainViewModel.generateImage()`/`generateList()` — logic export
 * batch giờ chạy trong `BatchExportWorker` (WorkManager, sống sót khi app xuống nền) thay vì
 * `viewModelScope`, nên không thể là hàm private của ViewModel nữa. Nhận cấu hình qua
 * [ExportSettings] (đọc 1 lần trước batch) thay vì đọc trực tiếp `StateFlow`/`LiveData` như cũ —
 * class này không có `viewModelScope` để cache Flow.
 */
class BatchExportEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val exportNaming: ExportNaming
) {
    private val matrixValues = FloatArray(9)

    /**
     * FEAT-13: caption riêng ("" hợp lệ = cố ý không watermark ảnh này) ghi đè watermark text
     * chung — DÙNG CHUNG giữa [generateImage] (export thật) và [generatePreviewBitmap] (preview),
     * tránh 2 nơi tự suy diễn cùng 1 rule rồi lệch nhau (từng gây bug: preview skip vẽ khi rỗng
     * nhưng export thật không skip, ảnh xuất ra bị tô đen kín). `internal` (thay vì `private`) chỉ
     * để test truy cập trực tiếp — Robolectric ở máy build này không rasterize pixel thật
     * (`getPixel`/`ShadowCanvas.description` đều không phản ánh nội dung đã vẽ) nên không thể
     * assert bằng ảnh xuất ra, phải test trực tiếp điều kiện quyết định có vẽ hay không.
     */
    internal fun resolveBaseText(imageInfo: ImageInfo, config: WaterMark, proofingMode: Boolean = false): String {
        val text = imageInfo.caption ?: config.text
        return if (proofingMode && imageInfo.caption != null) ProofingMode.withSeq(text) else text
    }

    /** Xem [resolveBaseText] — cùng lý do `internal`. */
    internal fun shouldSkipTextWatermark(markMode: WaterMarkRepository.MarkMode, baseText: String): Boolean =
        markMode == WaterMarkRepository.MarkMode.Text && baseText.isBlank()

    /**
     * FEAT-03: vẽ toàn bộ layer PHỤ lên [canvas] theo đúng z-order (index 0 = dưới cùng), SAU
     * layer chính mà caller đã vẽ xong. Dùng CHUNG cho cả 3 nơi vẽ watermark
     * ([generateImage]/[generatePreviewBitmap]/[generateCompareBitmaps]) — 3 hàm này CỐ TÌNH
     * không dùng chung logic layer CHÍNH (xem doc ở [generateCompareBitmaps]) để không đổi hành
     * vi code cũ đã test, nhưng đây là logic HOÀN TOÀN MỚI nên dùng chung an toàn, tránh viết lặp
     * 3 lần. Lỗi decode icon của 1 layer phụ → bỏ qua layer đó, KHÔNG fail cả export (khác layer
     * chính) — layer phụ là tính năng cộng thêm.
     *
     * Yêu cầu caller: khối vẽ layer CHÍNH ở nhánh CLAMP phải bọc trong `canvas.withSave { }`
     * (không còn để `translate()` trần) — nếu không, offset của layer phụ tính từ đây sẽ cộng dồn
     * lên transform layer chính thay vì tính từ gốc toạ độ ảnh.
     */
    private suspend fun drawExtraLayers(
        canvas: Canvas,
        bitmapWidth: Int,
        bitmapHeight: Int,
        imageInfo: ImageInfo,
        extraLayers: List<WatermarkLayer>,
        contentResolver: ContentResolver
    ) {
        if (extraLayers.isEmpty()) return
        val tileMode = imageInfo.obtainTileMode()
        extraLayers.forEach { layer ->
            val layerConfig = layer.toWaterMark()
            val textPaint = TextPaint().applyConfig(imageInfo, layerConfig, isScale = false)
            val shader = when (layer.markMode) {
                WaterMarkRepository.MarkMode.Text -> WaterMarkImageView.buildTextBitmapShader(
                    imageInfo = imageInfo,
                    config = layerConfig,
                    textPaint = textPaint,
                    coroutineContext = Dispatchers.IO
                )

                WaterMarkRepository.MarkMode.Image -> {
                    val iconResult = decodeSampledBitmapFromResource(
                        context = appContext,
                        resolver = contentResolver,
                        uri = layer.iconUri,
                        reqWidth = bitmapWidth,
                        reqHeight = bitmapHeight
                    )
                    val iconValue = iconResult.data ?: return@forEach
                    iconValue.retain()
                    try {
                        val srcBitmap = iconValue.bitmap ?: return@forEach
                        WaterMarkImageView.buildIconBitmapShader(
                            imageInfo = imageInfo,
                            srcBitmap = srcBitmap,
                            config = layerConfig,
                            textPaint = textPaint,
                            scale = true,
                            coroutineContext = Dispatchers.IO
                        )
                    } finally {
                        iconValue.release()
                    }
                }
            } ?: return@forEach

            val paint = Paint().apply { this.shader = shader.bitmapShader }
            if (tileMode == Shader.TileMode.CLAMP) {
                val (offsetX, offsetY) = Anchor.obtain(layer.anchor).toOffset(
                    layer.marginPercent,
                    shader.width / bitmapWidth.toFloat(),
                    shader.height / bitmapHeight.toFloat()
                )
                canvas.withSave {
                    translate(offsetX * bitmapWidth, offsetY * bitmapHeight)
                    drawRect(0f, 0f, shader.width.toFloat(), shader.height.toFloat(), paint)
                }
            } else {
                canvas.drawRect(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat(), paint)
            }
        }
    }

    /** Snapshot cấu hình cho 1 lần export batch — đọc 1 lần trước khi loop, không đổi giữa chừng. */
    data class ExportSettings(
        val config: WaterMark,
        val outputFormat: Bitmap.CompressFormat,
        val compressLevel: Int,
        val maxOutputLongEdge: Int,
        val copyright: String,
        val outputNamePattern: String,
        val conflictPolicy: com.mckimquyen.watermark.data.model.ConflictPolicy = com.mckimquyen.watermark.data.model.ConflictPolicy.KEEP_BOTH,
        // FEAT-15: null = hành vi cũ (MediaStore Pictures/WaterMarkCreator/); khác null = ghi qua
        // SAF (DocumentFile) vào đúng thư mục user đã chọn thay vì luôn cố định.
        val outputDirectoryUri: Uri? = null,
        val proofingMode: Boolean = false
    )

    /**
     * Export tuần tự từng ảnh trong [infoList], gọi [onProgress] sau mỗi bước đổi `jobState`
     * (Ready→Ing→Success/Failure), `onProgress(null)` khi xong toàn bộ batch (giống hành vi cũ
     * `saveProcess.postValue(null)`). Trả về list COPY MỚI (ENH-08, `ImageInfo` bất biến).
     *
     * `CancellationException` được rethrow ngay (KHÔNG bị nuốt bởi `catch (e: Exception)` như các
     * nhánh lỗi khác) — bắt buộc để `BatchExportWorker` huỷ giữa batch hoạt động đúng (ENH-01 AC2):
     * `CoroutineWorker` huỷ bằng cách cancel coroutine của `doWork()`, nếu nuốt mất
     * `CancellationException` thì huỷ sẽ không có tác dụng, batch cứ chạy tiếp tới hết.
     */
    suspend fun generateList(
        contentResolver: ContentResolver,
        viewInfo: ViewInfo,
        infoList: List<ImageInfo>?,
        settings: ExportSettings,
        onProgress: (ImageInfo?) -> Unit
    ): Result<List<ImageInfo>> =
        withContext(Dispatchers.Default) {
            if (infoList.isNullOrEmpty()) {
                return@withContext Result.failure(null, MainViewModel.TYPE_ERROR_NOT_IMG)
            }
            val updatedList = infoList.mapIndexed { index, original ->
                // FEAT-17: ảnh bị skip tại grid preview — giữ nguyên (JobState.Ready), không render/ghi
                // file, không tính vào doneCount (onProgress(null) là no-op ở BatchExportWorker).
                if (original.isSkippedInExport) {
                    onProgress(null)
                    return@mapIndexed original
                }
                var info = original
                try {
                    info = info.copy(jobState = JobState.Ing)
                    onProgress(info)
                    val generateResult = generateImage(contentResolver, viewInfo, info, index, settings)
                    // generateImage() có thể trả Result.failure (không throw) khi lỗi I/O/logic —
                    // JobStateResolver kiểm tra isFailure() thay vì luôn coi là Success (BUG-03).
                    info = info.copy(result = generateResult, jobState = JobStateResolver.resolve(generateResult))
                    onProgress(info)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (fne: FileNotFoundException) {
                    fne.printStackTrace()
                    val failResult = Result.failure(null, code = MainViewModel.TYPE_ERROR_FILE_NOT_FOUND)
                    info = info.copy(result = failResult, jobState = JobState.Failure(failResult))
                    onProgress(info)
                } catch (oom: OutOfMemoryError) {
                    val failResult = Result.failure(null, code = MainViewModel.TYPE_ERROR_SAVE_OOM)
                    info = info.copy(result = failResult, jobState = JobState.Failure(failResult))
                    onProgress(info)
                } catch (e: Exception) {
                    // Exception ngoài 2 loại trên (vd SecurityException khi mất quyền MediaStore
                    // giữa batch) trước đây không có handler, làm crash cả batch — chỉ đánh dấu
                    // ảnh này lỗi và tiếp tục ảnh kế tiếp.
                    e.printStackTrace()
                    val failResult = Result.failure(null, code = MainViewModel.TYPE_ERROR_SAVE_UNKNOWN, message = e.message)
                    info = info.copy(result = failResult, jobState = JobState.Failure(failResult))
                    onProgress(info)
                }
                Log.i("generateList", "${info.uri} : ${info.result}")
                info
            }
            onProgress(null)
            com.mckimquyen.watermark.utils.bitmap.BitmapCache.clearCache()
            return@withContext Result.success(updatedList)
        }

    suspend fun generateImage(
        contentResolver: ContentResolver,
        viewInfo: ViewInfo,
        originalImageInfo: ImageInfo,
        index: Int,
        settings: ExportSettings
    ): Result<Uri> =
        withContext(Dispatchers.IO) {
            // ENH-08: imageInfo bất biến (mọi field val) — width/height/inSample/scaleX/scaleY/
            // exifModel tính ra trong lúc export chỉ dùng cục bộ trong hàm này (không caller nào
            // đọc lại sau khi hàm return), nên reassign biến local qua copy() thay vì mutate
            // instance được truyền vào (tránh side-effect ngoài ý muốn lên object caller đang giữ).
            // IDEA-13: proof phải phủ kín ảnh — ép REPEAT bất kể user đặt CLAMP (1 vị trí) cho ảnh này.
            var imageInfo = if (settings.proofingMode) {
                originalImageInfo.copy(tileMode = Shader.TileMode.REPEAT.ordinal)
            } else {
                originalImageInfo
            }
            // ENH-14: downsample ngay lúc decode khi user đã chọn resize output (maxOutputLongEdge
            // != 0) — giảm peak memory khi vẽ watermark trên ảnh 12-48MP không cần thiết phải ở
            // full-res nếu output cuối cùng sẽ bị resize nhỏ lại. "Original" (0) giữ hành vi cũ.
            val rect = decodeBitmapFromUri(appContext, contentResolver, imageInfo.uri, settings.maxOutputLongEdge)
            if (rect.isFailure()) {
                return@withContext Result.extendMsg(rect)
            }
            val decodedBitmap = rect.data?.bitmap
                ?: return@withContext Result.failure(
                    data = null,
                    code = "-1",
                    message = "Decoded bitmap from uri is null."
                )
            // OOM-OPT: nếu decodedBitmap đã mutable (nhờ inMutable = true), tái dùng trực tiếp
            // thay vì copy tạo bản sao thứ hai gây spike RAM (tránh OOM trên ảnh 4K/8K/108MP).
            var mutableBitmap = if (decodedBitmap.isMutable) {
                decodedBitmap
            } else {
                val copied = decodedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    ?: return@withContext Result.failure(
                        data = null,
                        code = "-1",
                        message = "Copy bitmap from uri failed."
                    )
                if (decodedBitmap !== copied && !decodedBitmap.isRecycled) {
                    decodedBitmap.recycle()
                }
                copied
            }

            // FEAT-16: crop/rotate áp NGAY khi có bitmap khả biến, TRƯỚC khi vẽ watermark — cùng
            // thứ tự với WaterMarkImageView (preview) để export khớp preview. mutableBitmap ở đây
            // do hàm này sở hữu hoàn toàn (không qua BitmapCache) nên tự recycle bản cũ an toàn.
            if (imageInfo.rotationDegrees != 0f || imageInfo.cropRect != null) {
                val transformed = applyCropAndRotate(mutableBitmap, imageInfo.rotationDegrees, imageInfo.cropRect)
                if (transformed !== mutableBitmap && !mutableBitmap.isRecycled) {
                    mutableBitmap.recycle()
                }
                mutableBitmap = transformed
            }

            // BUG-21: theo dõi bitmap đang "sở hữu" (chưa recycle) — mọi early-return lỗi bên
            // dưới (config/icon/MediaStore) đều recycle qua finally, không chỉ nhánh thành công
            // (nhánh thành công tự gọi release() nên finally là no-op). Xem [BitmapRecycleGuard].
            val bitmapGuard = BitmapRecycleGuard(mutableBitmap)
            try {
                val inSample = calculateInSampleSize(
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    reqWidth = WaterMarkImageView.calculateDrawLimitWidth(viewInfo.width, viewInfo.paddingLeft),
                    reqHeight = WaterMarkImageView.calculateDrawLimitHeight(viewInfo.height, viewInfo.paddingRight)
                )
                imageInfo = imageInfo.copy(
                    width = mutableBitmap.width,
                    height = mutableBitmap.height,
                    exifModel = rect.data?.exifModel
                )
                val tmpConfig = settings.config
                imageInfo = imageInfo.copy(inSample = inSample)
                val canvas = Canvas(mutableBitmap)
                // generate matrix of drawable
                val imageMatrix = WaterMarkImageView.adjustMatrix(
                    srcMatrix = Matrix(),
                    viewWidth = viewInfo.width,
                    viewHeight = viewInfo.height,
                    paddingLeft = viewInfo.paddingLeft,
                    paddingTop = viewInfo.paddingTop,
                    bitmapWidth = imageInfo.width,
                    bitmapHeight = imageInfo.height
                )
                Log.i(
                    "generateImage",
                    """
                        imageMatrix = $imageMatrix,
                        inSample = $inSample,
                        imageInfo = $imageInfo
                        viewInfo = $viewInfo,
                        bitmapW = ${mutableBitmap.width}
                        bitmapH = ${mutableBitmap.height},
                    """.trimIndent()
                )
                // calculate the scale factor
                imageMatrix.getValues(matrixValues)
                imageInfo = imageInfo.copy(
                    scaleX = 1 / matrixValues[Matrix.MSCALE_X],
                    scaleY = 1 / matrixValues[Matrix.MSCALE_X]
                )
                val bitmapPaint = TextPaint().applyConfig(imageInfo, tmpConfig, isScale = false)
                val layoutPaint = Paint()
                // Thiếu guard skipTextWatermark bên dưới từng là bug: layoutPaint (Paint() mặc
                // định màu đen, alpha 255) vẫn bị canvas.drawRect() tô kín đè lên ảnh vì shader
                // null, biến "để trống caption = không watermark" thành "ảnh xuất ra bị đen kín".
                val baseText = resolveBaseText(imageInfo, tmpConfig, settings.proofingMode)
                if (!shouldSkipTextWatermark(tmpConfig.markMode, baseText)) {
                    val shader = when (tmpConfig.markMode) {
                        WaterMarkRepository.MarkMode.Text -> {
                            // Resolve dynamic text tokens (e.g. {date}, {filename}, {iso}) per image at export time.
                            val resolvedText = exportNaming.resolveTextTokens(baseText, imageInfo, contentResolver, index)
                            WaterMarkImageView.buildTextBitmapShader(
                                imageInfo = imageInfo,
                                config = tmpConfig.copy(text = resolvedText),
                                textPaint = bitmapPaint,
                                coroutineContext = Dispatchers.IO
                            )
                        }

                        WaterMarkRepository.MarkMode.Image -> {
                            // IDEA-07: QR động — sinh bitmap QR RIÊNG cho ảnh này ngay trong RAM
                            // (khác `tmpConfig.iconUri` tĩnh dùng chung cả batch), dùng thẳng làm
                            // srcBitmap thay vì đi qua decode/BitmapCache như nhánh tĩnh bên dưới
                            // (không cần ghi file tạm, không cần retain/release cache).
                            if (tmpConfig.qrDynamicEnabled) {
                                val qrContent = exportNaming.resolveQrContent(
                                    template = tmpConfig.qrContentTemplate.ifBlank {
                                        ExportNaming.DEFAULT_QR_CONTENT_TEMPLATE
                                    },
                                    imageInfo = imageInfo,
                                    contentResolver = contentResolver,
                                    index = index,
                                    portfolioLink = tmpConfig.qrPortfolioLink
                                )
                                val qrBitmap = QrCodeGenerator.generate(
                                    content = qrContent,
                                    size = QrCodeGenerator.DEFAULT_SIZE
                                ) ?: return@withContext Result.failure(
                                    data = null,
                                    code = "-1",
                                    message = "QR dynamic content generate failed"
                                )
                                // BUG-05/ENH-15: cùng nguyên tắc nhánh tĩnh bên dưới — pixel đã được
                                // copy vào bitmap riêng của shader bên trong buildIconBitmapShader
                                // (Bitmap.createScaledBitmap + vẽ vào targetBitmap), qrBitmap gốc
                                // (512x512 ARGB_8888, ~1MB) không cần giữ sau đó — recycle ngay
                                // tránh cộng dồn qua từng ảnh trong batch (nhiều ảnh = nhiều bitmap
                                // rác chờ GC, có thể OOM native trên máy thấp cấu hình/API cũ).
                                try {
                                    WaterMarkImageView.buildIconBitmapShader(
                                        imageInfo = imageInfo,
                                        srcBitmap = qrBitmap,
                                        config = tmpConfig,
                                        textPaint = bitmapPaint,
                                        scale = true,
                                        coroutineContext = Dispatchers.IO
                                    )
                                } finally {
                                    if (!qrBitmap.isRecycled) qrBitmap.recycle()
                                }
                            } else {
                                val iconBitmapRect = decodeSampledBitmapFromResource(
                                    context = appContext,
                                    resolver = contentResolver,
                                    uri = tmpConfig.iconUri,
                                    reqWidth = viewInfo.width,
                                    reqHeight = viewInfo.height
                                )
                                if (iconBitmapRect.isFailure() || iconBitmapRect.data == null) {
                                    return@withContext Result.failure(
                                        data = null,
                                        code = "-1",
                                        message = "decodeSampledBitmapFromResource == null"
                                    )
                                }
                                // ENH-15: giữ (retain) bitmap này trong lúc dùng để BitmapCache không
                                // recycle nó nếu bị evict giữa chừng (batch nhiều ảnh có thể evict entry
                                // đang xử lý) — release ngay sau khi build shader xong (đã copy pixel vào
                                // shader riêng, không cần iconBitmap gốc nữa).
                                val iconBitmapValue = iconBitmapRect.data!!
                                iconBitmapValue.retain()
                                try {
                                    WaterMarkImageView.buildIconBitmapShader(
                                        imageInfo = imageInfo,
                                        srcBitmap = iconBitmapValue.bitmap!!,
                                        config = tmpConfig,
                                        textPaint = bitmapPaint,
                                        scale = true,
                                        coroutineContext = Dispatchers.IO
                                    )
                                } finally {
                                    iconBitmapValue.release()
                                }
                            }
                        }
                    }

                    layoutPaint.shader = shader?.bitmapShader

                    if (imageInfo.obtainTileMode() == Shader.TileMode.CLAMP) {
                        // FEAT-03: bọc withSave — offset layer PHỤ (drawExtraLayers) tính từ gốc
                        // toạ độ ảnh, không được cộng dồn lên translate của layer chính này.
                        canvas.withSave {
                            translate(
                                0 + imageInfo.offsetX * mutableBitmap.width,
                                0 + imageInfo.offsetY * mutableBitmap.height
                            )
                            drawRect(
                                /* left = */ 0f,
                                /* top = */ 0f,
                                /* right = */ (shader?.width ?: 0).toFloat(),
                                /* bottom = */ (shader?.height ?: 0).toFloat(),
                                /* paint = */ layoutPaint
                            )
                        }
                    } else {
                        canvas.drawRect(
                            /* left = */ 0f,
                            /* top = */ 0f,
                            /* right = */ mutableBitmap.width.toFloat(),
                            /* bottom = */ mutableBitmap.height.toFloat(),
                            /* paint = */ layoutPaint
                        )
                    }
                }

                // FEAT-03: layer PHỤ vẽ SAU layer chính (dù layer chính có bị skip do rỗng text
                // hay không) — trước khi build khung EXIF (khung phải bao trọn mọi layer).
                drawExtraLayers(
                    canvas = canvas,
                    bitmapWidth = mutableBitmap.width,
                    bitmapHeight = mutableBitmap.height,
                    imageInfo = imageInfo,
                    extraLayers = tmpConfig.extraLayers,
                    contentResolver = contentResolver
                )

                val finalExportBitmap = if (tmpConfig.enableExif && imageInfo.exifModel != null && !imageInfo.exifModel!!.isEmpty()) {
                    val frameStyle = ExifFrameStyle.obtain(tmpConfig.exifFrameStyle)
                    val expandedBitmap = ExifBorderRenderer.buildExifBorderBitmap(
                        source = mutableBitmap,
                        eModel = imageInfo.exifModel!!,
                        style = frameStyle,
                        // FEAT-14 Custom Frame Builder — null nếu user chưa tuỳ chỉnh, giữ hành vi gốc.
                        bandColor = tmpConfig.exifBandColor,
                        bandThicknessPercent = tmpConfig.exifBandThicknessPercent,
                        useSerifCaption = tmpConfig.exifUseSerifCaption,
                        // IDEA-18 — Palette tự thu nhỏ bitmap (~112x112) trước khi đếm màu, rẻ kể cả ảnh full-res.
                        frameColors = if (tmpConfig.exifAutoPalette) {
                            ExifFramePalette.resolve(Palette.from(mutableBitmap).generate().dominantSwatch?.rgb, frameStyle)
                        } else {
                            null
                        }
                    )
                    // mutableBitmap đã được vẽ (drawBitmap) sang expandedBitmap, không còn dùng nữa
                    // (finalExportBitmap trỏ sang expandedBitmap) — recycle để tránh giữ 2 bitmap
                    // full-res cùng lúc (BUG-05).
                    mutableBitmap.recycle()
                    bitmapGuard.replace(expandedBitmap)
                    expandedBitmap
                } else {
                    mutableBitmap
                }

                // Resize cạnh dài khi lưu (0 = giữ nguyên kích thước gốc).
                val exportBitmap = OutputImageUtils.resizeIfNeeded(finalExportBitmap, settings.maxOutputLongEdge)
                // resizeIfNeeded trả về CÙNG instance khi maxOutputLongEdge=0 (không resize) — chỉ
                // recycle finalExportBitmap khi thực sự đã tạo bitmap mới, tránh recycle nhầm bitmap
                // đang dùng (BUG-05).
                if (exportBitmap !== finalExportBitmap && !finalExportBitmap.isRecycled) {
                    finalExportBitmap.recycle()
                }
                bitmapGuard.replace(exportBitmap)

                val conflictPolicy = settings.conflictPolicy
                val rawOutputName = exportNaming.generateOutputName(
                    contentResolver,
                    imageInfo,
                    index,
                    settings.outputNamePattern,
                    settings.outputFormat
                )

                return@withContext if (settings.outputDirectoryUri != null) {
                    writeToCustomDirectory(contentResolver, exportBitmap, rawOutputName, settings, bitmapGuard)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val imageCollection =
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val existingUri = if (conflictPolicy != com.mckimquyen.watermark.data.model.ConflictPolicy.KEEP_BOTH) {
                        exportNaming.queryExistingMediaUri(contentResolver, rawOutputName)
                    } else {
                        null
                    }

                    if (existingUri != null && conflictPolicy == com.mckimquyen.watermark.data.model.ConflictPolicy.SKIP) {
                        exportBitmap.recycle()
                        bitmapGuard.release()
                        return@withContext Result.success(existingUri)
                    }

                    val finalOutputName = when {
                        existingUri != null && conflictPolicy == com.mckimquyen.watermark.data.model.ConflictPolicy.RENAME_VERSION -> {
                            exportNaming.resolveVersionedName(rawOutputName) { candidate ->
                                exportNaming.isMediaFileExists(contentResolver, candidate)
                            }
                        }
                        else -> rawOutputName
                    }

                    val (targetUri, isNewRow) = if (existingUri != null && conflictPolicy == com.mckimquyen.watermark.data.model.ConflictPolicy.OVERWRITE) {
                        val updateDetails = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        contentResolver.update(existingUri, updateDetails, null, null)
                        existingUri to false
                    } else {
                        val imageDetail = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, finalOutputName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/${exportNaming.trapOutputExtension(settings.outputFormat)}")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$outPutFolderName/")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val insertResult = MediaStoreInsertResolver.resolve(
                            contentResolver.insert(imageCollection, imageDetail),
                            MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_INSERT
                        )
                        if (insertResult.isFailure()) return@withContext insertResult
                        insertResult.data!! to true
                    }

                    val imageContentUri = targetUri
                    // BUG-19: openFileDescriptor() có thể trả null, và compress() có thể trả false
                    // (trước đây cả 2 bị bỏ qua → báo "thành công" giả + để lại row IS_PENDING rác).
                    val writeResult = try {
                        val pfd = contentResolver.openFileDescriptor(imageContentUri, "w", null)
                        val compressOk = pfd?.use { p ->
                            exportBitmap.compress(
                                /* format = */ settings.outputFormat,
                                /* quality = */ settings.compressLevel,
                                /* stream = */ FileOutputStream(p.fileDescriptor)
                            )
                        } ?: false
                        MediaStoreWriteResolver.resolve(
                            fdAvailable = pfd != null,
                            compressSucceeded = compressOk,
                            errorCode = MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_WRITE
                        )
                    } catch (e: Exception) {
                        Result.failure<Unit>(data = null, code = MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_WRITE, message = e.message)
                    }
                    if (writeResult.isFailure()) {
                        // Ghi thất bại → xoá row IS_PENDING nếu mới tạo thay vì để lại file 0-byte/lỗi trong gallery.
                        if (isNewRow) {
                            contentResolver.delete(imageContentUri, null, null)
                        }
                        return@withContext Result.extendMsg(writeResult)
                    }
                    // Đã compress xong, không còn dùng bitmap này nữa (BUG-05).
                    exportBitmap.recycle()
                    bitmapGuard.release()
                    val finalDetails = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    contentResolver.update(imageContentUri, finalDetails, null, null)
                    exportNaming.applyCopyrightExif(contentResolver, imageContentUri, settings.copyright, settings.outputFormat)
                    Result.success(imageContentUri)
                } else {
                    // need request write_storage permission
                    // should check Pictures folder exist
                    val picturesFile: File =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            ?: return@withContext Result.failure(
                                data = null,
                                code = "-1",
                                message = "Can't get pictures directory."
                            )
                    if (!picturesFile.exists()) {
                        picturesFile.mkdir()
                    }
                    val mediaDir = File(picturesFile, outPutFolderName)

                    if (!mediaDir.exists()) {
                        mediaDir.mkdirs()
                    }

                    val initialFile = File(mediaDir, rawOutputName)
                    if (initialFile.exists() && conflictPolicy == com.mckimquyen.watermark.data.model.ConflictPolicy.SKIP) {
                        exportBitmap.recycle()
                        bitmapGuard.release()
                        val existingUri = FileProvider.getUriForFile(
                            /* context = */ appContext,
                            /* authority = */ "${BuildConfig.APPLICATION_ID}.fileprovider",
                            /* file = */ initialFile
                        )
                        return@withContext Result.success(existingUri)
                    }

                    val finalOutputName = when {
                        initialFile.exists() && conflictPolicy == com.mckimquyen.watermark.data.model.ConflictPolicy.RENAME_VERSION -> {
                            exportNaming.resolveVersionedName(rawOutputName) { candidate ->
                                File(mediaDir, candidate).exists()
                            }
                        }
                        initialFile.exists() && conflictPolicy == com.mckimquyen.watermark.data.model.ConflictPolicy.KEEP_BOTH -> {
                            // Legacy file system không tự đổi tên như MediaStore Q+, nên tự sinh phiên bản
                            exportNaming.resolveVersionedName(rawOutputName) { candidate ->
                                File(mediaDir, candidate).exists()
                            }
                        }
                        else -> rawOutputName
                    }

                    val outputFile = File(mediaDir, finalOutputName)
                    val compressOk = outputFile.outputStream().use { fileOutputStream ->
                        exportBitmap.compress(
                            /* format = */ settings.outputFormat,
                            /* quality = */ settings.compressLevel,
                            /* stream = */ fileOutputStream
                        )
                    }
                    if (!compressOk) {
                        outputFile.delete()
                        return@withContext Result.failure(
                            data = null,
                            code = MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_WRITE,
                            message = "Bitmap.compress() returned false."
                        )
                    }
                    // Đã compress xong, không còn dùng bitmap này nữa (BUG-05).
                    exportBitmap.recycle()
                    bitmapGuard.release()
                    exportNaming.applyCopyrightExif(outputFile.absolutePath, settings.copyright, settings.outputFormat)
                    val outputUri = FileProvider.getUriForFile(
                        /* context = */ appContext,
                        /* authority = */ "${BuildConfig.APPLICATION_ID}.fileprovider",
                        /* file = */ outputFile
                    )
                    appContext.sendBroadcast(
                        Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(outputFile)
                        )
                    )
                    Result.success(outputUri)
                }
            } finally {
                bitmapGuard.recycleIfOwned()
            }
        }

    /**
     * FEAT-15: ghi ảnh qua SAF (`DocumentFile`) vào thư mục user tự chọn thay vì cố định
     * `Pictures/WaterMarkCreator/` — cùng ngữ nghĩa conflict-policy với 2 nhánh MediaStore/legacy
     * File phía trên (tái dùng thẳng [ExportNaming.resolveVersionedName], không viết lại logic).
     * Mã lỗi tái dùng `TYPE_ERROR_SAVE_MEDIASTORE_*` cho nhất quán UI báo lỗi (cùng ý nghĩa người
     * dùng "ghi file thất bại", không cần thêm mã lỗi riêng cho 1 đường ghi khác).
     */
    private fun writeToCustomDirectory(
        contentResolver: ContentResolver,
        exportBitmap: Bitmap,
        rawOutputName: String,
        settings: ExportSettings,
        bitmapGuard: BitmapRecycleGuard
    ): Result<Uri> {
        val treeUri = settings.outputDirectoryUri!!
        val root = DocumentFile.fromTreeUri(appContext, treeUri)
        if (root == null || !root.canWrite()) {
            return Result.failure(
                data = null,
                code = MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_INSERT,
                message = "Custom output folder not accessible."
            )
        }
        return writeIntoDocumentTree(root, contentResolver, exportBitmap, rawOutputName, settings, bitmapGuard)
    }

    /**
     * Tách khỏi [writeToCustomDirectory] để test được logic conflict-policy/ghi file thật (qua
     * `DocumentFile.fromFile()` trỏ 1 thư mục thật trong instrumentation test) mà không cần user
     * thao tác chọn cây SAF thật — cùng tinh thần [com.mckimquyen.watermark.utils.FileUtils.collectImagesRecursively]
     * (ENH-33) nhận thẳng `root: DocumentFile` thay vì tự resolve từ Uri bên trong.
     */
    internal fun writeIntoDocumentTree(
        root: DocumentFile,
        contentResolver: ContentResolver,
        exportBitmap: Bitmap,
        rawOutputName: String,
        settings: ExportSettings,
        bitmapGuard: BitmapRecycleGuard
    ): Result<Uri> {
        val conflictPolicy = settings.conflictPolicy
        val existing = if (conflictPolicy != ConflictPolicy.KEEP_BOTH) root.findFile(rawOutputName) else null

        if (existing != null && conflictPolicy == ConflictPolicy.SKIP) {
            exportBitmap.recycle()
            bitmapGuard.release()
            return Result.success(existing.uri)
        }

        val finalOutputName = if (existing != null && conflictPolicy == ConflictPolicy.RENAME_VERSION) {
            exportNaming.resolveVersionedName(rawOutputName) { candidate -> root.findFile(candidate) != null }
        } else {
            rawOutputName
        }

        val targetDoc = if (existing != null && conflictPolicy == ConflictPolicy.OVERWRITE) {
            existing
        } else {
            val mimeType = "image/${exportNaming.trapOutputExtension(settings.outputFormat)}"
            root.createFile(mimeType, finalOutputName) ?: return Result.failure(
                data = null,
                code = MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_INSERT,
                message = "Failed to create file in custom output folder."
            )
        }

        val writeOk = try {
            contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                exportBitmap.compress(settings.outputFormat, settings.compressLevel, out)
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!writeOk) {
            // existing == null nghĩa là file mới TỰ TẠO ở trên (không phải ghi đè file cũ) — chỉ
            // dọn rác khi chính lần ghi này tạo ra nó, không đụng file cũ nếu đang OVERWRITE.
            if (existing == null) targetDoc.delete()
            return Result.failure(
                data = null,
                code = MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_WRITE,
                message = "Bitmap.compress() returned false."
            )
        }
        exportBitmap.recycle()
        bitmapGuard.release()
        exportNaming.applyCopyrightExif(contentResolver, targetDoc.uri, settings.copyright, settings.outputFormat)
        return Result.success(targetDoc.uri)
    }

    /** FEAT-07 & ENH-35: kết quả preview NHẸ cho 1 ảnh trong grid xem trước batch. */
    sealed interface PreviewResult {
        val approxOriginalWidth: Int
        val approxOriginalHeight: Int
        val bitmap: Bitmap?
            get() = (this as? Success)?.bitmap

        data class Success(
            override val bitmap: Bitmap,
            override val approxOriginalWidth: Int,
            override val approxOriginalHeight: Int,
            /** IDEA-15: kết quả đánh giá quy tắc thương hiệu (pass/warn/fail) */
            val compliance: BrandComplianceScorer.ComplianceResult? = null
        ) : PreviewResult

        data class DecodeFailure(
            val error: Throwable? = null,
            val message: String? = null,
            override val approxOriginalWidth: Int = 0,
            override val approxOriginalHeight: Int = 0
        ) : PreviewResult

        companion object {
            operator fun invoke(
                bitmap: Bitmap,
                approxOriginalWidth: Int,
                approxOriginalHeight: Int
            ): Success = Success(bitmap, approxOriginalWidth, approxOriginalHeight)
        }
    }

    /**
     * FEAT-07: render watermark preview NHẸ cho grid xem trước cả batch — KHÔNG ghi MediaStore,
     * KHÔNG expand khung EXIF (giữ đúng quy ước "khung EXIF không hiện trong preview để tối ưu
     * hiệu năng" đã áp dụng cho live editor, xem `ExifPbFragment`/`dlg_exif_border.xml`).
     *
     * Decode ảnh ở kích thước NHỎ ([PREVIEW_MAX_SIZE], qua `decodeSampledBitmapFromResource` đã
     * cache theo (uri, reqW, reqH)) rồi vẽ watermark trực tiếp lên canvas CÙNG kích thước đã decode
     * — giống cách `WaterMarkImageView.onDraw()` vẽ preview on-screen (`applyConfig(isScale=true)`
     * mặc định), KHÔNG dùng `adjustMatrix`/`scaleX` như [generateImage] (vốn tính cho canvas full-res
     * khác kích thước view — không áp dụng khi canvas preview chính là bitmap đã decode).
     */
    suspend fun generatePreviewBitmap(
        contentResolver: ContentResolver,
        imageInfo: ImageInfo,
        config: WaterMark,
        index: Int
    ): PreviewResult = withContext(Dispatchers.IO) {
        val decodeResult = decodeSampledBitmapFromResource(
            appContext,
            contentResolver,
            imageInfo.uri,
            PREVIEW_MAX_SIZE,
            PREVIEW_MAX_SIZE
        )
        val bitmapValue = decodeResult.data
        if (decodeResult.isFailure() || bitmapValue == null) {
            // ENH-35: Báo rõ DecodeFailure thay vì null im lặng để UI hiển thị badge/icon lỗi
            return@withContext PreviewResult.DecodeFailure(message = decodeResult.message)
        }
        bitmapValue.retain()
        try {
            val srcBitmap = bitmapValue.bitmap ?: return@withContext PreviewResult.DecodeFailure()
            var mutableBitmap = srcBitmap.copy(Bitmap.Config.ARGB_8888, true)
                ?: return@withContext PreviewResult.DecodeFailure()
            // FEAT-16: mutableBitmap ở đây là bản copy riêng (không qua BitmapCache) — áp
            // crop/rotate TRƯỚC khi vẽ watermark, cùng thứ tự với generateImage()/preview editor.
            if (imageInfo.rotationDegrees != 0f || imageInfo.cropRect != null) {
                val transformed = applyCropAndRotate(mutableBitmap, imageInfo.rotationDegrees, imageInfo.cropRect)
                if (transformed !== mutableBitmap && !mutableBitmap.isRecycled) {
                    mutableBitmap.recycle()
                }
                mutableBitmap = transformed
            }
            val approxOriginalWidth = mutableBitmap.width * bitmapValue.inSampleSize
            val approxOriginalHeight = mutableBitmap.height * bitmapValue.inSampleSize

            // Preview phải khớp đúng những gì export thật sẽ vẽ — dùng chung resolveBaseText()
            // với generateImage(), không tự suy diễn lại rule.
            val baseText = resolveBaseText(imageInfo, config)
            if (shouldSkipTextWatermark(config.markMode, baseText)) {
                // FEAT-03: layer chính bị skip (text rỗng) không có nghĩa layer PHỤ cũng phải ẩn.
                drawExtraLayers(Canvas(mutableBitmap), mutableBitmap.width, mutableBitmap.height, imageInfo, config.extraLayers, contentResolver)
                return@withContext PreviewResult.Success(mutableBitmap, approxOriginalWidth, approxOriginalHeight)
            }

            val previewInfo = imageInfo.copy(
                width = mutableBitmap.width,
                height = mutableBitmap.height,
                inSample = bitmapValue.inSampleSize,
                exifModel = bitmapValue.exifModel
            )
            val textPaint = TextPaint().applyConfig(previewInfo, config)
            val shader = when (config.markMode) {
                WaterMarkRepository.MarkMode.Text -> {
                    val resolvedText = exportNaming.resolveTextTokens(baseText, previewInfo, contentResolver, index)
                    WaterMarkImageView.buildTextBitmapShader(
                        imageInfo = previewInfo,
                        config = config.copy(text = resolvedText),
                        textPaint = textPaint,
                        coroutineContext = Dispatchers.IO
                    )
                }

                WaterMarkRepository.MarkMode.Image -> {
                    val iconResult = decodeSampledBitmapFromResource(
                        appContext,
                        contentResolver,
                        config.iconUri,
                        mutableBitmap.width,
                        mutableBitmap.height
                    )
                    val iconValue = iconResult.data
                    val iconBitmap = iconValue?.bitmap
                    if (iconValue == null || iconBitmap == null) {
                        // FEAT-03: icon layer chính lỗi decode không có nghĩa layer PHỤ cũng ẩn.
                        drawExtraLayers(Canvas(mutableBitmap), mutableBitmap.width, mutableBitmap.height, imageInfo, config.extraLayers, contentResolver)
                        return@withContext PreviewResult.Success(mutableBitmap, approxOriginalWidth, approxOriginalHeight)
                    }
                    iconValue.retain()
                    try {
                        WaterMarkImageView.buildIconBitmapShader(
                            imageInfo = previewInfo,
                            srcBitmap = iconBitmap,
                            config = config,
                            textPaint = textPaint,
                            scale = false,
                            coroutineContext = Dispatchers.IO
                        )
                    } finally {
                        iconValue.release()
                    }
                }
            }

            val isClamp = previewInfo.obtainTileMode() == Shader.TileMode.CLAMP
            val wmWidth = (shader?.width ?: 0).toFloat()
            val wmHeight = (shader?.height ?: 0).toFloat()
            val wmRectNormalized: BrandComplianceScorer.NormalizedBox? = if (isClamp && wmWidth > 0 && wmHeight > 0 && mutableBitmap.width > 0 && mutableBitmap.height > 0) {
                val l = previewInfo.offsetX
                val t = previewInfo.offsetY
                val r = l + (wmWidth / mutableBitmap.width)
                val b = t + (wmHeight / mutableBitmap.height)
                BrandComplianceScorer.NormalizedBox(l, t, r, b)
            } else {
                null
            }

            val contrastRatio: Double? = if (config.markMode == WaterMarkRepository.MarkMode.Text) {
                try {
                    val paletteBuilder = Palette.from(mutableBitmap)
                    wmRectNormalized?.let { rect ->
                        val left = (rect.left * mutableBitmap.width).toInt().coerceIn(0, mutableBitmap.width - 1)
                        val top = (rect.top * mutableBitmap.height).toInt().coerceIn(0, mutableBitmap.height - 1)
                        val right = (rect.right * mutableBitmap.width).toInt().coerceIn(left + 1, mutableBitmap.width)
                        val bottom = (rect.bottom * mutableBitmap.height).toInt().coerceIn(top + 1, mutableBitmap.height)
                        paletteBuilder.setRegion(left, top, right, bottom)
                    }
                    val backgroundColor = paletteBuilder.generate().dominantSwatch?.rgb ?: Color.GRAY
                    androidx.core.graphics.ColorUtils.calculateContrast(config.textColor, backgroundColor)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val faces = imageInfo.detectedFaceRectsNormalized.orEmpty().map {
                BrandComplianceScorer.NormalizedBox(it.left, it.top, it.right, it.bottom)
            }

            val compliance = BrandComplianceScorer.evaluate(
                watermarkRect = wmRectNormalized,
                alpha = config.alpha,
                contrastRatio = contrastRatio,
                faces = faces,
                layoutMode = if (isClamp) {
                    BrandComplianceScorer.LayoutMode.SINGLE
                } else {
                    BrandComplianceScorer.LayoutMode.TILED
                }
            )

            val layoutPaint = Paint().apply { this.shader = shader?.bitmapShader }
            val canvas = Canvas(mutableBitmap)
            if (isClamp) {
                // FEAT-03: bọc withSave — offset layer PHỤ (drawExtraLayers) tính từ gốc toạ độ
                // ảnh, không được cộng dồn lên translate của layer chính này.
                canvas.withSave {
                    translate(
                        previewInfo.offsetX * mutableBitmap.width,
                        previewInfo.offsetY * mutableBitmap.height
                    )
                    drawRect(
                        0f,
                        0f,
                        wmWidth,
                        wmHeight,
                        layoutPaint
                    )
                }
            } else {
                canvas.drawRect(0f, 0f, mutableBitmap.width.toFloat(), mutableBitmap.height.toFloat(), layoutPaint)
            }
            drawExtraLayers(canvas, mutableBitmap.width, mutableBitmap.height, imageInfo, config.extraLayers, contentResolver)
            PreviewResult.Success(mutableBitmap, approxOriginalWidth, approxOriginalHeight, compliance)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            e.printStackTrace()
            PreviewResult.DecodeFailure(e)
        } finally {
            bitmapValue.release()
        }
    }

    /** FEAT-18: 2 bitmap ĐỘC LẬP (copy riêng, không chia sẻ buffer với BitmapCache) — an toàn để UI giữ/hiển thị mà không cần biết cơ chế cache/refcount. */
    data class CompareBitmaps(val original: Bitmap, val watermarked: Bitmap)

    /**
     * FEAT-18: decode 1 LẦN rồi tạo 2 bitmap ĐỘC LẬP — 1 bản gốc (before) và 1 bản đã vẽ watermark
     * (after) — cho màn so sánh trượt (before/after) trong preview batch, KHÔNG ghi MediaStore/
     * export thật. Cùng kích thước decode [PREVIEW_MAX_SIZE] và cùng pipeline vẽ watermark với
     * [generatePreviewBitmap] — cố tình KHÔNG tách hàm dùng chung để không đổi hành vi hàm đã
     * test/dùng thật (FEAT-07/ENH-35), chấp nhận trùng lặp logic vẽ shader.
     */
    suspend fun generateCompareBitmaps(
        contentResolver: ContentResolver,
        imageInfo: ImageInfo,
        config: WaterMark,
        index: Int
    ): CompareBitmaps? = withContext(Dispatchers.IO) {
        val decodeResult = decodeSampledBitmapFromResource(
            appContext,
            contentResolver,
            imageInfo.uri,
            PREVIEW_MAX_SIZE,
            PREVIEW_MAX_SIZE
        )
        val bitmapValue = decodeResult.data
        if (decodeResult.isFailure() || bitmapValue == null) {
            return@withContext null
        }
        bitmapValue.retain()
        try {
            val srcBitmap = bitmapValue.bitmap ?: return@withContext null
            var originalCopy = srcBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return@withContext null
            var watermarkedCopy = srcBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: run {
                originalCopy.recycle()
                return@withContext null
            }

            // FEAT-16: áp crop/rotate cho CẢ 2 bản độc lập — so sánh trước/sau phải cùng khung
            // đã crop, không riêng bản watermarked.
            if (imageInfo.rotationDegrees != 0f || imageInfo.cropRect != null) {
                val transformedOriginal = applyCropAndRotate(originalCopy, imageInfo.rotationDegrees, imageInfo.cropRect)
                if (transformedOriginal !== originalCopy && !originalCopy.isRecycled) {
                    originalCopy.recycle()
                }
                originalCopy = transformedOriginal
                val transformedWatermarked = applyCropAndRotate(watermarkedCopy, imageInfo.rotationDegrees, imageInfo.cropRect)
                if (transformedWatermarked !== watermarkedCopy && !watermarkedCopy.isRecycled) {
                    watermarkedCopy.recycle()
                }
                watermarkedCopy = transformedWatermarked
            }

            val baseText = resolveBaseText(imageInfo, config)
            if (shouldSkipTextWatermark(config.markMode, baseText)) {
                // FEAT-03: layer chính bị skip (text rỗng) không có nghĩa layer PHỤ cũng phải ẩn.
                drawExtraLayers(Canvas(watermarkedCopy), watermarkedCopy.width, watermarkedCopy.height, imageInfo, config.extraLayers, contentResolver)
                return@withContext CompareBitmaps(originalCopy, watermarkedCopy)
            }

            val previewInfo = imageInfo.copy(
                width = watermarkedCopy.width,
                height = watermarkedCopy.height,
                inSample = bitmapValue.inSampleSize,
                exifModel = bitmapValue.exifModel
            )
            val textPaint = TextPaint().applyConfig(previewInfo, config)
            val shader = when (config.markMode) {
                WaterMarkRepository.MarkMode.Text -> {
                    val resolvedText = exportNaming.resolveTextTokens(baseText, previewInfo, contentResolver, index)
                    WaterMarkImageView.buildTextBitmapShader(
                        imageInfo = previewInfo,
                        config = config.copy(text = resolvedText),
                        textPaint = textPaint,
                        coroutineContext = Dispatchers.IO
                    )
                }

                WaterMarkRepository.MarkMode.Image -> {
                    val iconResult = decodeSampledBitmapFromResource(
                        appContext,
                        contentResolver,
                        config.iconUri,
                        watermarkedCopy.width,
                        watermarkedCopy.height
                    )
                    val iconValue = iconResult.data
                    val iconBitmap = iconValue?.bitmap
                    if (iconValue == null || iconBitmap == null) {
                        // FEAT-03: icon layer chính lỗi decode không có nghĩa layer PHỤ cũng ẩn.
                        drawExtraLayers(Canvas(watermarkedCopy), watermarkedCopy.width, watermarkedCopy.height, imageInfo, config.extraLayers, contentResolver)
                        return@withContext CompareBitmaps(originalCopy, watermarkedCopy)
                    }
                    iconValue.retain()
                    try {
                        WaterMarkImageView.buildIconBitmapShader(
                            imageInfo = previewInfo,
                            srcBitmap = iconBitmap,
                            config = config,
                            textPaint = textPaint,
                            scale = false,
                            coroutineContext = Dispatchers.IO
                        )
                    } finally {
                        iconValue.release()
                    }
                }
            }

            val layoutPaint = Paint().apply { this.shader = shader?.bitmapShader }
            val canvas = Canvas(watermarkedCopy)
            if (previewInfo.obtainTileMode() == Shader.TileMode.CLAMP) {
                // FEAT-03: bọc withSave — offset layer PHỤ (drawExtraLayers) tính từ gốc toạ độ
                // ảnh, không được cộng dồn lên translate của layer chính này.
                canvas.withSave {
                    translate(
                        previewInfo.offsetX * watermarkedCopy.width,
                        previewInfo.offsetY * watermarkedCopy.height
                    )
                    drawRect(0f, 0f, (shader?.width ?: 0).toFloat(), (shader?.height ?: 0).toFloat(), layoutPaint)
                }
            } else {
                canvas.drawRect(0f, 0f, watermarkedCopy.width.toFloat(), watermarkedCopy.height.toFloat(), layoutPaint)
            }
            drawExtraLayers(canvas, watermarkedCopy.width, watermarkedCopy.height, imageInfo, config.extraLayers, contentResolver)
            CompareBitmaps(originalCopy, watermarkedCopy)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            bitmapValue.release()
        }
    }

    companion object {
        /** FEAT-07: cạnh dài tối đa (px) khi decode cho grid preview — đủ nét cho thumbnail, rẻ hơn nhiều so với full-res. */
        const val PREVIEW_MAX_SIZE = 480
    }
}
