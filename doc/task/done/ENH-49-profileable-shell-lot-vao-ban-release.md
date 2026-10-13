---
id: ENH-49
type: Enhancement
priority: P2
effort: XS
sources: R5 security re-audit 2026-10-14 (verify tay: manifest + git log)
files:
  - app/src/main/AndroidManifest.xml
  - app/src/debug/AndroidManifest.xml
---

# ENH-49: `<profileable android:shell="true">` nằm ở manifest chính nên lọt vào APK release

**Priority:** P2
**Effort:** XS (<2h)
**Status:** done

## Vấn đề

`app/src/main/AndroidManifest.xml:62-64` khai báo `<profileable android:shell="true">`. Thẻ này được thêm cho macro benchmark (commit `3c67af5e`), nhưng 2 module `baseBenchmarks`/`macrobenchmark` đã bị comment trong `settings.gradle.kts` — không còn dùng. Vì nằm ở `main`, bản release cũng cho phép shell/ADB profile tiến trình (`simpleperf`, heap) dù `debuggable=false`.

Tác động thấp (cần quyền ADB), nhưng là bề mặt thừa không có lý do tồn tại ở release.

## Đề xuất

Chuyển `<profileable>` sang `app/src/debug/AndroidManifest.xml` (đã có sẵn, cùng pattern với `VipTokenSmokeActivity`), xoá khỏi `main`. Khi bật lại macrobenchmark thì dùng build type `benchmark` riêng.

## Test

- Unit: đọc merged manifest release (`app/build/intermediates/merged_manifests/release*/`) sau `processReleaseManifest` và assert không còn `profileable`; merged debug vẫn còn.
- Smoke: `adb shell dumpsys package com.mckimquyen.watermark | grep -i profile` trên bản release không báo profileable. Nếu thiếu keystore release thì ghi rõ giới hạn.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-14)

- **Fix**: chuyển `<profileable android:shell="true">` từ `app/src/main/AndroidManifest.xml` sang `app/src/debug/AndroidManifest.xml` (thêm `xmlns:tools` cho `tools:targetApi`).
- **Merged manifest**: `processDebugManifest` còn `profileable`; `processReleaseManifest` KHÔNG còn (kiểm lại sau `--rerun-tasks`).
- **Test**: `ManifestVariantConfigurationRoboTest` (Robolectric, debug variant) khoá `isProfileableByShell == BuildConfig.DEBUG`. Giới hạn: unit test chỉ chạy variant debug; `testReleaseUnitTest` không biên dịch được vì `VipTokenSmokeActivityRoboTest` (debug-only) nằm trong source set chung — lỗi có sẵn, ngoài phạm vi ticket. Bằng chứng cho release là merged manifest ở trên.
- **Build**: `assembleRelease` và `lint` pass. Release ký bằng secret trong myKeyStore (không đưa vào repo).
- **Tự audit**: 9.0/10 — diff 2 file manifest; trừ điểm vì test release variant không chạy được tự động.
