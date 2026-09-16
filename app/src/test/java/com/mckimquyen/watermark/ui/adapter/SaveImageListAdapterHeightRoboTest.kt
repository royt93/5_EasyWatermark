package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-27: `onCreateViewHolder` từng tính height của `ivIcon` từ `parent.height` (RecyclerView
 * `rvResult`) — nếu ViewHolder ĐẦU TIÊN được tạo trước khi RecyclerView hoàn tất layout (vd
 * BottomSheetDialog vừa show), `parent.height` = 0 nên thumbnail co về 0dp vĩnh viễn. Test tạo
 * `parent` CHƯA đo/layout (height mặc định = 0, y hệt tình huống lỗi) để xác nhận height của
 * `ivIcon` vẫn đúng — không còn phụ thuộc `parent.height` tại thời điểm tạo ViewHolder.
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageListAdapterHeightRoboTest {

    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun newAdapter() = SaveImageListAdapter(
        context = context,
        scope = CoroutineScope(Dispatchers.Unconfined),
        generatePreview = { _, _ -> null },
        estimateOutput = { w, h -> (w to h) to 0L }
    )

    @Test
    fun onCreateViewHolder_parentNotYetLaidOut_ivIconHeightStillFromDimenResource_notZero() {
        val adapter = newAdapter()
        // parent chưa measure/layout — parent.height = 0, đúng kịch bản lỗi gốc.
        val parent = FrameLayout(context)
        assertThat(parent.height).isEqualTo(0)

        val holder = adapter.onCreateViewHolder(parent, 0)

        val expectedHeight = context.resources.getDimensionPixelSize(R.dimen.save_result_row_height)
        assertThat(holder.ivIcon.layoutParams.height).isEqualTo(expectedHeight)
        assertThat(holder.ivIcon.layoutParams.height).isGreaterThan(0)
    }

    @Test
    fun onCreateViewHolder_manyItems_halvesRowHeightForTwoLineLayout() {
        val adapter = newAdapter()
        adapter.submitList((0 until 6).map { com.mckimquyen.watermark.data.model.ImageInfo(android.net.Uri.parse("content://media/$it")) })
        val parent = FrameLayout(context)

        val holder = adapter.onCreateViewHolder(parent, 0)

        val fullHeight = context.resources.getDimensionPixelSize(R.dimen.save_result_row_height)
        assertThat(holder.ivIcon.layoutParams.height).isEqualTo(fullHeight / 2)
    }
}
