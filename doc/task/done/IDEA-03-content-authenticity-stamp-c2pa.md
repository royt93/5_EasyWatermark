---
id: IDEA-03
type: Idea
effort: L
sources: Codex, Claude, Internal (3/4)
files: []
---

# Content authenticity stamp kiểu C2PA

## Mô tả
Mỗi ảnh xuất ra chứa watermark hiển thị bình thường KÈM payload xác minh (chữ ký số/hash) nhúng vào EXIF/metadata, liên kết hash ảnh gốc + thời gian + chủ sở hữu — theo hướng chuẩn C2PA (Content Credentials) đang được ngành nhiếp ảnh/báo chí quan tâm để chống ảnh giả mạo/AI-generated. Người xem/1 trang scanner có thể kiểm tra ảnh đã bị chỉnh sửa sau khi phát hành hay chưa.

## Vì sao đáng làm
Hợp lý với đối tượng nhiếp ảnh gia đang lo bị đánh cắp ảnh cho AI training hoặc bị chỉnh sửa giả mạo — xu hướng ngành đang đi theo hướng này (Adobe, Leica, Nikon đã hỗ trợ C2PA phần cứng/phần mềm).

## Triển khai (gợi ý sơ bộ)
- Nhúng metadata chuẩn (hoặc rút gọn, không cần full C2PA spec phức tạp ban đầu) vào EXIF/XMP khi export.
- Cân nhắc dùng key ký cục bộ trên máy (không cần server) cho bản MVP, nâng cấp lên có server xác thực sau nếu cần độ tin cậy cao hơn.

## Acceptance Criteria (sơ bộ)
- [x] Ảnh export chứa metadata xác thực (hash + timestamp + owner ID) đọc lại được.
- [x] Có cách kiểm tra (trong app hoặc công cụ riêng) xác minh ảnh chưa bị chỉnh sửa kể từ lúc xuất.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `IDEA-03`, file ticket = `todo/IDEA-03-content-authenticity-stamp-c2pa.md`.

## Kết quả kiểm chứng (2026-09-27)

### Vấn đề cốt lõi phải giải trước khi code

**Hash cả file không dùng được.** Pipeline ghi EXIF SAU khi file đã ghi xong (3 điểm gọi
`ExportNaming.applyCopyrightExif` trong `BatchExportEngine`). Hash cả file rồi ghi hash đó vào EXIF
thì chính hành động ghi làm file đổi — hash tự phủ định mình.

**Hash pixel đã decode cũng không dùng được.** `BitmapFactory` decode qua Skia; phiên bản Skia khác
nhau giữa các đời Android cho pixel lệch nhau — ảnh xuất máy này verify máy kia sẽ báo sai.

**Cách đã chọn:** hash phần dữ liệu ảnh của JPEG, BỎ QUA segment `APPn` (FFE0–FFEF) và `COM` (FFFE)
— đúng những segment việc ghi EXIF đụng vào. Ổn định mức byte, không cần decoder, không phụ thuộc
đời máy. Hệ quả phạm vi: **chỉ JPEG** (PNG đã bị `supportsExifWrite` loại sẵn; WEBP cấu trúc chunk
khác). Switch tự đổi mô tả giải thích khi format không phải JPEG.

### File đã sửa/thêm

| File | Vai trò |
|---|---|
| `export/JpegImageDigest.kt` (mới) | Hash SHA-256 phần dữ liệu ảnh, bỏ qua APPn/COM. Thuần JVM. |
| `export/AuthenticityStamp.kt` (mới) | Format `EWM1\|hash\|ts\|owner(b64)\|pubkey(b64)\|sig(b64)` + parse + `signedPayload`. |
| `utils/AuthenticityKeyStore.kt` (mới) | Bọc Android Keystore: EC P-256, ký/xác minh, vân tay khoá. |
| `export/AuthenticityVerifier.kt` (mới) | Đọc EXIF → parse → hash lại → xét chữ ký rồi mới xét hash. |
| `export/ExportNaming.kt` | +`applyAuthenticityExif` 2 overload (Uri/filePath), ghi `TAG_USER_COMMENT`. |
| `export/BatchExportEngine.kt` | +`authenticityStamp` vào `ExportSettings`, gọi ở đúng 3 điểm sau `applyCopyrightExif`. |
| `export/BatchExportWorker.kt`, `data/model/UserPreferences.kt`, `data/repo/UserConfigRepository.kt` | Cờ bật/tắt theo khuôn `proofingMode`. |
| `ui/dlg/SaveImageBSDialogFragment.kt` + `res/layout/dlg_save_file.xml` | Switch "Nhúng con dấu chứng thực" + mô tả. |
| `ui/about/AboutActivity.kt`, `AboutViewModel.kt`, `res/layout/a_about.xml`, `drawable/ic_verified_shield.xml` | Hàng "Kiểm tra chứng thực ảnh" + dialog kết quả. |
| `res/values/strings.xml`, `values-vi/strings.xml` | 19 string EN + VI. |

### Thiết kế đáng lưu ý

- **Con dấu mang theo KHOÁ CÔNG KHAI, không chỉ vân tay.** Thiếu khoá thì máy khác không xác minh nổi
  chữ ký, mà so vân tay suông thì vô nghĩa (chép lại vân tay là xong). Vân tay hiển thị được tính lại
  từ chính khoá đó nên không thể mâu thuẫn với nó.
- **Xét chữ ký TRƯỚC, hash sau.** Chữ ký hỏng = chính con dấu bị can thiệp, khi đó hash trong con dấu
  do kẻ sửa tự điền nên khớp hay không cũng vô nghĩa. Ràng buộc này có test khoá lại.
- **Ký thất bại thì KHÔNG ghi con dấu** (`buildStampComment` trả `null`) — thà không có con dấu còn
  hơn ghi một con dấu không ký được mà người xem tưởng đã xác thực.
- `@Synchronized` khi lấy/sinh khoá: batch export chạy song song, 2 thread cùng sinh khoá sẽ ghi đè
  alias làm chữ ký lệch với khoá công khai đã nhúng.

### Giới hạn đã biết (không giấu)

- **Chỉ JPEG.** Lý do ở phần đầu. PNG/WEBP bỏ qua im lặng, mô tả trong UI nói rõ.
- **Không có PKI.** Chữ ký chứng minh "ảnh chưa đổi kể từ lúc ký" và "ký bởi khoá có vân tay X"; nó
  KHÔNG tự chứng minh X là ai. Mạnh nhất khi verify ảnh của chính máy mình — UI phân biệt đúng 2 mức
  ("Ký bằng khoá của chính thiết bị này" vs "Ký bằng khoá của thiết bị khác…").
- Khoá Keystore mất khi gỡ app/đổi kiểu khoá màn hình → ảnh cũ mất vế "đúng khoá máy này", phần hash
  vẫn verify được.
- Đây là **C2PA-lite theo tinh thần**, không phải manifest C2PA đúng chuẩn (cần JUMBF box + chuỗi
  chứng thư) — đúng như ticket cho phép ở "Triển khai (gợi ý sơ bộ)".

### Test

- **Unit thuần** `JpegImageDigestTest` (8 case): quan trọng nhất là `hashGiuNguyenKhiApp1DoiDoDai` —
  APP1 dài 0/16/5000 byte cho cùng một hash, đúng lý do tồn tại của cả file. Kèm: hash đổi khi entropy
  data đổi, COM cũng bị bỏ qua, không phải JPEG → `null`, file cụt → `null` không treo vòng lặp, độ
  dài segment vô lý → `null`.
- **Robolectric** `AuthenticityStampTest` (11 case): round-trip, owner chứa `|`, owner tiếng Việt có
  dấu, chữ ký rỗng, sai version/thiếu trường/timestamp hỏng/chuỗi rác → `null`, `signedPayload` không
  chứa chữ ký và giống nhau trước/sau khi ký.
- **Robolectric** `AuthenticityVerifierEvaluateRoboTest` (5 case): khoá chặt "chữ ký không hợp lệ thì
  LUÔN không nguyên vẹn, kể cả khi hash khớp" — đảo thành `intact = hashMatches` là test đỏ ngay.
- **Robolectric** `SaveImageBSDialogFragmentAuthenticityRoboTest` (4 case) + `UserConfigRepositoryRoboTest`
  (+1 case mặc định tắt/bật/tắt).
- **Integration (androidTest, Keystore + Skia + ExifInterface thật)** `AuthenticityStampIntegrationTest`
  **10 case**: ảnh vừa đóng dấu → nguyên vẹn + đúng khoá máy này; sửa ảnh nén lại → hash lệch, FAIL,
  nhưng chữ ký vẫn đúng (phân biệt được 2 ca); **ghi đè EXIF Copyright sau khi đóng dấu → VẪN nguyên
  vẹn** (khoá lại mục tiêu thiết kế); sửa nội dung con dấu → chữ ký hỏng; PNG → bỏ qua, không ném;
  khoá thiết bị ổn định giữa các lần gọi; ký+verify khoá thật; đọc con dấu qua `InputStream` khớp
  đường `filePath`; `verify()` qua Uri thật → nguyên vẹn.
  → **Chạy trên device thật: OK (10 tests)**.
- `./gradlew :app:testDebugUnitTest` (toàn bộ 765 test): **XANH**. `./gradlew ktlintCheck`: **XANH**.

### Smoke test thật (device đã khoá session — TECNO KJ7, serial `115333744A005844`)

Mọi lệnh đều `adb -s 115333744A005844`; Pixel 7 Pro tuy cắm nhưng không nhận lệnh nào (R3).

- Export: editor → ⋮ → Lưu → sheet hiện switch **"Nhúng con dấu chứng thực"** ngay dưới switch proofing,
  mô tả "Ký từng ảnh xuất ra… Chỉ áp dụng cho JPEG." → bật (`checked=true`, proofing vẫn `false`) →
  Xuất vào bộ sưu tập. Không crash.
- **Kiểm chứng file độc lập:** kéo `ewm_1790484218561.jpg` về máy, parse APP1 bằng script Python riêng
  (không qua code app) → tìm thấy con dấu thật: `EWM1`, hash 64-hex, timestamp, pubkey X.509 122 ký tự,
  chữ ký ECDSA 94 ký tự. `owner` rỗng vì trường "Bản quyền (EXIF)" chưa điền — đúng thiết kế.
- Verify: Thông tin → **Kiểm tra chứng thực ảnh** → chọn đúng file vừa xuất → **"Ảnh còn nguyên vẹn"**,
  kèm "Xuất lúc: 27/09/2026 11:43", "Chủ sở hữu: chưa đặt", "Khoá: 1f91dfc94a5a9f2f", "Ký bằng khoá
  của chính thiết bị này."
- Ca âm: chọn một ảnh bất kỳ chưa từng xuất → **"Không có con dấu chứng thực"** + giải thích đúng.
- `FATAL EXCEPTION` trong logcat: **0 lần** suốt phiên. Không gặp quảng cáo che UI (R4 không kích hoạt).
- **Ghi chú điều tra giữa phiên:** lần verify đầu app báo "không có con dấu" trên ảnh tưởng là vừa xuất.
  Không đoán — viết thêm 2 test integration (`docConDauQuaInputStream_giongDuongVerifyThat`,
  `verifyQuaUri_traVeNguyenVen`) để loại giả thuyết "ghi bằng filePath, đọc bằng InputStream không
  khớp"; cả 2 PASS → code đúng. Dò tiếp bằng chế độ danh sách của file picker thì lộ nguyên nhân thật:
  **picker mặc định sắp xếp theo tên A→Z**, ô lưới đầu tiên là ảnh CŨ NHẤT chứ không phải ảnh vừa xuất
  — app báo "không có con dấu" là hoàn toàn đúng. Đổi sang "Có sửa đổi (mới nhất trước)" rồi chọn đúng
  file thì ra "Ảnh còn nguyên vẹn". 2 test thêm vào vẫn giữ lại vì chúng phủ đúng đường mà UI dùng thật.

**Tự audit: 9.5/10** — trừ 0.5 vì chưa kiểm chứng được vế "ảnh từ máy khác" (cần thiết bị thứ hai, mà
R3 khoá phiên này chỉ dùng TECNO KJ7); nhánh đó hiện chỉ được phủ bởi test `suaNoiDungConDau_*` và
`kyRoiVerify_bangKhoaThat_*` chứ chưa có ảnh ký bằng khoá máy khác chạy qua UI thật.
