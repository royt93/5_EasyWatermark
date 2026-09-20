---
id: ENH-22
type: Enhancement
effort: XS
sources: Claude fork nội bộ
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/Applovin.kt
---

# `Applovin.kt` 167 dòng dead code, toàn bộ bị comment — nên xoá

## Mô tả
Toàn bộ file (167 dòng) là code cũ bị comment hết (`//` từng dòng) — tích hợp AppLovin MAX thủ công đã thay bằng lib ngoài `AdmobWrapper` (CLAUDE.md xác nhận). File không còn function nào thực thi, chỉ làm rác/nhiễu khi đọc code — tương tự case thư mục `sdkadbmob/` đã dọn trước đó nhưng file này bị sót.

## Triển khai
Xoá hẳn file `Applovin.kt` (đã xác nhận không còn reference nào tới các hàm trong file qua grep).

## Acceptance Criteria
- [ ] `grep -r "Applovin"` sau khi xoá — không còn reference nào trong `app/src/main`.
- [ ] Build vẫn thành công sau khi xoá.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-22`, file ticket = `todo/ENH-22-applovinkt-167-dong-dead-code-toan-bo-bi-comment-nen-xoa.md`.
