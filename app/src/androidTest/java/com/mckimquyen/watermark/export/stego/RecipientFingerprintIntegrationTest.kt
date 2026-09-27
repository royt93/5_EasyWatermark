package com.mckimquyen.watermark.export.stego

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.RecipientDatabase
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.data.repo.RecipientRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * IDEA-10 — test QUAN TRỌNG NHẤT của ticket: mô phỏng đúng luồng "ảnh bị rò rỉ" đầu-tới-cuối.
 *
 * 1. Nhúng dấu vân tay `owner#recipientCode` (đúng công thức [BatchExportEngine] dùng khi export).
 * 2. Nén JPEG THẬT bằng Skia (giả lập ảnh bị mạng xã hội re-encode, xoá sạch EXIF).
 * 3. Đọc lại lớp ẩn từ ảnh đã nén.
 * 4. Tra cứu Room DB THẬT ([RecipientRepository]) để xác nhận đúng người nhận bị lộ.
 *
 * Đây là bằng chứng bằng dữ liệu thật (Keystore/Skia/Room), không phải giả định lý thuyết.
 */
@RunWith(AndroidJUnit4::class)
class RecipientFingerprintIntegrationTest {

    private val owner = "Roy Studio"

    private lateinit var db: RecipientDatabase
    private lateinit var repository: RecipientRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, RecipientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecipientRepository(db.recipientDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Ảnh có kết cấu thật (gradient + hình khối) — cùng khuôn `InvisibleWatermarkIntegrationTest`. */
    private fun texturedBitmap(width: Int = 512, height: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val random = Random(7)
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
        repeat(40) {
            canvas.drawCircle(
                random.nextInt(width).toFloat(),
                random.nextInt(height).toFloat(),
                random.nextInt(10, 50).toFloat(),
                Paint().apply {
                    color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                    alpha = 120
                }
            )
        }
        return bitmap
    }

    private fun recompress(bitmap: Bitmap, quality: Int): Bitmap {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    @Test
    fun anhBiXoaExifSauKhiChiaSe_vanTruyDungNguoiNhanRoRi() = runBlocking {
        val clientA = Recipient(name = "Khách hàng VIP A", code = "VIP-A")
        val clientB = Recipient(name = "Khách hàng VIP B", code = "VIP-B")
        repository.save(clientA)
        repository.save(clientB)

        // Xuất ảnh gửi cho Khách B — đúng công thức BatchExportEngine dùng: owner#recipientCode.
        val ownerIdForB = StegoPayload.ownerIdOf("$owner#${clientB.code}")
        val source = texturedBitmap()
        val stamped = InvisibleWatermark.embed(source, ownerIdForB)!!
        source.recycle()

        // Giả lập bị chia sẻ lại (re-encode, mất sạch EXIF).
        val leaked = recompress(stamped, 80)
        stamped.recycle()

        try {
            val extracted = InvisibleWatermark.extract(leaked)
            assertThat(extracted).isNotNull()

            val found = repository.findMatching(stegoOwnerId = extracted!!.ownerId, currentOwner = owner)

            // So khớp theo `code` (không so cả object): `found` đọc lại từ Room mang `id` autoGenerate
            // thật, khác `clientB.id = 0` lúc chưa insert — so nguyên object sẽ luôn lệch ở field đó.
            assertThat(found?.code).isEqualTo(clientB.code)
            assertThat(found?.code).isNotEqualTo(clientA.code)
        } finally {
            leaked.recycle()
        }
    }

    @Test
    fun anhCuaChinhChu_khongGanNguoiNhan_khongBiBaoNhamLaRoRi() = runBlocking {
        val clientA = Recipient(name = "Khách hàng VIP A", code = "VIP-A")
        repository.save(clientA)

        // Ảnh xuất bình thường, KHÔNG gắn recipient — đúng công thức copyright thuần.
        val ownIdOwner = StegoPayload.ownerIdOf(owner)
        val source = texturedBitmap()
        val stamped = InvisibleWatermark.embed(source, ownIdOwner)!!
        source.recycle()

        val recompressed = recompress(stamped, 80)
        stamped.recycle()

        try {
            val extracted = InvisibleWatermark.extract(recompressed)!!
            // ownerId trùng chính chủ → AboutViewModel.resolveLeakedRecipient bỏ qua tra cứu ở bước
            // này, nhưng dù có tra thì cũng phải KHÔNG khớp ai — không được đổ oan cho khách hàng nào.
            val found = repository.findMatching(stegoOwnerId = extracted.ownerId, currentOwner = owner)
            assertThat(found).isNull()
        } finally {
            recompressed.recycle()
        }
    }
}
