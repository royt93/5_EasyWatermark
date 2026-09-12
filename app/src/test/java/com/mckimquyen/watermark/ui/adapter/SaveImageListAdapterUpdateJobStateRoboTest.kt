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
 * ENH-08: `ImageInfo` bây giờ bất biến — `MainViewModel.generateList()` gửi qua `saveProcess`
 * NHỮNG COPY MỚI (jobState/result khác object cũ đang nằm trong `data`), không còn cùng tham
 * chiếu như trước (khi còn mutate tại chỗ). `updateJobState()` phải tìm đúng item theo `uri` (khoá
 * ổn định) — nếu tìm bằng full-equals/`indexOf()` như code cũ, sẽ luôn trả về "không tìm thấy" vì
 * jobState khác nhau khiến 2 object không bao giờ `==` nhau — và phải thực sự thay thế item trong
 * `differ` (qua `submitList`), nếu không `finishCount`/`failCount` (ENH-13) mãi kẹt ở state cũ.
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageListAdapterUpdateJobStateRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun imageInfo(name: String, jobState: JobState) =
        ImageInfo(Uri.parse("content://media/$name"), jobState = jobState)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** `AsyncListDiffer.submitList()` diff mặc định chạy trên background executor thật (thread
     * pool thật, ngoài tầm kiểm soát fake main looper) rồi mới post callback về main — 1 lần
     * `idle()` không đủ, phải bơm lặp lại tới khi diff xong (giống pattern `awaitX()` đã dùng ở
     * các test bất đồng bộ khác trong project). */
    private fun awaitFinishCount(adapter: SaveImageListAdapter, expected: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idle()
            if (adapter.finishCount == expected) return
            Thread.sleep(20)
        }
    }

    @Test
    fun updateJobState_withImmutableCopy_findsItemByUriAndUpdatesCounts() {
        val adapter = SaveImageListAdapter(context)
        val a = imageInfo("a", JobState.Ready)
        val b = imageInfo("b", JobState.Ready)
        adapter.submitList(listOf(a, b))
        idle()
        assertThat(adapter.finishCount).isEqualTo(0)

        // Mô phỏng đúng luồng thật: generateList() không mutate `a`, mà tạo COPY mới qua copy()
        // (khác object reference, jobState khác) — đây chính là dữ liệu updateJobState() nhận được.
        val aSuccess = a.copy(jobState = JobState.Success(Result.success(null)))
        adapter.updateJobState(aSuccess)
        awaitFinishCount(adapter, 1)

        assertThat(adapter.finishCount).isEqualTo(1)
        assertThat(adapter.getItem(0)?.jobState).isInstanceOf(JobState.Success::class.java)
        // Item khác (b) không bị ảnh hưởng.
        assertThat(adapter.getItem(1)?.jobState).isEqualTo(JobState.Ready)
    }

    @Test
    fun updateJobState_unknownUri_doesNothing() {
        val adapter = SaveImageListAdapter(context)
        adapter.submitList(listOf(imageInfo("a", JobState.Ready)))
        idle()

        adapter.updateJobState(imageInfo("not-in-list", JobState.Success(Result.success(null))))
        idle()

        assertThat(adapter.finishCount).isEqualTo(0)
        assertThat(adapter.itemCount).isEqualTo(1)
    }

    @Test
    fun updateJobState_null_doesNothing() {
        val adapter = SaveImageListAdapter(context)
        adapter.submitList(listOf(imageInfo("a", JobState.Ready)))
        idle()

        adapter.updateJobState(null)
        idle()

        assertThat(adapter.finishCount).isEqualTo(0)
    }

    /**
     * Bug thật phát hiện qua smoke test batch 2 ảnh trên thiết bị thật (TECNO BG6): 1 ảnh nặng
     * (icon watermark tile trên ảnh camera full-res) mất >60s xử lý — file export ra ĐÚNG và ĐẦY
     * ĐỦ trên đĩa, nhưng card của ảnh đó trong danh sách kẹt mãi ở icon "đang xử lý", không bao
     * giờ chuyển sang icon thành công.
     *
     * Root cause: `updateJobState()` (bản trước fix) build list mới bằng `data.toMutableList()`
     * (= `differ.currentList`) — list này CHỈ cập nhật SAU KHI `AsyncListDiffer` tính xong diff
     * trên background executor (bất đồng bộ). `generateList()` gọi `updateJobState()` 4 lần liên
     * tiếp rất nhanh (2 ảnh × 2 bước Ready→Ing→Success) — nếu gọi lần 2 trước khi lần 1 kịp áp
     * dụng vào `currentList`, lần 2 đọc `data` vẫn là snapshot CŨ (chưa có update lần 1) rồi
     * submit đè lên, xoá mất update lần 1. Test này gọi 4 lần liên tiếp KHÔNG `idle()` xen giữa —
     * tái hiện đúng race, chỉ pass khi dùng `pendingList` (mutate đồng bộ) thay vì snapshot
     * `differ.currentList`.
     */
    @Test
    fun updateJobState_rapidSuccessiveCallsForDifferentItems_noUpdateLost() {
        val adapter = SaveImageListAdapter(context)
        val a = imageInfo("a", JobState.Ready)
        val b = imageInfo("b", JobState.Ready)
        adapter.submitList(listOf(a, b))
        idle()

        // Không idle() giữa các lệnh — mô phỏng đúng race: AsyncListDiffer chưa kịp áp dụng lần
        // trước vào currentList khi lần sau đã gọi tới.
        adapter.updateJobState(a.copy(jobState = JobState.Ing))
        adapter.updateJobState(b.copy(jobState = JobState.Ing))
        adapter.updateJobState(a.copy(jobState = JobState.Success(Result.success(null))))
        adapter.updateJobState(b.copy(jobState = JobState.Success(Result.success(null))))

        awaitFinishCount(adapter, 2)

        assertThat(adapter.finishCount).isEqualTo(2)
        assertThat(adapter.getItem(0)?.jobState).isInstanceOf(JobState.Success::class.java)
        assertThat(adapter.getItem(1)?.jobState).isInstanceOf(JobState.Success::class.java)
    }
}
