---
id: FEAT-10
type: Feature
effort: S
sources: Claude, Agy, doc/feat.md mục E; codex exec re-audit 2026-09-10 (xác nhận chỉ done 1 phần)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/model/ExifFrameStyle.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/ExifPbFragment.kt
verified: true
---

# Tự nhận diện hãng máy để gợi ý style khung EXIF border

## Mô tả
**Phần "thêm style khung mới" ĐÃ XONG** (2026-09-06, xem `doc/feat.md` mục 9): 4 style Classic/Polaroid/Film Strip/Minimal, `data/model/ExifFrameStyle.kt` + `MainViewModel.buildExifBorderBitmap()`. Cố ý **không** dùng logo hãng máy thật (Canon/Sony/Apple/Leica) như đề xuất gốc — tránh rủi ro trademark, chỉ vẽ bằng Canvas thuần.

**Phần CÒN LẠI (scope thực của ticket này sau khi tách):** tự động gợi ý style khung khớp với `TAG_MAKE`/`TAG_MODEL` đọc từ EXIF (vd máy Fujifilm/Leica gợi ý Classic, máy không rõ hãng gợi ý Minimal), giảm thao tác chọn thủ công — người dùng vẫn đổi tay được, đây chỉ là gợi ý mặc định.

## Triển khai
Thêm bước map `TAG_MAKE` (đã đọc sẵn qua `ExifModel`) → 1 trong 4 `ExifFrameStyle` hiện có, làm giá trị mặc định gợi ý khi mở `ExifPbFragment` lần đầu cho ảnh đó (không ép, chỉ set giá trị khởi tạo).

## Acceptance Criteria
- [x] ~~Có tối thiểu 3 style khung mới ngoài style hiện tại~~ — ĐÃ XONG, xem `doc/feat.md` mục 9.
- [x] Ảnh có EXIF nhận diện được hãng máy tự động gợi ý đúng style khung tương ứng (vẫn đổi tay được).

## Kiến trúc
- `ExifFrameStyle.suggestFor(make: String): ExifFrameStyle` — hàm thuần map chuỗi hãng máy (chuẩn hoá uppercase + trim) sang 1 trong 4 style: rỗng/blank → MINIMAL; LEICA/FUJIFILM/FUJI → CLASSIC; POLAROID/INSTAX/KODAK → POLAROID; CANON/NIKON/SONY/PENTAX/OLYMPUS/PANASONIC → FILM_STRIP; còn lại (kể cả điện thoại như Apple/Samsung/Xiaomi) → MINIMAL, khớp đúng ví dụ AC gốc.
- `exifFrameStyle` là field **toàn cục** trên `WaterMark` (không phải per-ảnh), và codebase không có sẵn cơ chế theo dõi "user đã tự tay đổi style cho ảnh này chưa". Thay vì thêm state persist mới (quá nặng cho ticket effort S), dùng `MutableSet<Uri>` **session-scoped, in-memory** (`manuallyChosenExifStyleUris` trên `MainViewModel`) — không lưu DataStore, reset khi app restart (chấp nhận được vì đây chỉ là gợi ý UX, không phải preference cần lưu bền).
- `MainViewModel.suggestExifFrameStyleIfNeeded()` — gọi mỗi lần `ExifPbFragment.onViewCreated()`, no-op nếu URI ảnh đang chọn đã có trong set "đã tự chọn tay". `selectExifFrameStyle(style)` (được gọi khi user bấm nút style trong UI) đánh dấu URI đó vào set trước khi ghi style.
- `ExifPbFragment.onViewCreated()` gọi `suggestExifFrameStyleIfNeeded()` làm dòng đầu tiên, trước khi bind các listener khác — không ảnh hưởng luồng UI hiện có (`highlightStyle`, `bindCustomizeValues` vẫn tự chạy qua `waterMark.observe` như cũ).

## Kết quả kiểm chứng

### Unit/widget test
- `ExifFrameStyleTest` — 10 case mới cho `suggestFor()`: rỗng/blank → Minimal, Fujifilm/Leica → Classic, case-insensitive, trim whitespace, Polaroid/Kodak → Polaroid, Canon/Nikon/Sony → Film Strip, hãng không nhận diện (Apple/Samsung/Xiaomi) → Minimal. Cùng 4 case cũ (`obtain` round-trip) vẫn pass.
- `MainViewModelExifFrameSuggestionRoboTest` (mới, Robolectric) — 4 case dùng polling helper (`awaitExifFrameStyle`/`awaitJobFinished`-style, vì `waterMark` là DataStore-Flow-backed LiveData cần thời gian I/O thật để lan giá trị, một lần `idle()` không đủ khi ghi liên tiếp nhanh trong cùng test):
  - Ảnh Fujifilm → tự áp Classic.
  - Ảnh không rõ hãng (make rỗng) → tự áp Minimal.
  - Sau khi user tự chọn tay Polaroid cho 1 ảnh, gọi lại `suggestExifFrameStyleIfNeeded()` cho ĐÚNG ảnh đó → không bị đè lại (vẫn giữ Polaroid).
  - Ảnh KHÁC (chưa từng chọn tay) vẫn được gợi ý bình thường dù ảnh trước đó đã chọn tay (Canon → Film Strip).
- Regression: toàn bộ `MainViewModel*RoboTest` (8 class: ExifBorder, SaveImageImmutability, ResetJobStatus, ResolvePreviewText, CompressImg, RemoveImage, GenerateOutputName, ExifFrameSuggestion), `ExifFrameStyleHighlighterRoboTest`, `ExifPbFragmentRoboTest`, `:app:ktlintCheck` — pass. (Full 17-class suite không chạy trọn được do máy bị nghẽn RAM bởi các Gradle daemon của session khác chạy song song cùng lúc — dùng regression trọng tâm phủ toàn bộ file bị chạm + toàn bộ `MainViewModel*` làm bằng chứng thay thế.)

### Smoke test thật trên Pixel 7 Pro (2B051FDH3006MU)
- Load ảnh test (screenshot, không có EXIF Make thật) vào editor, bật "Leica EXIF Border" → mở đúng `ExifPbFragment` bottom sheet → **Frame Style tự động khoanh chọn "Minimal"** — đúng hành vi kỳ vọng cho ảnh "không rõ hãng máy" (khớp AC gốc).
- Do ảnh test trên máy đa số là screenshot (không có EXIF Make thật của Fujifilm/Leica/Canon...), nhánh "hãng máy → Classic/Film Strip/Polaroid" không demo trực tiếp được qua smoke test — dùng `ExifFrameStyleTest.suggestFor_*` (unit test JVM thuần) làm bằng chứng chính cho các nhánh này, tương tự cách ENH-01 đã document giới hạn môi trường tương tự.
- Không thấy crash, không có quảng cáo che UI trong lúc test.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-10`, file ticket = `todo/FEAT-10-frame-preset-nhan-dien-hang-may.md`.
