---
id: ENH-19
type: Enhancement
priority: P2
effort: S
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# EXIF border 4 style vẽ text trực tiếp, không đo/co chữ khi tràn

## Mô tả
Cả 4 hàm build EXIF border (`buildClassicExifBorder`/`buildPolaroidExifBorder`/`buildFilmStripExifBorder`/`buildMinimalExifBorder`, quanh dòng 729/759/809/842) dùng `Canvas.drawText()` trực tiếp không đo bề rộng chuỗi trước. Tên máy/model dài (một số máy Android có `TAG_MODEL` rất dài, hoặc copyright/tên máy có ký tự đặc biệt) sẽ bị vẽ tràn ra ngoài canvas, bị cắt cụt xấu thay vì co cỡ chữ hoặc ellipsis.

## Đề xuất
Trước khi `drawText`, đo bề rộng bằng `Paint.measureText()`/`TextUtils.ellipsize()`, co cỡ chữ dần hoặc cắt kèm "…" nếu vượt quá vùng khung dành cho text, áp dụng nhất quán cho cả 4 style.

## Acceptance Criteria
- [ ] Model/text rất dài (test với chuỗi giả lập ~100 ký tự) không bị vẽ tràn khỏi canvas ở cả 4 style.
- [ ] Text độ dài bình thường (case hiện tại) không đổi cách hiển thị (backward-compatible).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-19`, file ticket = `todo/ENH-19-exif-border-text-overflow-ellipsis.md`.
