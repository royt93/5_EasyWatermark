package com.mckimquyen.watermark.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardImageUrlImporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clients = mutableListOf<OkHttpClient>()

    @org.junit.After
    fun closeClients() {
        clients.forEach {
            it.dispatcher.cancelAll()
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdownNow()
        }
    }

    @Test
    fun rejectsUnsafeUrlsWithoutNetwork() {
        listOf("http://example.test/a.png", "https://127.0.0.1/a", "https://[::1]/a", "https://user:pass@example.test/a", "https://example.test:8443/a")
            .forEach { assertThat(ClipboardImageUrlImporter.isAllowedHttpsImageUrl(it)).isFalse() }
        // CDN thường không có phần mở rộng; không dùng đuôi URL để suy ra ảnh.
        assertThat(ClipboardImageUrlImporter.isAllowedHttpsImageUrl("https://cdn.example.test/image?id=1")).isTrue()
    }

    @Test
    fun blocksPrivateAndReservedAddresses() {
        listOf("0.0.0.0", "127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "198.18.0.1", "224.0.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1")
            .forEach { assertThat(ClipboardImageUrlImporter.isPublicAddress(InetAddress.getByName(it))).isFalse() }
        assertThat(ClipboardImageUrlImporter.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue()
    }

    @Test
    fun validPngBecomesReadableFileProviderUri() {
        runBlocking {
            val png = pngBytes()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
            assertThat(bounds.outMimeType).isEqualTo("image/png")
            val uri = ClipboardImageUrlImporter.import(context, "https://cdn.example.test/image?id=1", client(png))
            assertThat(uri?.scheme).isEqualTo("content")
            val bytes = requireNotNull(context.contentResolver.openInputStream(requireNotNull(uri))).use { it.readBytes() }
            assertThat(bytes.toList()).containsExactlyElementsIn(png.toList())
        }
    }

    @Test
    fun invalidImageHtmlAndRedirectAreRejectedAndPartialFilesRemoved() {
        runBlocking {
            val directory = File(context.cacheDir, "clipboard_images")
            val before = directory.listFiles().orEmpty().toSet()
            assertThat(ClipboardImageUrlImporter.import(context, "https://cdn.example.test/image", client(byteArrayOf(1, 2, 3)))).isNull()
            assertThat(ClipboardImageUrlImporter.import(context, "https://cdn.example.test/image", client(pngBytes(), "text/html"))).isNull()
            assertThat(ClipboardImageUrlImporter.import(context, "https://cdn.example.test/image", client(pngBytes(), code = 302))).isNull()
            assertThat(directory.listFiles().orEmpty().toSet()).isEqualTo(before)
        }
    }

    @Test
    fun declaredOversizeAndNetworkErrorAreRejectedBeforeCreatingCacheFile() {
        runBlocking {
            val directory = File(context.cacheDir, "clipboard_images")
            val before = directory.listFiles().orEmpty().toSet()
            val oversized = OkHttpClient.Builder().addInterceptor { chain ->
                val body = object : okhttp3.ResponseBody() {
                    override fun contentType() = "image/png".toMediaType()
                    override fun contentLength() = ClipboardImageUrlImporter.MAX_IMAGE_BYTES + 1
                    override fun source(): okio.BufferedSource = okio.Buffer()
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("test").body(body).build()
            }.build()
            val failed = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("test") }.build()
            try {
                assertThat(ClipboardImageUrlImporter.import(context, "https://cdn.example.test/image", oversized)).isNull()
                assertThat(ClipboardImageUrlImporter.import(context, "https://cdn.example.test/image", failed)).isNull()
                assertThat(directory.listFiles().orEmpty().toSet()).isEqualTo(before)
            } finally {
                listOf(oversized, failed).forEach {
                    it.connectionPool.evictAll()
                    it.dispatcher.executorService.shutdown()
                }
            }
        }
    }

    @Test
    fun cancellationClosesStreamAndRemovesPartialImage() {
        runBlocking {
            val enteredRead = java.util.concurrent.CountDownLatch(1)
            val releaseRead = java.util.concurrent.CountDownLatch(1)
            val streamClosed = java.util.concurrent.CountDownLatch(1)
            val directory = File(context.cacheDir, "clipboard_images")
            val before = directory.listFiles().orEmpty().toSet()
            val body = object : okhttp3.ResponseBody() {
                private val stream = object : java.io.InputStream() {
                    override fun read(): Int {
                        enteredRead.countDown()
                        check(releaseRead.await(TEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS))
                        return -1
                    }
                    override fun close() { streamClosed.countDown() }
                }
                override fun contentType() = "image/png".toMediaType()
                override fun contentLength() = -1L
                private val source = stream.source().buffer()
                override fun source(): okio.BufferedSource = source
            }
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("test").body(body).build()
            }.build().also(clients::add)
            val job = launch(kotlinx.coroutines.Dispatchers.Default) {
                ClipboardImageUrlImporter.import(context, "https://cdn.example.test/image", transport)
            }
            try {
                check(enteredRead.await(TEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS))
                job.cancel()
            } finally {
                releaseRead.countDown()
                job.join()
            }
            check(streamClosed.await(TEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS))
            // Callback có thể dọn file sau khi stream đóng; chờ có giới hạn, không sleep vô hạn.
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS)
            while (directory.listFiles().orEmpty().toSet() != before && System.nanoTime() < deadline) Thread.yield()
            assertThat(directory.listFiles().orEmpty().toSet()).isEqualTo(before)
            assertThat(job.isCancelled).isTrue()
        }
    }

    private fun client(bytes: ByteArray, mime: String = "image/png", code: Int = 200): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(bytes.toResponseBody(mime.toMediaType())).build()
        }.build().also(clients::add)

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 5L
    }
}
