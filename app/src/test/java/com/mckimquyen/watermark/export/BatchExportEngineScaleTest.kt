package com.mckimquyen.watermark.export

import android.graphics.Matrix
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-AUDIT-2026-09-29: [BatchExportEngine.resolveImageScale] từng bị copy-paste đọc `scaleY` từ
 * [Matrix.MSCALE_X] thay vì [Matrix.MSCALE_Y] — vô hại trong pipeline thật (adjustMatrix luôn
 * `postScale` đồng nhất X/Y nên 2 giá trị trùng nhau), nên test PHẢI dùng ma trận scale KHÔNG đồng
 * nhất (X≠Y) để thật sự phân biệt được field nào đang bị đọc — bug cũ sẽ fail test này.
 *
 * Cần [RobolectricTestRunner] (không chạy plain JUnit) — `Matrix.postScale`/`getValues` thật cần
 * shadow Robolectric, plain JUnit stub `android.graphics.Matrix` trả mặc định 0 cho mọi method.
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineScaleTest {

    @Test
    fun resolveImageScale_nonUniformScale_readsXAndYFromCorrectAxis() {
        val matrix = Matrix()
        matrix.postScale(2f, 4f) // scaleX=2, scaleY=4 -> không đồng nhất, phân biệt được 2 field
        val values = FloatArray(9)
        matrix.getValues(values)

        val (scaleX, scaleY) = BatchExportEngine.resolveImageScale(values)

        assertThat(scaleX).isWithin(1e-4f).of(0.5f) // 1/2
        assertThat(scaleY).isWithin(1e-4f).of(0.25f) // 1/4 — bug cũ sẽ trả 0.5f (đọc nhầm MSCALE_X)
    }

    @Test
    fun resolveImageScale_uniformScale_bothAxesEqual() {
        val matrix = Matrix()
        matrix.postScale(0.5f, 0.5f)
        val values = FloatArray(9)
        matrix.getValues(values)

        val (scaleX, scaleY) = BatchExportEngine.resolveImageScale(values)

        assertThat(scaleX).isWithin(1e-4f).of(2f)
        assertThat(scaleY).isWithin(1e-4f).of(2f)
    }
}
