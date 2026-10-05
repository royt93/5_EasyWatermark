package com.mckimquyen.watermark.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * BUG-64: QR user đã xác nhận phải nằm ở kho bền (`filesDir`), không phải `cacheDir` — cache bị
 * `cleanOldTempFiles` (chỉ giữ 3 file, xoá >24h) và hệ thống dọn bất kỳ lúc nào, để lại URI chết
 * trong DataStore/MRU/profile. [QrCodeGenerator.promoteLegacyCacheUri] cứu dữ liệu cũ: còn file thì
 * copy sang kho bền, mất file thì trả null.
 */
@RunWith(RobolectricTestRunner::class)
class QrCodePersistenceRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetFileProviderStaticCache() {
        // FileProvider cache PathStrategy tĩnh theo authority; mỗi sandbox Robolectric có thư mục mới.
        runCatching {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    private fun qrBitmap(): Bitmap = QrCodeGenerator.generate("bug64", size = 128)!!

    private fun wipeCacheQr() {
        File(context.cacheDir, "qrcodes").deleteRecursively()
    }

    private fun readBytes(uri: Uri): ByteArray? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

    @Test
    fun saveToFiles_confirmedQr_survivesCacheWipe() {
        val uri = QrCodeGenerator.saveToFiles(context, qrBitmap(), prefix = QrCodeGenerator.PERSISTENT_FILE_PREFIX)!!

        wipeCacheQr()

        assertThat(readBytes(uri)).isNotNull()
        assertThat(readBytes(uri)!!.size).isGreaterThan(0)
    }

    @Test
    fun saveToFiles_writesUnderFilesDir_notCacheDir() {
        val uri = QrCodeGenerator.saveToFiles(context, qrBitmap(), prefix = QrCodeGenerator.PERSISTENT_FILE_PREFIX)!!

        assertThat(File(context.filesDir, QrCodeGenerator.QR_DIR_NAME).listFiles().orEmpty()).isNotEmpty()
        assertThat(File(context.cacheDir, QrCodeGenerator.QR_DIR_NAME).listFiles().orEmpty()).isEmpty()
        assertThat(uri.path).doesNotContain(QrCodeGenerator.LEGACY_QR_URI_PATH_PREFIX.trimEnd('/') + "/qr_")
    }

    @Test
    fun saveToFiles_persistentFileName_isNotMatchedByTempCleaner() {
        QrCodeGenerator.saveToFiles(context, qrBitmap(), prefix = QrCodeGenerator.PERSISTENT_FILE_PREFIX)!!
        val dir = File(context.filesDir, QrCodeGenerator.QR_DIR_NAME)

        val deleted = FileUtils.cleanOldTempFiles(dir, maxRetainedFiles = 0, maxAgeMs = 0L, nowMs = Long.MAX_VALUE)

        assertThat(deleted).isEqualTo(0)
        assertThat(dir.listFiles().orEmpty()).isNotEmpty()
    }

    @Test
    fun promoteLegacyCacheUri_fileStillThere_copiesToPersistentStore() {
        val legacy = QrCodeGenerator.saveToCache(context, qrBitmap(), prefix = "qr_temp_")!!
        val originalBytes = readBytes(legacy)!!

        val promoted = QrCodeGenerator.promoteLegacyCacheUri(context, legacy)

        assertThat(promoted).isNotNull()
        assertThat(promoted).isNotEqualTo(legacy)
        wipeCacheQr()
        assertThat(readBytes(promoted!!)).isEqualTo(originalBytes)
    }

    @Test
    fun promoteLegacyCacheUri_fileAlreadyWiped_returnsNull() {
        val legacy = QrCodeGenerator.saveToCache(context, qrBitmap(), prefix = "qr_temp_")!!
        wipeCacheQr()

        assertThat(QrCodeGenerator.promoteLegacyCacheUri(context, legacy)).isNull()
    }

    @Test
    fun promoteLegacyCacheUri_nonCacheUri_returnedUntouched() {
        val gallery = Uri.parse("content://media/external/images/media/42")
        val persistent = QrCodeGenerator.saveToFiles(context, qrBitmap(), prefix = QrCodeGenerator.PERSISTENT_FILE_PREFIX)!!

        assertThat(QrCodeGenerator.promoteLegacyCacheUri(context, gallery)).isEqualTo(gallery)
        assertThat(QrCodeGenerator.promoteLegacyCacheUri(context, persistent)).isEqualTo(persistent)
        assertThat(QrCodeGenerator.promoteLegacyCacheUri(context, Uri.EMPTY)).isEqualTo(Uri.EMPTY)
    }
}
