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
- [ ] Đóng sheet so sánh → cả 2 bitmap được recycle (verify bằng `isRecycled` trong Robolectric test).
- [ ] Đóng sheet TRƯỚC khi render xong → bitmap sinh sau đó bị recycle ngay, không set vào View đã destroy.
- [ ] Không crash "trying to use a recycled bitmap" khi mở/đóng sheet liên tục.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-38`, file ticket = `todo/BUG-38-comparepreview-bottomsheet-khong-recycle-2-bitmap.md`.
