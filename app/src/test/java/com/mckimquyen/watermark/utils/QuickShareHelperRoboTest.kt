package com.mckimquyen.watermark.utils

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** FEAT-27: dựng intent chia sẻ + lọc app cài sẵn có activity nhận ảnh. */
@RunWith(RobolectricTestRunner::class)
class QuickShareHelperRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val one = Uri.parse("content://output/1.jpg")
    private val two = Uri.parse("content://output/2.jpg")

    @Test
    fun buildShareIntent_singleUri_isActionSendWithPackage() {
        val intent = QuickShareHelper.buildShareIntent(context.contentResolver, listOf(one), "org.telegram.messenger")

        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.`package`).isEqualTo("org.telegram.messenger")
        assertThat(intent.type).isEqualTo("image/*")
        assertThat(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).isEqualTo(one)
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    @Test
    fun buildShareIntent_multipleUris_isActionSendMultipleKeepingOrderAndClipData() {
        val intent = QuickShareHelper.buildShareIntent(context.contentResolver, listOf(one, two), "com.zing.zalo")

        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
        assertThat(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).containsExactly(one, two).inOrder()
        assertThat(intent.clipData?.itemCount).isEqualTo(2)
    }

    @Test
    fun buildShareIntent_noPackage_leavesPackageNullForSharesheet() {
        val intent = QuickShareHelper.buildShareIntent(context.contentResolver, listOf(one))
        assertThat(intent.`package`).isNull()
    }

    @Test
    fun buildShareIntent_emptyList_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            QuickShareHelper.buildShareIntent(context.contentResolver, emptyList())
        }
    }

    @Test
    fun resolveInstalled_returnsOnlyAppsThatAcceptImagesInGivenOrder() {
        val pm = shadowOf(context.packageManager)
        // Chỉ Telegram + Zalo có activity nhận ảnh; Messenger/Drive không cài.
        listOf("org.telegram.messenger", "com.zing.zalo").forEach { pkg ->
            val info = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = pkg
                    name = "$pkg.ShareActivity"
                    applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
                }
            }
            pm.addResolveInfoForIntent(QuickShareHelper.probeIntent(pkg), info)
        }

        val result = QuickShareHelper.resolveInstalled(context.packageManager)

        // Thứ tự theo KNOWN_PACKAGES (Zalo trước Telegram), không theo thứ tự đăng ký.
        assertThat(result.map { it.packageName }).containsExactly("com.zing.zalo", "org.telegram.messenger").inOrder()
    }

    @Test
    fun resolveInstalled_nothingInstalled_returnsEmpty() {
        assertThat(QuickShareHelper.resolveInstalled(context.packageManager)).isEmpty()
    }

    @Test
    fun knownPackages_matchManifestQueries() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        QuickShareHelper.KNOWN_PACKAGES.forEach { pkg ->
            assertThat(manifest).contains("<package android:name=\"$pkg\" />")
        }
    }
}
