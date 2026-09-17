---
id: BUG-36
priority: P2
type: Bug
effort: M
sources: User (quan sát trực tiếp qua screenshot smoke test BUG-27 trên Pixel 7 Pro)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/LaunchView.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/widget/WaterMarkImageView.kt
---

# Watermark tile preview đè lên toàn bộ UI chrome màn editor, không giới hạn trong vùng ảnh

## Mô tả
Quan sát được qua screenshot thật trên device (Google Pixel 7 Pro, `2B051FDH3006MU`, lúc smoke test BUG-27, 2026-09-16) — khi ảnh đang chọn hiển thị trong editor, pattern watermark tile ("DO NOT REDISTRIBUTE" lặp chéo góc) không chỉ phủ lên vùng ảnh preview mà còn lan ra phủ toàn bộ UI chrome xung quanh: toolbar "WATERMARK" wordmark ở trên cùng, toggle Repeat/Single, các chip TileMode/Size/Style, filmstrip thumbnail, tab Content/Style/Layout — toàn bộ màn hình tối màu + tile watermark chồng lên, không giới hạn đúng trong bounds của view hiển thị ảnh.

Ban đầu nghi ngờ đây là artifact do `adb screencap` chụp giữa lúc chạy transition animation (MaterialFadeThrough/SharedAxis dùng trong M3 migration — xem `TextWatermarkBSDFragment.kt`, `LaunchView.kt`), nhưng hiện tượng lặp lại nhất quán ở nhiều screenshot chụp cách nhau vài giây (không phải 1 frame thoáng qua của animation) — nên nhiều khả năng là bug rendering thật, không phải timing artifact.

**Ảnh chụp bằng chứng**: xem file `/tmp/pixel_18.png` (chụp trong phiên làm việc gây ra phát hiện này — không còn trong `/tmp` lâu dài, cần chụp lại để tái hiện khi bắt đầu điều tra).

## Cần điều tra
- Tái hiện trên device thật: chọn 1 ảnh vào editor, quan sát pattern watermark tile có thực sự vẽ tràn ra ngoài bounds của `WaterMarkImageView`/vùng preview hay không (dùng Layout Inspector hoặc `adb shell dumpsys activity <act> --view-hierarchy` để xác nhận view nào thực sự đang vẽ pattern lan rộng).
- Kiểm tra `WaterMarkImageView` — cách tính bounds/clip khi vẽ tile watermark (`Shader`/`Canvas.clipRect`), đặc biệt khi ảnh gốc có tỉ lệ khác thường (trường hợp gây phát hiện: ảnh test là 1 screenshot của chính app, tỉ lệ dọc dài).
- Kiểm tra `LaunchView.kt` — cấu trúc layout giữa launch mode và editor mode, xem view chứa preview có đang bị đặt sai `layout_width/height` (match_parent lồng trong match_parent) khiến nó phình to hơn dự kiến, kéo theo watermark tile "hợp lệ trong bounds" nhưng bounds đó lại lớn hơn cả màn hình.
- Xác nhận đây có phải hành vi ĐÃ CÓ TỪ TRƯỚC (regression cũ, không liên quan M3 migration) hay mới xuất hiện sau đợt migrate M3 (`M3-02-migrate-main-screen-launchview-editor.md` — xem `doc/task/done/`).

## Acceptance Criteria
- [x] Xác định rõ đây là bug rendering thật hay chỉ là screenshot-timing artifact — ghi kết luận vào ticket dù kết quả là gì.
- [x] Nếu là bug thật: watermark tile preview chỉ vẽ trong đúng bounds vùng hiển thị ảnh, không tràn lên toolbar/nút bấm/tab UI xung quanh. **(Không áp dụng — không phải bug, xem kết luận bên dưới.)**
- [x] Smoke test trên device thật xác nhận qua nhiều lần chọn ảnh khác nhau (ảnh dọc, ảnh ngang, ảnh vuông).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `BUG-36`, file ticket = `todo/BUG-36-watermark-tile-preview-de-len-toan-bo-ui-chrome-man-editor.md`.

## Kết quả điều tra (2026-09-16) — KHÔNG PHẢI BUG

**Kết luận: đây là hiểu nhầm do dữ liệu ảnh test, không phải bug rendering.**

**Nguyên nhân thật của hiện tượng quan sát ban đầu**: ảnh được chọn để test BUG-27 tình cờ là 1 screenshot CỦA CHÍNH APP (chụp màn hình launch screen của Watermark Creator-Debug, tỉ lệ 1440×3120 — trùng khít với độ phân giải thật của Pixel 7 Pro). Khi ảnh này hiển thị trong `ivPhoto` (chiếm gần hết chiều cao màn hình giữa toolbar và panel dưới), nó tạo ra hiệu ứng "màn hình trong màn hình": chữ "WATERMARK" thật ở toolbar THẬT + chữ "WATERMARK" xuất hiện lần nữa BÊN TRONG nội dung ảnh (vì ảnh đó vốn là ảnh chụp toolbar của chính app) — nhìn thoáng qua giống như watermark tile "tràn" lên UI chrome thật, nhưng thực chất watermark tile vẫn nằm đúng trong bounds của `ivPhoto`, chỉ là NỘI DUNG ảnh (ảnh chụp màn hình app) khiến mắt nhầm 2 lớp UI chồng nhau.

**Kiểm chứng bằng control test**: đọc code `LaunchView.kt` (`onMeasure`/`layoutEditor`) — `ivPhoto` được đo bằng `measureChildWithMargins(ivPhoto, ..., heightUsed)` với `heightUsed` = tổng chiều cao TẤT CẢ view khác (toolbar, tabLayout, rvPanel, fcFunctionDetail, rvPhotoList) đo TRƯỚC — về logic, `ivPhoto` không thể chiếm không gian của các view khác. Xác nhận trực tiếp trên device (Pixel 7 Pro): chọn 1 ảnh THƯỜNG (không phải screenshot app, ảnh khóa màn hình có gấu bông làm wallpaper, tỉ lệ dọc cao tương tự ảnh gây nhầm lẫn) — watermark tile "DO NOT REDISTRIBUTE" bám ĐÚNG trong khung ảnh preview, hoàn toàn không tràn lên toolbar "WATERMARK" thật, text input box, nút Text/Icon/Signature, hay tab Content/Style/Layout. Không crash, `adb logcat -d "*:E"` sạch (không `FATAL` từ package app).

**Không cần fix code** — đóng ticket, không push (không có thay đổi code).
