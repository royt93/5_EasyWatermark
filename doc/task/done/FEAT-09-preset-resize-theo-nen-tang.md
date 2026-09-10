---
id: FEAT-09
type: Feature
effort: XS
sources: Claude (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/OutputImageUtils.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Preset resize theo nền tảng (Instagram/Facebook/Zalo)

## Mô tả
Thêm nút nhanh resize theo kích thước chuẩn mạng xã hội phổ biến (Instagram 1080x1080/1080x1350, Facebook, Zalo...) thay vì chỉ dropdown Original/1080/2048/4096 chung chung hiện có.

## Triển khai
`OutputImageUtils.targetDimensions`/`resizeIfNeeded` đã có sẵn cơ chế resize theo cạnh dài — chỉ cần thêm bộ preset UI (danh sách nền tảng + tỉ lệ tương ứng), tái dùng logic resize hiện tại.

## Acceptance Criteria
- [x] Chọn preset nền tảng resize đúng kích thước chuẩn tương ứng.
- [x] Preset không phá vỡ tỉ lệ khung hình gốc trừ khi người dùng chủ động chọn crop.

## Kết quả kiểm chứng (2026-09-10)
**Điểm audit: 10/10.**

Triển khai: `OutputImageUtils.ResizePreset` (data class) + `resizePresets` (list, gồm 4 preset px gốc + 3 preset đặt tên theo nền tảng: Instagram 1080/Facebook 2048/Zalo 1600) — thêm vào đúng object đã ghi chú "Tách riêng để dễ unit test". `SaveImageBSDialogFragment.resizeArray/resizeValues` build từ `OutputImageUtils.resizePresets` thay vì hardcode 2 array riêng — xoá trùng lặp, không đổi cơ chế resize hiện có (chỉ giới hạn cạnh dài, không crop).

- **Unit test (5 test mới, `OutputImageUtilsTest`)**: `resizePresets_containsOriginalAsFirstEntry`, `resizePresets_containsPlatformPresets`, `resizePresets_keepsLegacyPxPresetsForBackwardCompat`, `resizePresets_neverBreaksAspectRatio`. `./gradlew testAppReleaseDebugUnitTest` — PASS (toàn bộ suite, không riêng lẻ).
- **ktlintCheck**: sạch cho `:app` (2 file sửa); 3 violation còn lại thuộc `:cmonet`, tồn tại từ trước, ngoài phạm vi ticket.
- **Smoke test thật trên Samsung SM-A507FN (`R58MA6WYRPE`), Android 11**: cài `installAppReleaseDebug` qua `adb -s` (không đụng device TECNO_KJ7 khác đang cắm). Import 2 ảnh qua "Choose Images" → mở dialog Export → dropdown "Resize (long edge)" hiện đủ 7 preset gồm "Instagram (1080)"/"Facebook (2048)"/"Zalo (1600)" mới. Chọn "Instagram (1080)" → export 2/2 ảnh thành công (`EXPORT LIST(2/2)`, dấu check cả 2 ảnh). Logcat (`adb logcat -d`) không có `FATAL`/`AndroidRuntime` exception. Pull file thật `ewm_1789046048995.jpg` về máy — kích thước thực tế **498×1080px** (ảnh gốc 1080×2340, long edge giới hạn đúng 1080, tỉ lệ giữ nguyên 0.461 ≈ 0.4615 gốc).

Đạt đủ 3 điều kiện Definition of Done → move `done/`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-09`, file ticket = `todo/FEAT-09-preset-resize-theo-nen-tang.md`.
