---
id: BUG-01
type: Bug
priority: P1
effort: S
sources: Codex, Claude, Agy, Internal (4/4 — đồng thuận cao)
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/BitmapUtils.kt
verified: true
---

# Tính sai `inSampleSize` và chiều xoay ảnh khi decode

## Mô tả
Đã đọc trực tiếp source, xác nhận đúng 2 lỗi:

1. `calculateInSampleSize()` (dòng 258-291): `var inSampleSize = 2` — khởi tạo bằng 2 thay vì 1. Nếu ảnh đã nhỏ hơn `reqWidth/reqHeight`, vòng `while` bị skip nhưng hàm vẫn trả về `2`, khiến MỌI ảnh nhỏ (icon watermark, chữ ký, ảnh preview nhỏ) bị decode ở nửa độ phân giải không cần thiết.
2. `interChangeSize()` (dòng 250-256): `if (rotation == 90f || rotation == 180f) return true`. Về hình học, chỉ 90°/270° mới cần đảo chiều rộng/cao, còn 180° thì không. Code hiện tại đảo sai ở 180° và bỏ sót 270° (rất phổ biến — ảnh chụp dọc từ nhiều điện thoại). Hậu quả: `calculateInSampleSize` nhận sai chiều rộng/cao cho ảnh 180°/270°, gây decode sai tỉ lệ (mờ hoặc tốn RAM thừa).

## Cách fix đề xuất
- `calculateInSampleSize`: đổi `var inSampleSize = 2` → `var inSampleSize = 1`.
- `interChangeSize`: đổi điều kiện thành `rotation == 90f || rotation == 270f`.

## Acceptance Criteria
- [ ] Ảnh nhỏ hơn kích thước yêu cầu decode ở full-resolution (inSampleSize=1).
- [ ] Ảnh EXIF orientation 90°/270° đảo đúng W/H khi tính sample size; 180° giữ nguyên.
- [ ] Có unit test cho `calculateInSampleSize` (ảnh nhỏ hơn req → trả 1) và `interChangeSize` (90/270 → true, 180/0 → false) — bổ sung vào `app/src/test`.
