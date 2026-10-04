---
id: BUG-60
type: Bug
priority: P2
effort: XS
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Chinh sach overwrite mo file mode w khong truncate tren android 10

## Mô tả
`openFileDescriptor(.., "w", null)` (~678) và `openOutputStream(targetDoc.uri)` (~899, mặc định `"w"`) trên Android 10+ không đảm bảo truncate — **độ tin cậy trung bình** (hành vi MediaProvider đã biết, chưa test máy thật).

Chính sách OVERWRITE tái dùng file có sẵn: file cũ 5MB ghi đè bằng ảnh 3MB → còn 2MB đuôi cũ, file phình, hash/stamp tính trên phần rác, WebP lệch độ dài RIFF.

## Đề xuất
Dùng mode `"wt"` ở cả 2 chỗ (`openOutputStream(uri, "wt")`).

## Acceptance Criteria
- [ ] Cả 2 nhánh ghi OVERWRITE dùng `"wt"`.
- [ ] Integration test (androidTest): ghi file lớn rồi OVERWRITE bằng ảnh nhỏ hơn → kích thước file = kích thước ảnh mới.
- [ ] Smoke test thật OVERWRITE trên device đã khoá.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-60`, file ticket = `todo/BUG-60-chinh-sach-overwrite-mo-file-mode-w-khong-truncate-tren-android-10.md`.
