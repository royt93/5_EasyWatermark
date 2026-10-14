package com.mckimquyen.watermark.ads

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/** Xác nhận Android thật resolve share ảnh vào MainActivity mà không mở Activity/UMP/ad SDK. */
@RunWith(AndroidJUnit4::class)
class ShareIntentManifestIntegrationTest {

    @Test
    fun imageSendIntent_resolvesToExportedSingleTaskMainActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg" }
        val matches = context.packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val mainActivity = matches.singleOrNull { it.activityInfo.name == MainActivity::class.java.name }

        assertThat(mainActivity).isNotNull()
        val activityInfo = requireNotNull(mainActivity).activityInfo
        assertThat(activityInfo.exported).isTrue()
        assertThat(activityInfo.launchMode).isEqualTo(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK)
    }

    @Test
    fun textSendIntent_doesNotResolveToMainActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        val resolved = context.packageManager.resolveActivity(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)

        assertThat(resolved?.activityInfo?.name).isNotEqualTo(MainActivity::class.java.name)
    }
}
