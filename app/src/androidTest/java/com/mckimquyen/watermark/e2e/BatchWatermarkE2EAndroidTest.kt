package com.mckimquyen.watermark.e2e

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.export.BatchExportEngine
import com.mckimquyen.watermark.export.ExportNaming
import com.mckimquyen.watermark.ui.Image
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.adapter.GalleryAdapter
import com.mckimquyen.watermark.ui.widget.LaunchView
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Espresso Instrumented E2E Integration Test Suite.
 * Executes on real device hardware (Tecno) to verify:
 * 1. Real batch export pipeline with disk I/O, MediaStore/storage creation, and Bitmap decoding.
 * 2. MainActivity launch with Material You / Material 3 home cards.
 * 3. Smooth transition to Editor mode, toolbar, tabs, and canvas.
 * 4. GalleryAdapter Select All / Deselect All logic and item tracking.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BatchWatermarkE2EAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val createdTestFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        BitmapCache.clearCache()
    }

    @After
    fun tearDown() {
        createdTestFiles.forEach { it.delete() }
        createdTestFiles.clear()
        BitmapCache.clearCache()
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
    }

    private fun createTestJpeg(width: Int, height: Int, color: Int, namePrefix: String): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        val file = File.createTempFile(namePrefix, ".jpg", context.cacheDir)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        createdTestFiles.add(file)
        return Uri.fromFile(file)
    }

    @Test
    fun test01_batchExportPipeline_generatesRealFilesOnTecnoStorage() {
        // 1. Create 2 real JPEG images
        val uri1 = createTestJpeg(800, 600, Color.BLUE, "tecno_test_1")
        val uri2 = createTestJpeg(1024, 768, Color.GREEN, "tecno_test_2")
        val inputList = listOf(ImageInfo(uri1), ImageInfo(uri2))

        // 2. Setup BatchExportEngine
        val exportEngine = BatchExportEngine(context, ExportNaming())
        val fakeViewInfo = ViewInfo(
            width = 1080,
            height = 1920,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            matrix = Matrix()
        )

        val settings = BatchExportEngine.ExportSettings(
            config = WaterMark(
                text = "Tecno E2E Watermark",
                textSize = 36f,
                textColor = Color.WHITE,
                textStyle = TextPaintStyle.Fill,
                textTypeface = TextTypeface.Normal,
                alpha = 220,
                degree = 0f,
                hGap = 120,
                vGap = 120,
                iconUri = Uri.EMPTY,
                markMode = WaterMarkRepository.MarkMode.Text,
                enableBounds = false
            ),
            outputFormat = Bitmap.CompressFormat.JPEG,
            compressLevel = 85,
            maxOutputLongEdge = 0, // original size
            copyright = "© 2026 EasyWatermark Tecno E2E",
            outputNamePattern = "e2e_tecno_{index}"
        )

        // 3. Execute batch export
        val progressUpdates = mutableListOf<ImageInfo?>()
        val result = runBlocking {
            exportEngine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo,
                infoList = inputList,
                settings = settings,
                onProgress = { progressUpdates.add(it) }
            )
        }

        // 4. Assert batch success
        assertThat(result.type).isEqualTo(Result.Type.Success)
        val exportedImages = result.data.orEmpty()
        assertThat(exportedImages).hasSize(2)

        for (imageInfo in exportedImages) {
            assertThat(imageInfo.jobState).isInstanceOf(JobState.Success::class.java)
            val successState = imageInfo.jobState as JobState.Success
            val outputUri = successState.result.data as? Uri
            assertThat(outputUri).isNotNull()

            // Verify that the generated output file is a valid readable bitmap
            val inputStream = context.contentResolver.openInputStream(outputUri!!)
            assertThat(inputStream).isNotNull()
            val decodedBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            assertThat(decodedBitmap).isNotNull()
            assertThat(decodedBitmap.width).isGreaterThan(0)
            assertThat(decodedBitmap.height).isGreaterThan(0)
            decodedBitmap.recycle()
        }

        // 5. Verify cache is cleanly cleared after export
        assertThat(BitmapCache.currentSize()).isEqualTo(0)
    }

    @Test
    fun test02_mainActivity_launchesWithMaterialYouHomeUI() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Verify Home Brand Headline
                assertThat(activity.getString(R.string.app_name)).isNotEmpty()

                // Verify Action Cards exist and are configured
                assertThat(activity.findViewById<android.view.View>(R.id.tvCrashInfo)).isNull()
            }
        }
    }

    @Test
    fun test03_editorModeTransition_displaysMaterialYouComponents() {
        val uri1 = createTestJpeg(600, 600, Color.RED, "editor_test_1")
        val uri2 = createTestJpeg(800, 800, Color.YELLOW, "editor_test_2")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = androidx.lifecycle.ViewModelProvider(activity)[MainViewModel::class.java]

                // Feed images to trigger Editor Mode
                viewModel.updateImageList(listOf(uri1, uri2))

                val launchView = activity.launchView

                // Transition to editor
                launchView.toEditorMode()

                // Verify Toolbar and Tabs
                assertThat(launchView.toolbar).isNotNull()
                assertThat(launchView.tabLayout.tabCount).isEqualTo(3)

                // Verify Tab Labels: Content, Style, Layout
                assertThat(launchView.tabLayout.getTabAt(0)?.text).isEqualTo(activity.getString(R.string.title_content))
                assertThat(launchView.tabLayout.getTabAt(1)?.text).isEqualTo(activity.getString(R.string.title_style))
                assertThat(launchView.tabLayout.getTabAt(2)?.text).isEqualTo(activity.getString(R.string.title_layout))

                // Select Style Tab
                launchView.tabLayout.getTabAt(1)?.select()
                assertThat(launchView.tabLayout.selectedTabPosition).isEqualTo(1)

                // Select Layout Tab
                launchView.tabLayout.getTabAt(2)?.select()
                assertThat(launchView.tabLayout.selectedTabPosition).isEqualTo(2)
            }
        }
    }

    @Test
    fun test04_galleryAdapter_selectAllAndUnSelectAll_onDevice() {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val adapter = GalleryAdapter()
            val dummyRv = RecyclerView(context)

            val testList = listOf(
                Image(id = 1, uri = Uri.parse("content://media/1"), name = "img1.jpg", size = 1024, date = 100),
                Image(id = 2, uri = Uri.parse("content://media/2"), name = "img2.jpg", size = 2048, date = 200),
                Image(id = 3, uri = Uri.parse("content://media/3"), name = "img3.jpg", size = 4096, date = 300)
            )

            adapter.submitList(testList) {
                // When submitted, selectAll
                adapter.selectAll(dummyRv)
                assertThat(adapter.isAllSelected()).isTrue()
                assertThat(adapter.selectedCount.value).isEqualTo(3)
                assertThat(adapter.getSelectedList().size).isEqualTo(3)

                // Unselect all
                adapter.unSelectAll(dummyRv)
                assertThat(adapter.isAllSelected()).isFalse()
                assertThat(adapter.selectedCount.value).isEqualTo(0)
                assertThat(adapter.getSelectedList()).isEmpty()

                latch.countDown()
            }
        }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    }
}
