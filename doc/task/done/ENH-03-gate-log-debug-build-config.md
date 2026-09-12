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
- [x] Build release không còn log debug xuất hiện trong Logcat.
- [x] Build debug vẫn giữ nguyên log để dev dùng.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-03`, file ticket = `todo/ENH-03-gate-log-debug-build-config.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** Thêm `object AppLog { fun d(tag, msg) { if (BuildConfig.DEBUG) Log.d(tag, msg) } }` trong `AppConst.kt` (cùng chỗ `LOG_TAG`). Thay thế cơ học toàn bộ 100 lời gọi `Log.d(` → `AppLog.d(` trên 13 file (`MyApplication`, `BaseActivity`, `MainViewModel`, `MainActivity`, `SplashActivity`, `QrCodeBottomSheetFragment`, `SignatureActivity`, `SaveImageBSDialogFragment`, `GalleryFragment`, `WaterMarkImageView`, `OpenSourceActivity`, `LaunchView`, `AboutActivity`), giữ nguyên `Log.e/w/i` (ngoài scope ticket — vẫn cần hiện trong release để chẩn đoán crash thật). Xoá `import android.util.Log` ở 6 file không còn dùng Log.e/w/i nào khác sau khi thay.
- **Điểm tự audit:** 9.5/10 — scope đúng như ticket (chỉ `Log.d`), không có lời gọi `Log.d` nào sót lại (verify bằng grep loại trừ chính `AppLog.d`), compile sạch không cảnh báo unused-import.
- **Test:** Không cần unit test riêng cho logic if/else 1 dòng (ponytail: trivial one-liner). Verify bằng cách generate `BuildConfig.java` thật cho cả 2 biến thể: `appReleaseDebug` → `DEBUG = true`, `appReleaseRelease` (biến thể release thật, minify) → `DEBUG = false` — xác nhận đúng cơ chế gate hoạt động ở tầng build tool, không phải giả định.
- **Smoke test:** chạy bộ test liên quan các file bị đổi (`GalleryFragmentLifecycleRoboTest`, `MainViewModelRemoveImageRoboTest`, `MainViewModelGenerateOutputNameRoboTest`, `MainActivityInterstitialRoboTest`) — PASS, xác nhận thay thế cơ học không phá vỡ logic nào. Smoke tay: chạy app thật trên Samsung SM_A115F ở build debug, xác nhận log vẫn xuất hiện trong Logcat như trước (không có gì để verify ở phía "release không log" qua smoke tay vì variant debug luôn bật).
