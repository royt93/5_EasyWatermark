package com.mckimquyen.watermark.data.repo

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-AUDIT-2026-09-29: `updatePalette()` trước đây launch coroutine trên 1
 * `CoroutineScope(Dispatchers.Main)` riêng không bao giờ cancel (vi phạm R5 — resource chưa
 * dispose) chỉ để emit `MutableStateFlow` — đổi thành gán `.value` đồng bộ, bỏ hẳn scope. Test
 * bằng reflection đọc thẳng field `_palette` (nguồn sự thật thật sự thay đổi bởi fix, khác
 * `paletteFlow` — 1 StateFlow PHÁI SINH qua `stateIn(Eagerly)` có độ trễ lan truyền riêng không
 * liên quan gì tới fix này) — chứng minh giá trị mới có NGAY sau lời gọi, không cần
 * `runBlocking`/`delay` nào để "chờ coroutine chạy xong" như code cũ.
 */
@RunWith(RobolectricTestRunner::class)
class MemorySettingRepoTest {

    private fun readPaletteField(repo: MemorySettingRepo): Palette? {
        val field = MemorySettingRepo::class.java.getDeclaredField("_palette")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<Palette?>
        return stateFlow.value
    }

    private fun realPalette(): Palette {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        return Palette.Builder(bitmap).generate()
    }

    @Test
    fun updatePalette_reflectsSynchronously_noSchedulingNeeded() {
        val repo = MemorySettingRepo()
        assertThat(readPaletteField(repo)).isNull()

        val palette = realPalette()
        repo.updatePalette(palette)

        // Không gọi shadowOf(Looper).idle() hay runBlocking nào — nếu fix bị revert (quay lại
        // scope.launch { emit(...) }), giá trị này vẫn null tại đây vì coroutine chưa kịp chạy.
        assertThat(readPaletteField(repo)).isSameInstanceAs(palette)
    }

    @Test
    fun updatePalette_calledWithNull_clearsPalette() {
        val repo = MemorySettingRepo()
        repo.updatePalette(realPalette())

        repo.updatePalette(null)

        assertThat(readPaletteField(repo)).isNull()
    }
}
