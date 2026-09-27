package com.mckimquyen.watermark.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * IDEA-14: [SmartRedactionActivity] — mirror [CropActivityRoboTest] về giới hạn môi trường: chỉ
 * cover phần đồng bộ (toolbar, trạng thái ban đầu trước khi detect xong), KHÔNG cover việc ML Kit
 * detect thật xong xuôi — detector thật (`MlKitFaceDetectionSource`/`MlKitSensitiveTextSource`,
 * cả 2 lấy qua Hilt field injection, `MyApplication` khởi tạo Hilt thật kể cả dưới Robolectric,
 * xem `MainActivityInterstitialRoboTest`) cần native lib thật không tồn tại trong JVM test — case
 * "detect xong, nút Apply enable, đúng danh sách rects" verify bằng smoke test thật trên device
 * (cùng tinh thần giới hạn đã ghi nhận ở `MlKitFaceDetectionSourceIntegrationTest`/
 * `CropActivityRoboTest.loadImage_decodeSucceeds_...`).
 */
@RunWith(RobolectricTestRunner::class)
class SmartRedactionActivityRoboTest {

    private fun applicationContext() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun onCreate_missingImageUri_finishesImmediately() {
        val controller = Robolectric.buildActivity(SmartRedactionActivity::class.java, Intent()).create()
        assertThat(controller.get().isFinishing).isTrue()
    }

    @Test
    fun onCreate_withImageUri_toolbarShowsCorrectTitle() {
        val activity = Robolectric.buildActivity(
            SmartRedactionActivity::class.java,
            Intent(applicationContext(), SmartRedactionActivity::class.java)
                .putExtra(SmartRedactionActivity.EXTRA_IMAGE_URI, "content://media/test.jpg")
        ).create().get()

        val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        assertThat(toolbar.title.toString()).isEqualTo(activity.getString(R.string.redaction_title))
    }

    @Test
    fun toolbarBackNavigation_finishesActivity() {
        val activity = Robolectric.buildActivity(
            SmartRedactionActivity::class.java,
            Intent(applicationContext(), SmartRedactionActivity::class.java)
                .putExtra(SmartRedactionActivity.EXTRA_IMAGE_URI, "content://media/test.jpg")
        ).create().get()

        val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val navButton = (0 until toolbar.childCount)
            .map { toolbar.getChildAt(it) }
            .firstOrNull { it is androidx.appcompat.widget.AppCompatImageButton }
            ?: error("navigation button view not found")
        navButton.performClick()

        assertThat(activity.isFinishing).isTrue()
    }

    /**
     * Bất biến quan trọng nhất trước khi ML Kit detect xong: nút Apply PHẢI bị khoá — nếu không,
     * user có thể Apply với danh sách rỗng trong lúc đang quét, tưởng nhầm là "không có gì nhạy
     * cảm" trong khi thực ra chỉ là CHƯA quét xong (giống bug đã fix ở `CropActivity`/btnApplyCrop).
     */
    @Test
    fun onCreate_beforeDetectionCompletes_applyButtonDisabled_progressVisible() {
        val activity = Robolectric.buildActivity(
            SmartRedactionActivity::class.java,
            Intent(applicationContext(), SmartRedactionActivity::class.java)
                .putExtra(SmartRedactionActivity.EXTRA_IMAGE_URI, "content://media/test.jpg")
        ).create().get()

        val btn = activity.findViewById<MaterialButton>(R.id.btnApplyRedaction)
        val progress = activity.findViewById<CircularProgressIndicator>(R.id.pbScanning)
        assertThat(btn.isEnabled).isFalse()
        assertThat(progress.visibility).isEqualTo(android.view.View.VISIBLE)
    }

    @Test
    fun createIntent_carriesUriCropRectAndRotation() {
        val uri = Uri.parse("content://media/test.jpg")
        val cropRect = android.graphics.RectF(0.1f, 0.1f, 0.9f, 0.9f)

        val intent = SmartRedactionActivity.createIntent(applicationContext(), uri, cropRect, 15f)

        assertThat(intent.getStringExtra(SmartRedactionActivity.EXTRA_IMAGE_URI)).isEqualTo(uri.toString())
        assertThat(intent.getFloatExtra(SmartRedactionActivity.EXTRA_ROTATION, 0f)).isEqualTo(15f)
        val readBackCropRect = androidx.core.content.IntentCompat.getParcelableExtra(
            intent,
            SmartRedactionActivity.EXTRA_CROP_RECT,
            android.graphics.RectF::class.java
        )
        assertThat(readBackCropRect).isEqualTo(cropRect)
    }

    /** ContentProvider giả lập ĐÚNG lỗi thật gặp qua smoke test: từ chối quyền đọc. */
    class PermissionDeniedProvider : ContentProvider() {
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            throw SecurityException("$AUTHORITY has no access to $uri")
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        companion object {
            const val AUTHORITY = "smart.redaction.permission.denied.test"
        }
    }

    /**
     * BUG thật phát hiện qua smoke test trên device: ảnh nhận qua `ACTION_SEND` (chia sẻ từ app
     * khác) chỉ mang quyền đọc URI TẠM THỜI gắn với Activity ĐẦU TIÊN nhận nó — mở
     * `SmartRedactionActivity` bằng CHÍNH uri đó (không có persistable permission) khiến
     * `ContentResolver.openInputStream()` ném `SecurityException` thẳng, làm CRASH toàn app (xác
     * nhận qua "Crash detected" dialog + stack trace thật trong SharedPreferences trên device).
     * Test này khoá đúng bất biến: lỗi quyền truy cập KHÔNG ĐƯỢC crash, chỉ đóng màn hình.
     */
    @Test
    fun onCreate_khongCoQuyenDocUri_khongCrash_dongManHinh() {
        Robolectric.setupContentProvider(PermissionDeniedProvider::class.java, PermissionDeniedProvider.AUTHORITY)
        val uri = "content://${PermissionDeniedProvider.AUTHORITY}/photo.jpg"

        val activity = Robolectric.buildActivity(
            SmartRedactionActivity::class.java,
            Intent(applicationContext(), SmartRedactionActivity::class.java)
                .putExtra(SmartRedactionActivity.EXTRA_IMAGE_URI, uri)
        ).setup().get()

        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline && !activity.isFinishing) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(activity.isFinishing).isTrue()
    }
}
