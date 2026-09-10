package com.mckimquyen.watermark.ui
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.JobStateResolver
import com.mckimquyen.watermark.data.model.MediaStoreInsertResolver
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
import com.mckimquyen.watermark.ui.widget.WaterMarkImageView
import com.mckimquyen.watermark.utils.FileUtils.Companion.outPutFolderName
import com.mckimquyen.watermark.utils.bitmap.calculateInSampleSize
import com.mckimquyen.watermark.utils.bitmap.decodeBitmapFromUri
import com.mckimquyen.watermark.utils.bitmap.decodeSampledBitmapFromResource
import com.mckimquyen.watermark.utils.ktx.applyConfig
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
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userRepo: UserConfigRepository,
    private val waterMarkRepo: WaterMarkRepository,
    private val memorySettingRepo: MemorySettingRepo,
    private val templateRepo: TemplateRepository
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

    private val saveImageUri: MutableLiveData<List<ImageInfo>> = MutableLiveData()

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

    private var matrixValues = FloatArray(9)

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

    fun saveImage(
        contentResolver: ContentResolver,
        viewInfo: ViewInfo,
        imageList: List<ImageInfo>
    ) {
        viewModelScope.launch {
            if (this@MainViewModel.imageList.value?.first.isNullOrEmpty()) {
                saveResult.value = Result.failure(data = null, code = TYPE_ERROR_NOT_IMG)
                return@launch
            }
            saveResult.value =
                Result.success(null, code = TYPE_SAVING)
            val result = generateList(contentResolver, viewInfo, imageList)
            if (result.isFailure()) {
                saveResult.value = Result.failure(data = null, code = TYPE_ERROR_FILE_NOT_FOUND)
                return@launch
            }
            saveImageUri.value = result.data!!
            saveResult.value = Result.success(code = TYPE_JOB_FINISH, data = result.data)
        }
    }

    private suspend fun generateList(
        contentResolver: ContentResolver,
        viewInfo: ViewInfo,
        infoList: List<ImageInfo>?
    ): Result<List<ImageInfo>> =
        withContext(Dispatchers.Default) {
            if (infoList.isNullOrEmpty()) {
                return@withContext Result.failure(null, TYPE_ERROR_NOT_IMG)
            }
            infoList.forEachIndexed { index, info ->
                try {
                    info.jobState = JobState.Ing
                    launch(Dispatchers.Main) { saveProcess.value = info }
                    info.result = generateImage(contentResolver, viewInfo, info, index)
                    // generateImage() có thể trả Result.failure (không throw) khi lỗi I/O/logic —
                    // JobStateResolver kiểm tra isFailure() thay vì luôn coi là Success (BUG-03).
                    info.jobState = JobStateResolver.resolve(info.result)
                    launch(Dispatchers.Main) { saveProcess.value = info }
                } catch (fne: FileNotFoundException) {
                    fne.printStackTrace()
                    info.result = Result.failure(null, code = TYPE_ERROR_FILE_NOT_FOUND)
                    info.jobState = JobState.Failure(info.result!!)
                    saveProcess.postValue(info)
                } catch (oom: OutOfMemoryError) {
                    info.result = Result.failure(null, code = TYPE_ERROR_SAVE_OOM)
                    info.jobState = JobState.Failure(info.result!!)
                    saveProcess.postValue(info)
                } catch (e: Exception) {
                    // Exception ngoài 2 loại trên (vd SecurityException khi mất quyền MediaStore
                    // giữa batch) trước đây không có handler, làm crash cả batch — chỉ đánh dấu
                    // ảnh này lỗi và tiếp tục ảnh kế tiếp.
                    e.printStackTrace()
                    info.result = Result.failure(null, code = TYPE_ERROR_SAVE_UNKNOWN, message = e.message)
                    info.jobState = JobState.Failure(info.result!!)
                    saveProcess.postValue(info)
                }
                Log.i("generateList", "${info.uri} : ${info.result}")
            }
            // reset process state
            saveProcess.postValue(null)
            return@withContext Result.success(infoList)
        }

    private suspend fun generateImage(
        contentResolver: ContentResolver,
        viewInfo: ViewInfo,
        imageInfo: ImageInfo,
        index: Int
    ): Result<Uri> =
        withContext(Dispatchers.IO) {
            val rect = decodeBitmapFromUri(appContext, contentResolver, imageInfo.uri)
            if (rect.isFailure()) {
                return@withContext Result.extendMsg(rect)
            }
            val mutableBitmap = rect.data?.bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                ?: return@withContext Result.failure(
                    data = null,
                    code = "-1",
                    message = "Copy bitmap from uri failed."
                )
            // rect.data.bitmap không cache/chia sẻ nơi khác (decodeBitmapFromUri không qua
            // BitmapCache) — đã copy xong sang mutableBitmap nên recycle ngay, tránh giữ 2 bitmap
            // full-res cùng lúc khi xử lý batch nhiều ảnh (BUG-05).
            rect.data?.bitmap?.let { original ->
                if (original !== mutableBitmap && !original.isRecycled) original.recycle()
            }

            val inSample = calculateInSampleSize(
                width = mutableBitmap.width,
                height = mutableBitmap.height,
                reqWidth = WaterMarkImageView.calculateDrawLimitWidth(viewInfo.width, viewInfo.paddingLeft),
                reqHeight = WaterMarkImageView.calculateDrawLimitHeight(viewInfo.height, viewInfo.paddingRight)
            )
            imageInfo.width = mutableBitmap.width
            imageInfo.height = mutableBitmap.height
            imageInfo.exifModel = rect.data?.exifModel
            val tmpConfig = waterMark.value ?: return@withContext Result.failure(
                data = null,
                code = "-1",
                message = "config.value == null"
            )
            imageInfo.inSample = inSample
            val canvas = Canvas(mutableBitmap)
            // generate matrix of drawable
            val imageMatrix = WaterMarkImageView.adjustMatrix(
                srcMatrix = Matrix(),
                viewWidth = viewInfo.width,
                viewHeight = viewInfo.height,
                paddingLeft = viewInfo.paddingLeft,
                paddingTop = viewInfo.paddingTop,
                bitmapWidth = imageInfo.width,
                bitmapHeight = imageInfo.height
            )
            Log.i(
                "generateImage",
                """
                    imageMatrix = $imageMatrix,
                    inSample = $inSample,
                    imageInfo = $imageInfo
                    viewInfo = $viewInfo,
                    bitmapW = ${mutableBitmap.width}
                    bitmapH = ${mutableBitmap.height},
                """.trimIndent()
            )
            // calculate the scale factor
            imageMatrix.getValues(matrixValues)
            imageInfo.scaleX = 1 / matrixValues[Matrix.MSCALE_X]
            imageInfo.scaleY = 1 / matrixValues[Matrix.MSCALE_X]
            val bitmapPaint = TextPaint().applyConfig(imageInfo, tmpConfig, isScale = false)
            val layoutPaint = Paint()
            val shader = when (waterMark.value?.markMode) {
                WaterMarkRepository.MarkMode.Text -> {
                    // Resolve dynamic text tokens (e.g. {date}, {filename}, {iso}) per image at export time.
                    val resolvedText = resolveTextTokens(tmpConfig.text, imageInfo, contentResolver, index)
                    WaterMarkImageView.buildTextBitmapShader(
                        imageInfo = imageInfo,
                        config = tmpConfig.copy(text = resolvedText),
                        textPaint = bitmapPaint,
                        coroutineContext = Dispatchers.IO
                    )
                }

                WaterMarkRepository.MarkMode.Image -> {
                    val iconBitmapRect = decodeSampledBitmapFromResource(
                        context = appContext,
                        resolver = contentResolver,
                        uri = tmpConfig.iconUri,
                        reqWidth = viewInfo.width,
                        reqHeight = viewInfo.height
                    )
                    if (iconBitmapRect.isFailure() || iconBitmapRect.data == null) {
                        return@withContext Result.failure(
                            data = null,
                            code = "-1",
                            message = "decodeSampledBitmapFromResource == null"
                        )
                    }
                    val iconBitmap = iconBitmapRect.data!!.bitmap!!
                    WaterMarkImageView.buildIconBitmapShader(
                        imageInfo = imageInfo,
                        srcBitmap = iconBitmap,
                        config = tmpConfig,
                        textPaint = bitmapPaint,
                        scale = true,
                        coroutineContext = Dispatchers.IO
                    )
                }

                null -> return@withContext Result.failure(
                    data = null,
                    code = "-1",
                    message = "Unknown markmode"
                )
            }

            layoutPaint.shader = shader?.bitmapShader

            if (imageInfo.obtainTileMode() == Shader.TileMode.CLAMP) {
                canvas.translate(
                    0 + imageInfo.offsetX * mutableBitmap.width,
                    0 + imageInfo.offsetY * mutableBitmap.height
                )
                canvas.drawRect(
                    /* left = */ 0f,
                    /* top = */ 0f,
                    /* right = */ (shader?.width ?: 0).toFloat(),
                    /* bottom = */ (shader?.height ?: 0).toFloat(),
                    /* paint = */ layoutPaint
                )
            } else {
                canvas.drawRect(
                    /* left = */ 0f,
                    /* top = */ 0f,
                    /* right = */ mutableBitmap.width.toFloat(),
                    /* bottom = */ mutableBitmap.height.toFloat(),
                    /* paint = */ layoutPaint
                )
            }

            val finalExportBitmap = if (tmpConfig.enableExif && imageInfo.exifModel != null && !imageInfo.exifModel!!.isEmpty()) {
                val expandedBitmap = buildExifBorderBitmap(
                    source = mutableBitmap,
                    eModel = imageInfo.exifModel!!,
                    style = ExifFrameStyle.obtain(tmpConfig.exifFrameStyle),
                    // FEAT-14 Custom Frame Builder — null nếu user chưa tuỳ chỉnh, giữ hành vi gốc.
                    bandColor = tmpConfig.exifBandColor,
                    bandThicknessPercent = tmpConfig.exifBandThicknessPercent,
                    useSerifCaption = tmpConfig.exifUseSerifCaption
                )
                // mutableBitmap đã được vẽ (drawBitmap) sang expandedBitmap, không còn dùng nữa
                // (finalExportBitmap trỏ sang expandedBitmap) — recycle để tránh giữ 2 bitmap
                // full-res cùng lúc (BUG-05).
                mutableBitmap.recycle()
                expandedBitmap
            } else {
                mutableBitmap
            }

            // Resize cạnh dài khi lưu (0 = giữ nguyên kích thước gốc).
            val exportBitmap = com.mckimquyen.watermark.utils.bitmap.OutputImageUtils.resizeIfNeeded(
                finalExportBitmap,
                maxOutputLongEdge
            )
            // resizeIfNeeded trả về CÙNG instance khi maxOutputLongEdge=0 (không resize) — chỉ
            // recycle finalExportBitmap khi thực sự đã tạo bitmap mới, tránh recycle nhầm bitmap
            // đang dùng (BUG-05).
            if (exportBitmap !== finalExportBitmap && !finalExportBitmap.isRecycled) {
                finalExportBitmap.recycle()
            }

            return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val imageCollection =
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val imageDetail = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        generateOutputName(contentResolver, imageInfo, index)
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "image/${trapOutputExtension()}")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$outPutFolderName/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val insertResult = MediaStoreInsertResolver.resolve(
                    contentResolver.insert(imageCollection, imageDetail),
                    TYPE_ERROR_SAVE_MEDIASTORE_INSERT
                )
                if (insertResult.isFailure()) return@withContext insertResult
                val imageContentUri = insertResult.data!!
                contentResolver.openFileDescriptor(imageContentUri, "w", null).use { pfd ->
                    exportBitmap.compress(
                        /* format = */ outputFormat,
                        /* quality = */ compressLevel,
                        /* stream = */ FileOutputStream(pfd!!.fileDescriptor)
                    )
                }
                // Đã compress xong, không còn dùng bitmap này nữa (BUG-05).
                exportBitmap.recycle()
                imageDetail.clear()
                imageDetail.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageContentUri, imageDetail, null, null)
                applyCopyrightExif(contentResolver, imageContentUri)
                Result.success(imageContentUri)
            } else {
                // need request write_storage permission
                // should check Pictures folder exist
                val picturesFile: File =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        ?: return@withContext Result.failure(
                            data = null,
                            code = "-1",
                            message = "Can't get pictures directory."
                        )
                if (!picturesFile.exists()) {
                    picturesFile.mkdir()
                }
                val mediaDir = File(picturesFile, outPutFolderName)

                if (!mediaDir.exists()) {
                    mediaDir.mkdirs()
                }
                val outputFile = File(mediaDir, generateOutputName(contentResolver, imageInfo, index))
                outputFile.outputStream().use { fileOutputStream ->
                    exportBitmap.compress(
                        /* format = */ outputFormat,
                        /* quality = */ compressLevel,
                        /* stream = */ fileOutputStream
                    )
                }
                // Đã compress xong, không còn dùng bitmap này nữa (BUG-05).
                exportBitmap.recycle()
                applyCopyrightExif(outputFile.absolutePath)
                val outputUri = FileProvider.getUriForFile(
                    /* context = */ appContext,
                    /* authority = */ "${BuildConfig.APPLICATION_ID}.fileprovider",
                    /* file = */ outputFile
                )
                appContext.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(outputFile)
                    )
                )
                Result.success(outputUri)
            }
        }

    /**
     * Resolve dynamic text tokens in the watermark text for a given image, per-image at export time
     * so batch jobs get per-photo values. No-op when the text has no '{' token.
     * Supported: {filename} {seq} {date} {model} {make} {iso} {fnumber} {exposure} {focal} {exif}
     */
    private fun resolveTextTokens(
        text: String,
        imageInfo: ImageInfo,
        contentResolver: ContentResolver,
        index: Int
    ): String {
        if (!text.contains('{')) return text
        val exif = imageInfo.exifModel
        val date = exif?.dateTime?.takeIf { it.isNotBlank() }
            ?: System.currentTimeMillis().formatDate("yyyy-MM-dd")
        val tokens = mapOf(
            "filename" to queryDisplayName(contentResolver, imageInfo.uri),
            "seq" to (index + 1).toString(),
            "date" to date,
            "model" to exif?.getCameraName().orEmpty(),
            "make" to exif?.make.orEmpty(),
            "iso" to exif?.iso.orEmpty(),
            "fnumber" to exif?.fNumber.orEmpty(),
            "exposure" to exif?.exposureTime.orEmpty(),
            "focal" to exif?.focalLength.orEmpty(),
            "exif" to exif?.getFormattedExif().orEmpty()
        )
        return com.mckimquyen.watermark.utils.TextTokenResolver.resolve(text, tokens)
    }

    /**
     * Resolve token cho preview trong editor (không phải export) — dùng đúng logic/token với
     * [resolveTextTokens], nhưng lấy index từ vị trí thật của ảnh trong danh sách batch và
     * `appContext.contentResolver` thay vì contentResolver truyền từ export flow.
     * No-op khi text không chứa '{' (fast path, không query filename mỗi lần gõ phím).
     */
    fun resolvePreviewText(text: String, imageInfo: ImageInfo): String {
        if (!text.contains('{')) return text
        val index = waterMarkRepo.imageInfoList.indexOfFirst { it.uri == imageInfo.uri }.coerceAtLeast(0)
        return resolveTextTokens(text, imageInfo, appContext.contentResolver, index)
    }

    private var lastDisplayName: Pair<Uri, String>? = null

    /** Cache theo uri hiện tại — preview gọi lại nhiều lần (mỗi ký tự gõ) không query lặp ContentResolver. */
    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String {
        lastDisplayName?.let { (cachedUri, cachedName) -> if (cachedUri == uri) return cachedName }
        val name = try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    (if (nameIndex >= 0) cursor.getString(nameIndex) else null)?.substringBeforeLast('.')
                } else {
                    null
                }
            } ?: uri.lastPathSegment?.substringBeforeLast('.').orEmpty()
        } catch (e: Exception) {
            uri.lastPathSegment?.substringBeforeLast('.').orEmpty()
        }
        lastDisplayName = uri to name
        return name
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
    ): String {
        val pattern = outputNamePattern.trim()
        val base = if (pattern.isEmpty()) {
            "ewm_${System.currentTimeMillis()}"
        } else {
            resolveTextTokens(pattern, imageInfo, contentResolver, index)
        }
        return "$base.${trapOutputExtension()}"
    }

    private fun trapOutputExtension(): String {
        return com.mckimquyen.watermark.utils.bitmap.OutputImageUtils.extensionFor(outputFormat)
    }

    /** Định dạng có hỗ trợ ghi EXIF (androidx ExifInterface): JPEG / WEBP / PNG. */
    private fun supportsExifWrite(): Boolean = outputFormat != Bitmap.CompressFormat.PNG

    /** Nhúng copyright vào EXIF cho ảnh đã lưu qua MediaStore (Android Q+). */
    private fun applyCopyrightExif(contentResolver: ContentResolver, uri: Uri) {
        val text = copyright.trim()
        if (text.isEmpty() || !supportsExifWrite()) return
        try {
            contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT, text)
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ARTIST, text)
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Nhúng copyright vào EXIF cho ảnh lưu theo đường dẫn file (Android < Q). */
    private fun applyCopyrightExif(filePath: String) {
        val text = copyright.trim()
        if (text.isEmpty() || !supportsExifWrite()) return
        try {
            val exif = androidx.exifinterface.media.ExifInterface(filePath)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT, text)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ARTIST, text)
            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        Log.d(LOG_TAG, "[VM] updateIcon called: uri=$iconUri  empty=${iconUri.toString().isEmpty()}")
        launch {
            if (iconUri.toString().isNotEmpty()) {
                Log.d(LOG_TAG, "[VM] waterMarkRepo.updateIcon() \u2192 uri=$iconUri")
                waterMarkRepo.updateIcon(iconUri)
                Log.d(LOG_TAG, "[VM] waterMarkRepo.updateIcon() done")
            } else {
                Log.d(LOG_TAG, "[VM] updateIcon: uri is EMPTY, skip")
            }
        }
    }

    /**
     * Vẽ khung EXIF theo [style] lên canvas mở rộng từ [source] — chỉ dùng Canvas thuần
     * (chữ + hình khối), không dùng logo hãng máy thật để tránh rủi ro bản quyền/trademark.
     *
     * FEAT-14 Custom Frame Builder: [bandColor]/[bandThicknessPercent]/[useSerifCaption] override
     * nhẹ lên style đang chọn — `null` (mặc định) giữ NGUYÊN hành vi gốc của từng style,
     * không đổi output nếu user chưa tuỳ chỉnh gì.
     */
    internal fun buildExifBorderBitmap(
        source: Bitmap,
        eModel: ExifModel,
        style: ExifFrameStyle,
        bandColor: Int? = null,
        bandThicknessPercent: Float? = null,
        useSerifCaption: Boolean? = null
    ): Bitmap {
        return when (style) {
            ExifFrameStyle.CLASSIC -> buildClassicExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
            ExifFrameStyle.POLAROID -> buildPolaroidExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
            ExifFrameStyle.FILM_STRIP -> buildFilmStripExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
            ExifFrameStyle.MINIMAL -> buildMinimalExifBorder(source, eModel, bandColor, bandThicknessPercent, useSerifCaption)
        }
    }

    /** null → font mặc định của style (không bold); true → serif; false → sans-serif ép buộc. */
    private fun captionTypefaceBase(useSerifCaption: Boolean?, styleDefault: Typeface): Typeface = when (useSerifCaption) {
        true -> Typeface.SERIF
        false -> Typeface.SANS_SERIF
        null -> styleDefault
    }

    /** Thanh trắng dưới đáy: tên máy đậm trái, thông số + ngày phải (hành vi gốc). */
    private fun buildClassicExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val borderHeight = (source.height * (bandThicknessPercent ?: ExifFrameStyle.CLASSIC.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val expanded = Bitmap.createBitmap(source.width, source.height + borderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.CLASSIC.defaultBandColor)
        canvas.drawBitmap(source, 0f, 0f, null)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = borderHeight * 0.35f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(captionTypefaceBase(useSerifCaption, Typeface.DEFAULT), Typeface.BOLD)
        }
        canvas.drawText(eModel.getCameraName(), source.width * 0.05f, source.height + borderHeight * 0.5f, textPaint)

        textPaint.textSize = borderHeight * 0.22f
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText(eModel.getFormattedExif(), source.width * 0.95f, source.height + borderHeight * 0.45f, textPaint)

        textPaint.textSize = borderHeight * 0.18f
        textPaint.color = Color.DKGRAY
        canvas.drawText(eModel.dateTime, source.width * 0.95f, source.height + borderHeight * 0.75f, textPaint)
        return expanded
    }

    /** Viền trắng dày đều 4 cạnh kiểu ảnh Polaroid, caption căn giữa ở đáy. */
    private fun buildPolaroidExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val sideBorder = (minOf(source.width, source.height) * 0.05f).toInt()
        val bottomBorder = (source.height * (bandThicknessPercent ?: ExifFrameStyle.POLAROID.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val totalWidth = source.width + sideBorder * 2
        val totalHeight = source.height + sideBorder + bottomBorder
        val expanded = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.POLAROID.defaultBandColor)
        canvas.drawBitmap(source, sideBorder.toFloat(), sideBorder.toFloat(), null)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            // Polaroid mặc định VỐN đã là serif — captionTypefaceBase(null) trả về styleDefault = SERIF.
            typeface = Typeface.create(captionTypefaceBase(useSerifCaption, Typeface.SERIF), Typeface.NORMAL)
        }
        textPaint.textSize = bottomBorder * 0.32f
        canvas.drawText(eModel.getCameraName(), totalWidth / 2f, source.height + sideBorder + bottomBorder * 0.55f, textPaint)

        textPaint.textSize = bottomBorder * 0.2f
        textPaint.color = Color.DKGRAY
        val detail = listOfNotNull(
            eModel.getFormattedExif().takeIf { it.isNotEmpty() },
            eModel.dateTime.takeIf { it.isNotEmpty() }
        ).joinToString("   ·   ")
        canvas.drawText(detail, totalWidth / 2f, source.height + sideBorder + bottomBorder * 0.85f, textPaint)
        return expanded
    }

    /** Dải đen trên/dưới có lỗ sprocket như phim máy ảnh; caption phủ scrim mờ ở đáy ảnh. */
    private fun buildFilmStripExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val bandHeight = (source.height * (bandThicknessPercent ?: ExifFrameStyle.FILM_STRIP.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val totalHeight = source.height + bandHeight * 2
        val expanded = Bitmap.createBitmap(source.width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.FILM_STRIP.defaultBandColor)
        canvas.drawBitmap(source, 0f, bandHeight.toFloat(), null)

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val holeSize = bandHeight * 0.42f
        val holeRadius = holeSize * 0.25f
        val holeGap = holeSize * 1.7f
        val holeCount = (source.width / holeGap).toInt().coerceAtLeast(2)
        fun drawHoleRow(centerY: Float) {
            for (i in 0 until holeCount) {
                val cx = holeGap * 0.5f + i * holeGap
                canvas.drawRoundRect(
                    cx - holeSize / 2f,
                    centerY - holeSize / 2f,
                    cx + holeSize / 2f,
                    centerY + holeSize / 2f,
                    holeRadius,
                    holeRadius,
                    holePaint
                )
            }
        }
        drawHoleRow(bandHeight * 0.5f)
        drawHoleRow(totalHeight - bandHeight * 0.5f)

        // Scrim mờ phủ đáy ảnh thật (không phải dải đen) để chữ không đè lên lỗ sprocket.
        val scrimHeight = bandHeight * 1.1f
        val scrimTop = bandHeight + source.height - scrimHeight
        canvas.drawRect(
            0f,
            scrimTop,
            source.width.toFloat(),
            (bandHeight + source.height).toFloat(),
            Paint().apply {
                color = Color.argb(140, 0, 0, 0)
            }
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(captionTypefaceBase(useSerifCaption, Typeface.DEFAULT), Typeface.BOLD)
            textSize = bandHeight * 0.34f
        }
        canvas.drawText(eModel.getCameraName(), source.width * 0.04f, bandHeight + source.height - scrimHeight * 0.45f, textPaint)
        textPaint.textSize = bandHeight * 0.22f
        textPaint.typeface = Typeface.DEFAULT
        val detail = listOfNotNull(
            eModel.getFormattedExif().takeIf { it.isNotEmpty() },
            eModel.dateTime.takeIf { it.isNotEmpty() }
        ).joinToString("  ·  ")
        canvas.drawText(detail, source.width * 0.04f, bandHeight + source.height - scrimHeight * 0.15f, textPaint)
        return expanded
    }

    /** Dải trắng mỏng + 1 dòng chữ gọn (tên máy · thông số · ngày), gọn nhẹ hơn CLASSIC. */
    private fun buildMinimalExifBorder(
        source: Bitmap,
        eModel: ExifModel,
        bandColor: Int?,
        bandThicknessPercent: Float?,
        useSerifCaption: Boolean?
    ): Bitmap {
        val borderHeight = (source.height * (bandThicknessPercent ?: ExifFrameStyle.MINIMAL.defaultBandThicknessPercent)).toInt().coerceAtLeast(1)
        val expanded = Bitmap.createBitmap(source.width, source.height + borderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(expanded)
        canvas.drawColor(bandColor ?: ExifFrameStyle.MINIMAL.defaultBandColor)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.drawLine(
            0f,
            source.height.toFloat(),
            source.width.toFloat(),
            source.height.toFloat(),
            Paint().apply {
                color = Color.LTGRAY
                strokeWidth = borderHeight * 0.03f
            }
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = borderHeight * 0.4f
            textAlign = Paint.Align.LEFT
            typeface = captionTypefaceBase(useSerifCaption, Typeface.DEFAULT)
        }
        val line = listOfNotNull(
            eModel.getCameraName().takeIf { it.isNotEmpty() && it != "Unknown Device" },
            eModel.getFormattedExif().takeIf { it.isNotEmpty() },
            eModel.dateTime.takeIf { it.isNotEmpty() }
        ).joinToString("   ")
        canvas.drawText(line, source.width * 0.03f, source.height + borderHeight * 0.65f, textPaint)
        return expanded
    }

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
        imageList.value?.first?.forEach {
            it.jobState = JobState.Ready
            saveProcess.value = it
        }
    }

    fun clearData() {
        launch {
            waterMarkRepo.select(Uri.EMPTY)
        }
    }

    fun compressImg(activity: Activity) {
        val appContext = activity.applicationContext
        compressedJob = viewModelScope.launch(Dispatchers.IO) {
            waterMark.value?.let {
                compressedResult.postValue(Result.success(null, code = TYPE_COMPRESSING))
                val tmpFile = File.createTempFile("easy_water_mark_", "_compressed")
                appContext.contentResolver.openInputStream(waterMarkRepo.imageInfoList.first().uri)
                    .use { input ->
                        tmpFile.outputStream().use { output ->
                            input?.copyTo(output)
                        }
                    }
                val compressedFile = Compressor.compress(appContext, tmpFile)
                // clear tmp files
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
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
            } ?: kotlin.run {
                compressedResult.postValue(
                    Result.failure(
                        null,
                        code = TYPE_COMPRESS_ERROR,
                        message = "Config value is null."
                    )
                )
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
        const val TYPE_COMPRESS_ERROR = "type_CompressError"
        const val TYPE_COMPRESS_OK = "type_CompressOK"
        const val TYPE_COMPRESSING = "type_Compressing"
        const val TYPE_SAVING = "type_saving"
        const val TYPE_JOB_FINISH = "type_job_finish"
    }
}
