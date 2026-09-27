---
id: IDEA-10
type: Idea
priority: P2
effort: L
sources: codex exec (external CLI, re-audit 2026-09-10)
files: []
---

# Recipient Fingerprint Batch — watermark riêng theo từng người nhận, truy nguồn rò rỉ

## Mô tả
Khác [IDEA-02](../done/IDEA-02-invisible-watermark-steganography.md) (invisible watermark/steganography chống xoá thuần kỹ thuật), ý tưởng này thêm hẳn 1 **workflow quản lý người nhận**: mỗi lần export batch cho 1 người/nhóm cụ thể, nhúng biến thể watermark vi mô (mã ID riêng) và lưu mapping "ảnh nào → gửi cho ai" cục bộ trên máy. Nếu sau này 1 ảnh bị phát tán trái phép, đối chiếu ngược lại mapping để biết nguồn rò rỉ từ người nhận nào.

## Vì sao đáng làm
Giá trị thực dụng cho nhóm khách hàng B2B của app (nhiếp ảnh gia bán ảnh preview cho khách, studio gửi ảnh cho nhiều đối tác) — khác hẳn nhóm dùng cá nhân thông thường.

## Acceptance Criteria
- [x] Tạo được danh sách người nhận, mỗi người gắn 1 mã định danh riêng.
- [x] Export batch cho 1 người nhận cụ thể → mọi ảnh trong batch mang watermark biến thể theo mã của người đó.
- [x] Có màn hình tra cứu: nhập/chọn 1 ảnh đã export → biết ảnh đó thuộc batch gửi cho người nhận nào.

## Thiết kế đã triển khai

### 1. Room DB riêng cho Người nhận
`data/db/RecipientDatabase.kt` (version 1, không seed asset — tách khỏi `AppDatabase` đúng khuôn `BatchHistoryDatabase`/`WatermarkProfileDatabase`) + `data/model/entity/Recipient.kt` (unique index trên `code`, chặn trùng mã ngay ở tầng DB) + `data/db/dao/RecipientDao.kt` + `data/repo/RecipientRepository.kt`.

### 2. Công thức dấu vân tay
Không phát minh cơ chế mới — **tái dùng nguyên `StegoPayload.ownerIdOf()` (IDEA-02) và `AuthenticityStamp`/EXIF UserComment (IDEA-03)** đã có sẵn, chỉ đổi chuỗi đầu vào:
- **Lớp ẩn DCT (IDEA-02):** `ownerId = StegoPayload.ownerIdOf("${copyright}#${recipientCode}")` thay vì `ownerIdOf(copyright)` khi có gắn người nhận — sống sót qua nén/xoá EXIF y hệt cơ chế gốc.
- **Con dấu EXIF (IDEA-03):** owner ghi vào stamp đổi thành `"${copyright} [${recipientCode}]"`.
- **Token `{recipient}`:** mở rộng `TextTokenResolver`/`ExportNaming.buildBaseTokens()` — dùng được trong watermark text hoặc tên file xuất.

### 3. UI
- `dlg_save_file.xml`: nút "Recipient fingerprint" + mô tả người nhận đang chọn, mở `RecipientPickerBottomSheetFragment` (chọn nhanh hoặc "Không gắn người nhận").
- `RecipientManagementActivity` (CRUD): thêm/sửa/xoá người nhận, mã tự sinh 6 ký tự hex (sửa được tay), validate tên/mã rỗng và mã trùng.
- Lựa chọn **không lưu DataStore** (chỉ sống trong phiên dialog) — cố ý: gắn dấu vân tay là quyết định theo TỪNG lần gửi, mặc định lần sau phải là "không gắn" để tránh nhầm người nhận vào batch kế tiếp.

### 4. Tra cứu ngược (màn "Kiểm tra chứng thực ảnh" có sẵn từ IDEA-03/IDEA-02)
`AboutViewModel.resolveLeakedRecipient()`: ưu tiên đối chiếu lớp ẩn DCT (bền hơn, sống sót re-encode) trước EXIF; nếu `hidden.ownerId` khớp chính chủ (không recipient) thì bỏ qua tra cứu (không phải rò rỉ). Khớp → hiện `⚠️ Leak source: Sent to <tên> (Code: <mã>)` lên **đầu** dialog kết quả.

### 5. Batch History
`BatchHistoryEntity` + `BatchHistoryDatabase` bump version 1→2 (migration thật `ALTER TABLE ADD COLUMN`, không destructive) lưu `recipientCode`/`recipientName` theo từng lần export.

## Test

- **Unit thuần**: `TextTokenResolverRecipientTest` (5 case token `{recipient}`), `StegoPayloadRecipientTest` (6 case: 2 người nhận khác code → ID khác nhau, ổn định qua nhiều lần gọi, khác hẳn ID không-gắn-người-nhận, round-trip encode/decode), `RecipientRepositoryTest` (10 case fake DAO: đối chiếu qua stego ID, qua chuỗi EXIF, ưu tiên đúng nguồn, không đoán bừa khi rỗng/không khớp).
- **Robolectric**: `AboutActivityRecipientLeakRoboTest` (2 case: cảnh báo rò rỉ lên đầu message, không tự bịa cảnh báo khi không khớp).
- **Integration trên device thật (Room + Skia thật)**:
  - `RecipientDaoIntegrationTest` (7 case): unique index trên `code` hoạt động đúng (REPLACE khi trùng mã, không tạo bản ghi mới), order by timestamp, CRUD đầy đủ.
  - `RecipientFingerprintIntegrationTest` (2 case) — **test quan trọng nhất**: nhúng dấu vân tay `owner#recipientCode` → nén JPEG Skia thật (giả lập bị chia sẻ mất EXIF) → đọc lại → tra Room DB thật → **đúng người nhận bị lộ, không lẫn người khác**; ảnh của chính chủ (không gắn ai) không bị báo nhầm là rò rỉ.
  → **Chạy trên device thật: OK (9 tests)**.
- `./gradlew :app:testDebugUnitTest`: **XANH** (858 test — bao gồm sửa 1 test cũ `TextTokenResolverTest` phải cập nhật danh sách token hỗ trợ).
- `./gradlew ktlintCheck`: **XANH**.

## Smoke test thật (device TECNO BG6, serial `118743744X002560`)

(2 thiết bị cắm cùng lúc — TECNO BG6 và Pixel 7 Pro; theo chỉ thị "chỉ dùng tecno, cấm dùng device khác" đã chốt từ trước, dùng thẳng TECNO BG6, không đụng Pixel 7 Pro.)

Luồng đầu-cuối thật qua `uiautomator`, không giả lập:
1. Gửi ảnh JPEG thật vào editor qua `ACTION_SEND` → mở Save sheet → bật "Embed authenticity stamp" + "Invisible watermark" → tap "Recipient fingerprint" (đang "No recipient assigned") → "Manage" → thêm người nhận **"Khach VIP A"** (mã tự sinh `0EF2A2`) → quay lại picker, danh sách reload real-time từ Room Flow, chọn đúng người vừa thêm → mô tả đổi thành "Recipient: Khach VIP A (0EF2A2)".
2. Export → thành công, không crash (logcat `FATAL EXCEPTION` = 0).
3. **Kiểm chứng độc lập byte-level** (không qua code app): kéo file xuất về, tìm thấy chuỗi `EWM1|...` trong JPEG, decode base64 phần owner ra đúng `"Roy Studio [0EF2A2]"`.
4. Information → "Verify photo authenticity" → chọn đúng file vừa xuất → dialog kết quả thật:
   > **Photo is intact**
   > ⚠️ **Leak source: Sent to Khach VIP A (Code: 0EF2A2)**
   >
   > Exported: 27/09/2026 16:14
   > Owner: Roy Studio [0EF2A2]
   > Key: 0243d505d52ba938
   > Signed by this device.
   >
   > Invisible watermark: 39c5ac82
   > Read confidence: 100%
   
   Cả 2 lớp (chữ ký EXIF EC P-256 lẫn DCT ẩn trong pixel) đều tra đúng người nhận, độc lập với nhau.
5. Dọn dẹp: xoá file test khỏi thiết bị sau khi xác nhận.

**Tự audit: 9.5/10** — trừ 0.5 vì lựa chọn không lưu người nhận qua DataStore giữa các lần mở dialog (cố ý, xem phần 3) khiến UX phải chọn lại mỗi lần, đổi lại tránh rủi ro gắn nhầm người nhận vào batch không liên quan.
