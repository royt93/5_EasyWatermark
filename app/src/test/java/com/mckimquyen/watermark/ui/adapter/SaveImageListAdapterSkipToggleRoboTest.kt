package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-17: nút toggle skip/active (`ivSkipToggle`) trên mỗi card preview — tap gọi đúng callback
 * với item hiện tại, icon/độ mờ đổi đúng theo [ImageInfo.isSkippedInExport].
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageListAdapterSkipToggleRoboTest {

    // M3 migration: ProgressImageView đọc ?attr/colorTertiary, ?attr/colorError — cần theme M3.
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun createBoundHolder(
        adapter: SaveImageListAdapter,
        position: Int
    ): SaveImageListAdapter.ImageHolder {
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    @Test
    fun tapSkipToggle_invokesCallbackWithCurrentItem() {
        var toggled: ImageInfo? = null
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L },
            onToggleSkip = { toggled = it }
        )
        val info = ImageInfo(Uri.parse("content://media/a"))
        adapter.submitList(listOf(info))

        val holder = createBoundHolder(adapter, 0)
        holder.ivSkipToggle.performClick()

        assertThat(toggled).isEqualTo(info)
    }

    @Test
    fun bind_activeImage_showsCheckIconFullOpacity() {
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        adapter.submitList(listOf(ImageInfo(Uri.parse("content://media/a"), isSkippedInExport = false)))

        val holder = createBoundHolder(adapter, 0)

        assertThat(holder.itemView.alpha).isEqualTo(1f)
    }

    @Test
    fun bind_skippedImage_dimsThumbnail() {
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        adapter.submitList(listOf(ImageInfo(Uri.parse("content://media/a"), isSkippedInExport = true)))

        val holder = createBoundHolder(adapter, 0)

        assertThat(holder.itemView.alpha).isLessThan(1f)
    }

    /** `AsyncListDiffer.submitList()` diff chạy trên background executor thật — bơm main looper
     * lặp lại tới khi `getItem()` phản ánh giá trị mới, cùng pattern `awaitFinishCount()` ở
     * [SaveImageListAdapterUpdateJobStateRoboTest]. */
    private fun awaitSkipFlag(adapter: SaveImageListAdapter, position: Int, expected: Boolean, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (adapter.getItem(position)?.isSkippedInExport == expected) return
            Thread.sleep(20)
        }
    }

    @Test
    fun updateJobState_togglingSkipFlag_updatesDimmingOnRebind() {
        val adapter = testAdapter(context)
        val info = ImageInfo(Uri.parse("content://media/a"))
        adapter.submitList(listOf(info))
        val holder = createBoundHolder(adapter, 0)
        assertThat(holder.itemView.alpha).isEqualTo(1f)

        // Cùng cơ chế Fragment thật dùng: copy + updateJobState() (xem SaveImageBSDialogFragment.onToggleSkip).
        adapter.updateJobState(info.copy(isSkippedInExport = true))
        awaitSkipFlag(adapter, 0, expected = true)
        // Mô phỏng rebind mà notifyItemChanged() trong updateJobState() sẽ trigger khi gắn RecyclerView thật.
        adapter.onBindViewHolder(holder, 0, mutableListOf("state"))

        assertThat(holder.itemView.alpha).isLessThan(1f)
    }
}
