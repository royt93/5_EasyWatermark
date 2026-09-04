---
id: ENH-04
type: Enhancement
effort: S
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
related: BUG-06
---

# Kích hoạt lại pinch-to-resize (đang bị comment)

## Mô tả
`onTouchEvent()` (dòng ~521-573) — `ScaleGestureDetector` đã được khởi tạo và tồn tại trong code nhưng lời gọi xử lý gesture bị comment, nên pinch-to-resize hiện KHÔNG hoạt động dù hạ tầng đã có sẵn.

## Đề xuất
Kích hoạt lại cùng giới hạn kích thước rõ ràng (min/max size hợp lý), kết hợp `ENH-02` (debounce ghi DataStore) và `BUG-06` (sửa điều kiện cache icon) để tránh giật lag khi bật lại tính năng này.

## Acceptance Criteria
- [ ] Pinch để resize watermark hoạt động trong cả 2 mode Text và Image.
- [ ] Không giật lag khi pinch nhanh (đã fix BUG-06/ENH-02 trước khi ticket này coi là hoàn thành).
