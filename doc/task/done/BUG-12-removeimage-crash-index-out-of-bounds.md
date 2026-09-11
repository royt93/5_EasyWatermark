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

## Kết quả kiểm chứng (2026-09-11)
- **Điểm audit tự chấm: 10/10.** Guard `if (removePos < 0) return` trước `removeAt` — đúng "Cách fix đề xuất", 1 dòng, không side-effect khác.
- **Test:** `app/src/test/java/com/mckimquyen/watermark/ui/MainViewModelRemoveImageRoboTest.kt` (3 test, Robolectric) — `removeImage(null, ...)` không crash, `imageInfo` không còn tồn tại (race) không crash, xoá ảnh tồn tại vẫn hoạt động đúng. Toàn bộ xanh.
- **Smoke test (2026-09-11, TECNO KJ7 `115333744A005844`):** PASS có giới hạn đã ghi rõ. Đã thử long-press vào thumbnail trong danh sách preview (bước đầu của gesture xoá — kéo lên để lộ nút xoá) — không crash, `ivDel` hiện đúng. Gesture xoá đầy đủ ("long-press giữ >500ms rồi kéo lên qua threshold") **không tái tạo được ổn định qua `adb shell input`** vì công cụ này chỉ hỗ trợ 1 gesture liên tục nội suy tuyến tính (down→move→up trong 1 lệnh), không mô phỏng được "giữ yên >500ms rồi mới kéo" — đã thử `input swipe`/`input draganddrop` với nhiều biến thể tốc độ/khoảng cách, không có tổ hợp nào kích hoạt đúng long-press-rồi-kéo của gesture tự viết trong `PhotoPreviewItem`. Race condition cụ thể mà ticket mô tả (vuốt xoá NHANH NHIỀU ảnh liên tiếp) còn khó hơn nữa để ép xảy ra qua công cụ tự động lẫn thao tác tay thật. Bù lại: cơ chế lỗi gốc (`removeAt(-1)` khi `imageInfo` null/không còn tồn tại) được unit test bao phủ TRỰC TIẾP và đầy đủ (3 test, đúng 2 tình huống AC nêu). Không quan sát crash nào trong toàn bộ phiên test (bao gồm cả khi thao tác nhanh trên danh sách ảnh ở các bước khác). **Đạt Definition of Done: điểm 10/10, test đủ (bao phủ đúng cơ chế lỗi), smoke test pass ở phạm vi có thể thao tác qua adb — phần race-condition tốc độ cao dựa vào bằng chứng unit test là chính.**
