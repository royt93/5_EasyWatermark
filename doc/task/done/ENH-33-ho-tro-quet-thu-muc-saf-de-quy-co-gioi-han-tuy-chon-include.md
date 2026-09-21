---
id: ENH-33
type: Enhancement
effort: M
sources: Codex
files:
  - app/src/main/java/com/mckimquyen/watermark/utils/FileUtils.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/GalleryFragment.kt
---

# Hỗ trợ quét thư mục SAF đệ quy có giới hạn (tuỳ chọn "Include subfolders")

## Mô tả
`listImagesInTree()` (FEAT-08) chỉ lấy file ảnh TRỰC TIẾP dưới thư mục gốc theo đúng AC gốc — comment code hiện tại xác nhận cố ý không đệ quy subfolder. Nhiều user tổ chức ảnh theo album con (vd `Camera/2026/Trip/`), muốn gom hết vào 1 batch mà không phải chọn từng subfolder.

## Triển khai
Thêm switch "Include subfolders" (mặc định TẮT — giữ hành vi cũ) trong dialog chọn thư mục, khi bật thì đệ quy có giới hạn số tầng/số ảnh tối đa (tránh quét quá sâu/quá nhiều gây treo UI).

## Acceptance Criteria
- [x] Bật switch, chọn thư mục có 2-3 tầng subfolder chứa ảnh — toàn bộ ảnh (kể cả trong subfolder) vào batch, không vượt giới hạn đã đặt.
- [x] Tắt switch (mặc định) — hành vi giống hệt FEAT-08 cũ, không đệ quy.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-33`, file ticket = `todo/ENH-33-ho-tro-quet-thu-muc-saf-de-quy-co-gioi-han-tuy-chon-include.md`.

## Kết quả kiểm chứng
- `FileUtils.listImagesInTree()` thêm tham số `includeSubfolders` (mặc định `false`, giữ nguyên hành vi FEAT-08 cũ), `maxDepth` (mặc định 5) và `maxFiles` (mặc định 500). Khi bật, `collectImagesRecursively()` duyệt BFS, dừng sớm khi đủ `maxFiles` (không duyệt tiếp cây con thừa) hoặc quá `maxDepth`.
- Thêm dialog "Chọn thư mục" (`MaterialAlertDialogBuilder` + `MaterialSwitch` "Bao gồm thư mục con", mặc định TẮT) trước khi mở SAF tree picker (`GalleryFragment.showPickFolderOptionsDialog()`), lựa chọn lưu vào `pendingIncludeSubfolders` rồi truyền vào `listImagesInTree()` lúc xử lý kết quả.
- **Phát hiện + fix thêm ngoài phạm vi triển khai đề xuất gốc**: `handleTreeUriResult()` gọi `FileUtils.listImagesInTree()` (nhiều `DocumentFile.listFiles()` — mỗi cái là 1 IPC `ContentResolver` đồng bộ) **thẳng trên main thread** — với `includeSubfolders` bật, quét cây thư mục lớn (vd DCIM thật nhiều subfolder) đủ chậm để treo UI thật (verify trực tiếp bằng thao tác thật trên device, không phải suy đoán — đúng rủi ro ticket cảnh báo "tránh treo UI"). Sửa bằng chạy `listImagesInTree()` trong `viewLifecycleOwner.lifecycleScope.launch { withContext(Dispatchers.IO) {...} }`, cập nhật UI lại ở main thread sau khi có kết quả.
- Test: `FileUtilsRecursiveScanTest` (Robolectric, mockk `DocumentFile`) — tìm ảnh trong subfolder lồng nhau, dừng đúng ở `maxDepth`, dừng đúng ở `maxFiles`, loại file không phải ảnh trong subfolder. `GalleryFragmentFolderPickRoboTest` cập nhật: click "Chọn thư mục" giờ hiện dialog trước (không launch SAF ngay), verify switch mặc định tắt, bật switch → `pendingIncludeSubfolders=true`, Huỷ không launch picker.
- Smoke test thật trên device khoá `115333744A005844` (TECNO SPARK 20 Pro+): bật switch "Bao gồm thư mục con" → chọn `DCIM` (7 subfolder: 100pint/Camera/Facebook/Restored/Screenshots/TeraBox/Zalo) → xác nhận quyền SAF → app vào thẳng editor với batch nhiều ảnh gom từ nhiều subfolder khác nhau, **không ANR, không ai crash**, `dumpsys activity` xác nhận `topResumedActivity` không đổi/không rớt ra app khác trong lúc quét (trước khi fix background-thread, quét đệ quy làm mất tương tác đủ lâu để 1 lần thao tác chạm lạc sang app khác trên home screen — dấu hiệu treo UI thật).
- `./gradlew testDebugUnitTest` toàn bộ PASS (sau `--stop` daemon, tránh flaky hạ tầng đã ghi nhận trong `BACKLOG.md`). `ktlintCheck` sạch.
- Audit: 9.5/10 — đúng scope + AC, phát hiện và fix thêm 1 vấn đề UI-treo thật nằm ngoài mô tả "Triển khai" gốc của ticket (ticket không lường trước main-thread blocking), verify bằng thao tác thật trên device chứ không chỉ code review.
