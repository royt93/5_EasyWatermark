package com.mckimquyen.watermark.ui.dlg

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Looper
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.chip.Chip
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * IDEA-16 widget test: chip {location} có trong danh sách chip token, bấm vào chèn token VÀ xin
 * `ACCESS_MEDIA_LOCATION` (Android 10+ redact GPS nếu thiếu quyền); chip khác không xin quyền;
 * đã có quyền thì không hỏi lại.
 */
@RunWith(RobolectricTestRunner::class)
class EditTextContentFragmentLocationChipRoboTest {

    companion object {
        lateinit var testViewModel: MainViewModel
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(newTestUserDataStore(context)),
            waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context)),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    private fun launch(): Pair<FragmentActivity, EditTextContentFragment> {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val container = FrameLayout(activity).apply { id = android.view.View.generateViewId() }
        activity.setContentView(container)
        val fragment = EditTextContentFragment()
        activity.supportFragmentManager.beginTransaction().add(container.id, fragment).commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    private fun EditTextContentFragment.chip(token: String): Chip {
        val group = requireView().findViewById<com.google.android.material.chip.ChipGroup>(R.id.cgTokens)
        return (0 until group.childCount).map { group.getChildAt(it) as Chip }.first { it.text == "{$token}" }
    }

    private fun EditTextContentFragment.editText(): android.widget.EditText = requireView().findViewById(R.id.etWaterText)

    @Test
    fun locationChip_insertsToken_andRequestsMediaLocationPermission() {
        val (activity, fragment) = launch()

        fragment.chip("location").performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.editText().text.toString()).contains("{location}")
        assertThat(shadowOf(activity).lastRequestedPermission?.requestedPermissions)
            .asList().containsExactly(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    @Test
    fun otherChip_doesNotRequestPermission() {
        val (activity, fragment) = launch()

        fragment.chip("date").performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(shadowOf(activity).lastRequestedPermission).isNull()
    }

    @Test
    fun locationChip_permissionAlreadyGranted_doesNotAskAgain() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
        val (activity, fragment) = launch()

        fragment.chip("location").performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(shadowOf(activity).lastRequestedPermission).isNull()
    }

    @Test
    fun isMediaLocationPermissionRequired_onlyLocationToken_onQPlus() {
        assertThat(EditTextContentFragment.isMediaLocationPermissionRequired("location", Build.VERSION_CODES.Q)).isTrue()
        assertThat(EditTextContentFragment.isMediaLocationPermissionRequired("location", Build.VERSION_CODES.P)).isFalse()
        assertThat(EditTextContentFragment.isMediaLocationPermissionRequired("date", Build.VERSION_CODES.TIRAMISU)).isFalse()
    }
}
