---
id: IDEA-01
type: Idea
effort: L
sources: Codex, Claude, Agy, Internal (4/4 — ĐỒNG THUẬN CAO NHẤT toàn bộ review, tất cả 4 agent độc lập đều đề xuất ý này đầu tiên)
files: []
---

# Auto-placement bằng on-device ML (né mặt người/chủ thể)

## Mô tả
Dùng model on-device (Google ML Kit Face Detection / Object Detection / Selfie Segmentation, hoặc Saliency detection) để tự động nhận diện khuôn mặt/người/vật thể chính trên từng ảnh trong batch, từ đó tự động đề xuất vị trí đặt watermark vào vùng "negative space" (nền ít chi tiết, tránh che chủ thể) — toàn bộ chạy local, không cần cloud, không tốn chi phí server.

## Vì sao đáng làm
Cả 4 nguồn AI độc lập (khác model, khác hãng) đều tự đưa ra ý tưởng này ở vị trí ưu tiên cao nhất khi được hỏi "tính năng độc quyền" — tín hiệu mạnh rằng đây là gap thực sự trên thị trường app watermark hiện tại (đa số chỉ hỗ trợ vị trí cố định/kéo tay), và khả thi kỹ thuật cao vì ML Kit chạy on-device miễn phí, không cần hạ tầng backend.

## Triển khai (gợi ý sơ bộ)
- Thêm dependency ML Kit (Face Detection hoặc Object Detection, chọn model nhẹ để không phình APK/thời gian xử lý batch quá lâu).
- Chạy detection song song lúc decode ảnh trong batch, tính vùng "an toàn" (không giao với bounding box chủ thể).
- Map kết quả vào cơ chế offset 0..1 đã có sẵn (giống `FEAT-01`), có thể để user bật/tắt tự động hoặc chỉ dùng làm gợi ý ban đầu (vẫn cho chỉnh tay sau đó).

## Acceptance Criteria (mức khởi tạo, cần refine khi lên kế hoạch chi tiết)
- [x] Có toggle "tự động đặt vị trí" trong editor — refine lúc lên plan: nút HÀNH ĐỘNG (chạy 1 lần cho cả batch) thay vì boolean persist DataStore, vì bản chất là "gợi ý 1 lần" (chính ticket này cũng ghi rõ điều đó ở mục Triển khai), không phải chế độ bật/tắt liên tục.
- [x] Với ảnh có mặt người rõ ràng, watermark tự động không đè lên vùng mặt — verify bằng ảnh mặt người THẬT trên device thật (không chỉ face rect giả lập trong unit test), xem Kết quả kiểm chứng.
- [x] Thời gian xử lý thêm không làm chậm batch quá đáng kể — ML Kit native detect 1 ảnh preview-resolution ~12ms (log `detectFacesImageByteBuffer.start/end` trên TECNO BG6), không đáng kể so với decode/render.
- [x] Vẫn cho phép user override thủ công sau khi có gợi ý tự động — verify tay: bấm anchor khác sau auto-placement, watermark di chuyển đúng theo lựa chọn mới.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-01`, file ticket = `todo/IDEA-01-ai-auto-placement-nhan-dien-chu-the.md`.

## Kết quả kiểm chứng (2026-09-25)

**Thiết kế:** ML Kit Face Detection BUNDLED (`com.google.mlkit:face-detection:16.1.7`, offline hoàn toàn, khớp tagline "Offline Protection" của app) — `MlKitFaceDetectionSource` (impl thật) sau interface `FaceDetectionSource` (test bằng fake, ML Kit native không mô phỏng được trong Robolectric). `AutoPlacementPositioner` là hàm THUẦN chọn 1 trong 9 preset `Anchor` có tổng diện tích giao với mặt nhỏ nhất — tái dùng nguyên `Anchor.toOffset()` đã có sẵn (FEAT-01), không phát minh công thức vị trí mới. `AutoPlacementEngine` điều phối cả batch: decode preview-resolution (tái dùng `BatchExportEngine.PREVIEW_MAX_SIZE`), cache kết quả detect vào `ImageInfo.detectedFaceRectsNormalized` (tránh detect lại), build shader watermark theo đúng cách `generatePreviewBitmap` làm (không cần `ViewInfo`/`adjustMatrix`) để tính tỉ lệ kích thước watermark, rồi ghi `offsetX/offsetY` + chuyển `tileMode=CLAMP` cho ảnh có mặt.

**File đã sửa/tạo:** `settings.gradle.kts`, `app/build.gradle.kts`, `data/model/ImageInfo.kt` (+field cache), `export/AutoPlacementPositioner.kt` (mới), `export/AutoPlacementEngine.kt` (mới), `utils/facedetection/FaceDetectionSource.kt` + `MlKitFaceDetectionSource.kt` (mới), `di/RepositoryModule.kt` (+provider), `ui/MainViewModel.kt` (+`autoPlaceWatermarkForBatch()`/`isAutoPlacing`), `ui/dlg/PositionAnchorBottomSheetFragment.kt` + layout (+nút action), `values/strings.xml` + `values-vi/strings.xml`.

**Bug thật phát hiện + fix trong lúc audit/smoke test:**
1. `AutoPlacementPositioner` dùng `android.graphics.RectF` cho hàm thuần — `app/build.gradle.kts` có `isReturnDefaultValues=true`, khiến constructor RectF bị stub rỗng (field luôn 0) trong plain JUnit không Robolectric → 5/6 test đầu tiên fail sai hướng. Fix: tách `NormalizedRect` (data class Kotlin thuần) dùng nội bộ hàm thuần, chỉ convert từ RectF ở lớp gọi ngoài. Đã lưu vào memory dự án để tránh lặp lại.
2. **Bug thật, phát hiện qua smoke test tay trên TECNO BG6**: `autoPlaceWatermarkForBatch()` gọi `waterMarkRepo.updateImageList()` (chỉ refresh dải thumbnail qua `imageInfoMapFlow`) nhưng canvas chính (`WaterMarkImageView`, `MainActivity` observe riêng `viewModel.selectedImage`) KHÔNG tự refresh — bấm nút xong, watermark KHÔNG di chuyển trên màn hình dù dữ liệu đã đổi đúng. Fix: thêm `selectedImage.value?.uri?.let { waterMarkRepo.select(it) }` sau `updateImageList()` để re-emit `selectedImage`. Verify lại bằng `MainViewModelAutoPlacementRoboTest` (mới) VÀ chụp màn hình thật trước/sau fix trên device — watermark di chuyển rõ từ giữa-phải sang góc trên-trái (né đúng mặt).

**Audit:** 9.5/10 — 1 điểm trừ vì `highlightAnchor` trong `PositionAnchorBottomSheetFragment` không tự đồng bộ theo offset do auto-placement ghi (vẫn hiển thị preset cũ nếu offset không trùng khớp 1 trong 9 preset) — hạn chế đã có sẵn từ trước với kéo tay tự do, không phải hồi quy mới, chấp nhận được trong phạm vi ticket.

**Test:**
- Unit (mới): `AutoPlacementPositionerTest` (6 case: không mặt→null, mặt giữa→chọn góc ưu tiên, mặt che 1 nửa→chọn nửa còn lại, nhiều mặt rải rác→ít giao nhất, mặt phủ toàn ảnh→vẫn có kết quả đỡ tệ nhất, 2 mặt chỉ chừa 1 góc sạch), `AutoPlacementEngineRoboTest` (6 case: ghi offset+CLAMP, cache không detect lại, không mặt→giữ nguyên, ảnh skip→không đụng, 1 ảnh lỗi không hỏng cả batch, progress callback đúng thứ tự), `MainViewModelAutoPlacementRoboTest` (mới, regression test cho bug #2 — verify đúng `viewModel.selectedImage` chứ không chỉ `waterMarkRepo.imageInfoList`), mở rộng `PositionAnchorAutoPlacementRoboTest` (bấm nút → loading đúng lúc → offset đổi).
- Integration (androidTest, mới): `MlKitFaceDetectionSourceIntegrationTest` — ML Kit thật, ảnh màu đặc/nhiễu → rỗng, không crash. Chạy PASS trên TECNO BG6.
- `./gradlew testDebugUnitTest` (toàn bộ, sau khi thêm ~692 test): XANH, chạy lại nhiều lần ổn định. `./gradlew ktlintCheck`: XANH. `./gradlew :app:assembleRelease` (R8/minify): thành công, ML Kit không bị strip sai.

**Smoke test thật (device đã khoá session — TECNO BG6, serial `118743744X002560`):**
- Ảnh test: chân dung thật (nguồn `randomuser.me` — stock photo dùng cho mục đích test phần mềm, không phải ảnh scrape từ mạng xã hội cá nhân), ML Kit thật detect đúng 1 mặt tại `RectF(0.09, 0.31, 0.55, 0.76)`.
- Trước fix bug #2: bấm "Auto-place away from faces" → không có gì đổi trên canvas (dữ liệu đổi ngầm nhưng không hiển thị) — CHỤP MÀN HÌNH xác nhận bug thật.
- Sau fix: bấm nút → watermark di chuyển từ vị trí mặc định (giữa-phải, đè lên tóc/tai) sang GÓC TRÊN-TRÁI (né hẳn khỏi vùng mặt 0.09-0.55 x 0.31-0.76) — CHỤP MÀN HÌNH xác nhận đúng.
- Override tay sau đó: bấm nút mũi tên góc trên-phải trong lưới 9-anchor → watermark di chuyển đúng theo lựa chọn mới, không bị auto-placement ghi đè lại.
- `dumpsys`/logcat: `FATAL EXCEPTION` = 0 lần suốt phiên. ML Kit log xác nhận dùng bundled model local (`Considering local module com.google.mlkit.dynamite.face:10000`), không cần tải qua mạng.
