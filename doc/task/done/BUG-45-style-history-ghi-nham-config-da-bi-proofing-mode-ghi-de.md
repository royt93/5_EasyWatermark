---
id: BUG-45
type: Bug
priority: P2
effort: XS
sources: Claude self-audit 2026-09-28 (đọc lại diff IDEA-12 vừa merge, đối chiếu ProofingMode.overrideConfig)
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportWorker.kt
  - app/src/main/java/com/mckimquyen/watermark/export/ProofingMode.kt
---

# Style history (IDEA-12) ghi nhầm config đã bị Client Proofing Mode ghi đè

## Mô tả
`BatchExportWorker.recordHistory()` (thêm ở IDEA-12) ghi 1 dòng lịch sử style bằng
`styleHistoryRepo.record(settings.config)` — nhưng `settings.config` ở `doWork()` đã là bản
**SAU KHI** áp dụng `ProofingMode.overrideConfig(baseConfig)` khi `prefs.proofingMode == true`
(`BatchExportWorker.kt` dòng ~61: `config = if (prefs.proofingMode) ProofingMode.overrideConfig(baseConfig) else baseConfig`).

`ProofingMode.overrideConfig()` (`ProofingMode.kt:37-52`) ghi đè `alpha` (→ `PROOF_ALPHA`) và
`markMode` (→ ép `Text`) — cả 2 field đều nằm trong "style signature" mà `WatermarkStyleCoach`
dùng để học "gu" user (`WatermarkStyleHistoryEntity.alpha`/`markModeValue`, xem
`WatermarkStyleHistoryRepository.toEntity()`).

Hệ quả: user hay dùng Client Proofing Mode (IDEA-13, xuất album cho khách xem trước) sẽ bị
`WatermarkStyleCoach` học nhầm `alpha`/`markMode` của **watermark tạm thời dùng để proofing**
(không phải cấu hình họ thực sự thích dùng cho ảnh xuất thật) — nếu đủ ≥6/10 lần export gần nhất
là proofing, banner IDEA-12 sẽ gợi ý sai (gợi ý áp `alpha` thấp/`markMode = Text` ép buộc của
proofing, ghi đè nhầm lên cấu hình thật user đang dùng).

## Cách fix đề xuất
`recordHistory()` nhận thêm `baseConfig` (bản GỐC trước override) thay vì dùng `settings.config`,
hoặc đơn giản hơn: bỏ qua ghi style history hoàn toàn khi `settings.proofingMode == true` (giống
cách `ProofingMode` đã tách biệt khỏi luồng "cấu hình thật" ở nhiều chỗ khác — export cho khách
xem trước không phải là 1 lần user "chọn dùng" style đó).

## Acceptance Criteria
- [x] Export batch với Client Proofing Mode bật → **không** ghi dòng nào vào `watermark_style_history`
      (hoặc ghi đúng `baseConfig` gốc, không phải bản đã bị `overrideConfig` ghi đè) — chọn 1 trong 2
      hướng, ưu tiên hướng "không ghi" (đơn giản hơn, đúng tinh thần "proofing không phải gu thật").
- [x] Export batch bình thường (không proofing) — hành vi ghi style history KHÔNG đổi so với hiện tại.
- [x] Test chứng minh: bật proofingMode, export → style history repo không nhận thêm dòng nào
      (hoặc nhận đúng baseConfig nếu chọn hướng 2).

## Test plan
- Robolectric: mở rộng `BatchExportWorkerRoboTest` — case proofingMode=true + export thành công ≥1
  ảnh → assert style history KHÔNG có dòng mới (hoặc field đúng baseConfig).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-45`, file ticket = `todo/BUG-45-style-history-ghi-nham-config-da-bi-proofing-mode-ghi-de.md`.

## Kết quả kiểm chứng (2026-09-28)

**Fix:** chọn hướng "không ghi" — `BatchExportWorker.recordHistory()` thêm điều kiện
`!settings.proofingMode` cạnh `outputUris.isNotEmpty()`. Nhân tiện bọc `runCatching` quanh
`styleHistoryRepo.record(...)` (finding khác từ cùng đợt code-review: DB lỗi không được làm cả batch
báo failure dù ảnh đã export/lưu thành công thật).

- **Điểm tự audit:** 9.5/10 — đúng đề xuất, tối thiểu diff, không phá luồng ghi style history bình
  thường (đã test cả 2 nhánh).
- **Test:**
  - `BatchExportWorkerStyleHistoryIntegrationTest` (androidTest MỚI, bitmap thật/decode thật/WorkManager
    thật/Room thật — `BatchExportWorkerRoboTest` Robolectric hiện có chỉ có nhánh decode-fail, không
    đủ verify nhánh THÀNH CÔNG mà proofing-mode cần): `proofingModeOff_...recordsOneStyleHistoryRow`,
    `proofingModeOn_...doesNotRecordStyleHistory` — cả 2 PASS thật trên device.
- **Smoke test thật (TECNO_KJ7, serial 115333744A005844):** chạy `connectedDebugAndroidTest` cho cả
  2 test trên + 5 test Room khác của IDEA-12 (migration + DAO) trong cùng lượt — 7/7 PASS trên
  device thật, không phải Robolectric giả lập.
- **Bonus finding cùng đợt audit này:** phát hiện thêm `BUG-46` (ArithmeticException thật trong
  `calculateInSampleSize` khi `reqWidth`/`reqHeight`=0) trong lúc viết androidTest trên — ticket
  riêng, KHÔNG fix trong ticket này (không liên quan BUG-45, tránh mở rộng scope).
