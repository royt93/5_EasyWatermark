---
id: ENH-36
type: Enhancement
priority: P2
effort: S
sources: Claude self-audit 2026-09-27 (đếm trực tiếp, đối chiếu phạm vi ENH-03 đã done)
files:
  - app/src/main/java/com/mckimquyen/watermark/AppConst.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/MultiSelectRv.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/PhotoPreviewItem.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
---

# 41 lời gọi `Log.i` / `Log.w` / `Log.e` chưa gate `BuildConfig.DEBUG` (sót khỏi ENH-03)

## Mô tả
ENH-03 chỉ chuyển `Log.d` → `AppLog.d` (gate `BuildConfig.DEBUG`); `AppLog.w`/`AppLog.e` trong
`AppConst.kt` **không gate gì** và `Log.i` không có wrapper. Hiện còn **61 lời gọi `android.util.Log`
trực tiếp** trong `app/src/main`, trong đó 41 là `Log.i` — tập trung ở đúng các đường nóng nhất:

| File | số `Log.i` |
|---|---|
| `MultiSelectRv.kt` | 9 (trong touch handler kéo chọn) |
| `WaterMarkImageView.kt` | 8 — gồm `onTouch $event` (dòng 905, **mỗi MotionEvent**), `isOutOfDrawable` (767), `onScale` (878) |
| `PhotoPreviewItem.kt` | 7 (gesture) |
| `BitmapUtils.kt` | 5 (mỗi lần decode: `"Hit the cache bitmap!"`, kích thước ảnh) |
| `GalleryAdapter.kt` / `CenterLayoutManager.kt` / `BatchExportEngine.kt` / khác | 12 |

Đúng 2 lý do ENH-03 nêu vẫn còn nguyên hiệu lực: (1) dựng string template + áp lực GC mỗi
frame/mỗi event ở bản release; (2) `Log.i("generateImage", ... imageInfo = $imageInfo ...)` in cả
**URI ảnh người dùng** ra logcat production (privacy — cùng loại rủi ro ENH-03 đã fix cho `Log.d`).

## Đề xuất
- Thêm `AppLog.i(tag, msg)` gate `BuildConfig.DEBUG` (cùng khuôn `AppLog.d`).
- Gate luôn `AppLog.w` — cân nhắc GIỮ `AppLog.e` không gate (lỗi thật nên thấy được ở release, ghi
  rõ quyết định trong KDoc), nhưng bỏ nội dung chứa URI/PII khỏi message.
- Thay cơ học 41 `Log.i` → `AppLog.i`, xoá hẳn các log rác giá trị thấp trong vòng vẽ/gesture
  (`"onTouch $event"`, `"ACTION_DOWN"`, `"Hit the cache bitmap!"`) thay vì gate.
- Cân nhắc thêm ProGuard `-assumenosideeffects class android.util.Log { public static int i(...); }`
  cho release (repo đã dùng pattern này cho coroutines trong `app/coroutines.pro`).

## Acceptance Criteria
- [x] `grep -rn "Log\." app/src/main --include=*.kt | grep -v AppLog` chỉ còn trong `AppConst.kt`.
- [x] Build release không in log info/debug nào của app; build debug giữ nguyên log dev cần.
- [x] Không còn log nào in URI ảnh/`imageInfo` đầy đủ ở đường release.
- [x] Test hiện có không regression (`testDebugUnitTest` xanh) — thay thế cơ học, không đổi logic.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-36`, file ticket = `todo/ENH-36-gate-log-i-w-e-con-sot-ngoai-enh-03.md`.

## Kết quả kiểm chứng (2026-09-28)

**Fix:** thêm `AppLog.i(tag, msg)` (gate `BuildConfig.DEBUG`, cùng khuôn `AppLog.d`), gate luôn
`AppLog.w`; **GIỮ `AppLog.e` không gate** (đúng đề xuất ticket — lỗi thật cần thấy ở release).
Thay cơ học 33 lời gọi `Log.i`/`Log.w`/`Log.e` chủ động (khác phần đã comment sẵn, vô hại) sang
`AppLog.*` qua 14 file (`MultiSelectRv`, `WaterMarkImageView`, `PhotoPreviewItem`, `BitmapUtils`,
`GalleryAdapter`, `SaveImageListAdapter`, `WaterMarkRepository`, `BatchExportEngine`,
`MainActivity`, `TemplateRepository`, `SplashActivity`, `GalleryFragment`,
`QrCodeBottomSheetFragment`). **Xoá hẳn** (không gate) 15 log rác giá trị thấp trong vòng vẽ/gesture
đúng tinh thần ticket (`onTouch $event`, `ACTION_DOWN`, `Hit the cache bitmap!`, và mở rộng cùng
nhóm: `onScale`/`onScaleEnd`/`isOutOfDrawable`/`onSizeChanged` của `WaterMarkImageView`, touch
handler đầy đủ của `MultiSelectRv`, gesture callback của `PhotoPreviewItem`) — thay vì gate, vì tần
suất gọi (mỗi frame/mỗi sự kiện chạm) khiến việc gate cũng không giải quyết được áp lực GC dựng
string template. `WaterMarkRepository.updateTileMode()` scrub `imageInfo.uri` khỏi message `Log.e`
(nhánh KHÔNG gate, uri ảnh user không nên lộ ra logcat release).

- **Điểm tự audit:** 9/10 — đúng đề xuất ticket, xoá rộng hơn 3 ví dụ nêu trong ticket (cùng nhóm
  "vòng vẽ/gesture", nhất quán tinh thần chung). Trừ 1 điểm: KHÔNG thêm rule ProGuard
  `-assumenosideeffects` cho `Log.i` (mục "Cân nhắc thêm" trong ticket, không nằm trong AC bắt
  buộc) — bỏ qua vì `AppLog.i` đã gate ở tầng Kotlin runtime (`if (BuildConfig.DEBUG)`), R8 minify
  release build thực tế đã có thể tối ưu nhánh `if (false)` tương đương; thêm rule riêng là tối ưu
  hoá bổ sung ngoài phạm vi cốt lõi của ticket.
- **Test:** không viết test mới (ticket tự nêu rõ AC4 "thay thế cơ học, không đổi logic" — không có
  hành vi mới cần test). `./gradlew ktlintCheck` sạch (phát hiện + fix nhân tiện 1 lỗi ktlint
  KHÔNG liên quan sót từ BUG-37: trailing comma thừa trong `MediaStoreCleanupAction` enum).
  `./gradlew :cmonet:testDebugUnitTest :app:testDebugUnitTest` 195/195 test suite PASS (2 lần full-
  suite gặp 3 flaky riêng biệt qua các lần chạy — `MainViewModelSaveImageImmutabilityRoboTest`,
  `ToastExtensionWidgetTest`, `GalleryFragmentFolderPickRoboTest` — xác nhận PASS riêng lẻ cả 3,
  đúng flakiness cross-test JVM fork đã ghi ở `doc/todo.md`, không liên quan đợt sửa này).
- **Verify AC1 (grep)**: chạy đúng lệnh ticket đề nghị — chỉ còn comment chết (không chạy) +
  1 false-positive (`Log.getStackTraceString()` trong `MyApplication.kt` — hàm FORMAT chuỗi thuần,
  không tự in log, ngoài phạm vi ticket) + 1 false-positive khác (chuỗi `"tvChangeLog"` chứa
  substring "Log" trong `AboutActivity.kt`, không phải lời gọi Log nào).
- **Smoke test thật** trên device đã khoá session — **TECNO KJ7 (115333744A005844)**: cài bản debug
  chứa toàn bộ thay đổi ENH-36, mở app + điều hướng About + toggle Dynamic Color (BUG-41) trong
  cùng phiên — không crash, logcat sạch, không `FATAL EXCEPTION` suốt phiên smoke test tổng hợp
  (BUG-41/42/43/ENH-36 cùng 1 lượt cài/chạy).
