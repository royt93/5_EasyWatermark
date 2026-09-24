package com.mckimquyen.watermark.ui.widget

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG phát hiện qua audit R5 2026-09-24: [MultiSelectRv.lastItemIndex] (dùng trong touch-handler
 * auto-scroll khi kéo chọn nhiều item) trước đây `adapter!!.itemCount` force-unwrap trực tiếp —
 * nếu gesture chạy lúc `adapter` null (list rỗng/RecyclerView detach giữa lúc kéo) crash thật.
 */
@RunWith(RobolectricTestRunner::class)
class MultiSelectRvWidgetTest {

    private class FakeAdapter(private val count: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            throw UnsupportedOperationException("not needed for this test")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        override fun getItemCount(): Int = count
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun lastItemIndex_noAdapterSet_doesNotCrash_returnsNegativeOne() {
        val rv = MultiSelectRv(context)

        assertThat(rv.lastItemIndex()).isEqualTo(-1)
    }

    @Test
    fun lastItemIndex_adapterSetToNull_afterHavingItems_doesNotCrash() {
        val rv = MultiSelectRv(context)
        rv.adapter = FakeAdapter(5)
        assertThat(rv.lastItemIndex()).isEqualTo(4)

        rv.adapter = null

        assertThat(rv.lastItemIndex()).isEqualTo(-1)
    }

    @Test
    fun lastItemIndex_adapterWithItems_returnsCountMinusOne() {
        val rv = MultiSelectRv(context)
        rv.adapter = FakeAdapter(12)

        assertThat(rv.lastItemIndex()).isEqualTo(11)
    }
}
