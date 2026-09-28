---
id: BUG-39
type: Bug
priority: P1
effort: M
sources: Claude self-audit 2026-09-27 (grep toàn repo: autoContrastEnabled không xuất hiện trong export/)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
---

# Auto-contrast (IDEA-06) chỉ áp preview editor — export + preview grid bỏ qua, ảnh xuất KHÁC preview

## Mô tả
`WaterMark.autoContrastEnabled` chỉ được đọc ở DUY NHẤT 1 nơi ngoài repo/UI:
`WaterMarkImageView.applyAutoContrastIfEnabled()` (dòng 417, gọi từ `applyNewConfig` dòng 320).

`grep -rn autoContrastEnabled app/src/main` — `export/` (BatchExportEngine, BatchExportWorker) **0
kết quả**. Nghĩa là:
- Bật chip "Tự động tương phản" → preview editor đảo màu chữ đen/trắng + nâng sàn alpha.
- Export thật (`generateImage`) và preview grid (`generatePreviewBitmap`, `generateCompareBitmaps`)
  vẫn dùng `settings.config`/`config` NGUYÊN BẢN → ảnh xuất ra giữ màu/alpha cũ.

Vi phạm 2 thứ cùng lúc: (1) nguyên tắc "preview khớp export" mà chính ENH-05 đã thiết lập;
(2) AC gốc của IDEA-06 — "**batch** ảnh có độ sáng nền khác nhau tại vị trí watermark tự động chọn
màu/opacity đọc rõ" — batch chạy hoàn toàn qua `BatchExportEngine`, tức AC này thực tế CHƯA đạt dù
ticket đã ở `done/` (smoke test khi đó chỉ verify trên preview editor, xem "Kết quả kiểm chứng" của
IDEA-06).

## Cách fix đề xuất
Trích `applyAutoContrastIfEnabled` + `computeAutoContrastSampleRegion` ra hàm dùng chung
(`utils/bitmap/` hoặc `export/AutoContrastResolver`) nhận `(bitmap, imageInfo, config)` trả
`WaterMark` đã điều chỉnh — gọi từ cả `WaterMarkImageView.applyNewConfig` (giữ hành vi cũ) và 3
điểm vẽ trong `BatchExportEngine` NGAY TRƯỚC khi build `bitmapPaint`/shader. Lưu ý vùng lấy mẫu
phải tính theo bitmap FULL-RES lúc export (tỉ lệ khác preview), không copy hằng số scale của View.

## Acceptance Criteria
- [x] Bật auto-contrast, export ảnh nền trắng → màu chữ trong FILE xuất ra khớp preview (đen), không phải màu cũ.
- [x] Tắt auto-contrast → file xuất byte-identical với trước khi fix (không regression).
- [x] Preview grid batch + so sánh trước/sau cũng phản ánh auto-contrast (khớp export).
- [x] Unit test cho hàm resolver dùng chung + 1 integration test export thật (ảnh nền sáng/tối).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-39`, file ticket = `todo/BUG-39-auto-contrast-chi-ap-preview-editor-export-bo-qua.md`.

## Kết quả kiểm chứng (2026-09-28)

**Fix:** trích `WaterMarkImageView.applyAutoContrastIfEnabled()` thành hàm dùng chung
`WaterMarkImageView.resolveAutoContrast(bitmap, tileMode, offsetX, offsetY, textSizeInBitmapPx,
config)` (companion, theo đúng pattern `buildTextBitmapShader`/`computeAutoContrastSampleRegion`
đã có — export đã tự do gọi các hàm companion này từ trước). `applyAutoContrastIfEnabled` giờ chỉ
tính `scaleToBitmap` rồi delegate. Wire vào 3 điểm vẽ layer chính trong `BatchExportEngine`:
`generateImage()` (isScale=false → `textSizeInBitmapPx = tmpConfig.textSize * imageInfo.scaleX`),
`generatePreviewBitmap()` + `generateCompareBitmaps()` (isScale=true → dùng thẳng `config.textSize`,
canvas chính là bitmap đang lấy mẫu). Cả 3 nơi dùng `mutableBitmap`/`watermarkedCopy` NGAY TRƯỚC
khi vẽ watermark (đã qua crop/rotate/redaction, đúng thứ tự preview) làm nguồn lấy mẫu màu.

- **Điểm tự audit:** 9/10 — đúng đề xuất ticket (tách hàm dùng chung, gọi từ đúng 3+1 nơi), không
  magic number mới, không leak/late/force-unwrap. Trừ 1 điểm: phát hiện thêm — qua debug thật —
  `Palette` KHÔNG tách được swatch cho bitmap 1-màu tuyệt đối (rơi fallback `Color.GRAY` y hệt cả
  Robolectric lẫn device thật), là giới hạn CÓ SẴN của chính thuật toán IDEA-06 gốc (không phải lỗi
  BUG-39 gây ra hay trong phạm vi sửa), nhưng đáng lẽ nên phát hiện/ghi chú sớm hơn thay vì assume
  Palette hoạt động tốt trên ảnh solid — mất thời gian debug thêm 1 vòng.
- **Test:**
  - Unit (Robolectric): `WaterMarkImageViewResolveAutoContrastRoboTest` (5 case: tắt/markMode
    Image/bitmap recycled → giữ nguyên config; case hợp lệ → sàn alpha luôn đúng, không phụ thuộc
    màu Palette trả về thật hay fallback). 11 test cũ `WaterMarkImageViewAutoContrastRoboTest`
    (helper thuần không đổi) vẫn PASS nguyên — không regression phần preview editor.
  - Integration (`app/src/androidTest`, thiết bị thật — Skia rasterize pixel thật, không dùng
    Robolectric vì Palette fallback GRAY cho bitmap solid-color y hệt cả 2 môi trường):
    `AutoContrastExportIntegrationTest` — 3 case qua `BatchExportEngine.generateList()` thật, decode
    lại file JPEG đã ghi đĩa: (1) chữ TRẮNG cấu hình trên ảnh nền theo tông TRẮNG (có texture nhẹ
    như ảnh thật, không solid tuyệt đối — Palette cần variance tối thiểu để không rơi fallback) →
    file xuất có pixel ĐỦ TỐI (auto-contrast đã đảo đen); (2) tương tự chiều ngược lại nền/chữ ĐEN
    → file xuất có pixel ĐỦ SÁNG; (3) tắt auto-contrast, chữ trùng tông nền → không pixel nào đủ tối
    (không regression, giữ đúng hành vi "hoà lẫn nền" như trước khi có BUG-39 fix). 3/3 PASS, chạy
    lại 3 lần liên tiếp không flaky.
  - `./gradlew :app:testDebugUnitTest` toàn bộ: 192/192 test suite thu thập được đều PASS (1 lần
    full-suite gặp `ToastExtensionWidgetTest` fail — xác nhận PASS riêng lẻ ngay sau đó, đúng
    flakiness cross-test JVM fork đã ghi ở `doc/todo.md`/nhiều ticket trước, không liên quan đợt
    sửa này).
- **Smoke test thật** trên device đã khoá session — **TECNO KJ7 (115333744A005844)**: push ảnh nền
  sáng có texture nhẹ (`bug39_texture_white.jpg`, giống ảnh thật, tránh giới hạn Palette solid-color
  vừa phát hiện) vào thiết bị, mở editor: (1) đặt màu chữ TRẮNG + bật "Tự động tương phản" → preview
  hiện chữ ĐEN rõ ràng (đúng đảo màu); (2) tắt cờ → chữ TRẮNG biến mất trên nền sáng (đúng hành vi
  cũ khi tắt); (3) bật lại → chữ ĐEN hiện lại; (4) mở "So sánh trước/sau" từ danh sách export preview
  (`ComparePreviewBottomSheetFragment`, dùng `generateCompareBitmaps()`) → chữ ĐEN hiện đúng, xác
  nhận AC3 (so sánh phản ánh đúng export) trên chính pipeline thật, không phải giả lập. Không gặp
  quảng cáo che UI (R4).
  - **Phát hiện thêm 1 vấn đề KHÔNG thuộc phạm vi BUG-39**: nút "Xuất vào bộ sưu tập"/"Chia sẻ"
    trong `SaveImageBSDialogFragment` giữ nguyên label/hành vi "Chia sẻ" (route sang `openShare()`)
    từ lần export THÀNH CÔNG trước đó trong cùng phiên `MainViewModel` (activity-scoped), dù vừa mở
    sheet với ảnh MỚI (danh sách 0/1) — bấm không kích hoạt export mới, không crash, không log lỗi.
    Không đụng tới file nào của BUG-39 (`WaterMarkImageView.kt`/`BatchExportEngine.kt`); root cause
    nằm ở `SaveImageBSDialogFragment.setUpLoadingView()`/`MainViewModel.saveResult` — đề xuất ticket
    riêng, đã báo user quyết định.
