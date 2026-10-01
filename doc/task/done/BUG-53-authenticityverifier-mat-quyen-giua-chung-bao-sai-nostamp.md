---
id: BUG-53
type: Bug
priority: P2
effort: XS
sources: full codebase audit (general-purpose agent, 2026-10-01) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/AuthenticityVerifier.kt
---

# `AuthenticityVerifier` — URI mất quyền đọc giữa chừng báo sai "NoStamp" thay vì "Unreadable"

## Mô tả
KDoc của file (dòng 39) ghi rõ quy ước: "Mọi lỗi IO → `Unreadable`". Nhưng tại chỗ đọc `rawStamp` (dòng ~39-48): `contentResolver.openInputStream(uri)` khi permission bị thu hồi giữa chừng (không throw exception, chỉ trả `null` theo đúng hợp đồng API của `ContentResolver`) khiến `rawStamp = null`, rồi `AuthenticityStamp.parse(null) ?: return NoStamp` — trả kết quả `NoStamp` ("ảnh không có con dấu").

Điều này không nhất quán với chính logic đọc hash ngay phía dưới (dòng 50-55), nơi case `null` tương tự được xử lý đúng bằng `?: return Unreadable`. Hai nhánh xử lý cùng 1 loại lỗi (mất quyền đọc) nhưng cho ra 2 kết quả khác nhau.

Hậu quả: ảnh thật sự CÓ con dấu xác thực, nhưng đúng lúc verify thì bị thu hồi quyền đọc file → app báo "ảnh không có con dấu" (sai, gây hiểu nhầm nghiêm trọng: ảnh thật bị coi là giả/không xác thực được) thay vì "không đọc được" (đúng bản chất, trung lập).

## Đề xuất
Sửa nhánh đọc `rawStamp`: khi `openInputStream(uri)` trả `null`, return `Unreadable` thay vì tiếp tục xuống `parse(null) ?: NoStamp`, nhất quán với nhánh đọc hash ngay bên dưới.

## Acceptance Criteria
- [x] `openInputStream(uri)` trả `null` (mất quyền đọc) → `verify()` trả `Unreadable`, KHÔNG trả `NoStamp`.
- [x] Ảnh thật sự không có con dấu (đọc file bình thường, parse ra null vì không có stamp) → vẫn trả đúng `NoStamp` như cũ, không bị đổi hành vi.
- [x] Unit test mock `ContentResolver.openInputStream()` trả `null`, xác nhận kết quả là `Unreadable`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-53`, file ticket = `todo/BUG-53-authenticityverifier-mat-quyen-giua-chung-bao-sai-nostamp.md`.

## Kết quả kiểm chứng

**Fix:** tách null-check của `InputStream` ra khỏi null-check của EXIF attribute trong `verify()`. Trước đây cả 2 trường hợp (`openInputStream()==null` VÀ attribute `TAG_USER_COMMENT` không tồn tại) đều gộp chung thành `rawStamp == null` rồi rơi xuống `parse(null) ?: NoStamp`. Giờ: mở stream trước, `null` → return `Unreadable` ngay; chỉ khi stream mở được mới đọc attribute, `rawStamp` null ở bước này (ảnh đọc được nhưng không có stamp) mới đi tiếp xuống `NoStamp` như cũ.

- **Audit:** 9.5/10 — đúng scope ticket, không đụng nhánh hash phía dưới (đã đúng sẵn), không leak (`stream.use{}`), giữ nguyên hành vi `NoStamp` cho ảnh thật không dấu (verify lại bằng test riêng, không chỉ suy luận).
- **Unit test:** file mới `AuthenticityVerifierUnreadableRoboTest.kt` — 2 test dùng `ContentProvider` giả (cùng pattern đã có ở `BitmapUtilsNullStreamGuardRoboTest`/`BitmapUtilsInputStreamCountRoboTest`, không mock trần): `verify_openInputStreamReturnsNull_returnsUnreadable_notNoStamp` (RED thật trước fix — assertion fail vì trả `NoStamp`) và `verify_realImageWithoutStamp_stillReturnsNoStamp` (ảnh JPEG thật, không set `TAG_USER_COMMENT`, xác nhận hành vi cũ không đổi — pass ngay cả trước fix, dùng để khoá chống regression). `./gradlew :app:testDebugUnitTest` + `ktlintCheck` — toàn bộ xanh, không ảnh hưởng `AuthenticityVerifierEvaluateRoboTest` cũ.
- **Smoke test thật** trên device đã khoá session (TECNO KJ7, serial `115333744A005844`): cài `assembleDebug`, mở app → Thông tin → Kiểm tra chứng thực ảnh → chọn 1 ảnh không có stamp → dialog "Không có con dấu chứng thực" hiện đúng (xác nhận `NoStamp` không bị đổi hành vi), không crash, logcat không có `FATAL`/`AndroidRuntime` exception. Case mất quyền đọc giữa chừng (race condition thật) không mô phỏng được qua UI thủ công — đã phủ đầy đủ bằng unit test ở trên.
