package com.mckimquyen.watermark.ui

import android.os.Bundle
import android.view.View
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
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.databinding.ActivityBatchHistoryBinding
import com.mckimquyen.watermark.ui.adapter.BatchHistoryAdapter
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * FEAT-04: màn hình lịch sử batch export — AC1 (danh sách entry sau mỗi lần export), AC2 (xem
 * chi tiết output/lỗi), AC3 (chạy lại đúng config cũ).
 */
@AndroidEntryPoint
class BatchHistoryActivity : BaseActivity() {

    private val binding by inflate<ActivityBatchHistoryBinding>()

    private val viewModel: BatchHistoryViewModel by viewModels()

    private val adapter = BatchHistoryAdapter(
        onView = { showDetailDialog(it) },
        onRerun = { confirmRerun(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        // Edge-to-edge (BaseActivity.applyEdgeToEdge): xem giải thích chi tiết ở
        // WatermarkProfileActivity — bug thật phát hiện lúc smoke test FEAT-06 (nút đáy màn hình bị
        // navigation bar/gesture hệ thống che tap), fix chung ở đây phòng ngừa cùng lớp bug cho
        // item cuối của rvHistory trên màn hình nhỏ.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val navBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            binding.topAppBar.setPadding(0, statusBarTop, 0, 0)
            binding.rvHistory.setPadding(0, binding.rvHistory.paddingTop, 0, navBarBottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        lifecycleScope.launch {
            viewModel.historyFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { list ->
                adapter.submitList(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.rvHistory.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showDetailDialog(entry: BatchHistoryEntity) {
        val success = BatchHistoryRepository.decodeUriList(entry.outputUris)
        val failed = BatchHistoryRepository.decodeUriList(entry.failedInputUris)
        val message = buildString {
            append(getString(R.string.batch_history_detail_success_header, success.size))
            append("\n")
            append(if (success.isEmpty()) getString(R.string.batch_history_detail_none) else success.joinToString("\n") { it.lastPathSegment ?: it.toString() })
            append("\n\n")
            append(getString(R.string.batch_history_detail_failed_header, failed.size))
            append("\n")
            append(if (failed.isEmpty()) getString(R.string.batch_history_detail_none) else failed.joinToString("\n") { it.lastPathSegment ?: it.toString() })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.batch_history_detail_title)
            .setMessage(message)
            .setPositiveButton(R.string.tips_confirm_dialog, null)
            .show()
    }

    private fun confirmRerun(entry: BatchHistoryEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.batch_history_rerun_confirm_title)
            .setMessage(R.string.batch_history_rerun_confirm_message)
            .setPositiveButton(R.string.tips_confirm_dialog) { _, _ ->
                viewModel.rerun(entry) {
                    toast(getString(R.string.batch_history_rerun_done))
                    finish()
                }
            }
            .setNegativeButton(R.string.tips_cancel_dialog, null)
            .show()
    }

    private fun confirmDelete(entry: BatchHistoryEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_exist_confirm)
            .setMessage(R.string.batch_history_delete_confirm_message)
            .setPositiveButton(R.string.tips_confirm_dialog) { _, _ -> viewModel.delete(entry) }
            .setNegativeButton(R.string.tips_cancel_dialog, null)
            .show()
    }
}
