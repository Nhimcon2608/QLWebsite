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

Giao diện sử dụng dữ liệu từ database của backend. Không tạo dữ liệu mẫu trong localStorage. Backend sử dụng các bảng `websites`, `uptime_check_logs` và `website_alerts` để lưu thông tin website, kết quả kiểm tra HTTP và cảnh báo.

## API và kiểm tra website

- `GET /api/websites`: danh sách website.
- `GET /api/websites/{id}`: chi tiết website.
- `POST /api/websites`: thêm website.
- `PUT /api/websites/{id}`: cập nhật website.
- `DELETE /api/websites/{id}`: xóa website.
- `POST /api/websites/{id}/check`: gửi HTTP GET tới URL đã đăng ký và lưu kết quả.
- `GET /api/websites/{id}/logs`: tối đa 20 kết quả kiểm tra mới nhất.
- `GET /api/alerts?page=0&size=8`: cảnh báo phân trang, mới nhất trước. Có thể lọc theo `websiteId` và `eventType=DOWN` hoặc `RECOVERY`; `size` từ 1 đến 100.

HTTP 200–399 được tính là thành công; timeout là 10 giây. Giao diện tải lại dữ liệu mỗi 10 giây. Kiểm tra URL hiện được kích hoạt thủ công; chưa có scheduler.

## Cảnh báo trong ứng dụng

Mỗi lần kiểm tra đầu tiên thất bại hoặc chuyển từ UP sang DOWN tạo một cảnh báo DOWN. Khi website chuyển từ DOWN sang UP, hệ thống tạo cảnh báo RECOVERY. Các lần kiểm tra liên tiếp cùng trạng thái không tạo thông báo trùng. Website tạm dừng sẽ bị từ chối khi gọi API kiểm tra.

Kết quả kiểm tra và cảnh báo được ghi trong cùng một transaction. Khóa database theo từng website và ràng buộc duy nhất trên `check_log_id` bảo đảm các yêu cầu kiểm tra đồng thời không tạo cảnh báo trùng.

Khi nâng cấp database có sẵn, hệ thống dựng lại các cảnh báo từ toàn bộ lịch sử kiểm tra thực tế, giữ thời điểm của kết quả gốc. Khởi động lại không tạo bản ghi trùng. Trang Cảnh báo và khối Cảnh báo gần đây trên dashboard lấy dữ liệu từ API; thông báo được lưu bền vững trong `website_alerts`.

Tính năng này hiển thị thông báo trong ứng dụng, chưa gửi email hoặc thông báo ra dịch vụ ngoài.

## Kiểm thử

`./mvnw verify` chạy 9 kiểm thử tích hợp bằng database H2 trong bộ nhớ và máy chủ HTTP cục bộ, bao gồm DOWN/RECOVERY, lỗi kết nối, chống trùng, phân trang/lọc, website tạm dừng, khôi phục lịch sử và rollback khi lưu cảnh báo lỗi. Dữ liệu người dùng không được sử dụng trong kiểm thử.

`./mvnw -f web/pom.xml verify` chạy 3 kiểm thử giao diện/API proxy, bao gồm chuyển tiếp phân trang và truyền lỗi backend. GitHub Actions kiểm tra cả hai ứng dụng.

## Môi trường local hiện tại

Phiên backend trên máy phát triển đang sử dụng H2 lưu trong `data/qlwebsite-local.mv.db` thông qua cấu hình ghi đè khi khởi động. Cấu hình mặc định trong repository vẫn là SQL Server. Thư mục `data/`, log và các artifact build được loại khỏi Git.

Có thể mở thư mục gốc và thư mục `web/` thành hai project Maven trong NetBeans. Khi chạy backend, main class là `com.mycompany.do_an_ltm_2380600550.Do_An_LTM_2380600550`; giao diện dùng `vn.qlwebsite.QlWebsiteApplication`.
