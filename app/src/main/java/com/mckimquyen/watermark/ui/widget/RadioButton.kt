package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.utils.ktx.colorOnSurface
import com.mckimquyen.watermark.utils.ktx.colorOnTertiaryContainer
import com.mckimquyen.watermark.utils.ktx.colorSurfaceVariant
import com.mckimquyen.watermark.utils.ktx.colorTertiaryContainer
import com.mckimquyen.watermark.utils.ktx.dp
import com.mckimquyen.watermark.utils.ktx.getColorFromAttr

class RadioButton : View {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

//    constructor(
//        context: Context?,
//        attrs: AttributeSet?,
//        defStyleAttr: Int,
//        defStyleRes: Int,
//    ) : super(context, attrs, defStyleAttr, defStyleRes)

//    companion object {
//        private const val TAG = "RadioButton"
//    }

//    private val icTint = ContextCompat.getColor(context, R.color.selector_gallery_icon_tint)

    private val bgColorNormal = Color.TRANSPARENT
    private val bgColorSelected = Color.TRANSPARENT

    private val strokeColorNormal by lazy {
        MaterialColors.compositeARGBWithAlpha(
            context.getColorFromAttr(com.google.android.material.R.attr.colorBackgroundFloating),
            125
        )
    }
    private val strokeColorSelected = Color.TRANSPARENT
    private val strokeWidth = 2.dp

    private val iconRes: Int = R.drawable.ic_gallery_radio_button

    private val icon: Drawable by lazy {
        ContextCompat.getDrawable(context, iconRes) ?: ColorDrawable(context.colorOnSurface)
    }

    private val paint = Paint().apply {
        isDither = true
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        // draw background
        paint.style = Paint.Style.FILL
        paint.color = if (isChecked) bgColorSelected else bgColorNormal
        paint.strokeWidth = 0f
        canvas?.drawCircle(
            /* cx = */ measuredWidth / 2f,
            /* cy = */ measuredHeight / 2f,
            /* radius = */ (measuredWidth - strokeWidth) / 2f,
            /* paint = */ paint
        )
        // draw stroke
        paint.style = Paint.Style.STROKE
        paint.color = if (isChecked) strokeColorSelected else strokeColorNormal
        paint.strokeWidth = strokeWidth.toFloat()
        canvas?.drawCircle(
            /* cx = */ measuredWidth / 2f,
            /* cy = */ measuredHeight / 2f,
            /* radius = */ (measuredWidth - strokeWidth) / 2f,
            /* paint = */ paint
        )
        // icon
        if (isChecked) {
            icon.setBounds(0, 0, measuredWidth, measuredHeight)
            icon.draw(canvas)
        }
    }

    var isChecked = false
        set(value) {
            if (field != value) {
                invalidate()
                listener.invoke(value)
            }
            field = value
        }

    fun toggle() {
        isChecked = !isChecked
    }

    private var listener: (isCheck: Boolean) -> Unit = {}

    fun setOnCheckedChangeListener(listener: (isCheck: Boolean) -> Unit) {
        this.listener = listener
    }
}
