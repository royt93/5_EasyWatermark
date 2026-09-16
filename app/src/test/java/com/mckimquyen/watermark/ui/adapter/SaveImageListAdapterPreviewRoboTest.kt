package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.export.BatchExportEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-07: [SaveImageListAdapter] phải render bitmap watermark trả về từ [generatePreview] (thay
 * ảnh gốc) + hiển thị text ước tính từ [estimateOutput], và KHÔNG gọi lại preview khi rebind
 * CÙNG uri (payload đổi jobState lúc export chạy) — tránh build lại canvas/shader tốn kém 2-3
 * lần/ảnh cho cùng 1 lần export (xem comment trong [SaveImageListAdapter.processUI]).
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageListAdapterPreviewRoboTest {

    // M3 migration: ProgressImageView đọc ?attr/colorTertiary, ?attr/colorError — theme mặc định
    // Robolectric không có attr M3 nên phải bọc ContextThemeWrapper(R.style.Theme_MyApp), giống
    // pattern DlgSaveFileLayoutRoboTest/BatchCaptionLayoutRoboTest.
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun previewBitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    private fun createBoundHolder(
        adapter: SaveImageListAdapter,
        position: Int
    ): SaveImageListAdapter.ImageHolder {
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    private fun tvPreviewInfo(holder: SaveImageListAdapter.ImageHolder) =
        holder.itemView.findViewById<android.widget.TextView>(R.id.tvPreviewInfo)

    @Test
    fun bind_previewReady_showsWatermarkedBitmapAndEstimateText() {
        val watermarked = previewBitmap()
        var generatePreviewCallCount = 0
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ ->
                generatePreviewCallCount++
                BatchExportEngine.PreviewResult(watermarked, approxOriginalWidth = 4000, approxOriginalHeight = 3000)
            },
            estimateOutput = { w, h -> (w to h) to 123_456L }
        )
        adapter.submitList(listOf(ImageInfo(Uri.parse("content://media/a"))))

        val holder = createBoundHolder(adapter, 0)

        assertThat(generatePreviewCallCount).isEqualTo(1)
        assertThat(holder.ivIcon.drawable).isNotNull()
        assertThat(tvPreviewInfo(holder).isVisible).isTrue()
        assertThat(tvPreviewInfo(holder).text.toString()).contains("4000")
        assertThat(tvPreviewInfo(holder).text.toString()).contains("3000")
    }

    @Test
    fun bind_previewGenerationFails_fallsBackToOriginalWithoutEstimateText() {
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        adapter.submitList(listOf(ImageInfo(Uri.parse("content://media/a"))))

        val holder = createBoundHolder(adapter, 0)

        assertThat(tvPreviewInfo(holder).isVisible).isFalse()
    }

    @Test
    fun rebind_samePayloadUri_doesNotRegeneratePreview() {
        var generatePreviewCallCount = 0
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ ->
                generatePreviewCallCount++
                BatchExportEngine.PreviewResult(previewBitmap(), 1000, 1000)
            },
            estimateOutput = { w, h -> (w to h) to 1L }
        )
        adapter.submitList(listOf(ImageInfo(Uri.parse("content://media/a"))))

        val holder = createBoundHolder(adapter, 0)
        assertThat(generatePreviewCallCount).isEqualTo(1)

        // Mô phỏng rebind qua payload "state" (đổi jobState lúc export, cùng uri) — không được
        // build lại preview lần nữa.
        adapter.onBindViewHolder(holder, 0, mutableListOf("state"))

        assertThat(generatePreviewCallCount).isEqualTo(1)
    }
}
