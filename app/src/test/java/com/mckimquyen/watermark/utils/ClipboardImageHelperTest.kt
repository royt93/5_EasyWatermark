package com.mckimquyen.watermark.utils

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ClipboardImageHelperTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var testTempDir: File

    @Before
    fun setUp() {
        testTempDir = File(context.cacheDir, "test_clipboard_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        testTempDir.deleteRecursively()
    }

    @Test
    fun extractImageUris_nullOrEmptyClipData_returnsEmptyList() {
        assertThat(ClipboardImageHelper.extractImageUris(context, null)).isEmpty()
        assertThat(ClipboardImageHelper.hasImage(context, null)).isFalse()

        val emptyClip = ClipData(ClipDescription("Empty", arrayOf()), ClipData.Item(""))
        val clipEmpty = ClipData.newPlainText("empty", "")
        clipEmpty.addItem(ClipData.Item(""))
    }

    @Test
    fun extractImageUris_clipDataWithTextOnly_returnsEmptyList() {
        val textClip = ClipData.newPlainText("text_label", "This is plain text message, not an image")
        val result = ClipboardImageHelper.extractImageUris(context, textClip)

        assertThat(result).isEmpty()
        assertThat(ClipboardImageHelper.hasImage(context, textClip)).isFalse()
    }

    @Test
    fun extractImageUris_clipDataWithValidImageUri_returnsUri() {
        val sampleImage = File(testTempDir, "screenshot_sample.png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val imageUri = Uri.fromFile(sampleImage)

        val imageClip = ClipData.newUri(context.contentResolver, "Image", imageUri)
        val result = ClipboardImageHelper.extractImageUris(context, imageClip)

        assertThat(result).containsExactly(imageUri)
        assertThat(ClipboardImageHelper.hasImage(context, imageClip)).isTrue()
    }

    @Test
    fun extractImageUris_clipDataWithMultipleImages_returnsAllImageUris() {
        val img1 = File(testTempDir, "photo1.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        val img2 = File(testTempDir, "photo2.webp").apply { writeBytes(byteArrayOf(3, 4)) }

        val uri1 = Uri.fromFile(img1)
        val uri2 = Uri.fromFile(img2)

        val clip = ClipData.newUri(context.contentResolver, "Img1", uri1).apply {
            addItem(ClipData.Item(uri2))
        }

        val result = ClipboardImageHelper.extractImageUris(context, clip)
        assertThat(result).containsExactly(uri1, uri2)
    }

    @Test
    fun extractImageUris_clipDataWithTextContainingImageUri_extractsParsedUri() {
        val sampleImage = File(testTempDir, "copied_photo.jpg").apply {
            writeBytes(byteArrayOf(5, 6, 7))
        }
        val uriString = Uri.fromFile(sampleImage).toString()

        val textClip = ClipData.newPlainText("ImageUri", uriString)
        val result = ClipboardImageHelper.extractImageUris(context, textClip)

        assertThat(result).hasSize(1)
        assertThat(result.first().toString()).isEqualTo(uriString)
        assertThat(ClipboardImageHelper.hasImage(context, textClip)).isTrue()
    }

    @Test
    fun extractImageUris_clipDataWithNonImageUri_returnsEmptyList() {
        val textFile = File(testTempDir, "document.pdf").apply {
            writeBytes(byteArrayOf(0, 0, 0))
        }
        val docUri = Uri.fromFile(textFile)

        val clip = ClipData.newUri(context.contentResolver, "Doc", docUri)
        val result = ClipboardImageHelper.extractImageUris(context, clip)

        assertThat(result).isEmpty()
        assertThat(ClipboardImageHelper.hasImage(context, clip)).isFalse()
    }
}
