---
id: FEAT-16
type: Feature
effort: L
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
  - app/src/main/java/com/mckimquyen/watermark/data/model/ImageInfo.kt
---

# Crop/straighten nhanh trước khi watermark

## Mô tả
Chưa có công cụ chỉnh crop tỉ lệ (1:1, 4:5, 16:9...) hay xoay thẳng nhẹ (deskew) trước khi đóng dấu — nhu cầu thực tế phổ biến trước khi watermark ảnh sản phẩm/tài liệu cho đúng khung chuẩn nền tảng đăng (khác FEAT-09 chỉ resize theo cạnh dài, không đổi khung hình/crop nội dung).

## Triển khai
Thêm màn hình crop đơn giản (thư viện crop có sẵn hoặc tự vẽ overlay) trước bước watermark, lưu crop rect + góc xoay vào `ImageInfo`, áp dụng transform này TRƯỚC khi build watermark shader (cả preview lẫn export dùng chung).

## Acceptance Criteria
- [x] Crop ảnh theo 1 tỉ lệ chuẩn (vd 1:1) rồi export — file kết quả đúng tỉ lệ đã crop, watermark vẫn đúng vị trí tương đối theo khung mới.
- [x] Xoay thẳng nhẹ vài độ rồi export — ảnh xuất ra đã xoay đúng, không mất watermark/méo hình.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-16`, file ticket = `todo/FEAT-16-cropstraighten-nhanh-truoc-khi-watermark.md`.

## Kết quả kiểm chứng (2026-09-24)

**Kiến trúc**: `ImageInfo.cropRect: RectF?` + `rotationDegrees: Float` (in-memory, per-image, không qua Room/DataStore). `BitmapUtils.applyCropAndRotate()` (rotate-rồi-crop, KHÔNG BAO GIỜ recycle bitmap `src` truyền vào — bitmap có thể đang được `BitmapCache` giữ refcount) dùng chung cho 4 nơi render: `WaterMarkImageView` (preview editor), `BatchExportEngine.generateImage()` (export thật), `.generatePreviewBitmap()` (grid preview FEAT-07), `.generateCompareBitmaps()` (so sánh trước/sau FEAT-18, áp cho cả 2 bản). Entry point: nút toolbar "Crop" mở `CropActivity` full-screen (mirror `SignatureActivity`) cho ảnh đang `selectedImage`, UI pan/zoom ảnh sau khung crop cố định theo tỉ lệ (Free/1:1/4:5/16:9/9:16/3:4) — tái dùng cấu trúc `ScaleGestureDetector`+pan-delta của `WaterMarkImageView`, quyết định qua `AskUserQuestion` để tránh viết khung kéo-góc tự do.

**Bug thật phát hiện + fix qua smoke test (không phải qua test tự động)**: `CropOverlayView` ban đầu tính `coverScale` (tỉ lệ phủ khung crop) theo bounding-box ĐÃ PAD của bitmap sau khi `Bitmap.createBitmap(...,matrix,...)` xoay — bounding-box này luôn LỚN HƠN nội dung ảnh thật, nên ở góc xoay lớn (thấy rõ nhất ở ±45°, biên trên của slider) khung crop hở ra góc trong suốt/trống thay vì phủ kín ảnh. Sửa bằng công thức lượng giác đúng (nội tiếp khung trục-thẳng vào hình chữ nhật đã xoay quanh tâm): `scale = max((Fw·|cosθ|+Fh·|sinθ|)/W, (Fw·|sinθ|+Fh·|cosθ|)/H)` dựa trên kích thước ảnh GỐC (trước khi pad), không phải bounding-box. Verify lại bằng smoke test thật (screenshot trước/sau + export file thật `566×566` phủ kín hoàn toàn, không góc trống) và unit test hồi quy `computeCoverScale_rotated45_...doublesTheNaiveBoundingBoxScale`.

**Giới hạn môi trường ghi nhận**: Robolectric shadow của `Bitmap.createBitmap(src,...,matrix,filter)` trả `width=0` sai cho góc xoay KHÔNG phải bội số 90° (xác nhận qua debug trực tiếp) — không dùng bitmap xoay thật cho unit test ở các góc này, tách công thức `coverScale` thành hàm thuần `CropOverlayView.computeCoverScale()` (không đụng Bitmap) để test được trên JVM, mirror giới hạn tương tự đã ghi nhận ở FEAT-07/FEAT-15.

**Test mới (tổng 19 test case, toàn bộ PASS)**:
- Unit/Robolectric: `BitmapUtilsCropRotateRoboTest` (5 case: fast-path không copy, crop đúng vùng, rotate90 đảo kích thước, rotate+crop kết hợp, clamp rect ngoài biên, không bao giờ recycle `src`), `WaterMarkRepositoryUpdateImageCropRoboTest` (4 case: đúng uri, giữ field khác, clear crop giữ rotation, uri lạ không đổi gì), `CropOverlayViewWidgetTest` (7 case: Free trả null, center-crop mặc định đúng cho ảnh vuông/ảnh chữ nhật, pan bị clamp trong biên, 2 case công thức `computeCoverScale` gồm đúng case bug 45° vừa fix), `CropActivityRoboTest` (5 case: thiếu uri tự finish, 6 chip tỉ lệ đúng thứ tự/mặc định Free, range slider -45..45, toolbar back finish, nút Áp dụng đúng style/text).
- Integration (`app/src/androidTest`, chạy PASS trên **TECNO KJ7** thật): `BatchExportEngineCropRotateIntegrationTest` (3 case: crop 1:1 800x600→400x600 đúng, rotate90 800x600→600x800 + watermark không mất, baseline không crop/rotate không đổi hành vi cũ).
- `./gradlew testDebugUnitTest` toàn bộ PASS (2 lần full-suite gặp 2 test flaky KHÁC NHAU không liên quan — `MainViewModelCompressImgRoboTest` rồi `ToastExtensionWidgetTest` — xác nhận cả 2 PASS riêng lẻ, đúng flakiness cross-test JVM fork đã ghi trong `doc/todo.md`, không phải do đợt sửa này).

**Lint/ktlint**: `ktlintCheck` sạch. `./gradlew lint` verify qua `git stash -u` đối chiếu baseline: 276 lỗi PRE-EXISTING (không thuộc phạm vi, đã ghi nhận nhiều lần trong file `done/` khác) — nhánh có FEAT-16 ban đầu 287 lỗi (11 lỗi `MissingTranslation` cho string mới `action_crop`/`crop_*`), đã chủ động thêm bản dịch đầy đủ cho cả 12 locale còn lại (de/es/fr/it/ja/nb-rNO/nn/pt/pt-rBR/ru/zh-rCN/zh-rTW) thay vì nợ tiếp — về lại đúng 276 baseline, không tăng thêm 1 lỗi lint nào.

**Smoke test thật trên TECNO KJ7** (khoá qua `AskUserQuestion` do có 2 device, theo R3): mở app → chọn ảnh → menu overflow → Crop → chip 1:1 → kéo slider Xoay thẳng lên gần biên +45° (phát hiện bug góc trong suốt ở bước này, đã fix + verify lại) → Áp dụng → preview editor cập nhật đúng khung vuông đã xoay → mở "So sánh trước/sau" xác nhận không crash, ảnh vuông render đúng → Export thật ra file `566×566` (`adb pull` xem trực tiếp) phủ kín hoàn toàn, watermark đầy đủ, không góc đen/trong suốt. Logcat sạch suốt phiên, không `FATAL EXCEPTION`.

**Điểm tự chấm: 9.5/10** — trừ 0.5 vì đơn giản hoá UI có chủ đích theo quyết định trước (pan/zoom sau khung cố định thay vì kéo-góc tự do, "Free" = không crop thay vì crop tự do thật) và không khôi phục crop/rotation cũ khi mở lại `CropActivity` (luôn bắt đầu từ Free/0°) — cả 2 đều nằm ngoài phạm vi AC, đã ghi rõ trong kế hoạch trước khi code.
