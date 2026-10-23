# QLWebsite

Ứng dụng giám sát website bằng Java 17, Spring Boot, Spring Data JPA, Spring Security và Thymeleaf. Backend ở thư mục gốc; giao diện ở `web/`. Dữ liệu thật được lưu trong SQL Server hoặc H2, không nhập dữ liệu demo vào database.

## Chạy nhanh trên máy cá nhân

Cần JDK 17 trở lên. Trên macOS/Linux:

```sh
./scripts/run-local.sh
```

Script build hai ứng dụng, chạy backend tại `http://127.0.0.1:8082` và giao diện tại **http://127.0.0.1:8080**, sử dụng H2 lưu tại `data/qlwebsite-local.mv.db`. Nhấn Ctrl+C để dừng cả hai. Mở `/login` và đăng nhập bằng tài khoản mặc định **`admin` / `admin123`**. Hệ thống tự tạo ADMIN khi database chưa có tài khoản; không cần bước đăng ký quản trị viên. Khởi động lại không đặt lại mật khẩu hoặc ghi đè tài khoản đã có. Tài khoản mới tạo trong trang quản trị dùng mật khẩu từ 10 ký tự và tối đa 72 byte UTF-8.

Để chạy từng ứng dụng riêng (Windows dùng `mvnw.cmd`):

```sh
./mvnw verify
java -jar target/qlwebsite-0.1.0.jar --spring.profiles.active=h2
```

```sh
./mvnw -f web/pom.xml verify
java -jar web/target/qlwebsite-0.1.0.jar
```

Bỏ `--spring.profiles.active=h2` để dùng SQL Server và đặt mật khẩu bằng biến môi trường `SPRING_DATASOURCE_PASSWORD`. Có thể ghi đè địa chỉ và tài khoản bằng `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`. Đổi URL backend của giao diện bằng `BACKEND_URL`. File `.env.example` mô tả các biến; ứng dụng không tự nạp file `.env`.

## Chức năng

1. **Giám sát tự động:** chu kỳ riêng từng website, từ 10 giây đến 24 giờ. Kiểm tra chạy trên backend khi trình duyệt đóng. Có kiểm tra thủ công, tạm dừng và lưu trữ mềm để giữ lịch sử. Worker có giới hạn, không xếp chồng kiểm tra theo lịch cùng website; lịch quá hạn được kiểm tra khi backend chạy trở lại.
2. **Cấu hình giám sát:** timeout 100–60000 ms, ngưỡng phản hồi chậm, mã HTTP chấp nhận như `200-299, 301`, ngưỡng 1–10 lỗi liên tiếp để xác nhận DOWN. Một lần thành công xóa bộ đếm lỗi. SLOW vẫn là lần kiểm tra thành công.
3. **Cảnh báo trong ứng dụng:** sự cố DOWN và phục hồi hiển thị tại mục Cảnh báo, không cần SMTP, tài khoản email hay bot Telegram. Trang Kênh thông báo và API cấu hình/gửi thử đã được gỡ; `/settings` cũ chuyển tới `/alerts`. Gửi ra ngoài bị tắt, hàng đợi cũ chuyển CANCELLED khi khởi động; lịch sử đã gửi được giữ trong database.
4. **Sự cố:** mở khi đủ ngưỡng lỗi, tự đóng khi phục hồi; lưu lần lỗi đầu, thời điểm xác nhận, kết thúc, nguyên nhân và ghi chú xử lý. Tạm dừng, đổi URL hoặc lưu trữ sẽ kết thúc sự cố với trạng thái MONITORING_STOPPED.
5. **Báo cáo:** lịch sử phân trang ở database, lọc thành công/thất bại và thời gian; biểu đồ 1 giờ/24 giờ/7 ngày/30 ngày; phản hồi trung bình, P95, số sự cố và thời gian gián đoạn đã ghi nhận. Xuất CSV gồm thống kê và các mẫu chi tiết, có xử lý ô bắt đầu bằng ký tự công thức. Tối đa 93 ngày và 100.000 mẫu cho một báo cáo; hệ thống yêu cầu thu hẹp khoảng thời gian nếu vượt giới hạn.
6. **SSL:** kiểm tra chuỗi chứng chỉ và hostname bằng HTTPS của Java, ghi ngày hết hạn từ phiên TLS. Cảnh báo ở ngưỡng cấu hình và các mốc 14/7/0 ngày, chống lặp theo chứng chỉ/mốc; cảnh báo riêng khi xác thực TLS lỗi. Kết quả SSL cập nhật cùng lần kiểm tra HTTPS. Website HTTP không có thông tin SSL.
7. **Tài khoản:** ADMIN quản lý toàn bộ và tài khoản; OPERATOR quản lý website, sự cố, CSV, bảo trì; VIEWER chỉ xem và xuất báo cáo. Mật khẩu BCrypt, session HttpOnly/SameSite, CSRF cho thao tác ghi. Khóa tài khoản/thay đổi vai trò có hiệu lực ở yêu cầu tiếp theo. Không cho khóa/hạ quyền quản trị viên hoạt động cuối cùng.
8. **Bảo trì:** thêm/xóa khoảng bảo trì tại chi tiết website; hệ thống bỏ qua kiểm tra và không gửi cảnh báo downtime trong khoảng đó, hiển thị MAINTENANCE. Sau bảo trì, kiểm tra tự động tiếp tục. Báo cáo trừ phần bảo trì khỏi thời gian sự cố và hợp nhất các khoảng bảo trì chồng nhau.
9. **Nhóm/tag:** nhóm/dự án, môi trường và tag được lưu trong database, hiển thị trong danh sách/chi tiết; tìm kiếm theo tên, URL và các trường này.
10. **Nhập CSV:** tải mẫu từ nút Nhập CSV, chọn file UTF-8, xem trước rồi nhập. Cột `url` bắt buộc; tùy chọn `name,groupName,environment,tags`. Tối đa 500 dòng/1 MB. Hỗ trợ dấu phẩy trong ngoặc kép, BOM và tiếng Việt. URL trùng hoặc dữ liệu sai được báo theo dòng; không ghi một phần file bị lỗi. Website nhập mặc định ngưỡng lỗi 3 lần.
11. **Từ khóa và JSON:** chọn kiểu HTTP trong form thêm/sửa website. Từ khóa hỗ trợ phải chứa/không được chứa, tùy chọn phân biệt hoa/thường; mặc định không phân biệt, tham khảo [Better Stack](https://betterstack.com/docs/uptime/keyword-monitor/). JSON dùng JSON Pointer RFC 6901 (ví dụ `/data/status`, `/items/0/id`), kiểm tra tồn tại hoặc bằng giá trị JSON đúng kiểu (`"ok"`, `true`, `200`, `null`, object/array). Tất cả điều kiện bật và mã HTTP phải cùng đạt. HTTP 200 nhưng nội dung sai vẫn tính lỗi, áp dụng ngưỡng DOWN/RECOVERY. Nội dung tối đa 2 MiB, timeout bao gồm đọc body; không thực thi JavaScript và không lưu body phản hồi.
12. **DNS và TCP:** chọn kiểu DNS/TCP trong form thêm/sửa; tên miền/IP lấy từ phần host của URL HTTP/HTTPS. DNS kiểm tra A/AAAA qua bộ phân giải hệ điều hành (có cache), tùy chọn danh sách IP mong đợi; tất cả IP này phải xuất hiện. TCP kết nối cổng 1–65535, thử các IP trong một timeout chung; chỉ xác nhận mở kết nối TCP. Có thể thêm cùng URL với kiểu, cổng hoặc loại bản ghi khác để giám sát song song. Lịch chạy, bảo trì, cảnh báo và lịch sử dùng chung; log ghi kiểu kiểm tra, IP/cổng và nguyên nhân lỗi. Website cũ giữ kiểu HTTP, kiểm tra nội dung mặc định tắt.
13. **Quét liên kết hỏng:** tại chi tiết website, chọn Bắt đầu quét; có danh sách 10 báo cáo gần nhất (báo cáo cũ vẫn giữ trong DB). Quét `a[href]`/`area[href]`, URL tương đối, loại trùng và bỏ fragment; chỉ duyệt tiếp HTML cùng nguồn đến 2 cấp, tùy chọn kiểm tra liên kết ngoài. Giới hạn 1–20 trang HTML, 1–200 URL, 5 chuyển hướng/URL, 5 giây/yêu cầu (hoặc timeout website nếu nhỏ hơn), 90 giây/lần quét. Dùng HEAD cho đích không duyệt tiếp, thử GET nếu HEAD trả 405/501. Báo cáo hiển thị trang nguồn, URL đích và mã HTTP: 401/403/429 cần xem lại, lỗi kết nối tách riêng với HTTP 4xx/5xx hỏng. Kết quả quét độc lập với incident uptime; không chạy JavaScript, không kiểm tra ảnh/script hay anchor fragment. Hai worker, hàng đợi 4, một lần quét đồng thời/website; lần dở dang được đánh dấu khi khởi động lại. VIEWER xem báo cáo, ADMIN/OPERATOR chạy quét.

File CSV mẫu: [websites-sample.csv](web/src/main/resources/static/samples/websites-sample.csv), cũng tải được bằng nút **Nhập CSV → Tải CSV mẫu** trong giao diện. File UTF-8 có BOM, gồm 5 website và các cột `name,url,groupName,environment,tags`. File chỉ được nhập sau khi bạn xem trước và xác nhận; URL trùng sẽ bị từ chối.

Dashboard tự tải lại mỗi 10 giây; đây là chu kỳ cập nhật giao diện, độc lập chu kỳ kiểm tra HTTP. Tỷ lệ thành công = số mẫu thành công / tổng số mẫu, **không phải uptime SLA theo thời gian**. Thời gian gián đoạn được suy ra từ các sự cố đã ghi nhận, không khẳng định trạng thái trong thời gian backend dừng. Thời điểm mới lưu theo UTC, giao diện hiển thị giờ địa phương.

Khi nâng cấp database, Hibernate thêm bảng/cột; dữ liệu giám sát cũ được giữ. Website cũ mặc định timeout 10 giây, chấp nhận HTTP 200–399 và ngưỡng lỗi 1 để giữ hành vi cũ. Lịch sử cũ được dựng lại thành trạng thái/sự cố khi chưa có trạng thái mới. Nên sao lưu database trước khi nâng cấp. Các timestamp cũ không có thông tin múi giờ được diễn giải theo UTC.

## Thông báo

Ứng dụng chỉ hiển thị cảnh báo nội bộ tại `/alerts`; bạn không cần thiết lập dịch vụ gửi ngoài. Thông tin SSL vẫn nằm trong chi tiết website. Trang Email/Telegram đã được gỡ, scheduler không gửi thông báo ra ngoài. Dữ liệu lịch sử gửi cũ được giữ trong database; các bản ghi đang chờ được hủy khi khởi động. `app.notifications.external-enabled=false` ngăn tạo hàng đợi mới và gửi lại thông báo cũ.

Triển khai mặc định dành cho **một backend**; scheduler kiểm tra chạy trong tiến trình đó. Mặc định hai ứng dụng chỉ lắng nghe localhost. Dùng HTTPS và đặt `SERVER_SERVLET_SESSION_COOKIE_SECURE=true` cho backend khi triển khai qua HTTPS. `MONITOR_JOBS_ENABLED=false` ngừng kiểm tra tự động.

## API chính

Mọi API riêng yêu cầu session; thao tác ghi yêu cầu `X-CSRF-TOKEN` lấy từ `GET /api/csrf` với cùng cookie. Giao diện chuyển tiếp qua `/backend-api`.

- `GET|POST|DELETE /api/session`: phiên hiện tại, đăng nhập JSON, đăng xuất.
- `GET|POST /api/users`, `PUT /api/users/{id}`: quản lý tài khoản ADMIN.
- `GET|POST /api/websites`, `GET|PUT|DELETE /api/websites/{id}`: quản lý và lưu trữ website.
- `POST /api/websites/{id}/check`: kiểm tra thủ công.
- `GET /api/websites/{id}/logs`: 20 mẫu gần nhất để hiển thị trạng thái mới nhất.
- `GET /api/websites/{id}/history?from=...&to=...&page=0&size=8&result=FAILURE`: lịch sử đầy đủ, phân trang.
- `GET /api/websites/{id}/metrics?from=...&to=...&bucket=1h`: báo cáo; bucket `5m`, `30m`, `1h`, `1d`.
- `GET /api/websites/{id}/incidents`, `PATCH /api/incidents/{id}` với `{"notes":"..."}`.
- `GET|POST /api/websites/{id}/maintenance`, `DELETE /api/websites/{id}/maintenance/{windowId}`. POST nhận `title`, `startsAt`, `endsAt` ISO-8601 có múi giờ.
- `POST /api/websites/import/preview`, `POST /api/websites/import`: JSON `{"csv":"..."}`.
- `GET /api/dashboard/summary`, `GET /api/reports/export?websiteId=...&from=...&to=...`.
- `GET /api/alerts?page=0&size=8&websiteId=...&eventType=DOWN`: cảnh báo phân trang/lọc.

## Kiểm thử

```sh
./mvnw verify
./mvnw -f web/pom.xml verify
```

Kiểm thử sử dụng H2 trong bộ nhớ, HTTP server cục bộ, HTTP/TLS và mail giả lập; không tác động database người dùng và không gửi thông báo thật. Bao gồm ngưỡng lỗi/phục hồi, concurrency và lịch chạy, rollback, bảo trì, CSV, thống kê trên hơn 20 mẫu, P95, báo cáo, phân quyền, CSRF, session, tài khoản, SSL và outbox gửi lại. Kiểm thử web xác minh proxy cookie/CSRF, tải CSV, danh sách cảnh báo và route mới.
