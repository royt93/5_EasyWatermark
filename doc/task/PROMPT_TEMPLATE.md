# Prompt Loop chuẩn cho mọi ticket trong `doc/task/`

Dùng chung cho mọi file `doc/task/todo/*.md`. Mỗi ticket có 1 dòng "Prompt loop" trỏ về đây thay vì lặp lại nguyên văn — khi chạy `/loop` hoặc giao việc cho AI agent, thay `<ID>` và `<FILE>` theo đúng ticket rồi dùng nguyên văn prompt dưới đây.

## Definition of Done (áp dụng mọi ticket)

Task chỉ được coi là xong khi ĐỦ CẢ 3 điều kiện:
1. **Điểm audit > 9/10** — tự chấm sau khi tự review lại diff (đúng bug/yêu cầu gốc, không phá luồng khác, tuân CLAUDE.md R5: không magic number, không `late`/force-unwrap tuỳ tiện, không leak stream/controller/listener/timer/subscription chưa dispose).
2. **Có test cho mọi case đã sửa**: unit test (`app/src/test`) bắt buộc; widget/Robolectric test nếu đụng UI; integration test (`app/src/androidTest`) nếu đụng Room/DataStore/IO thật. Toàn bộ `./gradlew testAppReleaseDebugUnitTest` phải xanh.
3. **Smoke test thật trên device đã khoá của session** (theo CLAUDE.md R3 — tuyệt đối không tự đổi device khi user đã khoá) — cài bản debug, thao tác đúng luồng vừa sửa, xác nhận không crash/regression qua logcat + quan sát UI thật.

## Prompt mẫu (điền `<ID>` / `<FILE>`)

```
Đọc file doc/task/todo/<FILE> (nếu đang làm dở, đọc ở doc/task/inprogress/<FILE>). Di chuyển file sang doc/task/inprogress/ khi bắt đầu (đổi trạng thái = di chuyển file, đúng quy ước BACKLOG.md).

Implement đúng nội dung "Cách fix đề xuất"/"Đề xuất" và làm thoả mọi "Acceptance Criteria" ghi trong chính file ticket đó. Không mở rộng scope ra ngoài ticket.

Sau khi code xong, lặp vòng audit sau tới khi đạt Definition of Done (xem trên) — KHÔNG dừng giữa chừng, KHÔNG báo "done" khi còn thiếu 1 trong 3 điều kiện:
1. Tự audit lại toàn bộ diff, chấm điểm thang 10.
2. Bổ sung/chạy đủ unit + widget (nếu có UI) + integration test (nếu đụng IO/DB/DataStore thật) cho mọi case đã sửa.
3. Smoke test thật trên device đã khoá của session (không tự đổi device). Nếu smoke test gặp quảng cáo che UI — dừng ngay theo CLAUDE.md R4, báo user, chờ "done" mới tiếp tục.
4. Nếu điểm ≤ 9/10 hoặc thiếu test hoặc smoke test fail — tự sửa tiếp, quay lại bước 1.

TÍN HIỆU DỪNG LOOP: chỉ dừng khi đạt đủ 3 điều kiện Definition of Done. Khi đạt: cập nhật section "Kết quả kiểm chứng" vào cuối file ticket (điểm số, số test đã thêm, kết quả smoke test cụ thể), move file sang doc/task/done/, rồi tạo commit theo Conventional Commit (xác nhận trước khi `git push` nếu chưa được uỷ quyền autonomous cho toàn phiên).

Nếu sau nhiều vòng vẫn không đạt điểm >9/10 — DỪNG lại, báo cáo user lý do cụ thể thay vì tự ý push hoặc tự hạ tiêu chuẩn.
```
