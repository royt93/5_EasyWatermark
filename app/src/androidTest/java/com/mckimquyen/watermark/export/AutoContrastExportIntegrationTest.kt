package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
 * BUG-39: `WaterMark.autoContrastEnabled` (IDEA-06) trước đây CHỈ preview editor áp dụng —
 * `BatchExportEngine.generateImage()` bỏ qua hoàn toàn, ảnh xuất ra giữ nguyên màu chữ cấu hình dù
 * preview đã đảo màu. Test trên thiết bị thật (Skia rasterize pixel thật, Robolectric fallback
 * `Color.GRAY` cho bitmap solid-color — xem `WaterMarkImageViewResolveAutoContrastRoboTest`).
 *
 * Đặt `config.textColor` TRÙNG màu nền: nếu auto-contrast không chạy, chữ hoà lẫn nền (vô hình
 * tuyệt đối vì cùng RGB+alpha=255) — nếu chạy đúng, chữ phải đảo màu và hiện rõ.
 */
@RunWith(AndroidJUnit4::class)
class AutoContrastExportIntegrationTest {

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

    /**
     * `Palette` KHÔNG tách được swatch nào cho bitmap 1 màu tuyệt đối (0 variance quantize) — rơi
     * fallback `Color.GRAY` (đã verify thật trên device, giống hạn chế Robolectric đã ghi ở
     * `WaterMarkImageViewResolveAutoContrastRoboTest`, nhưng đây là hạn chế THẬT của chính thuật
     * toán IDEA-06 gốc, không phải điều BUG-39 cần sửa). Ảnh test vẽ thêm vài dải màu lệch nhẹ
     * (như texture ảnh thật) để Palette có variance tối thiểu, dải nền vẫn CHIẾM ĐA SỐ diện tích
     * nên `getDominantColor` vẫn đúng về phía màu nền chủ đạo.
     */
    private fun createTestJpeg(width: Int, height: Int, color: Int, namePrefix: String): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val shade = androidx.core.graphics.ColorUtils.blendARGB(color, Color.GRAY, 0.12f)
        val stripeWidth = (width / 12).coerceAtLeast(1)
        val paint = android.graphics.Paint().apply { this.color = shade }
        var x = 0
        while (x < width) {
            canvas.drawRect(x.toFloat(), 0f, (x + stripeWidth / 3).toFloat(), height.toFloat(), paint)
            x += stripeWidth
        }
        val file = File.createTempFile(namePrefix, ".jpg", context.cacheDir)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        bitmap.recycle()
        createdTestFiles.add(file)
        return Uri.fromFile(file)
    }

    private fun settingsWith(textColor: Int, autoContrastEnabled: Boolean, namePattern: String) =
        BatchExportEngine.ExportSettings(
            config = WaterMark(
                text = "BUG-39",
                textSize = 60f,
                textColor = textColor,
                textStyle = TextPaintStyle.Fill,
                textTypeface = TextTypeface.Normal,
                alpha = 255,
                degree = 0f,
                hGap = 40,
                vGap = 40,
                iconUri = Uri.EMPTY,
                markMode = WaterMarkRepository.MarkMode.Text,
                enableBounds = false,
                autoContrastEnabled = autoContrastEnabled
            ),
            outputFormat = Bitmap.CompressFormat.PNG,
            compressLevel = 100,
            maxOutputLongEdge = 0,
            copyright = "© 2026 EasyWatermark BUG-39",
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

    /** Nền sáng (WHITE + dải xám nhạt 12% blend) không pixel nào có CẢ 3 kênh ≤ [DARK_CHANNEL_MAX] — có thì chắc chắn là chữ. */
    private fun hasDarkPixel(bitmap: Bitmap): Boolean {
        for (x in 0 until bitmap.width step 3) {
            for (y in 0 until bitmap.height step 3) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) <= DARK_CHANNEL_MAX && Color.green(p) <= DARK_CHANNEL_MAX && Color.blue(p) <= DARK_CHANNEL_MAX) return true
            }
        }
        return false
    }

    /** Nền tối (BLACK + dải xám đậm) không pixel nào có CẢ 3 kênh ≥ [LIGHT_CHANNEL_MIN] — có thì chắc chắn là chữ. */
    private fun hasLightPixel(bitmap: Bitmap): Boolean {
        for (x in 0 until bitmap.width step 3) {
            for (y in 0 until bitmap.height step 3) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) >= LIGHT_CHANNEL_MIN && Color.green(p) >= LIGHT_CHANNEL_MIN && Color.blue(p) >= LIGHT_CHANNEL_MIN) return true
            }
        }
        return false
    }

    @Test
    fun autoContrastEnabled_whiteTextOnWhiteBackground_exportedFileShowsDarkText() {
        val uri = createTestJpeg(600, 400, Color.WHITE, "bug39_white_bg")
        val info = ImageInfo(uri)
        val exportEngine = BatchExportEngine(context, ExportNaming())

        val result = runBlocking {
            exportEngine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo(),
                infoList = listOf(info),
                settings = settingsWith(textColor = Color.WHITE, autoContrastEnabled = true, namePattern = "bug39_white_on_white_{index}"),
                onProgress = {}
            )
        }

        assertThat(result.type).isEqualTo(Result.Type.Success)
        val bitmap = decodeExportedFile(result.data.orEmpty().single())
        // Trước fix BUG-39: chữ TRẮNG trên nền TRẮNG (+ dải xám nhạt, không đủ tối để tính là
        // "dark") → không pixel nào đủ tối → assertion này sẽ FAIL nếu quay lại hành vi cũ.
        assertThat(hasDarkPixel(bitmap)).isTrue()
        bitmap.recycle()
    }

    @Test
    fun autoContrastEnabled_blackTextOnBlackBackground_exportedFileShowsLightText() {
        val uri = createTestJpeg(600, 400, Color.BLACK, "bug39_black_bg")
        val info = ImageInfo(uri)
        val exportEngine = BatchExportEngine(context, ExportNaming())

        val result = runBlocking {
            exportEngine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo(),
                infoList = listOf(info),
                settings = settingsWith(textColor = Color.BLACK, autoContrastEnabled = true, namePattern = "bug39_black_on_black_{index}"),
                onProgress = {}
            )
        }

        assertThat(result.type).isEqualTo(Result.Type.Success)
        val bitmap = decodeExportedFile(result.data.orEmpty().single())
        assertThat(hasLightPixel(bitmap)).isTrue()
        bitmap.recycle()
    }

    @Test
    fun autoContrastDisabled_whiteTextOnWhiteBackground_staysInvisible_noRegression() {
        // AC2: tắt auto-contrast phải giữ ĐÚNG hành vi cũ — chữ trùng màu nền vẫn hoà lẫn (không
        // pixel nào đủ tối để tính là chữ), không bị hàm mới âm thầm can thiệp khi cờ tắt.
        val uri = createTestJpeg(600, 400, Color.WHITE, "bug39_disabled")
        val info = ImageInfo(uri)
        val exportEngine = BatchExportEngine(context, ExportNaming())

        val result = runBlocking {
            exportEngine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo(),
                infoList = listOf(info),
                settings = settingsWith(textColor = Color.WHITE, autoContrastEnabled = false, namePattern = "bug39_disabled_{index}"),
                onProgress = {}
            )
        }

        assertThat(result.type).isEqualTo(Result.Type.Success)
        val bitmap = decodeExportedFile(result.data.orEmpty().single())
        assertThat(hasDarkPixel(bitmap)).isFalse()
        bitmap.recycle()
    }

    private companion object {
        const val DARK_CHANNEL_MAX = 80
        const val LIGHT_CHANNEL_MIN = 190
    }
}
