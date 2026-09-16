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
- [ ] Build cả 2 module (`:app`, `:cmonet`) cùng `compileSdk` — không warning API level mismatch.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-25`, file ticket = `todo/ENH-25-cmonet-compilesdk-lech-voi-app-qua-appstargetsdk-gan-nhu-dea.md`.
