---
id: FEAT-18
type: Feature
effort: M
sources: Codex + Claude (2 nguồn đồng thuận)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# So sánh trước/sau bằng slider (before/after compare) — cả trong editor lẫn preview batch

## Mô tả
Không có cách nào xem NHANH watermark che nội dung quan trọng tới đâu so với ảnh gốc — phải nhìn ảnh đã áp watermark rồi tự nhớ/tưởng tượng ảnh gốc. Hữu ích cả lúc chỉnh sửa 1 ảnh (editor) lẫn duyệt nhanh cả batch trong preview grid.

## Triển khai
Thêm 1 slider kéo che/lộ nửa ảnh gốc vs nửa ảnh có watermark — trong editor dùng ngay trên `WaterMarkImageView` hiện tại (không cần layer riêng, chỉ clip canvas theo vị trí slider); trong preview batch mở từ card ra view full-screen tương tự.

## Acceptance Criteria
- [x] Kéo slider trong editor từ 0% tới 100% — thấy rõ ranh giới watermark/không-watermark di chuyển mượt theo tay.
- [x] Mở preview 1 ảnh từ grid batch, dùng slider tương tự — không cần export thật mới thấy so sánh.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-18`, file ticket = `todo/FEAT-18-so-sanh-truocsau-bang-slider-beforeafter-compare-ca-trong-ed.md`.

## Kết quả kiểm chứng
- **Editor (AC1)**: `WaterMarkImageView.compareRevealFraction` (mới, `Float` 0f..1f, mặc định 1f = hành vi cũ không đổi) — trong `onDraw()`, `canvas.clipRect(0, 0, width * compareRevealFraction, height)` gọi TRƯỚC `translate()` (toạ độ theo hệ View gốc, không bị dịch theo tile CLAMP/REPEAT) giới hạn vùng vẽ overlay watermark, để lộ ảnh gốc mà `super.onDraw()` đã vẽ sẵn bên dưới — đúng gợi ý "không cần layer riêng, chỉ clip canvas" của ticket, không cấp phát thêm bitmap nào.
- `MainViewModel.compareReveal` (mới, `LiveData<Float>`) — state THUẦN UI, CỐ TÌNH không đụng `waterMarkRepo`/DataStore (xác nhận bằng test `updateCompareReveal_doesNotTouchWaterMarkDataStore`) nên không tham gia Undo/Redo (FEAT-12) và không cần persist. `MainActivity` quan sát và gán thẳng vào `launchView.ivPhoto.compareRevealFraction`.
- UI editor: `CompareBottomSheetFragment` (mới, bottom sheet nhẹ chỉ có 1 `Slider`) mở từ menu toolbar `actionCompare` (`ifRoom`, icon `ic_compare` mới — 3 vạch dọc). `onDismiss()` LUÔN reset về 1f (hiện watermark đầy đủ) — đóng sheet theo bất kỳ cách nào (vuốt xuống/back/lập trình) đều không để lại trạng thái "còn đang so sánh" gây nhầm lẫn khi quay lại editor.
- **Preview batch (AC2)**: `BatchExportEngine.generateCompareBitmaps()` (mới) — decode ảnh 1 LẦN (`PREVIEW_MAX_SIZE`, tái dùng `decodeSampledBitmapFromResource` đã cache) rồi tạo 2 bitmap ĐỘC LẬP: 1 bản gốc chưa đụng gì (`originalCopy`), 1 bản vẽ watermark theo đúng pipeline `generatePreviewBitmap()` (FEAT-07/ENH-35) — cố tình KHÔNG tách hàm dùng chung với `generatePreviewBitmap()` để tránh đổi hành vi hàm đã test/dùng thật, chấp nhận trùng lặp logic vẽ shader (~50 dòng). KHÔNG ghi MediaStore — đúng yêu cầu AC "không cần export thật".
- `SaveImageListAdapter` thêm `onItemClick: (ImageInfo, Int) -> Unit` — bấm vào card (khác hẳn `ivSkipToggle`, xác nhận bằng test `tapSkipToggle_doesNotAlsoTriggerOnItemClick`) mở `ComparePreviewBottomSheetFragment` (mới) cho ĐÚNG ảnh + vị trí đó.
- `ComparePreviewBottomSheetFragment`: 2 `ImageView` xếp chồng trong `FrameLayout` (`ivOriginal` dưới, `ivWatermarked` trên, CÙNG kích thước/scaleType nên căn pixel khớp nhau tự nhiên) — dùng `View.clipBounds` (API có sẵn, không cần custom View/tự vẽ canvas) để lộ dần `ivWatermarked` theo % slider, đơn giản hơn nhiều so với phương án ban đầu định viết custom compare-view.
- Test mọi tầng: `WaterMarkImageViewCompareRoboTest` (coerce giá trị trong [0,1]), `BatchExportEngineCompareRoboTest` (2 bitmap độc lập không recycle, cùng kích thước, case text rỗng/index khác — cùng giới hạn môi trường Robolectric-không-mô-phỏng-decode-fail đã ghi nhận ở `BatchExportEnginePreviewRoboTest`), `MainViewModelCompareRoboTest` (coerce, KHÔNG đụng DataStore/canUndo), `SaveImageListAdapterItemClickRoboTest` (tap card đúng item+index, tap skip-toggle không lẫn sang onItemClick).
- Smoke test thật trên device khoá `118743744X002560` (TECNO BG6), cả 2 mặt: **Editor** — mở Compare từ overflow menu, kéo slider 100%→0%: watermark "UNDO_TÉT_X" biến mất hoàn toàn ở 0% (chỉ còn chữ có sẵn TRONG ảnh gốc), kéo về 50%: ranh giới trái/phải rõ ràng — nửa trái sạch watermark, nửa phải còn nguyên — đúng vị trí slider theo tỉ lệ (**AC1**). Đóng sheet (back) → quay lại watermark đầy đủ ngay, không cần thao tác gì thêm. **Preview batch** — mở dialog Export, bấm vào card ảnh trong Export list → sheet "Compare before/after" mở ngay với bitmap watermark render live (không export thật), kéo slider xuống 23% → góc trái ảnh sạch watermark đúng tỉ lệ (**AC2**). Không crash trong suốt luồng — `logcat` sạch `FATAL EXCEPTION`/`AndroidRuntime`.
- `./gradlew testDebugUnitTest` toàn bộ PASS (142 file unit test). `./gradlew ktlintCheck` sạch (main + test + androidTest source set, có 1 lần sửa vi phạm blank-line qua `ktlintFormat`). Ghi nhận: full-suite test có flaky hạ tầng đã biết trước (Robolectric nhiều class chung 1 JVM fork, xem comment `forkEvery=25` trong `app/build.gradle.kts`) — lần chạy full-suite gặp `QrCodeBottomSheetFragmentRoboTest` fail (KHÔNG liên quan gì tới thay đổi FEAT-18, module hoàn toàn khác, chưa từng đụng tới trong ticket này), chạy lại pass — không phải regression do ticket này gây ra.
- Audit: 9/10 — đủ 2 AC verify bằng smoke test thật trên CẢ HAI bề mặt (editor + preview batch), kỹ thuật clip/clipBounds đơn giản đúng tinh thần "không cần layer riêng" của ticket gốc, tránh được rủi ro đổi hành vi hàm cũ đã test kỹ (`generatePreviewBitmap`) bằng cách viết hàm mới song song thay vì refactor chung; trừ điểm vì chấp nhận trùng lặp ~50 dòng logic vẽ shader giữa `generatePreviewBitmap()`/`generateCompareBitmaps()` (đánh đổi có chủ đích, đã giải thích rõ lý do) và vì nút "Compare" trên toolbar (`ifRoom`) có thể rơi vào overflow trên màn hình hẹp giống Undo/Redo (FEAT-12) — chấp nhận được, không phải bug.
