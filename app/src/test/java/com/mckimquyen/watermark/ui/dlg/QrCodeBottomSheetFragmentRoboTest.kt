package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.export.ExportNaming
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.TimeUnit

/**
 * BUG-13: gõ vào ô nội dung QR không còn sinh bitmap đồng bộ trên Main thread ngay mỗi ký tự —
 * `refreshPreview()` phải debounce 250ms (`QR_REFRESH_DEBOUNCE_MS`) và huỷ job cũ khi gõ tiếp
 * (không tích luỹ nhiều lần generate cho cùng 1 chuỗi gõ liên tục).
 *
 * Dùng `shadowOf(Looper).idleFor(duration)` (tôn trọng mốc thời gian đã lên lịch của
 * `Handler.postDelayed`/coroutine `delay`) chứ KHÔNG dùng `idle()` trần — `idle()` không tham số
 * chạy hết mọi task đang chờ kể cả task lên lịch ở tương lai (đã verify thực nghiệm: gọi `idle()`
 * ngay sau `setText` khiến `refreshPreview` chạy ngay lập tức, che mất hành vi debounce cần test).
 *
 * IDEA-07: `onViewCreated`/`refreshPreview` giờ ĐỌC `shareViewModel.waterMark`/`selectedImage`
 * (restore state QR động khi mở lại sheet) — không còn tránh chạm `shareViewModel` như trước, nên
 * dùng `TestHostActivity` cung cấp thẳng 1 `MainViewModel` dựng trực tiếp (không qua Hilt) — đúng
 * pattern `GalleryFragmentLifecycleRoboTest`, thay vì `FragmentActivity` trần như code cũ.
 */
@RunWith(RobolectricTestRunner::class)
class QrCodeBottomSheetFragmentRoboTest {

    companion object {
        // Robolectric dựng Activity bằng constructor mặc định (reflection) nên không thể truyền
        // viewModel qua constructor — dùng biến static tạm cho factory đọc lại.
        lateinit var testViewModel: MainViewModel
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private lateinit var viewModel: MainViewModel
    private var activityController: ActivityController<TestHostActivity>? = null

    @Before
    fun setUp() {
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(newTestUserDataStore(context)),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        testViewModel = viewModel
        // `waterMark`/`selectedImage` là LiveData bọc Flow lazy (`asLiveData()`) — chỉ bắt đầu
        // collect khi có observer ACTIVE. App thật LUÔN có màn editor chính observe 2 LiveData này
        // trước khi user mở được QrCodeBottomSheetFragment; observeForever ở đây mô phỏng đúng điều
        // kiện đó (thiếu bước này, `restoreFromCurrentConfig()` đọc `.value` sẽ luôn là null).
        viewModel.waterMark.observeForever { }
        viewModel.selectedImage.observeForever { }
        resetFileProviderStaticCache()
    }

    /**
     * Robolectric bug thực nghiệm: `androidx.core.content.FileProvider` cache `PathStrategy` tĩnh
     * theo authority (`sCache`), chỉ tự invalidate khi ContentProvider thật được `attachInfo()`
     * (không xảy ra nếu code chỉ gọi `getUriForFile()` mà không query ngược qua ContentResolver).
     * 2 test trong class này (`saveBitmapToCache_prunesOldQrTempFiles` và
     * `btnUseQrCode_dynamicModeOn...`) đều gọi `getUriForFile()` — mỗi Robolectric sandbox có
     * `cacheDir` MỚI nên PathStrategy cache từ sandbox trước trỏ nhầm thư mục, ném
     * `IllegalArgumentException: Failed to find configured root`. Reset thủ công qua reflection
     * trước mỗi test — chỉ ảnh hưởng môi trường test, không đụng code thật.
     */
    private fun resetFileProviderStaticCache() {
        try {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * IDEA-07: nhiều test trong class này gõ/toggle thứ khiến `refreshPreview()` lên lịch coroutine
     * `delay(250) -> Dispatchers.Default thật`. Nếu test kết thúc mà KHÔNG huỷ đúng vòng đời
     * (`onDestroyView` cancel `viewLifecycleOwner.lifecycleScope`), job đó có thể còn "treo" và
     * tranh chấp real-thread với test chạy NGAY SAU trong cùng JVM (đã verify thực nghiệm gây flaky
     * ngẫu nhiên ở `typing_doesNotGenerateImmediately_generatesAfterDebounceDelay` — false dương
     * khoảng 30-60% khi chạy cả class, dù bản thân assertion đúng). `destroy()` activity ở `@After`
     * đảm bảo MỌI job được huỷ sạch trước khi JVM chuyển sang test kế tiếp, thay vì chỉ đợi
     * (`awaitPreviewGenerated`) — đợi không đủ vì có test không cần/không muốn CHỜ job chạy xong
     * (`swQrDynamic_toggleOn/toggleOff` chỉ quan tâm state UI, không quan tâm bitmap).
     */
    @After
    fun tearDown() {
        try {
            activityController?.pause()?.stop()?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchFragment(): QrCodeBottomSheetFragment {
        val controller = Robolectric.buildActivity(TestHostActivity::class.java).setup()
        activityController = controller
        val activity = controller.get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = QrCodeBottomSheetFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, QrCodeBottomSheetFragment.TAG)
            .commit()
        // An toàn: tại đây chưa có coroutine delay nào được lên lịch (etContent còn rỗng, chưa
        // gõ gì) nên idle() trần không "ăn nhầm" task debounce nào.
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun previewDrawableIsSet(fragment: QrCodeBottomSheetFragment): Boolean =
        fragment.binding.ivPreview.drawable != null

    /**
     * Sau khi debounce hết hạn, `generate` thật chạy trên `Dispatchers.Default` thật (một thread
     * pool thật, không phải virtual time của Robolectric) rồi mới post kết quả trở lại Main —
     * poll ngắn thay vì assert ngay để không bị race với thread nền đó (cùng pattern
     * `MainViewModelCompressImgRoboTest`).
     */
    private fun awaitPreviewGenerated(fragment: QrCodeBottomSheetFragment) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!previewDrawableIsSet(fragment) && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun typing_doesNotGenerateImmediately_generatesAfterDebounceDelay() {
        val fragment = launchFragment()
        assertThat(previewDrawableIsSet(fragment)).isFalse()

        fragment.binding.etContent.setText("hello")
        assertThat(previewDrawableIsSet(fragment)).isFalse()

        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS) // tổng > 250ms
        awaitPreviewGenerated(fragment)
        assertThat(previewDrawableIsSet(fragment)).isTrue()
    }

    @Test
    fun rapidRetyping_cancelsStaleJob_onlyFinalContentGeneratesAfterItsOwnDelay() {
        val fragment = launchFragment()

        fragment.binding.etContent.setText("a")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS) // < 250ms

        fragment.binding.etContent.setText("ab") // huỷ job của "a", đặt lại debounce từ đầu
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        // Tổng thời gian từ lần gõ đầu tiên là 300ms (> 250ms) nhưng job của "a" đã bị huỷ khi gõ
        // "ab" ở mốc 100ms, nên job mới ("ab") mới trôi qua 200ms (< 250ms) -> vẫn chưa generate.
        assertThat(previewDrawableIsSet(fragment)).isFalse()

        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS) // đủ 300ms cho "ab"
        awaitPreviewGenerated(fragment)
        assertThat(previewDrawableIsSet(fragment)).isTrue()
    }

    @Test
    fun saveBitmapToCache_prunesOldQrTempFiles() {
        val fragment = launchFragment()
        val cacheDir = java.io.File(fragment.requireContext().cacheDir, "qrcodes")
        cacheDir.mkdirs()

        // Tạo sẵn 5 file QR cũ
        for (i in 1..5) {
            val oldFile = java.io.File(cacheDir, "qr_temp_$i.png")
            oldFile.writeText("fake qr $i")
            oldFile.setLastModified(1000L * i)
        }
        assertThat(cacheDir.listFiles()?.filter { it.name.contains("_temp_") }?.size).isEqualTo(5)

        val bmp = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
        val uri = fragment.saveBitmapToCache(bmp)

        assertThat(uri).isNotNull()
        val remaining = cacheDir.listFiles()?.filter { it.name.contains("_temp_") } ?: emptyList()
        // ENH-29: Giữ tối đa 3 file cũ mới nhất + 1 file mới tạo = tối đa 4
        assertThat(remaining.size).isAtMost(4)
    }

    // --- IDEA-07: QR động theo từng ảnh ---

    @Test
    fun swQrDynamic_toggleOn_showsPortfolioLinkAndPrefillsDefaultTemplate() {
        val fragment = launchFragment()
        assertThat(fragment.binding.tilPortfolioLink.visibility).isEqualTo(android.view.View.GONE)

        fragment.binding.swQrDynamic.isChecked = true
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.tilPortfolioLink.visibility).isEqualTo(android.view.View.VISIBLE)
        assertThat(fragment.binding.etContent.text.toString()).isEqualTo(ExportNaming.DEFAULT_QR_CONTENT_TEMPLATE)
        // Bật switch tự prefill template non-blank → tự kích refreshPreview() (debounce 250ms trên
        // Dispatchers.Default THẬT) — đợi hoàn tất trước khi test kết thúc, tránh coroutine còn
        // treo tranh chấp real-thread với test CHẠY SAU trong cùng class (đã từng gây flaky ở
        // `typing_doesNotGenerateImmediately_generatesAfterDebounceDelay`).
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        awaitPreviewGenerated(fragment)
    }

    @Test
    fun swQrDynamic_toggleOff_hidesPortfolioLinkAgain() {
        val fragment = launchFragment()
        fragment.binding.swQrDynamic.isChecked = true
        shadowOf(Looper.getMainLooper()).idle()

        fragment.binding.swQrDynamic.isChecked = false
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.tilPortfolioLink.visibility).isEqualTo(android.view.View.GONE)
        // Xem comment ở `swQrDynamic_toggleOn_...` — cả bật lẫn tắt đều có thể kích refreshPreview()
        // (còn nội dung không rỗng), đợi hoàn tất tránh coroutine treo qua test sau.
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        awaitPreviewGenerated(fragment)
    }

    @Test
    fun btnUseQrCode_dynamicModeOn_callsUpdateQrDynamicConfig_notUpdateIcon() = runBlocking {
        val fragment = launchFragment()
        fragment.binding.swQrDynamic.isChecked = true
        shadowOf(Looper.getMainLooper()).idle()
        fragment.binding.etPortfolioLink.setText("https://me.example")
        fragment.binding.etContent.setText("{hash}|{portfolio_link}")
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        awaitPreviewGenerated(fragment)

        fragment.binding.btnUseQrCode.performClick()

        val dynamicEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey(
            WaterMarkRepository.SP_KEY_QR_DYNAMIC_ENABLED
        )
        val portfolioLinkKey = androidx.datastore.preferences.core.stringPreferencesKey(
            WaterMarkRepository.SP_KEY_QR_PORTFOLIO_LINK
        )
        // `shareViewModel.updateQrDynamicConfig()` ghi DataStore qua `viewModelScope.launch{}` —
        // fire-and-forget từ click listener, không đợi được bằng 1 lần idle() (cùng lý do cần poll
        // như `awaitPreviewGenerated`: DataStore.edit() thật chạy qua dispatcher riêng).
        val deadline = System.currentTimeMillis() + 5_000
        var waterMark = waterMarkDataStore.data.first()
        while (waterMark[dynamicEnabledKey] != true && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
            waterMark = waterMarkDataStore.data.first()
        }
        assertThat(waterMark[dynamicEnabledKey]).isTrue()
        assertThat(waterMark[portfolioLinkKey]).isEqualTo("https://me.example")
    }

    @Test
    fun restoreFromCurrentConfig_reopeningWithDynamicAlreadyEnabled_restoresSwitchAndFields() = runBlocking {
        viewModel.waterMarkRepo.updateQrDynamicConfig(
            android.net.Uri.parse("content://media/prev_preview"),
            "{hash}|{date}",
            "https://old.example"
        )
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = launchFragment()

        assertThat(fragment.binding.swQrDynamic.isChecked).isTrue()
        assertThat(fragment.binding.etContent.text.toString()).isEqualTo("{hash}|{date}")
        assertThat(fragment.binding.etPortfolioLink.text.toString()).isEqualTo("https://old.example")
        // restoreFromCurrentConfig() tự setText → tự kích refreshPreview() — đợi hoàn tất, xem
        // comment ở `swQrDynamic_toggleOn_...` (tránh coroutine treo qua test chạy sau).
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        awaitPreviewGenerated(fragment)
    }
}
