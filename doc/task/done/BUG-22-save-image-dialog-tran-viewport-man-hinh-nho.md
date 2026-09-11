---
id: BUG-22
type: Bug
priority: P1
effort: S
sources: Claude (phát hiện khi smoke test sprint P2 2026-09-11, Samsung SM_A115F 720x1560)
files:
  - app/src/main/res/layout/dlg_save_file.xml
verified: true
---

# `SaveImageBSDialogFragment` ("Export to the album") tràn viewport trên màn hình nhỏ, nút Export không bấm được

## Mô tả
`dlg_save_file.xml` — root là `LinearLayout` thẳng, **không bọc `ScrollView`/`NestedScrollView`**. `BaseBSDFragment.onCreateDialog()` set `BottomSheetBehavior.STATE_EXPANDED` (full chiều cao khả dụng của cửa sổ, đã trừ status/navigation bar).

Trên màn hình nhỏ (đã verify: Samsung SM_A115F, 720×1560px, nav bar 3 nút chiếm ~161px → chiều cao khả dụng thật cho content chỉ ~1322px), tổng chiều cao nội dung (format dropdown + resize dropdown + file name pattern + copyright + quality slider + export list preview + 2 nút CTA) **vượt quá** chiều cao khả dụng. Vì không có `ScrollView`, phần vượt quá (`btnSave` "Export to the album" và `btnOpenGallery`) bị cắt hẳn ra ngoài — không hiện, không bấm được bằng thao tác chạm thường. Đã verify bằng `uiautomator dump`: `btnSave` hoàn toàn vắng mặt khỏi cây UI ở kích thước màn hình thật (chỉ xuất hiện lại khi tăng chiều cao ảo màn hình qua `adb shell wm size` để test).

**Hậu quả: chặn hẳn luồng export ảnh trên các thiết bị màn hình nhỏ/tỷ lệ hẹp** — đây là action chính của cả app (batch watermark → export), không phải tính năng phụ.

## Cách fix đề xuất
Bọc phần nội dung cuộn được (mọi thứ trừ 2 nút CTA cuối) trong `NestedScrollView`, để `btnSave`/`btnOpenGallery` luôn nằm trong vùng nhìn thấy cuối cùng của bottom sheet (không bị đẩy ra ngoài bởi nội dung dài). Có thể dùng `layout_weight`/`fillViewport` để ScrollView co giãn đúng, tương tự pattern các bottom sheet dài khác trong app (kiểm tra `f_position_anchor_bottom_sheet.xml`/`dlg_exif_border.xml` nếu đã có pattern sẵn).

## Acceptance Criteria
- [x] Trên màn hình nhỏ (mô phỏng bằng cách giảm `wm size` hoặc test trên Samsung SM_A115F thật), nút "Export to the album" luôn nằm trong viewport, bấm được bằng thao tác chạm thường (không cần scroll thủ công nếu màn đủ lớn; cuộn được nếu màn quá nhỏ).
- [x] Không regression trên màn hình lớn hơn (TECNO KJ7, layout vẫn đúng, không bị giãn/co lạ).
- [x] Test: Robolectric kiểm chứng cấu trúc layout đã inflate (root là `NestedScrollView`, `fillViewport=true`, 2 nút CTA vẫn tồn tại trong hierarchy sau khi bọc).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-22`, file ticket = `todo/BUG-22-save-image-dialog-tran-viewport-man-hinh-nho.md`.

## Kết quả kiểm chứng (2026-09-11)

- **Fix:** Bọc toàn bộ nội dung `dlg_save_file.xml` (trừ handle pill giữ nguyên bên trong) trong `androidx.core.widget.NestedScrollView` (`fillViewport="true"`), đổi `llContainer` từ `layout_height="match_parent"` sang `wrap_content` — đúng y hệt pattern đã áp dụng cho `dlg_exif_border.xml` ở BUG-18 (cùng họ lỗi: nội dung dài hơn viewport khả dụng của `BottomSheetDialog` khi `STATE_EXPANDED`).
- **Điểm tự audit:** 9.5/10 — tái dùng pattern đã validate trong production (BUG-18), không đổi logic Kotlin, không phá `binding.root` (vẫn là `ViewGroup` hợp lệ cho `TransitionManager.beginDelayedTransition`), id `llContainer` vẫn resolve đúng qua ViewBinding dù lồng sâu hơn 1 cấp.
- **Test:** `DlgSaveFileLayoutRoboTest` (mới, 4 case) — root là `NestedScrollView`, `isFillViewport=true`, `btnSave`/`btnOpenGallery` vẫn tồn tại trong hierarchy, `llContainer` là con trực tiếp duy nhất (guard hồi quy: không ai vô tình thêm sibling ngoài scroll). Launch thật `SaveImageBSDialogFragment` không khả thi trong JVM test (ép kiểu `requireContext() as MainActivity` trực tiếp, không qua interface như các `BSDFragment` khác) — test cấu trúc layout độc lập là phương án khả thi nhất, cùng tinh thần `ExifPbFragmentRoboTest` (BUG-18). Toàn bộ `testAppReleaseDebugUnitTest` (31 class) PASS.
- **Smoke test thật (Samsung SM_A115F, R9JN61LDLFJ, KHÔNG dùng `wm size` hack):** chọn ảnh → mở dialog Export → **vuốt tay bình thường** lên → nút "Export to the album" hiện đầy đủ trong viewport, bấm được → export chạy thành công (`EXPORT LIST(1/1)`, nút "Share"/"View in gallery" hiện đúng), logcat sạch `FATAL EXCEPTION`. Đây chính là luồng đã BỊ CHẶN hoàn toàn trước khi fix (phải dùng `adb shell wm size` tăng chiều cao ảo mới bấm được nút, xem BACKLOG.md sprint note 2026-09-11).
