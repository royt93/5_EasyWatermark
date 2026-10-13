package com.mckimquyen.watermark.ui.widget

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-80: `smoothScrollToPosition` nhận `RecyclerView?` — null khi detached. Trước fix: `recyclerView!!.context` NPE.
 * Lưu ý: KHÔNG delegate `super` khi null, vì `LinearLayoutManager` cũng deref `recyclerView.getContext()` → NPE y hệt.
 */
@RunWith(RobolectricTestRunner::class)
class UniformScrollGridLayoutManagerRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun smoothScrollToPosition_nullRecyclerView_isNoOpAndDoesNotThrow() {
        val lm = UniformScrollGridLayoutManager(context, SPAN_COUNT)

        lm.smoothScrollToPosition(null, null, TARGET_POSITION)

        assertThat(lm.isSmoothScrolling).isFalse()
    }

    @Test
    fun smoothScrollToPosition_attachedRecyclerView_startsSmoothScroll() {
        val lm = UniformScrollGridLayoutManager(context, SPAN_COUNT)
        val rv = RecyclerView(context).apply { layoutManager = lm }

        lm.smoothScrollToPosition(rv, null, TARGET_POSITION)

        // Chống over-fix: nhánh bình thường vẫn chạy scroller riêng.
        assertThat(lm.isSmoothScrolling).isTrue()
    }

    private companion object {
        const val SPAN_COUNT = 3
        const val TARGET_POSITION = 5
    }
}
