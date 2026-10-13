package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

class UniformScrollGridLayoutManager : GridLayoutManager {

//    constructor(
//        context: Context?,
//        attrs: AttributeSet?,
//        defStyleAttr: Int,
//        defStyleRes: Int,
//    ) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context?, spanCount: Int) : super(context, spanCount)
//    constructor(
//        context: Context?,
//        spanCount: Int,
//        orientation: Int,
//        reverseLayout: Boolean,
//    ) : super(context, spanCount, orientation, reverseLayout)

    private var enable: Boolean = true

    var speed = 1.2f

    var scrollBarView: View? = null

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView?,
        state: RecyclerView.State?,
        position: Int
    ) {
        // BUG-80: RecyclerView có thể null khi đã detach; super cũng deref nó nên không delegate, bỏ qua yêu cầu cuộn.
        if (recyclerView == null) return
        if (enable) {
            val linearSmoothScroller =
                object : LinearSmoothScroller(recyclerView.context) {
                    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics?): Float {
                        return speed
                    }
                }
            linearSmoothScroller.targetPosition = position
            startSmoothScroll(linearSmoothScroller)
        } else {
            super.smoothScrollToPosition(recyclerView, state, position)
        }
    }

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int {
        return scrollBarView?.measuredHeight ?: DEFAULT_SCROLL_EXTENT_PX
    }

    companion object {
        /** Extent dự phòng (px) khi chưa gắn `scrollBarView` — giá trị gốc từ ban đầu, cho slider gallery không chia 0. */
        internal const val DEFAULT_SCROLL_EXTENT_PX = 110
    }
}
