package com.mckimquyen.watermark.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * IDEA-03: bọc Android Keystore để ký con dấu chứng thực bằng khoá EC P-256.
 *
 * Vì sao dùng Keystore chứ không tự sinh khoá rồi lưu file: khoá riêng trong Keystore KHÔNG trích
 * xuất ra được. Đó chính là điểm khiến người sửa ảnh không thể ký lại một con dấu hợp lệ bằng đúng
 * khoá của chủ ảnh — nếu khoá nằm ở file thì chỉ cần đọc file là ký lại được, con dấu mất hết ý nghĩa.
 *
 * GIỚI HẠN phải nói rõ: không có PKI nào chứng nhận khoá này thuộc về ai. Chữ ký chứng minh được
 * "ảnh chưa đổi kể từ lúc ký" và "được ký bởi khoá có vân tay X" — nó KHÔNG tự chứng minh X là ai.
 * Mạnh nhất khi xác minh ảnh do chính thiết bị này xuất ra ([isOwnKey]).
 *
 * Mọi lỗi Keystore (thiết bị lỗi, khoá bị xoá khi user đổi kiểu khoá màn hình) đều trả `null`/`false`
 * thay vì ném — cùng tinh thần `ExportNaming.applyCopyrightExif`: không bao giờ chặn export vì
 * metadata.
 */
object AuthenticityKeyStore {

    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "ewm_authenticity_ec_p256"
    private const val CURVE = "secp256r1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val KEY_ALGORITHM = "EC"

    /** Vân tay rút gọn còn 16 ký tự hex (64 bit) — đủ để người dùng đối chiếu bằng mắt. */
    private const val FINGERPRINT_HEX_LENGTH = 16

    private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING

    /** Ký [payload], trả chữ ký base64; `null` nếu Keystore không dùng được. */
    fun sign(payload: ByteArray): String? = try {
        getOrCreatePrivateKey()?.let { privateKey ->
            val signer = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initSign(privateKey)
                update(payload)
            }
            Base64.encodeToString(signer.sign(), BASE64_FLAGS)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /** Khoá công khai của thiết bị này dạng base64 X.509, để nhúng vào con dấu. */
    fun publicKeyEncoded(): String? = try {
        getOrCreatePublicKey()?.let { Base64.encodeToString(it.encoded, BASE64_FLAGS) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /** `true` nếu [publicKeyB64] đúng là khoá của thiết bị này. */
    fun isOwnKey(publicKeyB64: String): Boolean =
        publicKeyB64.isNotEmpty() && publicKeyEncoded() == publicKeyB64

    /**
     * Xác minh [signatureB64] trên [payload] bằng khoá công khai [publicKeyB64] (đọc từ chính con
     * dấu). `false` khi chữ ký sai, khoá hỏng, hoặc thiếu dữ liệu.
     */
    fun verify(payload: ByteArray, signatureB64: String, publicKeyB64: String): Boolean {
        if (signatureB64.isEmpty()) return false
        val publicKey = decodePublicKey(publicKeyB64) ?: return false
        return try {
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(payload)
                verify(Base64.decode(signatureB64, BASE64_FLAGS))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Vân tay rút gọn của một khoá công khai base64 để hiển thị; `null` nếu khoá hỏng. */
    fun fingerprintOf(publicKeyB64: String): String? =
        decodePublicKey(publicKeyB64)?.let { publicKey ->
            MessageDigest.getInstance("SHA-256")
                .digest(publicKey.encoded)
                .joinToString("") { "%02x".format(it) }
                .take(FINGERPRINT_HEX_LENGTH)
        }

    private fun decodePublicKey(publicKeyB64: String): PublicKey? {
        if (publicKeyB64.isEmpty()) return null
        return try {
            val bytes = Base64.decode(publicKeyB64, BASE64_FLAGS)
            KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(bytes))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    // @Synchronized: batch export có thể gọi song song ngay lần đầu; 2 thread cùng sinh khoá sẽ ghi
    // đè alias, làm chữ ký của ảnh này lệch với khoá công khai đã nhúng vào nó.
    @Synchronized
    private fun getOrCreatePrivateKey(): PrivateKey? {
        if (!loadKeyStore().containsAlias(KEY_ALIAS)) generateKeyPair()
        return loadKeyStore().getKey(KEY_ALIAS, null) as? PrivateKey
    }

    @Synchronized
    private fun getOrCreatePublicKey(): PublicKey? {
        if (!loadKeyStore().containsAlias(KEY_ALIAS)) generateKeyPair()
        return loadKeyStore().getCertificate(KEY_ALIAS)?.publicKey
    }

    private fun generateKeyPair() {
        // KHÔNG đặt setUserAuthenticationRequired: ký phải chạy được trong batch export ở nền, không
        // thể chờ user mở khoá màn hình từng ảnh.
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).run {
            initialize(spec)
            generateKeyPair()
        }
    }
}
