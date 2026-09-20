---
id: BUG-35
priority: P2
type: Bug
effort: XS
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
---

# Chọn thư mục SAF có thể crash nếu hệ thống từ chối persist permission

## Mô tả
`pickFolderLauncher` gọi `takePersistableUriPermission()` trực tiếp không try/catch. Nếu provider không cấp flag persistable hoặc hệ thống ném `SecurityException` (một số OEM/provider lạ), bottom sheet crash thay vì fallback dùng quyền tạm thời cho phiên hiện tại.

## Triển khai
Bọc `runCatching { takePersistableUriPermission(...) }`, nếu lỗi thì log/toast nhẹ rồi vẫn thử `FileUtils.listImagesInTree()` với quyền tạm thời (không persist được, chỉ dùng trong phiên này).

## Acceptance Criteria
- [ ] Giả lập `SecurityException` khi gọi `takePersistableUriPermission` (unit test mock) — flow không crash, vẫn cố xử lý ảnh trong thư mục đã chọn cho phiên hiện tại.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-35`, file ticket = `todo/BUG-35-chon-thu-muc-saf-co-the-crash-neu-he-thong-tu-choi-persist-p.md`.
