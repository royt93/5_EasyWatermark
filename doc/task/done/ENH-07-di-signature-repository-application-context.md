---
id: ENH-07
type: Enhancement
effort: XS
sources: Internal, Agy (2/4)
files:
  - app/src/main/java/com/mckimquyen/watermark/data/repo/SignatureRepository.kt
  - app/src/main/java/com/mckimquyen/watermark/ui/SignatureActivity.kt
---

# `SignatureRepository` dùng raw Context thay vì Hilt `@ApplicationContext`

## Mô tả
`SignatureRepository` (dòng ~19) nhận `Context` thô, được khởi tạo thủ công tại `SignatureActivity.kt:73` thay vì qua Hilt `@Singleton @Inject` như mọi repo khác trong `data/repo/`. Không nhất quán kiến trúc, và phụ thuộc thủ công vào Activity context là nguồn leak tiềm ẩn nếu repo giữ tham chiếu lâu dài.

## Đề xuất
Đưa `SignatureRepository` vào DI module (`RepositoryModule.kt`) như các repo khác, inject `@ApplicationContext Context` qua constructor Hilt.

## Acceptance Criteria
- [x] `SignatureRepository` được cung cấp qua Hilt, không khởi tạo thủ công trong Activity.
- [x] Không còn phụ thuộc trực tiếp vào Activity context trong repo.

## Prompt loop (tự động hoá)
Áp dụng checklist chuẩn tại [PROMPT_TEMPLATE.md](../PROMPT_TEMPLATE.md), thay `<ID>` = `ENH-07`, file ticket = `todo/ENH-07-di-signature-repository-application-context.md`.

## Kết quả kiểm chứng (2026-09-12)

- **Fix:** `SignatureRepository` nhận `@ApplicationContext Context` qua `@Inject constructor` + `@Singleton` (constructor injection, Hilt tự cung cấp không cần khai báo thêm trong `RepositoryModule.kt`). `SignatureActivity` thêm `@AndroidEntryPoint`, field `repo` đổi thành `@Inject lateinit var`, xoá dòng khởi tạo thủ công `SignatureRepository(this)`.
- **Điểm tự audit:** 9/10 — đúng root cause, nhất quán pattern DI của các repo khác trong `data/repo/`. Trừ nhẹ vì không thêm unit test riêng (constructor injection + đổi Activity context không có logic nghiệp vụ mới để test đơn vị — rủi ro chính là lỗi cấu hình Hilt, chỉ lộ ra khi build/chạy thật).
- **Test:** Không có test đơn vị mới (không có logic nghiệp vụ thay đổi). Compile + `hiltJavaCompileAppReleaseDebug`/`kaptAppReleaseDebugKotlin` PASS (Hilt annotation processor xác nhận graph hợp lệ — nếu thiếu constructor injection hợp lệ, build sẽ FAIL ngay ở bước này).
- **Smoke test thật (OnePlus CPH1989):** mở `SignatureActivity` từ editor (icon "Signature") — không crash (xác nhận Hilt graph hợp lệ + `@Inject lateinit var repo` resolve đúng lúc runtime, không chỉ lúc compile). Vẽ 1 nét, bấm "Apply Signature" — lưu file `.webp` qua `SignatureRepository` thật, quay lại editor với watermark đổi sang hình chữ ký vừa vẽ (tile đúng trên ảnh). Full round-trip DI hoạt động đúng.
