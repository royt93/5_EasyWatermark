---
id: BUG-59
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportWorker.kt
---

# Batchexportworker batchhistory record khong guard lam batch thanh cong bao failed

## Mô tả
`recordHistory()` (~178-185): `batchHistoryRepo.record(...)` (Room insert) không bọc `runCatching`, trong khi `styleHistoryRepo.record` ngay dưới đã bọc.

DB lock/hết dung lượng đúng lúc ghi lịch sử → exception làm `doWork` FAILED dù mọi ảnh đã lưu → UI báo lỗi, không `persistFinishedExport`, user export lại ra file trùng.

## Đề xuất
`runCatching { batchHistoryRepo.record(...) }.onFailure { AppLog.w(...) }`, cùng pattern với style history.

## Acceptance Criteria
- [x] `batchHistoryRepo.record` ném exception → worker vẫn trả `success` khi ảnh đã lưu.
- [x] Unit test với repo giả ném exception (RED trước fix).
- [x] Không nuốt `CancellationException`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-59`, file ticket = `todo/BUG-59-batchexportworker-batchhistory-record-khong-guard-lam-batch-thanh-cong-bao-failed.md`.

## Smoke test thật (Pixel 7 Pro, serial `2B051FDH3006MU`, ngày 2026-10-04)
Xuất ảnh (Lưu → Xuất vào bộ sưu tập) → ảnh hiện dấu tick thành công, không báo lỗi → mở "Lịch sử xuất ảnh" thấy mục mới `2026-10-04 22:27 · 1 thành công · 0 lỗi`, logcat không có `FATAL EXCEPTION`. Case DB ném exception khi ghi lịch sử không mô phỏng được trên máy thật — đã phủ bằng `BatchExportWorkerHistoryFailureRoboTest` (RED khi bỏ `runCatching`).
