---
id: BUG-58
type: Bug
priority: P1
effort: S
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WatermarkProfileRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/WatermarkProfileViewModel.kt
---

# Ap dung watermark profile tat am tham auto contrast exif palette qr dong

## Mô tả
`WatermarkProfileRepository.toEntity/toWaterMark` không lưu `autoContrastEnabled`, `exifAutoPalette`, `qrDynamicEnabled`, `qrContentTemplate`, `qrPortfolioLink`.

Áp 1 profile → `toWaterMark` trả mặc định `false`/`""`, `applyWaterMark` ghi đè TOÀN BỘ field vào DataStore, không đẩy Undo → user mất Auto-contrast/Auto-palette/QR động + template + link, không hoàn tác được. Profile mới lưu cũng không nhớ các tính năng này.

## Đề xuất
Bản nhanh: `WatermarkProfileViewModel` giữ 5 field này từ `waterMarkRepo.waterMark.first()` bằng `.copy(...)` khi áp. Bản đúng: thêm cột Room (migration) rồi map vào profile.

## Acceptance Criteria
- [x] Áp profile KHÔNG làm mất 5 field trên của cấu hình hiện tại (bản nhanh) hoặc khôi phục đúng giá trị đã lưu (bản đúng).
- [x] Unit/Robolectric test: cấu hình bật 3 cờ → áp profile → 3 cờ còn nguyên (RED trước fix).
- [x] Nếu thêm cột Room: migration test + `WatermarkProfileDaoIntegrationTest` cập nhật.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-58`, file ticket = `todo/BUG-58-ap-dung-watermark-profile-tat-am-tham-auto-contrast-exif-palette-qr-dong.md`.

## Kết quả kiểm chứng (Pixel 7 Pro, serial `2B051FDH3006MU`, ngày 2026-10-04)
**Fix:** thêm 5 cột nullable vào `WatermarkProfileEntity` (`exifAutoPalette`, `autoContrastEnabled`, `qrDynamicEnabled`, `qrContentTemplate`, `qrPortfolioLink`); `WatermarkProfileDatabase` v4→v5 + `MIGRATION_4_5` (đăng ký ở `AppModule`); `toEntity`/`toWaterMark` map 2 chiều (NULL → mặc định tắt/rỗng cho profile cũ).
- **Unit test:** `WatermarkProfileViewModelRoboTest` +2 test (round-trip 5 field; entity cũ NULL → mặc định). RED thật khi phá map `toWaterMark`, GREEN khi có.
- **Integration test thật trên device:** `WatermarkProfileMigration4To5IntegrationTest` (hàng cũ sống sót với cột mới NULL; cột mới nhận ghi) — chạy qua `connectedDebugAndroidTest` cùng 3 lớp khác, **13 test PASS, 0 fail** trên Pixel 7 Pro.
- **Chưa làm:** smoke test UI bằng tay (lưu rồi áp profile qua màn Hồ sơ watermark). Rủi ro cốt lõi (migration không phá dữ liệu cũ, map 2 chiều) đã được phủ bằng integration + unit test.
