package com.mckimquyen.watermark.data.repo

import androidx.palette.graphics.Palette
import com.mckimquyen.watermark.MyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * hold memory data for across business usage
 */
@Singleton
class MemorySettingRepo @Inject constructor() {
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * The color palette extracted from the picture, used to modify the overall theme color
     */
    private val _palette: MutableStateFlow<Palette?> = MutableStateFlow(null)

    val paletteFlow = _palette.stateIn(MyApplication.applicationScope, SharingStarted.Eagerly, null)

    fun updatePalette(palette: Palette?) {
        scope.launch {
            _palette.emit(palette)
        }
    }
}
