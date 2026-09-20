package com.mckimquyen.watermark.ui.dlg

import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.databinding.DlgEditTemplateBinding
import com.mckimquyen.watermark.databinding.DlgEditTextBinding
import com.mckimquyen.watermark.databinding.DlgEditTextTemplateListBinding
import com.mckimquyen.watermark.databinding.ItemTemplateListBinding
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TemplateEcosystemWidgetTest {

    private lateinit var themedContext: ContextThemeWrapper

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        themedContext = ContextThemeWrapper(app, R.style.Theme_MyApp)
    }

    @Test
    fun editTextDialog_hasAccessibleTouchTargets_clearTextEndIcon_andCheckIcon() {
        val inflater = LayoutInflater.from(themedContext)
        val binding = DlgEditTextBinding.inflate(inflater)

        // Template button >= 48dp touch target + content description
        val minTouchPx = (48 * themedContext.resources.displayMetrics.density).toInt()
        assertThat(binding.btnGoTemplate.layoutParams.width).isAtLeast(minTouchPx)
        assertThat(binding.btnGoTemplate.layoutParams.height).isAtLeast(minTouchPx)
        assertThat(binding.btnGoTemplate.contentDescription?.toString())
            .isEqualTo(themedContext.getString(R.string.dialog_title_template_title))

        // TextInputLayout clear_text end icon mode
        assertThat(binding.tlWaterText.endIconMode).isEqualTo(TextInputLayout.END_ICON_CLEAR_TEXT)
        assertThat(binding.tlWaterText.hint).isNotNull()

        // Confirm button has check icon + >= 48dp height
        assertThat(binding.btnConfirm.icon).isNotNull()
        assertThat(binding.btnConfirm.layoutParams.height).isAtLeast(minTouchPx)
    }

    @Test
    fun templateList_and_itemTemplateList_haveAccessibleTouchTargets_andLabels() {
        val inflater = LayoutInflater.from(themedContext)
        val listBinding = DlgEditTextTemplateListBinding.inflate(inflater)
        val itemBinding = ItemTemplateListBinding.inflate(inflater)

        val minTouchPx = (48 * themedContext.resources.displayMetrics.density).toInt()

        // Back button on template list >= 48dp
        assertThat(listBinding.ivBack.layoutParams.width).isAtLeast(minTouchPx)
        assertThat(listBinding.ivBack.layoutParams.height).isAtLeast(minTouchPx)
        assertThat(listBinding.ivBack.contentDescription?.toString())
            .isEqualTo(themedContext.getString(R.string.back))

        // Empty state has proper icon padding and string
        assertThat(listBinding.tvEmpty.text.toString())
            .isEqualTo(themedContext.getString(R.string.tips_list_empty))

        // Item Edit button >= 48dp + action_edit content description
        assertThat(itemBinding.ivEdit.layoutParams.width).isAtLeast(minTouchPx)
        assertThat(itemBinding.ivEdit.layoutParams.height).isAtLeast(minTouchPx)
        assertThat(itemBinding.ivEdit.contentDescription?.toString())
            .isEqualTo(themedContext.getString(R.string.action_edit))

        // Item Delete button >= 48dp + action_delete content description
        assertThat(itemBinding.ivDelete.layoutParams.width).isAtLeast(minTouchPx)
        assertThat(itemBinding.ivDelete.layoutParams.height).isAtLeast(minTouchPx)
        assertThat(itemBinding.ivDelete.contentDescription?.toString())
            .isEqualTo(themedContext.getString(R.string.action_delete))

        // Card is clickable with ripple feedback
        assertThat(itemBinding.root.isClickable).isTrue()
        assertThat(itemBinding.root.isFocusable).isTrue()
    }

    @Test
    fun editTemplateDialog_layoutHasClearTextEndIcon_andCheckIcon() {
        val inflater = LayoutInflater.from(themedContext)
        val binding = DlgEditTemplateBinding.inflate(inflater)

        val minTouchPx = (48 * themedContext.resources.displayMetrics.density).toInt()
        assertThat(binding.tlWaterText.endIconMode).isEqualTo(TextInputLayout.END_ICON_CLEAR_TEXT)
        assertThat(binding.btnConfirm.icon).isNotNull()
        assertThat(binding.btnConfirm.layoutParams.height).isAtLeast(minTouchPx)
    }

    @Test
    fun editTemplateContentFragment_dynamicallyConfiguresCreateVsEditMode() {
        val activityController = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = activityController.create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        // 1. Create mode (no template argument)
        val createFragment = EditTemplateContentFragment()
        createFragment.show(activity.supportFragmentManager, "create_template")
        shadowOf(Looper.getMainLooper()).idle()

        val createTitle = createFragment.view?.findViewById<TextView>(R.id.tvTitle)
        val createConfirm = createFragment.view?.findViewById<Button>(R.id.btnConfirm)
        assertThat(createTitle?.text?.toString())
            .isEqualTo(themedContext.getString(R.string.dialog_button_add_template))
        assertThat(createConfirm?.text?.toString())
            .isEqualTo(themedContext.getString(R.string.dialog_button_add_template))

        createFragment.dismiss()
        shadowOf(Looper.getMainLooper()).idle()

        // 2. Edit mode (with template argument)
        val sampleTemplate = Template(
            id = 42,
            content = "Watermark Sample",
            creationDate = Date(),
            lastModifiedDate = Date()
        )
        val editFragment = EditTemplateContentFragment().apply {
            arguments = Bundle().apply {
                putParcelable("template", sampleTemplate)
            }
        }
        editFragment.show(activity.supportFragmentManager, "edit_template")
        shadowOf(Looper.getMainLooper()).idle()

        val editTitle = editFragment.view?.findViewById<TextView>(R.id.tvTitle)
        val editConfirm = editFragment.view?.findViewById<Button>(R.id.btnConfirm)
        val editInput = editFragment.view?.findViewById<TextInputEditText>(R.id.etWaterText)

        assertThat(editTitle?.text?.toString())
            .isEqualTo(themedContext.getString(R.string.dialog_title_template_edit))
        assertThat(editConfirm?.text?.toString())
            .isEqualTo(themedContext.getString(R.string.tips_ok))
        assertThat(editInput?.text?.toString())
            .isEqualTo("Watermark Sample")

        editFragment.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
