---
id: FEAT-13
type: Feature
effort: M
sources: Codex, Internal (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Nhập caption/text riêng theo từng ảnh trong batch (CSV)

## Mô tả
Mở rộng cơ chế token `{seq}`/`{filename}` đã có — cho phép người dùng dán/nhập danh sách caption (mỗi dòng ứng với 1 ảnh theo đúng thứ tự), map 1-1 vào batch thay vì dùng chung 1 text watermark cho mọi ảnh.

## Triển khai
Thêm màn hình nhập multi-line/paste CSV, map theo index vào `ImageInfo` tương ứng trong `waterMarkRepo.imageInfoList`, override text watermark riêng cho từng ảnh khi generate.

## Acceptance Criteria
- [ ] Nhập được danh sách caption nhiều dòng, khớp đúng thứ tự ảnh trong batch.
- [ ] Export batch áp đúng caption riêng cho từng ảnh.
- [ ] Số dòng caption không khớp số ảnh có cảnh báo rõ ràng (không âm thầm sai lệch).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-13`, file ticket = `todo/FEAT-13-caption-rieng-tung-anh-batch.md`.

## Tiến độ (2026-09-16) — CHƯA đủ Definition of Done, còn ở `inprogress/`

**Điểm tự audit code: 9/10** — nhưng thiếu điều kiện 3/3 (smoke test thật riêng cho ticket này), nên CHƯA move `done/` theo đúng PROMPT_TEMPLATE.md.

- `BatchCaptionBSDialogFragment` + `BatchCaptionParser` (validate CountMismatch/InvalidCsv/Disabled) + `WaterMarkRepository.updateImageCaptions()` — đúng cả 3 AC: nhập nhiều dòng khớp thứ tự ảnh, export áp đúng caption riêng, số dòng không khớp báo `batch_caption_count_mismatch` rõ ràng (không âm thầm sai).
- **Bug nghiêm trọng tự phát hiện qua `/code-review` sau khi ship**: caption rỗng (`""` — ý định "cố ý không watermark ảnh này") không skip vẽ ở `BatchExportEngine.generateImage()` (khác `generatePreviewBitmap()`) — `layoutPaint` mặc định đen tô kín đè lên ảnh xuất ra thật. Đã fix (`shouldSkipTextWatermark()` dùng chung 2 nơi, xem commit `c3af54a`) + test regression `BatchExportEngineCaptionRoboTest`.
- Race phụ tìm được cùng đợt review: dialog dùng snapshot `imageList.size` chụp lúc mở thay vì đọc live lúc bấm Apply — đã fix, không test tự động được (hạn chế Robolectric có sẵn của repo, `requireContext() as MainActivity` không launch thật trong JVM test).
- Test: `BatchCaptionParserTest` (unit), `WaterMarkRepositoryCaptionRoboTest`, `MainViewModelBatchCaptionRoboTest`, `BatchCaptionLayoutRoboTest`, `BatchExportEngineCaptionRoboTest` (regression bug đen ảnh) — toàn bộ 271 unit test PASS.
- **Chưa smoke test thật trên device cho riêng luồng Batch Caption** (chỉ verify qua Robolectric/unit test) — phiên fix code review có smoke test trên TECNO KJ7 (115333744A005844) nhưng cho tính năng Backup/Restore (FEAT-05), không phải luồng này. Cần smoke test tay thật (nhập caption nhiều dòng cho batch 2-3 ảnh, export, xác nhận đúng ảnh nào có caption riêng/ảnh nào giữ text chung, và riêng case caption rỗng phải verify KHÔNG bị đen kín trên file export thật — đúng bug vừa fix) ở phiên sau trước khi coi ticket này đạt đủ 3/3 điều kiện Definition of Done.
- Commit: `32229a8` (feature gốc), `0d9b891`/`c3af54a` (fix bug đen ảnh + race, cùng đợt code review).

## Kết quả kiểm chứng (2026-09-24) — ĐỦ 3/3 Definition of Done, move `done/`

**Điểm tự audit: 9.5/10** (code không đổi từ đợt trước, chỉ bổ sung điều kiện 3/3 còn thiếu — smoke test thật).

1. **Audit code**: giữ nguyên logic đã audit 9/10 ở đợt trước (`BatchCaptionBSDialogFragment`, `BatchCaptionParser`, `WaterMarkRepository.updateImageCaptions()`, fix `shouldSkipTextWatermark()` dùng chung `BatchExportEngine`). Không phát hiện thêm vấn đề mới.
2. **Test**: `./gradlew :app:testDebugUnitTest --tests "*BatchCaption*"` PASS (UP-TO-DATE, giữ nguyên bộ test đã thêm từ trước: `BatchCaptionParserTest`, `WaterMarkRepositoryCaptionRoboTest`, `MainViewModelBatchCaptionRoboTest`, `BatchCaptionLayoutRoboTest`, `BatchExportEngineCaptionRoboTest`).
3. **Smoke test thật trên TECNO KJ7 (115333744A005844)** — khoá qua `AskUserQuestion` do có 2 device (Pixel 7 Pro cắm cùng lúc), đúng CLAUDE.md R3. Batch 3 ảnh thật, chạy đủ 3 case qua UI thật (không crash, logcat sạch):
   - **AC1+AC2**: nhập 3 dòng "Anh Dau/Anh Hai/Anh Ba" khớp đúng 3 ảnh → export ra 3 file JPEG thật, kéo về máy kiểm tra bằng mắt: đúng tile watermark riêng từng ảnh theo đúng thứ tự (`ewm_1790221639867.jpg`="Anh Dau", `...639409.jpg`="Anh Hai", `...639136.jpg`="Anh Ba").
   - **AC3**: nhập 2 dòng cho 3 ảnh → dialog hiện đúng cảnh báo `batch_caption_count_mismatch` ("Đã nhập 2 dòng nhưng có 3 ảnh trong danh sách — vui lòng kiểm tra lại"), KHÔNG áp dụng âm thầm sai, dialog vẫn mở chờ sửa.
   - **Case quan trọng nhất — caption rỗng (regression bug đen ảnh)**: nhập 3 dòng "TẽtA / (rỗng) / TẽtC" khớp 3 ảnh → export ra file thật, kéo về kiểm tra: ảnh ứng dòng rỗng **hoàn toàn sạch, không bị đen kín, không vẽ watermark nào** — đúng ý "cố ý không watermark ảnh này", bug đen ảnh đã fix ở commit `c3af54a` **không tái phát**. 2 ảnh còn lại đúng "TẽtA"/"TẽtC".
   - Phát hiện phụ (không phải bug, hành vi cố ý theo code `SaveImageBSDialogFragment`): nút Export đổi thành "Chia sẻ" sau khi export xong 1 lần trong cùng phiên dialog — cần đổi 1 giá trị `waterMark` config (trigger `resetJobStatus()`, xem `MainActivity.kt:497`) để export lại lần 2 trong cùng session, không phải reset qua đổi caption. Không phải bug của ticket này, không sửa (ngoài phạm vi).

Smoke test dùng `uiautomator dump` lấy bounds chính xác từng nút thay vì đoán toạ độ theo ảnh chụp màn hình scale — tránh tap nhầm giữa các session dialog lồng nhau (Save/Caption/SAF picker).

Commit thêm (chỉ cập nhật doc, không đổi code): xem commit tạo cùng lúc move file.
