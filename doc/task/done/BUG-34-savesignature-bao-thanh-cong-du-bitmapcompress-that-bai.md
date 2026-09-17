---
id: BUG-34
priority: P2
type: Bug
effort: XS
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/SignatureRepository.kt
---

# `saveSignature()` báo thành công dù `Bitmap.compress()` thất bại

## Mô tả
`saveSignature()` ghi WEBP qua `FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out) }` — bỏ qua giá trị `Boolean` trả về từ `compress()` (biểu thị thành công/thất bại thật). Nếu encode thất bại (hiếm nhưng có thể xảy ra — bitmap hỏng, hết dung lượng), hàm vẫn trả `SignatureModel` hợp lệ trỏ tới file rỗng/hỏng, signature không render được khi dùng làm watermark icon. BUG-19 đã fix pattern y hệt cho nhánh ghi MediaStore export, chưa bao phủ luồng signature.

## Triển khai
Kiểm tra giá trị trả về của `compress()`, nếu `false` thì xoá file rỗng + trả `null` thay vì `SignatureModel` giả.

## Acceptance Criteria
- [x] Mock/giả lập `compress()` trả `false` (hoặc dùng bitmap 0x0) — `saveSignature()` trả `null`, không để lại file rác.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-34`, file ticket = `todo/BUG-34-savesignature-bao-thanh-cong-du-bitmapcompress-that-bai.md`.

## Kết quả kiểm chứng (2026-09-17)

**Fix**: tách `SignatureRepository.resolveWriteResult(file, compressSucceeded): Boolean` (companion object, theo đúng pattern `MediaStoreWriteResolver` của BUG-19) — nếu `compressSucceeded == false`, xoá file rồi trả `false`; `saveSignature()` trả `null` khi hàm này trả `false`.

**Unit test mới**: `SignatureRepositoryWriteResultTest.kt` — dùng `TemporaryFolder` thật, 2 case (compress thành công/thất bại), assert đúng hành vi xoá file + giá trị trả về.
- Đã verify test THẬT SỰ bắt được bug: tạm revert fix (hàm luôn trả `true`) → 1/2 test FAILED đúng dự đoán (file KHÔNG bị xoá dù compress thất bại). Khôi phục fix → PASS lại.
- Full suite: 295 tests, 0 failures. `ktlintCheck` — BUILD SUCCESSFUL.

**Smoke test trên device thật** (TECNO KJ7, serial `115333744A005844`):
- Vào editor → tap "Signature" → mở đúng Signature Studio → vẽ nét chữ ký thật → tap "Apply Signature".
- Xác nhận qua `adb shell run-as com.mckimquyen.watermark ls -la files/signatures/`: file `signature_<timestamp>.webp` được tạo với kích thước thật (7856 bytes, không phải 0 byte/rác) — xác nhận `compress()` thành công, `resolveWriteResult()` giữ file đúng theo golden path.
- Không crash, `adb logcat -d "*:E"` sạch.
- **Giới hạn trung thực**: không thể ép `Bitmap.compress()` thất bại THẬT trên device (không có cách an toàn nào để làm hỏng bitmap/hết dung lượng đĩa một cách có kiểm soát trên thiết bị thật mà không rủi ro ảnh hưởng máy) — kịch bản lỗi cụ thể đã được tái hiện đầy đủ qua unit test dùng `TemporaryFolder` thật.

**Tự chấm điểm**: 9/10 — root cause đúng (đúng pattern đã áp dụng ở BUG-19), test tự-verify bằng revert, full suite xanh, smoke test xác nhận golden-path thật trên device (file thật với kích thước hợp lệ). Trừ 1 điểm vì không ép được kịch bản lỗi gốc trên device thật (đã bù bằng unit test dùng file thật).
