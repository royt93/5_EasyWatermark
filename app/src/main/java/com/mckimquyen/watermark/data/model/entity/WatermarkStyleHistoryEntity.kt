package com.mckimquyen.watermark.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IDEA-12: 1 dòng = 1 "style signature" ghi lại NGAY SAU 1 lần batch export thành công (từ
 * [com.mckimquyen.watermark.export.BatchExportWorker]) — dùng để [com.mckimquyen.watermark.data.repo.WatermarkStyleCoach]
 * học "gu" watermark của user theo thời gian và gợi ý áp lại.
 *
 * KHÔNG lưu `text`/`iconUri` cụ thể — 2 field đó đổi mỗi lần dùng, không thuộc về "gu" (khác
 * [WatermarkProfileEntity] lưu named profile đầy đủ do user tự bấm "Lưu").
 *
 * Bảng nằm chung [com.mckimquyen.watermark.data.db.WatermarkProfileDatabase] (không mở DB thứ 4
 * riêng) — DB này đã tách khỏi `AppDatabase` asset-seeded từ FEAT-06 nên thêm bảng mới + Migration
 * version+1 an toàn.
 */
@Entity(tableName = "watermark_style_history")
data class WatermarkStyleHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val textColor: Int,
    val textStyleKey: Int,
    val textTypefaceKey: Int,
    val alpha: Int,
    val anchor: Int,
    val markModeValue: Int,
    val exifFrameStyle: Int,
    val textEffectStroke: Boolean,
    val textEffectShadow: Boolean,
    val textEffectPillBackground: Boolean
)
