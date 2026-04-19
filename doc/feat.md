# Đề Xuất Các Tính Năng Mới Cho EasyWatermark

Dựa trên cấu trúc source code hiện tại của sự kiện `EasyWatermark` (đã hoàn thiện giao diện Liquid Glass, hỗ trợ các chế độ chèn Watermark bằng Text/Image, tùy chỉnh góc bo, alpha, kiểu chữ và có cơ chế Batch Processing lưu nhiều hình), em xin đề xuất 2 tính năng mạnh mẽ tiếp theo bắt đúng trend giúp tăng giá trị sản phẩm.

## 1. Dynamic EXIF & Device Info Watermark (Khung Chụp Chuyên Nghiệp Dạng Leica / Xiaomi)

**Mô tả:**  
Hiện nay, trend đóng dấu watermark bao gồm viền ảnh, thông số kỹ thuật (Aperture, Shutter Speed, ISO, Focal Length) và tên thiết bị chụp ở ngay dưới bức ảnh (như phong cách filigran của Xiaomi, Leica style) đang cực kỳ được ưa chuộng trên mạng xã hội.

**Cách triển khai vào Codebase:**  
- **Data Layer:** Sử dụng thư viện `androidx.exifinterface.media.ExifInterface` (thực tế dự án đã có sẵn hàm `getOrientation` dùng Exif trong `BitmapUtils.kt`). Ta sẽ lấy thêm mã thẻ: `TAG_MODEL`, `TAG_DATETIME`, `TAG_F_NUMBER`, `TAG_ISO_SPEED_RATINGS`.
- **Logic Vẽ Ảnh (`MainViewModel.generateImage`):** Thay vì vẽ đè (overlay) trực tiếp watermark lên trung tâm ảnh, ta cung cấp một tuỳ chọn tạo Canvas mới với chiều cao lớn hơn (expand padding bottom). Sau đó vẽ Bitmap gốc lên trước, rồi in các dòng text EXIF kèm logo camera (iPhone, Canon, Sony) vào phần background viền mới sinh ra.
- **Giá trị cốt lõi:** Người dùng không cần quan tâm thông số máy ảnh là gì, ứng dụng tự quét siêu dữ liệu (metadata) của ảnh và nhúng vào cực kỳ chuyên nghiệp. Tạo độ viral cao khi người dùng đem đi khoe trên MXH.

---

## 2. Dịch vụ Custom Handwritten Signature (Vẽ Chữ Ký Tay Cá Nhân)

**Mô tả:**  
Rất nhiều nhiếp ảnh gia hay người buôn bán muốn "đóng dấu" ảnh bằng chính nét chữ ký tay thật sự của họ để tăng tính chân thật và bản quyền sở hữu, thay vì sử dụng các phông chữ có sẵn thô cứng.

**Cách triển khai vào Codebase:**  
- **UI Interaction:** Tạo thêm một `SignatureBottomSheetFragment` sử dụng một view `Canvas` cơ bản (bắt sự kiện `ACTION_DOWN`, `ACTION_MOVE` vuốt ngón tay với paint draw path có hiệu ứng bo góc mượt mà).
- **Core Transform:** Sau khi người dùng vẽ xong chữ ký, sử dụng lệnh `canvas.drawColor(TRANSPARENT)` làm nền và lưu Canvas View đó lại thành một `Bitmap` in memory.
- **Tích hợp Flow sẵn có:** Đẩy bitmap này trực tiếp vào luồng xử lý `UiState.UseImage` (Chế độ Image Watermark) mà Sếp đã viết. Hệ thống Repo hiện tại tự động tiếp nhận nó như là một iconUri lưu vào thư mục nội bộ và thực hiện Overlay lên ảnh. Mọi thuộc tính như Color Tinting, Rotation, TileMode (Repeat) hiện tại sẽ kết nối khớp ngay lập tức với chữ ký này mà không cần code lại logic Watermark.
- **Giá trị cốt lõi:** Tận dụng lại tới 90% bộ máy sinh ảnh Image Watermark sẵn có, tiết kiệm nguồn lực trong khi mang lại một USP (Unique Selling Proposition) cực kỳ mạnh để thuyết phục người dùng trả phí mua Premium.
