# AD-03 — Sáu hạng mục trước release

Trạng thái: Done (2026-10-17). Source of truth: [feature.md](../../feature.md).

## Phạm vi đã duyệt

1. Banner About cố định dưới toolbar.
2. Audit bản dịch 13 locale (đã áp 176 sửa từ hai lượt audit, kèm test parity + semantic).
3. Crown VIP trên launch và toolbar, hai trạng thái miễn phí/VIP, animation hữu hạn.
4. Privacy options có phản hồi rõ khi SDK không mở form.
5. Clipboard hỗ trợ URI ảnh, HTML và URL HTTPS khi chủ động bấm Dán (không gửi cookie, không theo redirect, chặn mạng nội bộ, giới hạn 20 MB/20 s).
6. Interstitial sau export thành công và khi mở About; dùng cooldown/safety của SDK.

## Kiểm chứng 2026-10-17 trên TECNO KJ7 `115333744A005844`

Mọi thao tác trong phiên đều giữ nguyên máy TECNO KJ7 theo lệnh khoá.

- **Banner About:** cố định dưới toolbar, cuộn nội dung bên dưới không làm trôi banner (PASS trên release).
- **Mở About:** kích hoạt interstitial test ad (PASS; đã dừng theo R4, user xác nhận đóng).
- **Hàng Quyền riêng tư:** hiện Snackbar "Hiện không có tùy chọn quyền riêng tư quảng cáo nào cần cập nhật" (PASS, SDK không có form).
- **Crown VIP:** toolbar và launch card mở đúng `VipManagementActivity` ở trạng thái miễn phí (PASS).
- **Crown VIP active:** instrumentation test `VipQuickEntryIntegrationTest` 1/1 PASS trên máy thật, crown vàng và label đổi "VIP đang hoạt động" (PASS). Trạng thái sau đó đã thu hồi qua UI về miễn phí; cold restart kiểm chứng `isVIPMember=false`.
- **Dán ảnh:**
  - Chrome "Sao chép ảnh" dán thành công vào editor (PASS).
  - Dán chuỗi URL HTTPS dạng text tải được ảnh thật về editor (PASS).
  - Test instrumentation decode Skia + FileProvider + ContentResolver: 1/1 PASS trên TECNO KJ7.
  - Test unit: chặn mạng nội bộ, từ chối redirect/HTML giả mạo, hủy job xóa file cache, chặn URL bypass kiểm tra mạng — tất cả PASS.
- **Sửa lỗi cắt chữ menu:** 4 card chuyển sang wrap-content có chiều cao bằng nhau, 2 dòng title/3 dòng description, co logo theo chỗ trống; instrumentation `LaunchViewLargeFontLayoutIntegrationTest` 1/1 PASS trên TECNO KJ7. Release cuối hiển thị trọn vẹn cả 4 thẻ và footer (PASS).
- **Bản dịch 13 locale:** 176 sửa lỗi ngữ nghĩa, chính tả, thuật ngữ lộn ngữ tộc (tiếng Ý trong file Nhật, "Gøym" = Ẩn thay vì Lưu ở Nynorsk, đảo ngược nghĩa opacity/transparency ở tiếng Nga). `StringResourceParityTest` 2/2 PASS; `StringLocalizationParityTest` 13/13 PASS; `processReleaseResources` PASS. *Giới hạn: toàn bộ sửa đổi dựa trên đánh giá ngữ cảnh/mô hình, chưa có người bản ngữ duyệt (no native-speaker review).*
- **Interstitial sau export:**
  - Trên bản debug (ad test): ngay sau khi batch export hoàn tất, `AdActivity` bật toàn màn hình với "This is an interstitial test ad", log SDK ghi `showInterstitial called, isVIPMember=false` → `Ad SHOWN` (PASS).
  - Lần thử trước trên release không thấy hiện do `AdSafetyConfig` áp dụng throttle 60 s và trần giờ/ngày (lần mở About trước đó đã tính 1 lượt hiển thị).
- **Quyền ảnh:** quyền `READ_MEDIA_IMAGES` cấp tạm trong lúc thử export đã thu hồi hoàn toàn (`pm revoke`, `dumpsys package` xác nhận `granted=false`).

## Bộ kiểm tra tự động cuối cùng

- `ktlintCheck`: PASS (exit code 0).
- `testDebugUnitTest`: 1.559/1.559 test PASS, 0 failure, 0 error, 0 skipped.
- `lintDebug`: PASS với JDK 17 (baseline không tăng lỗi).
- `assembleRelease`: PASS, R8/shrink/signing thành công.
- Bản cài hiện tại trên TECNO KJ7: **release production-signed** (`SHA-256: 2b0568a7...`), dữ liệu người dùng được giữ nguyên.

## Điểm chưa đạt / Cần lưu ý trước khi phát hành

1. **Chưa có người bản ngữ duyệt dịch:** 13 locale đã sạch lỗi ngữ nghĩa thô và đúng định dạng, nhưng một số câu (đặc biệt tiếng Nynorsk và ngôi xưng bạn/ngài tiếng Tây Ban Nha) cần biên tập viên bản ngữ đọc lại nếu muốn đạt độ tự nhiên tối đa.
2. **Quyền khi xuất ảnh:** luồng "Xuất vào bộ sưu tập" hiện vẫn yêu cầu quyền đọc toàn bộ ảnh (`READ_MEDIA_IMAGES`) do hàm `preCheckStoragePermission` kiểm tra chung, kể cả khi ảnh xuất có nguồn gốc từ clipboard/mạng.
3. **Chưa commit và chưa push:** toàn bộ thay đổi đang ở working tree `dev`.
