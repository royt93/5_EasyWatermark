package com.mckimquyen.watermark.utils.bitmap

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream

private const val TAG = "BitmapUtils"

/**
 * ENH-06: [rotation]/[exifModel] phải được đọc TRƯỚC (qua [readExifOrientationAndModel], 1 lần
 * mở stream duy nhất) và truyền vào đây — hàm này KHÔNG tự mở thêm stream nào để đọc EXIF, tránh
 * lặp lại việc đọc EXIF nhiều lần cho cùng 1 Uri khi gọi từ [decodeSampledBitmapFromResourceSync].
 */
fun decodeBitmapWithExifSync(
    inputStream: InputStream,
    options: BitmapFactory.Options?,
    rotation: Float,
    exifModel: com.mckimquyen.watermark.data.model.ExifModel
): Result<BitmapCache.BitmapValue> {
    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        ?: return Result.failure(null, "-1", "Generate Bitmap failed.")
    val inSampleSize = options?.inSampleSize ?: 1
    val bitmapValue = BitmapCache.BitmapValue(bitmap, inSampleSize, exifModel)
    if (rotation == 0f) {
        return Result.success(bitmapValue)
    }

    val matrix = Matrix()
    matrix.postRotate(rotation)

    val rotatedBitmap = Bitmap.createBitmap(
        /* source = */ bitmap,
        /* x = */ 0,
        /* y = */ 0,
        /* width = */ bitmap.width,
        /* height = */ bitmap.height,
        /* m = */ matrix,
        /* filter = */ false
    )
    if (rotatedBitmap != bitmap && !bitmap.isRecycled) {
        bitmap.recycle()
    }
    val rotateBitmapValue = BitmapCache.BitmapValue(rotatedBitmap, inSampleSize, exifModel)
    return Result.success(rotateBitmapValue)
}

/**
 * FEAT-16: áp dụng straighten (xoay tự do theo [rotationDegrees]) RỒI crop theo [cropRect]
 * (normalized 0..1, tính theo bitmap ĐÃ xoay) — dùng chung cho preview (`WaterMarkImageView`)
 * lẫn 3 luồng export (`BatchExportEngine`) để khung ảnh nhất quán trước khi vẽ watermark.
 * Fast-path trả nguyên [src] khi không xoay/không crop, tránh copy bitmap thừa cho ảnh chưa chỉnh.
 *
 * KHÔNG BAO GIỜ recycle [src]: bitmap này có thể đang được `BitmapCache` quản lý refcount
 * (`WaterMarkImageView.mainImageBitmapValue.retain()/release()`) — caller vẫn là chủ sở hữu.
 * Chỉ bitmap trung gian do CHÍNH hàm này tạo ra (kết quả xoay) mới bị recycle khi bị thay thế.
 */
fun applyCropAndRotate(src: Bitmap, rotationDegrees: Float, cropRect: RectF?): Bitmap {
    if (rotationDegrees == 0f && cropRect == null) return src

    var current = src
    var ownsCurrent = false

    if (rotationDegrees != 0f) {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees)
        val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        if (ownsCurrent && rotated !== current && !current.isRecycled) {
            current.recycle()
        }
        current = rotated
        ownsCurrent = true
    }

    if (cropRect != null) {
        val left = (cropRect.left.coerceIn(0f, 1f) * current.width).toInt()
        val top = (cropRect.top.coerceIn(0f, 1f) * current.height).toInt()
        val right = (cropRect.right.coerceIn(0f, 1f) * current.width).toInt()
        val bottom = (cropRect.bottom.coerceIn(0f, 1f) * current.height).toInt()
        val cropWidth = (right - left).coerceAtLeast(1)
        val cropHeight = (bottom - top).coerceAtLeast(1)
        val safeLeft = left.coerceIn(0, current.width - cropWidth)
        val safeTop = top.coerceIn(0, current.height - cropHeight)
        val cropped = Bitmap.createBitmap(current, safeLeft, safeTop, cropWidth, cropHeight)
        if (ownsCurrent && cropped !== current && !current.isRecycled) {
            current.recycle()
        }
        current = cropped
        ownsCurrent = true
    }

    return current
}

/** IDEA-14: kích thước khối mosaic (px) — mỗi khối đại diện 1 vùng [MOSAIC_BLOCK]×[MOSAIC_BLOCK] pixel gốc. */
private const val MOSAIC_BLOCK = 12

/**
 * IDEA-14: che (mosaic hoá) từng vùng trong [rects] (normalized 0..1 theo kích thước [src]) — dùng
 * chung cho preview editor lẫn mọi luồng export, gọi NGAY SAU [applyCropAndRotate] và TRƯỚC khi vẽ
 * watermark, để nội dung nhạy cảm không bao giờ xuất hiện trong ảnh cuối cùng.
 *
 * Cố ý dùng MOSAIC (giảm hẳn độ phân giải theo khối) thay vì Gaussian blur: blur vẫn có thể bị khử
 * nhiễu để đọc lại một phần, mosaic phá huỷ thông tin gốc triệt để hơn cho mục đích riêng tư — và
 * không cần `RenderEffect` (chỉ API 31+, app minSdk 24) hay RenderScript (đã deprecated).
 *
 * Cùng nguyên tắc sở hữu bitmap như [applyCropAndRotate]: KHÔNG BAO GIỜ recycle [src], chỉ trả về
 * bitmap MỚI khi có ít nhất 1 rect hợp lệ được áp; fast-path trả nguyên [src] khi rỗng/null.
 */
fun applyRedaction(src: Bitmap, rects: List<RectF>?): Bitmap {
    if (rects.isNullOrEmpty()) return src

    val result = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
    val canvas = android.graphics.Canvas(result)
    var appliedAny = false

    for (rect in rects) {
        val left = (rect.left.coerceIn(0f, 1f) * result.width).toInt()
        val top = (rect.top.coerceIn(0f, 1f) * result.height).toInt()
        val right = (rect.right.coerceIn(0f, 1f) * result.width).toInt()
        val bottom = (rect.bottom.coerceIn(0f, 1f) * result.height).toInt()
        val width = (right - left).coerceAtMost(result.width - left)
        val height = (bottom - top).coerceAtMost(result.height - top)
        if (width <= 0 || height <= 0 || left < 0 || top < 0) continue

        val region = Bitmap.createBitmap(result, left, top, width, height)
        val smallWidth = (width / MOSAIC_BLOCK).coerceAtLeast(1)
        val smallHeight = (height / MOSAIC_BLOCK).coerceAtLeast(1)
        val downscaled = Bitmap.createScaledBitmap(region, smallWidth, smallHeight, false)
        val mosaic = Bitmap.createScaledBitmap(downscaled, width, height, false)

        canvas.drawBitmap(mosaic, left.toFloat(), top.toFloat(), null)
        appliedAny = true

        // Bitmap.createBitmap/createScaledBitmap có thể trả CHÍNH instance nguồn khi kích thước
        // yêu cầu trùng khớp nguồn (không tạo bản sao) — chỉ recycle khi thực sự là bản sao mới,
        // tránh vô tình recycle nhầm `result` (region trùng toàn bộ ảnh) hoặc `region`/`downscaled`.
        if (region !== result) region.recycle()
        if (downscaled !== region) downscaled.recycle()
        if (mosaic !== downscaled) mosaic.recycle()
    }

    if (!appliedAny) {
        result.recycle()
        return src
    }
    return result
}

/**
 * ENH-06: đọc rotation (orientation) VÀ [com.mckimquyen.watermark.data.model.ExifModel] trong
 * CÙNG 1 lần mở `InputStream`/`ExifInterface` cho 1 [uri] — trước đây `getOrientation()` và
 * `getExifData()` mỗi hàm tự mở 1 stream riêng cho cùng dữ liệu EXIF, nhân đôi I/O không cần
 * thiết (ảnh hưởng rõ với URI chậm từ SAF/cloud provider, đặc biệt khi xử lý batch).
 */
private fun readExifOrientationAndModel(
    context: Context,
    uri: Uri
): Pair<Float, com.mckimquyen.watermark.data.model.ExifModel> {
    openExifStream(context, uri).use { input ->
        if (input == null) {
            return 0f to com.mckimquyen.watermark.data.model.ExifModel()
        }
        val exif = if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.N) {
            try {
                ExifInterface(input)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            // do not support api lower 24
            null
        }
        return resolveRotation(context, uri, exif) to buildExifModel(exif)
    }
}

/**
 * IDEA-16: Android 10+ xoá (redact) tag GPS khỏi stream MediaStore trừ khi app có
 * `ACCESS_MEDIA_LOCATION` VÀ mở Uri dạng "original" — áp cho mọi Uri authority `media` (gồm cả Uri
 * Photo Picker `content://media/picker/...`). Provider từ chối bản original → fallback Uri thường
 * (vẫn đúng 1 stream mở thành công, không đổi số lần mở của ENH-06).
 */
private fun openExifStream(context: Context, uri: Uri): InputStream? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
        uri.authority == MediaStore.AUTHORITY &&
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_MEDIA_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        try {
            return context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))
        } catch (e: Exception) {
            AppLog.w(TAG, "openExifStream: original uri bị từ chối, fallback uri thường", e)
        }
    }
    return context.contentResolver.openInputStream(uri)
}

private fun buildExifModel(exif: ExifInterface?): com.mckimquyen.watermark.data.model.ExifModel {
    if (exif == null) return com.mckimquyen.watermark.data.model.ExifModel()
    val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
    val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
    // FEAT-25: ưu tiên TAG_DATETIME_ORIGINAL (thời điểm bấm chụp thật) -> TAG_DATETIME -> TAG_DATETIME_DIGITIZED
    val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.takeIf { it.isNotBlank() }
        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)?.takeIf { it.isNotBlank() }
        ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED) ?: ""
    val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: ""
    val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: ""
    val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: ""
    val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) ?: ""
    val latLong = exif.latLong
    return com.mckimquyen.watermark.data.model.ExifModel(
        latitude = latLong?.getOrNull(0),
        longitude = latLong?.getOrNull(1),
        make = make,
        model = model,
        dateTime = dateTime,
        fNumber = if (fNumber.isNotEmpty()) "f/$fNumber" else "",
        exposureTime = parseExposureTime(exposureTime),
        iso = iso,
        focalLength = parseFocalLength(focalLength)
    )
}

/**
 * FEAT-25: Chuyển đổi chuỗi ngày giờ EXIF sang timestamp (epoch milliseconds).
 * EXIF tiêu chuẩn dùng định dạng "yyyy:MM:dd HH:mm:ss".
 * Hàm thuần (pure function), testable độc lập, hỗ trợ nhiều biến thể định dạng.
 */
fun parseExifDateTime(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    // Danh sách các pattern ngày giờ EXIF hay gặp
    val patterns = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy:MM:dd HH:mm",
        "yyyy-MM-dd HH:mm",
        "yyyy:MM:dd",
        "yyyy-MM-dd"
    )

    for (pattern in patterns) {
        try {
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                isLenient = false
            }
            val date = sdf.parse(trimmed)
            if (date != null) {
                return date.time
            }
        } catch (_: Exception) {
            // Thử pattern tiếp theo
        }
    }
    return null
}

/**
 * BUG-43: tag EXIF `exposureTime` = "0" (máy/ROM ghi sai) → `d=0.0` → `1/0=Infinity` →
 * `.toInt()=Int.MAX_VALUE` → caption `"1/2147483647s"`. Hàm thuần, guard `d <= 0`/không hữu hạn
 * → trả rỗng (cùng quy ước "tag thiếu = rỗng" đã dùng ở [buildExifModel]).
 */
fun parseExposureTime(raw: String): String {
    if (raw.isEmpty()) return ""
    val d = raw.toDoubleOrNull() ?: return "${raw}s"
    if (d <= 0.0 || !d.isFinite()) return ""
    return if (d < 1) "1/${(1 / d).toInt()}s" else "${raw}s"
}

/**
 * BUG-43: `parts[0].toDouble()`/`parts[1].toDouble()` trần ném `NumberFormatException` nếu tag
 * không phải rational số hợp lệ (EXIF hỏng) — exception này không được bắt ở đâu trong pipeline
 * decode, lan ra làm ảnh export/preview thất bại mơ hồ. Hàm thuần, dùng `toDoubleOrNull()`, guard
 * mẫu số 0/không hữu hạn → trả rỗng; làm tròn số gọn (`50mm` thay vì `50.0mm`).
 */
fun parseFocalLength(raw: String): String {
    if (raw.isEmpty()) return ""
    val parts = raw.split("/")
    if (parts.size != 2) return "${raw}mm"
    val numerator = parts[0].toDoubleOrNull()
    val denominator = parts[1].toDoubleOrNull()
    if (numerator == null || denominator == null || denominator == 0.0) return ""
    val value = numerator / denominator
    if (!value.isFinite()) return ""
    return "${formatFocalValue(value)}mm"
}

/** BUG-43: bỏ ".0" thừa cho số focal nguyên (50.0 → "50"), giữ nguyên số lẻ thật (23.5 → "23.5"). */
private fun formatFocalValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * Get orientation from ExifInterface and System sql.
 */
private fun resolveRotation(
    context: Context,
    uri: Uri,
    exif: ExifInterface?
): Float {
    val tagOrientation: Int = exif?.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_UNDEFINED
    ) ?: ExifInterface.ORIENTATION_UNDEFINED

    return when (tagOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> {
            // do not need to rotate bitmap
            try {
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
                    null,
                    null,
                    null
                )
                if (cursor?.count != 1) {
                    cursor?.close()
                    return 0f
                }
                cursor.moveToFirst()
                val orientation: Int = cursor.getInt(0)
                cursor.close()
                orientation.toFloat()
            } catch (e: Exception) {
                0f
            }
        }
    }
}

/**
 * Tính kích thước cạnh dài an toàn tối đa để phòng ngừa OutOfMemoryError khi giải mã ảnh siêu phân giải (4K/8K/108MP).
 * Đảm bảo dung lượng bitmap ARGB_8888 không vượt quá 35% heap khả dụng của JVM thiết bị.
 * Hàm thuần (JVM-friendly), dễ dàng unit test độc lập.
 */
fun computeMaxSafeDimension(
    width: Int,
    height: Int,
    reqLongEdge: Int = 0,
    maxHeapBytes: Long = Runtime.getRuntime().maxMemory()
): Int {
    if (width <= 0 || height <= 0) return reqLongEdge
    val imageLongEdge = maxOf(width, height)
    val userConstrained = if (reqLongEdge > 0) minOf(imageLongEdge, reqLongEdge) else imageLongEdge

    // Ngưỡng an toàn bộ nhớ: Tối đa 35% tổng JVM heap cho một buffer bitmap đơn lẻ.
    // Mỗi pixel ARGB_8888 chiếm 4 bytes.
    val maxSafePixels = (maxHeapBytes * 0.35 / 4.0).toLong().coerceAtLeast(1024L * 1024L)
    val currentPixels = width.toLong() * height.toLong()

    if (currentPixels <= maxSafePixels) {
        return userConstrained
    }

    val scale = kotlin.math.sqrt(maxSafePixels.toDouble() / currentPixels.toDouble())
    val safeEdge = (imageLongEdge * scale).toInt().coerceAtLeast(1080)
    return if (userConstrained in 1 until safeEdge) userConstrained else safeEdge
}

/**
 * ENH-14 & OOM-PROTECT: downsample khi user chọn resize (reqLongEdge > 0) HOẶC khi ảnh có kích thước
 * siêu lớn (4K/8K/108MP) vượt quá ngưỡng an toàn bộ nhớ ([computeMaxSafeDimension]) để chống OOM crash.
 * Đồng thời đặt inMutable = true để giải mã trực tiếp thành bitmap có thể vẽ được, loại bỏ hoàn toàn
 * bước bitmap.copy() gây nhân đôi bộ nhớ đỉnh (Peak Memory).
 */
suspend fun decodeBitmapFromUri(
    context: Context,
    resolver: ContentResolver,
    uri: Uri,
    reqLongEdge: Int = 0,
    maxHeapBytes: Long = Runtime.getRuntime().maxMemory()
): Result<BitmapCache.BitmapValue> =
    withContext(Dispatchers.IO) {
        // BUG-AUDIT-2026-09-29: nhánh early-return riêng cho reqLongEdge<=0 (decode full-res,
        // không gọi computeMaxSafeDimension) đã bị xoá — bỏ sót bảo vệ OOM cho output "Original"
        // trái với doc comment ENH-14/OOM-PROTECT phía trên. computeMaxSafeDimension đã tự xử lý
        // đúng case reqLongEdge<=0 (giữ nguyên kích thước nếu ảnh dưới ngưỡng an toàn), nên dùng
        // chung 1 đường code duy nhất bên dưới cho mọi giá trị reqLongEdge.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // BUG-AUDIT-2026-09-29 (review pass): guard rõ ràng stream null (quyền bị thu hồi giữa
        // chừng/provider lỗi) TRƯỚC khi gọi BitmapFactory — trước đây chỉ nhánh reqLongEdge>0 mới
        // đi qua dòng này nên rủi ro thấp/chưa gặp; giờ "Original" cũng dùng chung đường này.
        resolver.openInputStream(uri).use { boundsStream ->
            if (boundsStream == null) {
                return@withContext Result.failure(null, "-1", "Open input stream failed.")
            }
            BitmapFactory.decodeStream(boundsStream, null, options)
        }
        val (rotation, exifModel) = readExifOrientationAndModel(context, uri)
        val (oHeight: Int, oWidth: Int) = if (shouldInterchangeSize(rotation)) {
            options.run { outWidth to outHeight }
        } else {
            options.run { outHeight to outWidth }
        }
        val safeLongEdge = computeMaxSafeDimension(oWidth, oHeight, reqLongEdge, maxHeapBytes)
        options.inSampleSize = calculateInSampleSizeForLongEdge(maxOf(oWidth, oHeight), safeLongEdge)
        options.inJustDecodeBounds = false
        options.inMutable = true
        resolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) {
                return@withContext Result.failure(null, "-1", "Open input stream failed.")
            }
            return@withContext decodeBitmapWithExifSync(inputStream, options, rotation, exifModel)
        }
    }

suspend fun decodeSampledBitmapFromResource(
    context: Context,
    resolver: ContentResolver,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Result<BitmapCache.BitmapValue> = withContext(Dispatchers.IO) {
    val info = BitmapCache.BitmapInfo(uri, reqWidth, reqHeight)
    var cacheValue = BitmapCache.getFromCache(info)
    if (cacheValue?.bitmap == null) {
        cacheValue = decodeSampledBitmapFromResourceSync(
            context,
            resolver,
            uri,
            reqWidth,
            reqHeight
        ).data
        BitmapCache.addToCache(info, cacheValue)
    }
    return@withContext Result.success(data = cacheValue)
}

fun decodeSampledBitmapFromResourceSync(
    context: Context,
    resolver: ContentResolver,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Result<BitmapCache.BitmapValue> {
    try {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        // 1. decode bounds only
        resolver.openInputStream(uri).use { `is` ->
            BitmapFactory.decodeStream(`is`, null, options)
        }
        // 2. Đọc EXIF (rotation + model) 1 LẦN DUY NHẤT — ENH-06, tái dùng cho cả quyết định
        // interchange width/height (bước này) lẫn gắn vào BitmapValue kết quả (bước 3), thay vì
        // đọc lại EXIF lần thứ 2 bên trong decodeBitmapWithExifSync như trước.
        val (rotation, exifModel) = readExifOrientationAndModel(context, uri)
        val (oHeight: Int, oWidth: Int) = if (shouldInterchangeSize(rotation)) {
            options.run { outWidth to outHeight }
        } else {
            options.run { outHeight to outWidth }
        }
        options.inSampleSize = calculateInSampleSize(oWidth, oHeight, reqWidth, reqHeight)
        AppLog.i(TAG) { "reqW x reqH = $reqWidth x $reqHeight, outWidth x outHeight = $oWidth x $oHeight, inSampleSize = ${options.inSampleSize}" }
        // 3. Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        options.inMutable = true
        resolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) {
                return Result.failure(null, "-1", "Open input stream failed.")
            }
            return decodeBitmapWithExifSync(inputStream, options, rotation, exifModel)
        }
    } catch (fne: FileNotFoundException) {
        return Result.failure(null, "-1", fne.message)
    } catch (oom: OutOfMemoryError) {
        AppLog.i("BitmapUtils") { "Decoding sampled bitmap from resource throw oom" }
        return Result.failure(
            null,
            "-1",
            "Decoding sampled bitmap from resource throw oom"
        )
    }
}

/**
 * Chỉ ảnh xoay 90°/270° mới cần đảo chiều rộng/cao khi tính sample size; 180° giữ nguyên
 * (BUG-01). Hàm thuần (không phụ thuộc Context/Uri) để dễ unit test.
 */
fun shouldInterchangeSize(rotation: Float): Boolean = rotation == 90f || rotation == 270f

fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    // Raw height and width of image
    AppLog.i("generateImage") { "w = $width, h = $height, reqW = $reqWidth, reqH = $reqHeight" }
    // BUG-46: reqWidth/reqHeight <= 0 (canvas chưa layout xong) làm điều kiện `>= 0` trong while
    // dưới luôn đúng bất kể inSampleSize bao lớn → Int overflow quay về 0 → chia cho 0. Không có
    // ý nghĩa downsample khi req <= 0 nên trả 1 ngay, cùng quy ước calculateInSampleSizeForLongEdge.
    if (reqWidth <= 0 || reqHeight <= 0) return 1

    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }

//        var totalPixels = (width / inSampleSize) * (height / inSampleSize)
//        val totalReqPixels = reqWidth * reqHeight * 2
//        while (totalPixels > totalReqPixels) {
//            inSampleSize *= 2;
//            Log.i(TAG, "totalPixels = $totalPixels, totalReqPixels = $totalReqPixels, inSample -> $inSampleSize")
//            totalPixels = (width / inSampleSize) * (height / inSampleSize)
//        }
    }

    return inSampleSize
}

/**
 * ENH-14: sample size cho downsample-khi-export, tính theo CẠNH DÀI thay vì bounding box
 * (width/height riêng) như [calculateInSampleSize] — vì mục tiêu chỉ là giới hạn cạnh dài
 * ([reqLongEdge], khớp `maxOutputLongEdge`), không phải fit vừa 1 khung reqWidth x reqHeight.
 * Dùng chung công thức [calculateInSampleSize] cho khung reqWidth=reqHeight=[longEdge] sẽ sai vì
 * hàm đó bắt CẢ 2 cạnh đều phải >= req, trong khi cạnh ngắn của ảnh không hình vuông luôn nhỏ hơn
 * cạnh dài — sẽ dừng downsample quá sớm. Hàm thuần, dễ test trực tiếp trên JVM.
 */
fun calculateInSampleSizeForLongEdge(longEdge: Int, reqLongEdge: Int): Int {
    if (reqLongEdge <= 0 || longEdge <= reqLongEdge) return 1
    var inSampleSize = 1
    while (longEdge / (inSampleSize * 2) >= reqLongEdge) {
        inSampleSize *= 2
    }
    return inSampleSize
}

// Get a MemoryInfo object for the device's current memory status.
fun getAvailableMemory(context: Context): ActivityManager.MemoryInfo {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { memoryInfo ->
        activityManager.getMemoryInfo(memoryInfo)
    }
}

// fun addInBitmapOptions(
//    options: BitmapFactory.Options,
//    reusableBitmaps: HashSet<SoftReference<Bitmap>>,
// ) {
//    options.inMutable = true
//    getBitmapFromReusableSet(options, reusableBitmaps)?.also { inBitmap ->
//        options.inBitmap = inBitmap
//    }
// }

// fun getBitmapFromReusableSet(
//    options: BitmapFactory.Options,
//    reusableBitmaps: HashSet<SoftReference<Bitmap>>,
// ): Bitmap? {
//    synchronized(reusableBitmaps) {
//        val iterator = reusableBitmaps.iterator()
//        while (iterator.hasNext()) {
//            iterator.next().get()?.let { item ->
//                when {
//                    !item.isMutable -> {
//                        iterator.remove()
//                    }
//
//                    canUseForInBitmap(item, options) -> {
//                        iterator.remove()
//                        return item
//                    }
//                }
//            }
//        }
//        return null
//    }
// }

/**
 * Only the size equals or larger target options can be reused.
 * @author roy.mobile.dev@gmail.com
 * @date 2021/8/16
 */
// private fun canUseForInBitmap(
//    candidate: Bitmap,
//    targetOptions: BitmapFactory.Options,
// ): Boolean {
//    val width = targetOptions.outWidth / targetOptions.inSampleSize
//    val height = targetOptions.outHeight / targetOptions.inSampleSize
//    val byteCount = width * height * getBytesInPixel(candidate.config)
//    return byteCount <= candidate.allocationByteCount
// }

// private fun getBytesInPixel(config: Bitmap.Config): Int {
//    return when (config) {
//        Bitmap.Config.ALPHA_8 -> 1
//        Bitmap.Config.RGB_565, Bitmap.Config.ARGB_4444 -> 2
//        Bitmap.Config.ARGB_8888 -> 4
//        else -> 1
//    }
// }

/**
 * @author roy.mobile.dev@gmail.com
 * @date 2021/10/16
 * Copy from [ImageView]
 */
// fun generateMatrix(
//    viewInfo: ViewInfo,
//    drawableWidth: Int,
//    drawableHeight: Int,
//    bounds: Rect,
//    tempSrc: RectF,
//    tempDst: RectF,
// ): Matrix {
//    val dwidth: Int = drawableWidth
//    val dheight: Int = drawableHeight
//    val vwidth: Int = viewInfo.width - viewInfo.paddingLeft - viewInfo.paddingRight
//    val vheight: Int = viewInfo.height - viewInfo.paddingTop - viewInfo.paddingBottom
//    val fits = ((dwidth < 0 || vwidth == dwidth)
//            && (dheight < 0 || vheight == dheight))
//    var mDrawMatrix = Matrix()
//    if (dwidth <= 0 || dheight <= 0 || ScaleType.FIT_XY == viewInfo.scaleType) {
//        /* If the drawable has no intrinsic size, or we're told to
//                scaletofit, then we just fill our entire view.
//            */
//        bounds.set(0, 0, vwidth, vheight)
//    } else {
//        // We need to do the scaling ourself, so have the drawable
//        // use its native size.
//        bounds.set(0, 0, dwidth, dheight)
//        if (ScaleType.MATRIX == viewInfo.scaleType) {
//            // Use the specified matrix as-is.
//            if (!viewInfo.matrix.isIdentity) {
//                mDrawMatrix = viewInfo.matrix
//            }
//        } else if (fits) {
//            // The bitmap fits exactly, no transform needed.
//        } else if (ScaleType.CENTER == viewInfo.scaleType) {
//            // Center bitmap in view, no scaling.
//            mDrawMatrix = viewInfo.matrix
//            mDrawMatrix.setTranslate(
//                ((vwidth - dwidth) * 0.5f).roundToInt().toFloat(),
//                ((vheight - dheight) * 0.5f).roundToInt().toFloat()
//            )
//        } else if (ScaleType.CENTER_CROP == viewInfo.scaleType) {
//            mDrawMatrix = viewInfo.matrix
//            val scale: Float
//            var dx = 0f
//            var dy = 0f
//            if (dwidth * vheight > vwidth * dheight) {
//                scale = vheight.toFloat() / dheight.toFloat()
//                dx = (vwidth - dwidth * scale) * 0.5f
//            } else {
//                scale = vwidth.toFloat() / dwidth.toFloat()
//                dy = (vheight - dheight * scale) * 0.5f
//            }
//            mDrawMatrix.setScale(scale, scale)
//            mDrawMatrix.postTranslate(Math.round(dx).toFloat(), Math.round(dy).toFloat())
//        } else if (ScaleType.CENTER_INSIDE == viewInfo.scaleType) {
//            mDrawMatrix = viewInfo.matrix
//            val dx: Float
//            val dy: Float
//            val scale: Float = if (dwidth <= vwidth && dheight <= vheight) {
//                1.0f
//            } else {
//                (vwidth.toFloat() / dwidth.toFloat()).coerceAtMost(vheight.toFloat() / dheight.toFloat())
//            }
//            dx = ((vwidth - dwidth * scale) * 0.5f).roundToInt().toFloat()
//            dy = ((vheight - dheight * scale) * 0.5f).roundToInt().toFloat()
//            mDrawMatrix.setScale(scale, scale)
//            mDrawMatrix.postTranslate(dx, dy)
//        } else {
//            // Generate the required transform.
//            tempSrc.set(0f, 0f, dwidth.toFloat(), dheight.toFloat())
//            tempDst.set(0f, 0f, vwidth.toFloat(), vheight.toFloat())
//            mDrawMatrix = viewInfo.matrix
//            mDrawMatrix.setRectToRect(
//                tempSrc,
//                tempDst,
//                scaleTypeToScaleToFit(viewInfo.scaleType)
//            )
//        }
//    }
//    return mDrawMatrix
// }

// fun scaleTypeToScaleToFit(st: ScaleType): ScaleToFit {
//    // ScaleToFit enum to their corresponding Matrix.ScaleToFit values
//    return sS2FArray[st.toNativeInt() - 1]
// }

// private val sS2FArray = arrayOf(
//    ScaleToFit.FILL,
//    ScaleToFit.START,
//    ScaleToFit.CENTER,
//    ScaleToFit.END
// )

// fun ScaleType.toNativeInt(): Int {
//    return when (this) {
//        ScaleType.MATRIX -> 0
//        ScaleType.FIT_XY -> 1
//        ScaleType.FIT_START -> 2
//        ScaleType.FIT_CENTER -> 3
//        ScaleType.FIT_END -> 4
//        ScaleType.CENTER -> 5
//        ScaleType.CENTER_CROP -> 6
//        ScaleType.CENTER_INSIDE -> 7
//    }
// }
