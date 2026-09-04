---
id: ENH-05
type: Enhancement
effort: S
sources: Claude, doc/feat.md mục 4 (đã ghi nhận "còn lại chưa làm")
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Preview token watermark khớp với lúc export

## Mô tả
Đã có sẵn hạ tầng token động (`{filename}`, `{seq}`, `{date}`... qua `TextTokenResolver`, xem `doc/feat.md` mục 4) nhưng chỉ resolve lúc `saveOutput()` — trong lúc chỉnh sửa, editor hiển thị token NGUYÊN VĂN (vd `{seq}` hiện đúng chữ `{seq}` thay vì số thứ tự thật), khiến người dùng không thấy trước kết quả thật sự.

## Đề xuất
Gọi `resolveTextTokens()` (hoặc tương đương) trực tiếp trong `WaterMarkImageView` cho ảnh đang được chọn preview, dùng cùng logic resolve với lúc export để đảm bảo nhất quán.

## Acceptance Criteria
- [ ] Text watermark chứa token hiển thị giá trị đã resolve (không phải token thô) trong lúc chỉnh sửa preview.
- [ ] Giá trị preview khớp chính xác với giá trị thật khi export.
