# QLWebsite

Ứng dụng quản lý và giám sát website nội bộ bằng Java 17, Spring Boot và Thymeleaf, mở được bằng Apache NetBeans.

**Task 2 đã triển khai giao diện đen trắng:** dashboard, quản lý website, trang chi tiết, biểu đồ phản hồi, lịch sử kiểm tra, sự cố và cảnh báo. Dữ liệu demo lưu bằng localStorage để sử dụng ngay khi chưa có backend. Task 1 hiện là [kế hoạch và hợp đồng REST](docs/TASK_1_PLAN.md); scheduler, checker và gửi cảnh báo thật thuộc task 3, chưa triển khai.

## Chạy ứng dụng

Cần JDK 17 trở lên. Maven Wrapper tự tải Maven/dependency ở lần chạy đầu.

```sh
./mvnw spring-boot:run
```

Mở **http://127.0.0.1:8080**. Trên Windows dùng `mvnw.cmd spring-boot:run`.

Hoặc build và chạy JAR:

```sh
./mvnw clean verify
java -jar target/qlwebsite-0.1.0.jar
```

Đổi cổng: `PORT=8085 ./mvnw spring-boot:run`. Ứng dụng mặc định bind `127.0.0.1`. Dừng bằng `Ctrl+C`. File `.env.example` là mẫu; Spring Boot không tự nạp `.env`.

NetBeans: **File → Open Project**, chọn thư mục có `pom.xml`, cấu hình JDK 17+, sau đó **Run Project**. Không cần Node.js để chạy ứng dụng.

## Các màn hình

| Đường dẫn | Chức năng |
|---|---|
| `/` | Thống kê, website cần chú ý, biểu đồ theo website và 1h/24h/7 ngày, danh sách gần đây, cảnh báo |
| `/websites` | Tìm theo tên/URL, lọc trạng thái, phân trang, thêm/sửa, pause/resume, kiểm tra demo, archive có xác nhận |
| `/websites/{id}` | Cấu hình, trạng thái, kết quả gần nhất, số lỗi liên tiếp, biểu đồ, lịch sử có lọc/phân trang, lịch sử sự cố |
| `/alerts` | Thông báo mất kết nối và phục hồi, phân trang |
| `/login` | Màn hình bắt đầu demo; biểu mẫu đăng nhập khi bật chế độ API |

UI dùng CSS và JavaScript thuần, biểu đồ SVG cục bộ; không CDN, font online hay bước build frontend. Responsive cho desktop/mobile; hỗ trợ bàn phím, nhãn truy cập, modal giữ focus, thông báo lỗi, loading/empty state. Các sắc độ xám là sắc độ của đen trắng; trạng thái có chữ và biểu tượng, không dùng xanh/đỏ/vàng.

## Dữ liệu demo và nối backend

Mặc định `UI_DEMO=true`:

- Tạo dữ liệu mẫu trong lần mở đầu; website/history/sự cố/cảnh báo được lưu trong trình duyệt theo origin.
- Dữ liệu mẫu cố định, có history mỗi 30 phút trong 7 ngày để minh họa. Không phải kết quả đo thật, không phát sinh nền theo chu kỳ cấu hình.
- Thêm/sửa, pause/resume và archive có lưu lại; nút **Kiểm tra demo** chỉ thêm một mẫu mô phỏng, không truy cập URL. Website DOWN mẫu vẫn mô phỏng timeout.
- Website mới hoặc vừa bật lại có trạng thái UNKNOWN. Dữ liệu cũ có nhãn riêng, không bị chuyển thành DOWN.
- Demo không có đăng nhập bảo mật, không cần tài khoản, không gửi email hay thực hiện scheduler.
- Để reset demo: xóa riêng key `qlwebsite.demo.v1` trong DevTools → Application → Local Storage rồi tải lại. Dùng cửa sổ riêng tư nếu muốn một phiên dữ liệu mới.

Khi task 1 và task 3 sẵn sàng:

```sh
UI_DEMO=false ./mvnw spring-boot:run
```

Adapter trong `static/js/data.js` sẽ gọi `/api/...` theo [hợp đồng REST](docs/TASK_1_PLAN.md). Không tự rơi về dữ liệu demo khi API bị lỗi. Template nhận CSRF từ Spring Security; adapter gửi token, xử lý lỗi field, 401, timeout và HTTP lỗi. Backend cần cung cấp session/CSRF và bảo vệ route theo tài liệu trước khi dùng thực tế.

Dashboard tự tải sau mỗi 10 giây, không chồng request; tạm ngừng khi ẩn tab, mở modal hoặc đang ghi. Khi lỗi tải, giữ số liệu đã có và hiển thị thời điểm đồng bộ cuối. Biểu đồ có khoảng trống cho mẫu không có HTTP response, không thay bằng 0 ms. Tỷ lệ thành công tính theo số mẫu, không phải uptime SLA.

## Kiểm thử

```sh
./mvnw clean verify
```

7 kiểm thử Spring Boot xác nhận route, render template, static assets, chế độ demo/API và việc chưa cung cấp REST backend.

Kiểm thử UI tùy chọn bằng Node.js và Playwright (chỉ cần cho phát triển):

```sh
npm ci
npx playwright install chromium
# Chạy Spring Boot trong terminal khác, sau đó:
npm run test:ui
```

Có thể dùng Chrome sẵn có trên macOS, không cần tải Chromium:

```sh
CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm run test:ui
```

Đổi địa chỉ bằng `BASE_URL=http://127.0.0.1:8085 npm run test:ui`. Test dùng browser context mới, không sửa dữ liệu trình duyệt của người dùng. Bao gồm CRUD, validate, persistence, check mô phỏng, pause/resume, archive, lọc, phân trang, biểu đồ, trang rỗng/404, cảnh báo và mobile. Ảnh kiểm tra nằm ở `target/ui-screenshots/` (không commit).

Kết quả kiểm tra: **7 test Spring Boot và 13 nhóm test UI đều qua**. Test UI còn xác minh tự cập nhật tiếp tục sau khi đóng modal, giữ trạng thái cũ khi API lỗi, gửi CSRF, lỗi validation từ server và điều hướng khi hết phiên. Các phản hồi REST trong test được Playwright mô phỏng theo hợp đồng; chưa phải kiểm thử tích hợp với backend task 1/3.

## Cấu trúc

```text
src/main/java/vn/qlwebsite/
├── QlWebsiteApplication.java
└── controller/PageController.java
src/main/resources/
├── application.yml
├── templates/app.html
└── static/
    ├── css/app.css
    ├── js/app.js       # Render màn hình và thao tác UI
    ├── js/data.js      # Demo adapter, REST adapter, validation
    ├── js/chart.js     # SVG chart + tooltip bàn phím/chuột
    └── favicon.svg
src/test/java/vn/qlwebsite/controller/
docs/TASK_1_PLAN.md     # Kế hoạch database/service/API và DTO
scripts/ui-smoke.mjs    # Luồng kiểm thử trình duyệt
```

Các package entity/repository/service/monitoring/scheduler/alert vẫn dành cho task 1 và task 3. Không có dependency database hay checker giả trong Java.
