---
id: FEAT-02
type: Feature
effort: S
sources: Codex, Claude, Agy (3/4 — đồng thuận cao)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# Naming template cho file xuất (tái dùng token có sẵn)

## Mô tả
`generateOutputName()` hiện luôn cố định dạng `ewm_${timestamp}.ext`, không tái dùng được `TextTokenResolver` đã có sẵn cho text watermark. Cho phép user đặt pattern tên file khi export, vd `{filename}_wm_{seq}`, `IMG_{index}`.

## Triển khai
Hạ tầng token (`{filename}`, `{seq}`, `{date}`...) đã tồn tại — chỉ cần thêm UI nhập pattern (trong `SaveImageBSDialogFragment`) và áp dụng resolver hiện có vào `generateOutputName()` thay vì hardcode.

## Acceptance Criteria
- [x] User nhập được pattern tên file trước khi export batch.
- [x] Token resolve đúng cho từng ảnh trong batch (không trùng tên khi có `{seq}`).
- [x] Giữ hành vi mặc định cũ nếu user không đổi pattern.

## Kết quả kiểm chứng (2026-09-10)
**Điểm audit: 10/10.**

Triển khai: `UserPreferences.outputNamePattern` (String, default "") + `UserConfigRepository.updateOutputNamePattern()`/`KEY_OUTPUT_NAME_PATTERN` (đúng pattern DataStore sẵn có của `copyright`/`maxLongEdge`). `MainViewModel.generateOutputName()` đổi `private` → `internal` (theo tiền lệ `buildExifBorderBitmap`, để test được), nhận thêm `contentResolver`/`imageInfo`/`index` (đã có sẵn trong scope `generateImage`) — pattern rỗng giữ nguyên hành vi cũ `ewm_{timestamp}`; pattern có giá trị **tái dùng thẳng `resolveTextTokens()`** (cùng resolver dùng cho text watermark) rồi nối đuôi file qua `trapOutputExtension()`. UI: `TextInputLayout` mới "File name pattern" trong `dlg_save_file.xml`, wire 2 chiều qua `etOutputName` giống hệt pattern `etCopyright` (set từ `shareViewModel.outputNamePattern`, lưu khi mất focus).

- **Unit test (5 test mới, `MainViewModelGenerateOutputNameRoboTest`, Robolectric)**: pattern rỗng/blank → tên mặc định; pattern có token → resolve đúng + nối đuôi; pattern không token → tên tĩnh vẫn có đuôi; 2 ảnh cùng batch dùng `{seq}` → tên khác nhau. `./gradlew testAppReleaseDebugUnitTest` — PASS toàn bộ suite.
- **ktlintCheck**: sạch cho `:app`; vi phạm còn lại thuộc `:cmonet`, có từ trước, ngoài phạm vi.
- **Smoke test thật trên Samsung SM-A507FN (`R58MA6WYRPE`)**: nhập pattern `{filename}-wm-{seq}` trong dialog Export (xác nhận qua `uiautomator dump` — field giữ đúng text, `focused=false` sau khi rời field nghĩa là đã lưu), đóng/mở lại dialog → pattern vẫn còn (persist qua DataStore đúng). Export 1 ảnh — file thật ghi ra tên **`ewm_1789046048995-wm-1.jpg`** (`{filename}` resolve đúng display-name gốc, `{seq}`=1 đúng vị trí ảnh đầu batch, đuôi `.jpg` đúng format JPEG). Logcat sạch `FATAL`/`AndroidRuntime` exception.

Đạt đủ 3 điều kiện Definition of Done → move `done/`.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-02`, file ticket = `todo/FEAT-02-naming-template-file-xuat.md`.
