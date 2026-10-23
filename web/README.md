# Giao diện QLWebsite

Spring Boot + Thymeleaf + JavaScript modules, mặc định dùng backend thật qua proxy `/backend-api`.

Chạy toàn bộ từ thư mục gốc bằng `./scripts/run-local.sh`, hoặc build/chạy riêng:

```sh
./mvnw -f web/pom.xml verify
java -jar web/target/qlwebsite-0.1.0.jar
```

Mở **http://127.0.0.1:8080**. Backend mặc định `http://127.0.0.1:8082`, đổi qua `BACKEND_URL`.

- `/login`: đăng nhập; tài khoản mặc định `admin` / `admin123` được backend tự tạo khi database chưa có tài khoản.
- `/`: dashboard.
- `/websites`: quản lý, tìm theo nhóm/tag/môi trường, nhập CSV có xem trước.
- `/websites/{id}`: cấu hình giám sát, lịch sử, biểu đồ/báo cáo CSV, SSL, sự cố/ghi chú và lịch bảo trì.
- `/alerts`: cảnh báo DOWN/RECOVERY.
- `/settings` cũ: chuyển về `/alerts`; trang Kênh thông báo đã được gỡ.
- `/users`: tài khoản và phân quyền, dành cho ADMIN.

Dashboard tải lại mỗi 10 giây; giám sát tự động chạy ở backend. Lịch sử/báo cáo được truy vấn từ database theo khoảng thời gian. Proxy chỉ cho phép các route định trước, chuyển tiếp riêng cookie QLSESSION và header CSRF, giữ Content-Type/Content-Disposition cho báo cáo CSV. Không cần mở CORS backend.

Xem README ở thư mục gốc cho cấu hình database và mô tả hành vi.

JavaScript/CSS được phục vụ qua đường dẫn có phiên bản nội dung (`/assets/v…/js/…`). Tất cả module dùng cùng tiền tố để tránh trộn file cũ và mới khi nâng cấp. Các trang HTML và URL tài nguyên cũ dùng `Cache-Control: no-store`.

Mẫu nhập dữ liệu: [websites-sample.csv](src/main/resources/static/samples/websites-sample.csv), tải qua nút Nhập CSV → Tải CSV mẫu. Đường dẫn `/status` cũ chuyển về `/websites`.
