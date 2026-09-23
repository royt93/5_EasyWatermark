---
id: FEAT-23
type: Feature
effort: M
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/WaterMarkRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
---

# Ghi nhớ vị trí watermark riêng theo tỉ lệ khung ảnh (portrait/landscape)

## Mô tả
Vị trí/anchor watermark hiện lưu CHUNG 1 config cho MỌI ảnh bất kể tỉ lệ khung hình — ảnh dọc và ảnh ngang thường cần vị trí khác nhau (vd logo góc dưới phải trên ảnh ngang có thể bị lệch/che nội dung khác trên ảnh dọc cùng config).

## Triển khai
Lưu 2 preset offset/anchor riêng theo orientation (dọc/ngang, phân loại bằng tỉ lệ width/height ảnh đang load) trong `WaterMarkRepository`, tự động áp preset đúng khi load ảnh mới theo orientation của ảnh đó.

## Acceptance Criteria
- [x] Chỉnh vị trí watermark riêng cho 1 ảnh dọc, load 1 ảnh ngang khác — vị trí không bị áp nhầm theo preset dọc.
- [x] Quay lại ảnh dọc — vị trí đã chỉnh trước đó cho orientation dọc vẫn giữ nguyên.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-23`, file ticket = `todo/FEAT-23-ghi-nho-vi-tri-watermark-rieng-theo-ti-le-khung-anh-portrait.md`.

## Kết quả kiểm chứng
- **Phát hiện orientation**: `WaterMarkImageView` thêm callback `onImageOrientationKnown(isPortrait: Boolean)`, gọi đúng 1 lần trong nhánh `applyNewConfig()` khi `decodedUri != uri` (ảnh THẬT SỰ mới, không gọi lại khi chỉ đổi config trên cùng ảnh) — dùng kích thước bitmap ĐÃ decode (đã áp EXIF rotation qua `BitmapUtils.decodeSampledBitmapFromResourceSync`), không phải kích thước gốc file, nên ảnh có EXIF xoay vẫn phân loại đúng theo hình dạng THẬT nhìn thấy. Quy tắc: `height >= width` → dọc (trường hợp ảnh vuông coi là dọc, tie-break tuỳ ý, ghi rõ trong code).
- **Lưu preset riêng**: `WaterMarkRepository` thêm 4 khoá DataStore (`KEY_ANCHOR_PORTRAIT/LANDSCAPE`, `KEY_MARGIN_PORTRAIT/LANDSCAPE`) + biến nhớ `lastKnownIsPortrait`. `updateAnchor()`/`updateMargin()` (đường user tự chỉnh) ghi ĐỒNG THỜI vào giá trị đang active VÀ preset của orientation hiện biết, trong CÙNG 1 `dataStore.edit{}` (atomic). `applyOrientationPreset(isPortrait)` (mới, gọi từ `MainViewModel.applyOrientationPreset()` khi có callback trên) LUÔN ghi đè giá trị active bằng preset đã lưu HOẶC giá trị mặc định (`Anchor.CENTER`/`DEFAULT_MARGIN_PERCENT`) nếu chưa từng lưu — không bao giờ chỉ "giữ nguyên nếu không có preset" (đó chính là bug rò rỉ vị trí giữa 2 orientation mà ticket muốn tránh).
- **Bug thật bắt được TRƯỚC khi ra device**: implementation đầu tiên chỉ ghi đè active value `IF` preset tồn tại (`prefs[key]?.let{...}`), khiến ảnh orientation MỚI (chưa từng lưu) vô tình thừa hưởng vị trí vừa chỉnh của orientation KHÁC. Test `editingPortraitImage_thenSwitchingToLandscape_doesNotLeakPortraitPosition` fail, lộ đúng lỗi này — sửa bằng cách luôn có fallback mặc định, thêm test `applyOrientationPreset_noPresetForThisOrientation_fallsBackToDefault_notLeftoverFromOtherOrientation` khoá lại.
- Theo đúng nguyên tắc đã thiết lập ở FEAT-12 ("thay đổi do hệ thống tự động ≠ hành động user"): `applyOrientationPreset()` KHÔNG bao giờ gọi `snapshotForUndoIfDue()` — đổi ảnh không phải 1 bước Undo được (test `applyOrientationPreset_doesNotPushUndoEntry`).
- Test: `WaterMarkRepositoryOrientationPresetRoboTest` (mới, 7 test) — no-preset giữ nguyên active, không rò rỉ portrait→landscape, khôi phục đúng khi quay lại, 2 preset độc lập qua nhiều lần đổi qua lại, không tạo entry Undo, chỉnh trước khi biết orientation vẫn áp dụng ngay (không lưu preset), fallback mặc định khi orientation chưa có preset.
- **Smoke test thật** trên device khoá — bắt đầu trên TECNO BG6 (`118743744X002560`), nhưng máy rớt kết nối USB giữa chừng; theo R3 KHÔNG tự chuyển thiết bị, dùng `AskUserQuestion` xin phép user và được xác nhận chuyển sang TECNO KJ7 (`115333744A005844`, máy còn lại đang kết nối) — hoàn tất toàn bộ smoke test trên KJ7. Máy cũng tự khoá màn hình 1 lần giữa chừng do dừng thao tác lâu để đọc code — dùng `AskUserQuestion` xin user mở khoá lại rồi tiếp tục, không có thao tác ADB nào cố bypass lock screen.
  - Ảnh test: `feat23_portrait_test.jpg` (3120×4160, dọc thật) và `feat23_landscape_test.jpg` (4160×3120, ngang thật, tạo bằng `sips -r 90` xoay từ ảnh dọc, xác nhận không có cờ EXIF orientation nên không bị app xoay lại).
  - Mở ảnh dọc qua `ACTION_SEND` trực tiếp vào `content://media/.../1000011663`, chỉnh anchor = "Dưới phải" (BOTTOM_RIGHT).
  - Mở ảnh ngang (`.../1000011664`) — xác nhận qua `uiautomator dump` (content-desc nút anchor): **"Ở giữa, đã chọn"** — mặc định CENTER, KHÔNG bị áp nhầm "Dưới phải" của ảnh dọc (**AC1**).
  - Quay lại ảnh dọc — xác nhận: **"Dưới phải, đã chọn"** — đúng vị trí đã chỉnh trước đó, không bị landscape vừa xem ghi đè (**AC2**).
  - `logcat` sạch `FATAL EXCEPTION`/`AndroidRuntime` suốt luồng.
- **Phát sinh ngoài ticket (theo yêu cầu user giữa lúc smoke test)**: phát hiện 2 lỗi UI ở tab "Kiểu dáng" — (1) thanh "Lặp lại/Đơn lẻ/Vị trí" lệch trái bất thường vì nút "Vị trí" `visibility=gone` khi chưa ở chế độ Đơn lẻ; (2) thanh icon chức năng (`rvPanel`) có khoảng trắng rất lớn 2 bên do `TouchSensitiveRv.onMeasure()` luôn set đệm `(bề rộng khả dụng − bề rộng 1 item)/2` bất kể nội dung có cần cuộn hay không (thiết kế carousel-1-item-giữa-màn-hình dùng chung với `rvPhotoList`, không hợp cho hàng icon nhiều item). User chọn qua `AskUserQuestion`: (1) luôn hiện nút Vị trí, làm mờ (`alpha=0.38f`) + `isEnabled=false` khi chưa dùng được, thay vì ẩn hẳn; (2) thêm cờ `TouchSensitiveRv.enableCenterPadding` (mặc định `true`, giữ nguyên `rvPhotoList`), tắt riêng cho `rvPanel` để tự co khít theo icon thật. Sửa xong phát hiện **hồi quy thật**: bỏ đệm carousel khiến cơ chế "chạm-để-chọn" cũ (yêu cầu item phải cuộn về ĐÚNG tâm màn hình mới được `handleFuncItem`) không còn hoạt động với item không đủ khoảng cuộn để tới tâm — sửa `MainActivity.kt`: `onItemClick` giờ chọn NGAY khi chạm, không gate theo `snapHelper.findSnapView() == v` nữa. Cập nhật lại `TileModeFragmentIntegrationRoboTest` (assert `View.VISIBLE` + `isEnabled` thay vì `View.GONE`/`View.VISIBLE`). Cả 2 fix đã verify lại trên device thật (KJ7) sau khi sửa hồi quy, tap 1 chạm hoạt động đúng, không còn khoảng trắng thừa.
- `./gradlew testDebugUnitTest` toàn bộ PASS (143 file unit test, +1 file mới). `./gradlew ktlintCheck` sạch. Lần chạy đầu gặp `MainViewModelUndoRedoRoboTest` fail — chạy lại pass ngay, đúng dạng flaky hạ tầng Robolectric/JVM-fork đã ghi nhận nhiều lần trước (không liên quan thay đổi ticket này).
- Audit: 9/10 — đủ 2 AC verify bằng smoke test thật (không chỉ unit test), bug rò rỉ vị trí giữa 2 orientation bắt được TRƯỚC khi lên device nhờ test, thiết kế atomic-write tránh race giữa active-value và preset. Trừ điểm vì phải sửa thêm 1 hồi quy tự gây ra khi tiện tay fix 2 bug UI không thuộc scope ticket gốc (dù đã fix đúng và verify lại đầy đủ) — bài học: đổi hành vi 1 component dùng chung (`TouchSensitiveRv`) cho nhiều nơi (`rvPanel` + `rvPhotoList`) cần rà lại MỌI nơi dùng chung, không chỉ nơi có bug được báo.
