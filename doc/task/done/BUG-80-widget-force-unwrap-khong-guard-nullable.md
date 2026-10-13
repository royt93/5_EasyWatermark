# BUG-80: Cụm `!!` không guard trong custom widget (R5 null-safety)

**Priority:** P1 (crash edge-case, vi phạm R5)
**Effort:** S
**Status:** done

## Vấn đề

- `app/src/main/java/com/mckimquyen/watermark/ui/widget/UniformScrollGridLayoutManager.kt:40` `recyclerView!!.context` — param `RecyclerView?`, null khi detached.
- `.../ui/widget/SignatureView.kt:56,59` `gradientColors!!` — public `var`, check-then-`!!` (TOCTOU).
- `.../ui/widget/SelectableImageView.kt:171-172` `srcBitmap!!.width/height` trong `srcBitmap?.let{}`; `onDetachedFromWindow` recycle + null field → NPE khi draw lúc detach.
- `.../ui/widget/SelectableImageView.kt:20-21` `super(context!!)` ở 2 constructor.
- `.../ui/widget/CircleImageView.kt:91` `destCircleBitmap!!` (`makeCircleFrame()` trả `Bitmap?`).

## Fix

- UniformScrollGrid: `if (recyclerView == null) return` (KHÔNG delegate `super` như đề xuất ban đầu: `LinearLayoutManager` cũng deref `recyclerView.getContext()` nên vẫn NPE — RED test xác nhận).
- SignatureView: `gradientColors?.takeIf { it.size >= 2 }?.let { ... }`.
- SelectableImageView: dùng `it.width/it.height`; đổi ctor param thành `Context` non-null.
- CircleImageView: `val mask = destCircleBitmap ?: return`.

## Test

Widget test Robolectric từng case: detach rồi draw, null gradient, smoothScroll khi detached.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-13)

- **Fix**: `UniformScrollGridLayoutManager` (null → no-op), `SignatureView` (bỏ `gradientColors!!` + `currentPaint!!`), `SelectableImageView` (bỏ `srcBitmap!!` + 2 `context!!`, ctor nhận `Context` non-null), `CircleImageView` (bỏ `destCircleBitmap!!`; test lộ thêm NPE thật `drawable.toBitmap()` khi chưa set src — ngoài audit, đã sửa).
- **Test mới (4 class, 12 case)**: `UniformScrollGridLayoutManagerRoboTest` (RED thật = NPE → GREEN, + case nhánh bình thường), `SignatureViewGradientRoboTest` (null/1 màu/2 màu), `WidgetNullSafetyRoboTest` (draw sau detach, ctor, CircleImageView có/không drawable — case không drawable RED thật trên code gốc). Case `SelectableImageView` draw-sau-detach và `SignatureView` là characterization (không tái hiện được NPE gốc single-thread). Toàn package `ui.widget` 94 test pass, ktlint pass.
- **Smoke TECNO KJ7**: Chọn ảnh → cuộn gallery lên/xuống (UniformScrollGrid) → editor → màn Chữ ký (vẽ 2 nét, Neon, đổi màu, áp dụng) → tab Kiểu dáng/Màu chạm 4 swatch (SelectableImageView) → đổi tab detach/rebind; không `AndroidRuntime:E`, không NPE/FATAL. `CircleImageView` (About) không được thao tác tay, chỉ phủ bằng unit test.
- **Tự audit**: 9.3/10 (4 file nhỏ đúng R5; trừ điểm vì phần lớn là edge-case hiếm không smoke trực tiếp được).
