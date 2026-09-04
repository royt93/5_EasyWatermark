---
id: ENH-03
type: Enhancement
effort: S
sources: Claude, Internal, Agy (3/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainActivity.kt
---

# Gate toàn bộ `Log.d` bằng `BuildConfig.DEBUG`

## Mô tả
Hàng trăm `Log.d(LOG_TAG, "[WMIV] ...")`, `[VM]`, `[MAIN]` chạy vô điều kiện xuyên suốt `WaterMarkImageView` (kể cả trong `onDraw`, gọi rất thường xuyên khi kéo/pinch), `MainViewModel`, `MainActivity` — không gate build. Hậu quả: tốn CPU dựng string template + áp lực GC không cần thiết trong bản release, đồng thời log lộ đường dẫn URI ảnh người dùng ra Logcat production (privacy).

## Đề xuất
Tạo wrapper `AppLog.d(...)` (hoặc dùng Timber) no-op ở release build (`if (BuildConfig.DEBUG) Log.d(...)`), thay thế toàn bộ lời gọi `Log.d` hiện có bằng wrapper này.

## Acceptance Criteria
- [ ] Build release không còn log debug xuất hiện trong Logcat.
- [ ] Build debug vẫn giữ nguyên log để dev dùng.
