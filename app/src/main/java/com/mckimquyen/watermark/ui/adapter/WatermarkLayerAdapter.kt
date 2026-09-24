package com.mckimquyen.watermark.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.databinding.ItemWatermarkLayerBinding

/**
 * FEAT-03: danh sách layer PHỤ trong [com.mckimquyen.watermark.ui.dlg.LayerManagerBSDFragment].
 * Tối đa [WaterMarkRepository.MAX_EXTRA_LAYERS] phần tử — không cần `DiffUtil`/`AsyncListDiffer`
 * (danh sách quá nhỏ để cần tối ưu, `notifyDataSetChanged()` đơn giản và đủ nhanh).
 */
class WatermarkLayerAdapter(
    private val onTap: (index: Int) -> Unit,
    private val onMoveUp: (index: Int) -> Unit,
    private val onMoveDown: (index: Int) -> Unit,
    private val onDelete: (index: Int) -> Unit
) : RecyclerView.Adapter<WatermarkLayerAdapter.ViewHolder>() {

    private var items: List<WatermarkLayer> = emptyList()

    fun submitList(list: List<WatermarkLayer>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemWatermarkLayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val layer = items.getOrNull(position) ?: return
        holder.binding.tvLayerSummary.text = summaryOf(holder.binding.root.context, layer)
        holder.binding.root.setOnClickListener { onTap(position) }
        holder.binding.btnMoveUp.isEnabled = position > 0
        holder.binding.btnMoveUp.alpha = if (position > 0) 1f else 0.4f
        holder.binding.btnMoveUp.setOnClickListener { onMoveUp(position) }
        holder.binding.btnMoveDown.isEnabled = position < items.size - 1
        holder.binding.btnMoveDown.alpha = if (position < items.size - 1) 1f else 0.4f
        holder.binding.btnMoveDown.setOnClickListener { onMoveDown(position) }
        holder.binding.btnDelete.setOnClickListener { onDelete(position) }
    }

    private fun summaryOf(context: android.content.Context, layer: WatermarkLayer): String = when (layer.markMode) {
        WaterMarkRepository.MarkMode.Text ->
            "${context.getString(com.mckimquyen.watermark.R.string.water_mark_mode_text)} · ${layer.text.ifBlank { "…" }}"
        WaterMarkRepository.MarkMode.Image ->
            "${context.getString(com.mckimquyen.watermark.R.string.water_mark_mode_image)} · ${layer.iconUri.lastPathSegment ?: layer.iconUri.toString()}"
    }

    class ViewHolder(val binding: ItemWatermarkLayerBinding) : RecyclerView.ViewHolder(binding.root)
}
