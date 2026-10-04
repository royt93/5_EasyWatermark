---
id: BUG-61
type: Bug
priority: P1
effort: M
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/ProofingMode.kt
---

# Proofingmode embedimages oom khi batch lon khong chon thu muc saf

## Mô tả
`embedImages` (~121-132) `readBytes()` + base64 toàn bộ ảnh full-res vào list String; `writeIndex` chỉ bắt `Exception`, không bắt `OutOfMemoryError`.

Proofing Mode, export 50-100 ảnh (Q+, không chọn thư mục SAF) → ~1GB+ heap → OOM ở bước cuối, sau khi ảnh đã ghi nhưng chưa `recordHistory` → worker chết, UI báo lỗi.

## Đề xuất
Nhúng thumbnail nhỏ (decode ~480px, JPEG q70) thay vì bytes gốc, hoặc stream từng `<img>` ra OutputStream; fallback path tương đối khi OOM.

## Acceptance Criteria
- [ ] Batch lớn không nhân bộ nhớ theo số ảnh (peak heap bị chặn).
- [ ] Unit test chứng minh thumbnail nhỏ hơn bytes gốc + index HTML vẫn hợp lệ (cập nhật `ProofingIndexIntegrationTest`).
- [ ] Smoke test thật Proofing Mode nhiều ảnh trên device đã khoá.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-61`, file ticket = `todo/BUG-61-proofingmode-embedimages-oom-khi-batch-lon-khong-chon-thu-muc-saf.md`.
