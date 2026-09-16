package com.mckimquyen.watermark.utils.ktx

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
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

fun Int.toColor(
    toColor: Int,
    autoStart: Boolean = true,
    duration: Long = WaterMarkImageView.ANIMATION_DURATION,
    doOnUpdate: (it: ValueAnimator) -> Unit = {}
): ObjectAnimator? {
    return ObjectAnimator.ofInt(
        /* target = */ this,
        /* propertyName = */ "backgroundColor",
        /* ...values = */ this,
        toColor
    ).apply {
        setEvaluator(ArgbEvaluator())
        addUpdateListener {
            doOnUpdate.invoke(it)
        }
        this.duration = duration
        if (autoStart) start()
    }
}
