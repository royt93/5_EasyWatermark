---
id: ENH-34
type: Enhancement
effort: S
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/data/repo/UserConfigRepository.kt
---

# Chuẩn hoá lựa chọn WEBP theo API mới (`WEBP_LOSSY`/`WEBP_LOSSLESS`)

## Mô tả
UI đang dùng `Bitmap.CompressFormat.WEBP` — deprecated từ API 30+ (thay bằng `WEBP_LOSSY`/`WEBP_LOSSLESS` tách biệt rõ chất lượng/dung lượng). Trên thiết bị API 30+, dùng constant cũ vẫn hoạt động (ánh xạ ngầm) nhưng không cho user chọn LOSSLESS thật sự khi cần giữ nguyên chất lượng tuyệt đối (vd export cho in ấn).

## Triển khai
Trên API 30+: phân biệt rõ WEBP_LOSSY (mặc định, giữ hành vi cũ) và WEBP_LOSSLESS (option mới) trong dropdown format; API <30: giữ nguyên `WEBP` như cũ (fallback).

## Acceptance Criteria
- [ ] Chọn WEBP_LOSSLESS trên thiết bị API 30+ — file xuất ra không mất chất lượng dù nén (so sánh byte-for-byte hoặc PSNR với ảnh gốc).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-34`, file ticket = `todo/ENH-34-chuan-hoa-lua-chon-webp-theo-api-moi-webplossywebplossless.md`.
