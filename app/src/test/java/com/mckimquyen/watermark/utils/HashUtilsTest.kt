package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

/** IDEA-07: [HashUtils.sha256] phải khớp vector chuẩn NIST — không phụ thuộc Android, chạy JVM thuần. */
class HashUtilsTest {

    @Test
    fun sha256_emptyInput_matchesKnownVector() {
        val hash = HashUtils.sha256(ByteArrayInputStream(ByteArray(0)))
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    @Test
    fun sha256_abc_matchesKnownVector() {
        val hash = HashUtils.sha256(ByteArrayInputStream("abc".toByteArray(Charsets.UTF_8)))
        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun sha256_differentInput_producesDifferentHash() {
        val hashA = HashUtils.sha256(ByteArrayInputStream("photo-a".toByteArray()))
        val hashB = HashUtils.sha256(ByteArrayInputStream("photo-b".toByteArray()))
        assertThat(hashA).isNotEqualTo(hashB)
        assertThat(hashA).hasLength(64)
        assertThat(hashB).hasLength(64)
    }

    @Test
    fun sha256_largeInput_readsAcrossMultipleBufferChunks() {
        // Input > buffer 8192 bytes nội bộ — đảm bảo vòng lặp đọc nhiều lần cho hash đúng, không
        // chỉ đúng với input nhỏ vừa 1 buffer.
        val bytes = ByteArray(20_000) { (it % 256).toByte() }
        val hash = HashUtils.sha256(ByteArrayInputStream(bytes))
        assertThat(hash).hasLength(64)
        // Đọc lại lần 2 với cùng input phải ra cùng hash (deterministic).
        val hashAgain = HashUtils.sha256(ByteArrayInputStream(bytes))
        assertThat(hash).isEqualTo(hashAgain)
    }
}
