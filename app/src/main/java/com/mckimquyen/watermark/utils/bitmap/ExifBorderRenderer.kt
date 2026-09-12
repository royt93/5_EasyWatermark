package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ExifModel

/**
 * ENH-01: trích xuất khỏi `MainViewModel` (nguyên vẹn, không đổi logic) — export batch giờ chạy
 * trong `BatchExportWorker` (không có instance `MainViewModel`), cần hàm vẽ khung EXIF thuần
 * (không phụ thuộc state ViewModel) dùng chung được ở cả 2 nơi. `MainViewModel.buildExifBorderBitmap`/
 * `fitTextForCanvas` giữ lại làm delegate 1 dòng để các test hiện có (gọi trực tiếp qua instance
 * `viewModel`) không cần sửa.
 */
object ExifBorderRenderer {

    /**
     * Vẽ khung EXIF theo [style] lên canvas mở rộng từ [source] — chỉ dùng Canvas thuần
     * (chữ + hình khối), không dùng logo hãng máy thật để tránh rủi ro bản quyền/trademark.
     *
     * FEAT-14 Custom Frame Builder: [bandColor]/[bandThicknessPercent]/[useSerifCaption] override
     * nhẹ lên style đang chọn — `null` (mặc định) giữ NGUYÊN hành vi gốc của từng style,
     * không đổi output nếu user chưa tuỳ chỉnh gì.
     */
    fun buildExifBorderBitmap(
        source: Bitmap,
        eModel: ExifModel,
        style: ExifFrameStyle,
        bandColor: Int? = null,
        bandThicknessPercent: Float? = null,
        useSerifCaption: Boolean? = null
    ): Bitmap {
        return when (style) {
            ExifFrameStyle.CLASSIC -> buildClassicExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
            ExifFrameStyle.POLAROID -> buildPolaroidExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
            ExifFrameStyle.FILM_STRIP -> buildFilmStripExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
            ExifFrameStyle.MINIMAL -> buildMinimalExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
        }
    }

    /**
     * ENH-19: co [text] bằng dấu "…" nếu vượt quá [maxWidth] theo [paint] hiện tại — tránh vẽ
     * tràn khỏi canvas khi model máy/copyright dài bất thường. Text bình thường (không vượt
     * quá) trả về y nguyên, không đổi hành vi hiện có.
     */
    fun fitTextForCanvas(paint: Paint, text: String, maxWidth: Float): String {
        if (text.isEmpty() || maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end--
        }
        return if (end <= 0) ellipsis else text.substring(0, end) + ellipsis
    }

    /** null → font mặc định của style (không bold); true → serif; false → sans-serif ép buộc. */
    private fun captionTypefaceBase(useSerifCaption: Boolean?, styleDefault: Typeface): Typeface = when (useSerifCaption) {
        true -> Typeface.SERIF
        false -> Typeface.SANS_SERIF
        null -> styleDefault
    }

    /** Thanh trắng dưới đáy: tên máy đậm trái, thông số + ngày phải (hành vi gốc). */
    private fun buildClassicExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val borderHeight = (source.height * (bandThicknessPercent ?: ExifFrameStyle.CLASSIC.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val expanded = Bitmap.createBitmap(source.width, source.height + borderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.CLASSIC.defaultBandColor)
        canvas.drawBitmap(source, 0f, 0f, null)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = borderHeight * 0.35f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(captionTypefaceBase(useSerifCaption, Typeface.DEFAULT), Typeface.BOLD)
        }
        val maxTextWidth = source.width * 0.9f
        canvas.drawText(fitTextForCanvas(textPaint, eModel.getCameraName(), maxTextWidth), source.width * 0.05f, source.height + borderHeight * 0.5f, textPaint)

        textPaint.textSize = borderHeight * 0.22f
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText(fitTextForCanvas(textPaint, eModel.getFormattedExif(), maxTextWidth), source.width * 0.95f, source.height + borderHeight * 0.45f, textPaint)

        textPaint.textSize = borderHeight * 0.18f
        textPaint.color = Color.DKGRAY
        canvas.drawText(fitTextForCanvas(textPaint, eModel.dateTime, maxTextWidth), source.width * 0.95f, source.height + borderHeight * 0.75f, textPaint)
        return expanded
    }

    /** Viền trắng dày đều 4 cạnh kiểu ảnh Polaroid, caption căn giữa ở đáy. */
    private fun buildPolaroidExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val sideBorder = (minOf(source.width, source.height) * 0.05f).toInt()
        val bottomBorder = (source.height * (bandThicknessPercent ?: ExifFrameStyle.POLAROID.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val totalWidth = source.width + sideBorder * 2
        val totalHeight = source.height + sideBorder + bottomBorder
        val expanded = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.POLAROID.defaultBandColor)
        canvas.drawBitmap(source, sideBorder.toFloat(), sideBorder.toFloat(), null)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            // Polaroid mặc định VỐN đã là serif — captionTypefaceBase(null) trả về styleDefault = SERIF.
            typeface = Typeface.create(captionTypefaceBase(useSerifCaption, Typeface.SERIF), Typeface.NORMAL)
        }
        val maxTextWidth = totalWidth * 0.9f
        textPaint.textSize = bottomBorder * 0.32f
        canvas.drawText(fitTextForCanvas(textPaint, eModel.getCameraName(), maxTextWidth), totalWidth / 2f, source.height + sideBorder + bottomBorder * 0.55f, textPaint)

        textPaint.textSize = bottomBorder * 0.2f
        textPaint.color = Color.DKGRAY
        val detail = listOfNotNull(
            eModel.getFormattedExif().takeIf { it.isNotEmpty() },
            eModel.dateTime.takeIf { it.isNotEmpty() }
        ).joinToString("   ·   ")
        canvas.drawText(fitTextForCanvas(textPaint, detail, maxTextWidth), totalWidth / 2f, source.height + sideBorder + bottomBorder * 0.85f, textPaint)
        return expanded
    }

    /** Dải đen trên/dưới có lỗ sprocket như phim máy ảnh; caption phủ scrim mờ ở đáy ảnh. */
    private fun buildFilmStripExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val bandHeight = (source.height * (bandThicknessPercent ?: ExifFrameStyle.FILM_STRIP.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val totalHeight = source.height + bandHeight * 2
        val expanded = Bitmap.createBitmap(source.width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.FILM_STRIP.defaultBandColor)
        canvas.drawBitmap(source, 0f, bandHeight.toFloat(), null)

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val holeSize = bandHeight * 0.42f
        val holeRadius = holeSize * 0.25f
        val holeGap = holeSize * 1.7f
        val holeCount = (source.width / holeGap).toInt().coerceAtLeast(2)
        fun drawHoleRow(centerY: Float) {
            for (i in 0 until holeCount) {
                val cx = holeGap * 0.5f + i * holeGap
                canvas.drawRoundRect(
                    cx - holeSize / 2f,
                    centerY - holeSize / 2f,
                    cx + holeSize / 2f,
                    centerY + holeSize / 2f,
                    holeRadius,
                    holeRadius,
                    holePaint
                )
            }
        }
        drawHoleRow(bandHeight * 0.5f)
        drawHoleRow(totalHeight - bandHeight * 0.5f)

        // Scrim mờ phủ đáy ảnh thật (không phải dải đen) để chữ không đè lên lỗ sprocket.
        val scrimHeight = bandHeight * 1.1f
        val scrimTop = bandHeight + source.height - scrimHeight
        canvas.drawRect(
            0f,
            scrimTop,
            source.width.toFloat(),
            (bandHeight + source.height).toFloat(),
            Paint().apply {
                color = Color.argb(140, 0, 0, 0)
            }
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(captionTypefaceBase(useSerifCaption, Typeface.DEFAULT), Typeface.BOLD)
            textSize = bandHeight * 0.34f
        }
        val maxTextWidth = source.width * 0.92f
        canvas.drawText(fitTextForCanvas(textPaint, eModel.getCameraName(), maxTextWidth), source.width * 0.04f, bandHeight + source.height - scrimHeight * 0.45f, textPaint)
        textPaint.textSize = bandHeight * 0.22f
        textPaint.typeface = Typeface.DEFAULT
        val detail = listOfNotNull(
            eModel.getFormattedExif().takeIf { it.isNotEmpty() },
            eModel.dateTime.takeIf { it.isNotEmpty() }
        ).joinToString("  ·  ")
        canvas.drawText(fitTextForCanvas(textPaint, detail, maxTextWidth), source.width * 0.04f, bandHeight + source.height - scrimHeight * 0.15f, textPaint)
        return expanded
    }

    /** Dải trắng mỏng + 1 dòng chữ gọn (tên máy · thông số · ngày), gọn nhẹ hơn CLASSIC. */
    private fun buildMinimalExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val borderHeight = (source.height * (bandThicknessPercent ?: ExifFrameStyle.MINIMAL.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val expanded = Bitmap.createBitmap(source.width, source.height + borderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.MINIMAL.defaultBandColor)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.drawLine(
            0f,
            source.height.toFloat(),
            source.width.toFloat(),
            source.height.toFloat(),
            Paint().apply {
                color = Color.LTGRAY
                strokeWidth = borderHeight * 0.03f
            }
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = borderHeight * 0.4f
            textAlign = Paint.Align.LEFT
            typeface = captionTypefaceBase(useSerifCaption, Typeface.DEFAULT)
        }
        val line = listOfNotNull(
            eModel.getCameraName().takeIf { it.isNotEmpty() && it != ExifModel.UNKNOWN_DEVICE_FALLBACK },
            eModel.getFormattedExif().takeIf { it.isNotEmpty() },
            eModel.dateTime.takeIf { it.isNotEmpty() }
        ).joinToString("   ")
        canvas.drawText(fitTextForCanvas(textPaint, line, source.width * 0.94f), source.width * 0.03f, source.height + borderHeight * 0.65f, textPaint)
        return expanded
    }
}
