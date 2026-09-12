package com.mckimquyen.watermark.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.ui.base.BaseViewHolder
import com.mckimquyen.watermark.ui.widget.ProgressImageView
import com.mckimquyen.watermark.utils.ktx.appear
import com.mckimquyen.watermark.utils.ktx.disappear

class SaveImageListAdapter(
    private val context: Context,
) : RecyclerView.Adapter<SaveImageListAdapter.ImageHolder>() {

    val data: List<ImageInfo>
        get() = differ.currentList

    private var maxLineHeight = 0

    private val differ: AsyncListDiffer<ImageInfo> by lazy {
        AsyncListDiffer(this, differCallback)
    }

    private val differCallback: DiffUtil.ItemCallback<ImageInfo> by lazy {
        object : DiffUtil.ItemCallback<ImageInfo>() {
            override fun areItemsTheSame(oldItem: ImageInfo, newItem: ImageInfo): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: ImageInfo, newItem: ImageInfo): Boolean {
                return oldItem.jobState == newItem.jobState
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageHolder {
        val rootView = LayoutInflater.from(context).inflate(R.layout.item_saving_image, parent, false)

        val holder = ImageHolder(rootView)
        (rootView as ConstraintLayout).apply {
            val h = (parent.height - parent.paddingTop - parent.paddingBottom)
            maxLineHeight = if (itemCount >= 5) h / 2 else h
            maxHeight = maxLineHeight
        }
        (holder.ivIcon).apply {
            updateLayoutParams {
                height = maxLineHeight
            }
        }
        return holder
    }

    override fun onBindViewHolder(
        holder: ImageHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        processUI(holder, position, isPayLoad = payloads.isNotEmpty())
    }

    override fun onBindViewHolder(holder: ImageHolder, position: Int) {
        processUI(holder, position)
    }

    private fun processUI(holder: ImageHolder, position: Int, isPayLoad: Boolean = false) {
        if (position < 0 || position >= differ.currentList.size) {
            return
        }
        with(differ.currentList[position]) {
            when (this.jobState) {
                JobState.Ready -> {
                    holder.ready()
                }

                JobState.Ing -> {
                    holder.start()
                }

                is JobState.Failure -> {
                    holder.failed()
                }

                is JobState.Success -> {
                    holder.success(isPayLoad)
                }
            }
            Glide.with(context)
                .load(this.uri)
                .into(holder.ivIcon)
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.count()
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun submitList(imageInfoList: List<ImageInfo>) {
        differ.submitList(imageInfoList)
    }

    fun getItem(pos: Int): ImageInfo? {
        return differ.currentList.getOrNull(pos)
    }

    val finishCount
        get() = data.count { it.jobState is JobState.Success }

    /** ENH-13: đếm riêng số ảnh lỗi để UI hiển thị rõ thay vì chỉ báo 1 trạng thái tổng. */
    val failCount
        get() = data.count { it.jobState is JobState.Failure }

    fun updateJobState(it: ImageInfo?) {
        val index = data.indexOf(it).takeIf { it != -1 } ?: return
        Log.i("onBindViewHolder", "payloads, in $index")
        notifyItemChanged(index, "state")
    }

    class ImageHolder(itemView: View) : BaseViewHolder(itemView) {
        fun ready() {
            ivIcon.ready()
            ivDone.animate().cancel()
            ivDone.isVisible = false
        }

        fun start() {
            ivIcon.start()
            ivDone.isVisible = false
        }

        fun success(animate: Boolean = true) {
            ivIcon.finish(animate)
            ivDone.appear()
        }

        fun failed() {
            ivIcon.failed()
            ivDone.disappear()
        }

        val ivIcon: ProgressImageView = itemView.findViewById(R.id.ivIcon)
        private val ivDone: ImageView = itemView.findViewById(R.id.ivDone)
    }
}
