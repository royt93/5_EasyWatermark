---
id: BUG-49
type: Bug
priority: P2
effort: XS
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
---

# `GalleryFragment` — slider cuộn tính sai vì dùng `.bottom` thay vì chiều cao view

## Mô tả
Dòng 230 và 280 dùng `recyclerView.bottom` / `rvContent.bottom` để tính `totalHeight`/`recyclerViewVisibleHeight`. `.bottom` là toạ độ Y tương đối so với parent (= `top + height`), KHÔNG phải chiều cao view.

`rvContent` nằm dưới toolbar (constraint `app:layout_constraintTop_toBottomOf="@id/abl"`), nên `.bottom` cộng dư thêm đúng bằng offset top (chiều cao toolbar/appbar phía trên). Kết quả: `totalHeight`/`recyclerViewVisibleHeight` bị thổi phồng theo đúng lượng đó → tỉ lệ scroll của slider lệch, thẻ slider (fast-scroll thumb) có thể trượt vượt quá vùng list thật hoặc dừng sai vị trí so với % đã cuộn.

## Đề xuất
Đổi `.bottom` → `.height` (trừ `paddingBottom`/`paddingTop` nếu công thức hiện tại đang cộng/trừ padding dựa trên giả định `.bottom` là height) tại cả 2 vị trí dòng 230 và 280.

## Acceptance Criteria
- [x] Slider cuộn tới đúng vị trí tương ứng % thật của RecyclerView (không còn offset bằng chiều cao toolbar).
- [x] Kéo slider xuống đáy danh sách → dừng đúng item cuối, không vượt/hụt.
- [x] Widget test (Robolectric) dựng `RecyclerView` có `top` khác 0 (mô phỏng nằm dưới toolbar), xác nhận công thức tính dùng `.height` chứ không lệch theo `.top`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-49`, file ticket = `todo/BUG-49-galleryfragment-slider-dung-bottom-thay-vi-chieu-cao-view.md`.

## Kết quả kiểm chứng

**Fix:** tách hàm `visibleScrollHeight(view: View): Int = view.height - view.paddingBottom` (companion object, theo đúng pattern tách-hàm-pure đã có ở BUG-29/BUG-AUDIT-2026-09-29 trong cùng file), thay cả 2 call site dòng 230 và 280 đang dùng `.bottom - paddingBottom` sang gọi hàm này.

- **Audit:** 9.5/10 — fix đúng 2 call site mô tả trong ticket, dùng chung 1 hàm (không lặp code), giữ nguyên hành vi trừ `paddingBottom`, không đụng logic `computeSliderScrollPercent`/`computeSliderTranslationY` (chỉ nhận tham số tính sẵn, đã đúng từ BUG-29/BUG-AUDIT-2026-09-29).
- **Widget test (Robolectric):** file mới `GalleryFragmentVisibleScrollHeightRoboTest.kt` — dựng `View` thật qua `view.layout(0, 200, 300, 1000)` (top=200 mô phỏng nằm dưới toolbar, height thật=800) xác nhận hàm trả về `.height` (800) chứ không phải `.bottom` (1000); thêm case trừ `paddingBottom` và case `top=0` (không regression khi view không nằm dưới view khác). `./gradlew :app:testDebugUnitTest` toàn bộ xanh (bao gồm `GalleryFragmentSliderScrollPercentTest`/`GalleryFragmentSliderTranslationYTest` cũ không đổi) + `ktlintCheck` pass.
- **Smoke test thật** trên device đã khoá session (TECNO KJ7, serial `115333744A005844`): mở editor → icon "Chọn ảnh" trong toolbar (`actionPick`) → `GalleryFragment` bottom sheet, xác nhận `rvContent` thật có `top=300` (nằm dưới toolbar/appbar, đúng kịch bản bug mô tả). Cuộn danh sách nhiều lần xuống tới item cuối cùng (ảnh "heart" sticker, xác nhận bằng screenshot không đổi giữa 2 lần cuộn liên tiếp = đã chạm đáy) — `sliderCard` dừng gần đáy vùng `rvContent` (bounds trong khoảng container, không vượt ra ngoài/không dính NaN/không kẹt ở đỉnh), không crash, logcat không có `FATAL`/`AndroidRuntime` exception. (Log debug `AppLog.d` bị ROM TECNO lọc mất ở `logcat -d`, không lấy được số liệu `totalHeight` runtime trực tiếp — verify bằng quan sát UI thật + toán học đã chứng minh ở unit test.)
