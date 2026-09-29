package com.mckimquyen.watermark.data.repo

import androidx.palette.graphics.Palette
import com.mckimquyen.watermark.MyApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * hold memory data for across business usage
 */
@Singleton
class MemorySettingRepo @Inject constructor() {

    /**
     * The color palette extracted from the picture, used to modify the overall theme color
     */
    private val _palette: MutableStateFlow<Palette?> = MutableStateFlow(null)

    val paletteFlow = _palette.stateIn(MyApplication.applicationScope, SharingStarted.Eagerly, null)

    /**
     * BUG-AUDIT-2026-09-29: trước đây launch 1 coroutine trên `CoroutineScope(Dispatchers.Main)`
     * riêng (không bao giờ cancel — vi phạm R5) chỉ để emit `MutableStateFlow` — gán `.value` trực
     * tiếp cho cùng hiệu quả, không cần coroutine/scope nào cả.
     */
    fun updatePalette(palette: Palette?) {
        _palette.value = palette
    }
}
