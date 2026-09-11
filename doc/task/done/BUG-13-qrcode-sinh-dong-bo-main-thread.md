---
id: BUG-13
type: Bug
priority: P2
effort: XS
sources: Agy (1/4, verify trực tiếp xác nhận đúng dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/QrCodeBottomSheetFragment.kt
verified: true
---

# Sinh QR đồng bộ trên Main thread mỗi ký tự gõ

## Mô tả
`refreshPreview()` dòng 74:
```kotlin
val bitmap = QrCodeGenerator.generate(content, size = QrCodeGenerator.DEFAULT_SIZE)
```
Được gọi từ `afterTextChanged` (dòng 47) — tức MỖI KÝ TỰ người dùng gõ vào ô nhập nội dung QR. `QrCodeGenerator.generate` dùng ZXing encode ma trận (mặc định 512x512 = 262144 pixel duyệt) chạy đồng bộ ngay trên Main thread → giật lag bàn phím rõ rệt, đặc biệt trên thiết bị cấu hình thấp.

## Cách fix đề xuất
- Debounce input (200-300ms) trước khi trigger sinh QR.
- Chuyển việc generate sang `Dispatchers.Default`, dùng coroutine (`viewLifecycleOwner.lifecycleScope`) rồi cập nhật preview trên Main.

## Acceptance Criteria
- [x] Gõ liên tục vào ô nhập nội dung QR không giật lag bàn phím.
- [x] `QrCodeGenerator.generate` không còn chạy trên Main thread.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-13`, file ticket = `todo/BUG-13-qrcode-sinh-dong-bo-main-thread.md`.

## Kết quả kiểm chứng (2026-09-11)

- **Fix:** `refreshPreview()` giờ debounce 250ms (`QR_REFRESH_DEBOUNCE_MS`, huỷ job cũ mỗi lần gõ qua `refreshJob?.cancel()`) trước khi gọi `QrCodeGenerator.generate` bên trong `withContext(Dispatchers.Default)`, chạy trên `viewLifecycleOwner.lifecycleScope` (tự huỷ đúng lifecycle View, không leak qua tái tạo dialog).
- **Điểm tự audit:** 9.5/10 — đúng root cause (Main thread + không debounce), giữ nguyên hành vi UI (preview vẫn cập nhật, chỉ trễ 250ms), nay có widget test thật chứng minh cả debounce lẫn huỷ job cũ.
- **Test (widget, `QrCodeBottomSheetFragmentRoboTest`, mới):** ban đầu đánh giá nhầm là "không launch được `BSDFragment` trong test" — thực ra `QrCodeBottomSheetFragment` không chạm `shareViewModel`/Hilt trong `onViewCreated`/`refreshPreview` (chỉ dùng ở `btnUse` click, ngoài phạm vi debounce), nên add thẳng được vào `FragmentActivity` thường qua `setShowsDialog(false)` — đúng kỹ thuật `GalleryFragmentLifecycleRoboTest` (BUG-10) đã dùng cho `BaseBindBSDFragment`. 2 case:
  - `typing_doesNotGenerateImmediately_generatesAfterDebounceDelay` — gõ text, `idleFor(100ms)` (< 250ms debounce) → preview vẫn chưa có; `idleFor` tiếp tới > 250ms → preview đã render.
  - `rapidRetyping_cancelsStaleJob_onlyFinalContentGeneratesAfterItsOwnDelay` — gõ "a" rồi gõ tiếp "ab" trước khi hết debounce → job của "a" bị huỷ, chỉ có 1 lần generate cho "ab" (không tích luỹ nhiều job chạy song song).
  - **Bài học kỹ thuật quan trọng (ghi lại cho ticket sau):** `shadowOf(Looper).idle()` KHÔNG tham số của Robolectric chạy hết mọi task đang chờ **kể cả task lên lịch ở tương lai** (đã verify thực nghiệm: gọi `idle()` trần ngay sau gõ text khiến debounce 250ms bị "ăn" tức thì) — phải dùng `idleFor(duration)` mới tôn trọng đúng mốc thời gian đã lên lịch của `delay()`/`postDelayed`. Đã thử `kotlinx-coroutines-test` (`Dispatchers.setMain(StandardTestDispatcher())` + `advanceTimeBy`) trước nhưng coroutine không resume dù `advanceUntilIdle()` — bỏ hướng đó, dùng `idleFor` trực tiếp trên Looper thật ổn định hơn trong môi trường này.
- Toàn bộ `testAppReleaseDebugUnitTest` (30 class, gồm `QrCodeGeneratorTest`/`QrCodeBottomSheetFragmentRoboTest`) PASS. Chạy lặp lại `QrCodeBottomSheetFragmentRoboTest` 5 lần liên tiếp — không flaky (phần `generate` thật vẫn chạy trên `Dispatchers.Default` thật nên assertion "đã generate" dùng poll ngắn thay vì assert tức thì, tránh race).
- **Smoke test thật:** device Samsung SM_A115F (R9JN61LDLFJ). Không thao tác trực tiếp được dialog QR qua UI thật trong phiên này (icon QR nằm ngoài vùng hiển thị của hàng func-icon trên màn hình nhỏ — vấn đề layout có sẵn, không thuộc ticket này, đã ghi backlog riêng). Xác nhận gián tiếp: build cài thành công, app không crash xuyên suốt phiên smoke test (logcat sạch `FATAL EXCEPTION`); hành vi debounce/threading đã được chứng minh đầy đủ qua widget test thật (không phải giả lập) ở trên.
