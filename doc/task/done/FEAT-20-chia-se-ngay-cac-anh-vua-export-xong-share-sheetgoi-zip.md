---
id: FEAT-20
type: Feature
effort: M
sources: Codex + Claude (2 nguồn đồng thuận)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/ExportZipHelper.kt
  - app/src/main/res/layout/dlg_save_file.xml
  - app/src/main/res/xml/filepaths.xml
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-vi/strings.xml
---

# Chia sẻ ngay các ảnh vừa export xong (share sheet/gói ZIP)

## Mô tả
Sau khi batch export xong (đã có ENH-13 hiện số thành công/thất bại), không có cách nào share ngay các ảnh vừa xuất — phải rời app vào Gallery tìm lại thủ công. Với batch nhiều ảnh, `ACTION_SEND_MULTIPLE` trực tiếp có thể kém ổn định trên 1 số app nhận (Zalo, Messenger) — cân nhắc thêm lựa chọn gói thành 1 file ZIP để share 1 URI duy nhất.

## Triển khai
Thêm nút "Chia sẻ ngay" sau khi batch export xong — mặc định `ACTION_SEND_MULTIPLE` các URI vừa export (tái dùng `shareUri` đã có trong `ImageInfo`/`Result`); tuỳ chọn phụ "Share as ZIP" gom vào 1 file tạm rồi share 1 URI.

## Acceptance Criteria
- [x] Export batch 3 ảnh thành công, bấm "Chia sẻ ngay" — share sheet mở với đúng 3 ảnh vừa xuất (không phải ảnh gốc).
- [x] Bật tuỳ chọn ZIP — share sheet mở với 1 file zip chứa đủ 3 ảnh.

## Kết quả kiểm chứng
1. **Kiến trúc & Bảo mật:**
   - Xây dựng `ExportZipHelper` thuần túy đóng gói file ZIP:
     - Chống Zip Slip / Path Traversal: chỉ lấy `File(name).name`, làm sạch ký tự cấm hệ thống.
     - Tự động khử trùng lặp (deduplicate) tên entry trong ZIP (`photo.jpg` -> `photo_2.jpg`).
     - Tự động dọn dẹp các file ZIP tạm cũ trong `zip_cache/` qua `FileUtils.cleanOldTempFiles()`.
     - Cấp quyền truy cập FileProvider thông qua `<cache-path name="export_zip" path="zip_cache/" />`.
     - `createShareZipIntent`: gắn `ClipData` và cờ `FLAG_GRANT_READ_URI_PERMISSION` an toàn.
2. **UI Material You M3:**
   - Tại `dlg_save_file.xml`, bố trí hàng nút phụ ngang hàng nhau (`layoutFinishedActions`): `btnOpenGallery` (Xem trong thư viện) và `btnShareZip` (Chia sẻ dạng ZIP).
   - Nút chính `btnSave` đổi trạng thái sang "Chia sẻ" / "Share" sau khi hoàn tất xuất ảnh.
   - Cả hai nút phụ tự động ẩn khi đang xuất hoặc khi toàn bộ batch lỗi, và hiện lên khi có ít nhất 1 ảnh xuất thành công.
3. **Kiểm thử tự động:**
   - Unit test: `ExportZipHelperTest` (5 tests PASS: xử lý tên, ngăn chặn zip slip, nén nhiều file, nén khi có file lỗi, cấu hình intent).
   - Widget & Robolectric integration tests: `SaveImageBSDialogFragmentBatchActionRoboTest` (8 tests PASS: kiểm tra hiển thị nút, share multiple ảnh thành công, share zip 3 ảnh đầy đủ, nén chỉ ảnh thành công khi có ảnh lỗi, và ẩn nút khi batch fail).
   - Lint check: `./gradlew :app:ktlintCheck` PASS 100%.
