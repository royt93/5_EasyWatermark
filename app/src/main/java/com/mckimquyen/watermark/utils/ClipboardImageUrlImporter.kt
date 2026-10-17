package com.mckimquyen.watermark.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Tải ảnh HTTPS chỉ sau thao tác Dán; không gửi cookie, không đăng nhập hoặc theo redirect. */
object ClipboardImageUrlImporter {
    internal const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    private const val TIMEOUT_SECONDS = 20L
    private const val CACHE_DIR = "clipboard_images"
    private const val FILE_PREFIX = "pasted_"
    private const val HTTPS = "https"
    private const val HTTPS_PORT = 443
    private const val IMAGE_MIME_PREFIX = "image/"
    private const val COPY_BUFFER_BYTES = 8 * 1024
    private val SUPPORTED_EXTENSIONS = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "image/bmp" to "bmp",
        "image/heic" to "heic",
        "image/heif" to "heif"
    )
    private val publicDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
            if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) {
                throw IOException("Địa chỉ mạng nội bộ không được phép")
            }
        }
    }
    private val client = OkHttpClient.Builder()
        .dns(publicDns)
        .proxy(java.net.Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun import(context: Context, urlText: String): Uri? = import(context, urlText, client)

    // Client thay thế chỉ dành cho test: production luôn dùng DNS chặn IP nội bộ ngay khi nối socket.
    internal suspend fun import(context: Context, urlText: String, transport: OkHttpClient): Uri? {
        if (!isAllowedHttpsImageUrl(urlText)) return null
        var file: File? = null
        return try {
            file = download(context.applicationContext, transport.newCall(Request.Builder().url(urlText).build()))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (cancelled: CancellationException) {
            file?.delete()
            throw cancelled
        } catch (error: IOException) {
            file?.delete()
            com.mckimquyen.watermark.AppLog.w("ClipboardImage", "Tải ảnh thất bại: ${error.javaClass.simpleName}")
            null
        } catch (error: IllegalArgumentException) {
            file?.delete()
            com.mckimquyen.watermark.AppLog.w("ClipboardImage", "Không tạo được URI ảnh: ${error.javaClass.simpleName}")
            null
        }
    }

    private suspend fun download(context: Context, call: Call): File = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                var file: File? = null
                try {
                    response.use {
                        val body = it.body ?: throw IOException("Không có dữ liệu ảnh")
                        if (!it.isSuccessful || body.contentType()?.toString()?.startsWith(IMAGE_MIME_PREFIX) != true) {
                            throw IOException("Phản hồi không phải ảnh")
                        }
                        if (body.contentLength() > MAX_IMAGE_BYTES) throw IOException("Ảnh quá lớn")
                        val directory = File(context.cacheDir, CACHE_DIR)
                        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Không tạo được cache")
                        val target = File.createTempFile(FILE_PREFIX, ".img", directory)
                        file = target
                        body.byteStream().use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(COPY_BUFFER_BYTES)
                                var total = 0L
                                while (true) {
                                    if (!continuation.isActive) throw IOException("Đã huỷ tải ảnh")
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > MAX_IMAGE_BYTES) throw IOException("Ảnh quá lớn")
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(target.absolutePath, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Không giải mã được ảnh")
                        // Đuôi file suy từ MIME của dữ liệu đã decode (không tin header HTTP); bảng cố định
                        // để kết quả không phụ thuộc MimeTypeMap của từng bản Android.
                        val extension = SUPPORTED_EXTENSIONS[bounds.outMimeType?.lowercase()]
                            ?: throw IOException("Định dạng không hỗ trợ")
                        val image = File(directory, "${target.nameWithoutExtension}.$extension")
                        if (!target.renameTo(image)) throw IOException("Không lưu được ảnh")
                        file = image
                        if (continuation.isActive) {
                            continuation.resume(image) { image.delete() }
                        } else {
                            image.delete()
                        }
                    }
                } catch (error: Exception) {
                    file?.delete()
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            if (error is IOException) error else IOException("Không đọc được ảnh", error)
                        )
                    }
                }
            }
        })
    }

    internal fun isAllowedHttpsImageUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        if (url.scheme != HTTPS || url.port != HTTPS_PORT || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val host = url.host
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (host.contains(':') || host.all { it.isDigit() || it == '.' }) {
            return runCatching { isPublicAddress(InetAddress.getByName(host)) }.getOrDefault(false)
        }
        return true
    }

    internal fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address.map { it.toInt() and 0xff }
        return when (bytes.size) {
            4 -> {
                val first = bytes[0]
                val second = bytes[1]
                first != 0 && first != 10 && first != 127 && first < 224 &&
                    !(first == 100 && second in 64..127) &&
                    !(first == 169 && second == 254) &&
                    !(first == 172 && second in 16..31) &&
                    !(first == 192 && second in listOf(0, 168)) &&
                    !(first == 198 && second in 18..19)
            }
            // Chỉ global-unicast IPv6; chặn ULA, mapped/translation, documentation, 6to4/Teredo.
            16 -> bytes[0] in 0x20..0x3f &&
                !(
                    bytes[0] == 0x20 && bytes[1] == 0x01 &&
                        (bytes[2] == 0 || (bytes[2] == 0x0d && bytes[3] == 0xb8))
                    ) &&
                !(bytes[0] == 0x20 && bytes[1] == 0x02)
            else -> false
        }
    }
}
