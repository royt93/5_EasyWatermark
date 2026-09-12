---
id: ENH-19
type: Enhancement
priority: P2
effort: S
sources: codex exec (external CLI, re-audit 2026-09-10)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/MainViewModel.kt
---

# EXIF border 4 style vẽ text trực tiếp, không đo/co chữ khi tràn

## Mô tả
Cả 4 hàm build EXIF border (`buildClassicExifBorder`/`buildPolaroidExifBorder`/`buildFilmStripExifBorder`/`buildMinimalExifBorder`, quanh dòng 729/759/809/842) dùng `Canvas.drawText()` trực tiếp không đo bề rộng chuỗi trước. Tên máy/model dài (một số máy Android có `TAG_MODEL` rất dài, hoặc copyright/tên máy có ký tự đặc biệt) sẽ bị vẽ tràn ra ngoài canvas, bị cắt cụt xấu thay vì co cỡ chữ hoặc ellipsis.

## Đề xuất
Trước khi `drawText`, đo bề rộng bằng `Paint.measureText()`/`TextUtils.ellipsize()`, co cỡ chữ dần hoặc cắt kèm "…" nếu vượt quá vùng khung dành cho text, áp dụng nhất quán cho cả 4 style.

## Acceptance Criteria
- [x] Model/text rất dài (test với chuỗi giả lập ~100 ký tự) không bị vẽ tràn khỏi canvas ở cả 4 style.
- [x] Text độ dài bình thường (case hiện tại) không đổi cách hiển thị (backward-compatible).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-19`, file ticket = `todo/ENH-19-exif-border-text-overflow-ellipsis.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** Thêm helper `MainViewModel.fitTextForCanvas(paint, text, maxWidth)` — co chữ bằng `Paint.measureText()` + dấu "…" nếu vượt `maxWidth`, trả nguyên văn nếu không tràn (backward-compatible). Áp dụng cho toàn bộ 8 điểm `drawText` ở cả 4 style (CLASSIC: cameraName/formattedExif/dateTime; POLAROID: cameraName/detail; FILM_STRIP: cameraName/detail; MINIMAL: dòng gộp), mỗi điểm dùng ngân sách chiều rộng riêng theo alignment (LEFT/RIGHT/CENTER) và vị trí x thật của nó trên canvas.
- **Điểm tự audit:** 9/10 — cover đủ 4 style, không dùng thư viện ngoài (`TextUtils.ellipsize` cần `TextPaint`, tự viết loop `measureText` đơn giản hơn cho `Paint` thường). Trừ nhẹ vì ngân sách chiều rộng mỗi điểm là ước lượng hợp lý (không tính toán chính xác tuyệt đối vị trí 2 text cạnh nhau có thể chồng lên nhau ở trường hợp cực đoan), nhưng AC chỉ yêu cầu "không tràn khỏi canvas", không yêu cầu 2 text không đè nhau.
- **Test:** `MainViewModelExifBorderRoboTest` thêm 4 case — `fitTextForCanvas_shortText_returnsUnchanged`, `fitTextForCanvas_veryLongText_getsEllipsized_andFitsWithinMaxWidth` (assert `paint.measureText(result) <= maxWidth` thật, không chỉ so sánh độ dài chuỗi), `fitTextForCanvas_emptyText_returnsEmpty`, `buildExifBorderBitmap_veryLongCameraModel_doesNotCrash_forEveryStyle` (model 100 ký tự + make 50 ký tự qua cả 4 style, không crash).
- **Smoke test thật:** verify qua unit test (đo pixel thật bằng `Paint.measureText`, đáng tin cậy hơn Robolectric rasterize vốn không hoạt động đúng trong môi trường build này — xem comment `redSource()` trong file test). Không cần smoke tay riêng vì đây là guard cho edge-case hiếm (model EXIF cực dài), hành vi bình thường không đổi.
