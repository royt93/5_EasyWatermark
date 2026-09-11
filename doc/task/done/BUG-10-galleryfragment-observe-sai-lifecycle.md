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

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-10`, file ticket = `todo/BUG-10-galleryfragment-observe-sai-lifecycle.md`.

## Kết quả kiểm chứng (2026-09-11)
- **Điểm audit tự chấm: 9.5/10.** Cả 2 `observe()` (`galleryPickedImageList`, `selectedCount`) đổi sang `viewLifecycleOwner` — khớp đúng "Cách fix đề xuất", diff tối thiểu (2 dòng).
- **Test (re-audit 2026-09-11 — thử lại nghiêm túc):** Báo cáo trước ghi "không có test tự động" vì `GalleryFragment` cần Hilt qua `activityViewModels()`. Thử lại: `GalleryFragment` **không** phải `@AndroidEntryPoint` (chỉ `MainActivity` mới cần Hilt) — viết `app/src/test/java/com/mckimquyen/watermark/ui/dlg/GalleryFragmentLifecycleRoboTest.kt`, host bằng 1 `FragmentActivity` test-only override `defaultViewModelProviderFactory` trả thẳng `MainViewModel` dựng trực tiếp (đúng pattern `MainViewModelRemoveImageRoboTest`, không qua Hilt). Test dùng **FragmentManager thật** (`add()` rồi `detach()` — đúng cơ chế ViewPager dùng để ẩn page ngoài màn hình, đúng kịch bản ticket mô tả): xác nhận `LiveData.hasObservers()` chuyển từ `true` → `false` ngay khi View bị huỷ (`detach()`) trong khi Fragment instance vẫn sống. Mutation test thủ công (đổi tạm `viewLifecycleOwner` về `this`) xác nhận test THẬT SỰ bắt được lỗi (fail đúng), rồi phục hồi lại fix. `./gradlew testAppReleaseDebugUnitTest` xanh toàn bộ (121 test, 0 failure).
- **Smoke test (2026-09-11, TECNO KJ7 `115333744A005844`):** PASS. Mở/đóng GalleryFragment (bottom sheet "Choose picture") 6 lần liên tiếp — logcat: đúng 6 lần `GalleryFragment onCreate`/`onDestroyView`, và mỗi lần mở chỉ có ĐÚNG 1 dòng `galleryPickedImageList updated` (không tích luỹ/duplicate qua các lần mở trước), không crash. **Đạt Definition of Done: điểm 9.5/10 > 9, test đủ (test tự động thật qua FragmentManager, đã mutation-test xác nhận), smoke test pass.**
