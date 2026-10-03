package com.mckimquyen.watermark.utils

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

/**
 * FEAT-27 Quick Share Bar — tìm các app nhắn tin/lưu trữ đã cài có nhận được ảnh, và dựng intent
 * chia sẻ (dùng chung với nút Chia sẻ thường để 2 đường luôn khớp).
 */
object QuickShareHelper {

    /** Zalo, Messenger, Telegram, Drive — PHẢI khớp `<queries>` trong AndroidManifest (Android 11+). */
    val KNOWN_PACKAGES = listOf(
        "com.zing.zalo",
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.google.android.apps.docs"
    )

    private const val MIME_IMAGE = "image/*"
    private const val CLIP_LABEL_SINGLE = "Image"
    private const val CLIP_LABEL_MULTIPLE = "Images"

    data class Target(val packageName: String, val label: CharSequence, val icon: Drawable)

    /** Intent chỉ dùng để hỏi PackageManager "app này có nhận ảnh không" (chưa gắn file). */
    fun probeIntent(packageName: String): Intent =
        Intent(Intent.ACTION_SEND).setType(MIME_IMAGE).setPackage(packageName)

    /** App cài sẵn VÀ thật sự có activity nhận ảnh; giữ thứ tự của [packages]. */
    @Suppress("DEPRECATION") // queryIntentActivities(Intent, Int): bản mới chỉ có từ API 33, minSdk 24.
    fun resolveInstalled(pm: PackageManager, packages: List<String> = KNOWN_PACKAGES): List<Target> =
        packages.mapNotNull { pkg ->
            val info = pm.queryIntentActivities(probeIntent(pkg), 0).firstOrNull() ?: return@mapNotNull null
            Target(pkg, info.loadLabel(pm), info.loadIcon(pm))
        }

    /** 1 ảnh → ACTION_SEND, nhiều ảnh → ACTION_SEND_MULTIPLE; [packageName] != null mở thẳng app đó. */
    fun buildShareIntent(resolver: ContentResolver, uris: List<Uri>, packageName: String? = null): Intent {
        require(uris.isNotEmpty()) { "uris must not be empty" }
        return Intent().apply {
            type = MIME_IMAGE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            packageName?.let { setPackage(it) }
            if (uris.size == 1) {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uris.first())
                clipData = ClipData.newUri(resolver, CLIP_LABEL_SINGLE, uris.first())
            } else {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                val clip = ClipData(CLIP_LABEL_MULTIPLE, arrayOf(MIME_IMAGE), ClipData.Item(uris.first()))
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                clipData = clip
            }
        }
    }
}
