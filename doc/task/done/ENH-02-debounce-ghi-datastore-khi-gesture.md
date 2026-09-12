---
id: ENH-02
type: Enhancement
effort: S
sources: Codex, Agy (2/4) — mô tả gốc đã sửa sau khi verify lại lúc fix BUG-06/ENH-04 (xem "Đính chính")
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/EditTextContentFragment.kt
---

# Debounce ghi DataStore khi nhập text (KHÔNG phải khi pinch/kéo)

## Đính chính (sau khi verify lại lúc fix BUG-06/ENH-04)
Mô tả gốc của ticket này SAI ở phần pinch/kéo — đã đọc lại code xác nhận:
- **Pinch** (`WaterMarkImageView.onScale()`): chỉ mutate `config` (biến local trên View) mỗi frame để render trực tiếp, **KHÔNG** gọi ViewModel/DataStore. Việc persist (`viewModel.updateTextSize()`) chỉ xảy ra **1 lần** ở `onScaleEnd()` (khi nhả tay), không phải mỗi frame.
- **Kéo watermark** (`onTouchEvent` `ACTION_MOVE`): tương tự, chỉ gọi `onOffsetChanged()` (→ ghi DataStore) ở `ACTION_UP`/`ACTION_CANCEL` (nhả tay), không phải mỗi `ACTION_MOVE`.
- **Chỉ nhập text watermark** (`EditTextContentFragment`) thực sự có khả năng gọi update theo mỗi ký tự gõ (cần xác nhận thêm khi làm ticket).

**Lag khi pinch mà người dùng báo cáo (lúc test BUG-06/ENH-04) không phải do ghi DataStore** — đã xác định nguyên nhân thật là `buildIconBitmapShader`/`buildTextBitmapShader` cấp phát bitmap mới (`Bitmap.createScaledBitmap` + `Bitmap.createBitmap`) mỗi lần gọi, chạy mỗi frame `onScale`. Xem chi tiết & hướng fix trong `ENH-16` (ticket mới, tách riêng vì đây là vấn đề khác — throttle rebuild shader, không phải debounce ghi đĩa).

## Mô tả (còn lại, đã thu hẹp scope)
Nhập text watermark liên tục (gõ nhanh) có thể trigger update/re-render mỗi ký tự — cần xác nhận cụ thể tần suất gọi ViewModel khi vào làm ticket này, trước khi giả định cần debounce.

## Đề xuất
Nếu xác nhận đúng là ghi mỗi ký tự: debounce 100-300ms trước khi ghi DataStore, giữ preview text tức thời.

## Acceptance Criteria
- [x] Xác nhận lại (đọc code) tần suất ghi DataStore thực tế khi nhập text watermark trước khi fix.
- [x] Nếu cần: gõ liên tục chỉ ghi DataStore sau khi dừng gõ >100-300ms, preview vẫn tức thời.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-02`, file ticket = `todo/ENH-02-debounce-ghi-datastore-khi-gesture.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Xác nhận lại:** đọc trực tiếp `EditTextContentFragment.onTextChanged` → `shareViewModel.updateText()` → `waterMarkRepo.updateText()` → `dataStore.edit { ... }` — xác nhận ĐÚNG là ghi DataStore thật (I/O đĩa) mỗi ký tự gõ, không có guard nào trước đó. Mô tả gốc ticket đúng cho nhánh text (nhánh pinch/kéo trong "Đính chính" ở trên đã xác nhận KHÔNG có vấn đề, không đụng tới).
- **Fix:** Debounce 200ms (`TEXT_UPDATE_DEBOUNCE_MS`) trong `onTextChanged` — huỷ job cũ (`updateTextJob?.cancel()`) mỗi ký tự, chỉ gọi `shareViewModel.updateText()` thật sau khi dừng gõ. `btnConfirm` huỷ job debounce đang chờ rồi ghi ngay (tránh ghi trùng thừa). Thêm `onDestroyView()` flush giá trị cuối cùng ngay lập tức (đọc trực tiếp `binding.etWaterText.text`, KHÔNG qua debounce) — đảm bảo back/rời màn hình đột ngột trong lúc debounce đang chờ không làm mất ký tự vừa gõ (rủi ro mới phát sinh do thêm debounce, đã tự phát hiện và tự vá trong lúc audit).
- **Điểm tự audit:** 9.5/10 — giải quyết đúng vấn đề I/O thrashing, đồng thời tự phát hiện + vá luôn rủi ro mất dữ liệu tiềm ẩn (`onDestroyView` flush) mà bản thân ticket gốc không lường tới. Preview watermark trên ảnh cũng trễ theo debounce 200ms (không tách riêng "preview tức thời" khỏi "ghi DataStore" vì preview trong kiến trúc hiện tại phụ thuộc trực tiếp DataStore roundtrip — 200ms không đáng chú ý với thao tác gõ chữ, khác hẳn thao tác kéo/pinch liên tục).
- **Test:** `EditTextContentFragmentDebounceRoboTest` (mới, 3 case, dùng lại kỹ thuật `TestHostActivity` launch thật fragment) — `typingRapidly_doesNotWriteDataStore_untilDebounceElapses`, `typingThenPausing_writesOnlyFinalValue_afterDebounceElapses`, `viewDestroyedRightAfterTyping_stillFlushesLatestValue_beforeDebounceElapses` (case flush tự phát hiện ở trên). Bài học kỹ thuật: `DataStore.edit()` ghi qua actor nội bộ trên dispatcher thật riêng — phải poll thời gian thật (`awaitPersistedText`) sau `idleFor()`, không assert ngay (cùng họ vấn đề với generate bitmap thật ở BUG-13).
- **Smoke test thật (OnePlus CPH1989, FUJZIFIR7DQCNRWW — Samsung mất kết nối giữa phiên, đổi máy theo yêu cầu user):** gõ nhanh liên tục 1 chuỗi dài vào ô "Edit watermark" — không giật/lag, keyboard mượt, watermark preview trên ảnh cập nhật đúng text sau khi dừng gõ. Bấm Confirm → text final được lưu đúng, hiện lại chính xác khi mở lại dialog. Không crash, logcat sạch `FATAL EXCEPTION`.
