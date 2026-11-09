# Hướng dẫn cài Redis cho RailJet

Từ phiên bản này, ứng dụng cần **Redis** để:

- **Giữ ghế:** khách bấm chọn ghế là ghế được khoá 15 phút trong Redis. Khi khách bấm thanh toán VNPay, khoá được gia hạn tới hết hạn link VNPay (15 phút) cộng 5 phút dự phòng. Hết hạn thì Redis tự xoá khoá, ghế trống lại.
- **Giới hạn request (rate limit):** đăng nhập, đăng ký, quên mật khẩu, OCR, chọn ghế, thanh toán, tìm chuyến.

Không có Redis thì ứng dụng vẫn khởi động được, nhưng **không đặt vé được**: trang chọn ghế báo "Hệ thống giữ ghế đang bận". Rate limit sẽ tự bỏ qua.

## 1. Chạy Redis

### Cách 1: Docker (khuyên dùng)

```bash
docker run -d --name redis -p 6379:6379 --restart unless-stopped redis:7
```

Kiểm tra:

```bash
docker exec -it redis redis-cli ping
# PONG
```

### Cách 2: Không có Docker (Windows)

Cài **Memurai** (bản Redis cho Windows, có gói Developer miễn phí): https://www.memurai.com/get-memurai

Sau khi cài, Memurai chạy như một Windows service ở cổng 6379. Kiểm tra bằng `memurai-cli ping`.

> Cần Redis **6.0 trở lên**, vì code dùng `SET ... KEEPTTL`.

## 2. Cấu hình

Mặc định ứng dụng kết nối `localhost:6379`, database 0, không mật khẩu. Muốn đổi thì thêm vào file `.env` ở thư mục gốc dự án:

```properties
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
# Tắt rate limit khi cần test thủ công nhiều lần (giữ ghế vẫn cần Redis)
RATE_LIMIT_ENABLED=true
```

## 3. Chạy test

Test giữ ghế và rate limit chạy với **Redis thật** (không mock):

- `RedisSeatHoldServiceTest`
- `RedisRateLimitTest`

Hai test này nằm trong `src/test/java/com/hsf302/trainoffice/redis`. Chúng dùng **database 15** và tiền tố key riêng (`rjtest:<uuid>:`), xoá sạch dữ liệu sau khi chạy nên không đụng dữ liệu của ứng dụng.

```bash
mvnw test
```

Redis chưa chạy thì hai test này báo lỗi kèm hướng dẫn lệnh `docker run`. Đổi máy chủ hoặc database cho test bằng biến môi trường `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_TEST_DATABASE`.

## 4. Xem dữ liệu trong Redis

```bash
docker exec -it redis redis-cli
```

| Lệnh | Ý nghĩa |
|---|---|
| `KEYS rj:hold:*` | Các lượt giữ ghế: `rj:hold:u:<userId>` là khách đang chọn, `rj:hold:b:<bookingId>` là đơn chờ thanh toán |
| `HGETALL rj:hold:u:5` | Chuyến, chặng, danh sách ghế khách 5 đang giữ |
| `PTTL rj:hold:u:5` | Thời gian giữ còn lại (ms). Đây cũng là số giây đồng hồ hiển thị trên trang |
| `KEYS rj:seat:{t12}:*` | Khoá từng đoạn của các ghế trên chuyến 12. Value là chủ khoá (`u:5` hoặc `b:40`) |
| `KEYS rj:rl:*` | Bộ đếm rate limit |

Lệnh `KEYS` quét toàn bộ dữ liệu, chỉ dùng khi xem ở máy dev.

## 5. Luồng giữ ghế

1. **Trang chọn ghế:** bấm một ghế thì ghế được khoá trong Redis và đồng hồ bắt đầu đếm 15 phút. Ghế chọn thêm dùng chung hạn này; bỏ chọn rồi chọn lại cũng không được thêm giờ. Người khác thấy ghế này là "không còn trống".
2. **Tải lại trang:** ghế đang giữ vẫn được chọn sẵn, đồng hồ lấy đúng thời gian còn lại từ Redis.
3. **Nhập hành khách, xác nhận:** dùng tiếp hạn cũ, đồng hồ vẫn hiển thị.
4. **Tạo đơn:** khoá chuyển từ khách sang đơn, giữ nguyên thời gian còn lại.
5. **Bấm thanh toán VNPay:** khoá được gia hạn tới hết hạn link VNPay cộng 5 phút.
   - Thanh toán thành công: vé CONFIRMED trong DB, khoá Redis bị xoá.
   - Thanh toán thất bại: khoá quay về hạn giữ chỗ ban đầu.
6. **Hết giờ:** trang đang mở hiện popup "Hết thời gian giữ ghế" và tự chuyển về trang tìm vé sau 10 giây. Job chạy mỗi phút sẽ huỷ các đơn chờ thanh toán đã hết khoá.

Mỗi tài khoản tối đa **2 đơn chưa thanh toán** cùng lúc.

## 6. Bảng giới hạn request

Sửa trong `src/main/java/com/hsf302/trainoffice/ratelimit/RateLimitRules.java`.

| Endpoint | Giới hạn |
|---|---|
| `POST /login` | 10/phút/IP và 10/15 phút/email |
| `POST /register` | 5/giờ/IP |
| `POST /forgot-password` | 3/15 phút/email |
| `POST /forgot-password`, `/reset-password/resend` | 10/giờ/IP |
| `POST /reset-password` | 10/15 phút/IP |
| `POST /booking/ocr-cccd` | 10/phút/tài khoản |
| Giữ / bỏ ghế (`/api/booking/holds/seats`) | 60/phút/tài khoản |
| `POST /booking/seat-selection`, `/booking/confirmation` | 10/phút/tài khoản |
| `POST /payments/bookings/{id}` | 5/phút/tài khoản |
| Tìm chuyến | 60/phút/IP |

Vượt giới hạn:
- API JSON trả **429** kèm header `Retry-After`.
- Trang thường hiển thị trang "Quá nhiều yêu cầu".

Nếu chạy sau reverse proxy (nginx), cần thêm `server.forward-headers-strategy=native` để lấy đúng IP của khách.
