---
id: ENH-43
type: Enhancement
priority: P2
effort: S
sources: re-audit 2026-10-04 (agent nợ kỹ thuật) + verify tay
files:
  - app/build.gradle.kts
  - old.xml
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/ColoredImageVIew.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/DetectedPerformanceSeekBarListener.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/base/BaseBindingActivity.kt
---

# Dọn nợ kỹ thuật: compileOptions VERSION_11 thừa, `old.xml`, 3 class chết

## Mô tả (đã verify)
- `app/build.gradle.kts:83-86` `compileOptions` VERSION_11 bị khối VERSION_17 (~dòng 103) ghi đè → xoá khối đầu.
- `old.xml` ở gốc repo (git track, 0 reference) → xoá.
- `ColoredImageVIew.kt`: chỉ còn 1 comment tham chiếu trong `ProgressImageView.kt:54` + test riêng → xoá class + `ColoredImageVIewWidgetTest`.
- `DetectedPerformanceSeekBarListener.kt`: 0 reference production (đã ghi "chưa dùng" ở `doc/todo.md`) → xoá + `DetectedPerformanceSeekBarListenerRoboTest`.
- `BaseBindingActivity.kt`: 0 reference → xoá.
- **KHÔNG xoá** `ItemClickSupport` — `BaseViewHolder` implement `ItemClickSupportViewHolder`, 6 adapter dùng.
- Kiểm `proguard-rules.pro`/`coroutines.pro` không nhắc các tên bị xoá.

## Acceptance Criteria
- [x] Mỗi file xoá: grep toàn repo (`app/src`, `cmonet`, proguard, XML, manifest) = 0 reference trước khi xoá.
- [x] `./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck lint` xanh.
- [x] Comment ở `ProgressImageView.kt:54` không còn dẫn tên class đã xoá (hoặc giữ nếu chỉ là lịch sử).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-43`, file ticket = `todo/ENH-43-don-no-ky-thuat-code-chet-compileoptions-thua-old-xml.md`.

## Kết quả kiểm chứng

**Đã xoá:** `ColoredImageVIew.kt` + `ColoredImageVIewWidgetTest.kt`, `DetectedPerformanceSeekBarListener.kt` + `DetectedPerformanceSeekBarListenerRoboTest.kt`, `BaseBindingActivity.kt`, `old.xml` (gốc repo), khối `compileOptions` VERSION_11 trong `app/build.gradle.kts` (khối VERSION_17 phía dưới giữ nguyên, hiệu lực thật không đổi). Giữ `ItemClickSupport` (`BaseViewHolder` dùng, 6 adapter phụ thuộc).

- **Audit:** 9.5/10 — grep lại trước khi xoá: 0 tham chiếu production/XML/manifest/proguard ngoài chính file và test riêng của nó; chỉ còn 1 comment lịch sử ở `ProgressImageView.kt:54` (giữ vì đúng ngữ cảnh BUG-42).
- **Test:** xoá 2 file test của class đã xoá (không còn đối tượng để test). `./gradlew testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest ktlintCheck lint` → BUILD SUCCESSFUL (exit 0), release minify + ký vẫn qua.
- **Smoke test:** không áp dụng — chỉ xoá code không có đường gọi, không đổi hành vi runtime; build release thành công là bằng chứng không có tham chiếu bị bỏ sót. Pixel đã khoá hiện không kết nối.
- **Chưa làm (ngoài scope ticket này):** `baseline-prof.txt` (package cũ `me/rosuh`) — chưa kết luận được có nạp hay không, cần ticket riêng nếu muốn xử lý.
