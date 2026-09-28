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
- [ ] `grep -rn "Log\." app/src/main --include=*.kt | grep -v AppLog` chỉ còn trong `AppConst.kt`.
- [ ] Build release không in log info/debug nào của app; build debug giữ nguyên log dev cần.
- [ ] Không còn log nào in URI ảnh/`imageInfo` đầy đủ ở đường release.
- [ ] Test hiện có không regression (`testDebugUnitTest` xanh) — thay thế cơ học, không đổi logic.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-36`, file ticket = `todo/ENH-36-gate-log-i-w-e-con-sot-ngoai-enh-03.md`.
