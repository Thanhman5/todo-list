# Checklist Công Việc Lặp Lại

Ứng dụng quản lý công việc lặp lại (hằng ngày / ngày làm việc / hằng tuần / hằng tháng):
trạng thái Chưa làm – Đang làm – Hoàn thành, tiến độ % theo từng lần làm,
ghi chú riêng cho từng lần, lịch sử thay đổi và thống kê.

## Tải bản cài đặt

Vào mục **[Releases](../../releases)** của repo này để tải:

- **Windows (desktop):** file `.exe`
- **Android:** file `.apk` — cài trực tiếp, máy sẽ hỏi cấp quyền
  "Cài đặt từ nguồn không xác định" cho trình duyệt/ứng dụng quản lý file.

Mỗi bản phát hành trên Releases tương ứng một phiên bản của ứng dụng.

## Chạy bản web

- Mở trực tiếp file `web/index.html` trong thư mục `web/` bằng trình duyệt, hoặc
- Dùng bản web đang chạy tại:
  https://muse.ai/s/checklist-c-ng-vi-c-l-p-l-i-b-n-public-xhxp6k2xpb1xax07/PGWiz0IUBhK5ISm3OfRz9wrP0ONzGZ12ZKgDlq39nTQ

## Dữ liệu

Ứng dụng lưu dữ liệu ngay trên máy của bạn (localStorage của trình duyệt /
bộ nhớ trong của app), không gửi đi máy chủ, không đồng bộ giữa các thiết bị.
Lần mở đầu tiên, ứng dụng yêu cầu tự đặt một mã khoá — mã này chỉ để chống
người khác mở máy xem trộm, không phải tài khoản trực tuyến.

Có xuất/nhập dữ liệu JSON trong mục "Thùng Rác & Dữ Liệu" của ứng dụng
để sao lưu hoặc chuyển dữ liệu sang máy khác.

## Cấu trúc repo

- `web/` — toàn bộ ứng dụng web trong một file `index.html` tự chứa.
- `android/` — project bọc ứng dụng web thành app Android (WebView).
- `desktop/` — project bọc ứng dụng web thành app desktop Windows.
