package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation

class BlinkCursorView : View {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val alphaAnimation by lazy {
        AlphaAnimation(1f, 0f).apply {
            duration = 400
            // BUG-AUDIT-2026-09-29: đúng ra dùng hằng số Animation.INFINITE/REVERSE (class của
            // chính AlphaAnimation) thay vì ObjectAnimator.INFINITE/REVERSE — giá trị số trùng
            // nhau (-1, 2) nên chạy đúng từ trước, chỉ sửa cho rõ ý, không đổi hành vi.
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBlink()
    }

    private fun startBlink() {
        if (this.animation == null) {
            this.startAnimation(alphaAnimation)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        this.clearAnimation()
    }
}
