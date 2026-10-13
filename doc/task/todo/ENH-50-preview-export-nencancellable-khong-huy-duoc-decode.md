---
id: ENH-50
type: Enhancement
priority: P2
effort: M
sources: R5 perf re-audit 2026-10-14 (verify tay: SaveImageListAdapter + ENH-32 test)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
  - app/src/test/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapterPreviewRoboTest.kt
---

# ENH-50: Grid preview export: `withContext(NonCancellable)` khiến cuộn nhanh vẫn decode đủ mọi ảnh đã lướt qua

**Priority:** P2 (jank/pin khi batch lớn, không sai kết quả)
**Effort:** M (cần thiết kế, không phải sửa 1 dòng)
**Status:** todo

## Vấn đề

`SaveImageListAdapter.kt:164-174`: `scope.launch { withContext(NonCancellable) { generatePreview(...) } }`. ENH-32 đã huỷ `previewJob` khi holder bind sang ảnh khác, nhưng vì phần decode + Palette + chấm compliance nằm trong `NonCancellable`, `cancel()` chỉ đánh dấu Job; việc nặng vẫn chạy trọn rồi kết quả bị bỏ (và bitmap mồ côi được recycle). Batch 50-100 ảnh, user fling nhanh → mọi ô lướt qua đều decode đủ, tranh CPU với các ô user đang chờ, preview hiện chậm.

## Vì sao KHÔNG sửa bằng cách xoá wrapper

`NonCancellable` có thể là chủ đích: nếu `generatePreview` bị huỷ giữa chừng khi đang giữ bitmap, bitmap rò (nhánh recycle ở dòng 168-174 chỉ chạy khi hàm trả về). Test ENH-32 `rebind_differentUriWhileGenerating_...` mô phỏng đúng tình huống này. Cần xác minh `generatePreview` → `BatchExportEngine.generatePreview` tự dọn bitmap trung gian khi bị huỷ.

## Đề xuất

1. Đọc `BatchExportEngine` preview path, liệt kê mọi bitmap cấp phát và đảm bảo `try/finally` recycle khi `CancellationException`.
2. Chỉ khi (1) đảm bảo được: bỏ `NonCancellable`, thêm `ensureActive()` giữa các bước nặng (decode → shader → Palette → compliance).
3. Phương án nhẹ hơn nếu (1) quá rủi ro: giới hạn đồng thời (semaphore 2-3) + bỏ qua ô không còn visible trước khi bắt đầu decode, giữ `NonCancellable`.

## Test

- Unit/Robolectric: bind 20 ảnh liên tiếp (giả lập fling), đếm số lần `generatePreview` thực sự chạy hết; mục tiêu ≈ số ô còn visible, không phải 20.
- Test rò bitmap: huỷ giữa chừng, assert mọi bitmap tạo ra đều `isRecycled`.
- Giữ nguyên test ENH-32 hiện có (cập nhật nếu hành vi cancel đổi, ghi rõ lý do).
- Smoke: batch ≥ 30 ảnh trên thiết bị đã khoá, fling danh sách, theo dõi thời gian hiện preview và `dumpsys meminfo` không tăng bất thường.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).
