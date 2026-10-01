---
id: ENH-42
type: Enhancement
priority: P2
effort: S
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/AppConst.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt (callsite điển hình)
---

# `AppLog.d`/`AppLog.i` vẫn dựng string template ở bản release dù comment nói đã gate (ENH-03/36 chưa fix hết)

## Mô tả
`AppLog.d`/`AppLog.i` (`AppConst.kt` dòng 19-30) có doc comment tự ghi "tốn CPU dựng string template... gate giống `d`", ngụ ý đã chặn việc dựng chuỗi ở bản release. nhưng chữ ký hàm nhận tham số `msg: String` — nghĩa là Kotlin **dựng xong chuỗi interpolation TRƯỚC** khi gọi vào hàm, bất kể `if (BuildConfig.DEBUG)` bên trong hàm có gate hay không. `if (BuildConfig.DEBUG)` chỉ chặn được lệnh gọi `Log.d`/`Log.i` syscall thật, không chặn được việc build string ở call site.

Nhiều callsite string-heavy (interpolation phức tạp, nối nhiều biến) nằm trong hot path chạy mỗi frame — ví dụ `WaterMarkImageView.onDraw()`/`onScale()`/`applyNewConfig()` — vẫn tốn CPU dựng chuỗi ở bản **release** dù log không bao giờ thực sự in ra. Đây đúng là vấn đề mà chính comment của hàm nói là đã xử lý nhưng thực ra chưa.

## Đề xuất
Đổi chữ ký `AppLog.d`/`AppLog.i` sang nhận lambda thay vì `String` sẵn:
```kotlin
inline fun d(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) Log.d(tag, msg())
}
```
Nhờ `inline`, lambda không tạo object, và quan trọng nhất: thân lambda (bao gồm string interpolation bên trong) chỉ được evaluate khi `BuildConfig.DEBUG == true` — đúng ý đồ ban đầu. Cập nhật toàn bộ callsite sang dạng trailing lambda (`AppLog.d(TAG) { "..." }`).

## Acceptance Criteria
- [ ] `AppLog.d`/`AppLog.i` nhận `msg: () -> String` (inline), không còn nhận `String` sẵn.
- [ ] Toàn bộ callsite hiện có trong codebase (không chỉ `WaterMarkImageView`) được cập nhật sang trailing lambda, build không lỗi.
- [ ] Verify bằng cách tạm thêm 1 biến `var evaluated = false` trong 1 lambda log test, xác nhận ở bản release (`BuildConfig.DEBUG = false`) lambda KHÔNG được gọi (string không được dựng).
- [ ] `./gradlew ktlintCheck` và `./gradlew :app:testDebugUnitTest` vẫn PASS sau khi đổi chữ ký toàn bộ callsite.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-42`, file ticket = `todo/ENH-42-applog-van-dung-string-template-o-release-du-da-gate.md`.
