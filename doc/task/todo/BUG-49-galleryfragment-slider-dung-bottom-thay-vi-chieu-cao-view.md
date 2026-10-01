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
- [ ] Slider cuộn tới đúng vị trí tương ứng % thật của RecyclerView (không còn offset bằng chiều cao toolbar).
- [ ] Kéo slider xuống đáy danh sách → dừng đúng item cuối, không vượt/hụt.
- [ ] Widget test (Robolectric) dựng `RecyclerView` có `top` khác 0 (mô phỏng nằm dưới toolbar), xác nhận công thức tính dùng `.height` chứ không lệch theo `.top`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-49`, file ticket = `todo/BUG-49-galleryfragment-slider-dung-bottom-thay-vi-chieu-cao-view.md`.
