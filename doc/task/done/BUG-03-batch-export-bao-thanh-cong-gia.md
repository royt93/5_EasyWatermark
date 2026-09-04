---
id: BUG-03
type: Bug
priority: P0
effort: S
sources: Codex, Agy, Internal (3/4, verify trực tiếp xác nhận đúng chính xác dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
verified: true
---

# Batch export báo "thành công" giả dù ảnh lỗi

## Mô tả
`generateList()` (dòng 173-200), đã đọc trực tiếp xác nhận:
```kotlin
info.result = generateImage(contentResolver, viewInfo, info, index)
info.jobState = JobState.Success(info.result!!)   // dòng 187 — luôn Success
```
`generateImage()` trả `Result<T>` có thể là `Result.failure(...)` khi lỗi I/O/logic (không throw exception) — nhưng dòng 187 gán `JobState.Success` **vô điều kiện**, bất kể `info.result` là success hay failure. Đồng thời `catch` chỉ bắt `FileNotFoundException` và `OutOfMemoryError` (dòng 189, 194) — mọi exception khác (SecurityException khi mất quyền MediaStore giữa batch, IllegalStateException...) không có handler, crash cả batch giữa chừng và mất tiến trình các ảnh đã xử lý xong.

Hậu quả thực tế: user chạy batch, một số ảnh lỗi âm thầm (không lưu được) nhưng UI vẫn báo hoàn thành + bắn quảng cáo Interstitial "job finish" như bình thường — người dùng phát hiện thiếu ảnh sau khi đã rời app.

## Cách fix đề xuất
1. Kiểm tra `info.result?.isFailure()` trước khi set `JobState.Success` — set `JobState.Failure` tương ứng nếu fail.
2. Bọc toàn bộ thân `try` bằng `catch (e: Exception)` chung ở cuối (giữ 2 catch cụ thể trước để log code lỗi riêng), tránh 1 ảnh lỗi crash cả batch.
3. Ở màn hình kết quả, hiển thị rõ số ảnh thành công/thất bại thay vì chỉ 1 trạng thái tổng.

## Acceptance Criteria
- [x] Ảnh export thất bại hiển thị đúng `JobState.Failure`, không phải `Success` — fix qua `JobStateResolver.resolve()` (extract pure function, unit test 4 case: success/failure/failure-với-data/null-guard).
- [x] Exception ngoài `FileNotFoundException`/`OutOfMemoryError` không làm crash cả batch — thêm `catch (e: Exception)` chung, ảnh đó đánh dấu `TYPE_ERROR_SAVE_UNKNOWN`, batch tiếp tục ảnh kế.
- [ ] ~~UI cuối batch hiển thị số lượng thành công/thất bại rõ ràng~~ — **tách thành [ENH-13](../todo/ENH-13-hien-thi-so-anh-thanh-cong-that-bai-cuoi-batch.md)** (UI feature riêng, dữ liệu per-item đã đúng nên ENH-13 chỉ cần đọc `jobState` có sẵn, không phụ thuộc thêm gì từ BUG-03).

## Kết quả kiểm chứng
- Unit test: `JobStateResolverTest` (4/4 pass) — trực tiếp cover logic quyết định Success/Failure.
- Compile sạch, không lint violation mới.
- Smoke test thật trên Pixel 7 Pro: batch 2 ảnh thật qua UI (Photo Picker → editor → export) → cả 2 thành công (checkmark xanh), logcat `generateList` xác nhận đúng luồng, không crash, không FATAL EXCEPTION.
- Nhánh lỗi (decode-fail) đã được chứng minh không crash qua smoke test BUG-02 (cùng call chain `applyNewConfig`/`decodeSampledBitmapFromResource`).
- Giới hạn đã biết: chưa có integration test tự động (androidTest) cho riêng nhánh exception-resilience trong `generateList` vì `MainViewModel` cần hạ tầng Hilt test (`HiltTestApplication` + custom test runner) chưa tồn tại trong repo — việc setup hạ tầng đó nằm ngoài phạm vi 1 bug ticket, note lại để cân nhắc làm riêng nếu cần.
