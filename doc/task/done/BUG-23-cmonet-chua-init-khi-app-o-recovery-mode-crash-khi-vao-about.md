---
id: BUG-23
priority: P1
type: Bug
effort: XS
sources: Claude fork nội bộ
files:
  - app/src/main/java/com/mckimquyen/watermark/MyApplication.kt
  - cmonet/src/main/java/com/mckimquyen/cmonet/CMonet.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
---

# CMonet chưa init khi app ở Recovery Mode, crash khi vào About

## Mô tả
`MyApplication.onCreate()` chỉ gọi `CMonet.init(this, true)` ở nhánh `else` của `checkRecoveryMode()` — khi app đang ở recovery mode (2 crash liên tiếp cùng version), `CMonet.init()` bị skip hẳn, để `CMonet.monetManufacturer`/`application` (`lateinit var`) chưa gán. `AboutActivity` gọi thẳng `CMonet.isDynamicColorAvailable()` không qua try/catch — nếu user mở Information/About đúng lúc app đang recovery mode (đúng lúc cần xem app đang lỗi gì), crash `UninitializedPropertyAccessException`, vô hiệu hoá luôn cơ chế graceful-recovery.

## Triển khai
Guard `CMonet.isDynamicColorAvailable()`/mọi entrypoint gọi `CMonet` khi chưa `init()` — trả false/no-op an toàn thay vì crash, hoặc gọi `CMonet.init()` sớm hơn (trước nhánh recovery-mode check) nếu không có side-effect nguy hiểm.

## Acceptance Criteria
- [x] App ở recovery mode (giả lập 2 crash liên tiếp), mở About — không crash `UninitializedPropertyAccessException`.
- [x] Hành vi bình thường (không recovery mode) không đổi — Dynamic Color vẫn hoạt động đúng như cũ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-23`, file ticket = `todo/BUG-23-cmonet-chua-init-khi-app-o-recovery-mode-crash-khi-vao-about.md`.

## Kết quả kiểm chứng (2026-09-16)

**Điểm audit: 9.5/10**

- **Phạm vi bug LỚN hơn mô tả ban đầu**: `CMonet.isDynamicColorAvailable()` không chỉ gọi ở `AboutActivity` — dùng ~30 điểm trong `ContextExtension.kt` (mọi getter màu theme `colorPrimary`/`colorSecondary`/...), tức MỌI màn hình đọc màu theme đều có nguy cơ crash khi recovery mode, không riêng About. Root cause fix (di chuyển `CMonet.init(this, true)` ra khỏi nhánh `else` của `checkRecoveryMode()`, gọi vô điều kiện ngay đầu `onCreate()`) bảo vệ toàn bộ các điểm gọi cùng lúc, không cần guard từng chỗ riêng lẻ (đúng 1 trong 2 hướng ticket gốc đề xuất).
- **Phát hiện phụ qua smoke test** (ngoài AC gốc): app có màn hình "Recovery Mode" riêng (`a_recovery.xml`, hiện qua `MainActivity` khi `MyApplication.recoveryMode == true`) — layout này KHÔNG xử lý edge-to-edge insets (chỉ margin cứng 48dp/32dp), khác hẳn luồng `launchView` bình thường ngay cạnh đã có `ViewCompat.setOnApplyWindowInsetsListener`. Đúng lúc app đang crash-loop, màn hình recovery càng cần chắc chắn hiển thị đủ — đã fix thêm insets listener cho `rootRecovery`, verify bằng ảnh chụp màn hình thật trước/sau (nút "Turn off recovery mode" từ sát mép dưới/nav bar chuyển thành cách hẳn 1 khoảng an toàn).
- **Giới hạn test tự động (đã ghi rõ, không giả vờ đã test được)**: không mô phỏng được toàn bộ `Application` lifecycle ở recovery mode qua Robolectric trong dự án này — `MyApplication` là `@HiltAndroidApp`, `ApplicationProvider.getApplicationContext()` đã tự chạy `onCreate()` trước khi test method bắt đầu (không "pre-seed" SharedPreferences trước onCreate được), và Robolectric 4.14.1 ở đây không có API `buildApplication`/`ApplicationController` để dựng lại instance riêng có kiểm soát. Verify bằng smoke test thật thay vì unit test cho đúng kịch bản này.
- **Smoke test thật trên TECNO KJ7 (115333744A005844, đã khoá đầu phiên)**: cài APK debug, giả lập recovery mode thật (ghi trực tiếp `shared_prefs/sp_water_mark_crash_info.xml` qua `adb run-as` với `crash_count=2`, `recovery_version=20260905` khớp `versionCode` build) — launch app, xác nhận app tự vào màn Recovery Mode, KHÔNG crash (`adb logcat *:E` sạch, không `FATAL EXCEPTION`/`UninitializedPropertyAccessException`), UI render đúng màu Material You (nút "Copy error message" xanh, "Turn off recovery mode" đỏ — chứng minh `CMonet` đã init thành công). Lặp lại lần 2 sau fix edge-to-edge, so sánh ảnh chụp màn hình xác nhận padding đúng.
- Full suite: 271/271 unit test PASS, `ktlintCheck` sạch.
- Commit: (xem commit theo sau file này khi move `done/`).
