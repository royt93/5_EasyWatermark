package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-03: bind đúng tóm tắt layer (mode + nội dung), nút lên/xuống disable đúng ở 2 đầu danh
 * sách, tap row/nút gọi đúng callback với INDEX hiện tại (không phải reference item, vì layer
 * không có id ổn định).
 */
@RunWith(RobolectricTestRunner::class)
class WatermarkLayerAdapterRoboTest {

    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun textLayer(text: String) = WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text, text = text)

    // Gắn RecyclerView thật + layout để `bindingAdapterPosition` hợp lệ (gọi onBind trực tiếp trả NO_POSITION).
    private fun attach(adapter: WatermarkLayerAdapter, layers: List<WatermarkLayer>): RecyclerView {
        val rv = RecyclerView(context)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter
        adapter.submitList(layers)
        layoutNow(rv)
        return rv
    }

    private fun layoutNow(rv: RecyclerView) {
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(RV_SIZE_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(RV_SIZE_PX, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, RV_SIZE_PX, RV_SIZE_PX)
    }

    private fun bindHolder(adapter: WatermarkLayerAdapter, layers: List<WatermarkLayer>, position: Int): WatermarkLayerAdapter.ViewHolder {
        val rv = attach(adapter, layers)
        return rv.findViewHolderForAdapterPosition(position) as WatermarkLayerAdapter.ViewHolder
    }

    @Test
    fun bind_textLayer_showsModeAndText() {
        val adapter = WatermarkLayerAdapter(onTap = {}, onMoveUp = {}, onMoveDown = {}, onDelete = {})
        val holder = bindHolder(adapter, listOf(textLayer("Copyright 2026")), 0)

        val summary = holder.binding.tvLayerSummary.text.toString()
        assertThat(summary).contains(context.getString(R.string.water_mark_mode_text))
        assertThat(summary).contains("Copyright 2026")
    }

    @Test
    fun bind_imageLayer_showsModeAndIconName() {
        val adapter = WatermarkLayerAdapter(onTap = {}, onMoveUp = {}, onMoveDown = {}, onDelete = {})
        val layer = WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Image, iconUri = android.net.Uri.parse("content://media/logo.png"))
        val holder = bindHolder(adapter, listOf(layer), 0)

        val summary = holder.binding.tvLayerSummary.text.toString()
        assertThat(summary).contains(context.getString(R.string.water_mark_mode_image))
        assertThat(summary).contains("logo.png")
    }

    @Test
    fun firstItem_moveUpDisabled_moveDownEnabled() {
        val adapter = WatermarkLayerAdapter(onTap = {}, onMoveUp = {}, onMoveDown = {}, onDelete = {})
        val holder = bindHolder(adapter, listOf(textLayer("A"), textLayer("B")), 0)

        assertThat(holder.binding.btnMoveUp.isEnabled).isFalse()
        assertThat(holder.binding.btnMoveDown.isEnabled).isTrue()
    }

    @Test
    fun lastItem_moveDownDisabled_moveUpEnabled() {
        val adapter = WatermarkLayerAdapter(onTap = {}, onMoveUp = {}, onMoveDown = {}, onDelete = {})
        val holder = bindHolder(adapter, listOf(textLayer("A"), textLayer("B")), 1)

        assertThat(holder.binding.btnMoveUp.isEnabled).isTrue()
        assertThat(holder.binding.btnMoveDown.isEnabled).isFalse()
    }

    @Test
    fun tapRow_invokesOnTapWithPosition() {
        var tappedIndex: Int? = null
        val adapter = WatermarkLayerAdapter(onTap = { tappedIndex = it }, onMoveUp = {}, onMoveDown = {}, onDelete = {})
        val holder = bindHolder(adapter, listOf(textLayer("A"), textLayer("B")), 1)

        holder.binding.root.performClick()

        assertThat(tappedIndex).isEqualTo(1)
    }

    @Test
    fun tapDelete_invokesOnDeleteWithPosition() {
        var deletedIndex: Int? = null
        val adapter = WatermarkLayerAdapter(onTap = {}, onMoveUp = {}, onMoveDown = {}, onDelete = { deletedIndex = it })
        val holder = bindHolder(adapter, listOf(textLayer("A"), textLayer("B")), 0)

        holder.binding.btnDelete.performClick()

        assertThat(deletedIndex).isEqualTo(0)
    }

    @Test
    fun tapMoveUp_invokesOnMoveUpWithPosition() {
        var movedIndex: Int? = null
        val adapter = WatermarkLayerAdapter(onTap = {}, onMoveUp = { movedIndex = it }, onMoveDown = {}, onDelete = {})
        val holder = bindHolder(adapter, listOf(textLayer("A"), textLayer("B")), 1)

        holder.binding.btnMoveUp.performClick()

        assertThat(movedIndex).isEqualTo(1)
    }

    @Test
    fun tapMoveDown_invokesOnMoveDownWithPosition() {
        var movedIndex: Int? = null
        val adapter = WatermarkLayerAdapter(onTap = {}, onMoveUp = {}, onMoveDown = { movedIndex = it }, onDelete = {})
        val holder = bindHolder(adapter, listOf(textLayer("A"), textLayer("B")), 0)

        holder.binding.btnMoveDown.performClick()

        assertThat(movedIndex).isEqualTo(0)
    }

    @Test
    fun clickOnStaleHolderAfterSubmitList_beforeRelayout_isIgnored() {
        // BUG-75: sau submitList (notifyDataSetChanged) holder cũ chưa rebind; click lúc đó
        // từng gọi callback với position đã capture (có thể out-of-bounds / sai layer).
        // Fix: dùng `currentIndex()` return null khi `bindingAdapterPosition==NO_POSITION` → ignore click.
        var tapCount = 0
        val adapter = WatermarkLayerAdapter(
            onTap = { tapCount++ },
            onMoveUp = { tapCount++ },
            onMoveDown = { tapCount++ },
            onDelete = { tapCount++ }
        )
        val rv = attach(adapter, listOf(textLayer("A"), textLayer("B")))
        val holder = rv.findViewHolderForAdapterPosition(1) as WatermarkLayerAdapter.ViewHolder

        adapter.submitList(listOf(textLayer("A"))) // chưa layout lại → holder cũ có bindingAdapterPosition = NO_POSITION

        holder.binding.btnDelete.performClick()
        holder.binding.btnMoveUp.performClick()
        holder.binding.btnMoveDown.performClick()
        holder.binding.root.performClick()

        assertThat(tapCount).isEqualTo(0)
    }

    // BUG-77: 3 nút icon (lên/xuống/xoá) phải đạt touch target 48dp.
    @Test
    fun actionButtons_touchTargetAtLeast48dp() {
        val holder = bindHolder(WatermarkLayerAdapter({}, {}, {}, {}), listOf(textLayer("A")), 0)
        val minPx = 48 * context.resources.displayMetrics.density
        listOf(holder.binding.btnMoveUp, holder.binding.btnMoveDown, holder.binding.btnDelete).forEach {
            assertThat(it.layoutParams.width.toFloat()).isAtLeast(minPx)
            assertThat(it.layoutParams.height.toFloat()).isAtLeast(minPx)
        }
    }

    private companion object {
        const val RV_SIZE_PX = 2000
    }
}
