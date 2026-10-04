---
id: BUG-74
type: Bug
priority: P1
effort: S
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/FileUtils.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/ClipboardImageHelper.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
---

# Filleutils getfiletypefromuri khong bat securityexception o bien tin cay

## Mô tả
`getFileTypeFromUri()` khai `@Throws(SecurityException)` và gọi `ContentResolver.getType()` không guard; `DocumentFile.fromTreeUri()/listFiles()` cũng có thể ném khi quyền SAF bị thu hồi. (Agent báo các caller ở `ClipboardImageHelper`, `GalleryFragment`, `MainActivity` không bắt — **tôi mới verify hàm, chưa kiểm từng caller**; kiểm trước khi sửa.)

**Kịch bản:** Clipboard chứa `content://` không cấp quyền đọc, hoặc user thu hồi quyền thư mục SAF rồi app quét lại → `SecurityException` thoát lên main coroutine/click handler và crash.

## Đề xuất
Bắt `SecurityException`/lỗi provider tại trust boundary trong `FileUtils`: trả `null`/`false`/danh sách rỗng + log; quét cây thì xử lý lỗi theo từng thư mục để không mất kết quả đã đọc. Cùng pattern BUG-54.

## Acceptance Criteria
- [ ] Provider ném `SecurityException` → hàm trả giá trị an toàn, không throw.
- [ ] Caller không crash (kiểm chứng từng caller).
- [ ] Unit test với ContentProvider giả ném lỗi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-74`, file ticket = `todo/BUG-74-filleutils-getfiletypefromuri-khong-bat-securityexception-o-bien-tin-cay.md`.
