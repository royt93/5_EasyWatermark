package com.mckimquyen.watermark.ui.adapter

import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG phát hiện qua audit R5 2026-09-24: [GalleryAdapter.select]/[GalleryAdapter.unSelect] trước
 * đây dùng `selectedCount.value!!` force-unwrap, khác style `?: 0` ở chỗ khác cùng file (dòng
 * 134) cho cùng field — đã thống nhất về `?: 0`. Test lại đúng hành vi select/unSelect nhiều item.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryAdapterWidgetTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun images(count: Int) = (0 until count).map {
        Image(id = it, uri = Uri.parse("content://media/$it"), name = "img$it.jpg", size = 1024, date = it.toLong())
    }

    @Test
    fun select_range_incrementsSelectedCountByRangeSize() {
        val adapter = GalleryAdapter()
        adapter.submitList(images(5))
        val rv = RecyclerView(context)

        adapter.select(rv, 3)

        assertThat(adapter.selectedCount.value).isEqualTo(4)
        assertThat(adapter.getSelectedList()).hasSize(4)
    }

    @Test
    fun selectThenUnSelect_countReturnsToZero() {
        val adapter = GalleryAdapter()
        adapter.submitList(images(5))
        val rv = RecyclerView(context)
        adapter.select(rv, 4)
        assertThat(adapter.selectedCount.value).isEqualTo(5)

        adapter.unSelect(rv, 0)

        assertThat(adapter.selectedCount.value).isEqualTo(0)
        assertThat(adapter.getSelectedList()).isEmpty()
    }

    @Test
    fun select_calledTwiceOverlappingRange_doesNotDoubleCountAlreadySelectedItems() {
        val adapter = GalleryAdapter()
        adapter.submitList(images(5))
        val rv = RecyclerView(context)
        adapter.select(rv, 2)
        assertThat(adapter.selectedCount.value).isEqualTo(3)

        adapter.select(rv, 4)

        assertThat(adapter.selectedCount.value).isEqualTo(5)
    }
}
