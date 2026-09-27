---
id: IDEA-09
type: Idea
priority: P2
effort: L
sources: codex exec (external CLI, re-audit 2026-09-10)
files: []
---

# Watermark Survivability Preview — mô phỏng crop/recompress mạng xã hội

## Mô tả
Trước khi export, mô phỏng watermark trông ra sao sau khi ảnh bị các nền tảng xử lý lại (Facebook/Zalo/Instagram thường crop tỉ lệ, recompress mạnh, downscale) — chấm điểm mức độ watermark còn đọc được/nhìn thấy được sau các phép biến đổi này, tự đề xuất size/vị trí/opacity tối ưu hơn nếu điểm thấp.

## Vì sao đáng làm
Đây đúng use-case cốt lõi của app (bảo vệ ảnh khi chia sẻ) nhưng hiện tại người dùng chỉ thấy preview watermark trên ảnh gốc, không biết nó có "sống sót" nổi qua 1 lượt share Facebook/Zalo hay không cho tới khi tự kiểm chứng thủ công. Đã có sẵn preset resize theo nền tảng ([FEAT-09](FEAT-09-preset-resize-theo-nen-tang.md)) làm nền tảng dữ liệu về hành vi từng platform.

## Gợi ý triển khai (sơ bộ, cần thiết kế riêng)
- Bộ profile biến đổi giả lập cho từng nền tảng phổ biến (tỉ lệ crop, mức JPEG quality recompress, max resolution) — có thể ước lượng dựa trên thông tin công khai, không cần chính xác tuyệt đối.
- Áp transform giả lập lên canvas preview, tính điểm dựa trên contrast/kích thước watermark còn lại so với khung hình.

## Acceptance Criteria
- [x] Chọn 1 nền tảng mục tiêu → preview hiển thị watermark sau khi mô phỏng biến đổi của nền tảng đó.
- [x] Có điểm số/mức cảnh báo khi watermark dự đoán khó nhìn thấy sau biến đổi.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-09`, file ticket = `todo/IDEA-09-watermark-survivability-preview.md`.

---

## Kết quả kiểm chứng (2026-09-27)

### Đã làm
| File | Vai trò |
|---|---|
| `export/SurvivabilityProfile.kt` (mới) | Profile 3 nền tảng + toán crop/map + rule chấm điểm. Thuần Kotlin (không `RectF`) nên unit test chạy thẳng trên JVM. |
| `export/BatchExportEngine.kt` | Thêm `generateSurvivabilityPreview()` (crop → resize → nén JPEG → decode lại) + `PreviewResult.Success.watermarkRect`/`contrastRatio`. |
| `ui/MainViewModel.kt` | Wrapper 5 dòng cho UI. |
| `ui/dlg/SurvivabilityBottomSheetFragment.kt` + `res/layout/f_survivability_bottom_sheet.xml` (mới) | Bottom sheet M3: dropdown nền tảng, preview, badge PASS/WARN/FAIL, danh sách gợi ý sửa. |
| `dlg_save_file.xml` + `SaveImageBSDialogFragment.kt` | Nút entry "Kiểm tra độ sống sót" cạnh nút caption hàng loạt. |
| `values/strings.xml`, `values-vi/strings.xml` | 17 string mới (2 locale). |

Thiết kế theo lựa chọn của user: **bottom sheet riêng** + **có gợi ý sửa** (`MOVE_TOWARD_CENTER`,
`INCREASE_SIZE`, `INCREASE_OPACITY`, `INCREASE_CONTRAST`), không chỉ PASS/WARN/FAIL.

Tái dùng tối đa: `BrandComplianceScorer.NormalizedBox`/`Level`/`evaluate()` cho rule
opacity/contrast/edge/size (áp trên rect ĐÃ map qua crop), `applyCropAndRotate()`,
`OutputImageUtils.resizeIfNeeded()`, `generatePreviewBitmap()` — không viết lại pipeline vẽ.

### Test (18 test mới, tất cả xanh)
- 11 unit thuần — `test/.../export/SurvivabilityProfileTest.kt`: `centerCropBox` (ngang/dọc/vuông/đầu vào lỗi), `mapThroughCrop` (null khi ngoài crop, đúng toạ độ khi trong), `evaluate` (PASS giữa ảnh; FAIL `CROPPED_OUT` ở Instagram nhưng cùng vị trí đó KHÔNG fail ở Facebook; FAIL `TOO_SMALL_AFTER_DOWNSCALE`; alpha/contrast thấp sinh đúng suggestion; TILED bỏ qua rule vị trí; watermark quá lớn không gợi ý phóng to thêm).
- 4 Robolectric — `test/.../ui/dlg/SurvivabilityBottomSheetFragmentRoboTest.kt`: dropdown đủ 3 nền tảng + chọn sẵn cái đầu, mọi `Issue`/`Suggestion` đều có chuỗi hiển thị, hint đúng text, huỷ view không crash.
- 3 integration trên device thật — `androidTest/.../export/SurvivabilityTransformIntegrationTest.kt`: crop Instagram cho ảnh vuông và xoá được vùng sát mép (kiểm pixel), downscale + JPEG round-trip đúng kích thước/tỉ lệ, watermark sát mép phải bị chấm FAIL + gợi ý dời vào giữa.

`./gradlew :app:testDebugUnitTest` xanh toàn bộ, `./gradlew ktlintCheck` xanh,
`connectedDebugAndroidTest` xanh trên TECNO KJ7.

### Smoke test thật (TECNO KJ7 `115333744A005844`, R3)
Chọn 2 ảnh → editor → Export → nút "Kiểm tra độ sống sót" hiện đúng vị trí → sheet mở, dropdown đủ
Facebook/Instagram/Zalo → Facebook: badge "Watermark sống sót trên nền tảng này" (PASS) → Instagram:
preview đổi thành ảnh vuông đúng logic `centerCropBox`, vẫn PASS (watermark nằm giữa) → back về editor
không crash. `logcat` không có `FATAL EXCEPTION`/`OutOfMemoryError`/"recycled bitmap". Không gặp quảng
cáo che UI trong luồng test.

Case FAIL/`CROPPED_OUT` được phủ bằng integration test chạy trên chính device này
(`watermarkSatMepPhai_biChamDiemFailTrenInstagram`) thay vì dựng thủ công trong editor.

### Tự audit: 9.5/10
- Đúng cả 2 acceptance criteria, thêm phần gợi ý sửa mà user yêu cầu.
- R5: không magic number (mọi ngưỡng là `const` có tên + KDoc), không `late`/force-unwrap mới, bitmap
  được `recycle()` ở mọi nhánh (kể cả `CancellationException`), fragment cancel job + recycle bitmap
  trong `onDestroyView`, UI gán bitmap mới TRƯỚC khi recycle bản cũ.
- Không phá luồng khác: 2 field mới của `PreviewResult.Success` đều có default `null`.
- Trừ 0.5: tham số crop/quality mỗi nền tảng là ước lượng (đã ghi `ponytail:` comment + hint trong UI),
  và phần nén JPEG chạy trên bitmap preview nên artifact chỉ mang tính minh hoạ — điểm số không phụ
  thuộc vào đó (tính analytically từ kích thước gốc).
