package com.mckimquyen.watermark.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.databinding.ItemDualPresetBinding

/**
 * FEAT-26: Adapter hiển thị danh sách các mẫu dấu kép (Dual Presets).
 */
class DualPresetAdapter(
    private val presets: List<DualWatermarkPreset> = DualWatermarkPreset.entries,
    private val onSelect: (preset: DualWatermarkPreset) -> Unit
) : RecyclerView.Adapter<DualPresetAdapter.ViewHolder>() {

    override fun getItemCount(): Int = presets.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDualPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = presets.getOrNull(position) ?: return
        val context = holder.binding.root.context

        holder.binding.tvPresetTitle.text = context.getString(item.titleRes)
        holder.binding.tvPresetDesc.text = context.getString(item.descRes)
        holder.binding.btnPresetIcon.setIconResource(item.iconRes)

        holder.binding.root.setOnClickListener { onSelect(item) }
    }

    class ViewHolder(val binding: ItemDualPresetBinding) : RecyclerView.ViewHolder(binding.root)
}
