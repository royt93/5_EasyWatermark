# BUG-78: ClipboardImageHelper — MIME type ambiguity (dùng cấp toàn bộ ClipData)

**Priority:** P2 (logic sai, hiếm truy cập, nhưng rủi ro sai kết quả)  
**Effort:** XS (1-2h)  
**Status:** todo

## Vấn đề

`ClipboardImageHelper.isImageUri()` dòng 72: `clipData.description.hasMimeType("image/*")`

Đây là **cấp toàn bộ ClipData**, không phải **từng item riêng lẻ**.

**Kịch bản bug:**
1. Clipboard chứa: 1 ảnh (JPEG) + 1 text ("hello world")
2. `ClipData.description.hasMimeType("image/*")` → TRUE (toàn bộ có ảnh)
3. Loop qua 2 item:
   - Item 0 (image): uri=content://media/image ✓ detect đúng
   - Item 1 (text): uri=null, text="hello" → dòng 72 check `clipData.description` lại → **TRUE** (sai!)
   - Text được coi là ảnh → thêm vào kết quả nhầm

## Root cause

- Dòng 72 là fallback khi không xác định type qua resolver
- Nhưng fallback này lấy MIME của **toàn bộ ClipData**, không phải **item hiện tại**
- Đúng cách: kiểm tra **item.getMimeType()** hoặc infer từ `item.uri`/`item.text`

## Fix

Bỏ dòng 72 (cấp ClipData). Giữ:
- Dòng 67: `FileUtils.isImage()` (kiểm tra resolver)
- Dòng 77-80: `resolver.getType()` + startsWith "image"
- Dòng 83-85: fallback file extension

Nếu vẫn không detect được → **từ chối item**, không assume.

## Test

1. Unit test: clipboard mix image+text → chỉ trả ảnh, không có text
2. Edge case: text có URL pattern "content://..." nhưng MIME khác

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
