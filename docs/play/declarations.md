# Play Console — khai báo và câu trả lời

Câu trả lời bằng tiếng Anh (Google duyệt bằng tiếng Anh). Chú thích tiếng Việt ở đầu mỗi mục.

---

## 1. All files access — `MANAGE_EXTERNAL_STORAGE`
*Vị trí: Policy and programs › App content › Permissions declaration (All files access). Rủi ro cao nhất — viết rõ, kèm video.*

**Core feature that requires the permission:** Device storage cleaner / file management.

**Description (paste):**
> SysClean is a storage cleaner and file management tool. Its core feature scans the whole shared storage to find junk files the user can remove: app caches written to shared folders, leftovers of uninstalled apps (folders named after packages that are no longer installed), empty folders, old downloads, APK installers, large files, exact duplicate files and similar or blurry photos. The user reviews every file before it is removed, and removed files are first moved to an in-app recycle bin so they can be restored.
>
> This cannot be done with MediaStore or the Storage Access Framework: junk is mostly in non-media files and in folders created by other apps (for example leftover app folders and `.cache`/`temp` folders), which MediaStore does not index and which SAF can only reach one folder at a time after the user picks it. Finding leftovers of uninstalled apps requires reading every top-level folder of shared storage.
>
> The permission is requested only after an in-app explanation, from the Settings screen. All processing happens on the device; the app has no INTERNET permission and transmits nothing.

**Video:** `https://penz7.github.io/SysClean/play/videos/all-files-access.mp4` — 32 giây, phụ đề tiếng Anh: hộp thoại giải thích → bật quyền trong cài đặt Android → quét → kết quả (không lộ tên tệp). Kịch bản ở mục 7.

---

## 2. `QUERY_ALL_PACKAGES`
*Vị trí: Permissions declaration (Query all packages).*

**Core purpose:** Device management / app management.

**Description (paste):**
> SysClean manages the apps installed on the device as part of its core cleaning and performance features: it lists every installed app with its size, cache size and last use so the user can uninstall apps they no longer use; it matches folders in shared storage against installed packages to detect leftovers of uninstalled apps; it identifies pre-installed apps the user never opens; and it must know the default launcher, keyboard, accessibility and device-admin apps to protect them from being stopped or disabled. These features need visibility of all installed packages, not of a fixed list of apps, so `<queries>` declarations cannot replace the permission. The package list is used only on the device and is never transmitted.

---

**Video (bắt buộc với QUERY_ALL_PACKAGES):** `https://penz7.github.io/SysClean/play/videos/query-all-packages.mp4` — 46 giây, phụ đề tiếng Anh, chỉ quay tab *System* và màn *Pre-installed apps* (không lộ app người dùng tự cài).

## 3. Data safety
*Vị trí: App content › Data safety.*

| Câu hỏi | Trả lời |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | (không hỏi khi trả lời No) |
| Do you provide a way for users to request that their data is deleted? | (không hỏi khi trả lời No) |

Lý do: Google định nghĩa "collect" là *truyền dữ liệu ra khỏi thiết bị*. SysClean không có quyền INTERNET, không SDK bên thứ ba → không thu thập, không chia sẻ. Xử lý trên máy không phải khai báo.

---

## 4. Các mục App content khác

| Mục | Trả lời |
|---|---|
| Privacy policy | `https://penz7.github.io/SysClean/privacy-policy.html` |
| Ads | **No, my app does not contain ads** |
| App access | **All functionality is available without special access** (không có đăng nhập). Ghi chú thêm cho reviewer ở mục 5. |
| Target audience | **18 and over** (công cụ hệ thống; tránh chính sách Gia đình). Không hấp dẫn trẻ em: **No**. |
| Content rating | Xem mục 6 |
| News app | No |
| COVID-19 / Health | No |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Foreground service permissions | Nếu Console hỏi: app không tự khởi chạy foreground service; `SystemForegroundService` đến từ thư viện WorkManager (dùng cho widget) và không khai báo loại nào. |

---

## 5. Ghi chú cho reviewer (App access › instructions, hoặc phần Notes khi gửi duyệt)
> No login is needed. Basic cleaning, the health score, the widget and the speed diagnosis work right after granting "All files access" and "Usage access" in Settings (each is explained in the app first).
>
> Advanced features (cleaning Android/data, RAM manager, battery drain, one-tap optimisation) are optional and need the free Shizuku app from Google Play or a rooted device. Without them the app shows a step-by-step guide (Settings › Set up step by step). Every advanced action is started by the user and can be undone.

---

## 6. Content rating (IARC)
*Category: **All Other App Types** (hoặc "Utility, Productivity, Communication, or Other").*

Trả lời **No** cho mọi câu: bạo lực, tình dục, ngôn ngữ thô tục, chất cấm, cờ bạc, nội dung người dùng tạo/chia sẻ, chia sẻ vị trí, mua hàng kỹ thuật số, trình duyệt web không hạn chế. → Kết quả dự kiến: **Everyone / 3+ (PEGI 3)**.

---

## 7. Kịch bản video demo cho All files access (≈ 45–60 giây, quay màn hình điện thoại)
1. Mở SysClean lần đầu → Tổng quan báo thiếu quyền → bấm **Cài đặt**.
2. Bấm **Cho phép** ở "Truy cập tất cả tệp" → **hộp thoại giải thích** hiện ra (dừng 3 giây cho đọc) → **Tiếp tục**.
3. Màn hình hệ thống "All files access" → bật cho SysClean → quay lại.
4. Tổng quan → **Quét ngay** → chờ quét xong → mở một nhóm (ví dụ *Thư mục sót lại* hoặc *File tải về cũ*) để thấy danh sách tệp ngoài thư mục ảnh/nhạc.
5. Chọn vài tệp → **Dọn** → mở **Thùng rác** → **Khôi phục** một tệp (cho thấy người dùng kiểm soát).
6. Kết thúc ở Cài đặt › Giới thiệu › **Chính sách quyền riêng tư**.

Mẹo: bật chế độ không làm phiền, ẩn thông báo cá nhân; dùng máy test không có ảnh/tài liệu riêng. Quay bằng tính năng quay màn hình của Android, tải lên YouTube ở chế độ **Không công khai**.
