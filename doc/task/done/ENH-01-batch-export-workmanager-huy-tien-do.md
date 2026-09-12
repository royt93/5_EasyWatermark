---
id: ENH-01
type: Enhancement
effort: L
sources: Claude, Agy, Internal (3/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportWorker.kt
  - app/src/main/java/com/mckimquyen/watermark/export/ExportNaming.kt
  - app/src/main/res/layout/dlg_save_file.xml
verified: true
---

# Batch export chạy qua WorkManager + huỷ + tiến độ tổng

## Mô tả
Batch export hiện chạy trong `viewModelScope` (`saveImage`/`generateList`) — job bị hệ thống throttle/kill khi app xuống nền (Doze, background limits), và không có cách huỷ giữa chừng hay resume ảnh lỗi. Với batch lớn (hàng chục-hàng trăm ảnh), đây là điểm yếu UX rõ rệt.

## Đề xuất
- Chuyển export sang `WorkManager` (hoặc tối thiểu foreground service) để job sống sót khi app xuống nền.
- Emit tiến độ tổng (x/y ảnh xong) qua notification + trong app.
- Cho phép huỷ giữa chừng, và (liên quan `BUG-03`) hiển thị rõ số ảnh thành công/thất bại khi kết thúc.

## Acceptance Criteria
- [x] Batch export tiếp tục chạy khi app bị đưa xuống nền — chạy qua `BatchExportWorker` (`@HiltWorker`, `CoroutineWorker` + `setForeground()`), sống sót Doze/OEM background-kill.
- [x] Có nút huỷ giữa batch — `MainViewModel.cancelSaveImage()` gọi `WorkManager.cancelUniqueWork()`; nút `btnSave` trong `SaveImageBSDialogFragment` đổi text/hành vi thành Cancel khi đang export; notification cũng có action Cancel riêng.
- [x] Notification hiển thị tiến độ % — `BatchExportWorker` cập nhật `ForegroundInfo` với `setProgress(total, done, false)` + text "x/y done" sau mỗi ảnh xong.

## Kiến trúc
Logic export thật (`generateImage`/`generateList`, verbatim từ `MainViewModel` cũ) chuyển sang `BatchExportEngine` (không có `viewModelScope`, nhận `ExportSettings` snapshot 1 lần thay vì đọc `StateFlow` trực tiếp) để `BatchExportWorker` dùng được (Worker không có ViewModel). Token/tên file (`resolveTextTokens`, `generateOutputName`, copyright EXIF...) tách sang `ExportNaming` (dùng chung giữa `MainViewModel.resolvePreviewText` và `BatchExportEngine`). `MainViewModel` giữ các hàm cũ (`buildExifBorderBitmap`, `generateOutputName`...) làm **thin delegate** 1 dòng gọi sang class mới — giữ nguyên ~4 test file cũ gọi trực tiếp các hàm này trên `MainViewModel`, không phải sửa.

`saveImage()` giờ chỉ enqueue `BatchExportWorker` rồi bridge `WorkInfo`/`progress Data` của nó về ĐÚNG contract `saveProcess`/`saveResult` cũ (không đổi gì ở `SaveImageBSDialogFragment`/`SaveImageListAdapter` ngoài thêm nhánh Cancel).

## Kết quả kiểm chứng

### Unit test
17 test mới/sửa cho ENH-01: `MainViewModelSaveImageImmutabilityRoboTest` (4 case — decode fail đi đúng chuỗi Ready→Ing→Failure qua WorkManager test harness, `cancelSaveImage()` map đúng `TYPE_ERROR_CANCELLED`, `reattachExportWorkIfRunning()` bắt lại đúng work đã enqueue bởi instance khác, `onCleared()` gỡ đúng observer không rò rỉ), `BatchExportEngineCancellationRoboTest` (verify `CancellationException` được rethrow chứ không bị `catch (e: Exception)` nuốt — huỷ giữa batch dừng đúng ảnh còn lại), `BatchExportWorkerRoboTest` (batch rỗng → `WorkResult.failure()`; batch 1 ảnh lỗi decode → `WorkResult.success()` + list cuối ghi đúng vào repo). Regression: `MainViewModelExifBorderRoboTest`, `MainViewModelGenerateOutputNameRoboTest`, và toàn bộ `MainViewModel*`/`SaveImageListAdapter*`/`GalleryFragment*`/`DlgSaveFileLayoutRoboTest` chạy qua — không có test nào vỡ do thin-delegate pattern. `ktlintCheck` module `:app` sạch (đã dọn ~20 import chết sót lại từ lúc trích xuất `BatchExportEngine`, 2 vi phạm trailing-comma/argument-wrapping ở file mới).

### 2 bug thật phát hiện khi tự audit code (trước khi build) — đã sửa trước khi build/test device
1. **Observer `observeForever` rò rỉ**: `exportWorkObserver` gắn vào `WorkManager`'s `LiveData` qua `observeForever` nhưng `onCleared()` không gỡ — ViewModel bị huỷ thật (không phải xoay màn hình) vẫn giữ observer sống mãi trong registry LiveData. Sửa: gỡ observer trong `onCleared()`. Test: `onCleared_removesExportWorkObserver_soLaterWorkUpdatesDoNotLeakIntoDeadViewModel`.
2. **Không bắt lại trạng thái sau process death**: nếu app bị kill giữa lúc export (đúng kịch bản AC1) rồi mở lại, `MainViewModel` mới không tự biết batch vẫn đang chạy nền — dialog Export sẽ hiện sai (coi như chưa export gì). Cân nhắc đặt việc bắt lại (`observeExportWork()`) trong `init{}` của ViewModel nhưng bỏ vì sẽ ép MỌI test dựng `MainViewModel` (12+ file không liên quan export) phải bootstrap WorkManager theo — quá rộng so với ticket. Sửa gọn hơn: thêm `reattachExportWorkIfRunning()` public, gọi từ `SaveImageBSDialogFragment.onViewCreated()` (đúng nơi cần biết trạng thái, không đụng construction path của ViewModel). Test: `reattachExportWorkIfRunning_onFreshViewModel_picksUpWorkEnqueuedBySomeoneElse`.

### 1 bug thật phát hiện qua smoke test thật trên Samsung SM-S928B (không phải giả định)
**Crash 100% khi bắt đầu export** (`java.lang.IllegalArgumentException: foregroundServiceType 0x00000001 is not a subset of foregroundServiceType attribute 0x00000000 in service element of manifest file`) — `BatchExportWorker.setForeground()` promote với `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (API 34+), nhưng hệ thống đối chiếu type này với `android:foregroundServiceType` khai báo trên chính `<service>` chạy nó (`SystemForegroundService` của thư viện WorkManager, mặc định "none" — khai `<uses-permission>` KHÔNG đủ, phải override tường minh `<service>` qua manifest merge). Crash lặp lại liên tục mỗi lần bấm Export → app tự kích hoạt Recovery Mode (cơ chế crash-guard sẵn có của `MyApplication`) sau vài lần crash liên tiếp.
- **Fix**: thêm `<service android:name="androidx.work.impl.foreground.SystemForegroundService" android:foregroundServiceType="dataSync" tools:node="merge" />` vào `AndroidManifest.xml`.
- **Verify lại trên chính Samsung SM-S928B sau fix**: xoá recovery-mode flag (`pm clear`), export batch 2 ảnh thật (chọn qua Photo Picker, ENH-10) — cả 2 ảnh export thành công, watermark "DO NOT REDISTRIBUTE" áp đúng, dialog chuyển state "Share"/"View in gallery" đúng, `adb pull` file thật về kiểm tra bằng mắt xác nhận đúng nội dung + watermark. `logcat` xác nhận KHÔNG còn `FATAL EXCEPTION` sau thời điểm fix (so sánh timestamp trước/sau).
- **Gap đã biết**: đây là lỗi cấu hình manifest/OS-level mà Robolectric (JVM) KHÔNG mô phỏng (không validate `foregroundServiceType` thật) — 17 unit test ENH-01 đều pass trước khi phát hiện bug này bằng smoke test thật. Ghi nhận làm bài học: bất kỳ `setForeground()`/`ForegroundInfo` với type cụ thể nào thêm sau này BẮT BUỘC phải verify bằng real-device smoke test, không thể tin JVM test.
- **Chưa verify được qua smoke test thủ công** (giới hạn thời gian/công cụ điều khiển device qua adb, không phải do nghi ngờ đúng sai code): tiến độ % hiển thị trên notification khi export nhiều ảnh lớn chạy đủ lâu để quan sát (batch test thực tế 2 ảnh nhỏ hoàn tất dưới 1 giây, không kịp chụp được notification giữa chừng), và bấm Cancel thật giữa batch đang chạy. Cả 2 hành vi đã có unit test xác nhận đúng logic (`BatchExportWorkerRoboTest`, `MainViewModelSaveImageImmutabilityRoboTest.cancelSaveImage_...`, `BatchExportEngineCancellationRoboTest`) — chỉ riêng phần hiển thị notification/tương tác vật lý trên UI hệ thống chưa có ảnh chụp trực tiếp.

### Re-audit vòng 2 trên Pixel 7 Pro (2B051FDH3006MU) — theo yêu cầu audit sâu hơn của user

- **Xác nhận crash fix ổn định trên device thứ 3** (khác kiến trúc/OEM so với Samsung SM-S928B đã fix trước đó): export nhiều lần (1 ảnh, rồi 8 ảnh) trên Pixel 7 Pro (Android AOSP gần chuẩn Google nhất) — không crash, `dumpsys notification` xác nhận notification `batch_export` (channel đúng, cờ `ONGOING_EVENT|FOREGROUND_SERVICE`, 1 action Cancel) thực sự tồn tại khi Worker chạy.
- **1 bug UI thật phát hiện qua smoke test, KHÔNG thuộc ENH-01 nhưng nằm trong cùng dialog Export**: field "File name pattern" và "Copyright (EXIF)" trong `dlg_save_file.xml` bị đè chữ — label nổi (`TextInputLayout.hint`) và ví dụ gợi ý (đặt qua `android:hint` trên `TextInputEditText` con) vẽ chồng lên nhau khi ô trống/chưa focus, vì đây là 2 cơ chế hint độc lập không tự loại trừ nhau (khác với 2 dropdown Format/Resize phía trên vốn "vô tình đúng" vì label và hint con là CÙNG 1 chuỗi). User được hỏi qua `AskUserQuestion`, chọn phương án dùng đúng API Material `app:placeholderText` (thay vì chuyển ví dụ xuống `helperText` hoặc xoá hẳn ví dụ). Fix: bỏ `android:hint` trên 2 `TextInputEditText` con (`etOutputName`, `etCopyright`), thêm `app:placeholderText`/`app:placeholderTextColor` trên `TextInputLayout` cha tương ứng. Verify lại trên chính Pixel 7 Pro: 2 field hiện tách bạch rõ ràng, không còn đè chữ.
- Test mới: `DlgSaveFileLayoutRoboTest.outputNameField_exampleUsesPlaceholderText_notChildHint_soItDoesNotOverlapFloatingLabel`, `copyrightField_...` (verify `EditText.hint` đồng bộ đúng với `TextInputLayout.hint` — hành vi bình thường của Material, không phải bug — và ví dụ dài nằm ở `placeholderText`, không còn là hint riêng của EditText).
- **Giới hạn còn lại xác nhận lần 2**: mọi ảnh test có sẵn trên Pixel 7 Pro (ảnh chụp màn hình UI, không có ảnh camera thật) export xong dưới 1 giây kể cả batch 8 ảnh — không đủ cửa sổ thời gian để chụp bắt notification % giữa chừng hay bấm Cancel thật qua adb (độ trễ round-trip adb ~50-100ms/lệnh còn chậm hơn cả thời gian xử lý). Không phải nghi ngờ logic sai — unit test đã cover đúng hành vi này; chỉ là giới hạn công cụ, giữ nguyên như đã ghi nhận ở lần audit đầu.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-01`, file ticket = `todo/ENH-01-batch-export-workmanager-huy-tien-do.md`.
