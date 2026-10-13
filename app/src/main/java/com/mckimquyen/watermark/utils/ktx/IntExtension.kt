package com.mckimquyen.watermark.utils.ktx

import android.animation.ValueAnimator
import android.content.res.Resources
import android.util.TypedValue
import com.mckimquyen.watermark.ui.widget.WaterMarkImageView

val Int.dp
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

val Float.dp
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )

/**
 * Animator đổi màu `this` -> [toColor]; giá trị đi qua [doOnUpdate] (`animatedValue as Int`).
 *
 * Dùng [ValueAnimator.ofArgb] chứ KHÔNG `ObjectAnimator.ofInt(this, "backgroundColor", ...)`:
 * `this` là Int boxing bị `ObjectAnimator` giữ qua `WeakReference` → GC thu thập là animator tự
 * `cancel()` giữa chừng (màu kẹt ở giá trị lưng chừng). Không cần target vì chỉ dùng update listener.
 */
fun Int.toColor(
    toColor: Int,
    autoStart: Boolean = true,
    duration: Long = WaterMarkImageView.ANIMATION_DURATION,
    doOnUpdate: (it: ValueAnimator) -> Unit = {}
): ValueAnimator {
    return ValueAnimator.ofArgb(this, toColor).apply {
        addUpdateListener { doOnUpdate.invoke(it) }
        this.duration = duration
        if (autoStart) start()
    }
}
