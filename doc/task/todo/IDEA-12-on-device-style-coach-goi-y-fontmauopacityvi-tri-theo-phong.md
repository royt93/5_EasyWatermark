---
id: IDEA-12
type: Idea
effort: L
sources: Codex (ý tưởng gốc) + Claude (thiết kế chi tiết, brainstorm 2026-09-27)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/db/WatermarkProfileDatabase.kt
  - app/src/main/java/com/mckimquyen/watermark/data/db/dao/
  - app/src/main/java/com/mckimquyen/watermark/data/model/entity/
  - app/src/main/java/com/mckimquyen/watermark/data/repo/
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportWorker.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# On-device Style Coach — gợi ý font/màu/opacity/vị trí theo phong cách ảnh cá nhân

## Mô tả
Tự động học "gu" watermark của user qua thời gian (font, màu, opacity, vị trí, mode text/icon...) từ lịch sử các lần export thành công, rồi tự đề xuất áp lại preset đó khi mở batch mới — không cần user tự lưu tên như `WatermarkProfile` (FEAT-06) đã có. Khác IDEA-01 (né mặt/chủ thể theo ML, xử lý hậu kỳ 1 ảnh) và IDEA-06 (auto-contrast/opacity theo TỪNG ảnh riêng lẻ, không học xu hướng chung theo thời gian).

**Effort hạ từ XL → L sau khi scope lại**: bỏ hướng ML/phân tích palette ảnh (quá phức tạp, không cần thiết cho AC gốc), dùng heuristic đếm tần suất thuần — tái dùng hạ tầng Room sẵn có (`WatermarkProfileDatabase`), không cần model on-device, không cần dependency mới.

## Vì sao đáng làm
Không cần user chủ động thao tác (khác `WatermarkProfile` cần tự bấm "Lưu"), giảm ma sát mỗi lần mở batch mới cho user hay dùng lại đúng 1 kiểu style quen. Thuần local, không backend.

## Thiết kế kỹ thuật

### 1. Data model + lưu trữ
Thêm bảng mới `watermark_style_history` **vào chung `WatermarkProfileDatabase` đã có** (đã tách khỏi `AppDatabase` asset-seeded từ FEAT-06, không asset, nên thêm bảng + `Migration` version+1 an toàn — không cần mở DB thứ 4 riêng).

`WatermarkStyleHistoryEntity` — chỉ lưu phần **"style signature"**, KHÔNG lưu `text`/`iconUri` cụ thể (đổi mỗi lần dùng, không thuộc "gu"):
```kotlin
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
```
`WatermarkStyleHistoryDao`: `insert()` + `@Query("SELECT * FROM watermark_style_history ORDER BY timestamp DESC LIMIT :n")` lấy N dòng gần nhất, kèm `@Query("DELETE FROM watermark_style_history WHERE id NOT IN (SELECT id FROM watermark_style_history ORDER BY timestamp DESC LIMIT :keep)")` để prune, tránh phình bảng vô hạn (giữ tối đa ~50 dòng).

Ghi 1 dòng mỗi khi `BatchExportWorker.recordHistory()` chạy xong ít nhất 1 ảnh thành công — cạnh lời gọi `batchHistoryRepo.record(...)` đã có sẵn (FEAT-04), cùng chỗ, không cần hook mới.

### 2. Logic gợi ý — `WatermarkStyleCoach`
Class thuần (không phụ thuộc Android Context), dễ unit test:
```kotlin
class WatermarkStyleCoach {
    fun suggest(recent: List<WatermarkStyleHistoryEntity>): WatermarkStyleHistoryEntity? {
        if (recent.size < MIN_SAMPLES) return null // MIN_SAMPLES = 10
        val (signature, count) = recent
            .groupingBy { it.copy(id = 0, timestamp = 0) } // bỏ 2 field không thuộc signature
            .eachCount()
            .maxByOrNull { it.value } ?: return null
        return signature.takeIf { count.toFloat() / recent.size >= SUGGEST_THRESHOLD } // 0.6f
    }
}
```
Gọi từ `MainViewModel` khi load batch mới, so với `WaterMark` hiện tại — chỉ hiện gợi ý nếu signature đề xuất KHÁC cấu hình đang áp dụng (tránh gợi ý cái đang dùng sẵn).

### 3. UI
`MainViewModel` thêm `styleSuggestionFlow: StateFlow<WatermarkStyleHistoryEntity?>`, tính lại mỗi lần `updateImageList()` nạp ảnh mới. Editor (`MainActivity`/`LaunchView`) thêm 1 `MaterialCardView` banner ngang nhỏ phía trên panel chính (chuẩn M3 đã migrate, không tạo style riêng) — text "Áp dụng preset thường dùng?" + 2 `MaterialButton`: **Áp dụng** (gọi `waterMarkRepo.applyWaterMark` với các field từ signature, giữ nguyên `text`/`iconUri` hiện tại) / **Bỏ qua** (ẩn banner, không hỏi lại trong session hiện tại — không cần persist trạng thái dismiss).

## Rủi ro / cân nhắc
- Không dùng similarity mờ (weighted/fuzzy) — chỉ match exact signature, đơn giản hơn, chấp nhận việc gợi ý "cứng" (đổi 1 field nhỏ coi như signature khác, không cộng dồn).
- Prune giữ tối đa 50 dòng — đủ cho N=10 tính gợi ý, tránh DB phình vô hạn theo thời gian dùng app.

## Acceptance Criteria
- [ ] Sau ≥10 lần export batch thành công, nếu ≥60% trong 10 lần gần nhất cùng 1 style signature và khác cấu hình đang áp dụng → hiện banner gợi ý khi mở batch mới.
- [ ] Bấm "Áp dụng" → cấu hình watermark đổi đúng theo signature gợi ý (giữ nguyên text/icon hiện tại).
- [ ] Bấm "Bỏ qua" → banner ẩn, không hiện lại trong cùng session batch đó.
- [ ] Chưa đủ 10 lần lịch sử hoặc không style nào đạt 60% → không hiện banner (không có false positive).

## Test plan
- Unit: `WatermarkStyleCoachTest` — đủ N + ≥60% → gợi ý đúng signature; <60%; <10 dòng; nhiều signature khác nhau không trộn nhầm; signature đề xuất trùng cấu hình hiện tại → không gợi ý.
- Room migration test: `WatermarkProfileDaoIntegrationTest` (mở rộng) hoặc file mới — migration version cũ→mới giữ nguyên data bảng `watermark_profile`, bảng mới tạo đúng schema.
- Widget/Robolectric: banner hiện/ẩn đúng theo `styleSuggestionFlow`, bấm Áp dụng gọi đúng `applyWaterMark` với field kỳ vọng, bấm Bỏ qua ẩn banner và không set lại trong cùng instance ViewModel.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-12`, file ticket = `todo/IDEA-12-on-device-style-coach-goi-y-fontmauopacityvi-tri-theo-phong.md`. Đây là ticket đã có thiết kế chi tiết sẵn (mục "Thiết kế kỹ thuật" trên) — implement bám đúng thiết kế đó, không tự đổi kiến trúc (VD không tự đổi sang bảng DB mới/logic similarity mờ) trừ khi phát hiện lý do kỹ thuật rõ ràng chặn được, và phải ghi lại lý do đổi trong "Kết quả kiểm chứng" lúc done.
