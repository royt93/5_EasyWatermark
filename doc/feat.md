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

### 5. Export options (định dạng + chất lượng) — đã có sẵn từ trước
- `SaveImageBSDialogFragment` có dropdown format (JPEG/PNG) + slider quality; `MainViewModel.saveOutput()` lưu vào `UserPreferences` (`outputFormat`/`compressLevel`).
- **Còn lại (chưa làm):** thêm WEBP, resize cạnh dài khi lưu, giữ/xóa EXIF gốc hoặc nhúng `TAG_COPYRIGHT`.

---

## 💭 Đề xuất tính năng mới (chưa làm)

### B. QR Code Watermark
**Mô tả:** Sinh QR (link bản quyền / liên hệ / portfolio) overlay như một loại Image watermark.
**Triển khai:** Thêm generator QR (zxing hoặc tự vẽ) xuất Bitmap → đẩy vào đúng luồng Image mode giống Signature (`iconUri` nội bộ). Tận dụng lại toàn bộ rotation/alpha/tile sẵn có.

### C. Mở rộng Export options (WEBP + resize + EXIF copyright)
**Mô tả:** Bổ sung cho mục "Export options" đã có: thêm WEBP, resize cạnh dài khi lưu, giữ/xóa EXIF gốc (hoặc nhúng `TAG_COPYRIGHT`).
**Triển khai:** Thêm WEBP vào `popArray` + `trapOutputExtension`/`UserPreferences` serialize; resize bitmap trước `compress`; copyright dùng `ExifInterface.setAttribute` sau khi ghi file.

### D. Position Anchor 9-grid
**Mô tả:** Ngoài kéo thả tự do (CLAMP), thêm preset neo theo lưới 3x3 + margin (góc/cạnh/giữa) cho watermark đơn.
**Triển khai:** Map anchor → `offsetX/offsetY` trong `ImageInfo` (`WaterMarkImageView` đã dùng offset chuẩn hóa 0..1), không cần đổi cơ chế vẽ.

### E. Frame presets cho EXIF border
**Mô tả:** Thêm các kiểu khung (Polaroid, film strip, logo hãng máy Canon/Sony/Apple/Leica) cho tính năng EXIF border đã có.
**Triển khai:** Bộ asset logo + chọn template khung trong `ExifPbFragment`.

### F. Backup/Restore Template & Signature
**Mô tả:** Xuất/nhập template (Room) và chữ ký để chuyển máy.
**Triển khai:** Serialize `Template` (Room) + thư mục signature → zip; cân nhắc tích hợp Firebase (đang là TODO trong `todo.md`).
