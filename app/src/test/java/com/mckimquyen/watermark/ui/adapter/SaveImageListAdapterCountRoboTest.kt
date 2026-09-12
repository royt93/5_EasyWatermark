package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-13: `SaveImageListAdapter.finishCount`/`failCount` phải đếm đúng riêng từng loại
 * `JobState` — trước đây chỉ có `finishCount` (đếm Success), UI cuối batch không biết được có
 * bao nhiêu ảnh lỗi để hiển thị (xem `SaveImageBSDialogFragment.exportCountText`).
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageListAdapterCountRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun imageInfo(name: String, jobState: JobState) =
        ImageInfo(Uri.parse("content://media/$name"), jobState = jobState)

    private fun submit(adapter: SaveImageListAdapter, items: List<ImageInfo>) {
        adapter.submitList(items)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun allSuccess_finishCountEqualsTotal_failCountZero() {
        val adapter = SaveImageListAdapter(context)
        submit(
            adapter,
            listOf(
                imageInfo("a", JobState.Success(Result.success(null))),
                imageInfo("b", JobState.Success(Result.success(null)))
            )
        )

        assertThat(adapter.finishCount).isEqualTo(2)
        assertThat(adapter.failCount).isEqualTo(0)
    }

    @Test
    fun mixedSuccessAndFailure_countsEachIndependently() {
        val adapter = SaveImageListAdapter(context)
        submit(
            adapter,
            listOf(
                imageInfo("a", JobState.Success(Result.success(null))),
                imageInfo("b", JobState.Failure(Result.failure(null))),
                imageInfo("c", JobState.Failure(Result.failure(null))),
                imageInfo("d", JobState.Ing)
            )
        )

        assertThat(adapter.finishCount).isEqualTo(1)
        assertThat(adapter.failCount).isEqualTo(2)
        assertThat(adapter.itemCount).isEqualTo(4)
    }

    @Test
    fun noneFinishedYet_bothCountsZero() {
        val adapter = SaveImageListAdapter(context)
        submit(adapter, listOf(imageInfo("a", JobState.Ready), imageInfo("b", JobState.Ing)))

        assertThat(adapter.finishCount).isEqualTo(0)
        assertThat(adapter.failCount).isEqualTo(0)
    }
}
