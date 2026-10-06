# Bộ hồ sơ Google Play — SysClean

Mọi thứ cần để đưa SysClean lên Google Play, theo thứ tự làm.

| File | Nội dung |
|---|---|
| `listing-en.md`, `listing-vi.md` | Tên, mô tả ngắn, mô tả đầy đủ, ghi chú phát hành (đã kiểm độ dài) |
| `declarations.md` | Khai báo quyền nhạy cảm, Data safety, phân loại nội dung, đối tượng, ghi chú cho reviewer, kịch bản video |
| `graphics/icon-512.png` | Biểu tượng 512×512 |
| `graphics/feature-graphic-en.png`, `-vi.png` | Ảnh nổi bật 1024×500 |
| `screenshots/` | Ảnh chụp 1080×1920 (đúng 9:16 như Play yêu cầu), tiếng Anh và tiếng Việt, không chứa dữ liệu cá nhân |
| `videos/query-all-packages.mp4` | Video demo cho khai báo QUERY_ALL_PACKAGES (Play bắt buộc) |
| `videos/all-files-access.mp4` | Video demo cho khai báo Truy cập mọi tệp |
| `../privacy-policy.html` | Chính sách quyền riêng tư (song ngữ), đăng qua GitHub Pages |

Bản build: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` (ký bằng `keystore/`, không có trong git).

---

## 1. Trước khi vào Play Console
- [ ] Bật GitHub Pages: repo › Settings › Pages › *Deploy from a branch* › `main` / `/docs`. Kiểm tra mở được `https://penz7.github.io/SysClean/privacy-policy.html` (trong app: Cài đặt › Giới thiệu › Chính sách quyền riêng tư).
- [ ] Quay video demo theo `declarations.md` mục 7, tải lên YouTube chế độ *Không công khai*.
- [ ] Sao lưu `keystore/sysclean-release.jks` và `keystore.properties` ra nơi an toàn (USB, ổ mã hóa). Mất khóa = không cập nhật được app.

## 2. Tài khoản nhà phát triển
- [ ] Đăng ký tại play.google.com/console (25 USD, xác minh danh tính, số điện thoại).
- [ ] Tài khoản **cá nhân** tạo sau 11/2023: phải chạy **thử nghiệm kín với ≥ 12 người thử trong 14 ngày liên tục** trước khi xin phát hành công khai. Chuẩn bị danh sách email Gmail của người thử.

## 3. Tạo app
- [ ] *Create app*: tên `SysClean: Phone Cleaner`, ngôn ngữ mặc định English (US), App, Free.
- [ ] Thêm ngôn ngữ Vietnamese, dán nội dung `listing-vi.md`.

## 4. Ký ứng dụng (Play App Signing)
Để người đang dùng bản APK từ GitHub cập nhật được qua Play mà không phải gỡ app, **dùng chính khóa hiện tại** làm khóa ký của Google:
- [ ] Khi tải AAB đầu tiên, chọn *Use a different key* › *Export and upload a key from Java keystore*.
- [ ] Console cho tải `pepk.jar` và hiển thị đúng lệnh cần chạy; lệnh có dạng:
  ```
  java -jar pepk.jar --keystore=keystore/sysclean-release.jks --alias=sysclean \
    --output=sysclean-signing-key.zip --include-cert --rsa-aes-encryption \
    --encryption-key-path=encryption_public_key.pem
  ```
  Mật khẩu nằm trong `keystore.properties`. Tải `sysclean-signing-key.zip` lên Console. Xóa file zip sau khi xong.
- [ ] Khóa tải lên (upload key): dùng luôn khóa này.

## 5. App content (Policy) — điền theo `declarations.md`
- [ ] Privacy policy · Ads (No) · App access · Content rating · Target audience (18+) · Data safety (No data collected) · Government/Financial/Health (No).
- [ ] Permissions declaration: **All files access** (mục 1 + video) và **QUERY_ALL_PACKAGES** (mục 2).

## 6. Store listing
- [ ] Ảnh: icon, feature graphic, ≥ 2 ảnh điện thoại (có sẵn 5 ảnh mỗi ngôn ngữ, đặt theo thứ tự tên file).
- [ ] Category: Tools · Email liên hệ · Website.

## 7. Phát hành
- [ ] *Testing › Closed testing*: tạo track, thêm người thử, tải `SysClean-0.7.1.aab`, dán ghi chú phát hành.
- [ ] Sau 14 ngày (≥ 12 người thử tham gia): *Apply for production*, trả lời bảng câu hỏi về quá trình thử nghiệm.
- [ ] Gửi duyệt. Nếu **All files access** bị từ chối: đọc lý do, sửa phần mô tả/video và gửi lại; nếu vẫn bị từ chối thì làm bản Play không dùng quyền này (dọn qua MediaStore + bộ chọn thư mục).

## Lưu ý mỗi lần cập nhật
- Tăng `versionCode` trong `app/build.gradle.kts` (Play không nhận lại mã cũ).
- AAB cho Play, APK cho GitHub Releases: cùng khóa nên người dùng chuyển qua lại không phải gỡ app.
