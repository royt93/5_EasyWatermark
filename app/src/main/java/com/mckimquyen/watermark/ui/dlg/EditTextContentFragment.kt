package com.mckimquyen.watermark.ui.dlg

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.databinding.DlgEditTextBinding
import com.mckimquyen.watermark.ui.UiState
import com.mckimquyen.watermark.ui.base.BaseBindFragment
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import kotlinx.coroutines.launch

class EditTextContentFragment : BaseBindFragment<DlgEditTextBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?,
    ): DlgEditTextBinding {
        return DlgEditTextBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.etWaterText?.apply {
            setText(shareViewModel.waterMark.value?.text.toString())
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    shareViewModel.updateText(s?.toString() ?: "")
                }
            })

            post {
                setSelection(text?.length ?: 0)
                requestFocus()
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        binding?.btnConfirm?.apply {
            setOnClickListener {
                shareViewModel.updateText(binding?.etWaterText?.text?.toString() ?: "")
                (requireParentFragment() as? DialogFragment)?.dismiss()
            }
        }

        binding?.btnGoTemplate?.apply {
            setOnClickListener {
                shareViewModel.goTemplate()
            }
        }

        lifecycleScope.launch {
            shareViewModel.uiStateFlow.flowWithLifecycle(
                this@EditTextContentFragment.lifecycle,
                Lifecycle.State.STARTED
            )
                .collect {
                    if (it !is UiState.UseTemplate) {
                        return@collect
                    }
                    val template = it.template
                    binding?.etWaterText?.setText(template.content)
                }
        }
    }


    companion object {
        const val TAG = "TextContentFragment"

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    containerId,
                    EditTextContentFragment(),
                    TAG
                )
            }
        }
    }

}