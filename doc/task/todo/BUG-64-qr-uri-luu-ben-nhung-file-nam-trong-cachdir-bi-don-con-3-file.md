---
id: BUG-64
type: Bug
priority: P1
effort: M
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/QrCodeGenerator.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/QrCodeBottomSheetFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
---

# Qr uri luu ben nhung file nam trong cachdir bi don con 3 file

## Mô tả
URI QR (`FileProvider` trỏ `cacheDir/qrcodes`) được ghi vào DataStore, MRU icon (tối đa `MAX_RECENT_ICONS = 8`) và profile Room như tài nguyên bền, nhưng `QrCodeBottomSheetFragment.kt:179` gọi `FileUtils.cleanOldTempFiles(qrcodes, maxRetainedFiles = 3)` (mặc định xoá cả file quá 24h), và Android cũng có thể tự xoá cache.

**Kịch bản:** Tạo QR A, dùng làm watermark/lưu profile; hôm sau mở lại sheet QR (hoặc tạo thêm QR B-E) → file A bị xoá, URI còn nguyên → decode lỗi, watermark logo/QR biến mất, không báo lỗi. MRU giữ 8 URI trong khi thư mục chỉ giữ 3 file.

## Đề xuất
Khi user XÁC NHẬN dùng QR, copy file sang `filesDir` (kho bền) rồi mới `updateIcon`/`updateQrDynamicConfig`; cache chỉ dùng cho preview chưa xác nhận. Dọn/migrate URI cache cũ khỏi MRU và profile.

## Acceptance Criteria
- [ ] URI QR đã xác nhận không còn nằm trong `cacheDir`.
- [ ] Test: tạo QR, mô phỏng dọn cache → watermark vẫn render.
- [ ] Profile cũ trỏ cache: mở lên không crash, bỏ icon hỏng thay vì giữ URI chết.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-64`, file ticket = `todo/BUG-64-qr-uri-luu-ben-nhung-file-nam-trong-cachdir-bi-don-con-3-file.md`.
