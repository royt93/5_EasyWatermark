package com.mckimquyen.watermark.data.repo

import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.data.model.UserPreferences
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_CHANGE_LOG
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_COMPRESS_LEVEL
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_COPYRIGHT
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_MAX_LONG_EDGE
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_OUTPUT_FORMAT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class UserConfigRepository @Inject constructor(
    @Named("UserPreferences") private val dataStore: DataStore<Preferences>
) {
    private object PreferenceKeys {
        val KEY_OUTPUT_FORMAT = intPreferencesKey(SP_KEY_FORMAT)
        val KEY_COMPRESS_LEVEL = intPreferencesKey(SP_KEY_COMPRESS_LEVEL)
        val KEY_MAX_LONG_EDGE = intPreferencesKey(SP_KEY_MAX_LONG_EDGE)
        val KEY_COPYRIGHT = stringPreferencesKey(SP_KEY_COPYRIGHT)
        val KEY_CHANGE_LOG = stringPreferencesKey(WaterMarkRepository.SP_KEY_CHANGE_LOG)
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map {
            val outputFormat = when (it[KEY_OUTPUT_FORMAT]) {
                Bitmap.CompressFormat.PNG.ordinal -> Bitmap.CompressFormat.PNG
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP.ordinal -> Bitmap.CompressFormat.WEBP
                else -> {
                    Bitmap.CompressFormat.JPEG
                }
            }
            val savedValue = (it[KEY_COMPRESS_LEVEL] ?: DEFAULT_COMPRESS_LEVEL).coerceAtLeast(20)
                .coerceAtMost(100)
            val compressLevel = if (savedValue % 20 != 0) DEFAULT_COMPRESS_LEVEL else savedValue
            val maxLongEdge = (it[KEY_MAX_LONG_EDGE] ?: DEFAULT_MAX_LONG_EDGE).coerceAtLeast(DEFAULT_MAX_LONG_EDGE)
            val copyright = it[KEY_COPYRIGHT] ?: ""
            UserPreferences(outputFormat, compressLevel, maxLongEdge, copyright)
        }

    suspend fun updateFormat(
        outputFormat: Bitmap.CompressFormat
    ) {
        dataStore.edit {
            it[KEY_OUTPUT_FORMAT] = outputFormat.ordinal
        }
    }

    suspend fun updateCompressLevel(
        compressLevel: Int
    ) {
        dataStore.edit {
            it[KEY_COMPRESS_LEVEL] = compressLevel
        }
    }

    suspend fun updateMaxLongEdge(
        maxLongEdge: Int
    ) {
        dataStore.edit {
            it[KEY_MAX_LONG_EDGE] = maxLongEdge.coerceAtLeast(DEFAULT_MAX_LONG_EDGE)
        }
    }

    suspend fun updateCopyright(
        copyright: String
    ) {
        dataStore.edit {
            it[KEY_COPYRIGHT] = copyright
        }
    }

    suspend fun saveVersionCode() {
        dataStore.edit {
            it[KEY_CHANGE_LOG] = BuildConfig.VERSION_CODE.toString()
        }
    }

    companion object {
        const val DEFAULT_COMPRESS_LEVEL = 80

        /** 0 = giữ nguyên kích thước gốc, không resize. */
        const val DEFAULT_MAX_LONG_EDGE = 0
        val DEFAULT_BITMAP_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG
        const val SP_NAME = "sp_water_mark_user_config"
        const val SP_KEY_FORMAT = "${SP_NAME}_key_format"
        const val SP_KEY_COMPRESS_LEVEL = "${SP_NAME}_key_compress_level"
        const val SP_KEY_MAX_LONG_EDGE = "${SP_NAME}_key_max_long_edge"
        const val SP_KEY_COPYRIGHT = "${SP_NAME}_key_copyright"
    }
}
