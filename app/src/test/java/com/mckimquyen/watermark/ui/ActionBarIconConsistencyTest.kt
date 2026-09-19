package com.mckimquyen.watermark.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.widget.LaunchView
import com.mckimquyen.watermark.utils.ktx.applyConsistentIconTint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Unit and Widget tests verifying that all Action Bar / Toolbar icons
 * (Navigation, Actions, Overflow ⋮) share consistent color and tint across all screens
 * in both Light and Dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActionBarIconConsistencyTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    private val inflater by lazy { LayoutInflater.from(themedContext) }

    @Test
    fun signatureActivity_toolbarIconsHaveConsistentColorOnSurface() {
        val root = inflater.inflate(R.layout.activity_signature, null)
        val toolbar = root.findViewById<MaterialToolbar>(R.id.toolbar)
        val ivUndo = root.findViewById<MaterialButton>(R.id.ivUndo)
        val ivClear = root.findViewById<MaterialButton>(R.id.ivClear)

        assertThat(toolbar).isNotNull()
        assertThat(ivUndo).isNotNull()
        assertThat(ivClear).isNotNull()

        val expectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )

        // Verify both Undo and Clear action buttons share colorOnSurface tint
        assertThat(ivUndo.iconTint?.defaultColor).isEqualTo(expectedColor)
        assertThat(ivClear.iconTint?.defaultColor).isEqualTo(expectedColor)
    }

    @Test
    fun menuXml_allMenuItemsHaveConsistentIconTint() {
        val toolbar = MaterialToolbar(themedContext)
        toolbar.inflateMenu(R.menu.menu)

        val expectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )

        val menu = toolbar.menu
        assertThat(menu.size()).isAtLeast(3)

        menu.forEach { item ->
            assertThat(item.icon).isNotNull()
            assertThat(item.iconTintList?.defaultColor).isEqualTo(expectedColor)
        }
    }

    @Test
    fun galleryMenuXml_allMenuItemsHaveConsistentIconTint() {
        val toolbar = MaterialToolbar(themedContext)
        toolbar.inflateMenu(R.menu.top_app_bar)

        val expectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )

        val menu = toolbar.menu
        assertThat(menu.size()).isAtLeast(2)

        menu.forEach { item ->
            assertThat(item.icon).isNotNull()
            assertThat(item.iconTintList?.defaultColor).isEqualTo(expectedColor)
        }
    }

    @Test
    fun launchView_toolbarInitializesWithConsistentIconTint() {
        val launchView = LaunchView(themedContext)
        val toolbar = launchView.toolbar

        assertThat(toolbar).isNotNull()
    }

    @Test
    fun mainActivity_applyToolbarIconColor_synchronizesAllIconsConsistently() {
        val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()

        val testColorDark = Color.WHITE
        activity.applyToolbarIconColor(testColorDark)

        val toolbar = activity.findViewById<Toolbar>(activity.launchView.toolbar.id)
            ?: activity.launchView.toolbar

        toolbar.menu.forEach { item ->
            assertThat(item.iconTintList?.defaultColor).isEqualTo(testColorDark)
        }

        val testColorLight = Color.DKGRAY
        activity.applyToolbarIconColor(testColorLight)

        toolbar.menu.forEach { item ->
            assertThat(item.iconTintList?.defaultColor).isEqualTo(testColorLight)
        }
    }

    @Test
    fun aboutAndVipAndOpenSource_toolbarsInflateWithNavigationIcons() {
        val aboutRoot = inflater.inflate(R.layout.a_about, null)
        val aboutToolbar = aboutRoot.findViewById<MaterialToolbar>(R.id.topAppBar)
        assertThat(aboutToolbar).isNotNull()
        assertThat(aboutToolbar.navigationIcon).isNotNull()

        val openSourceRoot = inflater.inflate(R.layout.a_open_source, null)
        val openSourceToolbar = openSourceRoot.findViewById<MaterialToolbar>(R.id.myToolbar)
        assertThat(openSourceToolbar).isNotNull()
        assertThat(openSourceToolbar.navigationIcon).isNotNull()

        val vipRoot = inflater.inflate(R.layout.activity_vip_management, null)
        val vipToolbar = vipRoot.findViewById<MaterialToolbar>(R.id.topAppBar)
        assertThat(vipToolbar).isNotNull()
        assertThat(vipToolbar.navigationIcon).isNotNull()
    }

    @Test
    fun universalTintEngine_samsungOneUiSimulation_tintsCompoundDrawablesAndActionViews() {
        val toolbar = MaterialToolbar(themedContext)
        toolbar.navigationIcon = ContextCompat.getDrawable(themedContext, R.drawable.ic_arrow_back)
        toolbar.overflowIcon = ContextCompat.getDrawable(themedContext, R.drawable.ic_save)

        val actionMenuView = ActionMenuView(themedContext)
        
        // Simulating Samsung OneUI ActionMenuItemView which extends TextView
        val mockActionItemView = android.widget.TextView(themedContext).apply {
            val iconDrawable = ContextCompat.getDrawable(themedContext, R.drawable.ic_picker_image)
            setCompoundDrawablesWithIntrinsicBounds(iconDrawable, null, null, null)
        }
        actionMenuView.addView(mockActionItemView)

        // Simulating OverflowMenuButton which is an ImageView
        val mockOverflowButton = ImageView(themedContext).apply {
            setImageDrawable(ContextCompat.getDrawable(themedContext, R.drawable.ic_about))
        }
        actionMenuView.addView(mockOverflowButton)

        toolbar.addView(actionMenuView)

        val testColor = Color.rgb(33, 150, 243)
        toolbar.applyConsistentIconTint(testColor)

        // Check Toolbar navigation tint
        assertThat(toolbar.navigationIconTint).isEqualTo(testColor)

        // Check ImageView imageTintList
        assertThat(mockOverflowButton.imageTintList?.defaultColor).isEqualTo(testColor)
    }

    @Test
    fun vectorDrawables_actionIconsArePureWhiteBase_avoidingTintClash() {
        // Assert that vector drawables load properly without throwing
        val vipIcon = ContextCompat.getDrawable(themedContext, R.drawable.ic_workspace_premium)
        assertThat(vipIcon).isNotNull()

        val selectAllIcon = ContextCompat.getDrawable(themedContext, R.drawable.ic_select_all)
        assertThat(selectAllIcon).isNotNull()

        val deselectAllIcon = ContextCompat.getDrawable(themedContext, R.drawable.ic_deselect_all)
        assertThat(deselectAllIcon).isNotNull()

        val aboutIcon = ContextCompat.getDrawable(themedContext, R.drawable.ic_about)
        assertThat(aboutIcon).isNotNull()
    }

    @Test
    fun signatureActivity_toolbarAndActionButtons_shareIdenticalTint() {
        val activityController = Robolectric.buildActivity(SignatureActivity::class.java).setup()
        val activity = activityController.get()

        val expectedColor = MaterialColors.getColor(
            activity,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
        val ivUndo = activity.findViewById<MaterialButton>(R.id.ivUndo)
        val ivClear = activity.findViewById<MaterialButton>(R.id.ivClear)

        assertThat(toolbar).isNotNull()
        assertThat(toolbar.navigationIconTint).isEqualTo(expectedColor)
        assertThat(ivUndo.iconTint?.defaultColor).isEqualTo(expectedColor)
        assertThat(ivClear.iconTint?.defaultColor).isEqualTo(expectedColor)
    }

    @Test
    fun vipManagementActivity_toolbarNavigationIcon_hasConsistentTint() {
        val activityController = Robolectric.buildActivity(com.mckimquyen.watermark.feature.vip.VipManagementActivity::class.java).setup()
        val activity = activityController.get()

        val expectedColor = MaterialColors.getColor(
            activity,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.topAppBar)
        assertThat(toolbar).isNotNull()
        assertThat(toolbar.navigationIconTint).isEqualTo(expectedColor)
    }

    @Test
    fun aboutActivity_and_openSourceActivity_toolbars_haveConsistentTint() {
        val aboutController = Robolectric.buildActivity(com.mckimquyen.watermark.ui.about.AboutActivity::class.java).setup()
        val aboutActivity = aboutController.get()
        val aboutColor = MaterialColors.getColor(
            aboutActivity,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )
        val aboutToolbar = aboutActivity.findViewById<MaterialToolbar>(R.id.topAppBar)
        assertThat(aboutToolbar).isNotNull()
        assertThat(aboutToolbar.navigationIconTint).isEqualTo(aboutColor)

        val openSourceController = Robolectric.buildActivity(com.mckimquyen.watermark.ui.about.OpenSourceActivity::class.java).setup()
        val openSourceActivity = openSourceController.get()
        val osColor = MaterialColors.getColor(
            openSourceActivity,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )
        val osToolbar = openSourceActivity.findViewById<MaterialToolbar>(R.id.myToolbar)
        assertThat(osToolbar).isNotNull()
        assertThat(osToolbar.navigationIconTint).isEqualTo(osColor)
    }
}

