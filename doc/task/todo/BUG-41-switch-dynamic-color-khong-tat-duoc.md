---
id: BUG-41
type: Bug
priority: P2
effort: S
sources: Claude self-audit 2026-09-27 (verify cả 3 file: AboutActivity, CMonet, MyApplication)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/MyApplication.kt
  - cmonet/src/main/java/com/mckimquyen/cmonet/MonetManufacturer.kt
---

# Switch "Dynamic Color" ở About không tắt được — luôn hiện bật, luôn áp màu động

## Mô tả
3 mắt xích cộng lại làm công tắc hoàn toàn vô tác dụng trên mọi máy Android 12+:

1. `MonetManufacturer.isDynamicColorAvailable()` = `DynamicColors.isDynamicColorAvailable() ||
   isForceSupport` (dòng 21) — trên API 31+ luôn `true` bất kể cờ user.
2. `AboutActivity.kt:263`: `switchDynamicColor.isChecked = CMonet.isDynamicColorAvailable()` —
   đọc chính hàm trên ⇒ mở lại About sau khi tắt, switch lại hiện BẬT.
3. `AboutViewModel.toggleSupportDynamicColor(false)` chỉ gọi `CMonet.disableSupportDynamicColor()`
   (đặt `isForceSupport = false`), trong khi màu động thực sự được bật bởi
   `MyApplication.onCreate()` dòng 67 `DynamicColors.applyToActivitiesIfAvailable(this)` — gọi VÔ
   ĐIỀU KIỆN, không hỏi CMonet. Kể cả `ProcessPhoenix.triggerRebirth` restart xong, màu động vẫn áp.

Kết quả: user tắt → app khởi động lại → giao diện y nguyên màu động, switch lại tick. Toast "cần
khởi động lại để đổi theme" thành lời hứa sai.

**Phát hiện kèm (cùng gốc, sửa chung 1 lần)**: `applyToActivitiesIfAvailable` được gọi **2 lần** —
`MyApplication.kt:67` và bên trong `CMonet.init(this, true)` (dòng 73) → đăng ký
`ActivityLifecycleCallbacks` trùng lặp, dư thừa.

## Cách fix đề xuất
Tách rõ 2 khái niệm đang bị trộn: "thiết bị CÓ HỖ TRỢ" (`DynamicColors.isDynamicColorAvailable()`)
và "user MUỐN bật" (cờ lưu `SimpleSp`, mặc định true).
- `MonetManufacturer`: thêm `isUserEnabled` riêng; `isDynamicColorAvailable()` chỉ trả năng lực
  thiết bị; thêm `shouldApplyDynamicColor() = deviceSupported && isUserEnabled`.
- `MyApplication`: bỏ lời gọi `DynamicColors.applyToActivitiesIfAvailable` trực tiếp (dòng 67), chỉ
  còn `CMonet.init(this, apply = ...)` quyết định theo `shouldApplyDynamicColor()` → hết cả 2 lần gọi trùng.
- `AboutActivity`: `isEnabled` theo năng lực thiết bị, `isChecked` theo cờ user.

## Acceptance Criteria
- [ ] Tắt switch → restart → UI dùng static M3 token (không màu ví Monet), switch hiện TẮT.
- [ ] Bật lại → restart → màu động trở lại, switch hiện BẬT.
- [ ] Máy API <31: switch vẫn `isEnabled = false` như hiện tại (không regression).
- [ ] `applyToActivitiesIfAvailable` chỉ còn đúng 1 đường gọi (verify bằng grep + test).
- [ ] Unit test `MonetManufacturer` cho 4 tổ hợp (device hỗ trợ/không × user bật/tắt).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-41`, file ticket = `todo/BUG-41-switch-dynamic-color-khong-tat-duoc.md`.
