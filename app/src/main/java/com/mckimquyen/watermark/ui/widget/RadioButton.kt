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
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.utils.ktx.colorOnSurface
import com.mckimquyen.watermark.utils.ktx.dp

/**
 * Enhanced RadioButton check indicator for gallery grid.
 *
 * Unselected: white circle with dark drop-shadow backdrop → always visible on any photo.
 * Selected: vivid iOS Blue (#007AFF) filled circle + white checkmark icon.
 *
 * Visual contrast rules (WCAG AA+):
 * - Unselected: semi-transparent black backdrop (#80000000) + white stroke ring
 * - Selected: solid iOS Blue + white icon
 */
class RadioButton : View {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    // ── Unselected state ──────────────────────────────────────────────────────
    // Dark backdrop pill so the white ring is always visible on bright photos
    private val bgColorNormal = Color.parseColor("#80000000")         // black 50%
    private val strokeColorNormal = Color.parseColor("#E0FFFFFF")     // white 88%
    private val strokeWidthNormal = 1.5f.dp.toFloat()

    // ── Selected state ────────────────────────────────────────────────────────
    // iOS vivid blue fill — unmissable selection indicator
    private val bgColorSelected = Color.parseColor("#FF007AFF")       // iOS Blue
    private val strokeWidthSelected = 0f

    private val iconRes: Int = R.drawable.ic_gallery_radio_button

    private val icon: Drawable by lazy {
        ContextCompat.getDrawable(context, iconRes) ?: ColorDrawable(context.colorOnSurface)
    }

    private val paint = Paint().apply {
        isDither = true
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = measuredWidth / 2f
        val cy = measuredHeight / 2f
        val r = measuredWidth / 2f - strokeWidthNormal / 2f

        if (isChecked) {
            // Solid blue fill
            paint.style = Paint.Style.FILL
            paint.color = bgColorSelected
            paint.strokeWidth = 0f
            canvas.drawCircle(cx, cy, r, paint)

            // Icon (white checkmark from drawable)
            icon.setBounds(2, 2, measuredWidth - 2, measuredHeight - 2)
            icon.draw(canvas)
        } else {
            // Dark backdrop fill — ensures white ring reads on any photo
            paint.style = Paint.Style.FILL
            paint.color = bgColorNormal
            paint.strokeWidth = 0f
            canvas.drawCircle(cx, cy, r, paint)

            // White stroke ring
            paint.style = Paint.Style.STROKE
            paint.color = strokeColorNormal
            paint.strokeWidth = strokeWidthNormal
            canvas.drawCircle(cx, cy, r, paint)
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
