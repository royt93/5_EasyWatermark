---
id: ENH-11
type: Enhancement
effort: XS
sources: Agy (1/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/ui/about/AboutActivity.kt
---

# Vòng đời Ad Banner đầy đủ (resume/pause/destroy) ở `AboutActivity`

## Mô tả
`AboutActivity.kt:123-147` chưa gọi đầy đủ `AdManager.bannerResume()`/`bannerPause()`/`bannerDestroy()` theo vòng đời Activity tương ứng (`onResume`/`onPause`/`onDestroy`), khiến banner ad có thể tiếp tục tiêu hao CPU/pin ngầm khi Activity không còn hiển thị.

## Đề xuất
Triển khai đầy đủ 3 lời gọi tương ứng đúng lifecycle callback.

## Acceptance Criteria
- [ ] `bannerResume`/`bannerPause`/`bannerDestroy` được gọi đúng lifecycle.
- [ ] Không còn banner ad hoạt động ngầm khi `AboutActivity` không ở foreground.
