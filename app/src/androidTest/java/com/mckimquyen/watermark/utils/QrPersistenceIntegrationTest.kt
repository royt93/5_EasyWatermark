package com.mckimquyen.watermark.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.utils.bitmap.decodeBitmapFromUri
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * BUG-64 trên máy thật (FileProvider + decode Skia thật): QR đã xác nhận phải còn decode được sau khi
 * cache bị dọn; URI cache cũ còn file thì được cứu sang kho bền, mất file thì promote trả null.
 */
@RunWith(AndroidJUnit4::class)
class QrPersistenceIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        File(context.cacheDir, QrCodeGenerator.QR_DIR_NAME).deleteRecursively()
        File(context.filesDir, QrCodeGenerator.QR_DIR_NAME).listFiles { f -> f.name.startsWith(TEST_PREFIX) }
            ?.forEach { it.delete() }
    }

    private fun wipeCache() {
        File(context.cacheDir, QrCodeGenerator.QR_DIR_NAME).deleteRecursively()
    }

    @Test
    fun confirmedQr_afterCacheWipe_stillDecodes() = runBlocking {
        val bitmap = QrCodeGenerator.generate("bug64-device", size = QR_SIZE)!!
        val uri = QrCodeGenerator.saveToFiles(context, bitmap, prefix = TEST_PREFIX)!!

        wipeCache()

        val decoded = decodeBitmapFromUri(context, context.contentResolver, uri)
        assertThat(decoded.isFailure()).isFalse()
        assertThat(decoded.data?.bitmap).isNotNull()
        decoded.data?.release()
        Unit
    }

    @Test
    fun legacyCacheQr_stillThere_promotedAndDecodesAfterWipe() = runBlocking {
        val bitmap = QrCodeGenerator.generate("bug64-legacy", size = QR_SIZE)!!
        val legacy = QrCodeGenerator.saveToCache(context, bitmap, prefix = "qr_temp_")!!

        val promoted = QrCodeGenerator.promoteLegacyCacheUri(context, legacy)

        assertThat(promoted).isNotNull()
        wipeCache()
        val decoded = decodeBitmapFromUri(context, context.contentResolver, promoted!!)
        assertThat(decoded.data?.bitmap).isNotNull()
        decoded.data?.release()
        Unit
    }

    @Test
    fun legacyCacheQr_alreadyWiped_promoteReturnsNull() {
        val bitmap = QrCodeGenerator.generate("bug64-gone", size = QR_SIZE)!!
        val legacy = QrCodeGenerator.saveToCache(context, bitmap, prefix = "qr_temp_")!!
        wipeCache()

        assertThat(QrCodeGenerator.promoteLegacyCacheUri(context, legacy)).isNull()
    }

    private companion object {
        const val QR_SIZE = 256
        const val TEST_PREFIX = "qr_bug64it_"
    }
}
