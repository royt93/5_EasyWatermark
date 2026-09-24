package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Shader
import android.net.Uri
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * IDEA-07: chứng minh đúng 2 Acceptance Criteria của ticket bằng file EXPORT THẬT (không suy đoán
 * qua bitmap trung gian) — export batch 2 ảnh KHÁC NHAU với QR động bật, đọc ngược QR trong từng
 * ảnh output bằng ZXing thật:
 * (1) Mỗi ảnh trong batch có QR chứa hash RIÊNG (khác nhau giữa 2 ảnh).
 * (2) Quét QR (ZXing, giống app quét mã ngoài) ra được nội dung đọc được, chứa hash hợp lệ.
 *
 * `tileMode = CLAMP` (thay vì `REPEAT` mặc định) — bắt buộc để QR chỉ vẽ 1 lần rõ nét (không lặp
 * lại thành lưới các ô nhỏ như watermark logo thường dùng), mới quét được bằng bộ đọc QR chuẩn.
 * `textSize` lớn (200f) vì kích thước icon render tỉ lệ theo `textSize * 3.5` (xem
 * `WaterMarkImageView.buildIconBitmapShader`) — cần đủ pixel để ZXing giải mã nội dung dài (hash
 * SHA-256 64 ký tự hex).
 */
@RunWith(AndroidJUnit4::class)
class BatchExportEngineQrDynamicIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val createdTestFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        BitmapCache.clearCache()
    }

    @After
    fun tearDown() {
        createdTestFiles.forEach { it.delete() }
        createdTestFiles.clear()
        BitmapCache.clearCache()
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
    }

    private fun createTestJpeg(color: Int, namePrefix: String): Uri {
        val bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        val file = File.createTempFile(namePrefix, ".jpg", context.cacheDir)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bitmap.recycle()
        createdTestFiles.add(file)
        return Uri.fromFile(file)
    }

    private fun qrDynamicSettings(namePattern: String) = BatchExportEngine.ExportSettings(
        config = WaterMark(
            text = "",
            textSize = 200f,
            textColor = Color.BLACK,
            textStyle = TextPaintStyle.Fill,
            textTypeface = TextTypeface.Normal,
            alpha = 255,
            degree = 0f,
            hGap = 0,
            vGap = 0,
            iconUri = Uri.EMPTY,
            markMode = WaterMarkRepository.MarkMode.Image,
            enableBounds = false,
            qrDynamicEnabled = true,
            qrContentTemplate = "{hash}",
            qrPortfolioLink = ""
        ),
        outputFormat = Bitmap.CompressFormat.PNG,
        compressLevel = 100,
        maxOutputLongEdge = 0,
        copyright = "",
        outputNamePattern = namePattern
    )

    private fun fakeViewInfo() = ViewInfo(
        width = 1080,
        height = 1920,
        paddingLeft = 0,
        paddingTop = 0,
        paddingRight = 0,
        paddingBottom = 0,
        scaleType = ImageView.ScaleType.FIT_CENTER,
        matrix = Matrix()
    )

    private fun decodeExportedFile(imageInfo: ImageInfo): Bitmap {
        val jobState = imageInfo.jobState
        assertThat(jobState).isInstanceOf(JobState.Success::class.java)
        val outputUri = (jobState as JobState.Success).result.data as? Uri
        assertThat(outputUri).isNotNull()
        val inputStream = context.contentResolver.openInputStream(outputUri!!)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        assertThat(bitmap).isNotNull()
        return bitmap
    }

    /** Đọc QR thật trong [bitmap] bằng ZXing — giống app quét mã ngoài đọc ảnh xuất ra. */
    private fun decodeQrContent(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        return MultiFormatReader().decode(binaryBitmap).text
    }

    @Test
    fun exportBatch_twoDifferentImages_eachHasDistinctScannableQrWithHash() {
        val uriA = createTestJpeg(Color.WHITE, "idea07_qr_a")
        val uriB = createTestJpeg(Color.WHITE, "idea07_qr_b")
        // Nội dung ảnh KHÁC NHAU dù cùng kích thước/màu nền — ghi thêm bytes riêng vào cuối file
        // JPEG (sau EOI marker, trình decode ảnh bỏ qua) để đảm bảo hash SHA-256 của 2 file khác
        // nhau mà không ảnh hưởng gì tới nội dung ảnh thật sự được decode/vẽ watermark.
        File(uriA.path!!).appendBytes(byteArrayOf(1, 2, 3))
        File(uriB.path!!).appendBytes(byteArrayOf(4, 5, 6, 7))

        // offsetX/offsetY mặc định 0.5/0.5 (neo GIỮA canvas, `drawRect` vẽ từ điểm neo mở rộng
        // sang phải/xuống) — với icon lớn (textSize=200 -> ~700px, xem qrDynamicSettings) sẽ bị
        // cắt cụt ở rìa phải/dưới. Đặt về 0f/0f (neo góc trên-trái) để QR vẽ trọn vẹn trong khung
        // 900x900, quét được bằng ZXing thật.
        val infoA = ImageInfo(uriA).copy(tileMode = Shader.TileMode.CLAMP.ordinal, offsetX = 0f, offsetY = 0f)
        val infoB = ImageInfo(uriB).copy(tileMode = Shader.TileMode.CLAMP.ordinal, offsetX = 0f, offsetY = 0f)

        val engine = BatchExportEngine(context, ExportNaming())
        val result = runBlocking {
            engine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo(),
                infoList = listOf(infoA, infoB),
                settings = qrDynamicSettings("idea07_qr_{seq}"),
                onProgress = {}
            )
        }

        assertThat(result.type).isEqualTo(Result.Type.Success)
        val exported = result.data.orEmpty()
        assertThat(exported).hasSize(2)

        val bitmapA = decodeExportedFile(exported[0])
        val bitmapB = decodeExportedFile(exported[1])
        val contentA = decodeQrContent(bitmapA)
        val contentB = decodeQrContent(bitmapB)
        bitmapA.recycle()
        bitmapB.recycle()

        // AC1: mỗi ảnh có QR chứa hash RIÊNG, khác nhau giữa 2 ảnh trong batch.
        assertThat(contentA).isNotEqualTo(contentB)
        // AC2: quét ra được nội dung, chứa hash SHA-256 hợp lệ (64 ký tự hex).
        assertThat(contentA).matches("[0-9a-f]{64}")
        assertThat(contentB).matches("[0-9a-f]{64}")
    }
}
