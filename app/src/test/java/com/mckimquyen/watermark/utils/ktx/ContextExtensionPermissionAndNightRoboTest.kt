package com.mckimquyen.watermark.utils.ktx

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Ma trận quyền lưu trữ theo API (R/W chỉ bắt buộc ghi dưới Q) + `isNight` theo uiMode. */
@RunWith(RobolectricTestRunner::class)
class ContextExtensionPermissionAndNightRoboTest {

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun grant(a: Activity, vararg perms: String) =
        shadowOf(a.application).grantPermissions(*perms)

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun api33_readMediaImagesOnly_isGranted() {
        val a = activity()
        assertThat(a.isStoragePermissionGrated()).isFalse()
        grant(a, Manifest.permission.READ_MEDIA_IMAGES)
        assertThat(a.isStoragePermissionGrated()).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun api33_legacyReadPermission_isNotEnough() {
        val a = activity()
        grant(a, Manifest.permission.READ_EXTERNAL_STORAGE)
        assertThat(a.isStoragePermissionGrated()).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun api29_readOnly_isGranted_writeNotRequired() {
        val a = activity()
        grant(a, Manifest.permission.READ_EXTERNAL_STORAGE)
        assertThat(a.isStoragePermissionGrated()).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun api28_needsBothReadAndWrite() {
        val a = activity()
        grant(a, Manifest.permission.READ_EXTERNAL_STORAGE)
        assertThat(a.isStoragePermissionGrated()).isFalse()
        grant(a, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertThat(a.isStoragePermissionGrated()).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun api28_writeOnly_isNotGranted() {
        val a = activity()
        grant(a, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertThat(a.isStoragePermissionGrated()).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun preCheck_granted_runsBlock_withoutRequestingPermission() {
        val a = activity()
        grant(a, Manifest.permission.READ_MEDIA_IMAGES)
        var ran = false
        a.preCheckStoragePermission { ran = true }
        assertThat(ran).isTrue()
        assertThat(shadowOf(a).lastRequestedPermission).isNull()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun preCheck_denied_skipsBlock_andRequestsReadMediaImages() {
        val a = activity()
        var ran = false
        a.preCheckStoragePermission { ran = true }
        assertThat(ran).isFalse()
        val req = shadowOf(a).lastRequestedPermission
        assertThat(req.requestCode).isEqualTo(MainActivity.REQ_CODE_REQ_WRITE_PERMISSION)
        assertThat(req.requestedPermissions.toList()).contains(Manifest.permission.READ_MEDIA_IMAGES)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun preCheck_denied_legacyApi_requestsReadAndWriteExternal() {
        val a = activity()
        a.preCheckStoragePermission { }
        val req = shadowOf(a).lastRequestedPermission
        assertThat(req.requestedPermissions.toList()).containsExactly(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    @Test
    fun isNight_followsUiModeMask() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun ctx(mode: Int) = base.createConfigurationContext(
            Configuration(base.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            }
        )
        assertThat(ctx(Configuration.UI_MODE_NIGHT_YES).isNight()).isTrue()
        assertThat(ctx(Configuration.UI_MODE_NIGHT_NO).isNight()).isFalse()
        assertThat(ctx(Configuration.UI_MODE_NIGHT_UNDEFINED).isNight()).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun supportNight_falseBelowQ() = assertThat(supportNight()).isFalse()

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun supportNight_trueFromQ() = assertThat(supportNight()).isTrue()
}
