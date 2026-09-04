---
id: ENH-02
type: Enhancement
effort: S
sources: Codex, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTextContentFragment.kt
---

# Debounce ghi DataStore khi kéo/pinch/nhập text

## Mô tả
`updateOffset`, `updateTextSize` (qua pinch `onScale`), `updateDegree`, và nhập text watermark (`EditTextContentFragment`) đều gọi `dataStore.edit{}` (ghi đĩa) ở MỖI sự kiện di chuyển/scale/phím gõ — không debounce. Kéo/pinch/gõ liên tục tạo áp lực I/O không cần thiết, có thể góp phần vào race đã ghi ở `BUG-06`.

## Đề xuất
Debounce 100-300ms trước khi ghi DataStore, giữ update UI (render shader) tức thời — chỉ trì hoãn phần persist xuống đĩa.

## Acceptance Criteria
- [ ] Kéo/pinch/gõ liên tục chỉ ghi DataStore sau khi dừng thao tác >100-300ms.
- [ ] UI vẫn render mượt theo thời gian thực (không debounce phần hiển thị).
