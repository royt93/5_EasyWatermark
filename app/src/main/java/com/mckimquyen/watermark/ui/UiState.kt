package com.mckimquyen.watermark.ui

import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.entity.Template

sealed class UiState {
    class UseTemplate(val template: Template) : UiState()
    object GoTemplate : UiState()
    object GoEdit : UiState()
    object GoEditDialog : UiState()
    object None : UiState()
    object DatabaseError : UiState()
    class ApplyAnchor(val anchor: Anchor, val marginPercent: Float) : UiState()
}
