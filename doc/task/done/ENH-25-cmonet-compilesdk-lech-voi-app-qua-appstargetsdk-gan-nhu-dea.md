---
id: ENH-25
type: Enhancement
effort: S
sources: Claude
files:
  - cmonet/build.gradle.kts
  - buildSrc/src/main/kotlin/Dependencies.kt
  - app/build.gradle.kts
---

# `cmonet` compileSdk lệch với `:app` qua `Apps.targetSdk` gần như dead object

## Mô tả
`cmonet` dùng `compileSdk = Apps.targetSdk` (=34), trong khi `:app` hardcode `compileSdk = 37` trực tiếp. `Apps` object (`buildSrc`) chỉ còn field `targetSdk` sống (phần còn lại đã comment out) — tên field gây hiểu lầm (`targetSdk` dùng để set `compileSdk`), và 2 module compile ở API level khác nhau có thể gây cảnh báo/incompatibility không rõ ràng khi build.

## Triển khai
Đồng bộ `compileSdk` 2 module (cùng 37), hoặc xoá hẳn `Apps` object nếu không còn ý nghĩa thực tế (không ai maintain field `targetSdk` riêng cho mục đích khác nữa).

## Acceptance Criteria
- [x] Build cả 2 module (`:app`, `:cmonet`) cùng `compileSdk` — không warning API level mismatch.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-25`, file ticket = `todo/ENH-25-cmonet-compilesdk-lech-voi-app-qua-appstargetsdk-gan-nhu-dea.md`.

## Kết quả kiểm chứng
- Phát hiện `cmonet/build.gradle.kts` đã hardcode `compileSdk = 37` từ trước (không còn đọc `Apps.targetSdk`) — 2 module đã đồng bộ sẵn. Chọn nhánh 2 của "Triển khai": xoá hẳn `buildSrc/src/main/kotlin/Dependencies.kt` (chỉ còn `object Apps { targetSdk = 34 (dead, không ai reference) }`) — `buildSrc/` giờ không còn file Kotlin nguồn nào.
- Audit: 9.5/10 — thay đổi build-config thuần, không logic runtime, diff tối thiểu đúng scope ticket.
- Test: không áp dụng unit/widget/integration (không có logic để test) — xác nhận bằng `./gradlew :app:compileAppReleaseDebugKotlin :cmonet:compileDebugKotlin -q` build sạch không lỗi/warning liên quan `Apps`.
- Smoke test: `installAppReleaseDebug` lên device khoá `115333744A005844` (TECNO KJ7), `am start -n com.mckimquyen.watermark/.ui.SplashActivity` — app mở bình thường, logcat không có `FATAL EXCEPTION`/`AndroidRuntime` crash.
- Cập nhật thêm `CLAUDE.md` mục "Cấu hình build & dependency" cho khớp thực tế (buildSrc không còn Apps/Dependencies.kt).
