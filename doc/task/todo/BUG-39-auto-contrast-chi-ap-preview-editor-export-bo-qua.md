---
id: BUG-39
type: Bug
priority: P1
effort: M
sources: Claude self-audit 2026-09-27 (grep toàn repo: autoContrastEnabled không xuất hiện trong export/)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Auto-contrast (IDEA-06) chỉ áp preview editor — export + preview grid bỏ qua, ảnh xuất KHÁC preview

## Mô tả
`WaterMark.autoContrastEnabled` chỉ được đọc ở DUY NHẤT 1 nơi ngoài repo/UI:
`WaterMarkImageView.applyAutoContrastIfEnabled()` (dòng 417, gọi từ `applyNewConfig` dòng 320).

`grep -rn autoContrastEnabled app/src/main` — `export/` (BatchExportEngine, BatchExportWorker) **0
kết quả**. Nghĩa là:
- Bật chip "Tự động tương phản" → preview editor đảo màu chữ đen/trắng + nâng sàn alpha.
- Export thật (`generateImage`) và preview grid (`generatePreviewBitmap`, `generateCompareBitmaps`)
  vẫn dùng `settings.config`/`config` NGUYÊN BẢN → ảnh xuất ra giữ màu/alpha cũ.

Vi phạm 2 thứ cùng lúc: (1) nguyên tắc "preview khớp export" mà chính ENH-05 đã thiết lập;
(2) AC gốc của IDEA-06 — "**batch** ảnh có độ sáng nền khác nhau tại vị trí watermark tự động chọn
màu/opacity đọc rõ" — batch chạy hoàn toàn qua `BatchExportEngine`, tức AC này thực tế CHƯA đạt dù
ticket đã ở `done/` (smoke test khi đó chỉ verify trên preview editor, xem "Kết quả kiểm chứng" của
IDEA-06).

## Cách fix đề xuất
Trích `applyAutoContrastIfEnabled` + `computeAutoContrastSampleRegion` ra hàm dùng chung
(`utils/bitmap/` hoặc `export/AutoContrastResolver`) nhận `(bitmap, imageInfo, config)` trả
`WaterMark` đã điều chỉnh — gọi từ cả `WaterMarkImageView.applyNewConfig` (giữ hành vi cũ) và 3
điểm vẽ trong `BatchExportEngine` NGAY TRƯỚC khi build `bitmapPaint`/shader. Lưu ý vùng lấy mẫu
phải tính theo bitmap FULL-RES lúc export (tỉ lệ khác preview), không copy hằng số scale của View.

## Acceptance Criteria
- [ ] Bật auto-contrast, export ảnh nền trắng → màu chữ trong FILE xuất ra khớp preview (đen), không phải màu cũ.
- [ ] Tắt auto-contrast → file xuất byte-identical với trước khi fix (không regression).
- [ ] Preview grid batch + so sánh trước/sau cũng phản ánh auto-contrast (khớp export).
- [ ] Unit test cho hàm resolver dùng chung + 1 integration test export thật (ảnh nền sáng/tối).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-39`, file ticket = `todo/BUG-39-auto-contrast-chi-ap-preview-editor-export-bo-qua.md`.
