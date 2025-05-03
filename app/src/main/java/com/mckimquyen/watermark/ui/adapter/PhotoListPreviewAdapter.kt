package com.mckimquyen.watermark.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mckimquyen.watermark.MyApplication
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.ui.base.BaseViewHolder
import com.mckimquyen.watermark.ui.widget.PhotoPreviewItem
import com.mckimquyen.watermark.utils.VibrateHelper

class PhotoListPreviewAdapter(
    private val context: Context,
) : RecyclerView.Adapter<PhotoListPreviewAdapter.ImageHolder>() {

    val data: List<ImageInfo>
        get() = differ.currentList

    private val differ: AsyncListDiffer<ImageInfo> by lazy {
        AsyncListDiffer(this, differCallback)
    }

    private val differCallback: DiffUtil.ItemCallback<ImageInfo> by lazy {
        object : DiffUtil.ItemCallback<ImageInfo>() {
            override fun areItemsTheSame(oldItem: ImageInfo, newItem: ImageInfo): Boolean {
                return oldItem.isSameItem(newItem)
            }

            override fun areContentsTheSame(oldItem: ImageInfo, newItem: ImageInfo): Boolean {
                return oldItem.isSameItem(newItem)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder {
        val rootView = PhotoPreviewItem(context)
        return ImageHolder(rootView)
    }

    override fun onBindViewHolder(
        holder: ImageHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        super.onBindViewHolder(holder, position, payloads)
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        processUI(holder, position, isPayLoad = true)
    }

    override fun onBindViewHolder(holder: ImageHolder, position: Int) {
        processUI(holder = holder, position = position)
    }

    private fun processUI(holder: ImageHolder, position: Int, isPayLoad: Boolean = false) {
        if (position < 0 || position >= differ.currentList.size) {
            return
        }
        (holder.itemView as PhotoPreviewItem).apply {
            onRemove {
                VibrateHelper.get().doVibrate(holder.itemView)
                remove(holder.bindingAdapterPosition)
            }
            onRemovePreview {
                VibrateHelper.get().doVibrate(holder.itemView)
            }
        }
        with(differ.currentList[position]) {
            Glide.with(MyApplication.instance)
                .load(this.uri)
                .into(holder.ivIcon)
            when {
                isPayLoad -> {
                    holder.ivIcon.animate().alpha(1f).setDuration(100).start()
                    holder.ivRemove
                        .animate()
                        .alpha(0f)
                        .setDuration(100)
                        .withEndAction { holder.ivRemove.isVisible = false }
                        .start()
                }

                else -> {
                    holder.ivIcon.apply {
                        isVisible = true
                        alpha = 1f
                    }
                    holder.ivRemove.apply {
                        isVisible = false
                        alpha = 0f
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.count()
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun submitList(imageInfoList: List<ImageInfo>, block: () -> Unit) {
        differ.submitList(imageInfoList) {
            block.invoke()
        }
    }

    fun getItem(pos: Int): ImageInfo? {
        return differ.currentList.getOrNull(pos)
    }

    var selectedPos: Int = 0

    private var doOnRemoveItem: (ImageInfo?) -> Unit = {}

    fun onRemove(block: (ImageInfo?) -> Unit = {}) {
        this.doOnRemoveItem = block
    }

    private fun remove(removePos: Int) {
        if (removePos < 0 || removePos >= itemCount || itemCount <= 1) {
            return
        }
        doOnRemoveItem.invoke(getItem(removePos))
    }

    class ImageHolder(view: PhotoPreviewItem) : BaseViewHolder(view) {
        val ivIcon: ImageView = view.ivIcon
        val ivRemove: ImageView = view.ivDel.apply {
            this.isVisible = false
        }
    }
}
