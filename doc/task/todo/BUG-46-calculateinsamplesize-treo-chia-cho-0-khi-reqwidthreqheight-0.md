---
id: BUG-46
type: Bug
priority: P2
effort: XS
sources: Claude self-audit 2026-09-28 (phát hiện tình cờ khi viết androidTest cho BUG-45 — ArithmeticException thật trên device, TECNO_KJ7)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# `calculateInSampleSize` treo rồi `ArithmeticException` khi `reqWidth`/`reqHeight` = 0

## Mô tả
`BatchExportEngine.generateImage()` gọi:
```kotlin
calculateInSampleSize(
    width = mutableBitmap.width,
    height = mutableBitmap.height,
    reqWidth = WaterMarkImageView.calculateDrawLimitWidth(viewInfo.width, viewInfo.paddingLeft),
    reqHeight = WaterMarkImageView.calculateDrawLimitHeight(viewInfo.height, viewInfo.paddingRight)
)
```
`calculateDrawLimitWidth(w, ps) = w - ps*2` — nếu `viewInfo.width == 0` (canvas `ivPhoto` CHƯA được đo/layout lần nào khi export bắt đầu — race hiếm nhưng có thể: export bắn ngay sau xoay màn hình/thay đổi cấu hình, trước khi `onMeasure` kịp chạy lại) → `reqWidth = 0`, tương tự `reqHeight`.

`calculateInSampleSize()` (`BitmapUtils.kt`, hàm `calculateInSampleSize`, dòng ~485-495):
```kotlin
if (height > reqHeight || width > reqWidth) {
    val halfHeight = height / 2
    val halfWidth = width / 2
    while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
        inSampleSize *= 2
    }
}
```
Khi `reqHeight = reqWidth = 0`, điều kiện `>= 0` LUÔN đúng bất kể `inSampleSize` bao lớn — vòng `while` không có điều kiện dừng thật, `inSampleSize` nhân đôi tới khi tràn số (`Int` overflow quanh 32 lần lặp) quay về **0**, lần chia tiếp theo `halfHeight / 0` ném `ArithmeticException: divide by zero`. Verify THẬT trên TECNO_KJ7 (log `w = 64, h = 64, reqW = 0, reqH = 0` → crash ngay `BitmapUtils.kt:491`).

Về mặt logic, đây LUÔN LÀ 1 bug tiềm ẩn (vòng lặp không có điều kiện dừng đúng khi req=0) dù khả năng chạm tới trong app thật thấp (chỉ khi `viewInfo.width`/`height` = 0 lúc export).

## Cách fix đề xuất
Guard đầu hàm `calculateInSampleSize`: nếu `reqWidth <= 0 || reqHeight <= 0` → trả `1` ngay (không downsample), cùng quy ước `calculateInSampleSizeForLongEdge` đã làm (`if (reqLongEdge <= 0 || longEdge <= reqLongEdge) return 1`).

## Acceptance Criteria
- [ ] `calculateInSampleSize(width, height, reqWidth=0, reqHeight=0)` trả `1` ngay, không treo/không throw.
- [ ] `reqWidth`/`reqHeight` âm cũng trả `1` (không có ý nghĩa downsample).
- [ ] Case bình thường (`reqWidth`/`reqHeight` > 0, ảnh lớn hơn) — kết quả KHÔNG đổi so với trước (test khoá case cũ).
- [ ] Unit test thuần JVM cho `calculateInSampleSize` (reqWidth=0, reqHeight=0, cả 2 = 0, âm, case bình thường giữ nguyên).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-46`, file ticket = `todo/BUG-46-calculateinsamplesize-treo-chia-cho-0-khi-reqwidthreqheight-0.md`.
