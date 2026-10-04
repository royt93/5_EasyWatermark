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
- [ ] `batchHistoryRepo.record` ném exception → worker vẫn trả `success` khi ảnh đã lưu.
- [ ] Unit test với repo giả ném exception (RED trước fix).
- [ ] Không nuốt `CancellationException`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-59`, file ticket = `todo/BUG-59-batchexportworker-batchhistory-record-khong-guard-lam-batch-thanh-cong-bao-failed.md`.
