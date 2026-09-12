package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.JobStateResolver
import com.mckimquyen.watermark.data.model.MediaStoreInsertResolver
import com.mckimquyen.watermark.data.model.MediaStoreWriteResolver
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.widget.WaterMarkImageView
import com.mckimquyen.watermark.utils.FileUtils.Companion.outPutFolderName
import com.mckimquyen.watermark.utils.bitmap.BitmapRecycleGuard
import com.mckimquyen.watermark.utils.bitmap.ExifBorderRenderer
import com.mckimquyen.watermark.utils.bitmap.OutputImageUtils
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

    /** Snapshot cấu hình cho 1 lần export batch — đọc 1 lần trước khi loop, không đổi giữa chừng. */
    data class ExportSettings(
        val config: WaterMark,
        val outputFormat: Bitmap.CompressFormat,
        val compressLevel: Int,
        val maxOutputLongEdge: Int,
        val copyright: String,
        val outputNamePattern: String
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
            var imageInfo = originalImageInfo
            // ENH-14: downsample ngay lúc decode khi user đã chọn resize output (maxOutputLongEdge
            // != 0) — giảm peak memory khi vẽ watermark trên ảnh 12-48MP không cần thiết phải ở
            // full-res nếu output cuối cùng sẽ bị resize nhỏ lại. "Original" (0) giữ hành vi cũ.
            val rect = decodeBitmapFromUri(appContext, contentResolver, imageInfo.uri, settings.maxOutputLongEdge)
            if (rect.isFailure()) {
                return@withContext Result.extendMsg(rect)
            }
            val mutableBitmap = rect.data?.bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                ?: return@withContext Result.failure(
                    data = null,
                    code = "-1",
                    message = "Copy bitmap from uri failed."
                )
            // rect.data.bitmap không cache/chia sẻ nơi khác (decodeBitmapFromUri không qua
            // BitmapCache) — đã copy xong sang mutableBitmap nên recycle ngay, tránh giữ 2 bitmap
            // full-res cùng lúc khi xử lý batch nhiều ảnh (BUG-05).
            rect.data?.bitmap?.let { original ->
                if (original !== mutableBitmap && !original.isRecycled) original.recycle()
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
                val shader = when (tmpConfig.markMode) {
                    WaterMarkRepository.MarkMode.Text -> {
                        // Resolve dynamic text tokens (e.g. {date}, {filename}, {iso}) per image at export time.
                        val resolvedText = exportNaming.resolveTextTokens(tmpConfig.text, imageInfo, contentResolver, index)
                        WaterMarkImageView.buildTextBitmapShader(
                            imageInfo = imageInfo,
                            config = tmpConfig.copy(text = resolvedText),
                            textPaint = bitmapPaint,
                            coroutineContext = Dispatchers.IO
                        )
                    }

                    WaterMarkRepository.MarkMode.Image -> {
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

                layoutPaint.shader = shader?.bitmapShader

                if (imageInfo.obtainTileMode() == Shader.TileMode.CLAMP) {
                    canvas.translate(
                        0 + imageInfo.offsetX * mutableBitmap.width,
                        0 + imageInfo.offsetY * mutableBitmap.height
                    )
                    canvas.drawRect(
                        /* left = */ 0f,
                        /* top = */ 0f,
                        /* right = */ (shader?.width ?: 0).toFloat(),
                        /* bottom = */ (shader?.height ?: 0).toFloat(),
                        /* paint = */ layoutPaint
                    )
                } else {
                    canvas.drawRect(
                        /* left = */ 0f,
                        /* top = */ 0f,
                        /* right = */ mutableBitmap.width.toFloat(),
                        /* bottom = */ mutableBitmap.height.toFloat(),
                        /* paint = */ layoutPaint
                    )
                }

                val finalExportBitmap = if (tmpConfig.enableExif && imageInfo.exifModel != null && !imageInfo.exifModel!!.isEmpty()) {
                    val expandedBitmap = ExifBorderRenderer.buildExifBorderBitmap(
                        source = mutableBitmap,
                        eModel = imageInfo.exifModel!!,
                        style = ExifFrameStyle.obtain(tmpConfig.exifFrameStyle),
                        // FEAT-14 Custom Frame Builder — null nếu user chưa tuỳ chỉnh, giữ hành vi gốc.
                        bandColor = tmpConfig.exifBandColor,
                        bandThicknessPercent = tmpConfig.exifBandThicknessPercent,
                        useSerifCaption = tmpConfig.exifUseSerifCaption
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

                return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val imageCollection =
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val outputName = exportNaming.generateOutputName(
                        contentResolver,
                        imageInfo,
                        index,
                        settings.outputNamePattern,
                        settings.outputFormat
                    )
                    val imageDetail = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/${exportNaming.trapOutputExtension(settings.outputFormat)}")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$outPutFolderName/")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }

                    val insertResult = MediaStoreInsertResolver.resolve(
                        contentResolver.insert(imageCollection, imageDetail),
                        MainViewModel.TYPE_ERROR_SAVE_MEDIASTORE_INSERT
                    )
                    if (insertResult.isFailure()) return@withContext insertResult
                    val imageContentUri = insertResult.data!!
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
                        // Ghi thất bại → xoá row IS_PENDING rác thay vì để lại file 0-byte/lỗi trong gallery.
                        contentResolver.delete(imageContentUri, null, null)
                        return@withContext Result.extendMsg(writeResult)
                    }
                    // Đã compress xong, không còn dùng bitmap này nữa (BUG-05).
                    exportBitmap.recycle()
                    bitmapGuard.release()
                    imageDetail.clear()
                    imageDetail.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(imageContentUri, imageDetail, null, null)
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
                    val outputFile = File(
                        mediaDir,
                        exportNaming.generateOutputName(contentResolver, imageInfo, index, settings.outputNamePattern, settings.outputFormat)
                    )
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
}
