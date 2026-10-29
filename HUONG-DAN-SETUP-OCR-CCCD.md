# Hướng dẫn setup tính năng OCR đọc CCCD

Tài liệu này mô tả toàn bộ cách cài đặt, cách hoạt động, cách xử lý lỗi và những đánh đổi (trade-off) của tính năng **quét Căn cước công dân (CCCD) để tự điền thông tin hành khách** khi đặt vé.

Tính năng dùng **Tesseract OCR** (qua thư viện `tess4j`) chạy **hoàn toàn cục bộ trên server** — không gọi dịch vụ ngoài, không gửi ảnh CCCD lên internet.

---

## 1. Kiến trúc tổng quan

```
Trình duyệt (passenger-info.html)
   │  ① người dùng bấm "Quét CCCD tự điền", chọn/chụp ảnh
   │  ② gửi ảnh qua AJAX: POST /booking/ocr-cccd  (multipart "image")
   ▼
OcrController  ──►  OcrService / OcrServiceImpl
   │                   ├─ Tiền xử lý ảnh (java.awt): grayscale, upscale, crop, nhị phân hóa
   │                   ├─ Lượt 1 (TEXT pass): quét cả thẻ  → tên, giới tính, ngày sinh có nhãn
   │                   ├─ Lượt 2 (DIGITS pass): crop vùng số + whitelist số → số CCCD, ngày sinh
   │                   └─ Bóc tách field bằng regex
   ▼
Trả JSON (CccdOcrResult) ──► JS tự điền vào các ô: fullName, identityNumber, dateOfBirth, gender
```

Các file liên quan:

| File | Vai trò |
|------|---------|
| `controller/OcrController.java` | Endpoint `POST /booking/ocr-cccd` |
| `service/OcrService.java` | Interface |
| `service/impl/OcrServiceImpl.java` | Toàn bộ logic xử lý ảnh + OCR + bóc tách |
| `dto/CccdOcrResult.java` | Kết quả trả về (JSON) |
| `templates/booking/passenger-info.html` | Nút quét + overlay loading + script AJAX tự điền |
| `tessdata/vie.traineddata`, `tessdata/eng.traineddata` | Dữ liệu ngôn ngữ cho Tesseract |
| `pom.xml` | Dependency `net.sourceforge.tess4j:tess4j:5.13.0` |
| `application.properties` | Cấu hình đường dẫn tessdata, ngôn ngữ, vùng crop |

---

## 2. Setup từ đầu đến cuối

### Bước 1 — Dependency (đã có sẵn trong `pom.xml`)

```xml
<!-- Tesseract OCR engine (bundles native libs for Windows x64) for CCCD scanning -->
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.13.0</version>
</dependency>
```

> `tess4j` đã đóng gói sẵn thư viện native (`.dll`) cho **Windows x64**, nên trên Windows không cần cài Tesseract riêng.
> Nếu chạy trên **Linux/macOS** hoặc server production, cần cài Tesseract hệ thống (`apt install tesseract-ocr`, `brew install tesseract`) — xem mục [6](#6-điều-kiện-để-chạy-được).

### Bước 2 — Dữ liệu ngôn ngữ `tessdata/`

Thư mục `tessdata/` ở **gốc project** phải chứa:

- `vie.traineddata` — tiếng Việt (bắt buộc)
- `eng.traineddata` — tiếng Anh (hỗ trợ nhãn "Full name", số)

Đường dẫn được cấu hình tương đối so với thư mục chạy app (`ocr.tessdata-path=tessdata`), nên **phải chạy app từ gốc project** để tìm thấy thư mục này.

> ⚠️ Hai file này nặng (~15MB + ~12MB). Cân nhắc có commit vào Git hay không (xem mục [8](#8-trade-off-tổng-hợp)).

### Bước 3 — Cấu hình `application.properties`

```properties
# OCR (Tesseract) for CCCD scanning
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
ocr.tessdata-path=tessdata
ocr.language=vie+eng

# Vùng crop (theo tỉ lệ của thẻ đã cắt khung) cho lượt đọc số/ngày sinh.
# Chỉnh các giá trị này nếu ngày sinh bị cắt mất hoặc còn nhiễu.
ocr.dob-crop.x=0.30
ocr.dob-crop.y=0.28
ocr.dob-crop.w=0.70
ocr.dob-crop.h=0.45
```

| Khóa | Ý nghĩa |
|------|---------|
| `spring.servlet.multipart.max-file-size` | Giới hạn kích thước ảnh upload (ảnh điện thoại dễ vượt 5MB) |
| `ocr.tessdata-path` | Đường dẫn tới thư mục `tessdata` |
| `ocr.language` | Ngôn ngữ nhận dạng, `vie+eng` = tiếng Việt + Anh |
| `ocr.dob-crop.*` | **Vùng crop cho lượt đọc số** (x, y, rộng, cao tính theo tỉ lệ 0–1) |

### Bước 4 — Chạy & kiểm tra

```powershell
.\mvnw.cmd spring-boot:run
```

Rồi vào luồng đặt vé:
`http://localhost:8081/booking/search` → chọn chuyến → chọn ghế → **trang nhập thông tin hành khách** → bấm **"Quét CCCD tự điền"**.

Khi quét, server in ra console **log cả 2 lượt** (rất hữu ích để debug):

```
===== CCCD OCR text pass =====
...văn bản thô lượt 1...
===== CCCD OCR digits pass =====
...văn bản thô lượt 2 (chỉ số)...
```

---

## 3. Cách hoạt động — vì sao phải quét **2 lần**

Đây là phần cốt lõi và là quyết định thiết kế quan trọng nhất.

### Vấn đề của việc quét 1 lần (lý do ban đầu không đọc được số)

Khi đưa **nguyên tấm thẻ CCCD** vào Tesseract một lần duy nhất:

- Mặt trước CCCD có **rất nhiều hoa văn trang trí, họa tiết trống đồng, chữ chìm (micro-text), quốc huy, ảnh chân dung**.
- Tesseract cố đọc **tất cả** những thứ đó như thể là chữ → sinh ra một mớ ký tự rác.
- Dãy **số CCCD (12 chữ số)** và **ngày sinh** bị lẫn trong đống nhiễu đó, và thường bị đọc nhầm số thành chữ (ví dụ `0`→`O`, `1`→`l`, `8`→`B`).

→ Kết quả: **tên có thể đọc tạm được, nhưng dãy số gần như luôn sai**.

### Giải pháp đã chọn: 2 lượt quét chuyên biệt

Thay vì ép một lượt quét làm tốt mọi thứ, ta tách thành **2 lượt**, mỗi lượt tối ưu cho một mục tiêu:

#### Lượt 1 — TEXT pass (đọc chữ)
```java
BufferedImage gray = toGrayUpscaled(original, 1200);
textPass = ocr(gray, false);
```
- Lấy **cả thẻ**, chuyển **grayscale**, phóng to cạnh ngắn lên ≥ 1200px.
- Dùng để lấy **họ tên**, **giới tính**, và **ngày sinh ở dòng có nhãn "sinh"/"birth"**.

#### Lượt 2 — DIGITS pass (đọc số) ⭐
```java
BufferedImage region = toGrayUpscaled(cropRegion(original), 1600);
BufferedImage regionBinary = binarize(region);
digitsPass = ocr(regionBinary, true);
```
- **Crop** chỉ lấy **cột bên phải chứa Số / Ngày sinh / Giới tính** (bỏ ảnh chân dung + quốc huy — nguồn nhiễu chính).
- Phóng to mạnh hơn (≥ 1600px), rồi **nhị phân hóa Otsu** (chuyển hẳn về đen/trắng để tách chữ khỏi nền hoa văn).
- Trong lượt này, Tesseract bị **giới hạn chỉ được xuất ra chữ số**:
  ```java
  tesseract.setPageSegMode(6);                                  // 1 khối văn bản đồng nhất
  tesseract.setTessVariable("tessedit_char_whitelist", "0123456789/.-");
  ```
  → Số **không còn bị đọc nhầm thành chữ** nữa, vì bảng ký tự cho phép chỉ có số và dấu phân cách ngày.

### Tại sao cách này hiệu quả
Việc **cắt bỏ vùng hoa văn/ảnh** + **nhị phân hóa** + **whitelist chữ số** loại bỏ gần như toàn bộ nguyên nhân gây lỗi ở lần đọc đầu. Lượt số giờ chỉ "nhìn" vào vùng có số trên nền trắng sạch, nên đọc dãy 12 số chính xác hơn hẳn.

### Ghép kết quả 2 lượt
```java
String id   = firstNonNull(extractId(digitsPass), extractId(textPass)); // ưu tiên lượt số
LocalDate dob = extractDateOfBirth(textPass, digitsPass);               // ưu tiên dòng có nhãn "sinh"
String gender = extractGender(textPass);                                // "nam"/"nữ" từ lượt chữ
String fullName = extractFullName(textPass);                            // tên từ lượt chữ
```

- **Số CCCD**: ưu tiên lượt số, fallback về lượt chữ. Tìm 12 số liên tiếp (CCCD mới), nếu không có thì tìm 9 số (CMND cũ).
- **Ngày sinh**: ưu tiên ngày nằm trên dòng có chữ "sinh"/"birth"; nếu không, lấy **ngày hợp lệ sớm nhất** trong cả 2 lượt (ngày sinh luôn sớm hơn ngày cấp/hết hạn).
- **Giới tính**: dò từ "nam"/"nữ".
- Chỉ cần tìm được **ít nhất một** trường (id/dob/tên) thì coi là `success = true`.

---

## 4. Xử lý khi lần đầu KHÔNG đọc được số/ảnh

Đây là tình huống thực tế hay gặp và cách xử lý tương ứng:

### Trình tự xử lý theo mức độ

1. **Luôn cho phép sửa tay.** Kết quả OCR chỉ là *best-effort* — trường nào không nhận được để `null`, không ghi đè. Thông báo luôn nhắc: *"Vui lòng kiểm tra lại các trường trước khi tiếp tục."* → Người dùng không bao giờ bị kẹt.

2. **Nếu đọc được chữ nhưng số sai/thiếu** → chính là lý do có **lượt 2 (DIGITS pass)**. Nếu lượt 2 vẫn chưa ra số, cần **chỉnh vùng crop**:
   - Mở log console, xem khối `===== CCCD OCR digits pass =====`.
   - Nếu phần số bị **cắt mất** → tăng `ocr.dob-crop.w` / `ocr.dob-crop.h`, hoặc giảm `ocr.dob-crop.x` / `ocr.dob-crop.y`.
   - Nếu còn **dính ảnh chân dung** (nhiễu) → tăng `ocr.dob-crop.x` để crop sâu hơn về bên phải.
   - Các giá trị này chỉnh trong `application.properties`, **không cần biên dịch lại**.

3. **Nếu hoàn toàn không ra gì** → thông báo: *"Không bóc tách được thông tin. Hãy nhập tay hoặc chụp lại rõ hơn."* và hướng dẫn người dùng chụp lại đúng khung (mục [6](#6-điều-kiện-để-chạy-được)).

4. **Nếu lỗi native/thiếu tessdata** → bắt `Throwable` (kể cả `UnsatisfiedLinkError`) và trả thông báo: *"OCR lỗi: … Kiểm tra thư mục tessdata và file vie.traineddata."*

### Bảng chẩn đoán nhanh

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|-----------|------------------------|-----------|
| Đọc được tên, sai số CCCD | Vùng crop chưa trúng / ảnh mờ | Chỉnh `ocr.dob-crop.*`, chụp nét hơn |
| Ngày sinh sai (lấy nhầm ngày cấp) | Nhãn "sinh" không đọc được | Chụp rõ dòng ngày sinh; logic đã ưu tiên ngày sớm nhất |
| Không ra gì cả | Ảnh nghiêng/thiếu sáng/sai khung | Chụp lại đúng khung, đủ sáng |
| `UnsatisfiedLinkError` / lỗi native | Thiếu tessdata hoặc Tesseract hệ thống | Kiểm tra `tessdata/`, cài Tesseract (Linux/mac) |
| Ảnh > 10MB bị từ chối | Vượt `max-file-size` | Tăng `spring.servlet.multipart.max-file-size` |

---

## 5. Pros & Cons của cách tiếp cận này

### Ưu điểm (Pros)
- ✅ **Chạy offline, miễn phí**: không phụ thuộc API trả phí (Google Vision, FPT.AI…), không có chi phí/giới hạn request.
- ✅ **Bảo mật dữ liệu cá nhân**: ảnh CCCD **không rời khỏi server**, không gửi lên bên thứ ba — rất quan trọng với dữ liệu nhạy cảm như CCCD.
- ✅ **Không cần GPU**, cài đặt nhẹ, tận dụng native lib đóng gói sẵn cho Windows.
- ✅ **Tinh chỉnh được không cần build lại**: vùng crop, ngôn ngữ nằm trong `application.properties`.
- ✅ **Tăng đáng kể độ chính xác dãy số** nhờ chiến lược 2 lượt + whitelist.

### Nhược điểm (Cons)
- ❌ **Độ chính xác phụ thuộc mạnh vào chất lượng ảnh** (ánh sáng, độ nghiêng, độ nét, lóa). Không "thông minh" như dịch vụ AI chuyên dụng.
- ❌ **Vùng crop cố định theo tỉ lệ** → giả định ảnh được chụp tương đối đúng khung; thẻ đặt lệch/xoay sẽ crop trượt.
- ❌ **Phụ thuộc layout CCCD hiện hành** — nếu mẫu thẻ đổi bố cục, phải chỉnh lại crop/regex.
- ❌ **Không xác thực tính hợp lệ** của CCCD (không kiểm tra checksum, không chống ảnh giả) — chỉ là tiện ích tự điền.
- ❌ **Tốn RAM/CPU khi xử lý ảnh lớn** (upscale 1600px + Otsu toàn ảnh) và **chậm hơn** do chạy 2 lượt.

---

## 6. Điều kiện để chạy được

### Điều kiện hệ thống
- Thư mục `tessdata/` có `vie.traineddata` (+ `eng.traineddata`) và app chạy **từ gốc project**.
- **Windows x64**: dùng native lib đóng gói trong `tess4j` → không cần cài gì thêm.
- **Linux/macOS/production**: cần cài **Tesseract hệ thống** và đảm bảo native lib khớp kiến trúc.
- `multipart` đang bật, giới hạn file đủ lớn cho ảnh điện thoại.

### Điều kiện về ảnh (người dùng chụp) — rất quan trọng
Để đọc tốt, ảnh CCCD cần:

- 📐 **Chụp đúng khung**: thẻ nằm gần kín khung hình, **không nghiêng/xoay**, các cạnh song song với mép ảnh. Đây là điều kiện tiên quyết vì **vùng crop lượt số dựa theo tỉ lệ** — thẻ lệch thì crop sẽ trượt khỏi vùng số.
- 💡 **Đủ sáng, không lóa**: tránh bóng đổ, tránh ánh sáng phản chiếu lên mặt thẻ (CCCD có lớp phủ dễ lóa).
- 🔍 **Nét, phân giải cao**: chữ số rõ ràng; ảnh mờ là nguyên nhân số 1 khiến OCR sai.
- 🖼️ **Nền tương phản**, chụp thẳng (không chụp chéo góc gây méo phối cảnh).
- 🧾 Định dạng **JPG/PNG**, dung lượng ≤ giới hạn `max-file-size`.

---

## 7. Các hướng khác & vì sao không chọn (hiện tại)

Khi lần quét 1 không ra số, có nhiều hướng để cải thiện. Dưới đây là so sánh và lý do chọn hướng hiện tại (**crop + quét lại vùng số**):

| Hướng | Mô tả | Vì sao **chưa** chọn |
|-------|-------|----------------------|
| **① Crop vùng số + quét lần 2** *(ĐÃ CHỌN)* | Cắt riêng cột chứa số, nhị phân hóa, whitelist chữ số | Đơn giản, miễn phí, offline, cải thiện ngay vấn đề cốt lõi (nhiễu hoa văn) |
| ② Gọi API OCR chuyên dụng (Google Vision, FPT.AI, VNPT eKYC) | Gửi ảnh lên dịch vụ AI | Tốn phí, cần internet, **gửi dữ liệu CCCD ra ngoài** (rủi ro bảo mật/pháp lý) |
| ③ Phát hiện biên thẻ tự động (OpenCV) + nắn phối cảnh | Tự tìm 4 góc thẻ, warp về chuẩn rồi mới crop | Phức tạp, thêm phụ thuộc nặng; chưa cần cho phạm vi đồ án |
| ④ Model deep learning tự train (nhận diện vùng + đọc số) | Train riêng cho CCCD VN | Cần dữ liệu + hạ tầng train, quá sức cho nhu cầu hiện tại |
| ⑤ Đọc chip NFC trên CCCD gắn chip | Lấy dữ liệu số từ chip | Cần đầu đọc NFC/điện thoại hỗ trợ + khóa bảo mật của Bộ Công an |

### Tại sao chọn hướng ① (quét lại vùng có số)
> **Vì lần quét đầu đọc "tất tần tật" cả hoa văn, họa tiết, chữ chìm trên thẻ → nhiễu nặng → không đọc tốt phần số.**
>
> Bằng cách **crop riêng vùng chứa số rồi quét thêm một lần nữa** (kèm nhị phân hóa + whitelist chỉ-số), ta loại bỏ nguồn nhiễu lớn nhất (ảnh chân dung, quốc huy, hoa văn) và ép Tesseract chỉ tập trung vào đúng dãy số. Đây là giải pháp **chi phí thấp, không cần dịch vụ ngoài, cải thiện ngay** đúng điểm yếu đã quan sát được — phù hợp nhất với phạm vi dự án.

---

## 8. Trade-off tổng hợp

| Quyết định | Được | Mất / Đánh đổi |
|-----------|------|----------------|
| **Quét 2 lượt** (text + digits) | Số chính xác hơn nhiều | Chậm gấp ~2 lần, tốn CPU hơn |
| **Crop theo tỉ lệ cố định** | Đơn giản, chỉnh được qua config | Phụ thuộc ảnh chụp đúng khung; thẻ lệch là trượt |
| **Whitelist chỉ chữ số ở lượt 2** | Số không bị đọc nhầm thành chữ | Nếu crop trượt khỏi vùng số thì ra rỗng |
| **Nhị phân hóa Otsu** | Tách chữ khỏi nền hoa văn tốt | Ảnh lóa/bóng đổ có thể bị mất nét chữ |
| **Tesseract offline** (thay vì API AI) | Miễn phí, bảo mật, không cần mạng | Độ chính xác thấp hơn dịch vụ AI chuyên dụng |
| **Commit `tessdata/` vào Git** | Ai clone về là chạy được ngay | Repo phình ~27MB; cân nhắc `.gitignore` + tải riêng |
| **Luôn cho sửa tay, không ép** | Không bao giờ chặn người dùng | OCR chỉ là hỗ trợ, không đảm bảo đúng 100% |

---

## 9. Tóm tắt một dòng

> Tính năng dùng **Tesseract chạy cục bộ**, quét CCCD **2 lượt** — lượt 1 đọc chữ (tên/giới tính/ngày sinh), lượt 2 **crop riêng vùng số + nhị phân hóa + chỉ cho đọc chữ số** để khắc phục việc lần quét đầu bị nhiễu bởi hoa văn trên thẻ. Kết quả chỉ để **tự điền gợi ý**, người dùng luôn kiểm tra/sửa tay. Điều kiện tiên quyết: **ảnh chụp đúng khung, nét, đủ sáng**.
