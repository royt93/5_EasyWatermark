# BUG-80: Cụm `!!` không guard trong custom widget (R5 null-safety)

**Priority:** P1 (crash edge-case, vi phạm R5)
**Effort:** S
**Status:** todo

## Vấn đề

- `app/src/main/java/com/mckimquyen/watermark/ui/widget/UniformScrollGridLayoutManager.kt:40` `recyclerView!!.context` — param `RecyclerView?`, null khi detached.
- `.../ui/widget/SignatureView.kt:56,59` `gradientColors!!` — public `var`, check-then-`!!` (TOCTOU).
- `.../ui/widget/SelectableImageView.kt:171-172` `srcBitmap!!.width/height` trong `srcBitmap?.let{}`; `onDetachedFromWindow` recycle + null field → NPE khi draw lúc detach.
- `.../ui/widget/SelectableImageView.kt:20-21` `super(context!!)` ở 2 constructor.
- `.../ui/widget/CircleImageView.kt:91` `destCircleBitmap!!` (`makeCircleFrame()` trả `Bitmap?`).

## Fix

- UniformScrollGrid: `val rv = recyclerView ?: return super.smoothScrollToPosition(...)`.
- SignatureView: `gradientColors?.takeIf { it.size >= 2 }?.let { ... }`.
- SelectableImageView: dùng `it.width/it.height`; đổi ctor param thành `Context` non-null.
- CircleImageView: `val mask = destCircleBitmap ?: return`.

## Test

Widget test Robolectric từng case: detach rồi draw, null gradient, smoothScroll khi detached.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
