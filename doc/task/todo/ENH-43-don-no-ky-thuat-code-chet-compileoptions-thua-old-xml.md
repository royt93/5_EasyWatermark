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
- [ ] Mỗi file xoá: grep toàn repo (`app/src`, `cmonet`, proguard, XML, manifest) = 0 reference trước khi xoá.
- [ ] `./gradlew assembleDebug assembleRelease testDebugUnitTest ktlintCheck lint` xanh.
- [ ] Comment ở `ProgressImageView.kt:54` không còn dẫn tên class đã xoá (hoặc giữ nếu chỉ là lịch sử).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-43`, file ticket = `todo/ENH-43-don-no-ky-thuat-code-chet-compileoptions-thua-old-xml.md`.
