package com.mckimquyen.watermark.ui

import android.content.Intent
import android.net.Uri
import com.mckimquyen.watermark.data.model.Result
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityInterstitialRoboTest {

    private fun MainActivity.viewModelField(): MainViewModel =
        MainActivity::class.java.getDeclaredMethod("getViewModel").apply { isAccessible = true }
            .invoke(this) as MainViewModel

    @Test
    fun `cold shared image intent requests UMP consent`() {
        mockkObject(AdManager)
        every { AdManager.requestConsentInfoUpdate(any(), any(), any()) } returns Unit
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/image.jpg"))
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            controller.create().start()

            verify(exactly = 1) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `non-image new intent does not request UMP consent`() {
        mockkObject(AdManager)
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            controller.create().start()
            controller.newIntent(Intent(Intent.ACTION_SEND).apply { type = "text/plain" })

            verify(exactly = 0) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `cold non-image share with URI does not request UMP consent`() {
        mockkObject(AdManager)
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/image.jpg"))
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            controller.create().start()

            verify(exactly = 0) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `image share through onNewIntent requests UMP consent`() {
        mockkObject(AdManager)
        every { AdManager.requestConsentInfoUpdate(any(), any(), any()) } returns Unit
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            controller.create().start()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/new-image.png"))
            }

            controller.newIntent(intent)

            verify(exactly = 1) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `ordinary launch does not request UMP consent from MainActivity`() {
        mockkObject(AdManager)
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            controller.create().start()

            verify(exactly = 0) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `job finish does not automatically show interstitial`() {
        mockkObject(AdManager)
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            val activity = controller.create().start().get()
            activity.viewModelField().saveResult.value =
                Result.success(code = MainViewModel.TYPE_JOB_FINISH, data = null)

            verify(exactly = 0) { AdManager.showInterstitial(any(), any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }
}
