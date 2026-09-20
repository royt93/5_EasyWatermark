package com.mckimquyen.watermark.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * FEAT-22: Hỗ trợ tạo file tạm và URI FileProvider để chụp ảnh từ Camera.
 */
object CameraCaptureHelper {

    private const val CAMERA_DIR_NAME = "camera"
    private const val FILE_PREFIX = "camera_photo_"
    private const val FILE_SUFFIX = ".jpg"

    /**
     * Tạo file tạm trong bộ nhớ đệm nội bộ để lưu ảnh chụp từ Camera.
     */
    fun createPhotoFile(context: Context): File {
        val baseCacheDir = runCatching { context.cacheDir.canonicalFile }.getOrDefault(context.cacheDir)
        val cameraDir = File(baseCacheDir, CAMERA_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        return File(cameraDir, "$FILE_PREFIX${System.currentTimeMillis()}$FILE_SUFFIX")
    }

    /**
     * Lấy content Uri an toàn thông qua [FileProvider] đã đăng ký trong manifest.
     */
    fun getPhotoUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Dọn dẹp tệp ảnh tạm nếu quá trình chụp bị huỷ hoặc tệp rỗng (0 bytes).
     */
    fun cleanupPhotoFile(file: File?): Boolean {
        return if (file != null && file.exists() && file.length() == 0L) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Kiểm tra xem thiết bị có ứng dụng Camera hỗ trợ intent ACTION_IMAGE_CAPTURE hay không.
     */
    fun isCameraAvailable(context: Context): Boolean {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val packageManager = context.packageManager ?: return false
        val activities = packageManager.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }
}
