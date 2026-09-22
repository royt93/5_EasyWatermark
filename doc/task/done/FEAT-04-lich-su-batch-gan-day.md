---
id: FEAT-04
type: Feature
effort: M
sources: Codex (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/data/db/
---

# Lịch sử batch export gần đây

## Mô tả
Thêm màn hình lịch sử các batch export gần đây — lưu cấu hình đã dùng, danh sách file output, và danh sách ảnh lỗi (liên quan `BUG-03` — cần biết chính xác ảnh nào fail) — cho phép mở lại thư mục, chia sẻ, hoặc chạy lại batch.

## Triển khai
Chỉ cần lưu metadata nhẹ (không nhân bản ảnh input): thêm bảng Room mới (vd `BatchHistory`) lưu timestamp, config snapshot, danh sách URI output + trạng thái từng ảnh.

## Acceptance Criteria
- [x] Sau mỗi batch export, có 1 entry lịch sử mới xuất hiện.
- [x] Mở lại entry cũ xem được danh sách ảnh output + ảnh lỗi (nếu có).
- [x] Có nút "chạy lại" áp dụng lại đúng config batch cũ.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-04`, file ticket = `todo/FEAT-04-lich-su-batch-gan-day.md`.

## Kết quả kiểm chứng
- DB riêng, ĐỘC LẬP với `AppDatabase` (asset-seeded, `ewm-db`) — `BatchHistoryDatabase` (version 1, không `createFromAsset`, file `batch-history-db`), `BatchHistoryEntity` + `BatchHistoryDao` (`getAll()` Flow ORDER BY timestamp DESC, `insert`, `deleteById`, `trimOldest(keepCount)`). Tránh mọi rủi ro migration lên asset DB có sẵn cho 1 tính năng hoàn toàn độc lập.
- `BatchHistoryEntity` lưu URI dạng chuỗi phân tách `\n` (cùng pattern `WaterMarkRepository.recentIconUris` — FEAT-24, không dùng bảng phụ/JSON) cho `inputUris` (toàn bộ batch, kể cả ảnh skip — dùng để khôi phục đúng khi "chạy lại"), `outputUris` (chỉ ảnh thành công), `failedInputUris` (chỉ ảnh lỗi) — cùng snapshot đầy đủ `ExportSettings` (format ordinal, compress level, resize, copyright, name pattern, conflict policy id, output directory uri) để "chạy lại" khôi phục ĐÚNG cấu hình đã dùng, không chỉ danh sách ảnh.
- `BatchHistoryRepository` (singleton, wrap DAO) — `record()` bỏ qua batch input rỗng (không phải 1 lần export thật), tự `trimOldest(MAX_HISTORY_ENTRIES = 20)` sau mỗi insert tránh phình DB vô hạn.
- `BatchExportWorker.doWork()` — sau `waterMarkRepo.updateImageList(finalList)`, gọi `recordHistory()`: `inputUris` lấy từ `infoList` GỐC (trước export, không phải `finalList`) vì ảnh skip (FEAT-17) giữ nguyên `jobState = Ready` ở cả 2 list nên không phân biệt được nếu dùng nhầm list; `outputUris` từ `finalList.mapNotNull { it.shareUri }`; `failedInputUris` từ ảnh có `jobState is JobState.Failure`.
- AC3 "chạy lại" — `BatchHistoryViewModel.restoreEntry()` (tách khỏi `rerun()` để test trực tiếp bằng `runBlocking`, không cần mock `viewModelScope`): decode `inputUris` → `ImageInfo` list → `waterMarkRepo.updateImageList()`; toàn bộ `UserConfigRepository` field khôi phục qua các hàm `updateXxx()` sẵn có — riêng `outputFormat` PHẢI qua `UserConfigRepository.resolveOutputFormat(ordinal, sdkInt)` (hàm đã có từ ENH-34, tránh `NoSuchFieldError` với `WEBP_LOSSY`/`WEBP_LOSSLESS` trên API <30), không tự decode ordinal thủ công. Không tự động export ngầm — không có `ViewInfo`/canvas sống ở màn hình lịch sử để derive watermark thật, nên chỉ khôi phục state rồi đưa user về editor tự xem lại + bấm Export (đúng nguyên tắc "review trước khi ghi đè file thật").
- UI: `BatchHistoryActivity` (Toolbar + RecyclerView + empty state, mirror `TextContentTemplateListFragment`) + `BatchHistoryAdapter` (`AsyncListDiffer`) + `item_batch_history.xml`/`activity_batch_history.xml`. Menu: `actionBatchHistory` (`showAsAction="never"`, trong overflow) + case trong `MainActivity.onOptionsItemSelected` mirror `actionSettings`/`actionVip`. Icon `ic_history` mới (chưa có sẵn trong repo).
- Test mọi tầng theo yêu cầu: `BatchHistoryRepositoryEncodingRoboTest` (encode/decode Uri thuần), `BatchHistoryRepositoryRoboTest` (record/delete với fake DAO — mirror `TemplateRepositoryCorruptedDbTest`, verify field mapping + trim + skip-khi-rỗng), `BatchHistoryViewModelRoboTest` (`restoreEntry()` với DataStore cô lập thật — verify toàn bộ config + ảnh khôi phục đúng, kể cả case `outputDirectoryUri = null` xoá đúng key), `BatchHistoryAdapterCountUrisTest` (đếm URI hiển thị summary), `BatchExportWorkerRoboTest` bổ sung assertion lịch sử (AC1: batch rỗng KHÔNG ghi lịch sử vì return sớm trước `recordHistory()`; batch lỗi vẫn ghi đúng `inputUris`/`failedInputUris`). `BatchHistoryDaoIntegrationTest` (**androidTest**, Room in-memory thật — mirror `TemplateDaoIntegrationTest`): insert/getAll order DESC, deleteById, trimOldest (đủ và thiếu dữ liệu).
- Vi phạm R3 phát hiện giữa chừng: `installDebug` không filter device cài nhầm lên cả TECNO (đã khoá) lẫn 1 Samsung SM-S928B vừa cắm vào máy. User xác nhận gỡ trên Samsung, chỉ giữ TECNO — đã gỡ thành công (`adb -s R5CX613VZBR uninstall`), xác nhận lại `adb devices -l` trước khi tiếp tục.
- Smoke test thật trên device khoá `118743744X002560` (TECNO BG6): chọn 2 ảnh → mở overflow menu thấy "Export history" đúng vị trí → màn hình rỗng đúng thiết kế trước khi export → Export 2 ảnh thành công (Export list 2/2, không crash, logcat sạch) → mở lại Export history: entry mới hiện đúng "2 success · 0 failed" (**AC1**) → bấm "View": dialog hiện đúng danh sách 2 ảnh thành công, "Failed (0)/None" (**AC2**) → bấm "Rerun" → dialog xác nhận → Confirm → quay về editor với toast "Restored, review and tap Export again", 2 ảnh gốc xuất hiện lại đúng trong filmstrip (**AC3**). Không có `FATAL EXCEPTION`/`AndroidRuntime` trong logcat suốt luồng.
- `./gradlew testDebugUnitTest` toàn bộ PASS (555 test, bao gồm test mới). `./gradlew ktlintCheck` sạch (cả main lẫn androidTest source set). `compileDebugAndroidTestKotlin` biên dịch sạch.
- Sửa thêm: bổ sung 16 string mới (`batch_history_*`) vào cả `values/strings.xml` và `values-vi/strings.xml` — `StringLocalizationParityTest` (test parity có sẵn trong repo) bắt lỗi thiếu bản dịch tiếng Việt ngay lần chạy suite đầu tiên, đã fix trước khi coi ticket hoàn tất.
- Audit: 9/10 — đủ 3 AC verify bằng smoke test thật (không chỉ UI, có kiểm logcat), test phủ đủ mọi tầng đổi (Entity/DAO/Repository/Worker/ViewModel/Adapter, cả Robolectric lẫn androidTest Room thật), tái dùng tối đa pattern đã có (`resolveOutputFormat`, Uri-list-as-string, `AsyncListDiffer`) thay vì viết lại; trừ điểm vì phát sinh 1 vi phạm R3 giữa chừng (dù đã tự phát hiện, báo cáo, và remediate đúng quy trình ngay lập tức) và vì UI thiết kế nhanh (chưa polish theo Material 3 đầy đủ như các màn hình cũ hơn trong app).
