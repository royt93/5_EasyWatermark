package com.mckimquyen.watermark.ui.dlg

import android.Manifest
import android.content.Context
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.MyApplication
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.export.BatchExportWorker
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * BUG-AUDIT-2026-09-29: [SaveImageBSDialogFragment.startExportOrConfirmReplace] phải hỏi xác nhận
 * qua [MaterialAlertDialogBuilder] trước khi bấm Export nếu [BatchExportWorker] đang có batch khác
 * chạy dở (ExistingWorkPolicy.REPLACE sẽ âm thầm huỷ nó) — và KHÔNG hỏi gì khi không có xung đột
 * (giữ nguyên hành vi cũ, đã cover bởi `SaveImageBSDialogFragmentBatchActionRoboTest.bug32_*`).
 *
 * Dùng 1 [Worker] chặn bằng [CountDownLatch] (không phải [BatchExportWorker] thật) đứng tên
 * [BatchExportWorker.UNIQUE_WORK_NAME] để mô phỏng "có batch đang chạy" xác định, tránh flaky.
 * [DelegatingWorkerFactory] fallback về đúng [HiltWorkerFactory][com.mckimquyen.watermark.MyApplication.workerFactory]
 * thật của app cho `BatchExportWorker` — nếu không, nhánh "không xung đột" gọi `saveImage()` thật
 * sẽ enqueue 1 work KHÔNG tạo được (factory lạ trả null) → crash ngầm trên executor riêng, kéo
 * `saveResult` bị `observeExportWork()` ghi đè sai lệch, làm test flaky/sai mà không liên quan gì
 * tới bug đang test.
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageBSDialogFragmentExportConflictRoboTest {

    private val context: Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
    private val blockLatch = CountDownLatch(1)
    private val startedLatch = CountDownLatch(1)

    class BlockingWorker(
        appContext: Context,
        params: WorkerParameters,
        private val started: CountDownLatch,
        private val block: CountDownLatch
    ) : Worker(appContext, params) {
        override fun doWork(): Result {
            started.countDown()
            block.await(5, TimeUnit.SECONDS)
            return Result.success()
        }
    }

    @Before
    fun setUp() {
        val blockingFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == BlockingWorker::class.java.name) {
                BlockingWorker(appContext, workerParameters, startedLatch, blockLatch)
            } else {
                null
            }
        }
        val realHiltFactory = (context.applicationContext as MyApplication).workerFactory
        val delegatingFactory = DelegatingWorkerFactory().apply {
            addFactory(blockingFactory)
            addFactory(realHiltFactory)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setWorkerFactory(delegatingFactory)
                .setExecutor(Executors.newSingleThreadExecutor())
                // setTaskExecutor riêng (không mặc định dùng chung main-looper) — lịch work/insert
                // Room không cần idle() main looper mới chạy được, tránh phải drain hết queue main
                // looper (điều sẽ vô tình chạy luôn observer LiveData mà test cố tình mô phỏng "chưa
                // kịp cập nhật").
                .setTaskExecutor(Executors.newSingleThreadExecutor())
                .build()
        )
        // preCheckStoragePermission chặn saveImage() nếu chưa cấp quyền — Robolectric mặc định
        // KHÔNG tự cấp quyền runtime, phải grant thủ công để test được nhánh Export thật.
        shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    /** R5: nếu assertion fail giữa chừng trước khi countDown() thủ công, đừng để BlockingWorker's
     *  thread treo vô thời hạn (leak) — luôn giải phóng sau mỗi test. */
    @After
    fun releaseBlockingWorker() {
        blockLatch.countDown()
    }

    private fun setupDialogWithOneImage(): Pair<MainActivity, SaveImageBSDialogFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        runBlocking {
            viewModel.waterMarkRepo.updateImageList(
                listOf(ImageInfo(android.net.Uri.parse("content://media/conflict_test/1")))
            )
        }
        val dialogFragment = SaveImageBSDialogFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(dialogFragment, "SaveImageBSDialogFragmentConflictTest")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to dialogFragment
    }

    @Test
    fun btnSaveClick_noOtherBatchRunning_doesNotShowConflictDialog_proceedsDirectly() {
        assertThat(BatchExportWorker.isActive(context)).isFalse()
        val (activity, dialog) = setupDialogWithOneImage()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        dialog.binding.btnSave.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        // ShadowDialog.getLatestDialog() ở đây là chính BottomSheetDialog của bản thân dialog
        // fragment (luôn tồn tại) — phải cast riêng để chỉ kiểm tra KHÔNG có AlertDialog xác nhận
        // nào được show thêm, không phải "không có Dialog nào" (assertion sai/quá chặt).
        assertThat(ShadowDialog.getLatestDialog() as? AlertDialog).isNull()
        // Không bị chặn bởi dialog xác nhận -> chuyển thẳng sang TYPE_SAVING (đúng hành vi cũ).
        val deadline = System.currentTimeMillis() + 3_000
        while (viewModel.saveResult.value?.code != MainViewModel.TYPE_SAVING && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_SAVING)
    }

    /**
     * QUAN TRỌNG: dựng dialog TRƯỚC (để `onViewCreated()` -> `reattachExportWorkIfRunning()` chỉ
     * thấy "không có batch nào chạy" lúc subscribe), rồi MỚI enqueue batch xung đột — và KHÔNG
     * `idle()` main looper giữa lúc enqueue/start batch chặn và lúc click. `BatchExportWorker
     * .isActive()` (query đồng bộ, blocking `.get()`) vẫn thấy đúng batch mới NGAY LẬP TỨC, trong
     * khi observer LiveData bất đồng bộ của `reattachExportWorkIfRunning()` (đã đăng ký từ trước)
     * CHƯA kịp tự cập nhật `saveResult` — mô phỏng đúng race thật trên device (LiveData báo trễ
     * hơn 1 nhịp so với 1 lần check đồng bộ ngay trước khi enqueue). Nếu không dựng đúng thứ tự
     * này, `onViewCreated()` sẽ tự set `saveResult=TYPE_SAVING` trước cả khi test kịp click, khiến
     * nút Save đổi thành "Cancel" và không bao giờ chạm nhánh code đang test.
     */
    private fun enqueueBlockingWorkWithoutDrainingMainLooper() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            BatchExportWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BlockingWorker>().build()
        )
        assertThat(startedLatch.await(5, TimeUnit.SECONDS)).isTrue()
    }

    /**
     * Review pass 2026-09-29: [BatchExportWorker.isActive] giờ chạy qua `lifecycleScope.launch {
     * withContext(Dispatchers.IO) {...} }` (không block main thread nữa) — dialog/`saveResult`
     * không còn cập nhật ĐỒNG BỘ ngay sau `performClick()`, phải poll như mọi coroutine bất đồng
     * bộ khác trong test suite này (`shadowOf(Looper).idle()` + sleep ngắn tới khi có kết quả).
     */
    private fun awaitLatestAlertDialog(timeoutMs: Long = 3_000): AlertDialog? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            (ShadowDialog.getLatestDialog() as? AlertDialog)?.let { return it }
            Thread.sleep(20)
        }
        return ShadowDialog.getLatestDialog() as? AlertDialog
    }

    @Test
    fun btnSaveClick_otherBatchRunning_showsConflictDialog_doesNotStartUntilConfirmed() {
        val (activity, dialog) = setupDialogWithOneImage()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        enqueueBlockingWorkWithoutDrainingMainLooper()
        assertThat(BatchExportWorker.isActive(activity)).isTrue()

        dialog.binding.btnSave.performClick()

        // MaterialAlertDialogBuilder tạo androidx.appcompat.app.AlertDialog (KHÔNG phải
        // android.app.AlertDialog) — ShadowAlertDialog chỉ shadow class framework nên luôn trả
        // null cho dialog này; phải dùng ShadowDialog (generic, theo dõi mọi Dialog) + cast đúng
        // kiểu, giống pattern đã có ở MainActivityBackPressRoboTest.
        val alertDialog = awaitLatestAlertDialog()
        assertThat(alertDialog).isNotNull()
        assertThat(alertDialog!!.isShowing).isTrue()
        val titleView = alertDialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)
        assertThat(titleView?.text?.toString())
            .isEqualTo(activity.getString(R.string.export_conflict_dialog_title))

        // Chưa xác nhận -> [MainViewModel.saveImage] (và REPLACE work cũ) chưa được gọi. `saveResult`
        // riêng có thể tự chuyển TYPE_SAVING độc lập qua [MainViewModel.reattachExportWorkIfRunning]
        // (LiveData quan sát work CŨ đang RUNNING, không liên quan gì tới dialog xác nhận đang test)
        // nên không dùng nó làm bằng chứng — thay vào đó verify chính batch CŨ (`startedLatch`) vẫn
        // là batch đang chạy, chưa bị `enqueueUniqueWork(REPLACE)` nào âm thầm thay thế.
        assertThat(BatchExportWorker.isActive(activity)).isTrue()
        assertThat(alertDialog.isShowing).isTrue()

        blockLatch.countDown() // dọn worker chặn, tránh treo test khác
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * BUG-76: bấm Export 2 lần liên tiếp trước khi check `isActive` (IO, bất đồng bộ) xong — mỗi
     * click từng chạy lại nhánh `else` → 2 coroutine → 2 dialog xung đột chồng nhau (hoặc 2
     * `saveImage()` enqueue REPLACE khi không xung đột). Phải chỉ có đúng 1 dialog.
     */
    @Test
    fun btnSaveDoubleClick_otherBatchRunning_showsOnlyOneConflictDialog() {
        val (_, dialog) = setupDialogWithOneImage()
        enqueueBlockingWorkWithoutDrainingMainLooper()

        dialog.binding.btnSave.performClick()
        dialog.binding.btnSave.performClick()

        assertThat(awaitLatestAlertDialog()).isNotNull()
        // Chờ thêm để coroutine thứ 2 (nếu có) kịp show dialog của nó.
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        val shownAlerts = ShadowDialog.getShownDialogs().count { it is AlertDialog && it.isShowing }
        assertThat(shownAlerts).isEqualTo(1)

        blockLatch.countDown()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun btnSaveClick_otherBatchRunning_confirmingDialog_proceedsWithSave() {
        val (activity, dialog) = setupDialogWithOneImage()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        enqueueBlockingWorkWithoutDrainingMainLooper()

        dialog.binding.btnSave.performClick()

        val alertDialog = awaitLatestAlertDialog()
        assertThat(alertDialog).isNotNull()
        blockLatch.countDown() // để REPLACE (do nhấn Xác nhận) không phải chờ work cũ tự thoát
        alertDialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()

        val deadline = System.currentTimeMillis() + 3_000
        while (viewModel.saveResult.value?.code != MainViewModel.TYPE_SAVING && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_SAVING)
    }
}
