---
id: BUG-14
type: Bug
priority: P0
effort: M
sources: Claude (1/4, verify trực tiếp), Claude-fork re-audit 2026-09-10 (mở rộng phạm vi, verify trực tiếp cả 3 vị trí)
files:
  - app/src/main/java/com/mckimquyen/watermark/MyApplication.kt
  - app/src/main/java/com/mckimquyen/watermark/feature/vip/AdKeys.kt
  - app/src/main/java/com/mckimquyen/watermark/feature/vip/VipKeys.kt
  - app/src/main/java/com/mckimquyen/watermark/feature/vip/VipManagementActivity.kt
verified: true
---

# VIP secret hardcode base64 trong APK, dễ bypass

## Mô tả
Đã đọc trực tiếp, xác nhận:
```kotlin
// dòng 180
private const val VIP_SECRET_30_DAYS_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="
// dòng 81
vipKeySecret = String(Base64.decode(VIP_SECRET_30_DAYS_B64, Base64.NO_WRAP)),
```
Secret dùng để verify VIP key được hardcode dạng base64 (không mã hoá thật, chỉ encode) ngay trong bytecode. Decompile APK bằng jadx (5 phút, không cần kỹ năng đặc biệt) là đọc được nguyên văn secret → bất kỳ ai cũng tạo được VIP key giả, bypass hoàn toàn cơ chế trả phí/giới hạn liên quan.

**Mở rộng phạm vi (re-audit 2026-09-10, đọc trực tiếp xác nhận):**
- Cùng 1 chuỗi `VIP_SECRET_30_DAYS_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="` bị **lặp lại y hệt 3 lần**: `MyApplication.kt:174`, `AdKeys.kt:13`, `VipKeys.kt:6`.
- `VipKeys.kt:7` chứa **secret thứ 2 chưa từng ticket hoá**: `KEY_3_DAYS_B64 = "ZVE3QDkzTDBmITJZMjcwN3hOMDQwMjE5OTN1MEkjMmFL"` (redeem code gói VIP 3 ngày), cùng mức rủi ro decompile.
- `VipManagementActivity.kt:84,118` (`redeemKey()`, `grantRewardedVip()`) luôn gọi `AdManager.activateVipByKey(this, AdKeys.VIP_SECRET_30_DAYS, days)` — truyền cố định secret 30-ngày dù `days` có thể là 3 hoặc 30 (`VipKeys.durationDaysFor`); không đọc được source `AdManager` (lib ngoài) nên chưa chắc là bug logic thật — **cần verify khi fix**.
- Toàn bộ package `feature/vip/` chưa từng xuất hiện trong backlog/CLAUDE.md trước đợt audit này.
- Repo **không có bất kỳ dependency network nào** (không Retrofit/OkHttp/Ktor trong `settings.gradle.kts`) → phương án "verify server-side" thực chất effort **XL** (phải dựng cả tầng backend mới), không phải M như ước tính ban đầu.

## Cách fix đề xuất
Tuỳ mức độ quan trọng của tính năng VIP với doanh thu, chọn 1 trong các hướng (ưu tiên giảm dần theo effort thực tế sau re-audit):
1. **Native code (NDK/JNI)** — secret không nằm trong bytecode Java/Kotlin dễ decompile, tăng độ khó reverse đáng kể so với hiện trạng, effort M-L, không cần hạ tầng mới. **Khuyến nghị cho quy mô app hiện tại** (không có backend sẵn).
2. **Chuyển verify logic lên server** — đáng tin cậy nhất về bảo mật, nhưng effort thực tế **XL** (phải dựng mới toàn bộ backend + auth), chỉ đáng làm nếu doanh thu VIP đủ lớn.
3. Tối thiểu/nhanh: gộp cả 2 secret về 1 nguồn duy nhất (xoá lặp), obfuscate string qua nhiều bước — không triệt để, chỉ tăng effort cho attacker.
4. Bổ sung: ràng buộc VIP key theo thiết bị (device-bound, xem [ENH-17](ENH-17-vip-key-device-bound.md)) — giảm thiệt hại nếu secret vẫn bị lộ (1 key rò rỉ không share được cho nhiều máy).

## Acceptance Criteria
- [ ] Quyết định hướng xử lý dựa trên mức độ thiệt hại thực tế nếu bị bypass (cần trao đổi với chủ dự án — đây là quyết định business, không chỉ kỹ thuật — xem AskUserQuestion đã hỏi trong phiên 2026-09-10).
- [ ] Secret không còn xuất hiện dạng plain string dễ tìm bằng `strings`/decompile cơ bản.
- [ ] Gộp về 1 nguồn duy nhất cho `VIP_SECRET_30_DAYS_B64` (xoá 2 bản lặp ở `AdKeys.kt`/`VipKeys.kt` hoặc `MyApplication.kt`, tuỳ kiến trúc chọn).
- [ ] `KEY_3_DAYS_B64` cũng được xử lý theo cùng phương án (không bỏ sót secret thứ 2).
- [ ] Verify lại `VipManagementActivity.redeemKey`/`grantRewardedVip` có thật sự dùng đúng secret theo từng duration hay luôn hardcode secret 30-ngày (đọc kỹ `AdManager.activateVipByKey` nếu source có, hoặc test thực tế redeem gói 3 ngày).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-14`, file ticket = `todo/BUG-14-vip-secret-hardcode-trong-apk.md`.
