package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.mckimquyen.watermark.utils.AuthenticityKeyStore

/**
 * IDEA-03: đọc con dấu trong EXIF của một ảnh bất kỳ rồi đối chiếu xem ảnh có bị sửa kể từ lúc xuất.
 *
 * Tách khỏi ViewModel để phần quyết định (so hash, xét chữ ký) test được riêng qua [evaluate], không
 * cần dựng Activity.
 */
object AuthenticityVerifier {

    sealed interface Result {
        /** Không đọc nổi file (không phải ảnh, thiếu quyền, file hỏng). */
        data object Unreadable : Result

        /** Ảnh hợp lệ nhưng không mang con dấu — không có gì để đối chiếu. */
        data object NoStamp : Result

        /** Có con dấu; [intact] = ảnh chưa đổi VÀ chữ ký hợp lệ. */
        data class Stamped(
            val intact: Boolean,
            /** `false` khi chính con dấu bị can thiệp (hash khớp hay không cũng không còn tin được). */
            val signatureValid: Boolean,
            /** `true` khi hash ảnh hiện tại vẫn khớp hash trong con dấu. */
            val hashMatches: Boolean,
            val timestampMs: Long,
            val owner: String,
            /** Vân tay rút gọn để hiển thị; rỗng nếu khoá trong con dấu hỏng. */
            val keyFingerprint: String,
            /** `true` nếu ký bằng khoá của chính thiết bị đang kiểm tra. */
            val signedByThisDevice: Boolean
        ) : Result
    }

    /** Đọc [uri] rồi trả kết quả kiểm tra. Mọi lỗi IO → [Result.Unreadable]. */
    fun verify(contentResolver: ContentResolver, uri: Uri): Result {
        // Null-check stream TRƯỚC, tách riêng khỏi attribute null (= ảnh đọc được nhưng không có
        // stamp, vẫn phải là NoStamp) — gộp chung 2 trường hợp null này từng khiến mất quyền đọc
        // giữa chừng bị báo nhầm NoStamp thay vì Unreadable (BUG-53).
        val stream = try {
            contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.Unreadable
        } ?: return Result.Unreadable

        val rawStamp = try {
            stream.use { ExifInterface(it).getAttribute(ExifInterface.TAG_USER_COMMENT) }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.Unreadable
        }

        val stamp = AuthenticityStamp.parse(rawStamp) ?: return Result.NoStamp

        val currentHash = try {
            contentResolver.openInputStream(uri)?.use { JpegImageDigest.hashImageData(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.Unreadable
        } ?: return Result.Unreadable

        return evaluate(stamp, currentHash)
    }

    /**
     * Phần quyết định thuần: so [currentHash] với con dấu [stamp] và xét chữ ký.
     *
     * Chữ ký phải hợp lệ TRƯỚC, rồi mới xét hash — chữ ký hỏng nghĩa là chính con dấu bị can thiệp,
     * lúc đó hash trong con dấu là do kẻ sửa ảnh tự điền nên khớp hay không cũng vô nghĩa.
     */
    fun evaluate(stamp: AuthenticityStamp.Stamp, currentHash: String): Result.Stamped {
        val signatureValid = AuthenticityKeyStore.verify(
            payload = AuthenticityStamp.signedPayload(stamp),
            signatureB64 = stamp.signature,
            publicKeyB64 = stamp.publicKey
        )
        val hashMatches = stamp.hash == currentHash
        return Result.Stamped(
            intact = signatureValid && hashMatches,
            signatureValid = signatureValid,
            hashMatches = hashMatches,
            timestampMs = stamp.timestampMs,
            owner = stamp.owner,
            keyFingerprint = AuthenticityKeyStore.fingerprintOf(stamp.publicKey).orEmpty(),
            signedByThisDevice = AuthenticityKeyStore.isOwnKey(stamp.publicKey)
        )
    }
}
