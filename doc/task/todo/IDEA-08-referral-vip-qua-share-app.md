---
id: IDEA-08
type: Idea
priority: P2
effort: M
sources: Claude-fork re-audit 2026-09-10
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
  - app/src/main/java/com/mckimquyen/watermark/feature/vip/
---

# Referral VIP — mời bạn cài app, nhận thưởng ngày VIP

## Mô tả
Tận dụng luôn hạ tầng Share App vừa làm xong (`doc/feat.md` mục 8) + package `feature/vip/` đã có sẵn cơ chế cấp VIP theo ngày: user chia sẻ app qua link riêng (deep-link hoặc mã giới thiệu) → khi người được mời cài đặt và mở app lần đầu, cả 2 bên nhận thưởng ngày VIP. Không cần dựng backend riêng nếu dùng **Play Install Referrer API** (Google Play cung cấp sẵn, đọc được referrer string lúc cài từ link Play Store) — chỉ cần sinh referrer code gắn với user hiện tại và validate offline.

## Vì sao đáng làm
- Network effect tự nhiên: mỗi lượt chia sẻ có động lực rõ ràng (nhận VIP) thay vì chỉ "share vì thích app".
- Tái dùng gần 100% hạ tầng sẵn có: Share intent (`AboutActivity`), cấp VIP theo ngày (`feature/vip/`), chỉ cần thêm Play Install Referrer API + sinh/verify mã giới thiệu.
- Không cần backend mới (khác IDEA-04/IDEA-05 vốn cần XL) — hợp với hiện trạng app không có dependency network nào.

## Acceptance Criteria
- [ ] Sinh được mã giới thiệu duy nhất gắn với thiết bị/người dùng hiện tại, nhúng vào link share.
- [ ] Người được mời cài qua link → app đọc đúng referrer code qua Install Referrer API lúc mở lần đầu.
- [ ] Cả người mời và người được mời đều nhận đúng số ngày VIP thưởng, không nhận trùng lặp (đã referred rồi thì không nhận lại).
- [ ] Có cơ chế chống lạm dụng cơ bản (self-referral trên cùng thiết bị bị chặn).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-08`, file ticket = `todo/IDEA-08-referral-vip-qua-share-app.md`.
