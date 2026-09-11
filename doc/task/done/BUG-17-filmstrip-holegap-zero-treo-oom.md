---
id: BUG-17
type: Bug
priority: P2
effort: XS
sources: claude -p (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# `buildFilmStripExifBorder` có thể treo/OOM khi ảnh nguồn cực nhỏ

## Mô tả
`MainViewModel.kt:772-796`, hàm `buildFilmStripExifBorder`: `bandHeight` tính từ `source.height` có thể làm tròn về 0 nếu ảnh nguồn quá nhỏ (khác 3 hàm style kia, riêng `buildMinimalExifBorder` đã có `coerceAtLeast(1)` cho trường hợp tương tự, hàm này thì chưa). Khi `bandHeight`/`holeGap` = 0, `source.width / holeGap` cho kết quả `Infinity`, `.toInt()` = `Int.MAX_VALUE` → vòng lặp vẽ lỗ sprocket chạy tới hàng tỷ lần, treo UI hoặc OOM.

Rủi ro thực tế thấp (ảnh thật hiếm khi cao dưới ~10px) nhưng vẫn là crash tiềm ẩn khi người dùng đưa ảnh bất thường (ảnh lỗi, ảnh 1px test, ảnh bị corrupt decode ra kích thước nhỏ).

## Cách fix đề xuất
Thêm `.coerceAtLeast(1)` cho `bandHeight`/`holeGap` trong `buildFilmStripExifBorder`, đồng nhất với pattern đã dùng ở `buildMinimalExifBorder`.

## Acceptance Criteria
- [x] `holeGap` (và mọi giá trị dùng làm mẫu số) không bao giờ bằng 0 trong `buildFilmStripExifBorder`.
- [x] Unit/Robolectric test: export style Film Strip với ảnh nguồn giả lập chiều cao rất nhỏ (vd 5px) không treo, không vượt thời gian hợp lý, kích thước bitmap hợp lệ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-17`, file ticket = `todo/BUG-17-filmstrip-holegap-zero-treo-oom.md`.

## Kết quả kiểm chứng (2026-09-11)

- **Phát hiện khi bắt tay vào fix:** `bandHeight` trong `buildFilmStripExifBorder` (MainViewModel.kt) **đã có `.coerceAtLeast(1)` từ trước** — do commit `f40b804` (FEAT-14, 2026-09-10 22:21, sau khi ticket này được sinh ra lúc 20:02 cùng ngày) đã thêm khi tham số hoá band thickness, nhưng chưa move ticket sang `done/`. `holeCount` cũng đã có `.coerceAtLeast(2)` sẵn. Không cần sửa code — chỉ còn thiếu test hồi quy + xác nhận.
- **Điểm tự audit:** 9/10 — verify lại toàn bộ hàm, xác nhận không còn phép chia nào có mẫu số có thể về 0.
- **Test:** Thêm `filmStrip_tinySource_neverHangs_andHasValidBitmapSize` (`MainViewModelExifBorderRoboTest`, `@Test(timeout = 5000)`) — export Film Strip với ảnh nguồn 5×5px, guard hồi quy nếu sau này ai gỡ `coerceAtLeast`. Toàn bộ `testAppReleaseDebugUnitTest` PASS.
- **Smoke test thật:** device Samsung SM_A115F (R9JN61LDLFJ). Bật Leica EXIF Border, chọn style Film Strip, export ảnh thật (kích thước bình thường, không phải case biên) — export thành công (`EXPORT LIST(1/1)`), không crash, logcat sạch. Case biên "ảnh cực nhỏ" không tái hiện được qua UI thật (picker hệ thống không có ảnh nào nhỏ cỡ vài px) — cover bằng unit test Robolectric.
