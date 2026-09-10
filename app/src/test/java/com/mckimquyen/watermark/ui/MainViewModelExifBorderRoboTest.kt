package com.mckimquyen.watermark.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit test (Robolectric — cần shadow Bitmap/Canvas thật): [MainViewModel.buildExifBorderBitmap]
 * dispatch đúng renderer theo [ExifFrameStyle] (feat.md #9 — frame presets cho EXIF border,
 * không dùng logo hãng máy thật). `internal` (thay vì `private`) chỉ để test truy cập trực tiếp,
 * theo đúng cách [MainViewModel.resolvePreviewText] được public hoá cho mục đích test.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelExifBorderRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var viewModel: MainViewModel

    private val exif = ExifModel(make = "Canon", model = "EOS R5", iso = "100", fNumber = "f/2.8")

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    // Chỉ assert kích thước/config bitmap, KHÔNG assert màu pixel (getPixel): đã xác minh bằng
    // diagnostic test rằng môi trường Robolectric 4.14.1/SDK34 hiện tại của máy build này không
    // rasterize pixel thật (drawColor+getPixel cơ bản trên 1 Canvas trần cũng trả về 0) — giới
    // hạn native-graphics của môi trường CI/local, không phải bug trong buildExifBorderBitmap.
    // Composite pixel thật đã được verify bằng mắt qua smoke test trên thiết bị (xem báo cáo audit).
    private fun redSource(width: Int = 100, height: Int = 100): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.RED)
        return bmp
    }

    @Test
    fun classic_expandsHeightBy12Percent_keepsWidthUnchanged() {
        val result = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, ExifFrameStyle.CLASSIC)

        assertThat(result.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(112) // 100 + (100*0.12).toInt()
    }

    @Test
    fun polaroid_expandsAllFourSides() {
        val result = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, ExifFrameStyle.POLAROID)

        // sideBorder = (min(100,100)*0.05).toInt() = 5; bottomBorder = (100*0.16).toInt() = 16
        assertThat(result.width).isEqualTo(110) // 100 + 5*2
        assertThat(result.height).isEqualTo(121) // 100 + 5 + 16
    }

    @Test
    fun filmStrip_expandsHeightByTwoBands_keepsWidthUnchanged() {
        val result = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, ExifFrameStyle.FILM_STRIP)

        // bandHeight = (100*0.10).toInt() = 10
        assertThat(result.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(120) // 100 + 10*2
    }

    @Test
    fun minimal_expandsHeightBy6Percent_smallerThanClassic() {
        val classic = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, ExifFrameStyle.CLASSIC)
        val minimal = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, ExifFrameStyle.MINIMAL)

        assertThat(minimal.width).isEqualTo(100)
        assertThat(minimal.height).isEqualTo(106) // 100 + (100*0.06).toInt()
        assertThat(minimal.height - 100).isLessThan(classic.height - 100)
    }

    @Test
    fun minimal_borderHeight_neverZero_forTinySource() {
        // coerceAtLeast(1) trong buildMinimalExifBorder — ảnh rất nhỏ vẫn phải có border > 0px.
        val result = viewModel.buildExifBorderBitmap(redSource(4, 4), exif, ExifFrameStyle.MINIMAL)

        assertThat(result.height).isGreaterThan(4)
    }

    @Test
    fun allStyles_produceDistinctHeightExpansion_forSameSource() {
        val heights = ExifFrameStyle.entries.associateWith {
            viewModel.buildExifBorderBitmap(redSource(200, 200), exif, it).height
        }

        assertThat(heights.values.toSet()).hasSize(ExifFrameStyle.entries.size)
    }

    @Test
    fun allStyles_returnArgb8888Config() {
        ExifFrameStyle.entries.forEach { style ->
            val result = viewModel.buildExifBorderBitmap(redSource(50, 50), exif, style)
            assertThat(result.config).isEqualTo(Bitmap.Config.ARGB_8888)
        }
    }

    @Test
    fun emptyExifModel_stillRendersWithoutCrash_usingUnknownDeviceFallback() {
        ExifFrameStyle.entries.forEach { style ->
            val result = viewModel.buildExifBorderBitmap(redSource(80, 80), ExifModel(), style)
            assertThat(result.width).isAtLeast(80)
            assertThat(result.height).isGreaterThan(80)
        }
    }

    // ══ FEAT-14 Custom Frame Builder — bandColor/bandThicknessPercent/useSerifCaption override ══

    @Test
    fun nullOverrides_produceSameSize_asOmittingParamsEntirely() {
        // AC "không tuỳ chỉnh gì → hành vi giữ nguyên": gọi tường minh với null phải ra kết quả
        // giống hệt gọi không truyền tham số (giá trị mặc định của hàm cũng là null).
        ExifFrameStyle.entries.forEach { style ->
            val default = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, style)
            val explicitNull = viewModel.buildExifBorderBitmap(
                redSource(100, 100),
                exif,
                style,
                bandColor = null,
                bandThicknessPercent = null,
                useSerifCaption = null
            )
            assertThat(explicitNull.width).isEqualTo(default.width)
            assertThat(explicitNull.height).isEqualTo(default.height)
        }
    }

    @Test
    fun customBandThicknessPercent_changesExpandedHeight_forEveryStyle() {
        // Độ dày băng tuỳ chỉnh (20%) phải khác độ dày mặc định của TỪNG style — chứng minh tham
        // số thật sự được dùng để tính kích thước canvas, không bị bỏ qua.
        ExifFrameStyle.entries.forEach { style ->
            val default = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, style)
            val customized = viewModel.buildExifBorderBitmap(
                redSource(100, 100),
                exif,
                style,
                bandThicknessPercent = 0.20f
            )
            assertThat(customized.height).isNotEqualTo(default.height)
        }
    }

    @Test
    fun customBandThicknessPercent_classic_matchesExactExpectedHeight() {
        val result = viewModel.buildExifBorderBitmap(
            redSource(100, 100),
            exif,
            ExifFrameStyle.CLASSIC,
            bandThicknessPercent = 0.20f
        )

        assertThat(result.height).isEqualTo(120) // 100 + (100*0.20).toInt()
    }

    @Test
    fun customBandColorAndSerifCaption_doNotCrash_andKeepSizeUnaffected() {
        // Màu/font không ảnh hưởng kích thước canvas — chỉ verify không crash + size ổn định
        // (assert màu pixel không khả thi trên môi trường Robolectric hiện tại, xem comment ở
        // redSource() phía trên; màu đã verify bằng mắt qua smoke test thật trên thiết bị).
        ExifFrameStyle.entries.forEach { style ->
            val default = viewModel.buildExifBorderBitmap(redSource(100, 100), exif, style)
            listOf(true, false).forEach { serif ->
                val customized = viewModel.buildExifBorderBitmap(
                    redSource(100, 100),
                    exif,
                    style,
                    bandColor = Color.rgb(10, 20, 30),
                    useSerifCaption = serif
                )
                assertThat(customized.width).isEqualTo(default.width)
                assertThat(customized.height).isEqualTo(default.height)
                assertThat(customized.config).isEqualTo(Bitmap.Config.ARGB_8888)
            }
        }
    }

    @Test
    fun customBandThicknessPercent_tinySource_stillNeverZeroHeight() {
        // coerceAtLeast(1) phải giữ đúng kể cả khi override rất nhỏ.
        val result = viewModel.buildExifBorderBitmap(
            redSource(2, 2),
            exif,
            ExifFrameStyle.MINIMAL,
            bandThicknessPercent = 0.01f
        )

        assertThat(result.height).isGreaterThan(2)
    }
}
