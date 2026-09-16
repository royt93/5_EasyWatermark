---
id: BUG-28
priority: P2
type: Bug
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# `WaterMarkImageView.reset()` không release bitmap refcount như `onDetachedFromWindow()`

## Mô tả
`onDetachedFromWindow()` release đúng `mainImageBitmapValue`/`iconBitmapValue` theo cơ chế refcount `BitmapCache` (ENH-15). `reset()` — được `MainActivity.resetView()` gọi khi user bấm huỷ để quay về LaunchMode (View KHÔNG bị detach khỏi window) — lại KHÔNG release 2 field này. Bitmap "mồ côi" giữ `refCount > 0`, `BitmapCache` không thể `recycle()` cho tới khi View load ảnh mới (ghi đè field) hoặc thực sự detach — leak tạm thời, tích luỹ nếu user lặp lại chọn ảnh → huỷ nhiều lần.

## Triển khai
Thêm release `mainImageBitmapValue`/`iconBitmapValue` (giống `onDetachedFromWindow()`) vào đầu `reset()`.

## Acceptance Criteria
- [ ] Chọn ảnh → bấm huỷ về LaunchMode → lặp lại 5-10 lần — `BitmapCache` không tích luỹ entry refCount>0 mồ côi (kiểm tra qua log/debug cache size).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-28`, file ticket = `todo/BUG-28-watermarkimageviewreset-khong-release-bitmap-refcount-nhu-on.md`.
