# QLWebsite

Ứng dụng quản lý và kiểm tra trạng thái website bằng Java 17, Spring Boot, Spring Data JPA và Thymeleaf.

- Thư mục gốc: backend cung cấp REST API và lưu dữ liệu kiểm tra.
- Thư mục [`web/`](web/README.md): giao diện dashboard, quản lý website, biểu đồ và lịch sử; mặc định kết nối backend thật và tắt demo.

## Chạy backend

Cần JDK 17 trở lên và một cơ sở dữ liệu SQL Server. Cấu hình kết nối nằm trong `src/main/resources/application.properties`; có thể ghi đè bằng các biến môi trường `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD`.

```sh
./mvnw verify
java -jar target/qlwebsite-0.1.0.jar
```

Backend mặc định chạy tại `http://127.0.0.1:8082`. Trên Windows dùng `mvnw.cmd`. Maven Wrapper tự tải Maven và các dependency khi cần.

## Chạy giao diện

Sau khi backend đã khởi động, mở terminal khác tại thư mục gốc:

```sh
./mvnw -f web/pom.xml verify
java -jar web/target/qlwebsite-0.1.0.jar
```

Mở **http://127.0.0.1:8080**. Nếu backend ở địa chỉ khác, truyền `--app.backend.url=http://dia-chi-backend:cong` khi chạy giao diện.

Giao diện sử dụng dữ liệu từ database của backend. Không tạo dữ liệu mẫu trong localStorage. Backend sử dụng các bảng `websites` và `uptime_check_logs` để lưu thông tin website và kết quả kiểm tra HTTP.

## API và kiểm tra website

- `GET /api/websites`: danh sách website.
- `GET /api/websites/{id}`: chi tiết website.
- `POST /api/websites`: thêm website.
- `PUT /api/websites/{id}`: cập nhật website.
- `DELETE /api/websites/{id}`: xóa website.
- `POST /api/websites/{id}/check`: gửi HTTP GET tới URL đã đăng ký và lưu kết quả.
- `GET /api/websites/{id}/logs`: tối đa 20 kết quả kiểm tra mới nhất.

HTTP 200–399 được tính là thành công; timeout là 10 giây. Giao diện tải lại dữ liệu mỗi 10 giây. Kiểm tra URL hiện được kích hoạt thủ công; chưa có scheduler hoặc gửi cảnh báo.

## Môi trường local hiện tại

Phiên backend trên máy phát triển đang sử dụng H2 lưu trong `data/qlwebsite-local.mv.db` thông qua cấu hình ghi đè khi khởi động. Cấu hình mặc định trong repository vẫn là SQL Server. Thư mục `data/`, log và các artifact build được loại khỏi Git.

Có thể mở thư mục gốc và thư mục `web/` thành hai project Maven trong NetBeans. Khi chạy backend, main class là `com.mycompany.do_an_ltm_2380600550.Do_An_LTM_2380600550`; giao diện dùng `vn.qlwebsite.QlWebsiteApplication`.
