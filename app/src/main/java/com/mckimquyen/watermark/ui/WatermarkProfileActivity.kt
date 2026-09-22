package com.mckimquyen.watermark.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import com.mckimquyen.watermark.databinding.ActivityWatermarkProfileBinding
import com.mckimquyen.watermark.ui.adapter.WatermarkProfileAdapter
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * FEAT-06: màn hình quản lý "hồ sơ" watermark — AC1 (lưu cấu hình hiện tại thành profile đặt
 * tên), AC2 (áp dụng lại đúng toàn bộ cấu hình đã lưu), AC3 (danh sách dễ chọn).
 */
@AndroidEntryPoint
class WatermarkProfileActivity : BaseActivity() {

    private val binding by inflate<ActivityWatermarkProfileBinding>()

    private val viewModel: WatermarkProfileViewModel by viewModels()

    private val adapter = WatermarkProfileAdapter(
        onApply = { confirmApply(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = adapter
        binding.btnSaveCurrent.setOnClickListener { showSaveNameDialog() }

        // Edge-to-edge (BaseActivity.applyEdgeToEdge): content vẽ xuyên qua status/navigation bar
        // — không padding thì `btnSaveCurrent` (anchor đáy màn hình) bị navigation bar/vùng gesture
        // hệ thống che 1 phần, tap không tới được (phát hiện qua smoke test thật, không phải bug lý
        // thuyết). Cùng pattern `AboutActivity`.
        val bottomExtraPadding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val navBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            binding.topAppBar.setPadding(0, statusBarTop, 0, 0)
            (binding.btnSaveCurrent.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottomExtraPadding + navBarBottom
            binding.rvProfiles.setPadding(0, binding.rvProfiles.paddingTop, 0, navBarBottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        lifecycleScope.launch {
            viewModel.profilesFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { list ->
                adapter.submitList(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.rvProfiles.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showSaveNameDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.watermark_profile_name_hint)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.watermark_profile_action_save_current)
            .setView(input)
            .setPositiveButton(R.string.tips_confirm_dialog) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.saveCurrentAsProfile(name) {
                        toast(getString(R.string.watermark_profile_saved))
                    }
                }
            }
            .setNegativeButton(R.string.tips_cancel_dialog, null)
            .create()
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
        input.doOnTextChanged { text, _, _, _ ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = !text.isNullOrBlank()
        }
    }

    private fun confirmApply(entity: WatermarkProfileEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.watermark_profile_apply_confirm_title)
            .setMessage(getString(R.string.watermark_profile_apply_confirm_message, entity.name))
            .setPositiveButton(R.string.tips_confirm_dialog) { _, _ ->
                viewModel.apply(entity) {
                    toast(getString(R.string.watermark_profile_applied))
                    finish()
                }
            }
            .setNegativeButton(R.string.tips_cancel_dialog, null)
            .show()
    }

    private fun confirmDelete(entity: WatermarkProfileEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_exist_confirm)
            .setMessage(R.string.watermark_profile_delete_confirm_message)
            .setPositiveButton(R.string.tips_confirm_dialog) { _, _ -> viewModel.delete(entity) }
            .setNegativeButton(R.string.tips_cancel_dialog, null)
            .show()
    }
}
