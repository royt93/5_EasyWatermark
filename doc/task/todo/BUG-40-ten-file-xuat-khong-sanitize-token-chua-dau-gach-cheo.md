---
id: BUG-40
type: Bug
priority: P2
effort: S
sources: Claude self-audit 2026-09-27 (đối chiếu với ExportZipHelper.sanitizeAndDeduplicateEntryName đã có sẵn)
files:
  - app/src/main/java/com/mckimquyen/watermark/export/ExportNaming.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Tên file xuất không sanitize — token EXIF chứa `/` làm export thất bại

## Mô tả
`ExportNaming.generateOutputName()` (dòng 136-151) nối thẳng kết quả `resolveTextTokens(pattern...)`
với extension, **không lọc ký tự cấm nào**. Nhưng chính bộ token có sẵn sinh ra ký tự `/`:

- `{exposure}` → `"1/125s"` (`BitmapUtils.kt:244`: `"1/${(1 / d).toInt()}s"`)
- `{fnumber}` → `"f/2.8"` (`BitmapUtils.kt:246`: `"f/$fNumber"`)
- `{exif}` → chứa cả 2 (`ExifModel.getFormattedExif()`)
- `{filename}`/`{recipient}`/`{location}` là chuỗi tự do từ provider/user → có thể chứa `: * ? " < >`

Hệ quả theo từng nhánh ghi:
- MediaStore Q+: `DISPLAY_NAME = "photo_1/125s.jpg"` — giá trị không hợp lệ, `insert()` trả null
  hoặc ném `IllegalArgumentException` → ảnh thất bại (mã lỗi mơ hồ `TYPE_ERROR_SAVE_MEDIASTORE_*`).
- Legacy (<Q): `File(mediaDir, "photo_1/125s.jpg")` trỏ vào thư mục con chưa tồn tại →
  `FileNotFoundException` khi `outputStream()`.
- SAF (FEAT-15): `root.createFile(mime, name)` — provider tự đổi/cắt tên, tên file không như user đặt.

Repo ĐÃ có logic sanitize đúng ở `ExportZipHelper.sanitizeAndDeduplicateEntryName()` (regex
`[/\\?%*:|"<>]` → `_`) nhưng chỉ dùng cho entry ZIP, không dùng cho tên file export.

## Cách fix đề xuất
Tách hàm thuần `sanitizeFileName(raw): String` (tái dùng đúng regex + rule fallback khi rỗng của
`ExportZipHelper`, gom về 1 nguồn duy nhất — không copy regex lần 2), áp trong
`generateOutputName()` sau khi resolve token, TRƯỚC khi nối extension. Giữ nguyên hành vi cho
pattern rỗng (`ewm_<timestamp>`).

## Acceptance Criteria
- [ ] Pattern `{filename}_{exposure}` export thành công, tên file không chứa `/` (vd `photo_1_125s.jpg`).
- [ ] Pattern không có ký tự cấm → tên file KHÔNG đổi so với trước fix (không regression FEAT-02/FEAT-19).
- [ ] Unit test cho `sanitizeFileName` (các ký tự cấm, chuỗi rỗng, chuỗi chỉ gồm ký tự cấm).
- [ ] Logic dedupe/versioning FEAT-19 vẫn hoạt động trên tên đã sanitize.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-40`, file ticket = `todo/BUG-40-ten-file-xuat-khong-sanitize-token-chua-dau-gach-cheo.md`.
