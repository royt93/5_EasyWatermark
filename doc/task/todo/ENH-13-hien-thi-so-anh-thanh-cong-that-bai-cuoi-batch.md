---
id: ENH-13
type: Enhancement
effort: S
sources: Tách từ BUG-03 (AC3 gốc) — sau khi BUG-03 fix xong phần logic per-item jobState, phần hiển thị UI tách riêng để tránh phình scope 1 ticket
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
related: BUG-03
---

# Hiển thị số ảnh thành công/thất bại cuối batch

## Mô tả
BUG-03 đã fix để mỗi `ImageInfo.jobState` phản ánh đúng Success/Failure (trước đó luôn báo Success giả). Dữ liệu per-item giờ đã chính xác trong `infoList` trả về từ `generateList()`, nhưng UI (`SaveImageBSDialogFragment`, xem `TYPE_JOB_FINISH` case dòng ~209) vẫn chỉ hiển thị 1 trạng thái tổng ("hoàn thành") mà không đếm số ảnh thành công/thất bại thực tế.

## Triển khai
Sau khi `saveImage()` nhận `result.data` (là `List<ImageInfo>` với `jobState` đã đúng cho từng phần tử), đếm `count { it.jobState is JobState.Success }` / `count { it.jobState is JobState.Failure }`, hiển thị dạng "Xong 8/10 ảnh, 2 ảnh lỗi" thay vì chỉ "Hoàn thành". Cân nhắc cho phép tap vào số ảnh lỗi để xem danh sách/thử lại.

## Acceptance Criteria
- [ ] Sau batch export có ảnh lỗi, UI hiển thị rõ số lượng thành công/thất bại (không chỉ 1 trạng thái chung).
- [ ] Batch toàn bộ thành công vẫn hiển thị bình thường như hiện tại (không thêm nhiễu UI khi không cần).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-13`, file ticket = `todo/ENH-13-hien-thi-so-anh-thanh-cong-that-bai-cuoi-batch.md`.
