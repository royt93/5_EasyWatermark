---
id: FEAT-10
type: Feature
effort: S
sources: Claude, Agy, doc/feat.md mục E; codex exec re-audit 2026-09-10 (xác nhận chỉ done 1 phần)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/ExifPbFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/data/model/ExifFrameStyle.kt
---

# Tự nhận diện hãng máy để gợi ý style khung EXIF border

## Mô tả
**Phần "thêm style khung mới" ĐÃ XONG** (2026-09-06, xem `doc/feat.md` mục 9): 4 style Classic/Polaroid/Film Strip/Minimal, `data/model/ExifFrameStyle.kt` + `MainViewModel.buildExifBorderBitmap()`. Cố ý **không** dùng logo hãng máy thật (Canon/Sony/Apple/Leica) như đề xuất gốc — tránh rủi ro trademark, chỉ vẽ bằng Canvas thuần.

**Phần CÒN LẠI (scope thực của ticket này sau khi tách):** tự động gợi ý style khung khớp với `TAG_MAKE`/`TAG_MODEL` đọc từ EXIF (vd máy Fujifilm/Leica gợi ý Classic, máy không rõ hãng gợi ý Minimal), giảm thao tác chọn thủ công — người dùng vẫn đổi tay được, đây chỉ là gợi ý mặc định.

## Triển khai
Thêm bước map `TAG_MAKE` (đã đọc sẵn qua `ExifModel`) → 1 trong 4 `ExifFrameStyle` hiện có, làm giá trị mặc định gợi ý khi mở `ExifPbFragment` lần đầu cho ảnh đó (không ép, chỉ set giá trị khởi tạo).

## Acceptance Criteria
- [x] ~~Có tối thiểu 3 style khung mới ngoài style hiện tại~~ — ĐÃ XONG, xem `doc/feat.md` mục 9.
- [ ] Ảnh có EXIF nhận diện được hãng máy tự động gợi ý đúng style khung tương ứng (vẫn đổi tay được).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-10`, file ticket = `todo/FEAT-10-frame-preset-nhan-dien-hang-may.md`.
