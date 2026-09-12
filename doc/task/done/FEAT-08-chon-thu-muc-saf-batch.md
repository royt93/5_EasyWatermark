---
id: FEAT-08
type: Feature
effort: S
sources: Internal (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/FileUtils.kt
  - app/src/main/res/menu/top_app_bar.xml
verified: true
---

# Chọn cả thư mục (SAF tree) để batch

## Mô tả
Hiện chỉ multi-pick từng ảnh riêng lẻ. Cho phép chọn cả 1 thư mục (Storage Access Framework tree Uri) để đưa toàn bộ ảnh trong đó vào batch — hữu ích cho nhiếp ảnh gia import cả folder từ thẻ nhớ/camera.

## Triển khai
Dùng `ActivityResultContracts.OpenDocumentTree` (`ACTION_OPEN_DOCUMENT_TREE`), liệt kê file ảnh trong cây thư mục được chọn (dùng `DocumentFile`/`ContentResolver.query` trên tree Uri), đẩy danh sách URI vào `updateImageList` đã có sẵn.

## Acceptance Criteria
- [x] Chọn 1 thư mục đưa được toàn bộ ảnh hợp lệ trong đó (không đệ quy subfolder) vào danh sách batch.
- [x] Giữ được lựa chọn multi-pick từng ảnh như cũ song song.

## Kiến trúc
- Thêm nút "Choose folder" (`ivPickFolder`, icon `ic_folder_24`) cạnh nút "pick via system" (`ivSysImage`) có sẵn trong menu toolbar của `GalleryFragment` — không đổi layout/FAB/grid hiện có.
- `FileUtils.listImagesInTree(context, treeUri)` dùng `DocumentFile.fromTreeUri(...).listFiles()` (KHÔNG đệ quy — đúng AC) rồi lọc bằng `FileUtils.filterImageUris()` (tách riêng thành hàm thuần, test được bằng mock `DocumentFile` thay vì cần dựng cả `DocumentsProvider` SAF thật).
- Kết quả đẩy thẳng qua `GalleryFragment.handleActivityResult()` — hàm ĐÃ CÓ SẴN cho multi-pick, filter `FileUtils.isImage()` + toast rỗng + `updateImageList()` + dismiss dùng lại nguyên vẹn, không cần code riêng.
- `takePersistableUriPermission()` gọi ngay khi nhận `treeUri` — giữ quyền đọc qua process restart (khớp AC1 của ENH-01: export qua WorkManager có thể chạy ở process mới).

## Kết quả kiểm chứng

### Unit/widget test
- `FileUtilsFolderPickTest` (6 case, Robolectric) — logic lọc (`filterImageUris`) test bằng `DocumentFile` mock qua mockk: giữ file ảnh, loại file không phải ảnh, loại subfolder (không đệ quy, đúng AC), loại file mimeType null, danh sách rỗng, và `listImagesInTree` với tree Uri không hợp lệ trả về rỗng không crash.
- `GalleryFragmentFolderPickRoboTest` (2 case) — bấm menu item `ivPickFolder` mở đúng `Intent.ACTION_OPEN_DOCUMENT_TREE`; xác nhận cả 2 nút `ivSysImage`/`ivPickFolder` cùng tồn tại trong menu (AC "giữ song song").
- Regression: `GalleryFragmentPhotoPickerRoboTest`, `GalleryFragmentSelectionLabelRoboTest`, `GalleryFragmentLifecycleRoboTest`, `MainActivity*`, `compileAppReleaseDebugAndroidTestKotlin` pass. Nhân tiện dọn `import android.view.*` (wildcard, ktlint flag) sang import tường minh trong `GalleryFragment.kt` — đã đụng file này nên fix luôn.

### Smoke test thật trên Pixel 7 Pro (2B051FDH3006MU)
- Bấm "Choose folder" → hệ thống mở đúng picker SAF (Android DocumentsUI), chọn thư mục `Pictures` (có sẵn cả file ảnh trực tiếp lẫn nhiều subfolder như `Screenshots`/`Facebook`/`Messenger`...).
- Bấm "Sử dụng thư mục này" → dialog xin quyền SAF chuẩn hệ thống → "Cho phép" → app nhận đúng CHỈ các ảnh nằm TRỰC TIẾP trong `Pictures` (2 file QR code + vài ảnh screenshot điện thoại), KHÔNG kéo theo nội dung của bất kỳ subfolder nào — xác nhận đúng AC "không đệ quy".
- Ảnh load vào editor thành công, xem trước watermark bình thường, không crash (`logcat` sạch, không `FATAL EXCEPTION`).
- Nút multi-pick từng ảnh cũ (`ivSysImage`/Photo Picker) vẫn còn nguyên trong menu, không bị ảnh hưởng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-08`, file ticket = `todo/FEAT-08-chon-thu-muc-saf-batch.md`.
