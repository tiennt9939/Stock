# Kiểm tra source ROLL GARDEN STOCK

## Các kiểm tra đã chạy trong môi trường hiện tại

- JavaScript trong `app/src/main/assets/index.html`: `node --check` — đạt.
- Phân tích dữ liệu giả lập: đơn DH001 có Nguyệt quế Thái C7 số lượng 3 và Cẩm tú mai C9 số lượng 2 — tổng đơn 5 cây; hai nhóm combo từng loại vẫn tách riêng 3 và 2 — đạt.
- Phân tích mã đơn DH002 với Nguyệt quế Thái C7 số lượng 5 — độc lập với DH001 — đạt.
- Quy tắc `combo 4 tặng 1`, số lượng 2 combo — tính ra 10 cây; quy tắc `combo 4 + 1`, số lượng 3 combo — tính ra 15 cây — đạt sau khi kiểm tra dấu `+` trên chuỗi gốc trước khi chuẩn hóa văn bản.
- Quy tắc số lượng theo mẫu `x3` — nhận 3 cây — đạt.
- Tệp AndroidManifest.xml được kiểm tra là XML hợp lệ.

## Những gì chưa thể xác nhận

- Không có PDF nhãn đóng gói gốc trong các tệp nguồn hiện có, nên chưa thể xác minh rằng quy tắc OCR/tách mã đơn tương thích hoàn toàn với bố cục nhãn thật. Mọi dòng thiếu mã đơn hoặc không rõ quy cách sẽ được gắn cờ.
- Môi trường tạo tệp không có Android SDK/Gradle cài sẵn và không thể tải thư viện build từ Internet, nên APK chưa được biên dịch trong môi trường này. Workflow GitHub Actions trong `.github/workflows/build-apk.yml` được cấu hình để build `assembleDebug` và xuất APK thành artifact sau khi source được đẩy lên một repository Android mới.
