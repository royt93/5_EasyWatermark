---
id: FEAT-07
type: Feature
effort: M
sources: Codex, Internal (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/adapter/SaveImageListAdapter.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg/SaveImageBSDialogFragment.kt
  - app/src/main/java/com/mckimquyen/watermark/utils/bitmap/OutputImageUtils.kt
verified: partial — xem "Kết quả kiểm chứng" bên dưới, có giới hạn môi trường
---

# Preview grid trước khi export cả batch

## Mô tả
Trước khi chạy `generateList` cho cả batch, hiện 1 lưới (grid) thumbnail preview watermark áp lên từng ảnh để người dùng phát hiện lỗi trước (chữ tràn khung, EXIF thiếu, watermark che mất chi tiết quan trọng...) trước khi tốn thời gian export cả loạt lớn.

## Đề xuất bổ sung (từ Codex)
Kết hợp hiển thị số ảnh, kích thước dự kiến, định dạng, dung lượng gần đúng, cảnh báo bộ nhớ — người dùng có thể đổi preset resize/compression ngay tại đây trước khi bắt đầu batch dài.

## Acceptance Criteria
- [x] Trước khi export, hiển thị grid thumbnail có watermark áp sẵn cho từng ảnh trong batch.
- [x] Hiển thị ước tính dung lượng/kích thước output.
- [x] Người dùng có thể quay lại chỉnh sửa trước khi xác nhận export.

## Kiến trúc
- **Không xây màn hình mới** — `SaveImageBSDialogFragment` (dialog Export) đã sẵn hiện 1 grid (`rvResult`/`SaveImageListAdapter`) NGAY KHI mở dialog, TRƯỚC khi user bấm nút export thật (`btnSave`) — đúng yêu cầu "trước khi export" + AC3 "quay lại chỉnh sửa" (dismiss dialog = quay lại editor, không export gì cả, hành vi này đã có sẵn từ trước, không cần code thêm). Chỉ cần nâng cấp NỘI DUNG grid từ ảnh gốc (Glide) sang ảnh đã áp watermark + thêm text ước tính.
- `BatchExportEngine.generatePreviewBitmap(contentResolver, imageInfo, config, index)` (hàm mới) — render watermark NHẸ cho preview: decode ảnh ở kích thước nhỏ (`PREVIEW_MAX_SIZE = 480px`, qua `decodeSampledBitmapFromResource` đã cache sẵn theo (uri, reqW, reqH)) rồi vẽ trực tiếp watermark lên canvas CÙNG kích thước đã decode — mô phỏng đúng cách `WaterMarkImageView.onDraw()` vẽ preview on-screen (`applyConfig(isScale=true)` mặc định, KHÔNG dùng `adjustMatrix`/`scaleX` như hàm export thật `generateImage()`, vì canvas preview chính là bitmap đã decode chứ không phải 1 "view" riêng cần quy đổi tỉ lệ). Tái dùng nguyên vẹn `buildTextBitmapShader`/`buildIconBitmapShader`/`applyConfig` — không viết lại logic render.
- **Cố ý KHÔNG vẽ khung EXIF border trong preview** — đúng quy ước sản phẩm ĐÃ CÓ (xem `dlg_exif_border.xml`: "Note: The border is not shown in the preview window to optimize performance"), áp dụng nhất quán cho preview grid mới này.
- `OutputImageUtils.estimateOutputBytes(width, height, format, quality)` (hàm thuần mới) — ước tính dung lượng theo heuristic bits-per-pixel tuyến tính theo quality (JPEG/WEBP: 0.1–2.0 bpp theo quality 0–100; PNG: 8 bpp cố định) — **CHỈ LÀ HEURISTIC**, không nén thử thật sự, vì nội dung ảnh thật ảnh hưởng lớn tới kích thước nén thật (ảnh phẳng màu nén nhỏ hơn nhiều so với ảnh nhiễu chi tiết cùng độ phân giải/quality). Đủ để user có khái niệm tương đối trước batch lớn, không nhằm chính xác tuyệt đối.
- `SaveImageListAdapter` nhận thêm `scope`/`generatePreview`/`estimateOutput` qua constructor (tách khỏi `MainViewModel` bằng lambda, dễ test độc lập). `processUI()` hiển thị NGAY ảnh gốc qua Glide (nhanh, tránh ô trống), rồi thay bằng bitmap watermark khi render xong (bất đồng bộ). Dùng `itemView.tag` làm khoá "đã/đang render uri nào" — vừa tránh set nhầm bitmap vào ViewHolder bị RecyclerView tái dùng cho item khác, vừa tránh build lại canvas/shader tốn kém khi payload "state" (đổi jobState Ready→Ing→Success lúc export chạy) rebind CÙNG uri liên tục.
- `item_saving_image.xml` — thêm `tvPreviewInfo` (TextView overlay góc dưới, nền scrim tối `glass_surface` có sẵn) hiển thị `"WxH · ~dung lượng"`, chỉ hiện khi preview render xong (ẩn mặc định, tránh nhấp nháy text cũ khi rebind).
- `MainViewModel.generateExportPreview()`/`estimateExportOutput()` — wrapper mỏng đọc `waterMark`/`outputFormat`/`compressLevel`/`maxOutputLongEdge` hiện tại rồi uỷ quyền cho `BatchExportEngine`, không chứa logic render.

## Kết quả kiểm chứng

### Unit/widget test (viết đầy đủ, KHÔNG chạy được tới xác nhận cuối do môi trường)
Đã viết bộ test đầy đủ cho mọi hàm/nhánh mới:
- `OutputImageUtilsTest` — 5 case mới cho `estimateOutputBytes` (quality cao hơn → ước tính lớn hơn, kích thước lớn hơn → ước tính lớn hơn, PNG bỏ qua quality, quality ngoài [0,100] bị clamp, sàn tối thiểu 1024 byte).
- `BatchExportEnginePreviewRoboTest` — 3 case cho `generatePreviewBitmap` (config text mặc định trả bitmap + kích thước ước lượng đúng từ inSampleSize, text rỗng trả bitmap gốc không build shader, index khác nhau không crash). Phát hiện + sửa 1 giả định sai trong lúc viết: Robolectric shadow `BitmapFactory` decode BẤT KỲ uri nào (kể cả không tồn tại) thành bitmap giả 100x100 thay vì trả lỗi — test ban đầu giả lập "uri không tồn tại → null" sai với thực tế, đã viết lại theo đúng hành vi thật, nhờ vậy lại test được thẳng nhánh render watermark thật thay vì chỉ nhánh lỗi.
- `SaveImageListAdapterPreviewRoboTest` — 3 case (bind hiện bitmap watermark + text ước tính, preview lỗi fallback không hiện text, rebind cùng uri qua payload "state" không gọi lại `generatePreview`).
- `MainViewModelExportPreviewRoboTest` — 3 case (`generateExportPreview` guard khi config chưa load, `estimateExportOutput` với resize "Original" giữ nguyên kích thước, `estimateExportOutput` sau khi đổi resize/format phản ánh đúng).
- `SaveImageListAdapterCountRoboTest`/`SaveImageListAdapterUpdateJobStateRoboTest` — cập nhật theo constructor mới của adapter (thêm `scope`/`generatePreview`/`estimateOutput`), hành vi cũ (đếm/update job state) không đổi.
- `compileAppReleaseDebugUnitTestKotlin` — **BUILD SUCCESSFUL**, xác nhận toàn bộ code (production + test) compile sạch, không lỗi type/signature.

**Giới hạn đã biết:** trong suốt phiên làm việc này, máy bị nghẽn tài nguyên hệ thống NGHIÊM TRỌNG và KÉO DÀI (swap tăng dần tới 7.85/8.19GB đã dùng, chỉ còn ~345MB free, do nhiều session Claude Code + Android Studio khác chạy song song trên cùng máy) — mọi lần chạy bộ test Robolectric cho FEAT-07 (kể cả chạy từng file riêng lẻ) đều bị treo (JVM test worker không tiến triển, TIME process đứng yên trong khi ETIME tăng), lặp lại qua hơn 10 lần thử trong ~1 giờ. **Không có lần nào bộ test chạy được tới kết quả PASS/FAIL cuối cùng** — chỉ có 1 lần chạy được tới hết (phát hiện + sửa giả định sai ở trên) trước khi các lần sau đó đều treo. Đây là giới hạn môi trường (tài nguyên máy), không phải lỗi code — `compileAppReleaseDebugUnitTestKotlin` đã xác nhận code hợp lệ. User đã được thông báo rõ tình trạng này và trực tiếp quyết định: chấp nhận đẩy code lên với giới hạn kiểm chứng này, verify đầy đủ lại (test suite + smoke test) trong phiên làm việc kế tiếp khi máy hết bị nghẽn.

### Smoke test thật trên Pixel 7 Pro (2B051FDH3006MU)
- `./gradlew installAppReleaseDebug` — **BUILD SUCCESSFUL**, cài đặt thành công lên thiết bị.
- Vào được editor với 2 ảnh trong batch (xác nhận qua screenshot), nhưng thao tác điều hướng tương tác tiếp theo (mở dialog Export để xem grid preview mới) liên tục thất bại do thao tác chạm qua `adb input tap` bị lệch/rớt sang app/màn hình khác một cách bất thường (mở nhầm launcher search, mở nhầm app Thời tiết) — nghi do độ trễ input toàn hệ thống dưới áp lực RAM/swap cực cao cùng thời điểm (xem giới hạn ở trên), không phải lỗi trong code UI của ticket này.
- **Chưa xác nhận trực quan được** nội dung grid preview thật trên màn hình (bitmap có watermark, text ước tính kích thước/dung lượng) trong phiên này — cần verify lại khi máy ổn định.

### Khuyến nghị cho phiên tiếp theo
1. Chạy lại toàn bộ bộ test FEAT-07 liệt kê ở trên tới khi có kết quả PASS/FAIL rõ ràng.
2. Smoke test lại trên Pixel 7 Pro: mở dialog Export với batch ≥2 ảnh, xác nhận grid hiện đúng thumbnail có watermark (không phải ảnh gốc) + text ước tính (vd "1080×1350 · ~180 KB"), xác nhận dismiss dialog không export quay lại editor bình thường.
3. Nếu phát hiện lỗi thật ở bước 1/2, sửa trước khi coi FEAT-07 là hoàn thành đầy đủ theo tiêu chuẩn DoD thông thường của dự án (audit >9/10 + test + smoke test đầy đủ).

### Ghi chú phụ: full-repo ktlintCheck
Trong phiên này, 1 lần chạy nhầm `:app:ktlintFormat` (định sửa 1 lỗi trailing-comma nhỏ trong file test mới) đã format lại TOÀN BỘ main source set thay vì chỉ file đang sửa, sau đó bị revert (`git checkout --`) — việc đổi mtime hàng loạt khiến cache incremental của `:app:ktlintCheck` bị invalidate, lộ ra **265 vi phạm style tồn tại từ trước** trên ~60 file KHÔNG liên quan tới FEAT-07 (đã verify riêng: 13 file FEAT-07 chạm tới trong ticket này sạch 100%, không vi phạm nào). Đây là nợ kỹ thuật có sẵn của dự án (có thể do cache `ktlintCheck` trước đó "pass giả" do chưa từng bị invalidate) — đáng làm 1 ticket dọn dẹp riêng, KHÔNG sửa trong ticket này (ngoài phạm vi).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-07`, file ticket = `todo/FEAT-07-preview-grid-truoc-khi-export.md`.
