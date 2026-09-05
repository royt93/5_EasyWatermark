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
}
