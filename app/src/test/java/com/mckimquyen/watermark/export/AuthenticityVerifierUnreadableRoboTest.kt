package com.mckimquyen.watermark.export

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

/**
 * BUG-53: `openInputStream(uri)` trả `null` (quyền đọc bị thu hồi giữa chừng, đúng hợp đồng API của
 * `ContentResolver` — không throw) phải trả [AuthenticityVerifier.Result.Unreadable], KHÔNG được rơi
 * xuống `NoStamp` — ảnh thật sự CÓ con dấu không được báo nhầm "không có con dấu" chỉ vì mất quyền
 * đọc đúng lúc verify. Dùng `ContentProvider` giả (cùng pattern `BitmapUtilsNullStreamGuardRoboTest`)
 * thay vì mock trần — mô phỏng đúng hợp đồng `openAssetFile=null` mà `ContentResolver` thật gặp phải.
 */
@RunWith(RobolectricTestRunner::class)
class AuthenticityVerifierUnreadableRoboTest {

    /** Provider giả lập "mất quyền đọc/provider lỗi" — luôn trả null cho asset file. */
    class NullStreamProvider : ContentProvider() {
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = null
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    /** Provider trả về 1 file JPEG thật — mô phỏng ảnh đọc được bình thường (không có con dấu). */
    class RealFileProvider : ContentProvider() {
        lateinit var file: File
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun verify_openInputStreamReturnsNull_returnsUnreadable_notNoStamp() {
        Robolectric.setupContentProvider(NullStreamProvider::class.java, "wm.auth.null.stream")
        val uri = Uri.parse("content://wm.auth.null.stream/test.jpg")

        val result = AuthenticityVerifier.verify(context.contentResolver, uri)

        assertThat(result).isEqualTo(AuthenticityVerifier.Result.Unreadable)
    }

    @Test
    fun verify_realImageWithoutStamp_stillReturnsNoStamp() {
        val file = File.createTempFile("wm_auth_no_stamp", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()

        val provider = Robolectric.setupContentProvider(RealFileProvider::class.java, "wm.auth.no.stamp")
        provider.file = file
        val uri = Uri.parse("content://wm.auth.no.stamp/test.jpg")

        val result = AuthenticityVerifier.verify(context.contentResolver, uri)

        assertThat(result).isEqualTo(AuthenticityVerifier.Result.NoStamp)
    }
}
