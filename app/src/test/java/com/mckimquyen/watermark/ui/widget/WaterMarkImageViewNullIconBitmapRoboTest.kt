package com.mckimquyen.watermark.ui.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import com.mckimquyen.watermark.utils.bitmap.decodeSampledBitmapFromResource
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-79: nhánh Image của `applyNewConfig` không được `!!` lên `iconBitmap` khi `BitmapValue.bitmap` null.
 * Hợp đồng decode (BUG-73): value trả về đã `retain()`, caller PHẢI `release()` đúng 1 lần — nên khi
 * bitmap null, view phải trả lại ref thay vì giữ value trong field (rò refCount) hoặc ném NPE.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewNullIconBitmapRoboTest {

    private val mainUri = Uri.parse("content://media/main")
    private val iconUri = Uri.parse("content://media/icon")

    @Before
    fun setUp() {
        mockkStatic("com.mckimquyen.watermark.utils.bitmap.BitmapUtilsKt")
    }

    @After
    fun tearDown() {
        unmockkStatic("com.mckimquyen.watermark.utils.bitmap.BitmapUtilsKt")
    }

    private fun imageConfig() = WaterMark(
        text = "",
        textSize = 40f,
        textColor = Color.WHITE,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = 255,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = iconUri,
        markMode = WaterMarkRepository.MarkMode.Image,
        enableBounds = false
    )

    @Test
    fun imageMode_iconValueWithNullBitmap_releasesRefAndDoesNotKeepValue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = WaterMarkImageView(context)
        val mainValue = BitmapCache.BitmapValue(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), 1)
            .apply { retain() }
        val nullIconValue = BitmapCache.BitmapValue(null, 1).apply { retain() }
        coEvery { decodeSampledBitmapFromResource(any(), any(), mainUri, any(), any()) } returns
            Result.success(mainValue)
        coEvery { decodeSampledBitmapFromResource(any(), any(), iconUri, any(), any()) } returns
            Result.success(nullIconValue)

        view.updateUri(true, ImageInfo(mainUri))
        view.config = imageConfig()
        repeat(IDLE_ROUNDS) { shadowOf(Looper.getMainLooper()).idle() }

        // Trước fix: NPE ở `iconBitmap!!` bị exceptionHandler nuốt, value null-bitmap kẹt trong field (refCount 1).
        assertThat(nullIconValue.getRefCount()).isEqualTo(0)
        assertThat(view.getIconBitmapValue()).isNull()
    }

    @Test
    fun imageMode_iconValueWithValidBitmap_isKeptAndRetainedByView() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = WaterMarkImageView(context)
        val mainValue = BitmapCache.BitmapValue(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), 1)
            .apply { retain() }
        val iconValue = BitmapCache.BitmapValue(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), 1)
            .apply { retain() }
        coEvery { decodeSampledBitmapFromResource(any(), any(), mainUri, any(), any()) } returns
            Result.success(mainValue)
        coEvery { decodeSampledBitmapFromResource(any(), any(), iconUri, any(), any()) } returns
            Result.success(iconValue)

        view.updateUri(true, ImageInfo(mainUri))
        view.config = imageConfig()
        repeat(IDLE_ROUNDS) { shadowOf(Looper.getMainLooper()).idle() }

        // Chống over-fix: icon hợp lệ vẫn được view giữ (refCount 1), không bị release nhầm.
        assertThat(view.getIconBitmapValue()).isSameInstanceAs(iconValue)
        assertThat(iconValue.getRefCount()).isEqualTo(1)
    }

    @Test
    fun imageMode_decodeFailure_doesNotCrashAndKeepsNoIconValue() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = WaterMarkImageView(context)
        val mainValue = BitmapCache.BitmapValue(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), 1)
            .apply { retain() }
        coEvery { decodeSampledBitmapFromResource(any(), any(), mainUri, any(), any()) } returns
            Result.success(mainValue)
        coEvery { decodeSampledBitmapFromResource(any(), any(), iconUri, any(), any()) } returns
            Result.failure()

        view.updateUri(true, ImageInfo(mainUri))
        view.config = imageConfig()
        repeat(IDLE_ROUNDS) { shadowOf(Looper.getMainLooper()).idle() }

        assertThat(view.getIconBitmapValue()).isNull()
    }

    private companion object {
        const val IDLE_ROUNDS = 5
    }
}
