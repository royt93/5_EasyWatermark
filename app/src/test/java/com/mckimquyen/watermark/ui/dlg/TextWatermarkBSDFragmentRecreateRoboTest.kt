package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-67: `onViewCreated` luôn `add(EditTextContentFragment())` + `addToBackStack`, không kiểm tra
 * `savedInstanceState`. `MainActivity` chỉ khai `configChanges="orientation|keyboardHidden"` nên đổi
 * dark mode / font scale / locale / process death recreate Activity: `childFragmentManager` đã
 * restore child cũ, `onViewCreated` add thêm cái nữa → 2 EditText chồng nhau, back phải bấm 2 lần.
 */
@RunWith(RobolectricTestRunner::class)
class TextWatermarkBSDFragmentRecreateRoboTest {

    companion object {
        lateinit var testViewModel: MainViewModel
        const val CONTAINER_ID = 0x7f0f0001
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }

        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
            // id cố định để FragmentManager restore đúng container sau recreate.
            setContentView(FrameLayout(this).apply { id = CONTAINER_ID })
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val waterMarkDataStore = newTestWaterMarkDataStore(context)
        val userDataStore = newTestUserDataStore(context)
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
        )
    }

    private fun editFragmentCount(fragment: TextWatermarkBSDFragment): Int =
        fragment.childFragmentManager.fragments.count { it is EditTextContentFragment }

    @Test
    fun recreateActivity_whileDialogOpen_keepsExactlyOneEditTextContentFragment() {
        val controller = Robolectric.buildActivity(TestHostActivity::class.java).setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(CONTAINER_ID, TextWatermarkBSDFragment().apply { setShowsDialog(false) }, TextWatermarkBSDFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        val before = controller.get().supportFragmentManager.findFragmentByTag(TextWatermarkBSDFragment.TAG)
            as TextWatermarkBSDFragment
        assertThat(editFragmentCount(before)).isEqualTo(1)

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()

        val after = controller.get().supportFragmentManager.findFragmentByTag(TextWatermarkBSDFragment.TAG)
            as TextWatermarkBSDFragment
        assertThat(editFragmentCount(after)).isEqualTo(1)
        assertThat(after.childFragmentManager.backStackEntryCount).isEqualTo(1)
        assertThat(after.requireView().findViewById<android.view.View>(R.id.fragmentContainerView)).isNotNull()
    }
}
