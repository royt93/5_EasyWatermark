---
id: FEAT-02
type: Feature
effort: S
sources: Codex, Claude, Agy (3/4 — đồng thuận cao)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Naming template cho file xuất (tái dùng token có sẵn)

## Mô tả
`generateOutputName()` hiện luôn cố định dạng `ewm_${timestamp}.ext`, không tái dùng được `TextTokenResolver` đã có sẵn cho text watermark. Cho phép user đặt pattern tên file khi export, vd `{filename}_wm_{seq}`, `IMG_{index}`.

## Triển khai
Hạ tầng token (`{filename}`, `{seq}`, `{date}`...) đã tồn tại — chỉ cần thêm UI nhập pattern (trong `SaveImageBSDialogFragment`) và áp dụng resolver hiện có vào `generateOutputName()` thay vì hardcode.

## Acceptance Criteria
- [ ] User nhập được pattern tên file trước khi export batch.
- [ ] Token resolve đúng cho từng ảnh trong batch (không trùng tên khi có `{seq}`).
- [ ] Giữ hành vi mặc định cũ nếu user không đổi pattern.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-02`, file ticket = `todo/FEAT-02-naming-template-file-xuat.md`.
