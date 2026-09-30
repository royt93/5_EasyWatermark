# Feature Source of Truth

> Quản lý danh sách tính năng theo quy tắc R2 toàn cục:
> ✅ Implemented | 🟡 In progress | 📋 Picked | ⏸️ Deferred | ❌ Skipped | 💭 Ideas

## 📋 Picked (Sprint 2026-09-29)

- 📋 **FEAT-26: Dual Watermark Preset (Text + Logo 2 góc)**
  - Preset 1-chạm kết hợp Logo công ty ở góc trên + Text bản quyền ở góc dưới
  - Tái dùng hạ tầng đa lớp FEAT-03 (`extraLayers`)

- 📋 **FEAT-27: Quick Share Bar (Chia sẻ nhanh 1 chạm)**
  - Sau khi xuất batch, hiển thị icon các ứng dụng nhắn tin/lưu trữ cài sẵn (Zalo, Messenger, Telegram, Drive)
  - Bấm mở thẳng app đích kèm file thay vì phải tìm trong Sharesheet

- 📋 **FEAT-28: Frame & Shadow Builder (Khung bo góc + đổ bóng card)**
  - Bo góc ảnh theo bán kính tuỳ chỉnh
  - Tạo đổ bóng nhẹ kiểu card sản phẩm, nền trắng/màu tuỳ chọn trước khi đóng dấu

---

## ✅ Implemented (Đã hoàn thành trước đây)

Xem chi tiết trong `doc/feat.md` và `doc/task/BACKLOG.md`:
- ✅ FEAT-25: Watermark Timestamp tự động từ EXIF (2026-09-30) — token `{time}`/`{datetime}`
  trong `TextTokenResolver`; `ExportNaming.buildBaseTokens` ưu tiên `TAG_DATETIME_ORIGINAL` →
  `TAG_DATETIME` → `TAG_DATETIME_DIGITIZED` (parse qua `parseExifDateTime`, hỗ trợ nhiều định
  dạng), fallback `queryFileLastModified` (MediaStore `DATE_TAKEN`/`DATE_MODIFIED` hoặc
  `File.lastModified()`) rồi mới tới `System.currentTimeMillis()`; chip chèn nhanh trong
  `EditTextContentFragment`. Smoke test thật trên device TECNO KJ7: watermark render đúng
  `{time}`→`17:28`, `{datetime}`→`2026-09-29 17:28`.
- ✅ FEAT-01: Position Anchor 9-grid (2026-09-05)
- ✅ FEAT-02: Naming template cho file xuất (2026-09-10)
- ✅ FEAT-03: Watermark đa lớp (2026-09-24)
- ✅ FEAT-04: Lịch sử batch gần đây (2026-09-22)
- ✅ FEAT-05: Backup/Restore Template & Signature qua SAF + ZIP (2026-09-16)
- ✅ FEAT-06: Watermark profile đầy đủ (2026-09-22)
- ✅ FEAT-07: Preview grid + ước tính dung lượng trước batch export (2026-09-13)
- ✅ FEAT-08: Chọn cả thư mục ảnh vào batch (2026-09-12)
- ✅ FEAT-09: Preset resize theo nền tảng mạng xã hội (2026-09-10)
- ✅ FEAT-10: Tự nhận diện hãng máy để gợi ý style EXIF (2026-09-13)
- ✅ FEAT-11: Hiệu ứng viền/bóng/nền pill cho text watermark (2026-09-12)
- ✅ FEAT-12: Undo/Redo editor (2026-09-22)
- ✅ FEAT-13: Caption riêng từng ảnh trong batch export (2026-09-24)
- ✅ FEAT-14: Tham số hoá EXIF frame style (2026-09-10)
- ✅ FEAT-15: Chọn thư mục XUẤT ảnh bằng SAF (2026-09-22)
- ✅ FEAT-16: Crop & Straighten tỉ lệ chuẩn trước watermark (2026-09-24)
- ✅ FEAT-17: Skip 1 ảnh tại grid preview export (2026-09-21)
- ✅ FEAT-18: So sánh trước/sau bằng slider (2026-09-22)
- ✅ FEAT-19: Chính sách xử lý trùng tên file khi export (2026-09-20)
- ✅ FEAT-20: Chia sẻ ZIP sau batch export (2026-09-20)
- ✅ FEAT-21: Dán ảnh từ Clipboard (2026-09-20)
- ✅ FEAT-22: Chụp ảnh từ Camera (2026-09-20)
- ✅ FEAT-23: Ghi nhớ vị trí watermark riêng theo portrait/landscape (2026-09-23)
- ✅ FEAT-24: MRU icon/logo gần đây (2026-09-22)

---

## ⏸️ Deferred

- ⏸️ BUG-14: VIP secret hardcode trong APK
- ⏸️ BUG-15: AdMob rewarded release dùng test ID
- ⏸️ ENH-17: VIP key device-bound
- ⏸️ IDEA-08: Referral VIP qua Share App
- ⏸️ IDEA-04: Cloud sync Brand Kit
- ⏸️ IDEA-05: Chợ template cộng đồng
- ⏸️ IDEA-11: Live Camera Watermark AR Preview
