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
- [x] Chuỗi test "0912345678" đứng riêng → vẫn redact đúng như hiện tại (không regression).
- [x] Chuỗi test SĐT dính liền số khác (ví dụ "09123456781234567" mô phỏng OCR dính mã đơn hàng) → vẫn tách và redact đúng phần SĐT 10 số hợp lệ bên trong, không bị loại bỏ toàn bộ.
- [x] Unit test bao phủ cả 2 case trên + các case biên đã có sẵn trong test suite hiện tại của `SensitivePatternMatcher` vẫn PASS.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-51`, file ticket = `todo/BUG-51-sensitivepatternmatcher-regex-sdt-greedy-bo-sot-so-dinh-lien.md`.

## Kết quả kiểm chứng

**Fix:** `PHONE_CANDIDATE_REGEX` quantifier `{7,10}` → `{7,9}` — root cause thật là quantifier TỰ nó cho phép match tới 12 chữ số (2 chữ số neo + 10 lặp) trong khi `PHONE_DIGITS_MAX=11`, lệch 1. Khi SĐT 10 số dính liền số khác, greedy nuốt luôn đủ 12 số thành 1 candidate, bị loại bỏ hoàn toàn ở bước đếm digit phía sau, và `findAll` không thử lại vị trí con bên trong. Thu hẹp quantifier về đúng trần 11 số khiến match LUÔN nằm trong khoảng hợp lệ ngay từ regex.

- **Phát hiện trong lúc TDD (không nằm ngoài ticket):** thu hẹp quantifier làm lộ ra 1 xung đột toán học thật với test cũ `chuoi so qua dai vuot nguong khong tinh la sdt` — mẫu cũ `"012345678901234"` TÌNH CỜ bắt đầu bằng `"0"` + digit hợp lệ, dưới quantifier đã sửa nó tạo đúng 1 cửa sổ 11-số hợp lệ ở đầu, về bản chất **giống hệt** case SĐT dính liền mà chính ticket này yêu cầu PHẢI phát hiện — không còn là phản ví dụ hợp lệ để test "chuỗi dài vô nghĩa". Đổi mẫu test sang `"123456789012345"` (không có `"0"`/`"+"` nào đứng đầu ≥7 chữ số tiếp theo → không tạo được candidate nào), giữ nguyên Ý ĐỊNH gốc của test, có giải thích rõ trong comment.
- **Audit:** 9/10 — fix 1 dòng quantifier + comment giải thích root cause; điều chỉnh 1 test cũ có lý do rõ ràng (không xoá coverage, chỉ đổi input để không còn ambiguous dưới logic đã sửa); không đụng `EMAIL_REGEX` hay logic khác.
- **Unit test:** thêm `sdt dinh lien voi chuoi so khac van duoc phat hien` (TDD — RED thật trước fix). `./gradlew :app:testDebugUnitTest` (17/17 test `SensitivePatternMatcherTest`) + `ktlintCheck` — toàn bộ xanh.
- **Integration test thật trên device đã khoá** (TECNO KJ7, serial `115333744A005844`): `connectedDebugAndroidTest` — `RedactionPipelineIntegrationTest` (2/2 PASS) xác nhận pipeline ML Kit + mosaic không regression.
- **Smoke test thật trên device đã khoá**: dựng ảnh JPEG chứa text "Goi ngay 09123456781234567 nhe" (mô phỏng SĐT dính mã đơn hàng), cài qua luồng Chọn ảnh → menu ⋮ → Che thông tin nhạy cảm. Kết quả: **"Tìm thấy 1 vùng"**, khoanh đúng dòng text chứa SĐT dính liền (trước fix sẽ là "Không tìm thấy vùng nào" — đúng bug mô tả). Áp dụng mosaic thành công, quay lại editor không crash, logcat sạch không `FATAL`/`AndroidRuntime` exception.
