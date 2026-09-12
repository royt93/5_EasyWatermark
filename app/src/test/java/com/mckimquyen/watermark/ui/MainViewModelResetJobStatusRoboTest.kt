package com.mckimquyen.watermark.ui

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-08: `resetJobStatus()` phải tạo copy mới (`jobState = Ready`) và đẩy qua
 * `waterMarkRepo.updateImageList()` thay vì mutate tại chỗ item đang sống trong repository list —
 * trước đây mutate im lặng không qua `updateImageList()`, `StateFlow` không bao giờ emit lại dù
 * nội dung item đã đổi (không predictable, đúng vấn đề ticket nêu).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelResetJobStatusRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        viewModel.imageList.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun resetJobStatus_resetsToReady_withoutMutatingOriginalObjects_andSyncsRepository() {
        val a = ImageInfo(
            uri = Uri.parse("content://media/A"),
            jobState = JobState.Success(Result.success(null))
        )
        val b = ImageInfo(uri = Uri.parse("content://media/B"), jobState = JobState.Failure(Result.failure(null)))
        runBlocking { waterMarkRepo.updateImageList(listOf(a, b)) }
        shadowOf(Looper.getMainLooper()).idle()

        val seen = mutableListOf<ImageInfo>()
        viewModel.saveProcess.observeForever { it?.let(seen::add) }

        viewModel.resetJobStatus()
        shadowOf(Looper.getMainLooper()).idle()

        // Object gốc do test giữ tham chiếu không bị đổi (copy() tạo instance mới).
        assertThat(a.jobState).isInstanceOf(JobState.Success::class.java)
        assertThat(b.jobState).isInstanceOf(JobState.Failure::class.java)

        // Repository/StateFlow phải phản ánh Ready cho cả 2 ảnh (đồng bộ qua updateImageList()).
        assertThat(viewModel.imageList.value?.first?.map { it.jobState })
            .containsExactly(JobState.Ready, JobState.Ready)
        assertThat(seen.map { it.jobState }).containsExactly(JobState.Ready, JobState.Ready)
    }
}
