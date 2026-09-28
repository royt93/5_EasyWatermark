---
id: BUG-44
type: Bug
priority: P1
effort: XS
sources: phát hiện qua smoke test thật khi làm BUG-39 (2026-09-28), điều tra + fix ngay theo yêu cầu user
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Nút export giữ nhầm trạng thái "Chia sẻ" cũ — không trigger export mới sau khi sửa cấu hình

## Mô tả
Phát hiện ngoài kế hoạch trong lúc smoke test BUG-39: sau 1 lần export THÀNH CÔNG, sửa cấu hình
watermark (đổi màu/toggle...) rồi mở lại `SaveImageBSDialogFragment` cho ảnh MỚI — nút đáy sheet
vẫn hiện "Chia sẻ" (label của trạng thái `TYPE_JOB_FINISH`) thay vì "Xuất vào bộ sưu tập", bấm vào
không làm gì (route sang `openShare()` thay vì `saveImage()`), không crash, không log lỗi.

**Root cause** (xác nhận qua đọc code + test tái hiện, không đoán):
`MainActivity` gọi `viewModel.resetJobStatus()` mỗi khi cấu hình watermark đổi (đúng, xoá
`saveResult` về trạng thái trung lập). Nhưng `SaveImageBSDialogFragment.onViewCreated()` luôn gọi
`shareViewModel.reattachExportWorkIfRunning()` (ENH-01 AC1 — bắt lại work đang chạy nếu app từng bị
kill giữa chừng) TRƯỚC khi đọc `saveResult.value`. Hàm này re-subscribe LiveData
`WorkManager.getWorkInfosForUniqueWorkLiveData("batch_export")` — WorkManager PERSIST WorkInfo đã
FINISHED (SUCCEEDED) của lần export trước và REPLAY NGAY giá trị đó cho observer MỚI dù chưa có
work nào enqueue lại — nhánh `info.state.isFinished` trong `observeExportWork()` ghi đè
`saveResult` về `TYPE_JOB_FINISH` một lần nữa, xoá mất `resetJobStatus()` vừa chạy.

Nhánh RUNNING (`ENQUEUED/RUNNING/BLOCKED`) đã có guard idempotent
(`if (saveResult.value?.code != TYPE_SAVING)`), nhánh FINISHED thì chưa — đây chính là lỗ hổng.

## Cách fix
Thêm `MainViewModel.lastHandledFinishedWorkId: UUID?` — ghi nhớ id WorkInfo FINISHED đã xử lý lần
gần nhất, bỏ qua (không ghi `saveResult`) nếu observer mới thấy LẠI đúng id đó (WorkManager replay,
không phải hoàn thành mới). Không phá vỡ AC1 gốc (ViewModel MỚI hoàn toàn có `lastHandledFinishedWorkId
= null` → vẫn bắt đúng work cũ khi app bị kill giữa chừng rồi mở lại).

## Acceptance Criteria
- [x] Sau export thành công → sửa cấu hình → mở lại sheet export cho ảnh mới: nút hiện "Xuất vào bộ
  sưu tập" (không phải "Chia sẻ"), bấm vào trigger export thật.
- [x] Không phá ENH-01 AC1: ViewModel MỚI (app bị kill giữa chừng) vẫn bắt đúng work đã chạy nền.
- [x] Unit test tái hiện đúng bug (RED trước khi fix) + verify fix (GREEN).

## Kết quả kiểm chứng (2026-09-28)

- **Điểm tự audit:** 9.5/10 — root cause xác nhận bằng đọc code + test tái hiện thật (không đoán
  mò), fix tối thiểu (+16 dòng), theo đúng pattern idempotent guard đã có sẵn ở nhánh RUNNING cùng
  hàm, không magic number/leak.
- **Test (TDD, RED→GREEN):** `MainViewModelSaveImageImmutabilityRoboTest.reattachExportWorkIfRunning_afterResetJobStatus_doesNotReplayStaleFinishedWork`
  — mô phỏng đúng chuỗi sự kiện thật (export xong → `resetJobStatus()` → `reattachExportWorkIfRunning()`
  lại), verify `saveResult` không bị replay ngược. RED xác nhận fail đúng dòng assertion trước khi
  fix; GREEN sau khi thêm guard. 4 test cũ cùng file (`reattachExportWorkIfRunning_onFreshViewModel_...`,
  `cancelSaveImage_...`, `onCleared_...`, `saveImage_decodeFailure_...`) vẫn PASS — không regression
  ENH-01 AC1/AC2. `./gradlew :app:testDebugUnitTest` toàn bộ 192/192 PASS (1 lần gặp
  `saveImage_decodeFailure_neverMutatesOriginalImageInfo_postsImmutableCopies` fail trong full-suite
  — xác nhận PASS riêng lẻ ngay sau, đúng flakiness cross-test JVM fork đã ghi ở `doc/todo.md`,
  KHÁC test mới thêm cho BUG-44 này, không liên quan).
- **Smoke test thật** trên device đã khoá session — **TECNO KJ7 (115333744A005844)**: dựng đúng
  chuỗi sự kiện thật trên app (không phải mô phỏng) — mở app fresh (nút hiện "Chia sẻ" đúng ENH-01
  vì có WorkInfo SUCCEEDED cũ từ phiên trước) → đổi màu watermark → mở lại sheet export (KHÔNG
  restart app) → nút đổi đúng thành "Xuất vào bộ sưu tập" → bấm → verify `content query` MediaStore
  tăng đúng 1 row (845→846), ảnh export thật xuất hiện, `DANH SÁCH XUẤT (1/1)` checkmark đúng.
  Logcat sạch, không `FATAL EXCEPTION` suốt phiên.
