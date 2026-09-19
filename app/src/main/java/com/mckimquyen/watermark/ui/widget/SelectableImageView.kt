package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import com.mckimquyen.watermark.R
import kotlin.math.min

class SelectableImageView : View {

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        val defaultPrimary = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorPrimary,
            Color.WHITE
        )
        context.obtainStyledAttributes(attrs, R.styleable.SelectableImageView).run {
            borderColor = getColor(R.styleable.SelectableImageView_siv_border_color, defaultPrimary)
            borderWidth = getDimension(R.styleable.SelectableImageView_siv_border_width, 3f)
            ringColor = getColor(R.styleable.SelectableImageView_siv_ring_color, defaultPrimary)
            ringWidth = getDimension(R.styleable.SelectableImageView_siv_ring_width, 3f)
            innerCircleWidth = getDimension(R.styleable.SelectableImageView_siv_circle_width, 10f)
            circleResId = getResourceId(R.styleable.SelectableImageView_siv_src, -1)
            circleColor = getColor(R.styleable.SelectableImageView_siv_color, Color.WHITE)
            srcBitmap = createSrcBitmapFromRes()
            recycle()
        }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    var circleColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }

    var circleResId: Int = 0
        set(value) {
            field = value
            srcBitmap = createSrcBitmapFromRes()
            invalidate()
        }

    private var srcBitmap: Bitmap? = null

    private val paint: Paint by lazy {
        generatePaint().apply {
            isDither = true
            isFilterBitmap = true
        }
    }

    private var borderColor: Int = Color.WHITE
        set(value) {
            field = value
            borderPaint.color = value
            invalidate()
        }

    private var borderWidth = 3f
        set(value) {
            field = value
            borderPaint.strokeWidth = field
            invalidate()
        }

    private val borderPaint by lazy {
        generatePaint().apply {
            isDither = true
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        }
    }

    private val swatchStrokePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * context.resources.displayMetrics.density
            color = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOutlineVariant,
                Color.LTGRAY
            )
        }
    }

    private val xfermode by lazy {
        PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    private var ringWidth = 3f

    private var ringColor = Color.WHITE

    private var innerCircleWidth = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val outSizeCircleRadius: Float
        get() {
            return (min(measuredWidth, measuredHeight).toFloat()) / 2 - borderWidth
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        srcBitmap = createSrcBitmapFromRes()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isSelected) {
            canvas?.drawCircle(
                (measuredWidth / 2).toFloat(),
                (measuredHeight / 2).toFloat(),
                outSizeCircleRadius,
                borderPaint
            )
        }

        val sc = canvas?.saveLayer(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), paint)
            ?: return

        canvas.drawCircle(
            /* cx = */ (measuredWidth / 2).toFloat(),
            /* cy = */ (measuredHeight / 2).toFloat(),
            /* radius = */ innerCircleWidth / 2,
            /* paint = */ paint
        )

        paint.xfermode = xfermode
        srcBitmap?.let {
            canvas.drawBitmap(
                /* bitmap = */ it,
                /* left = */ (measuredWidth - srcBitmap!!.width).toFloat() / 2,
                /* top = */ (measuredHeight - srcBitmap!!.height).toFloat() / 2,
                /* paint = */ paint
            )
        }
        paint.xfermode = null
        canvas.restoreToCount(sc)

        if (innerCircleWidth > 0f) {
            canvas.drawCircle(
                (measuredWidth / 2).toFloat(),
                (measuredHeight / 2).toFloat(),
                (innerCircleWidth / 2) - 0.5f,
                swatchStrokePaint
            )
        }
    }

    private fun createSrcBitmapFromRes(
        resId: Int = circleResId,
        w: Int = measuredWidth,
        h: Int = measuredHeight,
        color: Int = circleColor
    ): Bitmap? {
        var b: Bitmap? = null
        if (resId > 0) {
            b = context.getDrawable(resId)?.let {
                if (it is BitmapDrawable) {
                    it.bitmap
                } else if (it.intrinsicHeight > 0 && it.intrinsicWidth > 0) {
                    val bitmap = Bitmap.createBitmap(
                        /* width = */ it.intrinsicWidth,
                        /* height = */ it.intrinsicHeight,
                        /* config = */ Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    it.setBounds(0, 0, canvas.width, canvas.height)
                    it.draw(canvas)
                    bitmap
                } else {
                    null
                }
            }
        }
        if (b == null && color != 0 && w > 0 && h > 0) {
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
            }
        }
        return b
    }

    private fun generatePaint(): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            isFilterBitmap = true
        }
    }
}
