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

---

## 💭 Đề xuất tính năng mới (chưa làm)

### A. Text token động (Dynamic Text Placeholders)
**Mô tả:** Cho phép nhập biến trong nội dung text watermark, ví dụ `© {filename} - {date}` hoặc `Shot on {model} • ISO {iso}`. Khi batch, mỗi ảnh tự thay token bằng giá trị riêng (tên file, ngày, số thứ tự, EXIF).
**Triển khai:** Thêm bước resolve token trong `MainViewModel.generateImage` trước khi gọi `buildTextBitmapShader`; tận dụng EXIF đã đọc ở tính năng đã có. Giá trị cao cho batch hàng loạt, gần như không đụng pipeline vẽ.

### B. QR Code Watermark
**Mô tả:** Sinh QR (link bản quyền / liên hệ / portfolio) overlay như một loại Image watermark.
**Triển khai:** Thêm generator QR (zxing hoặc tự vẽ) xuất Bitmap → đẩy vào đúng luồng Image mode giống Signature (`iconUri` nội bộ). Tận dụng lại toàn bộ rotation/alpha/tile sẵn có.

### C. Tùy chọn xuất ảnh (Export Options)
**Mô tả:** Cho chọn định dạng (JPEG/PNG/WEBP) + chất lượng nén, resize cạnh dài khi lưu, và giữ/xóa EXIF gốc (hoặc nhúng `TAG_COPYRIGHT`).
**Triển khai:** Mở rộng `SaveImageBSDialogFragment` + nhánh lưu trong `MainViewModel` (hiện nén qua `Compressor`). Thêm copyright dùng `ExifInterface.setAttribute`.

### D. Position Anchor 9-grid
**Mô tả:** Ngoài kéo thả tự do (CLAMP), thêm preset neo theo lưới 3x3 + margin (góc/cạnh/giữa) cho watermark đơn.
**Triển khai:** Map anchor → `offsetX/offsetY` trong `ImageInfo` (`WaterMarkImageView` đã dùng offset chuẩn hóa 0..1), không cần đổi cơ chế vẽ.

### E. Frame presets cho EXIF border
**Mô tả:** Thêm các kiểu khung (Polaroid, film strip, logo hãng máy Canon/Sony/Apple/Leica) cho tính năng EXIF border đã có.
**Triển khai:** Bộ asset logo + chọn template khung trong `ExifPbFragment`.

### F. Backup/Restore Template & Signature
**Mô tả:** Xuất/nhập template (Room) và chữ ký để chuyển máy.
**Triển khai:** Serialize `Template` (Room) + thư mục signature → zip; cân nhắc tích hợp Firebase (đang là TODO trong `todo.md`).
