package com.mckimquyen.watermark.ui
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.UserPreferences
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.export.BatchExportWorker
import com.mckimquyen.watermark.utils.ktx.formatDate
import com.mckimquyen.watermark.utils.ktx.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.zelory.compressor.Compressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userRepo: UserConfigRepository,
    private val waterMarkRepo: WaterMarkRepository,
    private val memorySettingRepo: MemorySettingRepo,
    private val templateRepo: TemplateRepository,
    private val exportNaming: com.mckimquyen.watermark.export.ExportNaming = com.mckimquyen.watermark.export.ExportNaming()
) : ViewModel() {

    var nextSelectedPos: Int = 0

    val saveResult: MutableLiveData<Result<*>> = MutableLiveData()

    val compressedResult: MutableLiveData<Result<*>> = MutableLiveData()

    val waterMark: LiveData<WaterMark> = waterMarkRepo.waterMark.asLiveData()

    private val uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.None)

    val uiStateFlow: StateFlow<UiState> = uiState.asStateFlow()

    private var autoScroll = true

    val imageList: LiveData<Pair<List<ImageInfo>, Boolean>> =
        waterMarkRepo.imageInfoMapFlow.asLiveData().map { Pair(it, autoScroll) }

    val galleryPickedImageList: MutableLiveData<List<Image>> = MutableLiveData()

    val selectedImage: LiveData<ImageInfo> = waterMarkRepo.selectedImage.asLiveData()

    val saveProcess: MutableLiveData<ImageInfo?> = MutableLiveData()

    private var compressedJob: Job? = null

    private var userPreferences: StateFlow<UserPreferences> = userRepo.userPreferences.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserPreferences.DEFAULT
    )

    val outputFormat: Bitmap.CompressFormat
        get() = userPreferences.value.outputFormat

    val compressLevel: Int
        get() = userPreferences.value.compressLevel

    val maxOutputLongEdge: Int
        get() = userPreferences.value.maxOutputLongEdge

    val copyright: String
        get() = userPreferences.value.copyright

    val outputNamePattern: String
        get() = userPreferences.value.outputNamePattern

    val colorPalette: MutableLiveData<Palette> = MutableLiveData()

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        if (Build.VERSION.SDK_INT > 28) MediaStore.Images.Media.DATE_MODIFIED else MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.ORIENTATION,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.SIZE
    )

    val templateListFlow: StateFlow<List<Template>> = templateRepo.getAllTemplate().stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun addTemplate(content: String) {
        if (templateRepo.checkIfIsDaoNull()) {
            launch {
                uiState.emit(UiState.DatabaseError)
            }
            return
        }
        viewModelScope.launch {
            val template = Template(
                id = 0,
                content = content,
                creationDate = Date(),
                lastModifiedDate = Date()
            )
            templateRepo.insertTemplate(template)
        }
    }

    fun updateTemplate(template: Template) {
        viewModelScope.launch {
            templateRepo.updateTemplate(template)
        }
    }

    fun deleteTemplate(template: Template) {
        viewModelScope.launch {
            templateRepo.deleteTemplate(template)
        }
    }

    /** WorkInfo observer đang gắn cho lần export hiện tại — gỡ khi work xong để tránh leak. */
    private var exportWorkObserver: androidx.lifecycle.Observer<androidx.work.WorkInfo?>? = null
    private var exportWorkLiveData: LiveData<androidx.work.WorkInfo?>? = null

    /**
     * ENH-01: chạy qua `BatchExportWorker` (WorkManager) thay vì `viewModelScope` — batch sống sót
     * khi app xuống nền (Doze/OEM background-kill, đã thấy trên chính các máy Samsung/TECNO dùng
     * để test trong dự án này). `contentResolver`/`imageList` không cần truyền cho Worker nữa
     * (Worker tự đọc `waterMarkRepo.imageInfoList` + `applicationContext.contentResolver`) — giữ
     * nguyên tham số hàm để không phải sửa call site (`SaveImageBSDialogFragment`).
     */
    fun saveImage(
        contentResolver: ContentResolver,
        viewInfo: ViewInfo,
        imageList: List<ImageInfo>
    ) {
        if (this.imageList.value?.first.isNullOrEmpty()) {
            saveResult.value = Result.failure(data = null, code = TYPE_ERROR_NOT_IMG)
            return
        }
        saveResult.value = Result.success(null, code = TYPE_SAVING)
        BatchExportWorker.enqueue(appContext, viewInfo)
        observeExportWork()
    }

    /** ENH-01 AC2: huỷ batch export giữa chừng. */
    fun cancelSaveImage() {
        BatchExportWorker.cancel(appContext)
    }

    /**
     * ENH-01 AC1: gọi khi dialog Export mở ra ([SaveImageBSDialogFragment.onViewCreated]) — bắt lại
     * đúng trạng thái nếu batch_export vẫn đang chạy nền từ trước (app từng bị kill giữa chừng rồi
     * mở lại, MainViewModel là instance MỚI chưa từng gọi saveImage()). Idempotent, gọi nhiều lần
     * an toàn (observeExportWork() tự gỡ observer cũ trước khi gắn observer mới).
     */
    fun reattachExportWorkIfRunning() {
        observeExportWork()
    }

    private fun observeExportWork() {
        exportWorkObserver?.let { exportWorkLiveData?.removeObserver(it) }
        val liveData = androidx.work.WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWorkLiveData(BatchExportWorker.UNIQUE_WORK_NAME)
            .map { it.firstOrNull() }
        exportWorkLiveData = liveData
        val observer = androidx.lifecycle.Observer<androidx.work.WorkInfo?> { info ->
            if (info == null) return@Observer
            if (info.state.isFinished) {
                exportWorkObserver?.let { liveData.removeObserver(it) }
                // Progress Data của item cuối cùng có thể đã bị WorkManager xoá trước khi observer
                // này kịp thấy (xem BatchExportWorker.doWork()) — đẩy lại toàn bộ trạng thái cuối
                // (đã ghi vào repo, tự imageList cập nhật) qua saveProcess để adapter không bị kẹt
                // icon "đang xử lý" cho item bị lỡ progress update.
                imageList.value?.first?.forEach { saveProcess.value = it }
                saveProcess.value = null
                saveResult.value = when (info.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> Result.success(code = TYPE_JOB_FINISH, data = null)
                    androidx.work.WorkInfo.State.CANCELLED -> Result.failure(data = null, code = TYPE_ERROR_CANCELLED)
                    else -> Result.failure(data = null, code = TYPE_ERROR_FILE_NOT_FOUND)
                }
            } else {
                // ENQUEUED/RUNNING/BLOCKED: batch đang chạy (kể cả khi ViewModel này mới được tạo
                // lại và bắt gặp work đã chạy sẵn từ trước, xem init{}) — đảm bảo saveResult phản
                // ánh đúng "đang export" thay vì giữ giá trị mặc định null/trạng thái cũ.
                if (saveResult.value?.code != TYPE_SAVING) {
                    saveResult.value = Result.success(null, code = TYPE_SAVING)
                }
                applyExportProgress(info.progress)
            }
        }
        exportWorkObserver = observer
        liveData.observeForever(observer)
    }

    /** Bridge tiến độ per-image từ Worker (uri + state ordinal, xem [BatchExportWorker]) về đúng contract cũ (`saveProcess`). */
    private fun applyExportProgress(progress: androidx.work.Data) {
        val uriStr = progress.getString(BatchExportWorker.KEY_PROGRESS_URI) ?: return
        val stateOrdinal = progress.getInt(BatchExportWorker.KEY_PROGRESS_STATE, -1)
        if (stateOrdinal < 0) return
        val uri = Uri.parse(uriStr)
        val current = imageList.value?.first?.find { it.uri == uri } ?: return
        val jobState = when (stateOrdinal) {
            BatchExportWorker.STATE_ING -> JobState.Ing
            BatchExportWorker.STATE_SUCCESS -> JobState.Success(Result.success(uri))
            BatchExportWorker.STATE_FAILURE -> JobState.Failure(Result.failure(null, code = TYPE_ERROR_SAVE_UNKNOWN))
            else -> return
        }
        saveProcess.value = current.copy(jobState = jobState)
    }

    /**
     * ENH-01: logic thật đã chuyển sang [com.mckimquyen.watermark.export.ExportNaming] (dùng chung
     * với `BatchExportWorker`) — giữ delegate ở đây để [resolvePreviewText] không đổi.
     */
    private fun resolveTextTokens(
        text: String,
        imageInfo: ImageInfo,
        contentResolver: ContentResolver,
        index: Int
    ): String = exportNaming.resolveTextTokens(text, imageInfo, contentResolver, index)

    /**
     * Resolve token cho preview trong editor (không phải export) — dùng đúng logic/token với
     * [resolveTextTokens], nhưng lấy index từ vị trí thật của ảnh trong danh sách batch và
     * `appContext.contentResolver` thay vì contentResolver truyền từ export flow.
     * No-op khi text không chứa '{' (fast path, không query filename mỗi lần gõ phím).
     *
     * ENH-20: `suspend` + `withContext(Dispatchers.IO)` — lần đầu resolve `{filename}` cho 1 ảnh
     * (chưa có trong cache của [queryDisplayName]) gọi `ContentResolver.query()` đồng bộ; với URI
     * chậm (SAF thư mục mạng/cloud provider) việc này có thể khựng UI nếu chạy trên Main thread.
     * Hành vi cache theo uri không đổi (vẫn nằm trong [queryDisplayName]).
     */
    suspend fun resolvePreviewText(text: String, imageInfo: ImageInfo): String {
        if (!text.contains('{')) return text
        val index = waterMarkRepo.imageInfoList.indexOfFirst { it.uri == imageInfo.uri }.coerceAtLeast(0)
        return withContext(Dispatchers.IO) {
            resolveTextTokens(text, imageInfo, appContext.contentResolver, index)
        }
    }

    /**
     * Tên file xuất — mặc định "ewm_{timestamp}" nếu user chưa đặt pattern ([outputNamePattern]
     * rỗng); nếu có pattern, resolve token qua đúng [resolveTextTokens] đang dùng cho text
     * watermark (vd "{filename}_wm_{seq}"). Phần đuôi file luôn theo [trapOutputExtension].
     */
    internal fun generateOutputName(
        contentResolver: ContentResolver,
        imageInfo: ImageInfo,
        index: Int
    ): String = exportNaming.generateOutputName(contentResolver, imageInfo, index, outputNamePattern, outputFormat)

    private fun trapOutputExtension(): String = exportNaming.trapOutputExtension(outputFormat)

    fun selectImage(uri: Uri) {
        if (selectedImage.value?.uri == uri) {
            return
        }
        launch {
            waterMarkRepo.select(uri)
        }
    }

    fun updateImageList(list: List<Uri>) {
        launch {
            generateImageInfoList(list)?.run {
                updateImageListInternal(this)
            }
        }
    }

    private fun updateImageListInternal(list: List<ImageInfo>) {
        launch {
            autoScroll = true
            waterMarkRepo.select(list.first().uri)
            nextSelectedPos = 0
            waterMarkRepo.updateImageList(list)
        }
    }

    private suspend fun generateImageInfoList(list: List<Uri>) =
        withContext(Dispatchers.Default) {
            return@withContext list.toSet()
                .map { ImageInfo(it) }
                .takeIf {
                    it.isNotEmpty()
                }
        }

    fun updateText(text: String) {
        launch {
            waterMarkRepo.updateText(text)
        }
    }

    fun updateTextSize(textSize: Float) {
        launch {
            val finalTextSize = textSize.coerceAtLeast(0f)
            waterMarkRepo.updateTextSize(finalTextSize)
        }
    }

    fun updateTextColor(color: Int) {
        launch {
            waterMarkRepo.updateColor(color)
        }
    }

    fun updateTextStyle(style: TextPaintStyle) {
        launch {
            waterMarkRepo.updateTextStyle(style)
        }
    }

    fun updateTextTypeface(typeface: TextTypeface) {
        launch {
            waterMarkRepo.updateTypeFace(typeface)
        }
    }

    fun updateAlpha(alpha: Int) {
        launch {
            val finalAlpha = alpha.coerceAtLeast(0).coerceAtMost(255)
            waterMarkRepo.updateAlpha(finalAlpha)
        }
    }

    fun updateHorizon(gap: Int) {
        launch {
            waterMarkRepo.updateHorizon(gap)
        }
    }

    fun updateVertical(gap: Int) {
        launch {
            waterMarkRepo.updateVertical(gap)
        }
    }

    fun updateDegree(degree: Float) {
        launch {
            waterMarkRepo.updateDegree(degree)
        }
    }

    fun updateIcon(iconUri: Uri) {
        AppLog.d(LOG_TAG, "[VM] updateIcon called: uri=$iconUri  empty=${iconUri.toString().isEmpty()}")
        launch {
            if (iconUri.toString().isNotEmpty()) {
                AppLog.d(LOG_TAG, "[VM] waterMarkRepo.updateIcon() \u2192 uri=$iconUri")
                waterMarkRepo.updateIcon(iconUri)
                AppLog.d(LOG_TAG, "[VM] waterMarkRepo.updateIcon() done")
            } else {
                AppLog.d(LOG_TAG, "[VM] updateIcon: uri is EMPTY, skip")
            }
        }
    }

    /**
     * ENH-01: logic vẽ thật đã chuyển sang [com.mckimquyen.watermark.utils.bitmap.ExifBorderRenderer]
     * (hàm thuần, không phụ thuộc state ViewModel) để `BatchExportEngine`/`BatchExportWorker` dùng
     * chung được — giữ delegate 1 dòng ở đây để test hiện có (gọi qua instance `viewModel`) không
     * cần sửa.
     */
    internal fun buildExifBorderBitmap(
        source: Bitmap,
        eModel: ExifModel,
        style: ExifFrameStyle,
        bandColor: Int? = null,
        bandThicknessPercent: Float? = null,
        useSerifCaption: Boolean? = null
    ): Bitmap = com.mckimquyen.watermark.utils.bitmap.ExifBorderRenderer.buildExifBorderBitmap(
        source,
        eModel,
        style,
        bandColor,
        bandThicknessPercent,
        useSerifCaption
    )

    /** Xem [com.mckimquyen.watermark.utils.bitmap.ExifBorderRenderer.fitTextForCanvas]. */
    internal fun fitTextForCanvas(paint: Paint, text: String, maxWidth: Float): String =
        com.mckimquyen.watermark.utils.bitmap.ExifBorderRenderer.fitTextForCanvas(paint, text, maxWidth)

    fun toggleExifBorder() {
        launch {
            val currentValue = waterMark.value?.enableExif ?: false
            waterMarkRepo.updateEnableExif(!currentValue)
        }
    }

    fun selectExifFrameStyle(style: ExifFrameStyle) {
        launch {
            waterMarkRepo.updateExifFrameStyle(style)
        }
    }

    /** FEAT-14 Custom Frame Builder — `null` = xoá override, quay lại màu mặc định của style. */
    fun updateExifBandColor(color: Int?) {
        launch {
            waterMarkRepo.updateExifBandColor(color)
        }
    }

    /** FEAT-14 Custom Frame Builder — `null` = xoá override, quay lại độ dày mặc định của style. */
    fun updateExifBandThicknessPercent(percent: Float?) {
        launch {
            waterMarkRepo.updateExifBandThicknessPercent(percent)
        }
    }

    /** FEAT-14 Custom Frame Builder — `null` = xoá override, quay lại font mặc định của style. */
    fun updateExifUseSerifCaption(useSerif: Boolean?) {
        launch {
            waterMarkRepo.updateExifUseSerifCaption(useSerif)
        }
    }

    /** FEAT-14 Custom Frame Builder — xoá cả 3 override cùng lúc (nút "Reset" trong UI). */
    fun resetExifCustomization() {
        launch {
            waterMarkRepo.resetExifCustomization()
        }
    }

    fun updateTileMode(imageInfo: ImageInfo, tileMode: Shader.TileMode) {
        launch {
            autoScroll = false
            waterMarkRepo.updateTileMode(imageInfo, tileMode)
        }
    }

    fun updateOffset(info: ImageInfo) {
        launch {
            autoScroll = false
            waterMarkRepo.updateOffset(info)
        }
    }

    fun selectAnchor(anchor: Anchor) {
        launch {
            waterMarkRepo.updateAnchor(anchor)
            uiState.emit(UiState.ApplyAnchor(anchor, waterMark.value?.marginPercent ?: WaterMarkRepository.DEFAULT_MARGIN_PERCENT))
        }
    }

    fun updateMarginPercent(percent: Float) {
        launch {
            waterMarkRepo.updateMargin(percent)
        }
    }

    fun saveOutput(format: Bitmap.CompressFormat, level: Int) {
        viewModelScope.launch {
            userRepo.updateFormat(format)
            userRepo.updateCompressLevel(level)
        }
        resetJobStatus()
    }

    fun saveMaxLongEdge(maxLongEdge: Int) {
        viewModelScope.launch {
            userRepo.updateMaxLongEdge(maxLongEdge)
        }
        resetJobStatus()
    }

    fun saveCopyright(copyright: String) {
        viewModelScope.launch {
            userRepo.updateCopyright(copyright)
        }
    }

    fun saveOutputNamePattern(pattern: String) {
        viewModelScope.launch {
            userRepo.updateOutputNamePattern(pattern)
        }
    }

    fun removeImage(
        imageInfo: ImageInfo?,
        curSelectedPos: Int
    ) {
        val list = imageList.value?.first?.toMutableList() ?: return
        val removePos = list.indexOf(imageInfo)
        if (removePos < 0) return
        list.removeAt(removePos)
        val selectedPos =
            if (removePos < curSelectedPos || removePos >= (
                imageList.value?.first?.size
                    ?: 0
                ) - 1
            ) {
                (curSelectedPos - 1).coerceAtLeast(0)
            } else {
                curSelectedPos
            }
        launch {
            autoScroll = false
            nextSelectedPos = selectedPos
            waterMarkRepo.updateImageList(list)
            if (removePos == curSelectedPos) {
                list.getOrNull(selectedPos)?.uri?.let { selectImage(it) }
            }
        }
    }

    fun updateColorPalette(palette: Palette) {
        colorPalette.postValue(palette)
        memorySettingRepo.updatePalette(palette)
    }

    fun resetJobStatus() {
        saveResult.postValue(Result.success(null))
        // ENH-08: imageInfo bất biến — copy() thay vì mutate item đang sống trong repository list,
        // đồng thời đẩy list mới về repository (trước đây mutate im lặng không qua updateImageList,
        // StateFlow không bao giờ emit lại dù nội dung item đã đổi).
        val updatedList = imageList.value?.first?.map { it.copy(jobState = JobState.Ready) } ?: return
        launch {
            waterMarkRepo.updateImageList(updatedList)
        }
        updatedList.forEach { saveProcess.value = it }
    }

    fun clearData() {
        launch {
            waterMarkRepo.select(Uri.EMPTY)
        }
    }

    fun compressImg(activity: Activity) {
        val appContext = activity.applicationContext
        compressedJob = viewModelScope.launch(Dispatchers.IO) {
            val firstImage = waterMarkRepo.imageInfoList.firstOrNull()
            if (waterMark.value == null || firstImage == null) {
                compressedResult.postValue(
                    Result.failure(
                        null,
                        code = TYPE_COMPRESS_ERROR,
                        message = "Config value is null."
                    )
                )
                return@launch
            }
            compressedResult.postValue(Result.success(null, code = TYPE_COMPRESSING))
            val tmpFile = File.createTempFile("easy_water_mark_", "_compressed")
            try {
                appContext.contentResolver.openInputStream(firstImage.uri)
                    .use { input ->
                        tmpFile.outputStream().use { output ->
                            input?.copyTo(output)
                        }
                    }
                val compressedFile = Compressor.compress(appContext, tmpFile)
                try {
                    val compressedFileUri = FileProvider.getUriForFile(
                        appContext,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        compressedFile
                    )
                    selectImage(compressedFileUri)
                    compressedResult.postValue(Result.success(null, code = TYPE_COMPRESS_OK))
                } catch (ie: IllegalArgumentException) {
                    compressedResult.postValue(
                        Result.failure(
                            null,
                            code = TYPE_COMPRESS_ERROR,
                            message = "Images creates uri failed."
                        )
                    )
                }
            } finally {
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
            }
        }
    }

    fun cancelCompressJob() {
        compressedJob?.cancel()
    }

    fun extraCrashInfo(activity: Activity, crashInfo: String?) {
        // user do not saving crash info into external storage
        // So that wo just share the internal file
        val mainContent = """
Dear developer, here are my crash info:
```
$crashInfo
```
---

APP:

${BuildConfig.VERSION_CODE}, ${BuildConfig.VERSION_NAME}, ${BuildConfig.BUILD_TYPE} 

Devices:

${Build.VERSION.RELEASE}, ${Build.VERSION.SDK_INT}, ${Build.DEVICE}, ${Build.MODEL}, ${Build.PRODUCT}, ${Build.MANUFACTURER}

${System.currentTimeMillis().formatDate("yyy-MM-dd")}
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("roy.mobile.dev@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.email_subject))
            putExtra(Intent.EXTRA_TEXT, mainContent)
        }
        try {
            activity.startActivity(
                Intent.createChooser(
                    intent,
                    activity.getString(R.string.crash_mail)
                )
            )
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            Toast.makeText(
                /* context = */ activity,
                /* text = */ activity.getString(R.string.tip_not_mail_found),
                /* duration = */ Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCleared() {
        cancelCompressJob()
        // ENH-01: exportWorkObserver dùng observeForever (không tự gỡ theo Lifecycle) — phải gỡ
        // thủ công khi ViewModel bị huỷ thật, nếu không observer (giữ closure tham chiếu tới
        // instance này) rò rỉ vĩnh viễn trong registry của LiveData/WorkManager.
        exportWorkObserver?.let { exportWorkLiveData?.removeObserver(it) }
        super.onCleared()
    }

    fun saveUpgradeInfo() {
        launch { userRepo.saveVersionCode() }
    }

    fun query(contentResolver: ContentResolver) {
        launch {
            queryInternal(contentResolver)
        }
    }

    private suspend fun queryInternal(
        contentResolver: ContentResolver,
        force: Boolean = galleryPickedImageList.value == null
    ) = withContext(Dispatchers.IO) {
        if (!force) {
            return@withContext
        }
        val list = ArrayList<Image>()
        contentResolver.query(
            /* uri = */ MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            /* projection = */ projection,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ (if (Build.VERSION.SDK_INT > 28) MediaStore.Images.Media.DATE_MODIFIED else MediaStore.Images.Media.DATE_TAKEN) + " DESC"
        )?.use { cursor ->
            val imageIdColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn =
                cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            val dateColumn =
                cursor.getColumnIndex(if (Build.VERSION.SDK_INT > 28) MediaStore.Images.Media.DATE_MODIFIED else MediaStore.Images.Media.DATE_TAKEN)
            val orientationColumn = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
            val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                if (path.isNullOrBlank()) {
                    continue
                }

                val imageId = cursor.getInt(imageIdColumn)
                val bucketId = cursor.getInt(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: ""
                val dateTaken = cursor.getLong(dateColumn)
                val orientation = cursor.getInt(orientationColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val size = cursor.getLong(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageId.toLong()
                )

                // Stores column values and the contentUri in a local object
                // that represents the media file.
                val image = Image(imageId, contentUri, bucketName, size, dateTaken)
                list += image
            }
            galleryPickedImageList.postValue(list)
        }
    }

    fun selectGallery(selectedList: List<Image>) {
        launch {
            withContext(Dispatchers.Default) {
                selectedList
                    .map {
                        ImageInfo(it.uri)
                    }
                    .takeIf {
                        it.isNotEmpty()
                    }?.let {
                        updateImageListInternal(it)
                    }
            }
        }
    }

    fun resetGalleryData() {
        launch {
            withContext(Dispatchers.Default) {
                val iterator = galleryPickedImageList.value?.iterator() ?: return@withContext
                while (iterator.hasNext()) {
                    val image = iterator.next()
                    image.check = false
                }
            }
        }
    }

    fun goTemplate() {
        viewModelScope.launch {
            uiState.emit(UiState.GoTemplate)
        }
    }

    fun resetEditDialog() {
        viewModelScope.launch {
            uiState.emit(UiState.None)
        }
    }

    fun goTemplateEdit() {
        viewModelScope.launch {
            uiState.emit(UiState.GoEdit)
        }
    }

    fun useTemplate(template: Template) {
        viewModelScope.launch {
            uiState.emit(UiState.UseTemplate(template))
        }
    }

    fun goEditDialog() {
        viewModelScope.launch {
            uiState.emit(UiState.GoEditDialog)
        }
    }

    companion object {
        const val TYPE_ERROR_NOT_IMG = "type_error_not_img"
        const val TYPE_ERROR_FILE_NOT_FOUND = "type_error_file_not_found"
        const val TYPE_ERROR_SAVE_OOM = "type_error_save_oom"
        const val TYPE_ERROR_SAVE_UNKNOWN = "type_error_save_unknown"
        const val TYPE_ERROR_SAVE_MEDIASTORE_INSERT = "type_error_save_mediastore_insert"
        const val TYPE_ERROR_SAVE_MEDIASTORE_WRITE = "type_error_save_mediastore_write"
        const val TYPE_COMPRESS_ERROR = "type_CompressError"
        const val TYPE_COMPRESS_OK = "type_CompressOK"
        const val TYPE_COMPRESSING = "type_Compressing"
        const val TYPE_SAVING = "type_saving"
        const val TYPE_JOB_FINISH = "type_job_finish"

        /** ENH-01: user bấm huỷ giữa batch export (WorkManager cancel). */
        const val TYPE_ERROR_CANCELLED = "type_error_cancelled"
    }
}
