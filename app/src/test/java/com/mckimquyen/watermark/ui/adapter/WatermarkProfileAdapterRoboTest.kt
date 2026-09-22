package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** FEAT-06: bind đúng tên/thời gian, tap Apply/Delete gọi đúng callback với item hiện tại. */
@RunWith(RobolectricTestRunner::class)
class WatermarkProfileAdapterRoboTest {

    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun entity(name: String, id: Long = 1L) = WatermarkProfileEntity(
        id = id,
        name = name,
        createdAt = 1_700_000_000_000L,
        text = "hello",
        textSize = 14f,
        textColor = 0xFFFFFF,
        textStyleKey = 0,
        textTypefaceKey = 0,
        alpha = 255,
        degree = 315f,
        hGap = 0,
        vGap = 0,
        iconUri = "",
        markModeValue = 0,
        enableBounds = false,
        enableExif = false,
        exifFrameStyle = 0,
        anchor = 4,
        marginPercent = 0.05f
    )

    private fun awaitList(adapter: WatermarkProfileAdapter, expectedSize: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (adapter.itemCount == expectedSize) return
            Thread.sleep(20)
        }
    }

    @Test
    fun bind_showsProfileName() {
        val adapter = WatermarkProfileAdapter(onApply = {}, onDelete = {})
        adapter.submitList(listOf(entity("Instagram")))
        awaitList(adapter, 1)

        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)

        assertThat(holder.binding.tvName.text.toString()).isEqualTo("Instagram")
    }

    @Test
    fun tapApply_invokesCallbackWithCurrentItem() {
        var applied: WatermarkProfileEntity? = null
        val adapter = WatermarkProfileAdapter(onApply = { applied = it }, onDelete = {})
        val item = entity("Khách A")
        adapter.submitList(listOf(item))
        awaitList(adapter, 1)

        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        holder.binding.btnApply.performClick()

        assertThat(applied).isEqualTo(item)
    }

    @Test
    fun tapDelete_invokesCallbackWithCurrentItem() {
        var deleted: WatermarkProfileEntity? = null
        val adapter = WatermarkProfileAdapter(onApply = {}, onDelete = { deleted = it })
        val item = entity("Khách B")
        adapter.submitList(listOf(item))
        awaitList(adapter, 1)

        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        holder.binding.btnDelete.performClick()

        assertThat(deleted).isEqualTo(item)
    }
}
