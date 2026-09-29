package com.mckimquyen.watermark.ui.dlg

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.databinding.DlgEditTemplateBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.ktx.toast
import java.util.Date

class EditTemplateContentFragment : BaseBindBSDFragment<DlgEditTemplateBinding>() {

    private var template: Template? = null

    private var isEdit = false

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): DlgEditTemplateBinding {
        return DlgEditTemplateBinding.inflate(layoutInflater, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        template = arguments?.getParcelable("template") as? Template
        isEdit = template != null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindTemplateToViews()
        binding.btnConfirm.apply {
            setOnClickListener {
                val msg = binding.etWaterText.text.toString().trim()
                if (msg.isBlank()) {
                    toast(R.string.tips_input_text_can_not_be_empty, long = true)
                    return@setOnClickListener
                }
                if (isEdit) {
                    val t = template?.copy(content = msg, lastModifiedDate = Date()) ?: kotlin.run {
                        toast(R.string.tips_error, long = true)
                        dismissAllowingStateLoss()
                        return@setOnClickListener
                    }
                    shareViewModel.updateTemplate(t)
                } else {
                    shareViewModel.addTemplate(msg)
                }
                dismissAllowingStateLoss()
            }
        }
    }

    /**
     * BUG-AUDIT-2026-09-29: tách riêng để [updateTemplateForReuse] gọi lại được khi dialog ĐANG mở
     * (double-tap 2 template khác nhau liên tiếp) — set lại `arguments` trên fragment instance đã
     * `isAdded` ném `IllegalStateException("Fragment already active")` (trước đây bị nuốt âm thầm,
     * dialog giữ nguyên nội dung CŨ). `onCreate()` cũng chỉ chạy 1 lần lúc tạo fragment nên đổi
     * `arguments` sau đó không refresh được field `template`/`isEdit` dù có set thành công.
     */
    private fun bindTemplateToViews() {
        val titleText = if (isEdit) getString(R.string.dialog_title_template_edit) else getString(R.string.dialog_button_add_template)
        binding.tvTitle.text = titleText
        binding.tlWaterText.hint = titleText
        binding.btnConfirm.text = if (isEdit) getString(R.string.tips_ok) else getString(R.string.dialog_button_add_template)
        binding.btnConfirm.setIconResource(if (isEdit) R.drawable.ic_check else R.drawable.ic_add)
        binding.etWaterText.apply {
            setText(template?.content)
            post {
                setSelection(text?.length ?: 0)
                requestFocus()
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /** Gọi từ [safetyShow] khi dialog đang mở sẵn — cập nhật field + UI trực tiếp thay vì đi qua
     *  `arguments`/`onCreate()` (không dùng lại được trên instance đã active). */
    internal fun updateTemplateForReuse(newTemplate: Template?) {
        template = newTemplate
        isEdit = newTemplate != null
        if (view != null) bindTemplateToViews()
    }

    companion object {
        const val TAG = "EditTemplateContentFragment"

        fun safetyShow(manager: FragmentManager, template: Template? = null) {
            try {
                val existing = manager.findFragmentByTag(TAG) as? EditTemplateContentFragment
                if (existing != null && existing.isAdded) {
                    existing.updateTemplateForReuse(template)
                    return
                }
                val f = existing ?: EditTemplateContentFragment()
                f.arguments = Bundle().also {
                    it.putParcelable("template", template)
                }
                f.show(manager, TAG)
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
