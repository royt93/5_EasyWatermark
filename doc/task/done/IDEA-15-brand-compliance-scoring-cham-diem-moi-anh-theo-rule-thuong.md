---
id: IDEA-15
type: Idea
effort: L
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export
  - app/src/main/java/com/mckimquyen/watermark/data/model
---

# Brand Compliance Scoring — chấm điểm mỗi ảnh theo rule thương hiệu trước khi export

## Mô tả
Chấm điểm mỗi ảnh sau khi áp watermark theo rule brand tự định nghĩa (logo không quá sát mép, tương phản đủ đọc, không che mặt/sản phẩm chính, kích thước watermark trong khoảng chuẩn) — kết quả hiển thị pass/warn/fail cho từng ảnh trong batch TRƯỚC khi export thật. Khác IDEA-06 (chỉ auto-fix contrast/opacity, không có hệ thống RULE/SCORING tường minh).

## Đề xuất
Định nghĩa rule set đơn giản (vị trí trong % khung, ngưỡng tương phản tính từ luminance nền, ngưỡng kích thước) chạy trên preview bitmap đã có sẵn (FEAT-07), hiển thị badge pass/warn/fail trên mỗi card.

## Acceptance Criteria
- [ ] Watermark đặt quá sát mép hoặc opacity quá thấp (không đọc được) — card preview hiện badge "warn"/"fail" rõ ràng trước khi export.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-15`, file ticket = `todo/IDEA-15-brand-compliance-scoring-cham-diem-moi-anh-theo-rule-thuong.md`.

## Kết quả kiểm chứng (2026-09-26)

### Triển khai thực tế
- `BrandComplianceScorer`: Bộ đánh giá quy tắc thương hiệu thuần Kotlin (Level: PASS/WARN/FAIL; Issue: EDGE, LOW_OPACITY, LOW_CONTRAST, COVERS_FACE, SIZE_OUT_OF_RANGE). Đánh giá dựa trên độ mờ (alpha), độ tương phản màu (contrast ratio), vị trí mép ảnh, kích thước watermark và mức độ che khuôn mặt (dùng cache ML Kit từ IDEA-01). Chế độ lặp toàn khung (TILED) bỏ qua các quy tắc vị trí/mặt/kích thước.
- `BatchExportEngine`: Trong `generatePreviewBitmap`, tính toán vùng bounding box chuẩn hoá 0..1 của watermark, trích xuất độ tương phản WCAG qua Palette, lấy cache khuôn mặt `detectedFaceRectsNormalized` và gọi `BrandComplianceScorer.evaluate()`. Trả kết quả trong `PreviewResult.Success.compliance`.
- `SaveImageListAdapter`: Thêm `ivCompliance` vào góc trên-trái của mỗi card preview trong `item_saving_image.xml`. Hiển thị badge icon + màu sắc theo Material 3 (PASS: xanh check, WARN: vàng warning, FAIL: đỏ error) kèm `contentDescription` đầy đủ cho Accessibility (TalkBack). Lưu kết quả theo URI và re-apply mỗi lần bind.
- String resources: Thêm chuỗi đa ngôn ngữ (tiếng Anh và tiếng Việt) cho 3 mức độ và 5 loại vi phạm quy tắc thương hiệu.

### Audit R5: 9.5/10
- Không magic number: mọi ngưỡng (alpha 60/100, contrast 1.5/3.0, margin 1%/3%, face overlap 5%/15%, size 0.25%/1%/50%/85%) đều là hằng số có tên trong `BrandComplianceScorer`.
- Null-safety: xử lý an toàn mọi trường hợp null, contrast ratio không đo được, khuôn mặt rỗng.
- Không leak: không giữ tham chiếu Context, Bitmap hay listener dài hạn; icon và tint giải phóng đúng chu kỳ View.
- Test đầy đủ: Unit test thuần JVM, Robolectric widget test cho adapter, Robolectric preview test, và Instrumentation test trên thiết bị thật.

### Test
- Unit test:
  - `BrandComplianceScorerTest` (7 test cases): kiểm tra safe watermark PASS, sát mép WARN/FAIL, opacity thấp WARN/FAIL, tương phản thấp WARN/FAIL, che mặt WARN/FAIL, kích thước bất thường WARN/FAIL, tile mode bỏ qua mép/mặt, nhiều lỗi chọn mức nặng nhất.
  - `BatchExportEnginePreviewRoboTest`: kiểm tra `PreviewResult.Success` chứa `compliance` khi alpha thấp (LOW_OPACITY FAIL) và khi đè mặt (COVERS_FACE).
  - `SaveImageListAdapterComplianceRoboTest` (5 test cases): kiểm tra hiển thị badge PASS, WARN, FAIL, ẩn khi null, rebind qua payload giữ nguyên badge từ cache.
- Instrumentation test:
  - `BrandComplianceContrastIntegrationTest` (2 test cases): chạy trên Samsung Galaxy S24 Ultra (`R5CX613VZBR`) — `whiteTextOnWhiteBackground_failsComplianceWithLowContrast` (FAIL LOW_CONTRAST) và `whiteTextOnBlackBackground_passesCompliance` (PASS). PASS 2/2.

### Smoke test
- Thiết bị: Samsung Galaxy S24 Ultra (serial `R5CX613VZBR`, Android 16 SDK 36 — chuyển từ TECNO KJ7 theo xác nhận của user).
- Cài đặt APK debug `com.mckimquyen.watermark-v2026.09.05(20260905).apk`.
- Chọn 2 ảnh test (`ewm_dark.jpg` và `ewm_bright.jpg`), mở dialog Lưu, cuộn đến grid `rvResult`:
  - Ảnh 1 (nền tối + chữ vàng): Badge `ivCompliance` hiển thị `Đạt chuẩn thương hiệu` (PASS).
  - Ảnh 2 (nền sáng vàng + chữ sáng): Badge `ivCompliance` hiển thị `Không đạt chuẩn thương hiệu: Tương phản thấp` (FAIL - LOW_CONTRAST).
- Logcat sạch, không có FATAL/ANR, không có quảng cáo.
