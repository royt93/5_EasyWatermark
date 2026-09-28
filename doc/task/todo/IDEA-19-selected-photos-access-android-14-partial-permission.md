---
id: IDEA-19
type: Idea
priority: P2
effort: M
sources: Claude (audit dọn lint trước release, 2026-09-28)
files:
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
verified: false
---

# Xử lý "Selected Photos Access" (Android 14+) — quyền ảnh giới hạn

## Mô tả
Lint cảnh báo app dùng `READ_MEDIA_IMAGES` nhưng chưa xử lý luồng "Selected Photos Access" Android
14 giới thiệu: khi user chỉ cấp quyền truy cập MỘT PHẦN thư viện ảnh (chọn ảnh cụ thể thay vì "cho
phép tất cả"), hệ thống gọi lại app nhiều lần với tập ảnh giới hạn — app nên có cách để user bấm
"Chọn thêm ảnh" (mở lại photo picker hệ thống) thay vì chỉ thấy thư viện bị giới hạn không rõ lý do.

Hiện tại **không phải lint nông** — đây là 1 luồng UX thật cần thiết kế riêng (nút "Chọn thêm ảnh"
trong `GalleryFragment` khi phát hiện quyền bị giới hạn, gọi
`MediaStore.ACTION_PICK_IMAGES`/`ACTION_APPLICATION_MEDIA_ACCESS`), không nhét vội vào đợt dọn lint
trước release — đang suppress bằng `tools:ignore="SelectedPhotoAccess"` trong `AndroidManifest.xml`.

## Việc cần làm khi triển khai
1. Detect trạng thái quyền: `ContextCompat.checkSelfPermission` với
   `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+) để biết user đang ở chế độ "chỉ vài ảnh".
2. Hiện banner/nút "Chọn thêm ảnh" trong `GalleryFragment` khi ở trạng thái này.
3. Nút gọi `startActivity(Intent(MediaStore.ACTION_APPLICATION_MEDIA_ACCESS))` hoặc launcher quyền
   tương ứng để user bổ sung ảnh cho phép truy cập.
4. Test: widget test giả lập trạng thái quyền giới hạn (Robolectric shadow permission), smoke test
   thật trên device Android 14+ đã khoá theo R3.

## Prompt loop
Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md) — Definition of Done dùng chung.
