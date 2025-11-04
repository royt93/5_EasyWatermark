package com.mckimquyen.watermark.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.CalendarContract
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.widget.utils.BounceEdgeEffectFactory
import com.mckimquyen.watermark.utils.ktx.dp
import com.mckimquyen.watermark.utils.ktx.generateAppearAnimationList
import com.mckimquyen.watermark.utils.ktx.generateDisappearAnimationList
import kotlin.math.abs

/**
 * Custom launch ViewGroup to replace MotionLayout.
 * Using [toLaunchMode] or [toEditorMode] to transform the view layout.
 * Animation was Included.
 * @author roy.mobile.dev@gmail.com
 * @date 2021/8/11
 */
@SuppressLint("ClickableViewAccessibility")
class LaunchView : CustomViewGroup {

    companion object {
        private const val TAG = "LaunchView"
    }

    //region 1 constructor
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)
    //endregion

    //region 2 children components
    // Logo container with glow rings
    private val logoContainer: android.widget.FrameLayout by lazy {
        android.widget.FrameLayout(context).apply {
            layoutParams = MarginLayoutParams(
                300.dp,
                300.dp
            ).also {
                it.setMargins(0, 0, 0, 16.dp)
            }

            // Outer glow ring
            addView(View(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(300.dp, 300.dp).apply {
                    gravity = Gravity.CENTER
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_glass_shimmer)
                alpha = 0.15f
            })

            // Middle glow ring
            addView(View(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(270.dp, 270.dp).apply {
                    gravity = Gravity.CENTER
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_glass_shimmer)
                alpha = 0.25f
            })

            // Inner glow ring
            addView(View(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(240.dp, 240.dp).apply {
                    gravity = Gravity.CENTER
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_glass_shimmer)
                alpha = 0.35f
            })

            // Logo
            addView(ImageFilterView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(210.dp, 210.dp).apply {
                    gravity = Gravity.CENTER
                }
                roundPercent = 1.0f
                setImageResource(R.drawable.ic_launcher)
            })
        }
    }

    private val logoView: ImageView
        get() = logoContainer.children.last() as ImageView

    val ivSelectedPhotoTips: MaterialButton by lazy {
        MaterialButton(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                64.dp
            ).also {
                it.setMargins(0, 0, 0, 24.dp)
            }

            // Glass button style
            minHeight = 64.dp
            minWidth = 240.dp
            textAlignment = TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER

            // Glass colors
            setBackgroundColor(ContextCompat.getColor(context, R.color.glass_surface))
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))

            // Text
            text = context.getString(R.string.tips_pick_image)
            textSize = 18f
            letterSpacing = 0.05f

            // Rounded corners
            shapeAppearanceModel = ShapeAppearanceModel.Builder()
                .setAllCornerSizes(32f)
                .build()

            // Stroke for glass effect
            strokeWidth = 2.dp
            strokeColor = ContextCompat.getColorStateList(context, R.color.glass_border)

            // Elevation
            elevation = 8f

            // Padding horizontal for text
            setPadding(48.dp, paddingTop, 48.dp, paddingBottom)
        }
    }

    val ivGoAboutPage: MaterialButton by lazy {
        MaterialButton(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                64.dp
            ).also {
                it.setMargins(0, 0, 0, 48.dp)
            }

            // Glass button style - same as Choose Images
            minHeight = 64.dp
            minWidth = 240.dp
            textAlignment = TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER

            // Glass colors
            setBackgroundColor(ContextCompat.getColor(context, R.color.glass_surface))
            setTextColor(ContextCompat.getColor(context, R.color.glass_text_primary))

            // Text with icon
            text = context.getString(R.string.about_title_info)
            textSize = 18f
            letterSpacing = 0.05f

            // Add settings icon to the left of text
            icon = ContextCompat.getDrawable(context, R.drawable.ic_settings_glass)
            iconTint = ContextCompat.getColorStateList(context, R.color.glass_text_primary)
            iconGravity = MaterialButton.ICON_GRAVITY_START
            iconPadding = 12.dp
            iconSize = 24.dp

            // Rounded corners
            shapeAppearanceModel = ShapeAppearanceModel.Builder()
                .setAllCornerSizes(32f)
                .build()

            // Stroke for glass effect
            strokeWidth = 2.dp
            strokeColor = ContextCompat.getColorStateList(context, R.color.glass_border)

            // Elevation
            elevation = 8f

            // Padding horizontal for text
            setPadding(32.dp, paddingTop, 32.dp, paddingBottom)
        }
    }

    val toolbar: MaterialToolbar by lazy {
        MaterialToolbar(context).apply {
            layoutParams =
                MarginLayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
                    .also { it.setMargins(0, 20.dp, 0, 0) }
//            setBackgroundColor(context.colorSurface)
        }
    }

    val ivPhoto: WaterMarkImageView by lazy {
        WaterMarkImageView(context).apply {
            layoutParams =
                MarginLayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )
            setPadding(12.dp)
            scaleType = ImageView.ScaleType.MATRIX
        }
    }

    val tabLayout: TabLayout by lazy {
        TabLayout(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dp
            }
            isTabIndicatorFullWidth = false
            tabGravity = TabLayout.GRAVITY_FILL
            tabIndicatorAnimationMode = TabLayout.INDICATOR_ANIMATION_MODE_ELASTIC
            setBackgroundColor(Color.TRANSPARENT)
            val contentTab = newTab().also {
                it.text = context.getString(R.string.title_content)
            }
            val styleTab = newTab().also {
                it.text = context.getString(R.string.title_style)
            }
            val layoutTab = newTab().also {
                it.text = context.getString(R.string.title_layout)
            }

            addTab(contentTab)
            addTab(styleTab)
            addTab(layoutTab)
        }
    }

    val fcFunctionDetail: FragmentContainerView by lazy {
        FragmentContainerView(context).apply {
            id = generateViewId()
            layoutParams = MarginLayoutParams(
                LayoutParams.MATCH_PARENT,
                56.dp
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    val rvPanel: TouchSensitiveRv by lazy {
        TouchSensitiveRv(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            edgeEffectFactory = BounceEdgeEffectFactory(context, this)
        }
    }

    val rvPhotoList: TouchSensitiveRv by lazy {
        TouchSensitiveRv(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            this.minimumHeight = 144
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            edgeEffectFactory = BounceEdgeEffectFactory(context, this)
        }
    }
    //endregion

    //region 3 private field
    private val launchViews by lazy {
        listOf(logoContainer, ivSelectedPhotoTips, ivGoAboutPage)
    }

    private val editorViews by lazy {
        listOf(toolbar, ivPhoto, fcFunctionDetail, tabLayout, rvPanel, rvPhotoList)
    }

    private val launchModeAppearAnimationList by lazy {
        generateAppearAnimationList(launchViews)
    }

    private val editorModeDisappearAnimationList by lazy {
        generateDisappearAnimationList(editorViews)
    }

    var mode: ViewMode = ViewMode.LaunchMode
        private set(value) {
            if (field == value) return
            val oldMode = field
            field = value
            transformLayout(oldMode, value)
        }

    private var launchViewListener: LaunchViewListener? = null

    private var startX = 0f

    private var startY = 0f

    private val dragYAnimation by lazy {
        SpringAnimation(this, SpringAnimation.TRANSLATION_Y).apply {
            spring = SpringForce()
                .setFinalPosition(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW)
        }
    }

    private val dragXAnimation by lazy {
        SpringAnimation(this, SpringAnimation.TRANSLATION_X).apply {
            spring = SpringForce()
                .setFinalPosition(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW)
        }
    }
    //endregion

    init {
        clipChildren = false
        clipToPadding = false
//        setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_dark_background))

        // Apply glass gradient background
        val bgDrawable = ContextCompat.getDrawable(
            /* context = */ context,
            /* id = */ R.drawable.bg_glass_gradient
        )
        background = bgDrawable

        launchViews.forEach {
            it.isVisible = false
            addView(it)
        }
        editorViews.forEach {
            it.isVisible = false
            addView(it)
        }
        post {
            launchModeAppearAnimationList.forEach { it.start() }
        }
    }

    //region 4 override view rendering
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        rvPanel.let {
            it.setPadding(it.measuredWidth / 2, 0, it.measuredWidth / 2, 0)
        }
        // measure children
        children.forEach {
            if (it != ivPhoto) {
                measureChildWithMargins(it, widthMeasureSpec, 0, heightMeasureSpec, 0)
            }
        }
        val heightUsed = toolbar.measuredHeight
            .plus(tabLayout.measuredHeightWithMargins)
            .plus(rvPanel.measuredHeightWithMargins)
            .plus(fcFunctionDetail.measuredHeightWithMargins)
            .plus(rvPhotoList.measuredHeightWithMargins)

        Log.d(
            TAG,
            "${toolbar.measuredHeight}, ${tabLayout.measuredHeightWithMargins}, ${rvPanel.measuredHeightWithMargins},  ${fcFunctionDetail.measuredHeightWithMargins},  ${rvPhotoList.measuredHeightWithMargins}"
        )

        measureChildWithMargins(ivPhoto, widthMeasureSpec, 0, heightMeasureSpec, heightUsed)

        // we are in match paren mode so just using parent size.
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        when (mode) {
            ViewMode.Editor -> layoutEditor()
            ViewMode.LaunchMode -> layoutLaunch()
        }
    }

    private fun layoutLaunch() {
        logoContainer.layoutCenterHorizontal(appendY = (measuredHeight * 0.2f).toInt())
        ivSelectedPhotoTips.layoutCenterHorizontal(appendY = (measuredHeight * 0.6f).toInt())
        ivGoAboutPage.let {
            it.layoutCenterHorizontal(appendY = (measuredHeight - it.measuredHeightWithMargins))
        }
    }

    private fun layoutEditor() {
        // top
        toolbar.layout(0, 0)
        ivPhoto.layout(0, toolbar.bottom)
        // bottom
        tabLayout.let {
            it.layout(0, measuredHeight - it.measuredHeightWithMargins)
        }
        rvPanel.let {
            it.layout(0, tabLayout.top - it.measuredHeightWithMargins)
        }
        fcFunctionDetail.let {
            it.layout(0, rvPanel.top - it.measuredHeightWithMargins)
        }
        rvPhotoList.let {
            it.layout(0, fcFunctionDetail.top - it.measuredHeightWithMargins)
        }
    }
    //endregion

    //region 5 inside caller
    /**
     * Transform the layout when mode switched.
     * @author roy.mobile.dev@gmail.com
     * @date 2021/8/12
     */
    private fun transformLayout(oldMode: ViewMode, toMode: ViewMode) {
        when (toMode) {
            ViewMode.Editor -> {
                launchViews.forEach {
                    it.alpha = 0f
                    it.isVisible = false
                }
                editorViews.forEach {
                    it.alpha = 1f
                    it.translationY = 0f
                    it.isVisible = true
                }
            }

            ViewMode.LaunchMode -> {
                editorModeDisappearAnimationList.forEach {
                    it.start()
                }
                launchModeAppearAnimationList.forEach {
                    it.start()
                }
            }
        }
        launchViewListener?.onModeChange(oldMode, toMode)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (mode != ViewMode.LaunchMode) {
            return super.onTouchEvent(event)
        }
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragYAnimation.cancel()
                dragXAnimation.cancel()
                startX = event.rawX
                startY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startX)
                val dy = (event.rawY - startY)

                var percentX = (1 - abs(this.translationX) / measuredWidth).coerceAtMost(1f) / 4
                if (percentX <= 0.2) percentX = 0f
                var percentY = (1 - abs(this.translationY) / measuredHeight).coerceAtMost(1f) / 4
                if (percentY <= 0.2) percentY = 0f
                this.translationX += dx * percentX
                this.translationY += dy * percentY
                startY = event.rawY
                startX = event.rawX
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                dragYAnimation.start()
                dragXAnimation.start()
            }
        }
        return super.onTouchEvent(event)
    }
    //endregion

    //region 6 outside caller
    fun toLaunchMode() {
        mode = ViewMode.LaunchMode
    }

    fun toEditorMode(): Boolean {
        val animate = mode == ViewMode.LaunchMode
        mode = ViewMode.Editor
        return animate
    }

    fun isEdit(): Boolean = mode == ViewMode.Editor

    fun setListener(block: LaunchViewListenerBuilder.() -> Unit) {
        launchViewListener = LaunchViewListenerBuilder().also(block)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel animations to prevent memory leak
        dragYAnimation.cancel()
        dragXAnimation.cancel()
        launchViewListener = null
    }
    //endregion

    /**
     * Sealed class forView showing mode.
     * @author roy.mobile.dev@gmail.com
     * @date 2021/8/12
     */
    sealed class ViewMode {
        object LaunchMode : ViewMode()
        object Editor : ViewMode()
    }
}
