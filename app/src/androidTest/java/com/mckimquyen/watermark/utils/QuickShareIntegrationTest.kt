package com.mckimquyen.watermark.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-27 integration test trên Android thật: PackageManager bị package-visibility giới hạn từ API
 * 30, nên phải kiểm chứng `<queries>` thực sự cho phép thấy app đích và intent giữ đủ URI permission.
 */
@RunWith(AndroidJUnit4::class)
class QuickShareIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun resolveInstalled_returnsOnlyResolvableKnownTargetsAndEveryTargetAcceptsShareIntent() {
        val targets = QuickShareHelper.resolveInstalled(context.packageManager)

        assertThat(targets.map { it.packageName }).containsNoDuplicates()
        // Chỉ chứa app trong danh sách đã biết, theo đúng thứ tự khai báo.
        val installedInKnownOrder = QuickShareHelper.KNOWN_PACKAGES.filter { pkg -> targets.any { it.packageName == pkg } }
        assertThat(targets.map { it.packageName }).containsExactlyElementsIn(installedInKnownOrder).inOrder()
        targets.forEach { target ->
            val resolved = context.packageManager.resolveActivity(QuickShareHelper.probeIntent(target.packageName), 0)
            assertThat(resolved).isNotNull()
        }
    }

    @Test
    fun buildShareIntent_singleAndMultipleUris_preservesUrisClipDataAndReadGrant() {
        val one = Uri.parse("content://com.mckimquyen.watermark.test/out/1.jpg")
        val two = Uri.parse("content://com.mckimquyen.watermark.test/out/2.jpg")

        val single = QuickShareHelper.buildShareIntent(context.contentResolver, listOf(one), "com.zing.zalo")
        assertThat(single.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(IntentCompat.getParcelableExtra(single, Intent.EXTRA_STREAM, Uri::class.java)).isEqualTo(one)
        assertThat(single.clipData?.itemCount).isEqualTo(1)
        assertThat(single.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)

        val multiple = QuickShareHelper.buildShareIntent(context.contentResolver, listOf(one, two), "com.zing.zalo")
        assertThat(multiple.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
        assertThat(IntentCompat.getParcelableArrayListExtra(multiple, Intent.EXTRA_STREAM, Uri::class.java))
            .containsExactly(one, two)
            .inOrder()
        assertThat(multiple.clipData?.itemCount).isEqualTo(2)
        assertThat(multiple.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }
}
