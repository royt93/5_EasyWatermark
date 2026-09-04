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
- [ ] Ảnh export thất bại (I/O lỗi mô phỏng) hiển thị đúng `JobState.Failure`, không phải `Success`.
- [ ] Exception ngoài `FileNotFoundException`/`OutOfMemoryError` không làm crash cả batch — ảnh đó được đánh dấu lỗi, batch tiếp tục ảnh kế.
- [ ] UI cuối batch hiển thị số lượng thành công/thất bại rõ ràng.
