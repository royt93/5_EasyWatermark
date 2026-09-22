package com.mckimquyen.watermark.data.repo

import android.graphics.Bitmap
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * ENH-34: [UserConfigRepository.resolveOutputFormat] là hàm thuần (JVM, không cần Robolectric) —
 * [sdkInt] tham số hoá thay vì đọc `Build.VERSION.SDK_INT` thật, để test được cả nhánh API <30
 * (field `WEBP_LOSSY`/`WEBP_LOSSLESS` không tồn tại trên framework thiết bị cũ, phải fallback về
 * `WEBP` thay vì crash `NoSuchFieldError`).
 */
class UserConfigRepositoryFormatResolverTest {

    @Test
    fun resolveOutputFormat_pngOrdinal_returnsPng() {
        val result = UserConfigRepository.resolveOutputFormat(Bitmap.CompressFormat.PNG.ordinal, sdkInt = Build.VERSION_CODES.TIRAMISU)
        assertThat(result).isEqualTo(Bitmap.CompressFormat.PNG)
    }

    @Suppress("DEPRECATION")
    @Test
    fun resolveOutputFormat_legacyWebpOrdinal_returnsLegacyWebp_regardlessOfSdk() {
        val onModern = UserConfigRepository.resolveOutputFormat(Bitmap.CompressFormat.WEBP.ordinal, sdkInt = Build.VERSION_CODES.TIRAMISU)
        val onOld = UserConfigRepository.resolveOutputFormat(Bitmap.CompressFormat.WEBP.ordinal, sdkInt = Build.VERSION_CODES.LOLLIPOP)
        assertThat(onModern).isEqualTo(Bitmap.CompressFormat.WEBP)
        assertThat(onOld).isEqualTo(Bitmap.CompressFormat.WEBP)
    }

    @Test
    fun resolveOutputFormat_webpLossyOrdinal_onModernSdk_returnsWebpLossy() {
        val result = UserConfigRepository.resolveOutputFormat(3, sdkInt = Build.VERSION_CODES.R)
        assertThat(result).isEqualTo(Bitmap.CompressFormat.WEBP_LOSSY)
    }

    @Suppress("DEPRECATION")
    @Test
    fun resolveOutputFormat_webpLossyOrdinal_onOldSdk_fallsBackToLegacyWebp() {
        val result = UserConfigRepository.resolveOutputFormat(3, sdkInt = Build.VERSION_CODES.Q)
        assertThat(result).isEqualTo(Bitmap.CompressFormat.WEBP)
    }

    @Test
    fun resolveOutputFormat_webpLosslessOrdinal_onModernSdk_returnsWebpLossless() {
        val result = UserConfigRepository.resolveOutputFormat(4, sdkInt = Build.VERSION_CODES.R)
        assertThat(result).isEqualTo(Bitmap.CompressFormat.WEBP_LOSSLESS)
    }

    @Suppress("DEPRECATION")
    @Test
    fun resolveOutputFormat_webpLosslessOrdinal_onOldSdk_fallsBackToLegacyWebp() {
        val result = UserConfigRepository.resolveOutputFormat(4, sdkInt = Build.VERSION_CODES.Q)
        assertThat(result).isEqualTo(Bitmap.CompressFormat.WEBP)
    }

    @Test
    fun resolveOutputFormat_webpLosslessOrdinal_exactlyAtR_isModern() {
        // Ranh giới đúng: R (30) là API TỐI THIỂU hỗ trợ, không phải "lớn hơn R".
        val result = UserConfigRepository.resolveOutputFormat(4, sdkInt = Build.VERSION_CODES.R)
        assertThat(result).isEqualTo(Bitmap.CompressFormat.WEBP_LOSSLESS)
    }

    @Test
    fun resolveOutputFormat_nullOrUnknownOrdinal_defaultsToJpeg() {
        assertThat(UserConfigRepository.resolveOutputFormat(null, sdkInt = Build.VERSION_CODES.R))
            .isEqualTo(Bitmap.CompressFormat.JPEG)
        assertThat(UserConfigRepository.resolveOutputFormat(999, sdkInt = Build.VERSION_CODES.R))
            .isEqualTo(Bitmap.CompressFormat.JPEG)
        assertThat(UserConfigRepository.resolveOutputFormat(Bitmap.CompressFormat.JPEG.ordinal, sdkInt = Build.VERSION_CODES.R))
            .isEqualTo(Bitmap.CompressFormat.JPEG)
    }
}
