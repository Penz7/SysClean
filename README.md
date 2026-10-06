# SysClean

Ứng dụng phân tích và dọn dẹp hệ thống cho Android. **Phiên bản 0.7.0: phân tích + dọn dẹp + dọn sâu (Shizuku/Root) + widget + tối ưu hiệu năng & RAM, chạy như nhau trên mọi hãng.**

- minSdk 26 (Android 8.0) · targetSdk 36 · Kotlin 2.2 · Jetpack Compose + Material 3 · Hilt
- Ngôn ngữ: Tiếng Anh, Tiếng Việt (mặc định theo hệ thống, đổi được trong Cài đặt)
- Phát hành ngoài Google Play (APK)

## Tính năng

| Màn hình | Nội dung |
|---|---|
| Tổng quan | Điểm sức khỏe, RAM, pin, phân loại bộ nhớ, phân tích file rác |
| Thiết bị | Tổng quan · CPU (xung nhịp trực tiếp) · RAM/zRAM · Pin · Màn hình · Lưu trữ · Cảm biến · Camera · Bảo mật |
| Ứng dụng | Dung lượng, cache, lần dùng cuối; lọc ứng dụng không dùng 90+ ngày |
| Cài đặt | Quyền, trạng thái Shizuku/Root, ngôn ngữ |

Các loại rác được phát hiện: cache ứng dụng, file tạm/log, thumbnail, APK (kiểm tra đã cài chưa), thư mục rỗng,
thư mục sót lại của app đã gỡ, file trùng lặp, ảnh giống nhau, ảnh mờ, file lớn (>100 MB), ảnh chụp màn hình,
file tải về cũ (>90 ngày).

### Dọn dẹp (Giai đoạn 2)

| Cơ chế | Chi tiết |
|---|---|
| Thùng rác | File được **di chuyển** (rename, tức thì) vào `Android/media/vn.sysclean/trash` (có `.nomedia`). Khôi phục về đúng chỗ; không ghi đè file mới trùng tên. Tự xóa sau 7/14/30 ngày (mặc định 14). Gỡ app thì hệ thống tự xóa thư mục này |
| Chọn sẵn | Rác an toàn: chọn hết. File trùng: chọn mọi bản trừ bản gốc. **Ảnh giống nhau / ảnh mờ / file lớn / ảnh chụp màn hình: không chọn gì** |
| File trùng | Trước khi xóa, so **SHA-256 toàn bộ nội dung** với bản giữ lại; không khớp thì bỏ qua. Luôn giữ ít nhất 1 bản |
| Thư mục rỗng | Kiểm tra lại vẫn rỗng rồi xóa thẳng (không vào thùng rác) |
| Cache ứng dụng | Android 11+: hộp thoại hệ thống xóa cache mọi app (`ACTION_CLEAR_APP_CACHE`). Android 8–10: mở trang thông tin từng app |
| Hoàn tác | Snackbar "Hoàn tác" sau mỗi lần dọn |
| Bỏ qua (whitelist) | Menu ⋮ → "Không hiện mục này nữa"; quản lý ở Cài đặt → Mục bỏ qua |

### Ảnh giống nhau / ảnh mờ

- dHash 64-bit + độ nét (phương sai Laplacian) tính trên ảnh 512 px, cache trong Room theo `media_id + date_modified + size + phiên bản thuật toán`.
- Giống nhau: khoảng cách Hamming ≤ 4 **và** chụp cách nhau ≤ 30 phút, gom nhóm theo ảnh đại diện (không nối dây chuyền).
  Bỏ qua ảnh ít chi tiết (biểu đồ nền trắng, tài liệu) vì dHash của chúng không đáng tin.
- Mờ: độ nét < 60 và độ tương phản ≥ 20.

## Chế độ quyền

| Chế độ | Trạng thái |
|---|---|
| Thường (All files access + Usage access) | ✅ Đang dùng |
| Shizuku | ✅ Dọn sâu qua một "user service" chạy với quyền shell (AIDL) |
| Root | ✅ Bật thủ công trong Cài đặt (chỉ lúc đó mới gọi `su`). Dùng khi Shizuku không chạy; thêm khả năng xóa cache nội bộ từng app. **Chưa kiểm chứng trên máy root thật** |

### Dọn sâu với Shizuku (Giai đoạn 3)

| Tính năng | Cách làm |
|---|---|
| Thư mục sót lại trong `Android/data`, `Android/obb` | Thư mục có tên package nhưng app không còn (đối chiếu cả gói ẩn/đã gỡ cho user). Xóa **vĩnh viễn** (thùng rác nằm ở vùng shell không ghi được) |
| Cache từng app | Xóa `Android/data/<pkg>/cache`; mỗi dòng hiện đúng phần xóa được và tổng cache |
| Xóa toàn bộ cache (cả nội bộ) | `pm trim-caches` – `pm clear --cache-only` bị treo trên One UI 7 nên không dùng |
| Buộc dừng | `am force-stop`, từng app hoặc hàng loạt |
| Gỡ app người dùng | `pm uninstall`, không cần hộp thoại từng app |
| Ứng dụng cài sẵn | Danh sách khuyên gỡ (bảo thủ) → `pm uninstall -k --user current` hoặc `pm disable-user`; khôi phục bằng `cmd package install-existing` / `pm enable`. Không bao giờ đụng gói lõi, launcher/bàn phím đang dùng, SysClean, Shizuku |

Hàng rào an toàn của service (`PrivilegedPaths`, có unit test): chỉ xóa bên trong `Android/data`/`obb`
(không bao giờ chính hai thư mục gốc, chặn `..`/`//`), và chỉ chạy `pm`, `am`, `cmd`, `id`.

**Xiaomi/MIUI:** Shizuku chỉ cấp quyền được khi bật "Gỡ lỗi USB (Cài đặt bảo mật)" (cần SIM + Mi account).

### Chế độ Root

- Chỉ gọi `su` khi người dùng bấm "Bật chế độ root" (Magisk/KernelSU hỏi cấp quyền); có nút "Vẫn thử bật" cho root ẩn.
- Ưu tiên Shizuku nếu đang chạy, root thay thế khi Shizuku dừng (`CompositePrivilegedShell`).
- Mọi lệnh qua `RootCommands`: escape tham số, chỉ `pm`/`am`/`cmd`, chỉ xóa trong Android/data/obb và **chỉ làm rỗng**
  `/data/(data|user|user_de)/<pkg>/(cache|code_cache)` – có unit test cho injection và đường dẫn.
- Test trên máy chưa root: bật root bị từ chối gọn gàng (`RootModeTest`).

### Widget điểm sức khỏe

- Glance, 3 bố cục theo kích thước (2x1, 2x2, 4x2), **cả ba đều có điểm + Bộ nhớ % + RAM %**: vòng điểm (vẽ bitmap
  giống app), thanh Bộ nhớ và RAM, màu theo hình nền.
- **Cùng nguồn với app** (`HealthRepository`) nên điểm luôn khớp; bộ quét lưu "rác an toàn" mỗi khi quét/dọn.
- **Tự cập nhật** khi app đang chạy và có widget (RAM lấy mẫu 15 s khi màn hình bật, đo lại ngay khi bật màn hình,
  dừng khi tắt màn hình; chỉ vẽ lại khi số nguyên hiển thị thay đổi). Nội dung widget *collect* `StateFlow` chứ không
  chụp giá trị: Glance giữ phiên và recompose khi `update`, giá trị chụp sẵn sẽ đứng yên. Tối ưu / ngủ sâu / tắt dịch vụ
  gọi `HealthRepository.invalidate()` để app và widget đo lại ngay. Android tự làm mới mỗi 30 phút khi app không chạy. Thêm nhanh từ Cài đặt → "Thêm vào màn hình chính".

### Hiệu năng & RAM (tab "Hiệu năng")

Đo những gì thật sự gây giật, rồi chỉ can thiệp vào đó. Mọi lệnh đều là AOSP (`cmd`, `am`, `dumpsys`, `sm`) nên chạy
như nhau trên Samsung, Xiaomi, OPPO, vivo, Pixel, Transsion…

| Chẩn đoán | Nguồn | Cần |
|---|---|---|
| Độ giật của launcher (% khung hình giật, p50/p99) | `dumpsys gfxinfo` | Shizuku/Root |
| Thời gian chờ RAM / chờ bộ nhớ (PSI) | `/proc/pressure/*` | Shizuku/Root |
| App dùng trong 14 ngày nhưng chưa biên dịch AOT | `dumpsys package dexopt` (chỉ xét APK chính) | Shizuku/Root |
| Thời gian chạy liên tục, kích thước system_server | uptime, `dumpsys meminfo` | (một phần) |
| RAM trống, bộ nhớ trống, nhiệt độ, tốc độ hiệu ứng | API thường | — |
| Tiết kiệm pin, dịch vụ trợ năng đang chạy, "Không giữ hoạt động" | API thường | — |

**Tối ưu một chạm:** biên dịch `speed-profile` cho app chưa tối ưu, `sm fstrim`, **giải phóng RAM** (`am kill-all`: chỉ
tiến trình *cached* — app đã đóng, không dừng app đang mở; đo trên A05: 64% → 56%, +326 MB), (tùy chọn) ngủ sâu app không
dùng 2 tuần, (tùy chọn) hiệu ứng 0,5x. Kết quả hiện "RAM đang dùng X% → Y%" theo cùng cách tính với dashboard/widget, đo
sau 3 s cho kernel trả trang; nói rõ Android sẽ dần nạp lại (bình thường) và cách giảm lâu dài (ngủ sâu). Lưu số đo trước khi tối ưu và reset `gfxinfo` để so sánh với lúc dùng thật sau đó.

**App hao pin** (Shizuku/Root): đọc `dumpsys batterystats --checkin` (định dạng CSV ổn định của AOSP, chạy như nhau
trên mọi hãng), từ lần sạc đầy gần nhất: mAh ước tính, số lần đánh thức (`wua`), wakelock nền (`awl`), CPU, thời gian
chạy nền (`st`). Gắn cờ *đánh thức ≥ 6 lần/giờ*, *giữ máy thức ≥ 2% thời gian*, *chạy nền ≥ 30% mà hiếm khi mở* — chỉ
cho app người dùng thao tác được và chiếm ≥ 1% pin của các app; thành phần hệ thống chỉ liệt kê. Hành động: Ngủ sâu
(hoàn tác được), Tắt dịch vụ tùy chọn, hoặc Chi tiết. Dịch vụ đặc quyền chỉ cho phép đúng `dumpsys batterystats
--checkin` (không bao giờ `--reset`) và lọc bỏ các dòng không dùng trước khi qua Binder (182 KB → 30 KB sau 3 giờ).

**Quản lý RAM:** bản đồ RAM theo app (Hệ thống / Đang dùng / Chạy nền / Bộ nhớ đệm).
- *Ngủ sâu* app người dùng: `appops RUN_ANY_IN_BACKGROUND ignore` + standby `restricted` + force-stop; *Đánh thức* để hoàn tác.
- *Tắt* dịch vụ thường trực tùy chọn đã biết (App Google, Customization Service, Routines, Bixby voice…), ghi rõ mất gì.
- **Mọi hãng:** app cài sẵn có icon, >30 ngày không mở, không làm việc ngầm (`IDLE_PRELOAD`) được đề xuất ngủ sâu. Tìm theo
  dữ liệu sử dụng + intent (`AppsRepository.unusedPreinstalled`), loại trừ đồng hồ, lịch, mail, camera, danh bạ, gọi/SMS,
  trình duyệt, thư viện, file, bản đồ, trợ lý, cài đặt, kho ứng dụng (installer), app có foreground service trong 30 ngày.
  Màn "Ứng dụng cài sẵn" cũng có mục này (chỉ *Tắt*, không *Gỡ*), và mục khôi phục liệt kê mọi app cài sẵn đang bị tắt.
- Hướng dẫn Shizuku theo hãng (`ShizukuQuirk`): Xiaomi (Gỡ lỗi USB – bảo mật), OPPO/realme/OnePlus (Tắt giám sát quyền),
  Meizu (Bảo vệ thanh toán Flyme); Android 8–10 chỉ hướng dẫn cách dùng máy tính.
- **Thiết lập Shizuku từng bước** (Cài đặt → "Thiết lập từng bước"): 6 bước tự đánh dấu theo trạng thái thật của máy
  (đã cài Shizuku, Tùy chọn nhà phát triển, Wi-Fi, Gỡ lỗi không dây — `adb_wifi_enabled`, Shizuku chạy, đã cho phép),
  mỗi bước một nút mở đúng màn hình (Gỡ lỗi không dây được cuộn tới qua `:settings:fragment_args_key`). Đường dẫn
  "Số hiệu bản tạo" riêng cho Samsung/Xiaomi; Android 8–10 đi đường máy tính. Logic bước là hàm thuần có unit test.
- **Nhắc khi Shizuku dừng**: người đã từng dùng Shizuku mà nay Shizuku không chạy (thường do khởi động lại máy) thấy thẻ
  trên Tổng quan: Mở Shizuku / Hướng dẫn / Không nhắc nữa. Trình thiết lập rút gọn còn "mở Shizuku → Bắt đầu".
- Hướng dẫn ghép nối nhấn mạnh chỗ người dùng hay kẹt (đã kiểm chứng trên Galaxy A25): mã ghép nối hết hiệu lực khi hộp
  thoại đóng, nên phải giữ hộp thoại mở khi nhập mã vào thông báo Shizuku (gợi ý chia đôi màn hình). Sau khi cài lại
  SysClean, cần dừng/bật lại Shizuku (server cũ giữ UID cũ, `attachApplication` lỗi NPE — gặp trên Xiaomi).
- Không bao giờ đụng: hệ thống lõi, launcher, bàn phím, trợ năng, đọc thông báo, quản trị thiết bị, SMS/điện thoại
  mặc định, VPN luôn bật, SysClean, Shizuku (`AppClassifier`, có unit test).
- Test dùng app mẫu `:fixture` (`vn.sysclean.fixture`) để không đụng app thật.

## Cấu trúc module

```
app/                      MainActivity, điều hướng, thanh dưới
build-logic/convention/   Convention plugins (sysclean.android.*, sysclean.hilt, ...)
core/
  model/                  Data class thuần Kotlin
  domain/                 Công thức điểm sức khỏe (dùng chung app + widget)
  common/                 Dispatcher, ApplicationScope, định dạng dung lượng/tần số
  designsystem/           Theme M3, token (màu, spacing, trạng thái), component dùng chung
  ui/                     Nhãn song ngữ cho model (loại rác, trạng thái pin, ...)
  privilege/              Quyền, Shizuku, phát hiện root — khai báo toàn bộ permission
  database/               Room: thùng rác, whitelist, cache chữ ký ảnh (schema xuất ở core/database/schemas)
  data/                   Thông tin hệ thống, thùng rác, whitelist, tùy chọn (DataStore)
  scanner/                StorageWalker + PhotoAnalyzer — chỉ phát hiện
  cleaner/                Dọn theo lựa chọn, xác minh file trùng, hoàn tác
  performance/            Chẩn đoán hiệu năng, tối ưu một chạm, quản lý RAM (parser có test trên dump thật)
feature/
  dashboard/  deviceinfo/  apps/  settings/  cleaner/  trash/  widget/  performance/
fixture/                  App rỗng chỉ dùng để test (ngủ sâu/tắt)
```

Quy tắc: `feature` không phụ thuộc `feature` khác; điều hướng giữa các màn hình đi qua `app`.

## Build & chạy

```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease               # bản R8 (~3 MB), ký nếu có keystore.properties, nếu không thì chưa ký
./gradlew testDebugUnitTest             # unit test (scanner, health score, bộ lọc app, formatter, sysfs)
```

**Ký bản release:** tạo `keystore.properties` ở thư mục gốc (đã nằm trong `.gitignore`, không bao giờ commit):

```properties
storeFile=/đường/dẫn/tới/sysclean-release.jks
storePassword=...
keyAlias=sysclean
keyPassword=...
```

Giữ keystore và mật khẩu cẩn thận: mất keystore thì không thể phát hành bản cập nhật cài đè lên bản cũ.

### Test trên máy thật

- `SmokeTest`: đi qua mọi màn hình, chạy một lần quét thật (chỉ xem, không dọn).
- `PrivilegedFlowTest` (cần Shizuku sẵn sàng, tự bỏ qua nếu không): xóa thư mục sót lại mẫu trong Android/data & obb,
  xóa cache của chính SysClean, buộc dừng app Shizuku, gỡ rồi khôi phục Samsung Kids Installer. Trước khi chạy cần tạo
  fixture bằng adb (xem chú thích trong file test).
- `CleaningFlowTest`: tự tạo file mẫu trong `Download/SysCleanFixture`, rồi dọn → hoàn tác → dọn lại → bỏ qua →
  khôi phục / xóa vĩnh viễn trong thùng rác. **Luôn bỏ chọn hết trước khi chọn file mẫu**, nên không đụng file thật.

Ảnh chụp mỗi bước lưu ở `/sdcard/Android/data/vn.sysclean/files/smoke/`.

```bash
adb shell appops set --uid vn.sysclean MANAGE_EXTERNAL_STORAGE allow
adb shell appops set vn.sysclean GET_USAGE_STATS allow
./gradlew :app:connectedDebugAndroidTest
```

Trên Xiaomi/MIUI: bấm xác nhận "Cài đặt qua USB" khi được hỏi, và cho phép mở activity từ nền:
`adb shell appops set vn.sysclean 10021 allow`.

## Lộ trình

- ~~Giai đoạn 2 — Dọn dẹp chế độ Thường~~ ✅
- ~~Giai đoạn 3 — Shizuku~~ ✅
- ~~Widget điểm sức khỏe, chế độ Root~~ ✅
- **Tiếp theo:** kiểm chứng chế độ Root trên máy root thật, ký số bản phát hành.
