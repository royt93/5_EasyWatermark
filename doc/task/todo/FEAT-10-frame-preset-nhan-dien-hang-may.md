---
id: FEAT-10
type: Feature
effort: M
sources: Claude, Agy, doc/feat.md mục E (đã đề xuất, bổ sung ý tự nhận diện hãng máy)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/ExifPbFragment.kt
---

# Frame preset EXIF border + tự nhận diện hãng máy

## Mô tả
Mở rộng tính năng EXIF border đã có: thêm các mẫu khung (Polaroid, dải phim 35mm hoài cổ, logo hãng máy Canon/Sony/Apple/Leica) — và tự động chọn style khung khớp với `TAG_MAKE`/`TAG_MODEL` đọc được từ EXIF, giảm thao tác chọn thủ công.

## Triển khai
Bộ asset logo + chọn template khung trong `ExifPbFragment`; thêm bước map `TAG_MAKE` → style khung mặc định gợi ý (người dùng vẫn đổi tay được).

## Acceptance Criteria
- [ ] Có tối thiểu 3 style khung mới ngoài style hiện tại.
- [ ] Ảnh có EXIF nhận diện được hãng máy tự động gợi ý đúng style khung tương ứng.
