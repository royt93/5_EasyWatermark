package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.content.Context
import android.graphics.Shader
import android.text.TextPaint
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.widget.WaterMarkImageView
import com.mckimquyen.watermark.utils.bitmap.decodeSampledBitmapFromResource
import com.mckimquyen.watermark.utils.facedetection.FaceDetectionSource
import com.mckimquyen.watermark.utils.ktx.applyConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * IDEA-01: điều phối auto-placement cho cả batch — với mỗi ảnh: decode preview-resolution (tái
 * dùng [BatchExportEngine.PREVIEW_MAX_SIZE], đủ cho Face Detection và nhanh hơn full-res nhiều
 * lần), chạy [FaceDetectionSource] (cache kết quả vào [ImageInfo.detectedFaceRectsNormalized]),
 * build shader watermark THEO ĐÚNG CÁCH [BatchExportEngine.generatePreviewBitmap] làm (canvas =
 * chính bitmap đã decode, `scale=false` trong lệnh build — KHÔNG cần `ViewInfo`/`adjustMatrix` như
 * [BatchExportEngine.generateImage] vì preview-resolution không phân biệt "view bounds" khác "bitmap
 * bounds") để lấy tỉ lệ kích thước watermark, rồi nhờ [AutoPlacementPositioner] (hàm thuần) chọn
 * anchor né mặt tốt nhất và ghi vào `offsetX/offsetY` của đúng ảnh đó.
 */
class AutoPlacementEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val faceDetectionSource: FaceDetectionSource,
    private val exportNaming: ExportNaming
) {

    /**
     * @return list MỚI (mọi phần tử immutable `copy()`) — ảnh bị skip export, lỗi decode/detect,
     * hoặc không có mặt nào được giữ NGUYÊN (không đổi offset). Không bao giờ throw — lỗi 1 ảnh
     * không làm hỏng cả batch, giống [BatchExportEngine.generateList].
     */
    suspend fun suggestPlacements(
        contentResolver: ContentResolver,
        infoList: List<ImageInfo>,
        config: WaterMark,
        onProgress: (doneCount: Int, total: Int) -> Unit
    ): List<ImageInfo> = withContext(Dispatchers.Default) {
        val total = infoList.size
        var doneCount = 0
        infoList.mapIndexed { index, original ->
            val updated = if (original.isSkippedInExport) {
                original
            } else {
                try {
                    suggestPlacementForImage(contentResolver, original, config, index)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    e.printStackTrace()
                    original
                }
            }
            doneCount++
            onProgress(doneCount, total)
            updated
        }
    }

    private suspend fun suggestPlacementForImage(
        contentResolver: ContentResolver,
        original: ImageInfo,
        config: WaterMark,
        index: Int
    ): ImageInfo {
        val decodeResult = decodeSampledBitmapFromResource(
            appContext,
            contentResolver,
            original.uri,
            BatchExportEngine.PREVIEW_MAX_SIZE,
            BatchExportEngine.PREVIEW_MAX_SIZE
        )
        val bitmapValue = decodeResult.data ?: return original
        bitmapValue.retain()
        try {
            val srcBitmap = bitmapValue.bitmap ?: return original
            val faceRects = original.detectedFaceRectsNormalized ?: faceDetectionSource.detectFaces(srcBitmap)
            val infoWithCache = if (original.detectedFaceRectsNormalized == null) {
                original.copy(detectedFaceRectsNormalized = faceRects)
            } else {
                original
            }
            if (faceRects.isEmpty()) return infoWithCache

            // Watermark rỗng (text trống ở MarkMode.Text) -> không có gì để đặt vị trí.
            val baseText = infoWithCache.caption ?: config.text
            if (config.markMode == WaterMarkRepository.MarkMode.Text && baseText.isBlank()) return infoWithCache

            val previewInfo = infoWithCache.copy(width = srcBitmap.width, height = srcBitmap.height)
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
                    // IDEA-07 qrDynamicEnabled: kích thước render cuối cùng phụ thuộc textSize (xem
                    // buildIconBitmapShader), gần như không đổi theo nội dung bitmap nguồn — dùng
                    // thẳng config.iconUri để ước lượng kích thước, không cần sinh QR động thật ở đây.
                    val iconResult = decodeSampledBitmapFromResource(
                        appContext,
                        contentResolver,
                        config.iconUri,
                        srcBitmap.width,
                        srcBitmap.height
                    )
                    val iconValue = iconResult.data ?: return infoWithCache
                    iconValue.retain()
                    try {
                        val iconBitmap = iconValue.bitmap ?: return infoWithCache
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
            } ?: return infoWithCache

            val wmFracW = (shader.width.toFloat() / srcBitmap.width).coerceIn(0f, 1f)
            val wmFracH = (shader.height.toFloat() / srcBitmap.height).coerceIn(0f, 1f)
            val bestAnchor = AutoPlacementPositioner.pickBestAnchor(
                faceRects = faceRects.map { NormalizedRect(it.left, it.top, it.right, it.bottom) },
                marginPercent = config.marginPercent,
                wmFracW = wmFracW,
                wmFracH = wmFracH
            ) ?: return infoWithCache

            val (offsetX, offsetY) = bestAnchor.toOffset(config.marginPercent, wmFracW, wmFracH)
            return infoWithCache.copy(
                offsetX = offsetX,
                offsetY = offsetY,
                tileMode = Shader.TileMode.CLAMP.ordinal
            )
        } finally {
            bitmapValue.release()
        }
    }
}
