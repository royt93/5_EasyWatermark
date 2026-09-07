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
- **Preview token động trong editor (2026-09-06):** ĐÃ XONG. `MainViewModel.resolvePreviewText(text, imageInfo)` (public, tái dùng `resolveTextTokens` private sẵn có) resolve token theo ảnh đang chọn — `{seq}` lấy đúng vị trí thật trong `waterMarkRepo.imageInfoList`, `{filename}` cache theo uri (tránh query `ContentResolver` lặp lại mỗi ký tự gõ). `MainActivity` gọi hàm này ở cả `viewModel.waterMark.observe` (đổi text/config) lẫn `viewModel.selectedImage.observe` (đổi ảnh) rồi mới set `launchView.ivPhoto.config` — **chỉ set text đã resolve vào View để render, KHÔNG ghi ngược vào repo**, nên `EditTextContentFragment` (đọc `shareViewModel.waterMark.value.text`) vẫn thấy đúng token gốc để sửa tiếp.
- **UI chèn token nhanh (2026-09-06):** ĐÃ XONG. `EditTextContentFragment.setupTokenChips()` sinh dãy `Chip` (Material) từ `TextTokenResolver.SUPPORTED_TOKENS`, đặt trong `ChipGroup` bọc `HorizontalScrollView` (`dlg_edit_text.xml`, id `hsvTokens`/`cgTokens`) giữa tiêu đề và ô nhập. Bấm chip chèn `"{token}"` thay phần đang bôi đen tại vị trí con trỏ trong `etWaterText`.

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

### 7. Position Anchor 9-grid (2026-09-05)
**Đã làm:** Preset neo watermark theo lưới 3x3 (góc/cạnh/giữa) + slider margin, thay thế/bổ sung cho kéo thả tự do (CLAMP).
- `data/model/Anchor.kt` (enum 9 giá trị) — `toOffset(marginPercent, wmFracW, wmFracH)` tính offsetX/offsetY chuẩn hóa 0..1, trừ kích thước watermark thật để không tràn mép.
- `WaterMarkImageView.applyAnchor()` — tính toán dựa trên `drawableBounds`/`layoutShader` (chỉ View mới biết kích thước thật), tái dùng callback `onOffsetChanged` sẵn có (giống hệt luồng kéo thả tay).
- `WaterMarkRepository` — persist `anchor`/`marginPercent` (lựa chọn cuối) vào DataStore qua `updateAnchor()`/`updateMargin()`.
- `ui/dlg/PositionAnchorBottomSheetFragment.kt` + `f_position_anchor_bottom_sheet.xml` — bottom sheet 9 nút vuông (3 `LinearLayout` hàng ngang weight=1) + `Slider` margin 0-20%, mở từ nút "Position" mới trong `TileModeFragment`/`f_tile_mode.xml` (chỉ hiện khi tileMode = Decal/CLAMP).
- **Lưu ý đã verify trên thiết bị thật:** preset chỉ thấy rõ hiệu ứng dịch chuyển khi watermark nhỏ hơn canvas (Image/logo, hoặc Text có hGap/vGap > 0) — nếu gap = 0, block CLAMP to bằng cả ảnh nên 9 vị trí trông giống nhau (không phải bug, giới hạn hình học khi watermark ~ full-canvas).

### 8. Share App (2026-09-06)
**Đã làm:** Pill "Share App" trong `AboutActivity` mở `Intent.ACTION_SEND` text kèm link Play Store.
- `a_about.xml` id `tvShareApp`, cùng hàng `HorizontalScrollView` với Rate Us/More Apps.
- `R.string.share_app_message` format tên app + `packageName`.

### 9. Frame presets cho EXIF border (2026-09-06)
**Đã làm:** 4 kiểu khung EXIF border chọn được (thay vì chỉ 1 kiểu cố định trước đây) — **không dùng logo hãng máy thật** (tránh rủi ro trademark), chỉ vẽ bằng Canvas thuần.
- `data/model/ExifFrameStyle.kt` (enum `CLASSIC`/`POLAROID`/`FILM_STRIP`/`MINIMAL`) + `obtain(ordinal)`.
- `WaterMark.exifFrameStyle` (Int, mặc định `CLASSIC`) — persist qua `WaterMarkRepository.updateExifFrameStyle()`/`KEY_EXIF_FRAME_STYLE`, theo đúng pattern enum-ordinal của `anchor`.
- `MainViewModel.buildExifBorderBitmap()` dispatch theo style tại thời điểm export (`generateImage`), tách từ code gốc thành 4 hàm riêng (`buildClassicExifBorder`/`buildPolaroidExifBorder`/`buildFilmStripExifBorder`/`buildMinimalExifBorder`):
  - **Classic** — hành vi gốc: thanh trắng đáy, tên máy đậm trái, thông số + ngày phải.
  - **Polaroid** — viền trắng dày đều 4 cạnh, caption serif căn giữa ở đáy.
  - **Film Strip** — dải đen trên/dưới có lỗ sprocket bo góc, caption phủ scrim mờ ngay trong ảnh (tránh đè lên lỗ).
  - **Minimal** — dải trắng mỏng (6% thay vì 12% chiều cao), 1 dòng chữ gộp tên máy · thông số · ngày.
- `ui/dlg/ExifPbFragment.kt` + `dlg_exif_border.xml` — hàng 4 `MaterialButton` chọn style (id `btnStyleClassic/Polaroid/FilmStrip/Minimal`, group `groupFrameStyle`), chỉ hiện khi bật switch EXIF, highlight tách riêng thành `ui/dlg/ExifFrameStyleHighlighter.kt` (testable, không cần Fragment/Hilt).
- `groupFrameStyle` có `android:visibility="gone"` mặc định (audit fix) — tránh lộ ra 1 frame nếu `waterMark` LiveData chưa có giá trị ở lần observe đầu.
- **Test:** unit (`ExifFrameStyleTest`), Robolectric cho dispatch bitmap (`MainViewModelExifBorderRoboTest`, chỉ assert kích thước — môi trường Robolectric máy build không rasterize pixel thật) + highlighter (`ExifFrameStyleHighlighterRoboTest`), integration DataStore thật (`WaterMarkRepositoryIntegrationTest`, chạy PASS trên TECNO KJ7 Android 14 + Pixel 7 Pro).
- **Đã verify trên thiết bị thật (TECNO KJ7):** bật switch → 4 nút Frame Style hiện đúng, bấm đổi style → highlight đổi đúng, đóng/mở lại dialog → style đã chọn (Minimal) vẫn giữ nguyên (persist qua DataStore đúng).

### 10. Fix edge-to-edge che nội dung (2026-09-07)
**Bug (do user báo, không liên quan trực tiếp feature #9 nhưng phát hiện trong lúc smoke test):** targetSdk 37 bật edge-to-edge, nhiều màn hình vẽ tràn xuống dưới navigation bar mà không cộng padding bù — nút CTA cuối layout bị nav bar che khuất trên thiết bị 3-button nav (verify trên TECNO KJ7).
- **`SignatureActivity`** — kế thừa `BaseActivity` (đã có `applyEdgeToEdge()`) nhưng thiếu insets listener riêng như `AboutActivity`/`MainActivity`; `llBottomControls` (chứa nút "Apply Signature") chỉ có padding cố định 16dp. Thêm `ViewCompat.setOnApplyWindowInsetsListener` cộng `systemBars.bottom` vào padding gốc (capture 1 lần trước khi đăng ký listener — tránh cộng dồn vô hạn nếu listener chạy lại).
- **`BaseBSDFragment`** (base chung mọi `BottomSheetDialogFragment`: `ExifPbFragment`, `PositionAnchorBottomSheetFragment`, `SignatureBottomSheetFragment`, `SaveImageBSDialogFragment`, `QrCodeBottomSheetFragment`, `TextWatermarkBSDFragment`, `EditTemplateContentFragment`, `GalleryFragment`) — dialog window không tự inset trên OEM Transsion/TECNO. Thêm `WindowCompat.setDecorFitsSystemWindows(window, false)` + padding insets áp lên chính view `design_bottom_sheet` (nơi `BottomSheetBehavior` thật sự đo/định vị, không phải content root bên trong).
- **Đã verify trên thiết bị thật (TECNO KJ7):** "Apply Signature" hết bị che; dialog Leica EXIF Border có khoảng cách rõ ràng với nav bar.

---

## 💭 Đề xuất tính năng mới (chưa làm)

### F. Backup/Restore Template & Signature
**Mô tả:** Xuất/nhập template (Room) và chữ ký để chuyển máy.
**Triển khai:** Serialize `Template` (Room) + thư mục signature → zip; cân nhắc tích hợp Firebase (đang là TODO trong `todo.md`).
