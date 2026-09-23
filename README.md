# QLWebsite

Khung dự án Java dành cho hệ thống quản lý và giám sát website nội bộ, phát triển bằng Apache NetBeans.

**Trạng thái hiện tại: chỉ chuẩn bị cấu trúc mã nguồn và công cụ build. Chưa triển khai các chức năng trong kế hoạch.**

## Môi trường

- JDK 17 trở lên (project biên dịch với Java 17).
- Apache NetBeans có hỗ trợ Maven và tương thích với JDK cài trên máy.
- Spring Boot 3.5.16, Spring Web và Thymeleaf.
- Maven Wrapper 3.9.11: không cần cài Maven riêng khi chạy bằng terminal.

Lần build đầu cần Internet để tải Maven và các dependency.

## Mở bằng NetBeans

1. Chọn **File → Open Project** và mở thư mục chứa `pom.xml`.
2. Chọn JDK 17 hoặc mới hơn trong Java Platforms và cấu hình của project.
3. Chọn **Clean and Build** để biên dịch.
4. Chọn **Run Project** để chạy Spring Boot; `nbactions.xml` đã khai báo action Run/Debug.

## Build và chạy bằng terminal

macOS/Linux:

```sh
./mvnw clean verify
./mvnw spring-boot:run
```

Windows:

```bat
mvnw.cmd clean verify
mvnw.cmd spring-boot:run
```

Hoặc chạy JAR sau khi build:

```sh
java -jar target/qlwebsite-0.1.0.jar
```

Ứng dụng khởi động tại `127.0.0.1:8080`. Hiện chưa có controller, trang giao diện hoặc API nên truy cập trình duyệt sẽ trả về **404**; đây là trạng thái dự kiến của khung ban đầu. Dừng bằng `Ctrl+C`.

Đổi port bằng biến môi trường `PORT`, ví dụ `PORT=8081 ./mvnw spring-boot:run`. File `.env.example` chỉ là mẫu; Spring Boot không tự nạp `.env`.

## Cấu trúc

```text
src/main/java/vn/qlwebsite/
├── QlWebsiteApplication.java  # Điểm khởi động ứng dụng
├── config/                   # Cấu hình
├── controller/               # REST API và page controller
├── dto/                      # Dữ liệu request/response
├── entity/                   # Entity
├── repository/               # Truy cập dữ liệu
├── service/                  # Nghiệp vụ
├── monitoring/               # Website checker
├── scheduler/                # Lập lịch kiểm tra
├── alert/                    # Cảnh báo
└── exception/                # Xử lý lỗi
src/main/resources/
├── application.yml
├── db/migration/             # SQL migration sau này
├── templates/                # Giao diện Thymeleaf
└── static/
    ├── css/
    └── js/
src/test/java/vn/qlwebsite/    # Kiểm thử sau này
```

Các thư mục trống dùng `.gitkeep` để được lưu trong Git. Dependency database, bảo mật, schema, entity, API, dashboard, checker và test nghiệp vụ sẽ được bổ sung khi bắt đầu triển khai.

## Git

- `target/`, dữ liệu chạy thử, cấu hình cá nhân, `.env` và file bí mật không được commit.
- File kế hoạch cá nhân được giữ cục bộ và đã được loại khỏi Git bằng `.gitignore`.
- GitHub Actions chạy `clean verify` trên Java 17 khi push hoặc mở pull request. Hiện chưa có test nghiệp vụ; bước verify kiểm tra việc biên dịch và đóng gói.
