---
id: BUG-15
type: Bug
priority: P0
effort: XS
sources: claude -p (external CLI, re-audit 2026-09-10), verify trực tiếp xác nhận đúng dòng
files:
  - app/build.gradle.kts
---

# `ADMOB_REWARDED_ID` build release vẫn dùng ID test của Google

## Mô tả
`app/build.gradle.kts` dòng ~75-78, buildType `release`: `AD_BANNER_ID`/`AD_INTERSTITIAL_ID`/`AD_APP_OPEN_ID` đã đổi sang ID thật (`ca-app-pub-3612191981543807/...`) khi enable AdMob release (commit `f0b04ee`), nhưng `ADMOB_REWARDED_ID` bị sót lại, vẫn là ID test mẫu của Google (`ca-app-pub-3940256099942544/5224354917`).

Nếu tính năng rewarded ad được bật ở bản release, app production sẽ phát quảng cáo test thay vì thật → mất doanh thu, và theo chính sách AdMob việc để lộ test ad ID trong bản release có thể bị flag vi phạm.

## Cách fix đề xuất
Đổi `ADMOB_REWARDED_ID` trong block `release` sang ID rewarded thật (lấy từ AdMob console, cùng account với các ID banner/interstitial/appOpen đã đổi), giữ nguyên ID test ở block `debug`.

## Acceptance Criteria
- [ ] `ADMOB_REWARDED_ID` ở `release` buildConfigField là ID thật, khác ID test hiện tại.
- [ ] `ADMOB_REWARDED_ID` ở `debug` vẫn giữ ID test (đúng hành vi hiện có, không đổi).
- [ ] Build `assembleAppReleaseRelease` thành công, không lộ ID test nào trong `BuildConfig` của biến thể release.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-15`, file ticket = `todo/BUG-15-admob-rewarded-release-dung-test-id.md`.
