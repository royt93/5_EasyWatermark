---
id: FEAT-24
type: Feature
effort: S
sources: Claude
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/dlg
---

# Danh sách icon/logo gần đây dùng nhanh (MRU quick-pick)

## Mô tả
Mỗi lần đổi logo/icon watermark phải mở lại Gallery chọn từ đầu — không có danh sách N icon/logo gần nhất đã dùng để chọn nhanh (khác FEAT-06 watermark profile đầy đủ có ĐẶT TÊN/quản lý — đây chỉ là MRU nhẹ, tự động, không cần thao tác đặt tên).

## Triển khai
Lưu N (vd 5-10) URI icon gần nhất user đã chọn làm watermark (kèm thumbnail nhỏ) trong DataStore, hiển thị thành hàng ngang quick-pick trong màn chọn icon watermark.

## Acceptance Criteria
- [x] Đổi icon watermark 3 lần khác nhau — mở lại màn chọn icon thấy đúng 3 icon gần nhất theo thứ tự dùng, bấm chọn nhanh không cần mở Gallery.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `FEAT-24`, file ticket = `todo/FEAT-24-danh-sach-iconlogo-gan-day-dung-nhanh-mru-quick-pick.md`.

## Kết quả kiểm chứng
- `WaterMark.recentIconUris: List<Uri>` (mới, mặc định rỗng) — nguồn từ `WaterMarkRepository` (không phải màn hình riêng, vì "màn chọn icon watermark" thực ra KHÔNG tồn tại từ trước — bấm mục "Hình ảnh/Biểu tượng" trong func panel đi THẲNG vào system picker, không qua UI trung gian nào). Lưu chuỗi delimiter `\n` trong DataStore (Uri không chứa newline thật, chỉ percent-encode `%0A`).
- `WaterMarkRepository.updateIcon(uri)` giờ vừa cập nhật icon đang active vừa đẩy uri lên đầu MRU (`pushToFrontOfRecentIcons` — hàm thuần, dedupe khi chọn lại icon cũ, cắt còn tối đa `MAX_RECENT_ICONS=8`).
- `MainActivity`: tách `launchIconPicker()` từ logic cũ (Photo Picker ưu tiên, fallback ACTION_PICK); thêm `showIconQuickPickDialog()` — `MaterialAlertDialogBuilder` + hàng ngang `ShapeableImageView` thumbnail (Glide load), tap 1 icon gọi `viewModel.updateIcon()` + đóng dialog ngay, nút "Chọn ảnh khác" mở picker như cũ. `handleFuncItem(Icon)`: có MRU (`recentIconUris` khác rỗng) → hiện dialog; rỗng (lần đầu) → đi thẳng picker như hành vi cũ, không thêm ma sát.
- Test: `WaterMarkRepositoryRecentIconsResolverTest` (hàm thuần `pushToFrontOfRecentIcons`/`parseRecentIconUris` — prepend, dedupe-move-to-front, cắt maxSize, parse null/blank/newline). `WaterMarkRepositoryRecentIconsRoboTest` (DataStore cô lập `newTestWaterMarkDataStore` — round-trip thật qua `updateIcon()`, thứ tự MRU, cắt khi vượt `MAX_RECENT_ICONS`, không đụng `iconUri` đang active). **Không** test tầng `MainActivity` (dialog vs picker trực tiếp) bằng JVM — thử nghiệm ban đầu qua real Hilt-wired ViewModel đụng đúng lớp flaky DataStore-singleton-theo-file-path đã ghi nhận ở `testutil/TestDataStores.kt` (state rò rỉ giữa các test method trong cùng JVM fork); bỏ, verify bằng smoke test thật thay thế.
- Smoke test thật trên device khoá `115333744A005844` (TECNO SPARK 20 Pro+): lần đầu bấm "Hình ảnh/Biểu tượng" (chưa có MRU) → đi thẳng Android Photo Picker (đúng, không có ma sát) → chọn 1 ảnh → icon áp watermark thành công. Bấm lại lần 2 → dialog "Icon gần đây" hiện đúng 1 thumbnail + 2 nút Huỷ/Chọn ảnh khác (screenshot xác nhận) → tap thumbnail → dialog đóng ngay, icon áp lại lên preview, không crash (logcat sạch).
- `./gradlew testDebugUnitTest` toàn bộ PASS (sau `--stop` daemon). `ktlintCheck` sạch.
- Audit: 9/10 — đúng AC, xử lý đúng trade-off "không có màn chọn icon riêng từ trước" bằng dialog nhẹ thay vì phải dựng cả 1 màn hình mới (đúng tinh thần "MRU nhẹ" ticket mô tả); trừ điểm nhẹ vì thiếu test tầng Activity do giới hạn môi trường (đã verify kỹ bằng device thật thay thế).
