---
id: BUG-56
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (agent ui) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/SignatureActivity.kt
  - app/src/main/res/values*/strings.xml
---

# `SignatureActivity` — nút Apply không chống double-tap, toast tiếng Anh hardcode

## Mô tả
`btnApply` (~dòng 220-241) launch `repo.saveSignature(bitmap)` mà không disable nút: tap đúp → lưu 2 file `signature_<ts>.webp` (lịch sử chữ ký trùng), `returnResult` + `finish()` gọi 2 lần. `toast("Signature Applied!")` là chuỗi cứng, không i18n (app có 10+ locale).
Phụ: `buildColorList` dùng 8 `Color.parseColor("#...")` (vi phạm R5 magic value).

## Đề xuất
- `btnApply.isEnabled = false` đầu listener; bật lại ở nhánh lưu thất bại.
- Thêm `<string name="signature_applied">` vào `values/` + các locale (theo quy ước i18n của repo).
- Palette → `const val`/`colors.xml`.

## Acceptance Criteria
- [ ] Tap Apply 2 lần liên tiếp → `saveSignature` gọi đúng 1 lần.
- [ ] Lưu thất bại → nút bật lại để thử lại được.
- [ ] Toast dùng `getString(R.string.signature_applied)`, đủ các locale.
- [ ] Không còn `parseColor("#...")` literal trong `buildColorList`.
- [ ] Robolectric test cho double-tap + thất bại.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-56`, file ticket = `todo/BUG-56-signatureactivity-apply-khong-chong-double-tap-va-toast-hardcode.md`.
