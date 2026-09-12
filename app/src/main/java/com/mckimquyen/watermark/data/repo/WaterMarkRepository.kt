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
        val KEY_EXIF_BAND_COLOR = intPreferencesKey(SP_KEY_EXIF_BAND_COLOR)
        val KEY_EXIF_BAND_THICKNESS = floatPreferencesKey(SP_KEY_EXIF_BAND_THICKNESS)
        val KEY_EXIF_SERIF_CAPTION = booleanPreferencesKey(SP_KEY_EXIF_SERIF_CAPTION)
        val KEY_TEXT_EFFECT_STROKE = booleanPreferencesKey(SP_KEY_TEXT_EFFECT_STROKE)
        val KEY_TEXT_EFFECT_SHADOW = booleanPreferencesKey(SP_KEY_TEXT_EFFECT_SHADOW)
        val KEY_TEXT_EFFECT_PILL_BACKGROUND = booleanPreferencesKey(SP_KEY_TEXT_EFFECT_PILL_BACKGROUND)
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
                textEffectPillBackground = it[PreferenceKeys.KEY_TEXT_EFFECT_PILL_BACKGROUND] ?: false
            )
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

    suspend fun updateText(text: String) {
        dataStore.edit {
            it[KEY_MODE] = MarkMode.Text.value
            it[KEY_TEXT] = text
        }
    }

    suspend fun updateTextSize(size: Float) {
        dataStore.edit { it[KEY_TEXT_SIZE] = size }
    }

    suspend fun updateColor(color: Int) {
        dataStore.edit { it[KEY_TEXT_COLOR] = color }
    }

    suspend fun updateTextStyle(style: TextPaintStyle) {
        dataStore.edit { it[KEY_TEXT_STYLE] = style.serializeKey() }
    }

    suspend fun updateTypeFace(typeface: TextTypeface) {
        dataStore.edit { it[KEY_TEXT_TYPEFACE] = typeface.serializeKey() }
    }

    suspend fun updateAlpha(alpha: Int) {
        dataStore.edit { it[KEY_ALPHA] = alpha.coerceAtLeast(0).coerceAtMost(255) }
    }

    suspend fun updateHorizon(gap: Int) {
        dataStore.edit { it[KEY_HORIZON_GAP] = gap.coerceAtLeast(0).coerceAtMost(MAX_HORIZON_GAP) }
    }

    suspend fun updateVertical(gap: Int) {
        dataStore.edit {
            it[KEY_VERTICAL_GAP] = gap.coerceAtLeast(0).coerceAtMost(MAX_VERTICAL_GAP)
        }
    }

    suspend fun updateDegree(degree: Float) {
        dataStore.edit { it[KEY_DEGREE] = degree.coerceAtLeast(0f).coerceAtMost(MAX_DEGREE) }
    }

    suspend fun updateIcon(iconUri: Uri) {
        dataStore.edit {
            it[KEY_MODE] = MarkMode.Image.value
            it[KEY_ICON_URI] = iconUri.toString()
        }
    }

    suspend fun updateEnableExif(enable: Boolean) {
        dataStore.edit {
            it[PreferenceKeys.KEY_ENABLE_EXIF] = enable
        }
    }

    suspend fun updateExifFrameStyle(style: ExifFrameStyle) {
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

    suspend fun resetModeToText() {
        dataStore.edit { it[KEY_MODE] = MarkMode.Text.value }
    }

    suspend fun toggleBounds(enable: Boolean) {
        dataStore.edit { it[KEY_ENABLE_BOUNDS] = enable }
    }

    suspend fun updateAnchor(anchor: Anchor) {
        dataStore.edit { it[PreferenceKeys.KEY_ANCHOR] = anchor.ordinal }
    }

    suspend fun updateMargin(percent: Float) {
        dataStore.edit { it[PreferenceKeys.KEY_MARGIN] = percent.coerceIn(MIN_MARGIN_PERCENT, MAX_MARGIN_PERCENT) }
    }

    /** FEAT-14 — null = xoá override, quay lại màu mặc định của style đang chọn. */
    suspend fun updateExifBandColor(color: Int?) {
        dataStore.edit {
            if (color == null) it.remove(PreferenceKeys.KEY_EXIF_BAND_COLOR) else it[PreferenceKeys.KEY_EXIF_BAND_COLOR] = color
        }
    }

    /** FEAT-14 — null = xoá override, quay lại tỉ lệ mặc định của style đang chọn. Có giá trị thì clamp trong khoảng an toàn (tránh band cao 0px, xem BUG-17). */
    suspend fun updateExifBandThicknessPercent(percent: Float?) {
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
        dataStore.edit {
            if (useSerif == null) it.remove(PreferenceKeys.KEY_EXIF_SERIF_CAPTION) else it[PreferenceKeys.KEY_EXIF_SERIF_CAPTION] = useSerif
        }
    }

    /** FEAT-11 — bật/tắt viền tương phản cho text watermark, độc lập với shadow/pill. */
    suspend fun updateTextEffectStroke(enable: Boolean) {
        dataStore.edit { it[PreferenceKeys.KEY_TEXT_EFFECT_STROKE] = enable }
    }

    /** FEAT-11 — bật/tắt đổ bóng cho text watermark, độc lập với stroke/pill. */
    suspend fun updateTextEffectShadow(enable: Boolean) {
        dataStore.edit { it[PreferenceKeys.KEY_TEXT_EFFECT_SHADOW] = enable }
    }

    /** FEAT-11 — bật/tắt nền pill cho text watermark, độc lập với stroke/shadow. */
    suspend fun updateTextEffectPillBackground(enable: Boolean) {
        dataStore.edit { it[PreferenceKeys.KEY_TEXT_EFFECT_PILL_BACKGROUND] = enable }
    }

    /** FEAT-14 — xoá cả 3 override cùng lúc (nút "Reset" trong UI). */
    suspend fun resetExifCustomization() {
        dataStore.edit {
            it.remove(PreferenceKeys.KEY_EXIF_BAND_COLOR)
            it.remove(PreferenceKeys.KEY_EXIF_BAND_THICKNESS)
            it.remove(PreferenceKeys.KEY_EXIF_SERIF_CAPTION)
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
        const val SP_KEY_CHANGE_LOG = "${SP_NAME}_key_change_log"
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
        const val SP_KEY_EXIF_BAND_COLOR = "${SP_NAME}_key_exif_band_color"
        const val SP_KEY_EXIF_BAND_THICKNESS = "${SP_NAME}_key_exif_band_thickness"
        const val SP_KEY_EXIF_SERIF_CAPTION = "${SP_NAME}_key_exif_serif_caption"
        const val SP_KEY_TEXT_EFFECT_STROKE = "${SP_NAME}_key_text_effect_stroke"
        const val SP_KEY_TEXT_EFFECT_SHADOW = "${SP_NAME}_key_text_effect_shadow"
        const val SP_KEY_TEXT_EFFECT_PILL_BACKGROUND = "${SP_NAME}_key_text_effect_pill_background"
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
    }
}
