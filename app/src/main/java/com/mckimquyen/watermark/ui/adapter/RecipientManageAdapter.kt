package com.mckimquyen.watermark.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.databinding.ItemRecipientManageBinding

/**
 * IDEA-10: Adapter hiển thị danh sách người nhận trong màn hình quản lý.
 */
class RecipientManageAdapter(
    private val onEdit: (Recipient) -> Unit,
    private val onDelete: (Recipient) -> Unit
) : ListAdapter<Recipient, RecipientManageAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipientManageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onEdit, onDelete)
    }

    class ViewHolder(private val binding: ItemRecipientManageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: Recipient,
            onEdit: (Recipient) -> Unit,
            onDelete: (Recipient) -> Unit
        ) {
            binding.tvName.text = item.name
            binding.tvCode.text = "Mã: ${item.code}"
            if (!item.notes.isNullOrBlank()) {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = item.notes
            } else {
                binding.tvNotes.visibility = View.GONE
            }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Recipient>() {
            override fun areItemsTheSame(oldItem: Recipient, newItem: Recipient): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Recipient, newItem: Recipient): Boolean =
                oldItem == newItem
        }
    }
}
