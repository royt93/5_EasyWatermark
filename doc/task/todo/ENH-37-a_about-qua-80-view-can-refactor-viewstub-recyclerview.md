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
