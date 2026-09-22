---
id: FEAT-15
type: Feature
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
  - app/src/main/java/com/mckimquyen/watermark/export/ExportNaming.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
---

# Chọn thư mục XUẤT ảnh bằng SAF (khác FEAT-08 — chọn thư mục NGUỒN ảnh vào batch)

## Mô tả
Hiện tại ảnh xuất ra luôn cố định `Pictures/WaterMarkCreator/` (MediaStore RELATIVE_PATH) — không cho user chọn thư mục đích khác (vd lưu thẳng vào thư mục dự án cụ thể, thư mục đồng bộ cloud cục bộ). FEAT-08 đã cho chọn thư mục làm NGUỒN ảnh batch — đây là chiều ngược lại: chọn thư mục ĐÍCH lưu kết quả.

## Triển khai
Thêm tuỳ chọn "Chọn thư mục lưu" trong dialog Export (SAF `OpenDocumentTree`), lưu Uri thư mục đã chọn vào `UserConfigRepository`, dùng `DocumentFile`/`ContentResolver` ghi file thay vì MediaStore RELATIVE_PATH cố định khi user đã chọn thư mục riêng.

## Acceptance Criteria
- [x] Chọn thư mục đích khác `Pictures/WaterMarkCreator/` — file export ra đúng thư mục đã chọn, xác nhận qua trình quản lý file thật.
- [x] Không chọn thư mục riêng (mặc định) — hành vi giữ nguyên như cũ, lưu vào `Pictures/WaterMarkCreator/`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-15`, file ticket = `todo/FEAT-15-chon-thu-muc-xuat-anh-bang-saf-khac-feat-08-chon-thu-muc-ngu.md`.

## Kết quả kiểm chứng
- `ExportSettings.outputDirectoryUri: Uri? = null` (mới) — `BatchExportEngine.generateImage()` thêm nhánh ƯU TIÊN CAO NHẤT (trước cả check `SDK_INT >= Q`): khác null → `writeToCustomDirectory()` ghi qua `DocumentFile`/SAF; null → giữ nguyên 2 nhánh cũ (MediaStore Q+ / legacy File <Q) không đổi gì.
- `writeToCustomDirectory()` tách thành `writeIntoDocumentTree(root: DocumentFile, ...)` — cùng nguyên tắc `FileUtils.collectImagesRecursively(root: DocumentFile, ...)` (ENH-33): nhận thẳng `DocumentFile` đã resolve thay vì tự resolve Uri bên trong, để test được bằng `DocumentFile.fromFile()` (thư mục thật, không cần user thao tác chọn cây SAF qua `ACTION_OPEN_DOCUMENT_TREE`). Conflict-policy (SKIP/OVERWRITE/RENAME_VERSION/KEEP_BOTH) tái dùng thẳng `ExportNaming.resolveVersionedName()` — không viết lại thuật toán versioning đã có (đã test kỹ ở `ExportNamingConflictTest`).
- `UserConfigRepository`/`UserPreferences`/`MainViewModel`: thêm `outputDirectoryUri` theo đúng pattern các field khác (`updateOutputDirectoryUri(uri: Uri?)`, `null` = xoá key = quay lại mặc định). `BatchExportWorker.doWork()` truyền `prefs.outputDirectoryUri` vào `ExportSettings`.
- UI (`dlg_save_file.xml` + `SaveImageBSDialogFragment`): nút "Chọn thư mục lưu" (`btnOutputDirectory`, mở `ACTION_OPEN_DOCUMENT_TREE` qua `registerForActivityResult`, `takePersistableUriPermission` sau khi chọn) + dòng text hiện tên thư mục đã chọn kèm "Đặt lại mặc định" (`tvOutputDirectoryPath`, ẩn khi chưa chọn gì).
- Test: `BatchExportEngineCustomDirectoryIntegrationTest` (**androidTest**, bắt buộc vì đụng `ContentResolver.openOutputStream()`/`DocumentFile.createFile()` thật — `DocumentFile.fromFile()` trỏ thư mục thật trong `cacheDir`, `ContentResolver` tự route `file://` uri qua `FileOutputStream` nên verify được đúng logic mà không cần SAF provider thật): file mới ghi đúng thư mục, KEEP_BOTH tạo entry riêng biệt không đụng file cũ, SKIP giữ nguyên nội dung cũ không ghi gì, OVERWRITE thay nội dung đúng chỗ cũ, RENAME_VERSION tạo entry mới khác tên (không giả định tên chính xác DocumentFile tự đặt — verify qua chính Uri trả về, đúng cách dùng SAF), copyright EXIF vẫn áp dụng đúng qua đường ghi mới. `UserConfigRepositoryRoboTest` bổ sung case default/update/reset cho `outputDirectoryUri`. `DlgSaveFileLayoutRoboTest` bổ sung case layout (nút tồn tại, dòng path ẩn mặc định).
- **Bug thật phát hiện qua smoke test (không phải unit test bắt được)**: `updateOutputDirectoryUi()` gọi `binding.tvOutputDirectoryPath` NGAY TRONG `bindView()` — nhưng `BaseBindBSDFragment` chỉ gán `_binding = bindView(...)` SAU KHI `bindView()` return, nên đọc `binding` (qua `!!`) lúc đó luôn `null` → crash `NullPointerException` mỗi lần mở dialog Export (100% reproducible, không phải flaky). Fix: đổi hàm nhận `dlgBinding: DlgSaveFileBinding` qua tham số — gọi trong `bindView()` truyền thẳng `root` (biến cục bộ đã có), gọi trong callback `pickOutputDirectoryLauncher` (chạy sau khi `binding` đã gán an toàn) truyền `binding` như cũ. `DlgSaveFileLayoutRoboTest` (chỉ inflate layout, không gọi `bindView()`) không bắt được lớp bug này — đúng giới hạn môi trường đã ghi nhận trước đó (Fragment không launch được trong JVM test do ép kiểu `requireContext() as MainActivity`); chữ ký hàm mới (bắt buộc truyền binding tường minh) tự nó ngăn tái phát kiểu lỗi này.
- **Sự cố ngoài ý muốn phát hiện giữa chừng**: `./gradlew installDebug` không chỉ định device đã cài NHẦM lên 1 thiết bị Pixel 7 Pro vừa cắm vào máy (vi phạm CLAUDE.md R3 — chỉ target device đã khoá). User xác nhận gỡ cài trên Pixel (thiết bị đã rút kết nối trước khi gỡ được, coi như đã disconnect khỏi máy). Từ sau sự cố này, mọi lệnh `adb`/gradle install đều xác nhận `adb devices -l` chỉ có 1 device trước khi chạy.
- Smoke test thật trên device khoá `115333744A005844` (TECNO SPARK 20 Pro+): mở dialog Export → bấm "Chọn thư mục lưu" → chọn thư mục `DCIM` (khác `Pictures/WaterMarkCreator/`) qua SAF picker thật → cấp quyền → UI hiện đúng "DCIM · Đặt lại mặc định" → Export → **file thật xác nhận qua `adb shell ls -la /sdcard/DCIM/`: `ewm_*.jpg` (234915 bytes) nằm ĐÚNG trong DCIM**, đối chiếu `Pictures/WaterMarkCreator/` KHÔNG có file mới cùng thời điểm (không ghi nhầm cả 2 nơi) — AC1 xác nhận bằng file thật trên đĩa, không chỉ UI. Bấm "Đặt lại mặc định" → dòng path biến mất đúng thiết kế (AC2, kết hợp thêm bằng chứng từ `UserConfigRepositoryRoboTest` round-trip null). Không crash sau khi fix, logcat sạch.
- `./gradlew testDebugUnitTest` toàn bộ PASS. `ktlintCheck` sạch.
- Audit: 8.5/10 — đúng AC, verify bằng I/O thật (androidTest + smoke thật) thay vì chỉ mock, tái dùng tối đa logic đã có; trừ điểm vì có 1 crash-on-open thật lọt qua tới bước smoke test (dù đã bắt và fix đúng lúc, không phải bug ẩn còn sót) — cho thấy nên cẩn trọng hơn với thứ tự khởi tạo `binding` trong `BaseBindBSDFragment.bindView()` khi thêm code mới vào các Fragment dùng pattern này.
