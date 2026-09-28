---
id: BUG-38
type: Bug
priority: P1
effort: XS
sources: Claude self-audit 2026-09-27 (đối chiếu với SurvivabilityBottomSheetFragment cùng pattern)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/ComparePreviewBottomSheetFragment.kt
---

# `ComparePreviewBottomSheetFragment` không recycle 2 bitmap compare — leak mỗi lần mở

## Mô tả
`BatchExportEngine.generateCompareBitmaps()` trả `CompareBitmaps(original, watermarked)` — 2 bitmap
ĐỘC LẬP (`srcBitmap.copy(...)` 2 lần, `BatchExportEngine.kt:1122-1127`), doc ghi rõ "2 bitmap độc
lập, không chia sẻ buffer với BitmapCache" ⇒ caller sở hữu.

`ComparePreviewBottomSheetFragment` set cả 2 vào `ivOriginal`/`ivWatermarked` và **không có
`onDestroyView()`**, không `recycle()` bất kỳ bitmap nào (grep `recycle` trong file = 0 kết quả).
Mỗi lần mở sheet so sánh cho 1 ảnh trong grid preview = 2 bitmap cỡ `PREVIEW_MAX_SIZE` (ARGB_8888)
mồ côi chờ GC; mở liên tiếp nhiều ảnh trong batch lớn cộng dồn nhanh.

Đối chiếu: `SurvivabilityBottomSheetFragment` (cùng pattern "ViewModel trả bitmap, fragment sở hữu")
làm ĐÚNG — giữ `currentBitmap`, recycle bản cũ khi thay, recycle bản cuối trong `onDestroyView()`,
recycle bản mồ côi khi job bị huỷ. `ComparePreviewBottomSheetFragment` thiếu toàn bộ.

Ngoài ra `viewLifecycleOwner.lifecycleScope.launch` ở dòng 52 không giữ `Job` để cancel và không
kiểm tra `view == null` sau `generateCompareBitmaps` — job bị cancel giữa chừng (user đóng sheet
sớm) làm 2 bitmap vừa tạo mồ côi hoàn toàn.

## Cách fix đề xuất
Copy nguyên pattern của `SurvivabilityBottomSheetFragment`: giữ `renderJob` + 2 field bitmap,
`withContext(NonCancellable)` quanh phần đã cấp phát bitmap, recycle bản mồ côi nếu `!isActive ||
view == null`, `onDestroyView()` gỡ drawable khỏi 2 ImageView rồi recycle.

## Acceptance Criteria
- [x] Đóng sheet so sánh → cả 2 bitmap được recycle (verify bằng `isRecycled` trong Robolectric test).
- [x] Đóng sheet TRƯỚC khi render xong → bitmap sinh sau đó bị recycle ngay, không set vào View đã destroy.
- [x] Không crash "trying to use a recycled bitmap" khi mở/đóng sheet liên tục.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-38`, file ticket = `todo/BUG-38-comparepreview-bottomsheet-khong-recycle-2-bitmap.md`.

## Kết quả kiểm chứng (2026-09-28)

**Fix:** copy nguyên pattern `SurvivabilityBottomSheetFragment` — thêm `renderJob: Job?` +
`internal var originalBitmap`/`watermarkedBitmap` (private set, expose cho test). Render bọc
`withContext(NonCancellable)` quanh `generateCompareBitmaps()`, sau đó check `!isActive || view ==
null` → recycle bản mồ côi ngay thay vì set vào view đã destroy. Thêm `onDestroyView()`: cancel
job, gỡ drawable khỏi `ivOriginal`/`ivWatermarked`, recycle cả 2 bitmap nếu chưa recycled.

- **Điểm tự audit:** 9.5/10 — bám sát 100% "Cách fix đề xuất" trong ticket, tái dùng đúng pattern
  đã có (không phát minh cách mới), không magic number/leak/late/force-unwrap mới, diff tối thiểu
  (+43/-5, không đổi hành vi UI hiện có ngoài phần dọn bitmap).
- **Test:** TDD (RED → GREEN) — `ComparePreviewBottomSheetFragmentRoboTest` 3 case: (1) đóng view
  SAU khi render xong → cả 2 bitmap `isRecycled == true`; (2) đóng view NGAY LẬP TỨC (mô phỏng huỷ
  giữa chừng) → không crash, field không set giá trị mồ côi; (3) mở/đóng liên tục 3 lần không
  crash. 3/3 PASS, chạy lại 3 lần liên tiếp không flaky. Chạy kèm 2 test liên quan không regression:
  `BatchExportEngineCompareRoboTest` (FEAT-18, 3/3) + `SurvivabilityBottomSheetFragmentRoboTest`
  (IDEA-09, 4/4). Full `./gradlew :app:testDebugUnitTest` 191/191 suite xanh 0 failures (một vài
  lần full-suite thiếu ~10 class trong XML report / gặp 1 flaky riêng biệt — xác nhận là giới hạn
  môi trường JVM-fork PRE-EXISTING đã ghi ở `doc/todo.md`, không liên quan đợt sửa này, không lặp
  lại khi chạy riêng class liên quan).
- **Smoke test thật** trên device đã khoá session — **TECNO KJ7 (115333744A005844)**: mở editor,
  vào "Xuất vào bộ sưu tập" → bấm card ảnh mở đúng `ComparePreviewBottomSheetFragment` (khác với
  "So sánh" trong menu editor — đó là `CompareBottomSheetFragment` dùng `WaterMarkImageView` riêng,
  không liên quan bug này). Mở/đóng sheet liên tục 5 lần bằng phím Back + tap lại ngay (mô phỏng
  đóng sớm giữa lúc render) → không crash, sheet vẫn mở lại bình thường lần cuối, `logcat` không có
  `FATAL EXCEPTION`/`AndroidRuntime`/"recycled bitmap" suốt phiên. Không gặp quảng cáo che UI (R4).
