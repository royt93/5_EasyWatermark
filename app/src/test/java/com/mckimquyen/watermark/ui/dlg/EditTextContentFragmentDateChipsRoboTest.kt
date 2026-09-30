package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import com.mckimquyen.watermark.ui.MainViewModel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-25 Widget Test: Kiểm chứng chip token {time} và {datetime} trong [EditTextContentFragment].
 */
@RunWith(RobolectricTestRunner::class)
class EditTextContentFragmentDateChipsRoboTest {

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
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
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
        val group = requireView().findViewById<ChipGroup>(R.id.cgTokens)
        return (0 until group.childCount).map { group.getChildAt(it) as Chip }.first { it.text == "{$token}" }
    }

    private fun EditTextContentFragment.editText(): android.widget.EditText = requireView().findViewById(R.id.etWaterText)

    @Test
    fun timeChip_isAvailable_andInsertsTokenIntoEditText() {
        val (_, fragment) = launch()

        val chip = fragment.chip("time")
        assertThat(chip).isNotNull()

        chip.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.editText().text.toString()).contains("{time}")
    }

    @Test
    fun datetimeChip_isAvailable_andInsertsTokenIntoEditText() {
        val (_, fragment) = launch()

        val chip = fragment.chip("datetime")
        assertThat(chip).isNotNull()

        chip.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.editText().text.toString()).contains("{datetime}")
    }
}
