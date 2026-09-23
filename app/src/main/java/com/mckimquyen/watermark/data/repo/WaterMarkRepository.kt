package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.graphics.Color
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import androidx.collection.ArrayMap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_ALPHA
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_DEGREE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_ENABLE_BOUNDS
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_HORIZON_GAP
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_ICON_URI
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_MODE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_RECENT_ICON_URIS
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_TEXT
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_TEXT_COLOR
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_TEXT_SIZE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_TEXT_STYLE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_TEXT_TYPEFACE
import com.mckimquyen.watermark.data.repo.WaterMarkRepository.PreferenceKeys.KEY_VERTICAL_GAP
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WaterMarkRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Named("WaterMarkPreferences") private val dataStore: DataStore<Preferences>
) {

    private object PreferenceKeys {
        val KEY_TEXT = stringPreferencesKey(SP_KEY_TEXT)
        val KEY_TEXT_SIZE = floatPreferencesKey(SP_KEY_TEXT_SIZE)
        val KEY_TEXT_COLOR = intPreferencesKey(SP_KEY_TEXT_COLOR)
        val KEY_TEXT_STYLE = intPreferencesKey(SP_KEY_TEXT_STYLE)
        val KEY_TEXT_TYPEFACE = intPreferencesKey(SP_KEY_TEXT_TYPEFACE)
        val KEY_ALPHA = intPreferencesKey(SP_KEY_ALPHA)
        val KEY_HORIZON_GAP = intPreferencesKey(SP_KEY_HORIZON_GAP)
        val KEY_VERTICAL_GAP = intPreferencesKey(SP_KEY_VERTICAL_GAP)
        val KEY_DEGREE = floatPreferencesKey(SP_KEY_DEGREE)
        val KEY_ICON_URI = stringPreferencesKey(SP_KEY_ICON_URI)

        //        val KEY_URI = stringPreferencesKey(SP_KEY_URI)
        val KEY_MODE = intPreferencesKey(SP_KEY_WATERMARK_MODE)
        val KEY_ENABLE_BOUNDS = booleanPreferencesKey(SP_KEY_ENABLE_BOUNDS)
        val KEY_ENABLE_EXIF = booleanPreferencesKey(SP_KEY_ENABLE_EXIF)
        val KEY_EXIF_FRAME_STYLE = intPreferencesKey(SP_KEY_EXIF_FRAME_STYLE)
        val KEY_ANCHOR = intPreferencesKey(SP_KEY_ANCHOR)
        val KEY_MARGIN = floatPreferencesKey(SP_KEY_MARGIN)

        // FEAT-23: preset anchor/margin RIÊNG theo orientation ảnh (dọc/ngang) — [KEY_ANCHOR]/
        // [KEY_MARGIN] ở trên vẫn là giá trị ĐANG ÁP DỤNG (không đổi tên/hành vi, mọi nơi đọc
        // `WaterMark.anchor`/`marginPercent` không cần biết gì về preset), 4 key dưới chỉ là "bộ nhớ"
        // để tự động khôi phục đúng preset mỗi khi đổi ảnh khác orientation.
        val KEY_ANCHOR_PORTRAIT = intPreferencesKey(SP_KEY_ANCHOR_PORTRAIT)
        val KEY_MARGIN_PORTRAIT = floatPreferencesKey(SP_KEY_MARGIN_PORTRAIT)
        val KEY_ANCHOR_LANDSCAPE = intPreferencesKey(SP_KEY_ANCHOR_LANDSCAPE)
        val KEY_MARGIN_LANDSCAPE = floatPreferencesKey(SP_KEY_MARGIN_LANDSCAPE)
        val KEY_EXIF_BAND_COLOR = intPreferencesKey(SP_KEY_EXIF_BAND_COLOR)
        val KEY_EXIF_BAND_THICKNESS = floatPreferencesKey(SP_KEY_EXIF_BAND_THICKNESS)
        val KEY_EXIF_SERIF_CAPTION = booleanPreferencesKey(SP_KEY_EXIF_SERIF_CAPTION)
        val KEY_TEXT_EFFECT_STROKE = booleanPreferencesKey(SP_KEY_TEXT_EFFECT_STROKE)
        val KEY_TEXT_EFFECT_SHADOW = booleanPreferencesKey(SP_KEY_TEXT_EFFECT_SHADOW)
        val KEY_TEXT_EFFECT_PILL_BACKGROUND = booleanPreferencesKey(SP_KEY_TEXT_EFFECT_PILL_BACKGROUND)
        val KEY_RECENT_ICON_URIS = stringPreferencesKey(SP_KEY_RECENT_ICON_URIS)
//        val KEY_TILE_MODE = intPreferencesKey(SP_KEY_TILE_MODEL)
//        val KEY_OFFSET_X = floatPreferencesKey(SP_KEY_OFFSET_X)
//        val KEY_OFFSET_Y = floatPreferencesKey(SP_KEY_OFFSET_Y)
    }

    private val _selectedImage = MutableStateFlow(ImageInfo.empty())

    val selectedImage: StateFlow<ImageInfo> = _selectedImage

    val waterMark: Flow<WaterMark> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map {
            WaterMark(
                text = it[KEY_TEXT] ?: appContext.getString(R.string.config_default_water_mark_text),
                textSize = (it[KEY_TEXT_SIZE] ?: 14f).coerceAtLeast(1f),
                textColor = it[KEY_TEXT_COLOR] ?: Color.parseColor("#FFB800"),
                textStyle = TextPaintStyle.obtainSealedClass(it[KEY_TEXT_STYLE] ?: 0),
                textTypeface = TextTypeface.obtainSealedClass(it[KEY_TEXT_TYPEFACE] ?: 0),
                alpha = it[KEY_ALPHA] ?: 255,
                degree = it[KEY_DEGREE] ?: 315f,
                hGap = it[KEY_HORIZON_GAP] ?: 0,
                vGap = it[KEY_VERTICAL_GAP] ?: 0,
                iconUri = Uri.parse(it[KEY_ICON_URI] ?: ""),
                markMode = if (it[PreferenceKeys.KEY_MODE] == MarkMode.Image.value) MarkMode.Image else MarkMode.Text,
                enableBounds = it[PreferenceKeys.KEY_ENABLE_BOUNDS] ?: false,
                enableExif = it[PreferenceKeys.KEY_ENABLE_EXIF] ?: false,
                exifFrameStyle = it[PreferenceKeys.KEY_EXIF_FRAME_STYLE] ?: ExifFrameStyle.CLASSIC.ordinal,
                anchor = it[PreferenceKeys.KEY_ANCHOR] ?: Anchor.CENTER.ordinal,
                marginPercent = it[PreferenceKeys.KEY_MARGIN] ?: DEFAULT_MARGIN_PERCENT,
                exifBandColor = it[PreferenceKeys.KEY_EXIF_BAND_COLOR],
                exifBandThicknessPercent = it[PreferenceKeys.KEY_EXIF_BAND_THICKNESS],
                exifUseSerifCaption = it[PreferenceKeys.KEY_EXIF_SERIF_CAPTION],
                textEffectStroke = it[PreferenceKeys.KEY_TEXT_EFFECT_STROKE] ?: false,
                textEffectShadow = it[PreferenceKeys.KEY_TEXT_EFFECT_SHADOW] ?: false,
                textEffectPillBackground = it[PreferenceKeys.KEY_TEXT_EFFECT_PILL_BACKGROUND] ?: false,
                recentIconUris = parseRecentIconUris(it[KEY_RECENT_ICON_URIS])
            )
        }

    // FEAT-12: Undo/Redo cho chỉnh sửa watermark trong editor.
    private val undoStack = ArrayDeque<WaterMark>()
    private val redoStack = ArrayDeque<WaterMark>()
    private var lastUndoSnapshotAtMs = 0L
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    /**
     * FEAT-12: gọi ở ĐẦU mọi `updateXxx()` có ý nghĩa với Undo/Redo (không gọi trong
     * [applyWaterMark] — cố tình để "áp dụng profile" (FEAT-06) và bản thân [undo]/[redo] không tự
     * đẩy thêm entry, tránh vòng lặp/đẩy chồng lên nhau).
     *
     * Luôn xoá [redoStack] (bất kỳ edit mới nào cũng huỷ nhánh "redo" cũ — đúng ngữ nghĩa undo/redo
     * chuẩn). Việc ĐẨY entry vào [undoStack] thì debounce theo thời gian
     * ([UNDO_SNAPSHOT_DEBOUNCE_MS]) — nhiều lần gọi liên tục trong CÙNG 1 gesture (kéo slider,
     * pinch...) chỉ tạo ra 1 entry duy nhất thay vì 1 entry/frame, tránh stack phình to bất thường
     * (cùng tinh thần debounce ghi DataStore đã áp dụng ở ENH-02, nhưng ở đây debounce cho STACK
     * chứ KHÔNG debounce chính lần ghi DataStore — ghi vẫn xảy ra ngay để preview tức thời).
     */
    private suspend fun snapshotForUndoIfDue() {
        redoStack.clear()
        val now = System.currentTimeMillis()
        if (now - lastUndoSnapshotAtMs < UNDO_SNAPSHOT_DEBOUNCE_MS && undoStack.isNotEmpty()) {
            updateUndoRedoFlags()
            return
        }
        lastUndoSnapshotAtMs = now
        val current = waterMark.first()
        if (undoStack.lastOrNull() != current) {
            undoStack.addLast(current)
            if (undoStack.size > MAX_UNDO_STACK) undoStack.removeFirst()
        }
        updateUndoRedoFlags()
    }

    private fun updateUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /** Hoàn tác thay đổi gần nhất — no-op nếu stack rỗng. */
    suspend fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        val currentBeforeUndo = waterMark.first()
        redoStack.addLast(currentBeforeUndo)
        applyWaterMark(previous)
        updateUndoRedoFlags()
    }

    /** Làm lại thay đổi vừa undo — no-op nếu redo stack rỗng. */
    suspend fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val currentBeforeRedo = waterMark.first()
        undoStack.addLast(currentBeforeRedo)
        applyWaterMark(next)
        updateUndoRedoFlags()
    }

    private val _imageMapFlow: MutableStateFlow<List<ImageInfo>> = MutableStateFlow(emptyList())
    private val imageInfoMap: MutableMap<Uri, Int> = ArrayMap(_imageMapFlow.value.size)

    val imageInfoMapFlow = _imageMapFlow

    val imageInfoList: List<ImageInfo>
        get() = imageInfoMapFlow.value

    suspend fun updateImageList(imageList: List<ImageInfo>) {
        val map = imageList.mapIndexed { index, imageInfo -> imageInfo.uri to index }.toMap()
        imageInfoMap.clear()
        imageInfoMap.putAll(map)
        _imageMapFlow.emit(imageList)
    }

    /**
     * FEAT-13: gán caption riêng cho từng ảnh theo ĐÚNG thứ tự hiện tại của batch (`captions[i]`
     * ứng với ảnh thứ i trong [imageInfoList]) — nguồn từ [com.mckimquyen.watermark.data.model.BatchCaptionParser].
     * `null` tại vị trí nào xoá caption riêng của ảnh đó (dùng lại watermark text chung khi export).
     */
    suspend fun updateImageCaptions(captions: List<String?>) {
        val list = imageInfoList.mapIndexed { index, info -> info.copy(caption = captions.getOrNull(index)) }
        updateImageList(list)
    }

    /**
     * FEAT-17: bật/tắt loại ảnh này khỏi batch export ngay tại grid preview — [BatchExportEngine]
     * bỏ qua (giữ nguyên `JobState.Ready`, không render/ghi file) item có `isSkippedInExport=true`.
     */
    suspend fun toggleSkipExport(uri: Uri) {
        val list = imageInfoList.map { info ->
            if (info.uri == uri) info.copy(isSkippedInExport = !info.isSkippedInExport) else info
        }
        updateImageList(list)
    }

    suspend fun updateText(text: String) {
        snapshotForUndoIfDue()
        dataStore.edit {
            it[KEY_MODE] = MarkMode.Text.value
            it[KEY_TEXT] = text
        }
    }

    suspend fun updateTextSize(size: Float) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_TEXT_SIZE] = size }
    }

    suspend fun updateColor(color: Int) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_TEXT_COLOR] = color }
    }

    suspend fun updateTextStyle(style: TextPaintStyle) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_TEXT_STYLE] = style.serializeKey() }
    }

    suspend fun updateTypeFace(typeface: TextTypeface) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_TEXT_TYPEFACE] = typeface.serializeKey() }
    }

    suspend fun updateAlpha(alpha: Int) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_ALPHA] = alpha.coerceAtLeast(0).coerceAtMost(255) }
    }

    suspend fun updateHorizon(gap: Int) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_HORIZON_GAP] = gap.coerceAtLeast(0).coerceAtMost(MAX_HORIZON_GAP) }
    }

    suspend fun updateVertical(gap: Int) {
        snapshotForUndoIfDue()
        dataStore.edit {
            it[KEY_VERTICAL_GAP] = gap.coerceAtLeast(0).coerceAtMost(MAX_VERTICAL_GAP)
        }
    }

    suspend fun updateDegree(degree: Float) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_DEGREE] = degree.coerceAtLeast(0f).coerceAtMost(MAX_DEGREE) }
    }

    /** FEAT-24: mỗi lần đổi icon, đẩy uri lên đầu danh sách MRU (dùng lại nếu trùng, giới hạn [MAX_RECENT_ICONS]). */
    suspend fun updateIcon(iconUri: Uri) {
        snapshotForUndoIfDue()
        dataStore.edit {
            it[KEY_MODE] = MarkMode.Image.value
            it[KEY_ICON_URI] = iconUri.toString()
            val updatedRecents = pushToFrontOfRecentIcons(
                current = parseRecentIconUris(it[KEY_RECENT_ICON_URIS]).map(Uri::toString),
                newUri = iconUri.toString(),
                maxSize = MAX_RECENT_ICONS
            )
            it[KEY_RECENT_ICON_URIS] = serializeRecentIconUris(updatedRecents)
        }
    }

    suspend fun updateEnableExif(enable: Boolean) {
        snapshotForUndoIfDue()
        dataStore.edit {
            it[PreferenceKeys.KEY_ENABLE_EXIF] = enable
        }
    }

    suspend fun updateExifFrameStyle(style: ExifFrameStyle) {
        snapshotForUndoIfDue()
        dataStore.edit { it[PreferenceKeys.KEY_EXIF_FRAME_STYLE] = style.ordinal }
    }

    suspend fun updateTileMode(imageInfo: ImageInfo, mode: Shader.TileMode): ImageInfo {
        if (imageInfo.tileMode == mode.ordinal) {
            Log.i("WaterMarkRepository", "updateTileMode: same mode")
            return imageInfo
        }
        val index = imageInfoMap[imageInfo.uri] ?: kotlin.run {
            Log.e("WaterMarkRepository", "updateTileMode: imageInfo not found, uri = ${imageInfo.uri}")
            return imageInfo
        }

        val info = imageInfo.copy(tileMode = mode.ordinal)
        val list = ArrayList(imageInfoList)
        list[index] = info
        imageInfoMap[info.uri] = index
        _imageMapFlow.emit(list)
        _selectedImage.emit(info)
        return info
    }

    suspend fun updateOffset(imageInfo: ImageInfo) {
        if (imageInfo == selectedImage.value) {
            return
        }
        val index = imageInfoMap[selectedImage.value.uri] ?: kotlin.run {
//            Log.e("WaterMarkRepository", "updateOffset: imageInfo not found, uri = ${selectedImage.value.uri}")
            return
        }
        val list = ArrayList(imageInfoList)
        list[index] = imageInfo
        imageInfoMap[imageInfo.uri] = index
        _imageMapFlow.emit(list)
        _selectedImage.emit(imageInfo)
    }

    /**
     * Chỉ gọi từ [com.mckimquyen.watermark.MyApplication.onCreate] (reset mode mỗi lần app khởi
     * động lại) — KHÔNG đẩy Undo snapshot (FEAT-12): đây là reset nội bộ lúc khởi động app, không
     * phải hành động chỉnh sửa của user trong editor, không nên hiện ra như 1 bước có thể "Undo".
     */
    suspend fun resetModeToText() {
        dataStore.edit { it[KEY_MODE] = MarkMode.Text.value }
    }

    suspend fun toggleBounds(enable: Boolean) {
        snapshotForUndoIfDue()
        dataStore.edit { it[KEY_ENABLE_BOUNDS] = enable }
    }

    suspend fun updateAnchor(anchor: Anchor) {
        snapshotForUndoIfDue()
        dataStore.edit {
            it[PreferenceKeys.KEY_ANCHOR] = anchor.ordinal
            // FEAT-23: lưu luôn vào preset của orientation ảnh ĐANG chọn (nếu đã biết) — user tự
            // chỉnh vị trí cho ảnh này thì lần sau load lại ảnh CÙNG orientation phải nhớ đúng.
            when (lastKnownIsPortrait) {
                true -> it[PreferenceKeys.KEY_ANCHOR_PORTRAIT] = anchor.ordinal
                false -> it[PreferenceKeys.KEY_ANCHOR_LANDSCAPE] = anchor.ordinal
                null -> Unit
            }
        }
    }

    suspend fun updateMargin(percent: Float) {
        snapshotForUndoIfDue()
        val clamped = percent.coerceIn(MIN_MARGIN_PERCENT, MAX_MARGIN_PERCENT)
        dataStore.edit {
            it[PreferenceKeys.KEY_MARGIN] = clamped
            when (lastKnownIsPortrait) {
                true -> it[PreferenceKeys.KEY_MARGIN_PORTRAIT] = clamped
                false -> it[PreferenceKeys.KEY_MARGIN_LANDSCAPE] = clamped
                null -> Unit
            }
        }
    }

    /**
     * FEAT-23: [isPortrait] = `null` nghĩa là CHƯA biết orientation ảnh đang chọn (chưa decode
     * xong lần nào) — [updateAnchor]/[updateMargin] gọi trước khi biết orientation (hiếm, chỉ có
     * thể xảy ra nếu user bấm nhanh trước khi ảnh đầu tiên decode xong) sẽ không lưu vào preset
     * nào, chỉ ghi giá trị đang áp dụng — không mất dữ liệu, chỉ đơn giản chưa phân loại được.
     */
    private var lastKnownIsPortrait: Boolean? = null

    /**
     * FEAT-23: gọi mỗi khi 1 ảnh MỚI vừa decode xong trong editor và biết được tỉ lệ khung thật
     * (`WaterMarkImageView.onImageOrientationKnown`) — áp lại preset anchor/margin đã lưu riêng
     * cho orientation đó (nếu có, giữ nguyên giá trị đang áp dụng nếu chưa từng lưu preset nào cho
     * orientation này), đồng thời ghi nhớ orientation hiện tại để [updateAnchor]/[updateMargin]
     * sau đó lưu đúng preset. KHÔNG gọi [snapshotForUndoIfDue] — đây là hành động HỆ THỐNG tự động
     * theo ảnh đang xem, không phải 1 chỉnh sửa của user (cùng nguyên tắc đã áp dụng cho
     * `resetModeToText()` ở FEAT-12).
     */
    suspend fun applyOrientationPreset(isPortrait: Boolean) {
        lastKnownIsPortrait = isPortrait
        dataStore.edit { prefs ->
            val anchorKey = if (isPortrait) PreferenceKeys.KEY_ANCHOR_PORTRAIT else PreferenceKeys.KEY_ANCHOR_LANDSCAPE
            val marginKey = if (isPortrait) PreferenceKeys.KEY_MARGIN_PORTRAIT else PreferenceKeys.KEY_MARGIN_LANDSCAPE
            // LUÔN ghi đè (không chỉ khi có preset) — nếu KHÔNG dùng mặc định làm fallback, đổi
            // sang orientation CHƯA từng lưu preset sẽ vô tình giữ nguyên vị trí vừa chỉnh của
            // orientation TRƯỚC ĐÓ (đúng lỗi AC muốn tránh: "vị trí không bị áp nhầm theo preset dọc").
            prefs[PreferenceKeys.KEY_ANCHOR] = prefs[anchorKey] ?: Anchor.CENTER.ordinal
            prefs[PreferenceKeys.KEY_MARGIN] = prefs[marginKey] ?: DEFAULT_MARGIN_PERCENT
        }
    }

    /** FEAT-14 — null = xoá override, quay lại màu mặc định của style đang chọn. */
    suspend fun updateExifBandColor(color: Int?) {
        snapshotForUndoIfDue()
        dataStore.edit {
            if (color == null) it.remove(PreferenceKeys.KEY_EXIF_BAND_COLOR) else it[PreferenceKeys.KEY_EXIF_BAND_COLOR] = color
        }
    }

    /** FEAT-14 — null = xoá override, quay lại tỉ lệ mặc định của style đang chọn. Có giá trị thì clamp trong khoảng an toàn (tránh band cao 0px, xem BUG-17). */
    suspend fun updateExifBandThicknessPercent(percent: Float?) {
        snapshotForUndoIfDue()
        dataStore.edit {
            if (percent == null) {
                it.remove(PreferenceKeys.KEY_EXIF_BAND_THICKNESS)
            } else {
                it[PreferenceKeys.KEY_EXIF_BAND_THICKNESS] =
                    percent.coerceIn(MIN_EXIF_BAND_THICKNESS_PERCENT, MAX_EXIF_BAND_THICKNESS_PERCENT)
            }
        }
    }

    /** FEAT-14 — null = xoá override, quay lại font mặc định của style đang chọn. */
    suspend fun updateExifUseSerifCaption(useSerif: Boolean?) {
        snapshotForUndoIfDue()
        dataStore.edit {
            if (useSerif == null) it.remove(PreferenceKeys.KEY_EXIF_SERIF_CAPTION) else it[PreferenceKeys.KEY_EXIF_SERIF_CAPTION] = useSerif
        }
    }

    /** FEAT-11 — bật/tắt viền tương phản cho text watermark, độc lập với shadow/pill. */
    suspend fun updateTextEffectStroke(enable: Boolean) {
        snapshotForUndoIfDue()
        dataStore.edit { it[PreferenceKeys.KEY_TEXT_EFFECT_STROKE] = enable }
    }

    /** FEAT-11 — bật/tắt đổ bóng cho text watermark, độc lập với stroke/pill. */
    suspend fun updateTextEffectShadow(enable: Boolean) {
        snapshotForUndoIfDue()
        dataStore.edit { it[PreferenceKeys.KEY_TEXT_EFFECT_SHADOW] = enable }
    }

    /** FEAT-11 — bật/tắt nền pill cho text watermark, độc lập với stroke/shadow. */
    suspend fun updateTextEffectPillBackground(enable: Boolean) {
        snapshotForUndoIfDue()
        dataStore.edit { it[PreferenceKeys.KEY_TEXT_EFFECT_PILL_BACKGROUND] = enable }
    }

    /** FEAT-14 — xoá cả 3 override cùng lúc (nút "Reset" trong UI). */
    suspend fun resetExifCustomization() {
        snapshotForUndoIfDue()
        dataStore.edit {
            it.remove(PreferenceKeys.KEY_EXIF_BAND_COLOR)
            it.remove(PreferenceKeys.KEY_EXIF_BAND_THICKNESS)
            it.remove(PreferenceKeys.KEY_EXIF_SERIF_CAPTION)
        }
    }

    /**
     * FEAT-06: áp dụng lại 1 "hồ sơ" watermark đã lưu — ghi TOÀN BỘ field của [mark] trong 1
     * `edit{}` (atomic, tránh nhiều lần `updateXxx()` riêng lẻ khiến các Flow collector khác nhận
     * hàng loạt emit trung gian không nhất quán). KHÔNG đụng [KEY_RECENT_ICON_URIS] — đó là MRU
     * icon dùng chung toàn app (FEAT-24), không thuộc về riêng 1 profile.
     */
    suspend fun applyWaterMark(mark: WaterMark) {
        dataStore.edit {
            it[KEY_TEXT] = mark.text
            it[KEY_TEXT_SIZE] = mark.textSize
            it[KEY_TEXT_COLOR] = mark.textColor
            it[KEY_TEXT_STYLE] = mark.textStyle.serializeKey()
            it[KEY_TEXT_TYPEFACE] = mark.textTypeface.serializeKey()
            it[KEY_ALPHA] = mark.alpha
            it[KEY_DEGREE] = mark.degree
            it[KEY_HORIZON_GAP] = mark.hGap
            it[KEY_VERTICAL_GAP] = mark.vGap
            it[KEY_ICON_URI] = mark.iconUri.toString()
            it[KEY_MODE] = mark.markMode.value
            it[PreferenceKeys.KEY_ENABLE_BOUNDS] = mark.enableBounds
            it[PreferenceKeys.KEY_ENABLE_EXIF] = mark.enableExif
            it[PreferenceKeys.KEY_EXIF_FRAME_STYLE] = mark.exifFrameStyle
            it[PreferenceKeys.KEY_ANCHOR] = mark.anchor
            it[PreferenceKeys.KEY_MARGIN] = mark.marginPercent
            if (mark.exifBandColor == null) it.remove(PreferenceKeys.KEY_EXIF_BAND_COLOR) else it[PreferenceKeys.KEY_EXIF_BAND_COLOR] = mark.exifBandColor
            if (mark.exifBandThicknessPercent == null) {
                it.remove(PreferenceKeys.KEY_EXIF_BAND_THICKNESS)
            } else {
                it[PreferenceKeys.KEY_EXIF_BAND_THICKNESS] = mark.exifBandThicknessPercent
            }
            if (mark.exifUseSerifCaption == null) it.remove(PreferenceKeys.KEY_EXIF_SERIF_CAPTION) else it[PreferenceKeys.KEY_EXIF_SERIF_CAPTION] = mark.exifUseSerifCaption
            it[PreferenceKeys.KEY_TEXT_EFFECT_STROKE] = mark.textEffectStroke
            it[PreferenceKeys.KEY_TEXT_EFFECT_SHADOW] = mark.textEffectShadow
            it[PreferenceKeys.KEY_TEXT_EFFECT_PILL_BACKGROUND] = mark.textEffectPillBackground
        }
    }

//    suspend fun resetList() {
//        updateImageList(emptyList())
//    }

    suspend fun select(uri: Uri) = withContext(Dispatchers.Default) {
        val info = imageInfoList.find { it.uri == uri } ?: ImageInfo(uri)
        _selectedImage.emit(info)
    }

    sealed class MarkMode(val value: Int) {
        object Text : MarkMode(0)

        object Image : MarkMode(1)
    }

    companion object {
        const val SP_NAME = "sp_water_mark_config"
        const val SP_KEY_TEXT = "${SP_NAME}_key_text"
        const val SP_KEY_TEXT_SIZE = "${SP_NAME}_key_text_size"
        const val SP_KEY_TEXT_COLOR = "${SP_NAME}_key_text_color"
        const val SP_KEY_TEXT_STYLE = "${SP_NAME}_key_text_style"
        const val SP_KEY_TEXT_TYPEFACE = "${SP_NAME}_key_text_typeface"
        const val SP_KEY_ALPHA = "${SP_NAME}_key_alpha"
        const val SP_KEY_HORIZON_GAP = "${SP_NAME}_key_horizon_gap"
        const val SP_KEY_VERTICAL_GAP = "${SP_NAME}_key_vertical_gap"
        const val SP_KEY_DEGREE = "${SP_NAME}_key_degree"
        const val SP_KEY_ENABLE_BOUNDS = "${SP_NAME}_key_enable_bounds"
        const val SP_KEY_ENABLE_EXIF = "${SP_NAME}_key_enable_exif"
        const val SP_KEY_EXIF_FRAME_STYLE = "${SP_NAME}_key_exif_frame_style"
        const val SP_KEY_ICON_URI = "${SP_NAME}_key_icon_uri"

//        const val SP_KEY_URI = "${SP_NAME}_key_uri"
        const val SP_KEY_WATERMARK_MODE = "${SP_NAME}_key_watermark_mode"

//        const val SP_KEY_IMAGE_ROTATION = "${SP_NAME}_key_watermark_mode"
//        const val SP_KEY_TILE_MODEL = "${SP_NAME}_key_tile_model"
//        const val SP_KEY_OFFSET_X = "${SP_NAME}_key_offset_x"
//        const val SP_KEY_OFFSET_Y = "${SP_NAME}_key_offset_y"
        const val SP_KEY_ANCHOR = "${SP_NAME}_key_anchor"
        const val SP_KEY_MARGIN = "${SP_NAME}_key_margin"

        /** FEAT-23. */
        const val SP_KEY_ANCHOR_PORTRAIT = "${SP_NAME}_key_anchor_portrait"
        const val SP_KEY_MARGIN_PORTRAIT = "${SP_NAME}_key_margin_portrait"
        const val SP_KEY_ANCHOR_LANDSCAPE = "${SP_NAME}_key_anchor_landscape"
        const val SP_KEY_MARGIN_LANDSCAPE = "${SP_NAME}_key_margin_landscape"
        const val SP_KEY_EXIF_BAND_COLOR = "${SP_NAME}_key_exif_band_color"
        const val SP_KEY_EXIF_BAND_THICKNESS = "${SP_NAME}_key_exif_band_thickness"
        const val SP_KEY_EXIF_SERIF_CAPTION = "${SP_NAME}_key_exif_serif_caption"
        const val SP_KEY_TEXT_EFFECT_STROKE = "${SP_NAME}_key_text_effect_stroke"
        const val SP_KEY_TEXT_EFFECT_SHADOW = "${SP_NAME}_key_text_effect_shadow"
        const val SP_KEY_TEXT_EFFECT_PILL_BACKGROUND = "${SP_NAME}_key_text_effect_pill_background"
        const val SP_KEY_RECENT_ICON_URIS = "${SP_NAME}_key_recent_icon_uris"
        const val MIN_EXIF_BAND_THICKNESS_PERCENT = 0.04f
        const val MAX_EXIF_BAND_THICKNESS_PERCENT = 0.30f
        const val MAX_TEXT_SIZE = 100f
        const val MIN_TEXT_SIZE = 1f
        const val DEFAULT_TEXT_SIZE = 14f
        const val MAX_DEGREE = 360f
        const val MAX_HORIZON_GAP = 500
        const val MAX_VERTICAL_GAP = 500
        const val MIN_MARGIN_PERCENT = 0f
        const val MAX_MARGIN_PERCENT = 0.2f
        const val DEFAULT_MARGIN_PERCENT = 0.05f

        /** FEAT-24: số icon/logo gần đây tối đa giữ lại cho quick-pick. */
        const val MAX_RECENT_ICONS = 8

        /** FEAT-12: số bước Undo tối đa giữ lại (entry cũ nhất bị bỏ khi vượt). */
        const val MAX_UNDO_STACK = 20

        /** FEAT-12: gộp nhiều lần gọi update liên tục (vd 1 gesture kéo slider) thành 1 entry Undo. */
        const val UNDO_SNAPSHOT_DEBOUNCE_MS = 400L

        /** Uri không thể chứa newline thật (chỉ %0A percent-encode) — an toàn làm delimiter. */
        private const val RECENT_ICON_URI_DELIMITER = "\n"

        /** Hàm thuần — parse chuỗi lưu trong DataStore thành danh sách Uri, bỏ qua entry rỗng. */
        internal fun parseRecentIconUris(raw: String?): List<Uri> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECENT_ICON_URI_DELIMITER).filter { it.isNotBlank() }.map { Uri.parse(it) }
        }

        private fun serializeRecentIconUris(uris: List<String>): String = uris.joinToString(RECENT_ICON_URI_DELIMITER)

        /**
         * FEAT-24: đẩy [newUri] lên đầu danh sách MRU — nếu đã có trong [current] thì di chuyển
         * lên đầu (không nhân đôi) thay vì thêm mới, cắt còn tối đa [maxSize] phần tử. Hàm thuần
         * (không phụ thuộc DataStore) để dễ unit test.
         */
        internal fun pushToFrontOfRecentIcons(current: List<String>, newUri: String, maxSize: Int): List<String> {
            val withoutDuplicate = current.filterNot { it == newUri }
            return (listOf(newUri) + withoutDuplicate).take(maxSize)
        }
    }
}
