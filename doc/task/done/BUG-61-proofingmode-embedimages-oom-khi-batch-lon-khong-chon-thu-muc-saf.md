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

## Smoke test thật (Pixel 7 Pro, serial `2B051FDH3006MU`, ngày 2026-10-04) — phát hiện và sửa lỗi của chính fix
Bật "Chế độ ảnh duyệt cho khách" → xuất ảnh → `proof_index.html` sinh ra nhưng **không nhúng thumbnail** (`data-uri=0`, vẫn `src="ewm_...jpg"` tương đối, 744 bytes). Log thăm dò chỉ ra `encodeAsDataUri` thoát sớm ở bước đọc bounds.

**Nguyên nhân gốc:** `BitmapFactory.decodeStream(stream, null, opts)` với `inJustDecodeBounds=true` LUÔN trả `null` trên Android thật (chỉ điền `outWidth/outHeight`), nhưng code dùng `?: return null` trên chính giá trị trả về → mọi ảnh bị coi là "không mở được stream". Robolectric mô phỏng decode-bounds trả bitmap giả nên unit test (`ProofingModeEmbedThumbnailRoboTest`) KHÔNG bắt được — cùng lớp lỗi với `SignatureRepositoryImportIntegrationTest`.

**Sửa:** tách `openInputStream(uri) ?: return null` khỏi kết quả decode, chỉ kiểm tra `outWidth/outHeight > 0`. Đã gỡ log thăm dò tạm.

**Sau sửa, kiểm chứng lại trên máy thật:** `proof_index (7).html` = 15.232 bytes, `data-uri=1`, `relative-src=0`, thumbnail pull về = JPEG **480×360**, 10.866 bytes (ảnh nguồn 800×600 / ~97 kB). `connectedDebugAndroidTest` 4 lớp (13 test) PASS, gồm `ProofingIndexIntegrationTest` (decode Skia thật).
Bài học: decode-bounds phải kiểm tra ở androidTest, không tin Robolectric. Full suite sau sửa: `testDebugUnitTest assembleDebug assembleDebugAndroidTest ktlintCheck lint` → BUILD SUCCESSFUL.
