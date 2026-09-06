package com.mckimquyen.watermark.ui.widget

import android.app.ActivityManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Unit test (Robolectric): [DetectedPerformanceSeekBarListener] nay nhan `context` qua
 * constructor thay vi doc `MyApplication.instance` (dep chinh cua doc/todo.md). Xac nhan
 * predicate hieu nang dung context duoc truyen vao (khong con phu thuoc static field da xoa).
 */
@RunWith(RobolectricTestRunner::class)
class DetectedPerformanceSeekBarListenerRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun waterMark(mode: WaterMarkRepository.MarkMode) = WaterMark(
        text = "",
        textSize = 14f,
        textColor = 0,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = 255,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = Uri.EMPTY,
        markMode = mode,
        enableBounds = false
    )

    private fun setLowMemory(lowMemory: Boolean) {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().apply { this.lowMemory = lowMemory }
        shadowOf(activityManager).setMemoryInfo(info)
    }

    @Suppress("UNCHECKED_CAST")
    private fun predicateOf(listener: DetectedPerformanceSeekBarListener): () -> Boolean {
        val field = listener.javaClass.getDeclaredField("isHighPerformancePredicate")
        field.isAccessible = true
        return field.get(listener) as () -> Boolean
    }

    @Test
    fun textMode_alwaysHighPerformance_regardlessOfMemory() {
        setLowMemory(true)
        val listener = DetectedPerformanceSeekBarListener(context, waterMark(WaterMarkRepository.MarkMode.Text))

        assertThat(predicateOf(listener)()).isTrue()
    }

    @Test
    fun imageMode_lowMemory_isNotHighPerformance() {
        setLowMemory(true)
        val listener = DetectedPerformanceSeekBarListener(context, waterMark(WaterMarkRepository.MarkMode.Image))

        assertThat(predicateOf(listener)()).isFalse()
    }

    @Test
    fun imageMode_normalMemory_isHighPerformance() {
        setLowMemory(false)
        val listener = DetectedPerformanceSeekBarListener(context, waterMark(WaterMarkRepository.MarkMode.Image))

        assertThat(predicateOf(listener)()).isTrue()
    }

    @Test
    fun nullConfig_fallsBackTo_memoryCheckOnly() {
        setLowMemory(true)
        val listener = DetectedPerformanceSeekBarListener(context, config = null)

        assertThat(predicateOf(listener)()).isFalse()
    }
}
