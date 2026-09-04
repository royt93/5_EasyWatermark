---
id: BUG-14
type: Bug
priority: P1
effort: M
sources: Claude (1/4, verify trực tiếp xác nhận đúng dòng)
files:
  - app/src/main/java/com/mckimquyen/watermark/MyApplication.kt
verified: true
---

# VIP secret hardcode base64 trong APK, dễ bypass

## Mô tả
Đã đọc trực tiếp, xác nhận:
```kotlin
// dòng 180
private const val VIP_SECRET_30_DAYS_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="
// dòng 81
vipKeySecret = String(Base64.decode(VIP_SECRET_30_DAYS_B64, Base64.NO_WRAP)),
```
Secret dùng để verify VIP key được hardcode dạng base64 (không mã hoá thật, chỉ encode) ngay trong bytecode. Decompile APK bằng jadx (5 phút, không cần kỹ năng đặc biệt) là đọc được nguyên văn secret → bất kỳ ai cũng tạo được VIP key giả, bypass hoàn toàn cơ chế trả phí/giới hạn liên quan.

## Cách fix đề xuất
Tuỳ mức độ quan trọng của tính năng VIP với doanh thu, chọn 1 trong các hướng (ưu tiên giảm dần theo effort):
1. **Chuyển verify logic lên server** (đáng tin cậy nhất, effort cao nhất) — endpoint xác thực key, app chỉ gọi API.
2. **Native code (NDK/JNI)** — secret không nằm trong bytecode Java/Kotlin dễ decompile, tăng độ khó reverse (không tuyệt đối nhưng cao hơn nhiều).
3. Tối thiểu: không để secret dạng hardcode string trực tiếp — obfuscate qua nhiều bước (không phải giải pháp triệt để, chỉ tăng effort cho attacker).

## Acceptance Criteria
- [ ] Quyết định hướng xử lý dựa trên mức độ thiệt hại thực tế nếu bị bypass (cần trao đổi với chủ dự án — đây là quyết định business, không chỉ kỹ thuật).
- [ ] Secret không còn xuất hiện dạng plain string dễ tìm bằng `strings`/decompile cơ bản.
