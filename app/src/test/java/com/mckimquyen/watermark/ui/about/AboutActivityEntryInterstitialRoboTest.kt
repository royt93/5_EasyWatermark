package com.mckimquyen.watermark.ui.about

import android.os.Build
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AboutActivityEntryInterstitialRoboTest {
    @Before
    fun setUp() {
        mockkObject(AdManager)
        every { AdManager.loadBanner(any(), any(), any(), any(), any()) } returns null
        every { AdManager.showInterstitial(any(), any()) } returns Unit
    }

    @After
    fun tearDown() = unmockkObject(AdManager)

    @Test
    fun opening_about_showsInterstitial_once() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()

        verify(exactly = 1) { AdManager.showInterstitial(controller.get(), any()) }
    }

    @Test
    fun recreatedAbout_doesNotShowInterstitialAgain() {
        val first = Robolectric.buildActivity(AboutActivity::class.java).setup()
        first.recreate()

        verify(exactly = 1) { AdManager.showInterstitial(any(), any()) }
    }
}
