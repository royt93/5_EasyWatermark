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

## ❌ Skipped (2026-09-29, user phát hiện + verify code)
Ticket sinh ra chỉ từ lint cảnh báo Manifest, không đọc code thật của chính file liệt kê
(`GalleryFragment.kt`) — verify lại: app **đã** dùng Android Photo Picker làm luồng chính (ENH-10,
comment dòng 75 file này: "không cần quyền READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE"). Nút menu
`ivSysImage` (dòng 222-233) gọi `pickImageVisualMediaLauncher.launch(...)` mỗi lần bấm — chính là
nút "Chọn thêm ảnh" ticket này đòi làm, đã có sẵn: Photo Picker mở picker mới mỗi lần, không giữ
persistent read permission nên không có khái niệm "giới hạn tồn đọng" mà Selected Photos Access
giải quyết.

`READ_MEDIA_IMAGES` runtime request (`ContextExtension.kt` `preCheckStoragePermission`) chỉ là
fallback khi Photo Picker không khả dụng (comment `MainActivity.kt`: "chỉ gate quyền khi phải
fallback về ACTION_PICK") — case đó là Android <11/thiếu Play Services, không tồn tại Selected
Photos Access (tính năng chỉ có từ Android 14+, mà Android 14+ luôn có Photo Picker). Dùng Photo
Picker làm luồng chính chính là giải pháp Google khuyến nghị để né hoàn toàn permission model cũ —
không cần code thêm gì. `tools:ignore="SelectedPhotoAccess"` trong Manifest giữ nguyên (đúng, vì
fallback path không thật sự vi phạm best practice này về mặt UX).
