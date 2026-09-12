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

    // ENH-08 (bug phát hiện qua smoke test thật, batch 2 ảnh trên TECNO BG6): nguồn "sự thật"
    // đồng bộ để build update kế tiếp trong updateJobState() — KHÔNG được dùng differ.currentList
    // (chỉ đổi SAU KHI AsyncListDiffer tính xong diff trên background thread, bất đồng bộ). Nếu
    // build list mới từ currentList mỗi lần, 2 lệnh updateJobState() gọi liên tiếp trước khi lần
    // trước kịp áp dụng vào currentList sẽ làm MẤT update trước đó — quan sát thật: ảnh nặng (icon
    // watermark tile trên ảnh camera full-res) mất >60s xử lý, khi export xong file thật trên đĩa
    // nhưng card trong danh sách vẫn kẹt icon "đang xử lý" mãi mãi. `pendingList` mutate đồng bộ
    // ngay tại lúc gọi (luôn trên main thread, đúng thứ tự FIFO) nên không bao giờ mất update.
    private var pendingList: MutableList<ImageInfo> = mutableListOf()

    private val differ: AsyncListDiffer<ImageInfo> by lazy {
        AsyncListDiffer(this, differCallback)
    }

    private val differCallback: DiffUtil.ItemCallback<ImageInfo> by lazy {
        object : DiffUtil.ItemCallback<ImageInfo>() {
            // ENH-08: "cùng item" phải xét theo khoá ổn định (uri), không phải full equals — 1
            // ảnh đổi jobState/result vẫn LÀ CÙNG 1 item (chỉ nội dung đổi), không phải item mới.
            // Trước đây (`oldItem == newItem`, data class equals so mọi field) tình cờ "đúng" chỉ
            // vì ImageInfo còn mutable (mutate tại chỗ nên old/new luôn trùng object) — giờ
            // ImageInfo bất biến (ENH-08), full equals sẽ luôn `false` khi jobState/result đổi.
            override fun areItemsTheSame(oldItem: ImageInfo, newItem: ImageInfo): Boolean {
                return oldItem.uri == newItem.uri
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
        pendingList = imageInfoList.toMutableList()
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
        // ENH-08: ImageInfo bất biến — `it` là 1 COPY mới (jobState/result đã đổi so với item
        // đang nằm trong danh sách), full equals `indexOf()` cũ sẽ luôn trả -1. Tìm theo uri (khoá
        // ổn định), mutate `pendingList` (đồng bộ, không phụ thuộc AsyncListDiffer đã áp dụng xong
        // hay chưa — xem comment tại khai báo `pendingList`) rồi submit bản sao của nó.
        // `notifyItemChanged(index, "state")` trong commitCallback giữ nguyên payload "state" cũ
        // (trigger animate khi thành công — xem `ImageHolder.success(isPayLoad)`), chạy SAU khi
        // `submitList` đã cập nhật xong `currentList` nên không rebind nhầm dữ liệu cũ.
        if (it == null) return
        val index = pendingList.indexOfFirst { existing -> existing.uri == it.uri }
        if (index == -1) return
        pendingList[index] = it
        differ.submitList(pendingList.toList()) {
            Log.i("onBindViewHolder", "payloads, in $index")
            notifyItemChanged(index, "state")
        }
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
