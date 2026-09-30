package com.mckimquyen.watermark.export.stego

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Review pass 12 (2026-09-30): `HiddenWatermarkReader` chưa từng có test nào — thêm bài đọc-qua-Uri
 * cơ bản làm regression guard cho refactor `decodeOptions` (trước đây field `object` dùng chung cho
 * mọi lần gọi `read()`, không an toàn nếu 2 lần verify chạy chồng — xem `AboutViewModel.verifyAuthenticity`,
 * không debounce nút chọn ảnh xác thực). Đổi sang tạo `BitmapFactory.Options` mới mỗi lần gọi.
 */
@RunWith(AndroidJUnit4::class)
class HiddenWatermarkReaderIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val contentResolver = context.contentResolver
    private val tempFiles = mutableListOf<File>()

    private fun texturedBitmap(width: Int = 256, height: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (y in 0 until height step 16) {
            val shade = 40 + (y * 150 / height)
            canvas.drawRect(
                0f,
                y.toFloat(),
                width.toFloat(),
                (y + 16).toFloat(),
                Paint().apply { color = Color.rgb(shade, shade * 2 / 3, 200 - shade / 2) }
            )
        }
        return bitmap
    }

    private fun writeToUri(bitmap: Bitmap): Uri {
        val file = File.createTempFile("hidden_wm_reader_test", ".png", context.cacheDir)
        FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
        tempFiles += file
        return Uri.fromFile(file)
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun anhCoWatermarkAn_docQuaUriThat_traVeDungChuSoHuu() {
        val source = texturedBitmap()
        val stamped = InvisibleWatermark.embed(source, StegoPayload.ownerIdOf("Roy Studio"))!!
        source.recycle()
        val uri = writeToUri(stamped)
        stamped.recycle()

        val result = HiddenWatermarkReader.read(contentResolver, uri)

        assertThat(result).isNotNull()
        assertThat(result!!.ownerId).isEqualTo(StegoPayload.ownerIdOf("Roy Studio"))
    }

    @Test
    fun anhSach_docQuaUriThat_traVeNull_khongBaoNham() {
        val clean = texturedBitmap()
        val uri = writeToUri(clean)
        clean.recycle()

        assertThat(HiddenWatermarkReader.read(contentResolver, uri)).isNull()
    }

    /**
     * Đúng kịch bản bug đã fix: 2 lần đọc CHỒNG LÊN NHAU (2 ảnh khác nhau, khác chủ sở hữu) trên 2
     * thread riêng — trước đây dùng chung 1 `BitmapFactory.Options` có thể làm 1 trong 2 lượt decode
     * đọc nhầm state của lượt kia. Chạy lặp nhiều lần để tăng cơ hội bắt race nếu còn sót.
     */
    @Test
    fun doc2AnhKhacNhau_songSong_khongLanLonKetQua() {
        val sourceA = texturedBitmap()
        val stampedA = InvisibleWatermark.embed(sourceA, StegoPayload.ownerIdOf("Chu A"))!!
        sourceA.recycle()
        val uriA = writeToUri(stampedA)
        stampedA.recycle()

        val sourceB = texturedBitmap(width = 320, height = 320)
        val stampedB = InvisibleWatermark.embed(sourceB, StegoPayload.ownerIdOf("Chu B"))!!
        sourceB.recycle()
        val uriB = writeToUri(stampedB)
        stampedB.recycle()

        repeat(20) {
            var resultA: InvisibleWatermark.Result? = null
            var resultB: InvisibleWatermark.Result? = null
            val threadA = Thread { resultA = HiddenWatermarkReader.read(contentResolver, uriA) }
            val threadB = Thread { resultB = HiddenWatermarkReader.read(contentResolver, uriB) }
            threadA.start()
            threadB.start()
            threadA.join()
            threadB.join()

            assertThat(resultA?.ownerId).isEqualTo(StegoPayload.ownerIdOf("Chu A"))
            assertThat(resultB?.ownerId).isEqualTo(StegoPayload.ownerIdOf("Chu B"))
        }
    }
}
