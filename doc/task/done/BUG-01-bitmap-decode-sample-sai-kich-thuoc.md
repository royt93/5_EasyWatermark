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
- [x] Ảnh nhỏ hơn kích thước yêu cầu decode ở full-resolution (inSampleSize=1).
- [x] Ảnh EXIF orientation 90°/270° đảo đúng W/H khi tính sample size; 180° giữ nguyên — đổi tên qua hàm thuần `shouldInterchangeSize(rotation)` (tách khỏi `interChangeSize(context, uri)` để dễ test, theo pattern resolver đã dùng ở BUG-02/03/04).
- [x] Unit test `BitmapUtilsTest` (8/8 pass): `calculateInSampleSize` 4 case (nhỏ hơn/bằng/gấp đôi/gấp 4 → 1/1/2/4), `shouldInterchangeSize` 4 case (90/270 → true, 180/0 → false).

## Kết quả kiểm chứng
- Compile sạch, không lint violation mới (3 violation ktlint báo ở dòng lân cận đã tồn tại từ trước, ngoài diff).
- Full regression: 49/49 unit test pass.
- Smoke test thật trên **Tecno KJ7** (Android 14) — chuyển từ Pixel 7 Pro sang theo yêu cầu (Pixel đang được dùng song song bởi người khác trong lúc test): load ảnh camera thật `IMG_20231204_180640.jpg` (1280x960) qua `ACTION_SEND`, xem preview, export — thành công, không crash. Logcat xác nhận trực tiếp `inSample = 1` cho ảnh này (đúng hành vi mới, trước fix sẽ luôn ≥2 khi vào nhánh if).
