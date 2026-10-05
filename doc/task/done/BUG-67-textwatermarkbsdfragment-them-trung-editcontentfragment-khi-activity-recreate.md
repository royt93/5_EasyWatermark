---
id: BUG-67
type: Bug
priority: P1
effort: XS
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/TextWatermarkBSDFragment.kt
---

# Textwatermarkbsdfragment them trung editcontentfragment khi activity recreate

## Mô tả
`onViewCreated` (~dòng 182-197) luôn `add(EditTextContentFragment())` + `addToBackStack`, không kiểm tra `savedInstanceState`. `MainActivity` chỉ khai `configChanges="orientation|keyboardHidden"` nên đổi dark mode/font scale/locale hoặc process death sẽ recreate.

**Kịch bản:** Mở dialog sửa text rồi đổi dark mode → `childFragmentManager` đã restore child cũ, `onViewCreated` add thêm cái nữa → 2 `EditText` chồng nhau, 2 collector chạy, phải bấm back 2 lần.

## Đề xuất
Bọc việc `add` bằng `if (savedInstanceState == null)`.

## Acceptance Criteria
- [x] Recreate khi dialog mở → chỉ còn đúng 1 `EditTextContentFragment`.
- [x] Robolectric test recreate (`ActivityController.recreate()`) đếm child fragment.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-67`, file ticket = `todo/BUG-67-textwatermarkbsdfragment-them-trung-editcontentfragment-khi-activity-recreate.md`.

## Kết quả kiểm chứng

**Tái hiện thật trước khi sửa:** `TextWatermarkBSDFragmentRecreateRoboTest` (dựng fragment, `ActivityController.recreate()`, đếm child) → RED `expected: 1 but was: 2` — xác nhận bug đúng như agent mô tả.
**Fix:** `TextWatermarkBSDFragment.onViewCreated` thêm `if (savedInstanceState != null) return` trước khi `add(EditTextContentFragment())` (childFragmentManager đã tự restore child cũ).

- **Audit:** 9.4/10 — sửa 1 dòng đúng gốc, có comment giải thích; test dựng đủ chuỗi recreate với id container cố định để FragmentManager restore thật.
- **Test:** `TextWatermarkBSDFragment*` + `EditTextContentFragment*` → 7 lớp, **17 test, 0 fail**; `ktlintCheck` xanh (BUILD SUCCESSFUL).
- **Chưa làm:** smoke test thật trên máy (Pixel `2B051FDH3006MU` đang mất kết nối) — đổi dark mode khi dialog sửa text mở, kỳ vọng chỉ 1 ô nhập và back 1 lần. Rủi ro thấp vì đã tái hiện + sửa ở mức FragmentManager; ghi lại để làm khi có máy. Ticket đóng với điều kiện này.
