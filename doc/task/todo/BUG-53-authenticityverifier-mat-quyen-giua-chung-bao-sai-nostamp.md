---
id: BUG-53
type: Bug
priority: P2
effort: XS
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/AuthenticityVerifier.kt
---

# `AuthenticityVerifier` — URI mất quyền đọc giữa chừng báo sai "NoStamp" thay vì "Unreadable"

## Mô tả
KDoc của file (dòng 39) ghi rõ quy ước: "Mọi lỗi IO → `Unreadable`". Nhưng tại chỗ đọc `rawStamp` (dòng ~39-48): `contentResolver.openInputStream(uri)` khi permission bị thu hồi giữa chừng (không throw exception, chỉ trả `null` theo đúng hợp đồng API của `ContentResolver`) khiến `rawStamp = null`, rồi `AuthenticityStamp.parse(null) ?: return NoStamp` — trả kết quả `NoStamp` ("ảnh không có con dấu").

Điều này không nhất quán với chính logic đọc hash ngay phía dưới (dòng 50-55), nơi case `null` tương tự được xử lý đúng bằng `?: return Unreadable`. Hai nhánh xử lý cùng 1 loại lỗi (mất quyền đọc) nhưng cho ra 2 kết quả khác nhau.

Hậu quả: ảnh thật sự CÓ con dấu xác thực, nhưng đúng lúc verify thì bị thu hồi quyền đọc file → app báo "ảnh không có con dấu" (sai, gây hiểu nhầm nghiêm trọng: ảnh thật bị coi là giả/không xác thực được) thay vì "không đọc được" (đúng bản chất, trung lập).

## Đề xuất
Sửa nhánh đọc `rawStamp`: khi `openInputStream(uri)` trả `null`, return `Unreadable` thay vì tiếp tục xuống `parse(null) ?: NoStamp`, nhất quán với nhánh đọc hash ngay bên dưới.

## Acceptance Criteria
- [ ] `openInputStream(uri)` trả `null` (mất quyền đọc) → `verify()` trả `Unreadable`, KHÔNG trả `NoStamp`.
- [ ] Ảnh thật sự không có con dấu (đọc file bình thường, parse ra null vì không có stamp) → vẫn trả đúng `NoStamp` như cũ, không bị đổi hành vi.
- [ ] Unit test mock `ContentResolver.openInputStream()` trả `null`, xác nhận kết quả là `Unreadable`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-53`, file ticket = `todo/BUG-53-authenticityverifier-mat-quyen-giua-chung-bao-sai-nostamp.md`.
