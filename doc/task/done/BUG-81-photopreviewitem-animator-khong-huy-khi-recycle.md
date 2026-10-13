# BUG-81: PhotoPreviewItem — animator không huỷ khi ViewHolder recycle

**Priority:** P1 (leak/glitch UI, gọi `onRemove` sai item)
**Effort:** S
**Status:** done

## Vấn đề

`app/src/main/java/com/mckimquyen/watermark/ui/widget/PhotoPreviewItem.kt:85,103,118` chạy `view.animate()`/`ivDel.animate()` kèm `withEndAction { onRemove.invoke() }`. `.../ui/adapter/PhotoListPreviewAdapter.kt` không có `onViewRecycled` → holder tái dùng thừa hưởng animator đang chạy, ghi `alpha`/`translationY` lên item mới và gọi `onRemove` với `bindingAdapterPosition` cũ.

## Fix

`override fun onViewRecycled(holder)` gọi `PhotoPreviewItem.resetForRecycle()`: cancel animator + reset alpha/translation/long-press + **`removeEpoch++`** (BẮT BUỘC: `ViewPropertyAnimator.cancel()` vẫn chạy `withEndAction`, nên chỉ cancel thì `onRemove` vẫn bắn; end action so epoch để bỏ qua).

## Test

Robolectric: bắt đầu animation xoá, gọi recycle, advance looper → `onRemove` không được gọi, view về trạng thái gốc.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-13)

- **Fix**: `PhotoPreviewItem.resetForRecycle()` + `removeEpoch` guard trong end action; `PhotoListPreviewAdapter.onViewRecycled` gọi nó.
- **Test**: `PhotoPreviewItemRecycleRoboTest` 5 case dùng gesture kéo-xoá thật (DOWN → long-click → MOVE → UP). Trên code gốc 3/5 RED đúng 3 hành vi bug (`onRemove` bắn sau recycle, view không reset, long-press thừa hưởng); có fix 5/5 GREEN. 2 case đối chứng (không recycle vẫn bắn `onRemove` đúng 1 lần; recycle không gesture là no-op) pass cả trước/sau.
- **Giới hạn test**: Robolectric hoàn tất `ViewPropertyAnimator` trong 1 tick (kể cả ép `durationScale=1`), không idle một phần được → case "giữa chừng" thực chất là recycle ngay sau khi thả tay, trước frame đầu.
- **Toàn suite**: 1469 unit test, 0 fail; ktlint pass.
- **Smoke TECNO KJ7**: chọn 3 ảnh → nhấn giữ thumbnail thứ 3 → khung đỏ preview xoá hiện → kéo lên thả → danh sách 3 → 2, không crash, không `AndroidRuntime:E`. Recycle ViewHolder giữa animation không tái hiện được bằng tay → bằng chứng chính là Robolectric.
- **Tự audit**: 9.2/10 (đúng R5, không leak; trừ điểm vì kịch bản recycle giữa chừng chỉ phủ bằng test).
