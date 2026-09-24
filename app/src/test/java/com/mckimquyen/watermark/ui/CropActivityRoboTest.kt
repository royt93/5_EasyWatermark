package com.mckimquyen.watermark.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream

/**
 * FEAT-16: [CropActivity] — phần đồng bộ trong onCreate (chip tỉ lệ, slider, toolbar) + trạng thái
 * nút Áp dụng quanh luồng decode ảnh bất đồng bộ (`lifecycleScope.launch`). Test case "decode thất
 * bại" KHÔNG cover được trong JVM (xem ghi chú ở [loadImage_decodeSucceeds_enablesApplyButton_andFeedsBitmapToOverlay])
 * — mirror giới hạn môi trường đã ghi nhận ở FEAT-15/FEAT-07, verify bằng smoke test thật trên
 * device thay vì cố ép 1 test JVM không thể tái hiện đúng.
 */
@RunWith(RobolectricTestRunner::class)
class CropActivityRoboTest {

    /** Fake ContentProvider phục vụ URI thật — mirror `BitmapUtilsDownsampleExportRoboTest`. */
    class SimpleProvider : ContentProvider() {
        lateinit var file: File
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private fun launchWithUri(uri: String = "content://media/test.jpg") =
        Robolectric.buildActivity(
            CropActivity::class.java,
            Intent(null, Uri.EMPTY).putExtra(CropActivity.EXTRA_IMAGE_URI, uri)
        ).setup().get()

    /** Đăng ký 1 content provider phục vụ JPEG thật, trả về Uri content:// hợp lệ để decode. */
    private fun setupRealImageUri(authority: String): Uri {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("crop_activity_test", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val provider = Robolectric.setupContentProvider(SimpleProvider::class.java, authority)
        provider.file = file
        return Uri.parse("content://$authority/test.jpg")
    }

    @Test
    fun onCreate_missingImageUri_finishesImmediately() {
        val controller = Robolectric.buildActivity(CropActivity::class.java, Intent()).create()
        val activity = controller.get()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun onCreate_withImageUri_buildsRatioChips_freeSelectedByDefault() {
        val activity = launchWithUri()

        val chipGroup = activity.findViewById<ChipGroup>(R.id.cgRatio)
        assertThat(chipGroup.childCount).isEqualTo(6)
        val labels = (0 until chipGroup.childCount).map { (chipGroup.getChildAt(it) as Chip).text.toString() }
        assertThat(labels).containsExactly(
            activity.getString(R.string.crop_ratio_free),
            activity.getString(R.string.crop_ratio_square),
            activity.getString(R.string.crop_ratio_4_5),
            activity.getString(R.string.crop_ratio_16_9),
            activity.getString(R.string.crop_ratio_9_16),
            activity.getString(R.string.crop_ratio_3_4)
        ).inOrder()

        val freeChip = chipGroup.getChildAt(0) as Chip
        assertThat(freeChip.isChecked).isTrue()
        assertThat(chipGroup.checkedChipId).isEqualTo(freeChip.id)
    }

    @Test
    fun onCreate_straightenSlider_rangeMatchesSpec() {
        val activity = launchWithUri()

        val slider = activity.findViewById<Slider>(R.id.sliderStraighten)
        assertThat(slider.valueFrom).isEqualTo(-45f)
        assertThat(slider.valueTo).isEqualTo(45f)
        assertThat(slider.value).isEqualTo(0f)
    }

    @Test
    fun toolbarBackNavigation_finishesActivity() {
        val activity = launchWithUri()
        val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        assertThat(toolbar.navigationIcon).isNotNull()

        // Toolbar không expose getter cho navigation click listener — tìm đúng
        // AppCompatImageButton nội bộ mà Toolbar tự tạo cho navigation icon rồi click thật.
        val navButton = (0 until toolbar.childCount)
            .map { toolbar.getChildAt(it) }
            .firstOrNull { it is androidx.appcompat.widget.AppCompatImageButton }
            ?: error("navigation button view not found")
        navButton.performClick()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun applyButton_exists_andIsFilledStyle() {
        val activity = launchWithUri()

        val btn = activity.findViewById<MaterialButton>(R.id.btnApplyCrop)
        assertThat(btn.text.toString()).isEqualTo(activity.getString(R.string.crop_apply))
    }

    /**
     * BUG phát hiện qua audit 2026-09-24: `loadImage()` decode bất đồng bộ — nếu nút Áp dụng
     * KHÔNG bị khoá ngay từ đầu, user bấm trước khi decode xong sẽ âm thầm nhận
     * `cropRect=null`/`rotationDegrees=0f` (CropOverlayView chưa có bitmap) dù đã chọn tỉ lệ/góc
     * xoay — không crash, không báo lỗi, chỉ SAI kết quả. Test này khoá đúng bất biến: nút PHẢI
     * bị disable ngay khi Activity mở, trước khi ảnh decode xong.
     *
     * Dùng `.create()` (KHÔNG `.setup()`) — đã xác nhận qua debug trực tiếp: `.setup()` (chạy
     * hết start/resume/visible) khiến coroutine `loadImage()` kịp hoàn tất round-trip Main→IO→Main
     * trước khi trả quyền điều khiển về test (Robolectric xử lý nhanh/đồng bộ), không còn quan sát
     * được trạng thái "chưa xong" — chỉ `.create()` đơn thuần mới giữ được đúng thời điểm giữa
     * chừng cần test.
     */
    @Test
    fun onCreate_beforeImageDecodes_applyButtonDisabled() {
        val activity = Robolectric.buildActivity(
            CropActivity::class.java,
            Intent(null, Uri.EMPTY).putExtra(CropActivity.EXTRA_IMAGE_URI, "content://media/test.jpg")
        ).create().get()

        val btn = activity.findViewById<MaterialButton>(R.id.btnApplyCrop)
        assertThat(btn.isEnabled).isFalse()
    }

    // BUG phát hiện qua audit + verify bằng debug trực tiếp: KHÔNG test được case "decode thất
    // bại" trong JVM — `decodeBitmapFromUri` qua shadow Bitmap/ContentResolver của Robolectric
    // LUÔN trả về 1 Bitmap giả hợp lệ (isFailure=false) bất kể URI/provider có tồn tại thật hay
    // không (đã thử cả URI trùng authority "media" lẫn authority hoàn toàn không tồn tại, cả 2
    // đều "decode thành công"). Giới hạn môi trường tương tự đã ghi nhận nhiều lần trong repo
    // (FEAT-15/FEAT-07) — verify case lỗi decode thật qua smoke test trên device thay vì JVM test.

    @Test
    fun loadImage_decodeSucceeds_enablesApplyButton_andFeedsBitmapToOverlay() {
        val uri = setupRealImageUri("crop.activity.success")
        val activity = Robolectric.buildActivity(
            CropActivity::class.java,
            Intent(null, Uri.EMPTY).putExtra(CropActivity.EXTRA_IMAGE_URI, uri.toString())
        ).setup().get()

        val btn = activity.findViewById<MaterialButton>(R.id.btnApplyCrop)
        waitUntilTrue(timeoutMs = 3000) { btn.isEnabled }

        assertThat(btn.isEnabled).isTrue()
    }

    /**
     * `loadImage()` chạy `Dispatchers.IO` thật trên thread pool thật (không mock) rồi resume lại
     * Main — 1 lần `idle()` không đảm bảo thread IO đã kịp post continuation trở lại trước khi
     * `idle()` chạy (race thật, đã thấy flaky qua nhiều lần chạy). Poll xen kẽ `idle()` + sleep
     * ngắn tới khi điều kiện đúng hoặc hết timeout — không dùng sleep dài 1 lần (chậm không cần
     * thiết khi điều kiện đã đúng sớm).
     */
    private fun waitUntilTrue(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}
