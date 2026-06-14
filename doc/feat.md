# Tính Năng EasyWatermark

> Cập nhật: 2026-06-14. File theo dõi trạng thái tính năng (✅ đã làm / 💭 đề xuất).

## ✅ Đã triển khai

### 1. Dynamic EXIF & Device Info Watermark (Khung kiểu Leica / Xiaomi)
Khung viền ảnh in thông số máy (Model, ngày chụp, F-number, ISO...) đọc từ EXIF.
- `FuncTitleModel.ExifBorder` → `MainActivity` mở `ui/dlg/ExifPbFragment.kt`.
- Đọc EXIF tại `utils/bitmap/BitmapUtils.kt` (`TAG_MODEL`, `TAG_DATETIME`, `TAG_F_NUMBER`, `TAG_ISO_SPEED_RATINGS`).

### 2. Custom Handwritten Signature (Chữ ký tay)
Vẽ chữ ký tay, xuất bitmap rồi tái dùng pipeline Image watermark.
- `ui/SignatureActivity.kt`, `ui/dlg/SignatureBottomSheetFragment.kt`, `ui/widget/SignatureView.kt`, `data/repo/SignatureRepository.kt`.
- Chữ ký lưu dạng `*.webp` chứa "signature" → `WaterMarkImageView.buildIconBitmapShader` áp `PorterDuffColorFilter` để tint màu.

### 3. Khác đã có sẵn
- Watermark Text & Image, Template lưu/tái dùng, Batch processing nhiều ảnh.
- Color picker (`FuncTitleModel.Color` → `ColorFragment`).
- Dynamic color / Material You qua module `:cmonet`.

### 4. Text token động (Dynamic Text Placeholders) — export-time
Token trong nội dung text watermark được thay theo từng ảnh khi xuất (batch).
- `MainViewModel.resolveTextTokens()` resolve trước `buildTextBitmapShader` trong `generateImage`, có guard `!text.contains('{')` nên text thường không đổi.
- Token hỗ trợ: `{filename}` `{seq}` `{date}` `{model}` `{make}` `{iso}` `{fnumber}` `{exposure}` `{focal}` `{exif}` (lấy từ `ImageInfo.exifModel` + `OpenableColumns.DISPLAY_NAME`).
- **Còn lại (chưa làm):** preview trong editor hiện hiển thị token nguyên văn (chỉ resolve khi save); và UI nút chèn token nhanh trong dialog sửa text.

### 5. Export options (định dạng + chất lượng + WEBP + resize + copyright)
- `SaveImageBSDialogFragment` có dropdown format (JPEG/PNG/**WEBP**) + slider quality; `MainViewModel.saveOutput()` lưu vào `UserPreferences`.
- **WEBP:** thêm vào `popArray` + `formatByIndex`; `OutputImageUtils.extensionFor()` xử lý đuôi/mime; serialize qua ordinal trong `UserConfigRepository`.
- **Resize cạnh dài:** dropdown Original/1080/2048/4096 → `maxOutputLongEdge`; `OutputImageUtils.resizeIfNeeded()` áp trước `compress` trong `generateImage`.
- **EXIF copyright:** ô nhập copyright → `UserPreferences.copyright`; `MainViewModel.applyCopyrightExif()` nhúng `TAG_COPYRIGHT`/`TAG_ARTIST` sau khi ghi file (cả MediaStore Q+ lẫn file < Q; bỏ qua PNG).

### 6. QR Code Watermark
**Đã làm:** Sinh QR (link bản quyền / liên hệ / portfolio) overlay như một loại Image watermark.
- `utils/QrCodeGenerator.kt` (zxing core 3.5.3) encode text → Bitmap (nền trong suốt).
- `ui/dlg/QrCodeBottomSheetFragment.kt` nhập text → preview live → `updateIcon(uri)` reuse toàn bộ pipeline Image (rotation/alpha/tile).
- `FuncTitleModel.FuncType.QRCode` + entry trong `contentFunList` (icon `ic_func_qr_code`) + route trong `MainActivity.handleFuncItem`.

---

## 💭 Đề xuất tính năng mới (chưa làm)

### D. Position Anchor 9-grid
**Mô tả:** Ngoài kéo thả tự do (CLAMP), thêm preset neo theo lưới 3x3 + margin (góc/cạnh/giữa) cho watermark đơn.
**Triển khai:** Map anchor → `offsetX/offsetY` trong `ImageInfo` (`WaterMarkImageView` đã dùng offset chuẩn hóa 0..1), không cần đổi cơ chế vẽ.

### E. Frame presets cho EXIF border
**Mô tả:** Thêm các kiểu khung (Polaroid, film strip, logo hãng máy Canon/Sony/Apple/Leica) cho tính năng EXIF border đã có.
**Triển khai:** Bộ asset logo + chọn template khung trong `ExifPbFragment`.

### F. Backup/Restore Template & Signature
**Mô tả:** Xuất/nhập template (Room) và chữ ký để chuyển máy.
**Triển khai:** Serialize `Template` (Room) + thư mục signature → zip; cân nhắc tích hợp Firebase (đang là TODO trong `todo.md`).
