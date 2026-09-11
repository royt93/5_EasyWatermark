---
id: BUG-12
type: Bug
priority: P1
effort: XS
sources: Claude (1/4, verify trực tiếp xác nhận đúng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
verified: true
---

# `removeImage` crash `IndexOutOfBoundsException` khi xoá ảnh

## Mô tả
Đã đọc trực tiếp `removeImage()` (dòng 680-705):
```kotlin
val list = imageList.value?.first?.toMutableList() ?: return
val removePos = list.indexOf(imageInfo)
list.removeAt(removePos)
```
`imageInfo: ImageInfo?` nhận nullable, và `list.indexOf(imageInfo)` trả `-1` nếu `imageInfo` là `null` HOẶC không còn tồn tại trong list (đã bị xoá/thay thế bởi thao tác khác). `list.removeAt(-1)` ném `IndexOutOfBoundsException` ngay lập tức. Rủi ro thực tế xảy ra khi user vuốt xoá nhanh nhiều ảnh liên tiếp trong danh sách preview — có khả năng race giữa `AsyncListDiffer` (cập nhật danh sách nền) và vị trí item UI đưa vào lời gọi này.

## Cách fix đề xuất
```kotlin
val removePos = list.indexOf(imageInfo)
if (removePos < 0) return
list.removeAt(removePos)
```

## Acceptance Criteria
- [ ] Gọi `removeImage(null, ...)` không crash.
- [ ] Vuốt xoá nhanh nhiều ảnh liên tiếp trong danh sách preview không crash (test thủ công).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-12`, file ticket = `todo/BUG-12-removeimage-crash-index-out-of-bounds.md`.
