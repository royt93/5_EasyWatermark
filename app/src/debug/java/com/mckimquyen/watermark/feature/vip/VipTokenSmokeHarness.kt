package com.mckimquyen.watermark.feature.vip

import java.io.File

internal class VipTokenSmokeHarness(
    private val filesDir: File,
    private val signer: (String) -> String
) {
    internal val keyFile = File(filesDir, KEY_FILE_NAME)
    internal val tokenFile = File(filesDir, TOKEN_FILE_NAME)
    internal val sentinelFile = File(filesDir, SENTINEL_FILE_NAME)
    internal val initDelayFile = File(filesDir, INIT_DELAY_FILE_NAME)

    fun mintToken(): MintResult {
        if (!keyFile.isFile) return MintResult.NotArmed
        if (sentinelFile.exists()) {
            // Đã armed: không ký thêm, nhưng vẫn xoá key để giữ bất biến key dùng một lần.
            keyFile.delete()
            return MintResult.NotArmed
        }
        // Mọi đường sau đây (kể cả đọc key lỗi) đều đi qua finally để key không sót trên đĩa.
        return try {
            val token = signer(keyFile.readText())
            if (token.isBlank() || !writeAtomically(tokenFile, token)) {
                tokenFile.delete()
                sentinelFile.delete()
                MintResult.Failed
            } else {
                sentinelFile.writeText(ARMED_VALUE)
                MintResult.Created
            }
        } catch (_: Exception) {
            tokenFile.delete()
            sentinelFile.delete()
            MintResult.Failed
        } finally {
            keyFile.delete()
        }
    }

    /** True nếu key vẫn còn trên đĩa sau mint (delete thất bại); UI dùng để cảnh báo operator xoá tay. */
    fun keyStillOnDisk(): Boolean = keyFile.exists()

    fun consumePendingToken(submit: (String) -> Boolean): SubmitResult {
        if (!sentinelFile.isFile || !tokenFile.isFile) return SubmitResult.NotArmed
        val token = try {
            tokenFile.readText()
        } catch (_: Exception) {
            clearHandoff()
            return SubmitResult.Failed
        }
        if (!clearHandoff()) return SubmitResult.Failed
        return try {
            if (submit(token)) SubmitResult.Submitted else SubmitResult.NotSubmitted
        } catch (_: Exception) {
            SubmitResult.Failed
        }
    }

    /**
     * Đọc rồi xoá file hoãn khởi tạo (một lần). Giá trị lỗi/âm/rỗng → 0; chặn trên [MAX_INIT_DELAY_MS] để app
     * không bao giờ kẹt chưa initialize. Dùng để giữ token ở trạng thái SDK pending đủ lâu mà chụp được UI.
     */
    fun consumeInitDelayMs(): Long {
        if (!initDelayFile.isFile) return 0L
        val raw = try {
            initDelayFile.readText().trim()
        } catch (_: Exception) {
            ""
        }
        initDelayFile.delete()
        return (raw.toLongOrNull() ?: 0L).coerceIn(0L, MAX_INIT_DELAY_MS)
    }

    private fun clearHandoff(): Boolean {
        val tokenRemoved = !tokenFile.exists() || tokenFile.delete()
        val sentinelRemoved = !sentinelFile.exists() || sentinelFile.delete()
        return tokenRemoved && sentinelRemoved
    }

    private fun writeAtomically(target: File, content: String): Boolean {
        val temp = File(filesDir, "$TOKEN_FILE_NAME.tmp")
        return try {
            temp.writeText(content)
            temp.renameTo(target)
        } finally {
            temp.delete()
        }
    }

    enum class MintResult {
        NotArmed,
        Created,
        Failed
    }

    internal companion object {
        const val MAX_INIT_DELAY_MS = 600_000L
        private const val INIT_DELAY_FILE_NAME = "vip-smoke-init-delay"
        private const val KEY_FILE_NAME = "vip-smoke-private-key"
        private const val TOKEN_FILE_NAME = "vip-smoke-token"
        private const val SENTINEL_FILE_NAME = "vip-smoke-armed"
        private const val ARMED_VALUE = "armed"
    }
}
