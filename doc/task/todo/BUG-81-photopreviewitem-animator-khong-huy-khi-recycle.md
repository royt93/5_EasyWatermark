# BUG-81: PhotoPreviewItem — animator không huỷ khi ViewHolder recycle

**Priority:** P1 (leak/glitch UI, gọi `onRemove` sai item)
**Effort:** S
**Status:** todo

## Vấn đề

`app/src/main/java/com/mckimquyen/watermark/ui/widget/PhotoPreviewItem.kt:85,103,118` chạy `view.animate()`/`ivDel.animate()` kèm `withEndAction { onRemove.invoke() }`. `.../ui/adapter/PhotoListPreviewAdapter.kt` không có `onViewRecycled` → holder tái dùng thừa hưởng animator đang chạy, ghi `alpha`/`translationY` lên item mới và gọi `onRemove` với `bindingAdapterPosition` cũ.

## Fix

`override fun onViewRecycled(holder)`: `itemView.animate().cancel()` + `ivRemove.animate().cancel()`, reset alpha/translation.

## Test

Robolectric: bắt đầu animation xoá, gọi recycle, advance looper → `onRemove` không được gọi, view về trạng thái gốc.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
