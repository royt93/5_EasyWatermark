---
id: ENH-27
type: Enhancement
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# `MainViewModel.removeImage()` tính sai `selectedPos` khi xoá ảnh CUỐI danh sách

## Mô tả
Điều kiện `removePos >= size-1 OR removePos < curSelectedPos` trừ `selectedPos` sai trong 1 trường hợp: xoá ảnh cuối danh sách trong khi ảnh đang chọn (không phải ảnh cuối) không đổi vị trí thật, nhưng `selectedPos` vẫn bị trừ theo logic hiện tại — highlight filmstrip lệch sang item liền trước dù ảnh đang edit không đổi. Không crash (khác BUG-12 đã fix `IndexOutOfBoundsException`), chỉ sai UI/UX.

## Triển khai
Sửa điều kiện tính `selectedPos` mới: chỉ trừ khi `removePos` thực sự nằm TRƯỚC `curSelectedPos` trong danh sách, không phụ thuộc việc removePos có phải phần tử cuối hay không.

## Acceptance Criteria
- [ ] Batch ≥3 ảnh, chọn ảnh giữa danh sách, xoá ảnh CUỐI cùng — highlight vẫn đúng ngay ảnh đang chọn ban đầu, không lệch sang ảnh khác.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-27`, file ticket = `todo/ENH-27-mainviewmodelremoveimage-tinh-sai-selectedpos-khi-xoa-anh-cu.md`.
