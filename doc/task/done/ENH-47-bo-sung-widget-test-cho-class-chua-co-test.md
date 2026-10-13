# ENH-47: Bổ sung widget/unit test cho class UI chưa có test

**Priority:** P2
**Effort:** M
**Status:** done

## Vấn đề (R5 test bắt buộc)

0 tham chiếu trong `app/src/test` cho: `TouchSensitiveRv` (233 dòng, snap/debounce), `PhotoPreviewItem` (190, gesture kéo-xoá), `RecipientPickerBottomSheetFragment` (162), `UniformScrollGridLayoutManager`, `CompressImageDialogFragment` (154), `CompareBottomSheetFragment`.

## Fix

1 Robolectric test mỗi class theo pattern `MultiSelectRvWidgetTest`/`CropOverlayViewWidgetTest`. Làm sau BUG-80/81 để test gộp được.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-14)

- **Test mới**:
  - `RecipientPickerAdapterWidgetTest`: 4 case — danh sách rỗng/None, selected và click trả đúng item, ẩn check/notes rỗng, cập nhật list/count.
  - `CompressImageDialogFragmentWidgetTest`: 5 case — idle, compressing, success, error/retry state, cancel dismiss. Host `MainActivity` + `MainViewModel` thật qua Robolectric; không mock state.
  - `CompareBottomSheetFragmentWidgetTest`: 3 case — giá trị ban đầu/label, đổi Slider cập nhật label, dismiss reset shared state.
- **Coverage tổng cho 6 target**: `TouchSensitiveRvDebounceRoboTest` (5), `PhotoPreviewItemRecycleRoboTest` (5), `UniformScrollGridLayoutManagerRoboTest` (4), `RecipientPickerAdapterWidgetTest` (4), `CompressImageDialogFragmentWidgetTest` (5), `CompareBottomSheetFragmentWidgetTest` (3). Có thêm test trước đó của BUG-80/81/ENH-46, không thêm class test trùng.
- **Integration**: các luồng VM↔UI ở Compress/Compare được host qua `MainActivity` + ViewModel thật trong Robolectric. Không thêm `androidTest` riêng vì ticket chỉ bổ sung state/layout/adapter UI, không chạm Room/DataStore/SAF/IO; integration test I/O không tăng coverage cho các hành vi này. Các integration test nén ảnh/Room/DB sẵn có vẫn giữ nguyên.
- **Kiểm chứng**: full unit suite **1495 test, 0 fail**; `:app:ktlintCheck` pass; `:app:assembleDebug` pass. Smoke trên Pixel 7 Pro (`2B051FDH3006MU`): app launch, gallery render, editor load, Compare sheet mở đúng và hiển thị 100%; không thấy crash. Slider change cuối cùng không xác nhận được bằng smoke (sheet đóng khi gesture thoát vùng); hành vi change + label/reset được xác minh bằng Robolectric.
- **Tự audit**: 9.2/10 — phủ 6 class/luồng UI, không thêm dependency production; trừ điểm vì smoke Compare chỉ xác nhận mở, state transition dựa test.
