package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.ui.base.BaseViewHolder
import com.mckimquyen.watermark.utils.ktx.colorPrimary

class TextPaintStyleAdapter(
    private val dataList: ArrayList<TextPaintStyleModel>,
    initPaintStyle: TextPaintStyle? = TextPaintStyle.Fill,
    private val onClickAction: (pos: Int, paintStyle: TextPaintStyle) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedPos: Int

    init {
        selectedPos = dataList.indexOfFirst { it.paintStyle == initPaintStyle }.coerceAtLeast(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val root = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_typeface_style, parent, false)

        return TypefaceHolder(
            root
        )
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        super.onBindViewHolder(holder, position, payloads)
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        handleView(holder, position)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        handleView(holder, position)
    }

    private fun handleView(holder: RecyclerView.ViewHolder, position: Int) {
        val model = dataList[position]
        val selected = position == selectedPos
        with(holder as TypefaceHolder) {
            tvPreview.apply {
                model.paintStyle.applyStyle(this)
            }
            tvTitle?.text = model.title
            root.setBackgroundResource(
                if (selected) R.drawable.bg_glass_button_checked else R.drawable.bg_glass_button
            )
            tvPreview.setTextColor(
                if (selected) {
                    MaterialColors.getColor(
                        tvPreview.context,
                        com.google.android.material.R.attr.colorOnPrimaryContainer,
                        tvPreview.context.colorPrimary
                    )
                } else {
                    MaterialColors.getColor(
                        tvPreview.context,
                        com.google.android.material.R.attr.colorOnSurface,
                        Color.BLACK
                    )
                }
            )
            val stateText = root.context.getString(
                if (selected) R.string.state_enabled else R.string.state_disabled
            )
            root.contentDescription = "${model.title}, $stateText"
            root.setOnClickListener {
                onClickAction.invoke(position, model.paintStyle)
                updateSelected(position)
            }
        }
    }

    private fun updateSelected(pos: Int) {
        if (pos == selectedPos) {
            return
        }
        notifyItemChanged(selectedPos, "Selected")
        selectedPos = pos
        notifyItemChanged(selectedPos, "Selected")
    }

    internal class TypefaceHolder(val root: View) : BaseViewHolder(root) {
        val tvPreview: TextView by lazy { root.findViewById(R.id.tvPreview) }
        val tvTitle: TextView? by lazy { root.findViewById(R.id.tvTitle) }
    }

    data class TextPaintStyleModel(
        val paintStyle: TextPaintStyle = TextPaintStyle.Fill,
        val title: String
    )

    companion object {
        fun obtainDefaultPaintStyleList(context: Context): ArrayList<TextPaintStyleModel> {
            return arrayListOf(
                TextPaintStyleModel(
                    paintStyle = TextPaintStyle.Fill,
                    title = context.getString(R.string.text_paint_fill)
                ),
                TextPaintStyleModel(
                    paintStyle = TextPaintStyle.Stroke,
                    title = context.getString(R.string.text_paint_stroke)
                )
            )
        }
    }
}
