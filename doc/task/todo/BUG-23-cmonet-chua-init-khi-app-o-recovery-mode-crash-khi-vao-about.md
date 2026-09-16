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
- [ ] App ở recovery mode (giả lập 2 crash liên tiếp), mở About — không crash `UninitializedPropertyAccessException`.
- [ ] Hành vi bình thường (không recovery mode) không đổi — Dynamic Color vẫn hoạt động đúng như cũ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-23`, file ticket = `todo/BUG-23-cmonet-chua-init-khi-app-o-recovery-mode-crash-khi-vao-about.md`.
