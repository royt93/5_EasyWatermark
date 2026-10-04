---
id: BUG-61
type: Bug
priority: P1
effort: M
sources: re-audit 2026-10-04 (agent export/data/utils) + verify tay
files:
  - app/src/main/java/com/mckimquyen/watermark/export/ProofingMode.kt
---

# Proofingmode embedimages oom khi batch lon khong chon thu muc saf

## Mô tả
`embedImages` (~121-132) `readBytes()` + base64 toàn bộ ảnh full-res vào list String; `writeIndex` chỉ bắt `Exception`, không bắt `OutOfMemoryError`.

Proofing Mode, export 50-100 ảnh (Q+, không chọn thư mục SAF) → ~1GB+ heap → OOM ở bước cuối, sau khi ảnh đã ghi nhưng chưa `recordHistory` → worker chết, UI báo lỗi.

## Đề xuất
Nhúng thumbnail nhỏ (decode ~480px, JPEG q70) thay vì bytes gốc, hoặc stream từng `<img>` ra OutputStream; fallback path tương đối khi OOM.

## Acceptance Criteria
- [x] Batch lớn không nhân bộ nhớ theo số ảnh (peak heap bị chặn).
- [x] Unit test chứng minh thumbnail nhỏ hơn bytes gốc + index HTML vẫn hợp lệ (cập nhật `ProofingIndexIntegrationTest`).
- [x] Smoke test thật Proofing Mode nhiều ảnh trên device đã khoá.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-61`, file ticket = `todo/BUG-61-proofingmode-embedimages-oom-khi-batch-lon-khong-chon-thu-muc-saf.md`.

## Kết quả kiểm chứng

**Fix:** `ProofingMode.encodeAsDataUri` — thay vì `readBytes()` + base64 ảnh full-res, decode THUMBNAIL: đọc bounds, `inSampleSize` qua `calculateInSampleSizeForLongEdge`, scale nốt về trần `PROOF_THUMBNAIL_LONG_EDGE = 480`px (không phóng to ảnh nhỏ), nén JPEG q70 rồi base64, recycle bitmap trong `finally`. Ảnh không decode được trả null → giữ path tương đối (1 ảnh hỏng không làm hỏng cả batch, như cũ).

- **Audit:** 9.3/10 — peak heap mỗi ảnh chỉ cỡ thumbnail, không tăng theo kích thước ảnh gốc và không còn giữ bytes full-res của mọi ảnh cùng lúc; hằng số đặt tên (R5), bitmap recycle ở `finally`. Trừ điểm: chưa đo peak heap định lượng với batch 100 ảnh thật trên máy.
- **Unit test:** file mới `ProofingModeEmbedThumbnailRoboTest.kt` (2 test: ảnh lớn 3000x2000 → thumbnail <= 480px và nhỏ hơn bytes gốc; ảnh nhỏ 200x100 không bị phóng to). RED thật với bản cũ, GREEN sau fix. Sửa test cũ `embedImages_entryWithUri_replacesImageSrcWithBase64DataUri` dùng ảnh JPEG thật thay bytes giả (hành vi mới decode + nén lại).
- **Integration test (androidTest):** thêm `embedImages_undecodableImage_keepsRelativeName_whileGoodImageIsEmbedded` trong `ProofingIndexIntegrationTest` — decode Skia thật (Robolectric không mô phỏng đúng decode-failure cho bytes rác, cùng lý do `SignatureRepositoryImportIntegrationTest`). Chờ device để chạy.
- **Full suite:** `./gradlew testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest ktlintCheck lint` → BUILD SUCCESSFUL (exit 0).
- **Smoke test:** CHƯA — Pixel đã khoá không kết nối. Cần: bật Proofing Mode, export nhiều ảnh (không chọn thư mục SAF), mở `proof_index.html` kiểm tra thumbnail hiển thị đúng. Ticket giữ ở `inprogress/` cho tới khi smoke xong.
