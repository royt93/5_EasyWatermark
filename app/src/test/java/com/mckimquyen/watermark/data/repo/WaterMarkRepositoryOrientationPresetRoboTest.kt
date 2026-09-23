package com.mckimquyen.watermark.data.repo

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-23: nhớ vị trí watermark (anchor + margin) RIÊNG theo orientation ảnh (dọc/ngang) —
 * [WaterMarkRepository.applyOrientationPreset] áp lại preset đã lưu khi đổi ảnh, [WaterMarkRepository.updateAnchor]/
 * [WaterMarkRepository.updateMargin] lưu vào ĐÚNG preset của orientation đang chọn. DataStore cô
 * lập — xem lý do ở `testutil/TestDataStores.kt`.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryOrientationPresetRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    @Test
    fun applyOrientationPreset_noPresetSavedYet_keepsCurrentActiveValues() = runBlocking {
        val before = repo.waterMark.first()

        repo.applyOrientationPreset(isPortrait = true)

        val after = repo.waterMark.first()
        assertThat(after.anchor).isEqualTo(before.anchor)
        assertThat(after.marginPercent).isEqualTo(before.marginPercent)
    }

    @Test
    fun editingPortraitImage_thenSwitchingToLandscape_doesNotLeakPortraitPosition() = runBlocking {
        // Ảnh dọc — user chỉnh vị trí về góc trên-trái.
        repo.applyOrientationPreset(isPortrait = true)
        repo.updateAnchor(Anchor.TOP_LEFT)
        repo.updateMargin(0.08f)

        // Đổi sang ảnh ngang KHÁC — chưa từng chỉnh gì cho orientation này — không được áp nhầm vị trí dọc.
        repo.applyOrientationPreset(isPortrait = false)

        val landscapeConfig = repo.waterMark.first()
        assertThat(landscapeConfig.anchor).isNotEqualTo(Anchor.TOP_LEFT.ordinal)
    }

    @Test
    fun returningToPortraitOrientation_restoresPreviouslyChosenPosition() = runBlocking {
        repo.applyOrientationPreset(isPortrait = true)
        repo.updateAnchor(Anchor.BOTTOM_RIGHT)
        repo.updateMargin(0.12f)

        // Đi qua ảnh ngang (đổi vị trí khác hẳn cho landscape)...
        repo.applyOrientationPreset(isPortrait = false)
        repo.updateAnchor(Anchor.TOP_CENTER)
        repo.updateMargin(0.02f)

        // ...rồi quay lại ảnh dọc — phải khôi phục ĐÚNG vị trí đã chỉnh cho dọc trước đó.
        repo.applyOrientationPreset(isPortrait = true)

        val restored = repo.waterMark.first()
        assertThat(restored.anchor).isEqualTo(Anchor.BOTTOM_RIGHT.ordinal)
        assertThat(restored.marginPercent).isEqualTo(0.12f)
    }

    @Test
    fun landscapePreset_independentFromPortraitPreset_afterMultipleSwitches() = runBlocking {
        repo.applyOrientationPreset(isPortrait = false)
        repo.updateAnchor(Anchor.CENTER_LEFT)

        repo.applyOrientationPreset(isPortrait = true)
        repo.updateAnchor(Anchor.TOP_RIGHT)

        repo.applyOrientationPreset(isPortrait = false)
        assertThat(repo.waterMark.first().anchor).isEqualTo(Anchor.CENTER_LEFT.ordinal)

        repo.applyOrientationPreset(isPortrait = true)
        assertThat(repo.waterMark.first().anchor).isEqualTo(Anchor.TOP_RIGHT.ordinal)
    }

    @Test
    fun applyOrientationPreset_doesNotPushUndoEntry() = runBlocking {
        // Cùng nguyên tắc FEAT-12: đổi ảnh (hệ thống tự áp preset) không phải 1 hành động user Undo được.
        repo.applyOrientationPreset(isPortrait = true)

        assertThat(repo.canUndo.value).isFalse()
    }

    @Test
    fun updateAnchor_beforeAnyOrientationKnown_stillUpdatesActiveValue_savesNoPreset() = runBlocking {
        // Trường hợp hiếm: user bấm trước khi ảnh đầu tiên decode xong — vẫn áp dụng NGAY (preview
        // tức thời), nhưng không có orientation nào để lưu preset (chưa biết dọc/ngang).
        repo.updateAnchor(Anchor.BOTTOM_LEFT)

        assertThat(repo.waterMark.first().anchor).isEqualTo(Anchor.BOTTOM_LEFT.ordinal)
    }

    @Test
    fun applyOrientationPreset_noPresetForThisOrientation_fallsBackToDefault_notLeftoverFromOtherOrientation() = runBlocking {
        // Không phải chỉ "giữ nguyên nếu không có preset" — nếu vậy ảnh landscape MỚI sẽ vô tình
        // thừa hưởng vị trí vừa chỉnh cho ảnh portrait trước đó (đúng lỗi AC muốn tránh).
        repo.applyOrientationPreset(isPortrait = true)
        repo.updateAnchor(Anchor.BOTTOM_LEFT)

        repo.applyOrientationPreset(isPortrait = false)

        assertThat(repo.waterMark.first().anchor).isEqualTo(Anchor.CENTER.ordinal)
    }
}
