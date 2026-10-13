# ENH-47: Bổ sung widget/unit test cho class UI chưa có test

**Priority:** P2
**Effort:** M
**Status:** todo

## Vấn đề (R5 test bắt buộc)

0 tham chiếu trong `app/src/test` cho: `TouchSensitiveRv` (233 dòng, snap/debounce), `PhotoPreviewItem` (190, gesture kéo-xoá), `RecipientPickerBottomSheetFragment` (162), `UniformScrollGridLayoutManager`, `CompressImageDialogFragment` (154), `CompareBottomSheetFragment`.

## Fix

1 Robolectric test mỗi class theo pattern `MultiSelectRvWidgetTest`/`CropOverlayViewWidgetTest`. Làm sau BUG-80/81 để test gộp được.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
