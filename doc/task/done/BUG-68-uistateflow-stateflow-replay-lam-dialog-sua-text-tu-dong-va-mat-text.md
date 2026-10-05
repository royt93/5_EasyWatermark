---
id: BUG-68
type: Bug
priority: P1
effort: S
sources: re-audit 2026-10-04 (2 agent đọc code) + verify tay hình dạng code; chưa tái hiện trên máy trừ khi ghi khác
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/TextWatermarkBSDFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTextContentFragment.kt
---

# Uistateflow stateflow replay lam dialog sua text tu dong va mat text

## Mô tả
`uiStateFlow` là `StateFlow` (giữ giá trị cuối `GoEdit`/`UseTemplate`); `flowWithLifecycle(STARTED)` chạy lại khi STOP→START và nhận lại giá trị cũ như event mới → `dialog?.onBackPressed()` chạy. (Đã verify đọc code; **chưa tái hiện trên máy** và chưa kiểm có chỗ nào reset về `None` sau khi xử lý — cần xác nhận trước khi sửa.)

**Kịch bản:** Mở dialog → vào Template → back/chọn template → bấm Home rồi quay lại → dialog tự đóng; với `UseTemplate` còn ghi đè `etWaterText` bằng nội dung template, text user vừa sửa mất.

## Đề xuất
Biến điều hướng thành one-shot (`SharedFlow(replay=0)`/`Channel`) hoặc consume rồi emit `None` sau khi xử lý.

## Acceptance Criteria
- [x] Tái hiện được lỗi bằng test/smoke trước khi sửa (RED).
- [x] Sau sửa: STOP→START không replay điều hướng cũ.
- [x] Không regression luồng Template bình thường.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-68`, file ticket = `todo/BUG-68-uistateflow-stateflow-replay-lam-dialog-sua-text-tu-dong-va-mat-text.md`.

## Kết quả kiểm chứng

**Bug thật đã tái hiện bằng kịch bản đúng và đối chiếu A/B:**
- Test đầu tiên của tôi SAI: phát `GoEdit`/`UseTemplate` ngay khi đang ở màn sửa → dialog đóng ngay cả không STOP/START, vì đó là back-cascade có chủ đích. Không dùng kết quả đó làm bằng chứng.
- Viết lại đúng kịch bản: vào danh sách Template (`goTemplate`) → back/chọn template (về màn sửa) → Home (`STOP`) → quay lại (`START`). Có ca đối chứng không qua template.
- Chạy cùng 3 test trên bản **StateFlow gốc**: control PASS; `backFromTemplateList...` và `useTemplate...` FAIL (`dialog == null`).
- Chạy trên bản **SharedFlow**: cả 3 PASS; text user vừa sửa sau khi chọn template không bị ghi đè.

**Fix:** `MainViewModel.uiStateFlow` từ `MutableStateFlow(UiState.None)` → `MutableSharedFlow(replay=0, extraBufferCapacity=1)` + `asSharedFlow` (điều hướng là event one-shot). `TextWatermarkBSDFragment` predictive back quyết định theo child fragment thật (đang template list → về edit; còn lại → đóng) thay vì đọc `.value`. Sửa 3 test phụ thuộc `.value`: collector event thật cho DualPreset, reflection SharedFlow cho lifecycle test, bỏ assertion state nội bộ `None` nhưng giữ assertion dialog đóng.

- **Audit:** 9.2/10 — đối chiếu StateFlow/SharedFlow trên cùng test chứng minh fix. Trừ điểm: SharedFlow mất event khi không có subscriber.
- **Rủi ro DatabaseError đã phủ:** `MainViewModelDatabaseErrorEventRoboTest` 2/2: collector active nhận `DatabaseError`; collector đến sau không replay lỗi cũ (one-shot). Trong luồng thật `addTemplate()` được gọi từ màn danh sách đang STARTED.
- **Test:** lô chính SharedFlow: 24 lớp, **106 test, 0 fail**, ktlint xanh. Test DatabaseError riêng: 2/2.
- **Chưa làm:** smoke test thật Home→quay lại khi dialog template mở (Pixel mất kết nối).

## Smoke test thật (2026-10-05)
- **Máy:** Galaxy S24 Ultra `R5CX613VZBR` (Pixel mất kết nối ở bước này). Mở dialog sửa text → Danh sách mẫu → Home → `am start` MainActivity.
- **Kết quả:** quay lại vẫn đúng 1 màn "Danh sách mẫu", không điều hướng lặp, không crash.
- **Giới hạn nói thẳng:** quay lại bằng `am start` (không phải chạm app trong Recents); không so A/B với code StateFlow cũ trên máy. Lần thử đầu trên Pixel bị App Open ad chặn nên không tính. Bằng chứng chính vẫn là test StateFlow đỏ / SharedFlow xanh.
