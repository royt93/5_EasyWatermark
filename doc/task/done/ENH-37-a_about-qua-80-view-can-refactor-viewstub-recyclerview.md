---
id: ENH-37
type: Enhancement
priority: P2
effort: M
sources: Claude (audit dọn lint trước release, 2026-09-28)
files:
  - app/src/main/res/layout/a_about.xml
verified: true
---

# `a_about.xml` >80 view, lint `TooManyViews` — cần refactor cấu trúc

## Mô tả
Lint cảnh báo `a_about.xml` (màn About) có hơn 80 view trong 1 layout tĩnh — không sai chức năng,
không crash, chỉ ảnh hưởng nhẹ hiệu năng inflate (đo lường thực tế trên device chưa thấy giật, màn
About chỉ mở 1 lần/phiên, không phải hot path). Hiện đang suppress bằng
`tools:ignore="TooManyViews"` trên root `CoordinatorLayout` (xem đợt dọn lint trước release
2026-09-28) để không chặn build.

## Vì sao chưa fix ngay
Refactor đúng cách (tách phần danh sách open-source library thành `RecyclerView`/`ViewStub` thay vì
liệt kê view tĩnh cho từng thư viện) là thay đổi cấu trúc, rủi ro hồi quy UI cho 1 màn hình có nhiều
lần audit M3 trước đó (`M3-06`, các đợt "Audit M3 lần 1-5" trong `BACKLOG.md`) — không phù hợp làm
vội trong lúc dọn lint trước release.

## Đề xuất khi làm
- Tách phần liệt kê open-source library (nhiều block lặp cấu trúc giống nhau: tên lib, badge
  license, mô tả) thành `RecyclerView` + adapter, hoặc `<include>`/`<merge>` cho từng block lặp.
- Giữ nguyên phần header/thông tin app phía trên (không lặp, không cần tách).
- Bắt buộc widget test + smoke test thật đối chiếu ảnh chụp trước/sau (màn hình đã audit UI nhiều
  lần, dễ hồi quy pixel nếu tách sai).

## Prompt loop
Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md) — Definition of Done dùng chung.

## ✅ Done (2026-09-29, scoped xuống — xoá dead view thay vì RecyclerView)
Audit trực tiếp `a_about.xml` (1015 dòng) + `AboutActivity.kt` + Explore agent grep toàn bộ
`app/src/test`/`app/src/androidTest`: trong 105 view tag, **19 view chết hoàn toàn** — không test
nào đụng, không code Kotlin nào tham chiếu (ngoại trừ 3 dòng đã comment `//`), luôn
`visibility="gone"`/`0dp` vĩnh viễn, không code path nào từng bật hiển thị:
- 2 dev card quảng bá tác giả/designer cũ ("rosu"/"tovi") — `hsv` + `clDevContainer` +
  `clDesignerContainer` và con
- 6 binding-stub rỗng: `tvChangeLog`/`tvOpenSource`/`tvPrivacyCn` (chỉ dùng trong code đã comment
  chết) + `ivBack`/`flBackButton`/`tvHeaderTitle` (0 reference)
- 1 section label "Debug" mồ côi (`tvTitleInfo`)

**Quyết định KHÔNG làm RecyclerView refactor** (như đề xuất gốc) — YAGNI: xoá 19 view dead đã mang
giá trị thật (rủi ro hồi quy = 0, view chưa từng render) với công sức tối thiểu; RecyclerView cho
11 row lặp còn lại (rating/share/backup/...) tốn effort lớn hơn nhiều (thiết kế model/adapter cho
11 row khác click-handler) cho rủi ro hồi quy pixel/click trên màn đã audit M3 5 lần — đúng risk
ticket gốc tự cảnh báo, không đáng đổi lấy lợi ích lint cosmetic thuần túy (đã suppress, P2, không
chặn build). **Giữ nguyên** `tools:ignore="TooManyViews"` — 86 view còn lại (105→86 view tag) đều
là UI thật đang hiển thị, không phải rác.

Dọn kèm: block comment chết trong `AboutActivity.kt` (3 listener tham chiếu view đã xoá), 5 string
key thành unused (`debug_title`, `developed_with_by_rosu`, `dev_comment`, `designed_with_by_tovi`,
`designer_comment`) xoá đồng bộ 14 file locale tránh phát sinh `ExtraTranslation`. **Giữ nguyên**
`switchDebug` + card bọc nó (798–830 cũ) — có listener sống (`AboutActivity.kt`), xoá = quyết định
bỏ tính năng debug "show bounds", ngoài phạm vi ticket lint thuần tuý.

Test: `./gradlew testDebugUnitTest --tests "*.about.*" --tests "*.ActionBarIconConsistencyTest"
--tests "*.SignatureAndPanelsAccessibilityWidgetTest" --tests "*.MaterialYouInsetsAndThemeRoboTest"`
— 9 suite/44 case PASS 100% (không vỡ gì). Thêm 3 test mới
`AboutActivityDeadViewRemovedWidgetTest` (regression-guard, reflection lên field `AAboutBinding`
thay vì `getIdentifier` toàn cục — vài tên id như `tvTitle` trùng tên layout khác trong app nên
check global resource id sẽ false-negative). `ktlintCheck` sạch. `./gradlew lint`: 0 error, 1
warning `UnusedResources` (`vip_gold_dark`) xác nhận pre-existing không liên quan (qua `git log`),
không phát sinh `ExtraTranslation`/`UnusedResources` mới từ đợt xoá string. Unit/integration test:
không áp dụng (thuần xoá dead XML/resource, không có logic mới, không đụng Room/DataStore/IO).
