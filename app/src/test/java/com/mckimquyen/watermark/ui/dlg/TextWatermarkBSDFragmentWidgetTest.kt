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
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-30: Widget test cho TextWatermarkBSDFragment đảm bảo dialog container
 * khởi tạo và inflate thành công child fragment (EditTextContentFragment)
 * mà không bị NPE hay crash liên quan tới field et thừa trước đây.
 */
@RunWith(RobolectricTestRunner::class)
class TextWatermarkBSDFragmentWidgetTest {

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
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    @Test
    fun testTextWatermarkBSDFragmentInflationAndChildAttachment() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = TextWatermarkBSDFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, TextWatermarkBSDFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.isAdded).isTrue()
        val containerView = fragment.requireView().findViewById<androidx.fragment.app.FragmentContainerView>(
            R.id.fragmentContainerView
        )
        assertThat(containerView).isNotNull()

        val childFragment = fragment.childFragmentManager.findFragmentById(R.id.fragmentContainerView)
            ?: fragment.childFragmentManager.fragments.firstOrNull()
        assertThat(childFragment).isNotNull()
        assertThat(childFragment).isInstanceOf(EditTextContentFragment::class.java)
    }

    @Test
    fun testSafetyShow() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        TextWatermarkBSDFragment.safetyShow(activity.supportFragmentManager)
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = activity.supportFragmentManager.findFragmentByTag(TextWatermarkBSDFragment.TAG)
        assertThat(fragment).isNotNull()
        assertThat(fragment).isInstanceOf(TextWatermarkBSDFragment::class.java)
    }
}
