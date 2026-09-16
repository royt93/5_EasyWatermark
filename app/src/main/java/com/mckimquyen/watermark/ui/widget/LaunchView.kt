package com.mckimquyen.watermark.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.marginStart
import androidx.core.view.setPadding
import androidx.fragment.app.FragmentContainerView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.TransitionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.transition.MaterialFadeThrough
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.widget.utils.BounceEdgeEffectFactory
import com.mckimquyen.watermark.utils.ktx.dp
import com.mckimquyen.watermark.utils.ktx.generateAppearAnimationList

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
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)
    //endregion

    //region 2 children components
    // Logo container with clean M3 rounded logo
    private val logoContainer: android.widget.FrameLayout by lazy {
        android.widget.FrameLayout(context).apply {
            layoutParams = MarginLayoutParams(
                160.dp,
                160.dp
            ).also {
                it.setMargins(0, 0, 0, 16.dp)
            }

            addView(
                ShapeableImageView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(140.dp, 140.dp).apply {
                        gravity = Gravity.CENTER
                    }
                    shapeAppearanceModel = ShapeAppearanceModel.Builder()
                        .setAllCornerSizes(36.dp.toFloat())
                        .build()
                    setImageResource(R.drawable.ic_launcher)
                }
            )
        }
    }

    val tvAppBrand: TextView by lazy {
        MaterialTextView(context).apply {
            layoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            text = context.getString(R.string.app_name)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
            typeface = Typeface.DEFAULT_BOLD
            val onSurfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            setTextColor(onSurfaceColor)
            gravity = Gravity.CENTER
            textAlignment = TEXT_ALIGNMENT_CENTER
        }
    }

    val tvAppTagline: TextView by lazy {
        MaterialTextView(context).apply {
            layoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            text = "Fast • Elegant • Offline Protection"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
            setTextColor(onSurfaceVariant)
            gravity = Gravity.CENTER
            textAlignment = TEXT_ALIGNMENT_CENTER
        }
    }

    val tvVersionCopyright: TextView by lazy {
        MaterialTextView(context).apply {
            layoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            text = "v${BuildConfig.VERSION_NAME} • © 2026 McKim Quyen"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            val textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            textAlignment = TEXT_ALIGNMENT_CENTER
        }
    }

    val ivSelectedPhotoTips: MaterialButton by lazy {
        MaterialButton(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                56.dp
            ).also {
                it.setMargins(0, 0, 0, 16.dp)
            }

            minHeight = 56.dp
            minWidth = 240.dp
            textAlignment = TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER

            val primaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.BLACK)
            val onPrimaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)

            setBackgroundColor(primaryColor)
            setTextColor(onPrimaryColor)

            text = context.getString(R.string.tips_pick_image)
            textSize = 16f
            letterSpacing = 0.02f

            icon = ContextCompat.getDrawable(context, R.drawable.ic_picker_image)
            iconTint = ColorStateList.valueOf(onPrimaryColor)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 10.dp
            iconSize = 22.dp

            shapeAppearanceModel = ShapeAppearanceModel.Builder()
                .setAllCornerSizes(28.dp.toFloat())
                .build()

            strokeWidth = 0
            elevation = 2.dp.toFloat()
            setPadding(32.dp, paddingTop, 32.dp, paddingBottom)
        }
    }

    val ivGoAboutPage: MaterialButton by lazy {
        MaterialButton(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                56.dp
            ).also {
                it.setMargins(0, 0, 0, 40.dp)
            }

            minHeight = 56.dp
            minWidth = 240.dp
            textAlignment = TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER

            val secContainerColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, Color.LTGRAY)
            val onSecContainerColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer, Color.BLACK)

            setBackgroundColor(secContainerColor)
            setTextColor(onSecContainerColor)

            text = context.getString(R.string.about_title_info)
            textSize = 16f
            letterSpacing = 0.02f

            icon = ContextCompat.getDrawable(context, R.drawable.ic_settings_glass)
            iconTint = ColorStateList.valueOf(onSecContainerColor)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 10.dp
            iconSize = 22.dp

            shapeAppearanceModel = ShapeAppearanceModel.Builder()
                .setAllCornerSizes(28.dp.toFloat())
                .build()

            strokeWidth = 0
            elevation = 0f
            setPadding(28.dp, paddingTop, 28.dp, paddingBottom)
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
            // Edge-to-edge: small right padding so the last action icon (settings) clears the
            // screen edge / rounded corner (Toolbar contentInsetEnd is 0 here).
            setPadding(0, 0, 12.dp, 0)
            clipToPadding = false
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
            val primaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.BLACK)
            val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
            setSelectedTabIndicatorColor(primaryColor)
            setTabTextColors(onSurfaceVariant, primaryColor)
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
            ).also {
                it.setMargins(16.dp, 0, 16.dp, 0)
            }
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }
    }

    val rvPanel: TouchSensitiveRv by lazy {
        TouchSensitiveRv(context).apply {
            layoutParams = MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dp, 0, 16.dp, 0)
            }
            setPadding(8.dp, 4.dp, 8.dp, 4.dp)
            val surfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY)
            val pill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28.dp.toFloat()
                setColor(surfaceColor)
            }
            background = pill
            elevation = 3.dp.toFloat()
            clipToOutline = true
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
            ).also {
                it.setMargins(16.dp, 0, 16.dp, 0)
            }
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
        listOf(logoContainer, tvAppBrand, tvAppTagline, ivSelectedPhotoTips, ivGoAboutPage, tvVersionCopyright)
    }

    private val editorViews by lazy {
        listOf(toolbar, ivPhoto, fcFunctionDetail, tabLayout, rvPanel, rvPhotoList)
    }

    private val launchModeAppearAnimationList by lazy {
        generateAppearAnimationList(launchViews)
    }

    var mode: ViewMode = ViewMode.LaunchMode
        private set(value) {
            if (field == value) return
            val oldMode = field
            field = value
            transformLayout(oldMode, value)
        }

    private var launchViewListener: LaunchViewListener? = null

    //endregion

    init {
        clipChildren = false
        clipToPadding = false

        val surfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        setBackgroundColor(surfaceColor)

        launchViews.forEach {
            it.isVisible = false
            addView(it)
        }
        editorViews.forEach {
            it.isVisible = false
            addView(it)
        }
        post {
            // Only play the launch-mode appear animation if we're still in launch mode.
            // Share-image entry (ACTION_SEND) can switch to Editor before this runs; without the
            // guard the appear animation re-shows the launch views (logo/tips/about) over the editor.
            if (mode == ViewMode.LaunchMode) {
                launchModeAppearAnimationList.forEach { it.start() }
            }
        }
    }
    //endregion

    //region 4 override view rendering
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
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

        AppLog.d(
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

    private fun View.layoutHorizontallyCentered(topY: Int) {
        val startX = (this@LaunchView.measuredWidth - this.measuredWidth) / 2
        layout(startX, topY, startX + this.measuredWidth, topY + this.measuredHeight)
    }

    private fun layoutLaunch() {
        val usableTop = paddingTop
        val usableBottom = measuredHeight - paddingBottom
        val usableHeight = (usableBottom - usableTop).coerceAtLeast(1)

        // 1. Logo container placed proportionally in top-middle area
        val logoY = usableTop + (usableHeight * 0.08f).toInt()
        logoContainer.layoutHorizontallyCentered(logoY)

        // 2. App Name directly below logo
        val titleY = logoContainer.bottom + 16.dp
        tvAppBrand.layoutHorizontallyCentered(titleY)

        // 3. Tagline directly below App Name
        val subtitleY = tvAppBrand.bottom + 8.dp
        tvAppTagline.layoutHorizontallyCentered(subtitleY)

        // 4. "Choose Images" primary CTA
        val ctaY = tvAppTagline.bottom + 48.dp
        ivSelectedPhotoTips.layoutHorizontallyCentered(ctaY)

        // 5. "Information & Settings" secondary button
        val aboutY = ivSelectedPhotoTips.bottom + 16.dp
        ivGoAboutPage.layoutHorizontallyCentered(aboutY)

        // 6. Version & Copyright footer safely placed above navigation bar inset
        val footerY = usableBottom - tvVersionCopyright.measuredHeight - 20.dp
        tvVersionCopyright.layoutHorizontallyCentered(footerY)
    }

    private fun layoutEditor() {
        // top — sit directly below the status-bar inset (paddingTop). The 64dp toolbar's own
        // centered content already provides the gap; the legacy 20dp top margin is redundant
        // under edge-to-edge and only added a large empty band above the icons.
        toolbar.layout(0, paddingTop)
        ivPhoto.layout(0, toolbar.bottom)
        // bottom
        tabLayout.let {
            // Place tabLayout at the bottom, above the navigation bar padding
            val yOffset = measuredHeight - paddingBottom - it.measuredHeight - 16.dp
            it.layout(it.marginStart, yOffset)
        }
        rvPanel.let {
            // 8dp gap above tabLayout
            val yOffset = tabLayout.top - 8.dp - it.measuredHeight
            val xOffset = (measuredWidth - it.measuredWidth) / 2
            it.layout(xOffset, yOffset)
        }
        fcFunctionDetail.let {
            // 8dp gap above rvPanel
            val yOffset = rvPanel.top - 8.dp - it.measuredHeight
            it.layout(it.marginStart, yOffset)
        }
        rvPhotoList.let {
            // 16dp gap above fcFunctionDetail for thumbnails
            val yOffset = fcFunctionDetail.top - 16.dp - it.measuredHeight
            it.layout(it.marginStart, yOffset)
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
        val transition = MaterialFadeThrough().apply {
            duration = 300L
            interpolator = FastOutSlowInInterpolator()
        }
        TransitionManager.beginDelayedTransition(this, transition)
        when (toMode) {
            ViewMode.Editor -> {
                launchViews.forEach {
                    it.animate().cancel()
                    it.isVisible = false
                }
                editorViews.forEach {
                    it.alpha = 1f
                    it.translationY = 0f
                    it.isVisible = true
                }
            }

            ViewMode.LaunchMode -> {
                editorViews.forEach {
                    it.isVisible = false
                }
                launchViews.forEach {
                    it.alpha = 1f
                    it.translationY = 0f
                    it.isVisible = true
                }
            }
        }
        launchViewListener?.onModeChange(oldMode, toMode)
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
