---
id: ENH-21
type: Enhancement
effort: XS
sources: Claude fork nội bộ
files:
  - cmonet/src/main/java/com/mckimquyen/cmonet/SimpleSp.kt
---

# `cmonet/SimpleSp` log không gate DEBUG, rò log setting Monet mọi lần đọc/ghi ở bản release

## Mô tả
`Log.i(TAG, "save: $key, $value")`/`Log.i(TAG, "getValue: ...")` (dòng 44, 83) gọi thẳng `android.util.Log`, không qua `AppLog`/`BuildConfig.DEBUG` như ENH-03 đã áp dụng cho module `app`. Module `cmonet` là artifact riêng, ENH-03 (13 file, chỉ module app) không đụng tới — log này chạy CẢ BẢN RELEASE, in ra key/value setting Monet mỗi lần đọc/ghi.

## Triển khai
Thêm gate `BuildConfig.DEBUG` tương tự ENH-03, hoặc dùng logger riêng của module `cmonet` nếu có.

## Acceptance Criteria
- [ ] Build variant release — logcat không còn dòng log `save:`/`getValue:` từ `SimpleSp` khi đọc/ghi setting Monet.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-21`, file ticket = `todo/ENH-21-cmonetsimplesp-log-khong-gate-debug-ro-log-setting-monet-moi.md`.
