package com.mckimquyen.watermark.data.repo

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-12: Undo/Redo cho chỉnh sửa watermark trong editor — [WaterMarkRepository.undo]/[redo],
 * `canUndo`/`canRedo`, và debounce gộp nhiều lần gọi liên tục (kéo slider) thành 1 entry Undo duy
 * nhất (AC "Stack không phình to bất thường khi thao tác kéo/pinch liên tục"). DataStore cô lập —
 * xem lý do ở `testutil/TestDataStores.kt`.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryUndoRedoRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    @Test
    fun freshRepo_undoRedoDisabled() = runBlocking {
        assertThat(repo.canUndo.value).isFalse()
        assertThat(repo.canRedo.value).isFalse()
    }

    @Test
    fun undo_afterSingleChange_restoresOriginalValue() = runBlocking {
        val originalText = repo.waterMark.first().text

        repo.updateText("changed")
        assertThat(repo.waterMark.first().text).isEqualTo("changed")
        assertThat(repo.canUndo.value).isTrue()

        repo.undo()

        assertThat(repo.waterMark.first().text).isEqualTo(originalText)
    }

    @Test
    fun redo_afterUndo_restoresChangedValue() = runBlocking {
        repo.updateText("changed")
        repo.undo()
        assertThat(repo.canRedo.value).isTrue()

        repo.redo()

        assertThat(repo.waterMark.first().text).isEqualTo("changed")
    }

    @Test
    fun undo_emptyStack_isNoOp() = runBlocking {
        val originalText = repo.waterMark.first().text

        repo.undo()

        assertThat(repo.waterMark.first().text).isEqualTo(originalText)
        assertThat(repo.canUndo.value).isFalse()
    }

    @Test
    fun redo_emptyStack_isNoOp() = runBlocking {
        val originalText = repo.waterMark.first().text

        repo.redo()

        assertThat(repo.waterMark.first().text).isEqualTo(originalText)
        assertThat(repo.canRedo.value).isFalse()
    }

    @Test
    fun newEditAfterUndo_clearsRedoStack() = runBlocking {
        repo.updateText("A")
        repo.undo()
        assertThat(repo.canRedo.value).isTrue()

        repo.updateText("B")

        assertThat(repo.canRedo.value).isFalse()
        repo.redo()
        // redo no-op — vẫn giữ nguyên "B", không nhảy về "A" (nhánh redo cũ đã bị huỷ đúng).
        assertThat(repo.waterMark.first().text).isEqualTo("B")
    }

    @Test
    fun multipleUndo_walksBackThroughHistoryInOrder() = runBlocking {
        val original = repo.waterMark.first().text
        repo.updateText("A")
        Thread.sleep(WaterMarkRepository.UNDO_SNAPSHOT_DEBOUNCE_MS + 50)
        repo.updateText("B")
        Thread.sleep(WaterMarkRepository.UNDO_SNAPSHOT_DEBOUNCE_MS + 50)
        repo.updateText("C")

        assertThat(repo.waterMark.first().text).isEqualTo("C")
        repo.undo()
        assertThat(repo.waterMark.first().text).isEqualTo("B")
        repo.undo()
        assertThat(repo.waterMark.first().text).isEqualTo("A")
        repo.undo()
        assertThat(repo.waterMark.first().text).isEqualTo(original)
        assertThat(repo.canUndo.value).isFalse()
    }

    @Test
    fun rapidConsecutiveUpdates_withinDebounceWindow_coalesceIntoSingleUndoEntry() = runBlocking {
        val original = repo.waterMark.first().text

        // Mô phỏng nhiều lần gọi liên tục trong CÙNG 1 gesture kéo slider — không sleep giữa các lần.
        repo.updateAlpha(10)
        repo.updateAlpha(20)
        repo.updateAlpha(30)
        repo.updateAlpha(40)

        // Dù gọi 4 lần, chỉ 1 entry Undo được tạo (debounce) — undo() 1 lần phải quay thẳng về gốc.
        repo.undo()

        assertThat(repo.waterMark.first().text).isEqualTo(original)
        assertThat(repo.canUndo.value).isFalse()
    }

    @Test
    fun updatesSeparatedByMoreThanDebounceWindow_createSeparateUndoEntries() = runBlocking {
        repo.updateAlpha(10)
        Thread.sleep(WaterMarkRepository.UNDO_SNAPSHOT_DEBOUNCE_MS + 50)
        repo.updateAlpha(20)

        // 2 entry riêng biệt — undo lần 1 chỉ lùi về alpha=10, chưa về gốc (255 mặc định).
        repo.undo()
        assertThat(repo.waterMark.first().alpha).isEqualTo(10)
        assertThat(repo.canUndo.value).isTrue()
    }

    @Test
    fun applyWaterMark_doesNotPushUndoEntry() = runBlocking {
        // FEAT-06: applyWaterMark() (áp dụng profile) cố tình KHÔNG tham gia Undo/Redo.
        repo.applyWaterMark(repo.waterMark.first().copy(text = "from profile"))

        assertThat(repo.canUndo.value).isFalse()
    }

    @Test
    fun undo_afterAddLayer_restoresPreviousLayerList() = runBlocking {
        // FEAT-03: addLayer/removeLayer/updateLayer/reorderLayer ghi qua snapshotForUndoIfDue()
        // như mọi updateXxx() khác — không cần logic Undo/Redo riêng, chỉ cần data class equals()
        // (đã tự bao gồm extraLayers) hoạt động đúng.
        assertThat(repo.waterMark.first().extraLayers).isEmpty()

        repo.addLayer(com.mckimquyen.watermark.data.model.WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text, text = "logo"))
        assertThat(repo.waterMark.first().extraLayers).hasSize(1)

        repo.undo()

        assertThat(repo.waterMark.first().extraLayers).isEmpty()
    }

    @Test
    fun resetModeToText_doesNotPushUndoEntry() = runBlocking {
        // Bug thật phát hiện qua smoke test: MyApplication.onCreate() gọi resetModeToText() mỗi
        // lần app khởi động (không phải hành động editor của user) — trước fix, lệnh gọi này khiến
        // "Undo" hiện sẵn có thể bấm ngay khi vừa mở app, dù user chưa làm gì.
        repo.resetModeToText()

        assertThat(repo.canUndo.value).isFalse()
    }
}
