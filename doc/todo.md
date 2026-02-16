# Danh sách việc cần làm & Cải tiến

## Tính năng cần triển khai (từ comment trong MyApplication)

- [ ] Tích hợp Firebase
- [ ] Thêm tính năng chọn màu (Color)
- [ ] Thêm tính năng chia sẻ ứng dụng (Share App)

## Cải thiện mã nguồn

- [ ] **Dọn dẹp code bị comment**: Xóa các khối code lớn bị comment (callback AdMob/AppLovin) trong `MyApplication.kt` và `build.gradle.kts` để code sạch và dễ đọc hơn.
- [ ] **Chuỗi cứng (Hardcoded Strings)**: Loại bỏ các log tag cứng như `roy93~` và thay thế các số magic bằng hằng số định nghĩa rõ ràng.

## Sửa lỗi rò rỉ bộ nhớ (Memory Leak Fixes)

- [ ] **WaterMarkImageView**:
  - **Vấn đề**: Hàm `cancel()` cho `generateBitmapJob` được gọi, nhưng view implement `CoroutineScope` mà không override `onDetachedFromWindow` để hủy scope. Điều này có thể dẫn đến rò rỉ nếu các tác vụ (như tạo bitmap) vẫn chạy sau khi view bị hủy.
  - **Vấn đề nghiêm trọng**: `Executors.newSingleThreadExecutor()` được dùng cho `generateBitmapCoroutineCtx` nhưng không bao giờ được shutdown. Mỗi lần View được tạo (ví dụ trong RecyclerView), một thread mới sẽ được tạo và không bao giờ giải phóng.
  - **Đề xuất**: Sử dụng một `Dispatcher` chung (như `Dispatchers.Default` hoặc `Dispatchers.IO`) hoặc đảm bảo `onDetachedFromWindow` shutdown executor này.
- [ ] **MyApplication**:
  - **Vấn đề**: Biến `instance` trong `companion object` giữ tham chiếu tĩnh tới `Context` (Application). Mặc dù ít nghiêm trọng hơn leak Activity context, nhưng nên hạn chế truy cập static kiểu này. Sử dụng Dependency Injection (Hilt) đã có sẵn để inject context an toàn hơn.
