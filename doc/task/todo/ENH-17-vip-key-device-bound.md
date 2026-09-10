---
id: ENH-17
type: Enhancement
priority: P1
effort: S
sources: Claude-fork re-audit 2026-09-10
files:
  - app/src/main/java/com/mckimquyen/watermark/feature/vip/VipManagementActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/feature/vip/VipPrefs.kt
---

# VIP key gắn thiết bị (device-bound) — giảm thiệt hại nếu secret bị lộ

## Mô tả
Kể cả sau khi fix [BUG-14](BUG-14-vip-secret-hardcode-trong-apk.md), 1 key redeem hợp lệ vẫn dùng được trên vô số thiết bị nếu bị chia sẻ công khai (forum, group chia sẻ key). Ràng buộc thêm theo thiết bị giảm thiệt hại lan rộng mà không cần backend.

## Đề xuất
Khi redeem thành công, hash `Settings.Secure.ANDROID_ID` (hoặc định danh ổn định khác, tránh PII thật) trộn vào giá trị lưu trong `VipPrefs`. Lần mở app sau chỉ công nhận VIP nếu hash khớp thiết bị hiện tại — hạn chế 1 key redeem lại được trên máy khác dù secret có lộ.

## Acceptance Criteria
- [ ] Redeem key trên thiết bị A, chuyển `VipPrefs` (backup/copy thủ công) sang thiết bị B → VIP không được công nhận trên thiết bị B.
- [ ] Redeem lại đúng key trên thiết bị A vẫn hoạt động bình thường (không tự khoá nhầm chủ sở hữu hợp lệ).
- [ ] Không lưu định danh thiết bị dạng plain-text dễ đọc trong DataStore/SharedPreferences.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-17`, file ticket = `todo/ENH-17-vip-key-device-bound.md`.
