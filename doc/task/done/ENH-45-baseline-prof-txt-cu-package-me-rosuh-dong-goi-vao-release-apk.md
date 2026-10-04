---
id: ENH-45
type: Enhancement
priority: P2
effort: XS
sources: re-audit 2026-10-04 (verify bằng APK release + mapping.txt thật, đính chính ghi chú sai trong BACKLOG)
files:
  - app/src/main/baseline-prof.txt
---

# `baseline-prof.txt` thuộc package cũ `me.rosuh.easywatermark` vẫn bị đóng gói vào release APK

## Mô tả (đã verify bằng bằng chứng, không suy đoán)
- `BACKLOG.md` từng ghi "baseline-prof.txt không wire vào build, không ticket" — **SAI**. AGP tự động merge `app/src/main/baseline-prof.txt` thành `assets/dexopt/baseline.prof` + `baseline.profm` trong APK (đã `unzip -l` release APK: có cả 2 asset), không cần plugin `baselineprofile`.
- Nội dung stale: 4.194 dòng, 171 dòng tham chiếu `Lme/rosuh/easywatermark/...` (package cũ, `mapping.txt` release hiện tại có **0** class `me.rosuh`), 0 dòng `com/mckimquyen`. Các dòng còn lại dùng tên đã R8-obfuscate của build cũ (`HSPLa/a;`, `HSPLd6/b0;`...) — mapping hiện tại cho thấy `BaseActivity` là `T3.a`, không phải `a/a`, nên gần như không khớp.
- Hậu quả: profile đóng gói không tối ưu được gì cho code thật của app (startup không được AOT-compile đúng class/method nóng), chỉ thêm rác vào APK; còn có nguy cơ tối ưu nhầm class trùng tên obfuscate.
- 2 module benchmark (`baseBenchmarks`, `macrobenchmark`) vẫn bị comment trong `settings.gradle.kts` nên không có cách sinh profile mới.

## Đề xuất
- **Bản tối thiểu (khuyến nghị làm trước):** xoá `app/src/main/baseline-prof.txt` để không đóng gói profile sai; ghi vào `doc/todo.md` rằng cần sinh lại baseline profile khi bật lại module macrobenchmark.
- Bản đầy đủ (ticket riêng, effort M): bật lại `macrobenchmark`, sinh baseline profile mới từ luồng thật (Splash → chọn ảnh → editor → xuất).

## Acceptance Criteria
- [x] Release APK build lại không còn rule app stale; `baseline.prof/.profm` từ dependency AndroidX vẫn được giữ (hợp lệ).
- [x] `./gradlew assembleRelease` thành công, minify + ký vẫn qua; kiểm tra merged/binary art profile xác nhận 0 `me/rosuh`.
- [x] Sửa ghi chú sai trong `BACKLOG.md` ("không wire vào build").
- [x] Không còn tham chiếu `me/rosuh` trong source/build profile (ngoài tài liệu ticket/lịch sử git).

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-45`, file ticket = `todo/ENH-45-baseline-prof-txt-cu-package-me-rosuh-dong-goi-vao-release-apk.md`.

## Kết quả kiểm chứng

**Fix:** xoá `app/src/main/baseline-prof.txt` stale (4.194 dòng; 171 rule package cũ `me/rosuh`, phần còn lại tên R8 cũ), sửa ghi chú sai trong BACKLOG.

- **Audit:** 9.6/10 — bằng chứng trước fix: release APK chứa `assets/dexopt/baseline.prof` 3.445 bytes + `.profm` 410 bytes; `mapping.txt` hiện tại có 0 class `me.rosuh`. Sau fix, APK vẫn chứa profile từ dependency AndroidX (đúng và cần giữ), giảm còn 3.225 + 389 bytes.
- **Verify:** `./gradlew assembleRelease` exit 0. `merged_art_profile/.../baseline-prof.txt` còn 2.426 dòng do dependency cung cấp, **0** `me/rosuh`, **0** `com/mckimquyen`; `strings binary_art_profile/.../baseline.prof` cũng 0 package cũ.
- **Không làm:** chưa bật lại macrobenchmark để sinh baseline profile riêng cho app — cần ticket mới effort M khi muốn tối ưu startup thực sự.
- **Smoke test:** không áp dụng (chỉ thay profile build-time); release minify + ký thành công.
