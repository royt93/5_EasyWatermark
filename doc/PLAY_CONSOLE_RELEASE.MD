# Nội dung chuẩn bị submit Play Console — version 2026.09.05 (versionCode 20260905)

> Soạn 2026-09-28 dựa theo audit code thật (AndroidManifest, `BatchExportWorker`, lịch sử commit từ
> `221d21d` tới HEAD). Phần Ad SDK/VIP (AD_ID, quảng cáo, gói VIP) **không nằm trong tài liệu này** —
> do người phụ trách Ad SDK tự khai riêng theo quy ước cũ. Trước khi build AAB thật, cân nhắc bump
> lại `versionCode`/`versionName` theo ngày build thực tế (Play không cho upload trùng/thấp hơn
> versionCode đã dùng).

## 1. Release notes (What's New) — dán vào Play Console > "Release notes"

Bản thảo tiếng Việt, đã gộp ~100 commit từ 2026-09-04 thành các điểm chính người dùng quan tâm:

```
✨ Tính năng mới
• Style Coach — gợi ý kiểu watermark bạn hay dùng
• Che thông tin nhạy cảm tự động (khuôn mặt, biển số...) trước khi xuất ảnh
• Con dấu xác thực nguồn ảnh (chống giả mạo) khi xuất file
• Chế độ "Duyệt ảnh cho khách" (Proofing Mode) — gửi bản xem trước trước khi giao ảnh gốc
• Khung EXIF tự đổi màu theo tông ảnh, nhận diện hãng máy ảnh để gợi ý khung phù hợp
• Watermark nhiều lớp — kết hợp chữ + logo cùng lúc
• So sánh ảnh trước/sau watermark bằng thanh trượt, Undo/Redo khi chỉnh sửa
• Lưu/áp dụng nhanh bộ cấu hình watermark (profile), lịch sử các lần xuất ảnh hàng loạt
• Chọn thư mục lưu tuỳ ý, chọn cả thư mục ảnh để xuất hàng loạt
• Chụp ảnh trực tiếp từ camera hoặc dán ảnh từ clipboard để gắn watermark ngay
• Nhập chú thích ảnh hàng loạt bằng giọng nói
• Tự động chỉnh tương phản màu chữ/logo theo độ sáng ảnh nền
• QR xác thực nguồn ảnh, mã hoá theo từng ảnh

🎨 Giao diện
• Làm mới toàn bộ giao diện theo Material You (Android 12+), đồng bộ màu theo hình nền máy

🛠️ Ổn định & hiệu năng
• Sửa nhiều lỗi treo/crash khi xuất ảnh hàng loạt, tối ưu bộ nhớ khi xử lý ảnh lớn (4K/8K)
• Sửa lỗi mất ảnh khi ghi đè thất bại giữa chừng
```

Góp ý: bản đầy đủ hơi dài cho khung "What's new" (Play giới hạn ~500 ký tự/ngôn ngữ) — có thể cắt
bớt xuống 4-5 dòng nổi bật nhất nếu Play Console báo vượt giới hạn khi dán.

## 2. Data Safety (Play Console > App content > Data safety)

Dựa theo permission thật khai trong `AndroidManifest.xml` (loại AD_ID — do Ad SDK):

| Loại dữ liệu | Có thu thập không? | Ghi chú điền vào form |
|---|---|---|
| Ảnh (READ_MEDIA_IMAGES, ...EXTERNAL_STORAGE) | **Không thu thập** | App xử lý watermark hoàn toàn trên máy, không upload ảnh lên server. Chỉ ghi lại đúng nơi người dùng chọn (bộ nhớ máy/thư mục SAF tự chọn). |
| Vị trí (ACCESS_MEDIA_LOCATION — đọc GPS trong EXIF) | **Không thu thập** | Chỉ đọc cục bộ để tự sinh chữ `{location}` trong watermark khi người dùng bật tính năng này, không gửi đi đâu. |
| Thông báo (POST_NOTIFICATIONS) | Không áp dụng (không phải "dữ liệu") | Dùng hiển thị tiến trình xuất ảnh hàng loạt. |
| Mạng (INTERNET, ACCESS_NETWORK_STATE) | Tuỳ theo SDK quảng cáo — **ngoài phạm vi tài liệu này** | Phần Ad SDK tự khai riêng. |

Kết luận nhanh cho phần "core app" (bỏ Ad SDK): có thể chọn **"App doesn't collect or share
the specified types of user data"** cho nhóm Photos/Location, vì toàn bộ xử lý ảnh + GPS đều
on-device, không transmit ra ngoài. Phần Ad ID/quảng cáo do người phụ trách Ad SDK khai riêng.

## 3. Foreground Service — ĐÃ BỎ (2026-09-28, quyết định business)

`BatchExportWorker` từng dùng `setForeground()` + khai `FOREGROUND_SERVICE_DATA_SYNC` (ENH-01 gốc).
Đã audit kỹ chính sách Play Console: **video demo bắt buộc cho MỌI foreground service type khai
báo, không có ngoại lệ** (quy mô app, số lượt cài, hay dùng job type khác đều không miễn) — user
chọn bỏ hẳn foreground service thay vì làm thủ tục khai báo + quay video.

**Đổi gì trong code**: `BatchExportWorker` giờ chạy như WorkManager work thường (không
`setForeground()`/`getForegroundInfo()`), xoá 2 permission `FOREGROUND_SERVICE`/
`FOREGROUND_SERVICE_DATA_SYNC` + service override trong `AndroidManifest.xml`. Notification tiến
trình (số ảnh đã xong/tổng số, nút Huỷ) **vẫn hiện bình thường** — không cần foreground service để
post notification.

**Đánh đổi thật cần biết**: nếu người dùng đưa app xuống nền lâu hoặc thiết bị vào Doze giữa lúc
đang xuất batch dài (nhiều ảnh), hệ thống có thể hoãn/dừng tiến trình giữa chừng — khác hành vi cũ
"sống sót khi xuống nền" của ENH-01. Ảnh đã xử lý xong trước đó không mất (đã ghi vào bộ nhớ), chỉ
phần còn lại trong batch bị treo tới khi app quay lại foreground hoặc hệ thống cho chạy tiếp. Không
cần khai báo/video gì thêm cho mục này khi submit Play Console.

## Nguồn tham khảo
- [Foreground service declaration requirements — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
- [Foreground service types — Android Developers](https://developer.android.com/develop/background-work/services/fgs/service-types)
