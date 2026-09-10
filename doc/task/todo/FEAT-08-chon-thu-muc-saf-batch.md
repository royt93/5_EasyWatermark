---
id: FEAT-08
type: Feature
effort: S
sources: Internal (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/PickImageContract.kt
---

# Chọn cả thư mục (SAF tree) để batch

## Mô tả
Hiện chỉ multi-pick từng ảnh riêng lẻ. Cho phép chọn cả 1 thư mục (Storage Access Framework tree Uri) để đưa toàn bộ ảnh trong đó vào batch — hữu ích cho nhiếp ảnh gia import cả folder từ thẻ nhớ/camera.

## Triển khai
Dùng `ActivityResultContracts.OpenDocumentTree` (`ACTION_OPEN_DOCUMENT_TREE`), liệt kê file ảnh trong cây thư mục được chọn (dùng `DocumentFile`/`ContentResolver.query` trên tree Uri), đẩy danh sách URI vào `updateImageList` đã có sẵn.

## Acceptance Criteria
- [ ] Chọn 1 thư mục đưa được toàn bộ ảnh hợp lệ trong đó (không đệ quy subfolder, trừ khi cần) vào danh sách batch.
- [ ] Giữ được lựa chọn multi-pick từng ảnh như cũ song song.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-08`, file ticket = `todo/FEAT-08-chon-thu-muc-saf-batch.md`.
