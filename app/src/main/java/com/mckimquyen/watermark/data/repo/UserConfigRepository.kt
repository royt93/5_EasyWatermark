package com.mckimquyen.watermark.data.repo

import android.graphics.Bitmap
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.UserPreferences
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_COMPRESS_LEVEL
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_CONFLICT_POLICY
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_COPYRIGHT
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_MAX_LONG_EDGE
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_OUTPUT_FORMAT
import com.mckimquyen.watermark.data.repo.UserConfigRepository.PreferenceKeys.KEY_OUTPUT_NAME_PATTERN
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
        val KEY_OUTPUT_NAME_PATTERN = stringPreferencesKey(SP_KEY_OUTPUT_NAME_PATTERN)
        val KEY_CONFLICT_POLICY = intPreferencesKey(SP_KEY_CONFLICT_POLICY)
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
            val outputFormat = resolveOutputFormat(it[KEY_OUTPUT_FORMAT], Build.VERSION.SDK_INT)
            val savedValue = (it[KEY_COMPRESS_LEVEL] ?: DEFAULT_COMPRESS_LEVEL).coerceAtLeast(20)
                .coerceAtMost(100)
            val compressLevel = if (savedValue % 20 != 0) DEFAULT_COMPRESS_LEVEL else savedValue
            val maxLongEdge = (it[KEY_MAX_LONG_EDGE] ?: DEFAULT_MAX_LONG_EDGE).coerceAtLeast(DEFAULT_MAX_LONG_EDGE)
            val copyright = it[KEY_COPYRIGHT] ?: ""
            val outputNamePattern = it[KEY_OUTPUT_NAME_PATTERN] ?: ""
            val conflictPolicy = ConflictPolicy.fromId(it[KEY_CONFLICT_POLICY] ?: ConflictPolicy.KEEP_BOTH.id)
            UserPreferences(outputFormat, compressLevel, maxLongEdge, copyright, outputNamePattern, conflictPolicy)
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

    suspend fun updateOutputNamePattern(
        pattern: String
    ) {
        dataStore.edit {
            it[KEY_OUTPUT_NAME_PATTERN] = pattern
        }
    }

    suspend fun updateConflictPolicy(
        policy: ConflictPolicy
    ) {
        dataStore.edit {
            it[KEY_CONFLICT_POLICY] = policy.id
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
        const val SP_KEY_OUTPUT_NAME_PATTERN = "${SP_NAME}_key_output_name_pattern"
        const val SP_KEY_CONFLICT_POLICY = "${SP_NAME}_key_conflict_policy"

        // ENH-34: ordinal thật của Bitmap.CompressFormat.WEBP_LOSSY/WEBP_LOSSLESS (API 30+) — dùng
        // hằng số Int thay vì tham chiếu thẳng field enum ở đây, để so khớp ordinal đọc từ DataStore
        // không đụng tới field không tồn tại trên framework thiết bị API <30 (NoSuchFieldError).
        // Ordinal cố định theo thứ tự khai báo trong android.graphics.Bitmap.CompressFormat.
        private const val WEBP_LOSSY_ORDINAL = 3
        private const val WEBP_LOSSLESS_ORDINAL = 4

        /**
         * Hàm thuần (không phụ thuộc DataStore thật) để dễ unit test — [sdkInt] tham số hoá thay vì
         * đọc thẳng `Build.VERSION.SDK_INT` để test được cả nhánh API <30 trên máy build thật (luôn
         * là API cao). API <30 fallback về `WEBP` cũ (field đó tồn tại mọi API level, an toàn).
         */
        internal fun resolveOutputFormat(ordinal: Int?, sdkInt: Int): Bitmap.CompressFormat {
            val supportsModernWebp = sdkInt >= Build.VERSION_CODES.R
            return when (ordinal) {
                Bitmap.CompressFormat.PNG.ordinal -> Bitmap.CompressFormat.PNG
                WEBP_LOSSY_ORDINAL -> if (supportsModernWebp) Bitmap.CompressFormat.WEBP_LOSSY else legacyWebp()
                WEBP_LOSSLESS_ORDINAL -> if (supportsModernWebp) Bitmap.CompressFormat.WEBP_LOSSLESS else legacyWebp()
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP.ordinal -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
        }

        @Suppress("DEPRECATION")
        private fun legacyWebp(): Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP
    }
}
