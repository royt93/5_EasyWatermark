---
id: BUG-54
type: Bug
priority: P0
effort: M
sources: audit bảo mật 2026-10-07 (gom secret về myKeyStore) — git log + git grep xác nhận
files:
  - gradle.properties
  - app/keystore.jks
  - doc/AD.MD
  - doc/admob/admob.MD
---

# Keystore, mật khẩu ký và 2 mã VIP đã lộ vĩnh viễn trong git history công khai

## Mô tả
Repo `royt93/5_EasyWatermark` là PUBLIC. `app/keystore.jks` (alias `loi`) và `gradle.properties` chứa `STORE_PASSWORD`/`KEY_PASSWORD` plaintext đã commit từ 2024 (`e8e1cac4`, `d031385e` mới tách). Hai mã VIP legacy (30 ngày, 3 ngày), VIP secret và AppLovin SDK key cũng nằm trong source/doc. Commit `8e7aaecd` đã xoá khỏi HEAD và gom về `myKeyStore/com.mckimquyen.watermark/app.properties`, nhưng **history cũ vẫn công khai** — xoá khỏi HEAD không thu hồi được.

## Quyết định 2026-10-08 (xác nhận lần 2 bởi chủ dự án)
- Keystore + mật khẩu: **chấp nhận rủi ro, giữ nguyên**, không rotate.
- 2 mã VIP legacy (30/3 ngày): **giữ nguyên**, chấp nhận rủi ro ai cũng kích hoạt được VIP miễn phí.
- Ticket giữ trạng thái todo/deferred như bản ghi rủi ro, không làm tiếp trừ khi chủ dự án đổi ý.

Ghi chú ban đầu: chưa rotate, chưa viết lại history. Lý do: user đang giữ 2 mã VIP cũ; đổi VIP secret làm user hiện có mất ledger dedup; đổi keystore cần Play App Signing.

## Checklist rotate (làm khi sẵn sàng)
- [ ] Play Console: kiểm tra app có dùng Play App Signing không. Nếu có → xin reset upload key, tạo keystore mới, cập nhật `myKeyStore`. Nếu không → key này là app-signing key, KHÔNG thay được, chỉ thu hẹp bề mặt lộ.
- [ ] Đổi 2 mã VIP legacy (`VIP_LEGACY_30D_CODE`, `VIP_LEGACY_3D_CODE`) và phát mã mới cho người đang dùng.
- [ ] Cân nhắc đổi `VIP_KEY_SECRET` (cảnh báo: mất ledger dedup của user đã kích hoạt).
- [ ] Rotate AppLovin SDK key trong dashboard (nếu bật AppLovin).
- [ ] KHÔNG dùng `git filter-repo` + force push như giải pháp duy nhất: fork/clone cũ vẫn giữ bản gốc.

## Acceptance Criteria
- [ ] Keystore/mật khẩu mới không xuất hiện trong repo public, chỉ trong `myKeyStore`.
- [ ] Mã VIP cũ không còn kích hoạt được (hoặc user đã được thông báo).
- [ ] `git grep` các chuỗi bí mật cũ trên HEAD = 0 kết quả (đã đúng từ `8e7aaecd`).

## Prompt loop
Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
