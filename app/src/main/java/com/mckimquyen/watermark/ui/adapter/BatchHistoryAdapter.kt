package com.mckimquyen.watermark.ui.adapter

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.databinding.ItemBatchHistoryBinding

/**
 * FEAT-04: hiển thị danh sách lịch sử batch export, mới nhất trước (DAO đã `ORDER BY timestamp
 * DESC`, adapter không tự sort lại).
 */
class BatchHistoryAdapter(
    private val onView: (BatchHistoryEntity) -> Unit,
    private val onRerun: (BatchHistoryEntity) -> Unit,
    private val onDelete: (BatchHistoryEntity) -> Unit
) : RecyclerView.Adapter<BatchHistoryAdapter.ViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<BatchHistoryEntity>() {
        override fun areItemsTheSame(oldItem: BatchHistoryEntity, newItem: BatchHistoryEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BatchHistoryEntity, newItem: BatchHistoryEntity) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<BatchHistoryEntity>) = differ.submitList(list)

    override fun getItemCount() = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemBatchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = differ.currentList.getOrNull(position) ?: return
        val context = holder.itemView.context
        val successCount = countUris(item.outputUris)
        val failedCount = countUris(item.failedInputUris)
        holder.binding.tvTimestamp.text = DateFormat.format("yyyy-MM-dd HH:mm", item.timestamp)
        holder.binding.tvSummary.text = context.getString(R.string.batch_history_summary, successCount, failedCount)
        holder.binding.btnView.setOnClickListener { onView(item) }
        holder.binding.btnRerun.setOnClickListener { onRerun(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    class ViewHolder(val binding: ItemBatchHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        internal fun countUris(raw: String): Int = if (raw.isBlank()) 0 else raw.split("\n").count { it.isNotBlank() }
    }
}
