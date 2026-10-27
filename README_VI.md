# ROLL GARDEN STOCK — Android source

## Chức năng đã có trong source
- Ứng dụng Android riêng, package `com.rollgarden.stock`; không đụng đến ROLL CÂY CẢNH.
- Chọn PDF từ điện thoại; dùng Android `PdfRenderer` và ML Kit để OCR từng trang ngay trong thiết bị, không cần thư viện PDF.js từ CDN. Giới hạn hiện tại: 25 MB / 80 trang cho mỗi file.
- Đối chiếu tên cây với catalog 96 cặp tên/quy cách đã trích từ file source ROLL CÂY CẢNH bạn cung cấp. Có màn hình chỉnh sửa danh mục và nhập JSON catalog/backup có mảng `catalog`.
- Bảng kết quả cho phép sửa mã đơn, tên cây, quy cách, số lượng. Dòng chưa đủ căn cứ được đánh dấu cần kiểm tra.
- Bảng tổng nhu cầu theo tên cây/quy cách; bảng combo theo số cây của từng loại trong một đơn; thống kê tổng cây/đơn. Các dòng trùng cây/quy cách trong cùng mã đơn được cộng trước khi phân nhóm.
- Quản lý tồn kho khả dụng tại máy; tính lượng cần bổ sung; xuất CSV/JSON vào Downloads/ROLL GARDEN STOCK.
- Tự lưu dữ liệu cục bộ; nhận diện dấu vân tay file đã nhập để cảnh báo khi nhập lại.

## Lưu ý quan trọng
Bộ tệp đầu vào hiện chưa có PDF nhãn đóng gói gốc. Vì vậy parser là bộ khởi đầu có kiểm tra an toàn, chưa thể khẳng định quy tắc nhận dạng mã đơn/tên cây/số lượng đúng 100% với bố cục PDF thực tế. Khi không có mã đơn, ứng dụng dùng mã `TRANG_###` và gắn cờ để người dùng kiểm tra; không tự chế tên cây khi không khớp catalog. Trước khi dùng số liệu để chốt tồn kho, hãy kiểm tra những dòng được gắn cờ.

## Build APK trên GitHub từ điện thoại
1. Tạo repository mới tên `ROLL-GARDEN-STOCK` (không dùng repository ROLL CÂY CẢNH).
2. Upload toàn bộ nội dung thư mục này lên nhánh `main`.
3. Vào tab **Actions** → workflow **Build ROLL GARDEN STOCK APK** → **Run workflow**.
4. Khi job xanh, mở build run → **Artifacts** → tải `ROLL-GARDEN-STOCK-debug-apk`.

Workflow dùng JDK 17, Gradle 8.9 và Android SDK; cần kết nối internet trong GitHub Actions để tải plugin/dependency lần đầu.

## Cấu trúc chính
- `app/src/main/assets/index.html`: giao diện và bộ phân tích/nhóm dữ liệu.
- `app/src/main/assets/plant_catalog_seed.json`: catalog ban đầu.
- `app/src/main/java/com/rollgarden/stock/MainActivity.java`: chọn PDF, OCR PDF bằng ML Kit và lưu báo cáo.
- `.github/workflows/build-apk.yml`: GitHub Actions build APK debug.

Xem `VALIDATION_NOTES.md` để biết các kiểm tra logic đã chạy và các giới hạn chưa thể xác nhận trong môi trường hiện tại.
