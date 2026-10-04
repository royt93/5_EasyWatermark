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
- [x] Tap Apply 2 lần liên tiếp → `saveSignature` gọi đúng 1 lần.
- [x] Lưu thất bại → nút bật lại để thử lại được.
- [x] Toast dùng `getString(R.string.signature_applied)`, đủ các locale.
- [x] Không còn `parseColor("#...")` literal trong `buildColorList`.
- [x] Robolectric test cho double-tap + thất bại.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-56`, file ticket = `todo/BUG-56-signatureactivity-apply-khong-chong-double-tap-va-toast-hardcode.md`.

## Smoke test thật (Pixel 7 Pro, serial `2B051FDH3006MU`, ngày 2026-10-04)
Mở Chữ ký → vẽ 1 nét → bấm "Áp dụng chữ ký" 2 lần đồng thời (`input tap & input tap`) → số file trong `files/signatures` tăng đúng **1** (2 → 3), quay về editor, không crash. Toast dùng chuỗi i18n `signature_applied`.
Ghi chú: unit test double-tap ban đầu sai vì `View.performClick()` BỎ QUA `isEnabled`; đã sửa dùng chạm thật (commit `fdc7f128`).
