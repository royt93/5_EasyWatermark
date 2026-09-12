package com.mckimquyen.watermark.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.base.BaseViewHolder
import com.mckimquyen.watermark.utils.ktx.colorPrimary

/**
 * FEAT-11: 3 chip toggle độc lập (viền/bóng/nền pill) cho text watermark — KHÁC
 * [TextPaintStyleAdapter] ở chỗ không phải chọn 1-trong-N, mỗi chip bật/tắt riêng, kết hợp tự do.
 */
class TextEffectAdapter(
    private val dataList: List<TextEffectModel>,
    private val onToggle: (pos: Int, enabled: Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<TextEffectAdapter.EffectHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.item_text_effect, parent, false)
        return EffectHolder(root)
    }

    override fun getItemCount(): Int = dataList.size

    override fun onBindViewHolder(holder: EffectHolder, position: Int) {
        val model = dataList[position]
        holder.tvLabel.text = model.label
        applyCheckedState(holder, model.enabled)
        holder.root.setOnClickListener {
            val newEnabled = !dataList[position].enabled
            dataList[position].enabled = newEnabled
            applyCheckedState(holder, newEnabled)
            onToggle(position, newEnabled)
        }
    }

    private fun applyCheckedState(holder: EffectHolder, enabled: Boolean) {
        holder.root.setBackgroundResource(
            if (enabled) R.drawable.bg_glass_button_checked else R.drawable.bg_glass_button
        )
        holder.tvLabel.setTextColor(
            if (enabled) holder.tvLabel.context.colorPrimary else ContextCompat.getColor(holder.tvLabel.context, R.color.glass_text_primary)
        )
    }

    class EffectHolder(val root: View) : BaseViewHolder(root) {
        val tvLabel: TextView = root.findViewById(R.id.tvEffectLabel)
    }

    data class TextEffectModel(val label: String, var enabled: Boolean)
}
