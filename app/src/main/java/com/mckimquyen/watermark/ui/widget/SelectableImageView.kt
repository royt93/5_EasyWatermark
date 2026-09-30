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
import androidx.appcompat.content.res.AppCompatResources
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
            replaceSrcBitmap()
            invalidate()
        }

    var circleResId: Int = 0
        set(value) {
            field = value
            replaceSrcBitmap()
            invalidate()
        }

    private var srcBitmap: Bitmap? = null
    private var ownsSrcBitmap = false

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
        replaceSrcBitmap()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // RecyclerView (ColorPreviewAdapter) luôn set lại circleColor/circleResId trước khi 1
        // ViewHolder tái sử dụng hiển thị lại — an toàn recycle bitmap tự tạo ở đây, replaceSrcBitmap()
        // sẽ tự tạo bitmap mới đúng lúc rebind.
        val bmp = srcBitmap
        if (ownsSrcBitmap && bmp != null && !bmp.isRecycled) {
            bmp.recycle()
        }
        srcBitmap = null
        ownsSrcBitmap = false
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

    /**
     * Thay bitmap nguồn và recycle bản CŨ nếu view tự tạo (màu/vector). Bitmap lấy thẳng từ
     * [BitmapDrawable] là resource shared — không thuộc sở hữu view, tuyệt đối không recycle.
     */
    private fun replaceSrcBitmap() {
        val old = srcBitmap
        val oldOwned = ownsSrcBitmap
        val (replacement, owned) = createSrcBitmapFromRes()
        srcBitmap = replacement
        ownsSrcBitmap = owned
        if (oldOwned && old != null && old !== replacement && !old.isRecycled) {
            old.recycle()
        }
    }

    private fun createSrcBitmapFromRes(
        resId: Int = circleResId,
        w: Int = measuredWidth,
        h: Int = measuredHeight,
        color: Int = circleColor
    ): Pair<Bitmap?, Boolean> {
        if (resId > 0) {
            val drawable = AppCompatResources.getDrawable(context, resId)
            if (drawable is BitmapDrawable) {
                return drawable.bitmap to false
            }
            if (drawable != null && drawable.intrinsicHeight > 0 && drawable.intrinsicWidth > 0) {
                val bitmap = Bitmap.createBitmap(
                    /* width = */ drawable.intrinsicWidth,
                    /* height = */ drawable.intrinsicHeight,
                    /* config = */ Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                return bitmap to true
            }
        }
        if (color != 0 && w > 0 && h > 0) {
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
            } to true
        }
        return null to false
    }

    private fun generatePaint(): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            isFilterBitmap = true
        }
    }
}
