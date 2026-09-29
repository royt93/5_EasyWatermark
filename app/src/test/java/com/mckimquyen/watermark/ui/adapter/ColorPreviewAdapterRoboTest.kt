package com.mckimquyen.watermark.ui.adapter

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-AUDIT-2026-09-29: `init` block gọi `previewList.last()` không guard rỗng — construct với
 * list rỗng ném `NoSuchElementException`. Chưa call site thật nào truyền rỗng hiện tại, nhưng đây
 * là bẫy crash tiềm ẩn nếu sau này `colorArrayList` động theo config/DB có thể rỗng.
 */
@RunWith(RobolectricTestRunner::class)
class ColorPreviewAdapterRoboTest {

    @Test
    fun constructor_emptyList_doesNotThrow() {
        val adapter = ColorPreviewAdapter(ArrayList())

        assertThat(adapter.itemCount).isEqualTo(0)
    }

    @Test
    fun constructor_noItemPreSelected_selectsLastItem() {
        val list = arrayListOf(
            ColorPreviewAdapter.PreViewModel(color = 0xFF0000),
            ColorPreviewAdapter.PreViewModel(color = 0x00FF00),
            ColorPreviewAdapter.PreViewModel(color = 0x0000FF)
        )

        ColorPreviewAdapter(list)

        assertThat(list.last().selected).isTrue()
        assertThat(list.dropLast(1).any { it.selected }).isFalse()
    }

    @Test
    fun constructor_itemAlreadySelected_doesNotOverrideSelection() {
        val list = arrayListOf(
            ColorPreviewAdapter.PreViewModel(color = 0xFF0000, selected = true),
            ColorPreviewAdapter.PreViewModel(color = 0x00FF00)
        )

        ColorPreviewAdapter(list)

        assertThat(list[0].selected).isTrue()
        assertThat(list[1].selected).isFalse()
    }
}
