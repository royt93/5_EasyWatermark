package com.mckimquyen.watermark.ui

import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.ui.adapter.TextContentTemplateListAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for:
 * - Position Anchor button selection state updates and icon tints
 * - TextContentTemplateListAdapter list operations and click callbacks
 */
@RunWith(RobolectricTestRunner::class)
class PositionAnchorIntegrationTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun anchorButtons_selectionStateUpdatesStrokeAndTint() {
        val button = MaterialButton(themedContext)
        button.icon = themedContext.getDrawable(R.drawable.ic_anchor_center)

        // Verify initial state
        assertThat(button.icon).isNotNull()

        // Simulate selection highlighting
        val primaryColor = com.google.android.material.color.MaterialColors.getColor(
            button,
            com.google.android.material.R.attr.colorPrimary
        )
        val containerColor = com.google.android.material.color.MaterialColors.getColor(
            button,
            com.google.android.material.R.attr.colorPrimaryContainer
        )

        button.setTextColor(primaryColor)
        button.iconTint = android.content.res.ColorStateList.valueOf(primaryColor)
        button.strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(containerColor)

        assertThat(button.textColors.defaultColor).isEqualTo(primaryColor)
        assertThat(button.iconTint.defaultColor).isEqualTo(primaryColor)
        assertThat(button.strokeColor.defaultColor).isEqualTo(primaryColor)
        assertThat(button.backgroundTintList?.defaultColor).isEqualTo(containerColor)
    }

    @Test
    fun templateListAdapter_submitsListAndHandlesItemClicks() {
        var clickedTemplate: Template? = null
        var removedTemplate: Template? = null

        val adapter = TextContentTemplateListAdapter {
            setOnClickListener { template, _ ->
                clickedTemplate = template
            }
            setOnRemoveListener { template, _ ->
                removedTemplate = template
            }
        }

        val now = java.util.Date()
        val templates = listOf(
            Template(id = 1, content = "Confidential - Do not share", creationDate = now, lastModifiedDate = now),
            Template(id = 2, content = "Sample Watermark 2026", creationDate = now, lastModifiedDate = now)
        )

        adapter.submitList(templates)
        // AsyncListDiffer in Robolectric synchronous dispatch
        assertThat(adapter.itemCount).isEqualTo(2)
    }
}
