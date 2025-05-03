package com.mckimquyen.watermark.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.mckimquyen.watermark.MyApplication
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.FuncTitleModel
import com.mckimquyen.watermark.ui.base.BaseViewHolder
import com.mckimquyen.watermark.utils.ktx.colorPrimary

class FuncPanelAdapter(
    val dataSet: ArrayList<FuncTitleModel>,
) : RecyclerView.Adapter<FuncPanelAdapter.FuncTitleHolder>() {

    var textColor: Int = MyApplication.instance.applicationContext.colorPrimary
        private set

    var selectedPos = 0
        set(value) {
            notifyItemChanged(/* position = */ field, /* payload = */ "Selected")
            field = value
            notifyItemChanged(/* position = */ value, /* payload = */ "Selected")
        }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FuncTitleHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_func_panel, parent, false)
        return FuncTitleHolder(view)
    }

    override fun onBindViewHolder(
        holder: FuncTitleHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        super.onBindViewHolder(holder, position, payloads)
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        processUI(holder, position)
    }

    override fun onBindViewHolder(holder: FuncTitleHolder, position: Int) {
        processUI(holder, position)
    }

    private fun processUI(holder: FuncTitleHolder, position: Int) {
        if (position < 0 || position >= dataSet.size) {
            return
        }
        with(dataSet[position]) {
            holder.tvTitle.text = title
            holder.ivIcon.setImageResource(iconRes)
            if (position == selectedPos) {
                holder.ivIcon.drawable.setTint(
                    MaterialColors.harmonize(textColor, holder.tvTitle.context.colorPrimary)
                )
                holder.tvTitle.setTextColor(
                    MaterialColors.harmonize(textColor, holder.tvTitle.context.colorPrimary)
                )
            } else {
                holder.ivIcon.drawable.setTint(textColor)
                holder.tvTitle.setTextColor(textColor)
            }
        }
    }

    override fun getItemCount(): Int {
        return dataSet.count()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun seNewData(contentFunList: List<FuncTitleModel>, toPos: Int = selectedPos) {
        selectedPos = toPos
        dataSet.clear()
        dataSet.addAll(contentFunList)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun applyTextColor(color: Int) {
        textColor = color
        notifyDataSetChanged()
    }

    class FuncTitleHolder(view: View) : BaseViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
    }
}
