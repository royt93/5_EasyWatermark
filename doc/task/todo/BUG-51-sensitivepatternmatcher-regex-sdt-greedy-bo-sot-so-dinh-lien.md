---
id: BUG-51
type: Bug
priority: P2
effort: S
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/redaction/SensitivePatternMatcher.kt
---

# `SensitivePatternMatcher` — regex số điện thoại greedy không chặn biên, bỏ sót SĐT dính liền chuỗi số dài

## Mô tả
`PHONE_CANDIDATE_REGEX` (dòng 20) không có lookahead/ranh giới chặn ký tự số liền kề. Khi so khớp (dòng 32-36), nếu số điện thoại thật (10 số) nằm dính liền với digit khác ngay sát (OCR đọc ảnh không có khoảng cách/xuống dòng rõ ràng giữa SĐT và số khác, ví dụ mã đơn hàng in liền sau SĐT), regex khớp **tham lam** toàn bộ chuỗi 12+ chữ số thành 1 candidate duy nhất. Sau khi strip ký tự không phải số, `digitsOnly.length` rơi ra ngoài khoảng `9..11` hợp lệ → candidate bị loại bỏ hoàn toàn. `findAll` không thử khớp lại các vị trí con bên trong (regex match không overlap), nên SĐT thật nằm bên trong chuỗi đó **không bao giờ được thử lại**.

Hậu quả: tính năng "Che thông tin nhạy cảm" (Smart Redaction) bỏ sót số điện thoại thật sự tồn tại trong ảnh, thông tin nhạy cảm lộ ra ngoài dù tính năng báo đã xử lý.

## Đề xuất
Sửa regex để chặn biên đúng độ dài số điện thoại hợp lệ (dùng lookahead/lookbehind `(?<!\d)` / `(?!\d)` quanh pattern, hoặc giới hạn alternation theo đúng số chữ số 9-11 thay vì `+`/`*` không giới hạn), đảm bảo không nuốt digit thừa từ chuỗi liền kề.

## Acceptance Criteria
- [ ] Chuỗi test "0912345678" đứng riêng → vẫn redact đúng như hiện tại (không regression).
- [ ] Chuỗi test SĐT dính liền số khác (ví dụ "09123456781234567" mô phỏng OCR dính mã đơn hàng) → vẫn tách và redact đúng phần SĐT 10 số hợp lệ bên trong, không bị loại bỏ toàn bộ.
- [ ] Unit test bao phủ cả 2 case trên + các case biên đã có sẵn trong test suite hiện tại của `SensitivePatternMatcher` vẫn PASS.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-51`, file ticket = `todo/BUG-51-sensitivepatternmatcher-regex-sdt-greedy-bo-sot-so-dinh-lien.md`.
