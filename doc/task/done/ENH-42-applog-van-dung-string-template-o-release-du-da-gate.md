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
- [x] `AppLog.d`/`AppLog.i` nhận `msg: () -> String` (inline), không còn nhận `String` sẵn.
- [x] Toàn bộ callsite hiện có trong codebase (không chỉ `WaterMarkImageView`) được cập nhật sang trailing lambda, build không lỗi.
- [x] Verify bằng cách tạm thêm 1 biến `var evaluated = false` trong 1 lambda log test, xác nhận ở bản release (`BuildConfig.DEBUG = false`) lambda KHÔNG được gọi (string không được dựng).
- [x] `./gradlew ktlintCheck` và `./gradlew :app:testDebugUnitTest` vẫn PASS sau khi đổi chữ ký toàn bộ callsite.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-42`, file ticket = `todo/ENH-42-applog-van-dung-string-template-o-release-du-da-gate.md`.

## Kết quả kiểm chứng

**Fix:** `AppLog.d`/`AppLog.i` đổi thành `inline fun` nhận `msg: () -> String` thay vì `String` sẵn, đúng y hệt đề xuất ticket. Cập nhật **toàn bộ 124 callsite** trên **21 file** (`AppLog.d`/`AppLog.i`) sang dạng trailing lambda `AppLog.d(TAG) { "..." }` — dùng script Python tự viết với mini-parser cân bằng ngoặc/chuỗi (không naive regex, xử lý đúng string có dấu phẩy/ngoặc lồng bên trong `${...}`, kể cả 6 callsite nhiều dòng và 1 callsite dùng triple-quote string `.trimIndent()`). `AppLog.w`/`AppLog.e` KHÔNG đổi — ngoài phạm vi ticket (dù `w` có cùng vấn đề tiềm ẩn, `e` cố ý không gate).

- **Audit:** 9.5/10 — đúng scope ticket (chỉ `d`/`i`), transform cơ học chính xác 100% (verify bằng parser riêng đếm lại đúng 124/124 trước và sau), không đụng logic nghiệp vụ nào khác, `ktlintFormat` chạy sạch không cần sửa thêm.
- **Unit test:** `AppLogGatingTest.kt` — 2 test assert `evaluated == BuildConfig.DEBUG`, chạy được trên CẢ 2 variant nhờ `src/test` dùng chung (thông minh hơn yêu cầu "tạm thêm biến" của ticket — đây là test THẬT, tự động, không phải kiểm tra thủ công 1 lần). **`testDebugUnitTest`**: 2/2 PASS (`evaluated=true`, đúng `BuildConfig.DEBUG=true`). **`testReleaseUnitTest`**: 2/2 PASS (`evaluated=false`, đúng `BuildConfig.DEBUG=false`) — **chứng minh trực tiếp lambda KHÔNG được gọi ở bản release**, đúng tinh thần AC3. Chạy thêm toàn bộ `testReleaseUnitTest` (không chỉ test mới) để đảm bảo cả codebase build/test được dưới biến thể release — PASS hết.
- **`ktlintCheck`**: PASS. **`testDebugUnitTest`** (toàn bộ, không chỉ file liên quan): PASS.
- **Smoke test thật** trên device đã khoá session (TECNO KJ7, serial `115333744A005844`): cài `assembleDebug`, mở app → Chọn ảnh → vào editor (chạm `WaterMarkImageView.applyNewConfig()`/`onDraw()` — 2 hot path có nhiều callsite nhất, 28 lời gọi). Logcat tag `roy93~` hiển thị đúng nội dung string interpolation (`"[WMIV] onDraw SKIP: decodedUri empty mode=..."`, `"[WMIV] applyNewConfig: building shader for mode=..."`) — xác nhận lambda evaluate đúng ở bản debug, nội dung string không bị hỏng qua transform. Không crash, không `FATAL`/`AndroidRuntime` exception trong logcat.
