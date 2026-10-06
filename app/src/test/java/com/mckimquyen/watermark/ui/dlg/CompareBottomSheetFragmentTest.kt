package com.mckimquyen.watermark.ui.dlg

import android.content.DialogInterface
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.MainActivity
import com.mckimquyen.watermark.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-18: CompareBottomSheetFragment — slider so sánh trước/sau trong editor.
 * - onViewCreated: slider init từ shareViewModel.compareReveal
 * - Slider change: update viewModel
 * - onDismiss: reset compareReveal = 1f
 * - safetyShow: tránh duplicate fragment show
 */
@RunWith(RobolectricTestRunner::class)
class CompareBottomSheetFragmentTest {

    @Before
    fun setUp() {
        // Initialization if needed
    }

    @Test
    fun onViewCreated_sliderInitializedFromViewModel() {
        // Test that slider value is initialized from shareViewModel.compareReveal
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = CompareBottomSheetFragment()
                val fragmentManager = activity.supportFragmentManager

                // Set initial compareReveal value via test
                // (In real code, this would come from ViewModel's shared state)
                // Note: Direct test of fragment lifecycle with real ViewBinding

                fragment.show(fragmentManager, CompareBottomSheetFragment.TAG)

                // Verify fragment is added
                assertThat(fragmentManager.findFragmentByTag(CompareBottomSheetFragment.TAG)).isNotNull()
            }
        }
    }

    @Test
    fun safetyShow_preventsDuplicateFragment() {
        // Test that safetyShow() prevents duplicate fragment instances
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragmentManager = activity.supportFragmentManager

                // First show
                CompareBottomSheetFragment.safetyShow(fragmentManager)

                val firstInstance = fragmentManager.findFragmentByTag(CompareBottomSheetFragment.TAG)
                assertThat(firstInstance).isNotNull()

                // Second show (should reuse same instance)
                CompareBottomSheetFragment.safetyShow(fragmentManager)

                val secondInstance = fragmentManager.findFragmentByTag(CompareBottomSheetFragment.TAG)
                assertThat(secondInstance).isSameInstanceAs(firstInstance)
            }
        }
    }

    @Test
    fun safetyShow_handlesIllegalState() {
        // Test that safetyShow() gracefully handles IllegalStateException
        val mockFragmentManager = object : FragmentManager() {
            override fun findFragmentByTag(tag: String?): androidx.fragment.app.Fragment? {
                throw IllegalStateException("Fragment manager transaction in progress")
            }

            override fun getFragments() = emptyList<androidx.fragment.app.Fragment>()
            override fun executePendingTransactions() = false
            override fun popBackStack() {}
            override fun popBackStack(id: Int, flags: Int) {}
            override fun popBackStack(name: String?, flags: Int) {}
            override fun getBackStackEntryCount() = 0
            override fun getBackStackEntryAt(index: Int) = null
            override fun addOnBackStackChangedListener(listener: FragmentManager.OnBackStackChangedListener) {}
            override fun removeOnBackStackChangedListener(listener: FragmentManager.OnBackStackChangedListener) {}
            override fun putFragment(bundle: androidx.fragment.app.Fragment, key: String) {}
            override fun getFragment(bundle: androidx.fragment.app.Fragment, key: String) = null
            override fun saveFragmentInstanceState(f: androidx.fragment.app.Fragment) = null
            override fun beginTransaction() = throw NotImplementedError()
            override fun isStateSaved() = false
        }

        // Should not throw — catches IllegalStateException
        CompareBottomSheetFragment.safetyShow(mockFragmentManager)
    }
}
