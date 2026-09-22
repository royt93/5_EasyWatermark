package com.mckimquyen.watermark.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FEAT-06: 1 dòng = 1 "hồ sơ" watermark đặt tên, snapshot TOÀN BỘ field của
 * [com.mckimquyen.watermark.data.model.WaterMark] (trừ `recentIconUris` — đó là MRU icon dùng
 * chung toàn app qua [com.mckimquyen.watermark.data.repo.WaterMarkRepository], không thuộc về
 * riêng 1 "look" cụ thể nên không lưu lại ở đây).
 *
 * DB riêng ([com.mckimquyen.watermark.data.db.WatermarkProfileDatabase]), KHÔNG chung với
 * [com.mckimquyen.watermark.data.db.AppDatabase] — cùng lý do đã áp dụng ở FEAT-04
 * ([com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity]): `AppDatabase` tạo từ asset
 * (`createFromAsset`), thêm entity/đổi version vào đó rủi ro migration cao không cần thiết.
 *
 * Enum/sealed-class field lưu dạng `Int` (`serializeKey()`/`.ordinal`) — cùng cách
 * `WaterMarkRepository` đã lưu các field này vào DataStore, tái dùng thẳng hàm
 * `TextPaintStyle.obtainSealedClass()`/`TextTypeface.obtainSealedClass()` lúc áp dụng lại.
 */
@Entity(tableName = "watermark_profile")
data class WatermarkProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val text: String,
    val textSize: Float,
    val textColor: Int,
    val textStyleKey: Int,
    val textTypefaceKey: Int,
    val alpha: Int,
    val degree: Float,
    val hGap: Int,
    val vGap: Int,
    val iconUri: String,
    val markModeValue: Int,
    val enableBounds: Boolean,
    val enableExif: Boolean,
    val exifFrameStyle: Int,
    val anchor: Int,
    val marginPercent: Float,
    val exifBandColor: Int? = null,
    val exifBandThicknessPercent: Float? = null,
    val exifUseSerifCaption: Boolean? = null,
    val textEffectStroke: Boolean = false,
    val textEffectShadow: Boolean = false,
    val textEffectPillBackground: Boolean = false
)
