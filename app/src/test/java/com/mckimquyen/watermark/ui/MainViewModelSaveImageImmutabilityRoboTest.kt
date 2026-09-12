package com.mckimquyen.watermark.ui

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.os.Looper
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.ViewInfo
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
 * ENH-08: `ImageInfo` bây giờ bất biến — `saveImage()`/`generateList()` KHÔNG được mutate object
 * `ImageInfo` gốc do caller truyền vào (trước đây mutate tại chỗ `info.jobState =`/`info.result =`
 * ngay trên object trong `infoList`, khiến state không thể dự đoán nếu object đó còn được tham
 * chiếu ở nơi khác — vd trong `WaterMarkRepository.imageInfoList`). Verify bằng URI không tồn tại
 * (decode thất bại ngay, không cần bitmap thật) — đủ để đi qua toàn bộ luồng `jobState`
 * Ready → Ing → Failure mà không chạm nhánh vẽ watermark thật.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelSaveImageImmutabilityRoboTest {

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
        viewModel.waterMark.observeForever {}
        viewModel.imageList.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitJobFinished(timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.saveResult.value?.code == MainViewModel.TYPE_JOB_FINISH) return
            Thread.sleep(20)
        }
    }

    @Test
    fun saveImage_decodeFailure_neverMutatesOriginalImageInfo_postsImmutableCopies() {
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        val viewInfo = ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            matrix = Matrix()
        )
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()

        val seenJobStates = mutableListOf<JobState>()
        viewModel.saveProcess.observeForever { it?.let { info -> seenJobStates.add(info.jobState) } }

        viewModel.saveImage(context.contentResolver, viewInfo, listOf(original))
        awaitJobFinished()

        // Object GỐC do test truyền vào không hề bị đổi — mọi update đi qua copy() tạo instance mới.
        assertThat(original.jobState).isEqualTo(JobState.Ready)
        assertThat(original.result).isNull()

        // saveProcess nhận đủ các bước tiến trình: Ing rồi tới Failure (decode URI không tồn tại thất bại).
        assertThat(seenJobStates).contains(JobState.Ing)
        assertThat(seenJobStates.last()).isInstanceOf(JobState.Failure::class.java)
    }
}
