# Giao diện QLWebsite

Giao diện dashboard, quản lý website, lịch sử kiểm tra, biểu đồ và cảnh báo kết nối với API của ứng dụng backend ở thư mục gốc. Mặc định sử dụng dữ liệu thật từ backend và tắt demo.

## Chạy

Khởi động backend tại `http://127.0.0.1:8082` theo hướng dẫn trong README ở thư mục gốc. Sau đó, từ thư mục gốc repository:

```sh
./mvnw -f web/pom.xml verify
java -jar web/target/qlwebsite-0.1.0.jar
```

Mở `http://127.0.0.1:8080`. Đổi URL backend bằng `BACKEND_URL`, hoặc truyền `--app.backend.url=http://127.0.0.1:8082` khi chạy JAR.

Các thao tác thêm/sửa website, bật/tắt theo dõi và kiểm tra thủ công gọi API qua `/backend-api`. Kết quả HTTP được lưu bởi backend trong database đã cấu hình. Không tạo website mẫu hoặc nhập dữ liệu localStorage vào database.

Backend hiện cung cấp tối đa 20 kết quả gần nhất mỗi website. Giao diện tải lại dữ liệu mỗi 10 giây; kiểm tra HTTP được kích hoạt bằng nút “Kiểm tra ngay”. Chức năng kiểm tra theo lịch chưa được triển khai.

Trang `/alerts` và khối Cảnh báo gần đây trên dashboard hiển thị cảnh báo DOWN/RECOVERY đã lưu trong database. Danh sách cảnh báo được phân trang qua `GET /backend-api/alerts`, mới nhất trước, và có liên kết tới chi tiết website.

`./mvnw -f web/pom.xml verify` chạy các kiểm thử API proxy và cấu hình dữ liệu thật mặc định.
