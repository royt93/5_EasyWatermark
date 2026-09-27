package com.mckimquyen.watermark.ui.recipient

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.databinding.ActivityRecipientManagementBinding
import com.mckimquyen.watermark.databinding.DlgEditRecipientBinding
import com.mckimquyen.watermark.ui.adapter.RecipientManageAdapter
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * IDEA-10: màn hình CRUD quản lý danh sách Người nhận (AC1).
 */
@AndroidEntryPoint
class RecipientManagementActivity : BaseActivity() {

    private val binding by inflate<ActivityRecipientManagementBinding>()

    private val viewModel: RecipientViewModel by viewModels()

    private val adapter = RecipientManageAdapter(
        onEdit = { showEditDialog(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.rvRecipients.layoutManager = LinearLayoutManager(this)
        binding.rvRecipients.adapter = adapter
        binding.btnAddRecipient.setOnClickListener { showEditDialog(null) }

        // Edge-to-edge: nút đáy màn hình dễ bị navigation bar che tap nếu thiếu inset padding
        // (bài học FEAT-06/BatchHistoryActivity).
        val bottomExtraPadding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val navBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            binding.topAppBar.setPadding(0, statusBarTop, 0, 0)
            (binding.btnAddRecipient.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                bottomExtraPadding + navBarBottom
            binding.rvRecipients.setPadding(
                binding.rvRecipients.paddingLeft,
                binding.rvRecipients.paddingTop,
                binding.rvRecipients.paddingRight,
                navBarBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        lifecycleScope.launch {
            viewModel.recipients.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { list ->
                adapter.submitList(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.rvRecipients.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    /** [existing] null = thêm mới, khác null = sửa. */
    private fun showEditDialog(existing: Recipient?) {
        val dialogBinding = DlgEditRecipientBinding.inflate(layoutInflater)
        dialogBinding.etName.setText(existing?.name.orEmpty())
        dialogBinding.etCode.setText(existing?.code ?: generateCode())
        dialogBinding.etNotes.setText(existing?.notes.orEmpty())

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.recipient_add else R.string.recipient_edit)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.tips_confirm_dialog) { _, _ ->
                val name = dialogBinding.etName.text?.toString()?.trim().orEmpty()
                val code = dialogBinding.etCode.text?.toString()?.trim().orEmpty()
                val notes = dialogBinding.etNotes.text?.toString()?.trim()

                if (name.isEmpty()) {
                    toast(getString(R.string.recipient_name_error_empty))
                    return@setPositiveButton
                }
                if (code.isEmpty()) {
                    toast(getString(R.string.recipient_code_error_empty))
                    return@setPositiveButton
                }

                val recipient = Recipient(
                    id = existing?.id ?: 0L,
                    name = name,
                    code = code,
                    notes = notes?.takeIf { it.isNotBlank() },
                    timestamp = existing?.timestamp ?: System.currentTimeMillis()
                )
                viewModel.save(recipient) { success ->
                    if (!success) toast(getString(R.string.recipient_code_error_duplicate))
                }
            }
            .setNegativeButton(R.string.tips_cancel_dialog, null)
            .show()
    }

    private fun confirmDelete(recipient: Recipient) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_exist_confirm)
            .setMessage(getString(R.string.recipient_delete_confirm, recipient.name))
            .setPositiveButton(R.string.tips_confirm_dialog) { _, _ -> viewModel.delete(recipient) }
            .setNegativeButton(R.string.tips_cancel_dialog, null)
            .show()
    }

    companion object {
        /** Độ dài mã gợi ý mặc định — đủ ngắn để gõ tay, đủ dài để không trùng ngẫu nhiên. */
        private const val GENERATED_CODE_LENGTH = 6

        internal fun generateCode(): String =
            UUID.randomUUID().toString().replace("-", "").take(GENERATED_CODE_LENGTH).uppercase()
    }
}
