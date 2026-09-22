package com.mckimquyen.watermark.ui.adapter

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import com.mckimquyen.watermark.databinding.ItemWatermarkProfileBinding

/** FEAT-06: hiển thị danh sách profile, mới nhất trước (DAO đã `ORDER BY createdAt DESC`). */
class WatermarkProfileAdapter(
    private val onApply: (WatermarkProfileEntity) -> Unit,
    private val onDelete: (WatermarkProfileEntity) -> Unit
) : RecyclerView.Adapter<WatermarkProfileAdapter.ViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<WatermarkProfileEntity>() {
        override fun areItemsTheSame(oldItem: WatermarkProfileEntity, newItem: WatermarkProfileEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WatermarkProfileEntity, newItem: WatermarkProfileEntity) = oldItem == newItem
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<WatermarkProfileEntity>) = differ.submitList(list)

    override fun getItemCount() = differ.currentList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemWatermarkProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = differ.currentList.getOrNull(position) ?: return
        holder.binding.tvName.text = item.name
        holder.binding.tvCreatedAt.text = DateFormat.format("yyyy-MM-dd HH:mm", item.createdAt)
        holder.binding.btnApply.setOnClickListener { onApply(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    class ViewHolder(val binding: ItemWatermarkProfileBinding) : RecyclerView.ViewHolder(binding.root)
}
