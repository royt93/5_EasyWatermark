---
id: FEAT-05
type: Feature
effort: M
sources: Codex, Claude, doc/feat.md mục F (đã đề xuất, nâng thành task cụ thể)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/db/
  - app/src/main/java/com/mckimquyen/watermark/data/repo/SignatureRepository.kt
---

# Xuất/nhập Template + Signature (backup/restore)

## Mô tả
Cho phép xuất/nhập toàn bộ danh sách Template (Room) và các file chữ ký cá nhân (`.webp`) thành 1 file backup (JSON + assets, đóng gói zip) để chuyển máy hoặc sao lưu — không phụ thuộc cloud/Firebase.

## Triển khai
Serialize bảng `Template` (Room) sang JSON + copy thư mục chứa file signature vào cùng 1 file zip; chiều ngược lại giải nén + import vào Room/thư mục local.

## Acceptance Criteria
- [ ] Xuất được file backup chứa đủ Template + Signature hiện có.
- [ ] Nhập file backup vào máy khác khôi phục đúng toàn bộ Template + Signature.
- [ ] Xử lý hợp lý trường hợp trùng tên/conflict khi nhập vào máy đã có dữ liệu sẵn.
