# Task Backlog — EasyWatermark

> Sinh ngày 2026-09-04. Nguồn: đọc trực tiếp toàn bộ `app/src/main` + `cmonet/src/main`, đối chiếu `CLAUDE.md`, `doc/todo.md`, `doc/feat.md`, `doc/memory_leak.md`, `doc/AD.MD` (không lặp việc đã ghi "ĐÃ XONG"), cộng review độc lập song song từ 4 AI agent: Claude Code (internal), **codex exec** (OpenAI Codex, sandbox read-only), **claude -p** (session riêng, allowlist Read/Grep/Glob), **agy** (plan mode). Mỗi bug quan trọng đã được verify lại bằng cách đọc trực tiếp source, không chỉ tin theo báo cáo AI.

## Quy ước

- Mỗi ticket là 1 file `.md` trong `doc/task/todo/` → di chuyển sang `doc/task/inprogress/` khi bắt đầu làm, `doc/task/done/` khi xong (đổi trạng thái = di chuyển file).
- Prefix: `BUG-` (lỗi cần fix), `ENH-` (cải tiến tính năng có sẵn), `FEAT-` (tính năng mới thực dụng), `IDEA-` (tính năng độc quyền/đột phá, effort cao, để tham khảo định hướng dài hạn).
- `priority`: P0 (crash/mất dữ liệu/bảo mật, core feature) > P1 (leak/perf/crash edge-case) > P2 (nhỏ, tối ưu).
- `effort`: XS (<2h) / S (nửa ngày) / M (1-2 ngày) / L (3-5 ngày) / XL (>1 tuần, cần thiết kế riêng).
- `sources`: agent nào tìm ra/đồng thuận — độ đồng thuận cao (3-4/4) = độ tin cậy cao.

## BUGS_TO_FIX (14) — ưu tiên P0 trước

| ID | Priority | Effort | Tiêu đề |
|---|---|---|---|
| [BUG-02](todo/BUG-02-bitmapcache-npe-khi-decode-fail.md) | P0 | XS | BitmapCache NPE khi decode ảnh lỗi |
| [BUG-03](todo/BUG-03-batch-export-bao-thanh-cong-gia.md) | P0 | S | Batch export báo "thành công" giả dù ảnh lỗi |
| [BUG-04](todo/BUG-04-content-resolver-insert-force-unwrap.md) | P0 | S | `contentResolver.insert()!!` crash khi MediaStore trả null |
| [BUG-05](todo/BUG-05-oom-batch-export-khong-downsample-recycle.md) | P0 | M | OOM khi export batch: không downsample + không recycle bitmap |
| [BUG-01](todo/BUG-01-bitmap-decode-sample-sai-kich-thuoc.md) | P1 | S | Tính sai `inSampleSize` và chiều xoay ảnh khi decode |
| [BUG-06](todo/BUG-06-watermark-imageview-icon-cache-race-leak.md) | P1 | M | Icon watermark cache luôn miss khi pinch-zoom + race coroutine |
| [BUG-07](todo/BUG-07-text-shader-indexof-va-kich-thuoc-am.md) | P1 | S | Text shader: `indexOf` sai dòng trùng lặp + kích thước bitmap có thể ≤0 |
| [BUG-08](todo/BUG-08-interstitial-postdelayed-khong-huy.md) | P1 | XS | `postDelayed` hiện interstitial không huỷ khi thoát Activity |
| [BUG-09](todo/BUG-09-action-send-thieu-extra-stream.md) | P1 | S | Nhận ảnh share (`ACTION_SEND`) thiếu `EXTRA_STREAM`, lặp/rơi ảnh |
| [BUG-10](todo/BUG-10-galleryfragment-observe-sai-lifecycle.md) | P1 | XS | `GalleryFragment` observe LiveData sai lifecycle owner |
| [BUG-12](todo/BUG-12-removeimage-crash-index-out-of-bounds.md) | P1 | XS | `removeImage` crash `IndexOutOfBoundsException` khi xoá ảnh |
| [BUG-14](todo/BUG-14-vip-secret-hardcode-trong-apk.md) | P1 | M | VIP secret hardcode base64 trong APK, dễ bypass |
| [BUG-11](todo/BUG-11-compressimg-khong-guard-rong-leak-file-tam.md) | P2 | XS | `compressImg` không guard danh sách rỗng + leak file tạm |
| [BUG-13](todo/BUG-13-qrcode-sinh-dong-bo-main-thread.md) | P2 | XS | Sinh QR đồng bộ trên Main thread mỗi ký tự gõ |

## ENHANCEMENTS (12) — cải tiến tính năng có sẵn

| ID | Effort | Tiêu đề |
|---|---|---|
| [ENH-01](todo/ENH-01-batch-export-workmanager-huy-tien-do.md) | L | Batch export chạy qua WorkManager + huỷ + tiến độ tổng |
| [ENH-02](todo/ENH-02-debounce-ghi-datastore-khi-gesture.md) | S | Debounce ghi DataStore khi nhập text (đã đính chính: KHÔNG phải pinch/kéo) |
| [ENH-03](todo/ENH-03-gate-log-debug-build-config.md) | S | Gate toàn bộ `Log.d` bằng `BuildConfig.DEBUG` |
| [ENH-04](todo/ENH-04-kich-hoat-lai-pinch-to-resize.md) | S | Kích hoạt lại pinch-to-resize (đang bị comment) |
| [ENH-05](todo/ENH-05-preview-token-khop-export.md) | S | Preview token watermark khớp với lúc export |
| [ENH-06](todo/ENH-06-gom-input-stream-decode-anh.md) | M | Gộp mở `InputStream` lặp lại khi decode 1 ảnh (4-5 lần → 1-2 lần) |
| [ENH-07](todo/ENH-07-di-signature-repository-application-context.md) | XS | `SignatureRepository` dùng raw Context thay vì Hilt `@ApplicationContext` |
| [ENH-08](todo/ENH-08-immutable-state-watermark-repository.md) | M | Model bất biến cho `ImageInfo`/`StateFlow` (`WaterMarkRepository`) |
| [ENH-09](todo/ENH-09-hardcode-string-sang-resources.md) | M | Đưa hardcode string UI sang `resources` (i18n/accessibility) |
| [ENH-10](todo/ENH-10-android-photo-picker.md) | M | Chuyển sang Android Photo Picker thay `ACTION_PICK` legacy |
| [ENH-11](todo/ENH-11-vong-doi-ad-banner-day-du.md) | XS | Vòng đời Ad Banner đầy đủ (resume/pause/destroy) ở `AboutActivity` |
| [ENH-12](todo/ENH-12-monet-manufacturer-dua-vao-api-chinh-thuc.md) | S | `MonetManufacturer` whitelist nên dựa API `isDynamicColorAvailable()` |
| [ENH-13](todo/ENH-13-hien-thi-so-anh-thanh-cong-that-bai-cuoi-batch.md) | S | Hiển thị số ảnh thành công/thất bại cuối batch (tách từ BUG-03) |
| [ENH-14](todo/ENH-14-downsample-truc-tiep-khi-decode-export.md) | M | Downsample trực tiếp khi decode ảnh export (tách từ BUG-05) |
| [ENH-15](todo/ENH-15-bitmapcache-recycle-an-toan-khi-evict.md) | M | BitmapCache recycle an toàn khi evict — cần thiết kế refcounting (tách từ BUG-05) |
| [ENH-16](todo/ENH-16-throttle-rebuild-shader-khi-pinch.md) | M | Throttle rebuild shader khi pinch — nguyên nhân lag thật (tách từ ENH-02 sau khi test BUG-06/ENH-04) |

## NEW_FEATURES (13) — tính năng mới thực dụng, 1-2 tuần

| ID | Effort | Tiêu đề |
|---|---|---|
| [FEAT-01](todo/FEAT-01-9-grid-position-anchor.md) | S | Preset vị trí neo 9-grid + margin % |
| [FEAT-02](todo/FEAT-02-naming-template-file-xuat.md) | S | Naming template cho file xuất (tái dùng token có sẵn) |
| [FEAT-03](todo/FEAT-03-multi-layer-watermark.md) | L | Watermark đa lớp (chồng text + logo/QR cùng lúc) |
| [FEAT-04](todo/FEAT-04-lich-su-batch-gan-day.md) | M | Lịch sử batch export gần đây |
| [FEAT-05](todo/FEAT-05-backup-restore-template-signature.md) | M | Xuất/nhập Template + Signature (backup/restore) |
| [FEAT-06](todo/FEAT-06-watermark-profile-day-du.md) | M | Watermark profile đầy đủ (không chỉ text, đặt tên tái dùng) |
| [FEAT-07](todo/FEAT-07-preview-grid-truoc-khi-export.md) | M | Preview grid trước khi export cả batch |
| [FEAT-08](todo/FEAT-08-chon-thu-muc-saf-batch.md) | S | Chọn cả thư mục (SAF tree) để batch |
| [FEAT-09](todo/FEAT-09-preset-resize-theo-nen-tang.md) | XS | Preset resize theo nền tảng (Instagram/Facebook/Zalo) |
| [FEAT-10](todo/FEAT-10-frame-preset-nhan-dien-hang-may.md) | M | Frame preset EXIF border + tự nhận diện hãng máy |
| [FEAT-11](todo/FEAT-11-hieu-ung-vien-bong-text.md) | S | Hiệu ứng viền/bóng/nền pill cho text watermark |
| [FEAT-12](todo/FEAT-12-undo-redo-editor.md) | M | Undo/Redo chỉnh sửa watermark trong editor |
| [FEAT-13](todo/FEAT-13-caption-rieng-tung-anh-batch.md) | M | Nhập caption/text riêng theo từng ảnh trong batch (CSV) |

## UNIQUE_IDEAS (7) — tính năng độc quyền/đột phá, effort cao

| ID | Effort | Tiêu đề |
|---|---|---|
| [IDEA-01](todo/IDEA-01-ai-auto-placement-nhan-dien-chu-the.md) | L | Auto-placement bằng on-device ML (né mặt người/chủ thể) — **đồng thuận 4/4 agent** |
| [IDEA-02](todo/IDEA-02-invisible-watermark-steganography.md) | XL | Invisible watermark / steganography chống xoá |
| [IDEA-03](todo/IDEA-03-content-authenticity-stamp-c2pa.md) | L | Content authenticity stamp kiểu C2PA |
| [IDEA-04](todo/IDEA-04-cloud-sync-brand-kit.md) | XL | Cloud sync Brand Kit đa thiết bị |
| [IDEA-05](todo/IDEA-05-cho-template-cong-dong.md) | XL | Chợ template cộng đồng (network effect) |
| [IDEA-06](todo/IDEA-06-auto-contrast-opacity-harmonizer.md) | M | Auto-contrast/opacity harmonizer theo từng ảnh |
| [IDEA-07](todo/IDEA-07-batch-qr-smart-bridge.md) | M | Batch QR Smart-Bridge (hash SHA-256 + xác thực nguồn gốc) |

## Gợi ý sprint đầu tiên

Nhóm P0 (BUG-02, 03, 04, 05) là core-feature crash/data-loss trong đúng luồng chính (save/export ảnh) — nên làm trước tiên, gộp chung 1 sprint vì cùng khu vực code (`MainViewModel` export path). Sau đó chọn 2-3 FEAT effort S/XS (FEAT-01, FEAT-02, FEAT-09) để có tính năng "nhìn thấy được" song song với dọn nợ kỹ thuật.
