package com.mckimquyen.watermark.ui.dlg

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.databinding.DlgEditTemplateBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import java.util.Date

class EditTemplateContentFragment : BaseBindBSDFragment<DlgEditTemplateBinding>() {

    private var template: Template? = null

    private var isEdit = false

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?,
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
        binding.tvTitle.text = if (isEdit) getString(R.string.dialog_title_template_edit) else getString(R.string.dialog_button_add_template)
        binding.etWaterText.apply {
            setText(template?.content)
            post {
                setSelection(text?.length ?: 0)
                requestFocus()
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        binding.btnConfirm.apply {
            setOnClickListener {
                val msg = binding.etWaterText.text.toString().trim()
                if (msg.isBlank()) {
                    Toast.makeText(requireContext(), R.string.tips_input_text_can_not_be_empty, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (isEdit) {
                    val t = template?.copy(content = msg, lastModifiedDate = Date()) ?: kotlin.run {
                        Toast.makeText(requireContext(), R.string.tips_error, Toast.LENGTH_LONG).show()
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


    companion object {
        const val TAG = "EditTemplateContentFragment"

        fun safetyShow(manager: FragmentManager, template: Template? = null) {
            try {
                val f = (manager.findFragmentByTag(TAG) as? SaveImageBSDialogFragment)
                    ?: EditTemplateContentFragment()
                f.apply {
                    arguments = Bundle().also {
                        it.putParcelable("template", template)
                    }
                }
                if (!f.isAdded) {
                    f.show(manager, TAG)
                }
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
