# ENH-46: Gom magic number (quality step, scroll extent, debounce) về const

**Priority:** P2
**Effort:** XS
**Status:** done

## Vấn đề (R5 magic number)

- `app/src/main/java/com/mckimquyen/watermark/data/repo/UserConfigRepository.kt:60-62` `coerceAtLeast(20)`/`coerceAtMost(100)`/`% 20` — step 20 trùng `android:stepSize="20"` ở `res/layout/dlg_save_file.xml:257`; lệch 1 bên là reset quality user về mặc định.
- `.../ui/widget/UniformScrollGridLayoutManager.kt:53` fallback `110`.
- `.../ui/widget/TouchSensitiveRv.kt:129` debounce `300`.

## Fix

`COMPRESS_LEVEL_MIN/MAX/STEP`, `DEFAULT_SCROLL_EXTENT_PX`, `AUTO_SELECT_DEBOUNCE_MS` kèm comment nguồn gốc giá trị. Test khoá `STEP` khớp `stepSize` layout.

## Prompt loop

Xem [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md).

## Kết quả kiểm chứng (2026-10-14)

- **Thay magic number**: compression min/max/step (repo), default scroll extent 110 (layout manager), auto-select debounce 300ms (TouchSensitiveRv).
- **Tests**: `UserConfigRepositoryCompressLevelRoboTest` 7 case (giá trị biên/step/off-grid/default + layout Slider khớp const); `UniformScrollGridLayoutManagerRoboTest` 4 case (2 mới cho scroll extent); `TouchSensitiveRvDebounceRoboTest` 5 case (const, boundary function, first idle, suppress/reenable). 1483 unit tests pass, ktlint pass.
- **Smoke Pixel 7 Pro (`2B051FDH3006MU`)**: gallery opened, one image selected, editor loaded, opened Lưu sheet, changed quality 80→40; value updated on screen, no crash. Gallery scroll and editor also rendered. Ad không che màn hình trong lần smoke Pixel này.
- **Tự audit**: 9.5/10; diff nhỏ, no magic values duplicated for audited sites; test khóa layout XML với Kotlin constants.
