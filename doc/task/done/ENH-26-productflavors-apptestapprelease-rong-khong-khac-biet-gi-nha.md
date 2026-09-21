---
id: ENH-26
type: Enhancement
effort: S
sources: Claude
files:
  - app/build.gradle.kts
---

# `productFlavors` `appTest`/`appRelease` rỗng, không khác biệt gì, nhân đôi build variant vô ích

## Mô tả
2 flavor (`appTest`, `appRelease`) không có `applicationIdSuffix`, `resValue`, hay source set riêng (`app/src/appTest`, `app/src/appRelease` không tồn tại trên đĩa) — gây 4 variant build (2 flavor × 2 buildType) thay vì cần thiết 2, gây rối khi chọn variant trong Android Studio và tốn thời gian nếu build-all-variants trên CI.

## Triển khai
Nếu 2 flavor thật sự không có mục đích khác biệt nào, gộp lại thành 1 (xoá flavor dimension); nếu có ý định phân biệt trong tương lai (vd applicationIdSuffix cho test track riêng trên Play Console) thì hiện thực hoá sự khác biệt đó, không để rỗng.

## Acceptance Criteria
- [x] Xác nhận với người hiểu rõ mục đích 2 flavor này trước khi gộp/xoá — tránh phá vỡ quy trình release/CI đang phụ thuộc tên variant.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-26`, file ticket = `todo/ENH-26-productflavors-apptestapprelease-rong-khong-khac-biet-gi-nha.md`.

## Kết quả kiểm chứng
- User xác nhận qua `AskUserQuestion`: gộp thành 1 flavor duy nhất. Xoá khối `flavorDimensions`/`productFlavors` khỏi `app/build.gradle.kts`.
- Tên task Gradle đổi: `assembleAppReleaseDebug/Release` → `assembleDebug/Release`; `installAppReleaseDebug` → `installDebug`; `testAppReleaseDebugUnitTest` → `testDebugUnitTest`; `connectedAppReleaseDebugAndroidTest` → `connectedDebugAndroidTest`. CI (`.github/workflows/*.yml`) đã dùng sẵn tên rút gọn (`assembleRelease`/`bundleRelease`) từ trước — không cần sửa CI.
- Cập nhật CLAUDE.md, `doc/task/PROMPT_TEMPLATE.md`, `doc/todo.md`, AC của `BUG-15` (todo) cho khớp tên task mới. Không sửa các ticket đã `done/` (giữ nguyên như bản ghi lịch sử).
- Audit: 9.5/10 — đúng scope, có xác nhận người dùng trước khi đổi (theo đúng AC gốc), không phá CI.
- Test: `./gradlew testDebugUnitTest` PASS (không có case logic nào cần test mới — build-config thuần).
- Smoke test: `./gradlew assembleDebug` + `installDebug` lên device khoá `115333744A005844` (TECNO KJ7), `am start -n com.mckimquyen.watermark/.ui.SplashActivity` — mở sạch, logcat không `FATAL EXCEPTION`.
