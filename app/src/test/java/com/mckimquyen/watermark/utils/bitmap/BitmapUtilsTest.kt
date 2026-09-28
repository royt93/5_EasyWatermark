package com.mckimquyen.watermark.utils.bitmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test thuần (JVM) cho phần tính sample size/xoay ảnh của [BitmapUtils]
 * (không phụ thuộc Android nên chạy trực tiếp trên JVM) — trực tiếp phòng regression BUG-01.
 */
class BitmapUtilsTest {

    @Test
    fun calculateInSampleSize_imageSmallerThanRequested_returns1() {
        assertThat(calculateInSampleSize(width = 400, height = 300, reqWidth = 1000, reqHeight = 1000))
            .isEqualTo(1)
    }

    @Test
    fun calculateInSampleSize_imageExactlyRequested_returns1() {
        assertThat(calculateInSampleSize(width = 1000, height = 1000, reqWidth = 1000, reqHeight = 1000))
            .isEqualTo(1)
    }

    @Test
    fun calculateInSampleSize_imageDouble_returns2() {
        assertThat(calculateInSampleSize(width = 2000, height = 2000, reqWidth = 1000, reqHeight = 1000))
            .isEqualTo(2)
    }

    @Test
    fun calculateInSampleSize_imageQuadruple_returns4() {
        assertThat(calculateInSampleSize(width = 4000, height = 4000, reqWidth = 1000, reqHeight = 1000))
            .isEqualTo(4)
    }

    @Test
    fun shouldInterchangeSize_90degrees_returnsTrue() {
        assertThat(shouldInterchangeSize(90f)).isTrue()
    }

    @Test
    fun shouldInterchangeSize_270degrees_returnsTrue() {
        assertThat(shouldInterchangeSize(270f)).isTrue()
    }

    @Test
    fun shouldInterchangeSize_180degrees_returnsFalse() {
        assertThat(shouldInterchangeSize(180f)).isFalse()
    }

    @Test
    fun shouldInterchangeSize_0degrees_returnsFalse() {
        assertThat(shouldInterchangeSize(0f)).isFalse()
    }

    // ══ ENH-14 — sample size cho downsample-khi-export, tính theo cạnh dài ══

    @Test
    fun calculateInSampleSizeForLongEdge_reqZero_meansOriginal_returns1() {
        // reqLongEdge = 0 = "Original" (không resize) — giữ hành vi decode full-res.
        assertThat(calculateInSampleSizeForLongEdge(longEdge = 4000, reqLongEdge = 0)).isEqualTo(1)
    }

    @Test
    fun calculateInSampleSizeForLongEdge_imageAlreadySmaller_returns1() {
        assertThat(calculateInSampleSizeForLongEdge(longEdge = 800, reqLongEdge = 1080)).isEqualTo(1)
    }

    @Test
    fun calculateInSampleSizeForLongEdge_nonSquareImage_gatesOnLongEdgeOnly() {
        // Ảnh 3200x1600 (cạnh dài 3200) target 1080 — dùng chung calculateInSampleSize(reqWidth=
        // reqHeight=1080) sẽ SAI (dừng sớm ở inSample=1 vì cạnh ngắn 1600/1=1600>=1080 nhưng
        // 1600/2=800<1080 → NHƯNG hàm đó bắt CẢ 2 cạnh cùng thoả nên vẫn dừng đúng ở test này;
        // case rõ ràng hơn là ảnh rất dẹt, xem test dưới). Ở đây xác nhận cạnh dài 3200 → inSample=2.
        assertThat(calculateInSampleSizeForLongEdge(longEdge = 3200, reqLongEdge = 1080)).isEqualTo(2)
    }

    @Test
    fun calculateInSampleSizeForLongEdge_veryWideImage_doesNotUnderDownsample() {
        // Ảnh cực dẹt (panorama) cạnh dài 8000, cạnh ngắn giả định rất nhỏ (không truyền vào hàm
        // vì hàm chỉ nhận đúng 1 cạnh dài) — calculateInSampleSize(reqW=reqH=1080) trên ảnh dẹt
        // thật (vd 8000x400) sẽ dừng downsample gần như ngay (cạnh ngắn 400 đã < 1080 từ đầu),
        // trong khi calculateInSampleSizeForLongEdge downsample đúng theo cạnh dài, không bị cạnh
        // ngắn "khoá" sớm.
        assertThat(calculateInSampleSizeForLongEdge(longEdge = 8000, reqLongEdge = 1080)).isEqualTo(4)
    }

    @Test
    fun calculateInSampleSizeForLongEdge_exactlyRequested_returns1() {
        assertThat(calculateInSampleSizeForLongEdge(longEdge = 1080, reqLongEdge = 1080)).isEqualTo(1)
    }

    // ---- BUG-43: parseExposureTime ----

    @Test
    fun parseExposureTime_fastShutter_returnsFractionFormat() {
        // 1/125s = 0.008
        assertThat(parseExposureTime("0.008")).isEqualTo("1/125s")
    }

    @Test
    fun parseExposureTime_slowShutter_returnsRawSecondsFormat() {
        assertThat(parseExposureTime("2")).isEqualTo("2s")
    }

    @Test
    fun parseExposureTime_zero_returnsEmpty_notInfinityOrMaxInt() {
        assertThat(parseExposureTime("0")).isEmpty()
    }

    @Test
    fun parseExposureTime_garbageString_doesNotThrow_returnsRawWithSuffix() {
        assertThat(parseExposureTime("not_a_number")).isEqualTo("not_a_numbers")
    }

    @Test
    fun parseExposureTime_empty_returnsEmpty() {
        assertThat(parseExposureTime("")).isEmpty()
    }

    // ---- BUG-43: parseFocalLength ----

    @Test
    fun parseFocalLength_validRational_roundsWholeNumber_noTrailingDotZero() {
        assertThat(parseFocalLength("50/1")).isEqualTo("50mm")
    }

    @Test
    fun parseFocalLength_validRational_keepsRealFraction() {
        assertThat(parseFocalLength("235/10")).isEqualTo("23.5mm")
    }

    @Test
    fun parseFocalLength_zeroDenominator_returnsEmpty_notInfinity() {
        assertThat(parseFocalLength("50/0")).isEmpty()
    }

    @Test
    fun parseFocalLength_garbageNumerator_doesNotThrow_returnsEmpty() {
        assertThat(parseFocalLength("abc/2")).isEmpty()
    }

    @Test
    fun parseFocalLength_notRationalFormat_returnsRawWithSuffix() {
        assertThat(parseFocalLength("50")).isEqualTo("50mm")
    }

    @Test
    fun parseFocalLength_empty_returnsEmpty() {
        assertThat(parseFocalLength("")).isEmpty()
    }
}
