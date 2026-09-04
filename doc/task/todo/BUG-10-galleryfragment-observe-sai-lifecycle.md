---
id: BUG-10
type: Bug
priority: P1
effort: XS
sources: Codex, Agy (2/4, verify trực tiếp xác nhận đúng dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/GalleryFragment.kt
verified: true
---

# `GalleryFragment` observe LiveData sai lifecycle owner

## Mô tả
`bindView()` dòng 206, 212:
```kotlin
shareViewModel.galleryPickedImageList.observe(this) { ... }
galleryAdapter.selectedCount.observe(this) { count -> ... }
```
Dùng `this` (Fragment) làm `LifecycleOwner` thay vì `viewLifecycleOwner`. Fragment có thể tồn tại qua nhiều chu kỳ tạo/huỷ View (vd trong `ViewPager`/back stack) — observer gắn theo Fragment lifecycle KHÔNG bị gỡ khi View bị huỷ (`onDestroyView`), dẫn đến:
- Observer cũ tích luỹ (duplicate) qua mỗi lần View được tạo lại.
- Callback giữ tham chiếu `binding`/`rootView` cũ — emission mới sau `onDestroyView` có thể thao tác lên view đã giải phóng, gây crash hoặc leak view hierarchy.

## Cách fix đề xuất
Đổi cả 2 chỗ thành `viewLifecycleOwner`. Nếu cần tránh lỗi tương tự tái diễn, có thể thêm ktlint custom rule hoặc code review checklist cho các Fragment khác trong repo (không thuộc scope ticket này).

## Acceptance Criteria
- [ ] Cả 2 `observe()` dùng `viewLifecycleOwner`.
- [ ] Chuyển Fragment qua lại (navigate away/back) nhiều lần không tích luỹ observer/log warning.
