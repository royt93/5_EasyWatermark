package com.mckimquyen.watermark.utils

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** HTTP response giả, còn decode Skia, FileProvider và ContentResolver đều thật trên thiết bị. */
@RunWith(AndroidJUnit4::class)
class ClipboardImageImportIntegrationTest {
    @Test
    fun htmlHttpsImage_becomesReadableContentUriAndPassesLocalPasteExtraction() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val bytes = try {
                ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            val clip = ClipData.newHtmlText("image", "image", "<img src=\"https://images.example.test/asset?id=1&amp;size=small\">")
            val url = ClipboardImageHelper.extractImageUrls(clip).single()
            assertThat(url).isEqualTo("https://images.example.test/asset?id=1&size=small")
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(HTTP_OK).message("test").body(bytes.toResponseBody("image/png".toMediaType())).build()
            }.build()
            try {
                val uri = requireNotNull(ClipboardImageUrlImporter.import(context, url, transport))
                try {
                    assertThat(context.contentResolver.getType(uri)).isEqualTo("image/png")
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use { input ->
                        BitmapFactory.decodeStream(requireNotNull(input), null, options)
                    }
                    assertThat(options.outWidth).isEqualTo(IMAGE_WIDTH)
                    assertThat(options.outHeight).isEqualTo(IMAGE_HEIGHT)
                    val localClip = ClipData.newUri(context.contentResolver, "imported image", uri)
                    assertThat(ClipboardImageHelper.extractImageUris(context, localClip)).containsExactly(uri)
                } finally {
                    context.contentResolver.delete(uri, null, null)
                }
            } finally {
                transport.connectionPool.evictAll()
                transport.dispatcher.executorService.shutdown()
            }
        }
    }

    private companion object {
        const val IMAGE_WIDTH = 3
        const val IMAGE_HEIGHT = 2
        const val PNG_QUALITY = 100
        const val HTTP_OK = 200
    }
}
