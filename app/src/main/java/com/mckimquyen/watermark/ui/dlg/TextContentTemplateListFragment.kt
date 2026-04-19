package com.mckimquyen.watermark.ui.dlg

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.DlgEditTextTemplateListBinding
import com.mckimquyen.watermark.ui.UiState
import com.mckimquyen.watermark.ui.adapter.TextContentTemplateListAdapter
import com.mckimquyen.watermark.ui.base.BaseBindFragment
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import kotlinx.coroutines.launch

class TextContentTemplateListFragment : BaseBindFragment<DlgEditTextTemplateListBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater, container: ViewGroup?,
    ): DlgEditTextTemplateListBinding {
        return DlgEditTextTemplateListBinding.inflate(
            /* inflater = */ layoutInflater,
            /* parent = */ container,
            /* attachToParent = */ false
        )
    }

    private val listAdapter by lazy {
        TextContentTemplateListAdapter {
            setOnEditListener { template, _ ->
                EditTemplateContentFragment.safetyShow(childFragmentManager, template)
            }

            setOnClickListener { template, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dialog_title_exist_confirm))
                    .setMessage(getString(R.string.tips_use_this_template))
                    .setPositiveButton(getString(R.string.tips_confirm_dialog)) { _, _ ->
                        shareViewModel.useTemplate(template)
                    }
                    .setNegativeButton(getString(R.string.tips_cancel_dialog)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            setOnRemoveListener { template, pos ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dialog_title_exist_confirm))
                    .setMessage(getString(R.string.tips_delete_template))
                    .setPositiveButton(getString(R.string.tips_confirm_dialog)) { _, _ ->
                        shareViewModel.deleteTemplate(template)
                    }
                    .setNegativeButton(getString(R.string.tips_cancel_dialog)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.ivBack?.setOnClickListener {
            shareViewModel.goTemplateEdit()
        }
        binding?.rvTemplate?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = listAdapter
        }
        binding?.btnAdd?.setOnClickListener {
            EditTemplateContentFragment.safetyShow(childFragmentManager)
        }

        lifecycleScope.launch {
            shareViewModel.templateListFlow.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ).collect {
                listAdapter.submitList(it)
                binding?.tvEmpty?.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            shareViewModel.uiStateFlow.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ).collect {
                if (it is UiState.DatabaseError) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tips_database_init_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    companion object {
        const val TAG = "TextContentTemplateListFragment"

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    containerId, TextContentTemplateListFragment(), TAG
                )
            }
        }
    }

}
