package com.mckimquyen.watermark.ui

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
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
    fun `cold shared image requests consent before preloading interstitial`() {
        mockkObject(AdManager)
        val consentCallback = io.mockk.slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(consentCallback)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/image.jpg"))
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent)

            controller.create().start()
            verify(exactly = 0) { AdManager.loadInterstitial(any()) }
            consentCallback.captured(true)
            verify(exactly = 1) { AdManager.loadInterstitial(any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `cold shared image callback after destroy does not preload interstitial`() {
        mockkObject(AdManager)
        val consentCallback = io.mockk.slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(consentCallback)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/image.jpg"))
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            controller.create().start()
            controller.destroy()

            consentCallback.captured(true)

            verify(exactly = 0) { AdManager.loadInterstitial(any()) }
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `shared image onNewIntent callback after destroy does not preload interstitial`() {
        mockkObject(AdManager)
        val consentCallback = io.mockk.slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(consentCallback)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            controller.create().start()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/new-image.png"))
            }
            controller.newIntent(intent)
            controller.destroy()

            consentCallback.captured(true)

            verify(exactly = 1) { AdManager.loadInterstitial(any()) }
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `cold shared image does not preload when consent denies ad requests`() {
        mockkObject(AdManager)
        val consentCallback = io.mockk.slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(consentCallback)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/image.jpg"))
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent)

            controller.create().start()
            consentCallback.captured(false)

            verify(exactly = 0) { AdManager.loadInterstitial(any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `restored shared image requests consent before preloading interstitial`() {
        mockkObject(AdManager)
        val consentCallback = io.mockk.slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(consentCallback)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/restored-image.jpg"))
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent)

            controller.create(android.os.Bundle()).start()

            verify(exactly = 1) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }
            verify(exactly = 0) { AdManager.loadInterstitial(any()) }
            consentCallback.captured(true)
            verify(exactly = 1) { AdManager.loadInterstitial(any()) }

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
    fun `image share through onNewIntent only preloads again after consent callback`() {
        mockkObject(AdManager)
        val consentCallback = io.mockk.slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(consentCallback)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            controller.create().start()
            verify(exactly = 1) { AdManager.loadInterstitial(any()) }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/new-image.png"))
            }

            controller.newIntent(intent)
            verify(exactly = 1) { AdManager.loadInterstitial(any()) }
            consentCallback.captured(true)
            verify(exactly = 2) { AdManager.loadInterstitial(any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `image share through onNewIntent does not preload when consent denies ad requests`() {
        mockkObject(AdManager)
        val consentCallback = io.mockk.slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(consentCallback)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            controller.create().start()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/new-image.png"))
            }

            controller.newIntent(intent)
            consentCallback.captured(false)

            // Chỉ còn lần preload thường ở onCreate; deny không được thêm lần nào.
            verify(exactly = 1) { AdManager.loadInterstitial(any()) }

            controller.destroy()
        } finally {
            unmockkObject(AdManager)
        }
    }

    @Test
    fun `recreated shared image ignores stale callback and preloads once from live activity`() {
        mockkObject(AdManager)
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(callbacks)) } returns Unit
        every { AdManager.loadInterstitial(any()) } returns Unit
        try {
            // Intent mới cho mỗi Activity: hệ thống cũng giao bản sao khi recreate.
            fun shareIntent() = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://test/rotate-image.jpg"))
            }
            val first = Robolectric.buildActivity(MainActivity::class.java, shareIntent())
            first.create().start()
            first.destroy() // xoay màn hình / system recreate khi consent còn chờ

            val second = Robolectric.buildActivity(MainActivity::class.java, shareIntent())
            second.create(android.os.Bundle()).start()

            verify(exactly = 2) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }
            assertThat(callbacks).hasSize(2)
            callbacks[0](true) // callback cũ của Activity đã huỷ
            verify(exactly = 0) { AdManager.loadInterstitial(any()) }
            callbacks[1](true)
            verify(exactly = 1) { AdManager.loadInterstitial(any()) }

            second.destroy()
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
