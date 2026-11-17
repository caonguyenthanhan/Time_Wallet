# Phụ lục (addendum) cho hồ sơ v1.0.7 — V107-F3: sai lệch manifest hậu đóng gói

Phụ lục này **bổ sung, không thay thế** hồ sơ lịch sử. Không có tệp nào của v1.0.7 bị sửa để tạo kết quả khớp: APK, `MANIFEST_SHA256_after.txt`, `source_v1.0.7.tar`, XML và log trong `out/wally_candidate_v107/` và `out/wally_evidence_v107/` (kể cả `superseded_round1/`) giữ nguyên; thời điểm sửa gần nhất của hai thư mục là 20:07 (gói vòng 2), không đổi sau đó.

## 1. Sự việc

| Mục | Giá trị |
|---|---|
| Reviewer ghi nhận | manifest v1.0.7 khớp **102/103**; entry lệch: `app/src/test/java/com/timewallet/mvp/V107RemediationTest.kt` (REVIEW_WALLY_V107.md §2.2, V107-F3) |
| Master tự kiểm lại (20:34) | đúng như vậy. `diff -rq` giữa cây làm việc và `source_v1.0.7.tar` chỉ khác **đúng 1 tệp** này; `app/build.gradle.kts` và mọi tệp `app/src/main/**` giống hệt |
| Bản **đã đóng gói** (trong manifest, tar, XML) | SHA-256 `6d855ba381d1b63defc3de392cde6d9f2bb480f6ce9e46e176a8d3702c2cef95`, 25.396 byte, sửa lúc 20:04:33, kết thúc có dòng mới |
| Bản **trên đĩa lúc kiểm** | SHA-256 `558c97acf544eeecf68f0ee05563bea1907933d074f16c9e629992d318b1a450`, 25.226 byte, sửa lúc **20:10:43** (sau khi đóng gói lúc 20:06), **không** có dòng mới ở cuối |
| Khác biệt (`diff -u`) | 3 điểm: (1) chú thích dòng 167; (2) tên hàm kiểm thử dòng 186; (3) dòng mới cuối tệp |
| Ảnh hưởng tới APK / mã sản phẩm | **Không.** Tệp là test; APK `3df0fee7…c61` được dựng từ bản đã đóng gói và không đổi (SHA-256 vẫn khớp) |

**Nội dung khác biệt:** bản đã đóng gói có tên "14 adversarial replies (6 named in REVIEW_WALLY_V106 plus 8 reconstructed)" và chú thích nói rõ 8 mẫu là tái dựng; bản trên đĩa có lại tên cũ "all 14 reviewer adversarial replies are blocked…" và chú thích "6 mẫu bị lọt trong REVIEW_WALLY_V106.md". Tên cũ **sai về nguồn gốc** (14 mẫu đó không phải nguyên bản của Reviewer).

**Nguyên nhân:** không xác định. Dữ kiện: tệp bị ghi lại lúc 20:10:43 bởi một quy trình khác pipeline đóng gói; nội dung trùng với bản trước khi đổi tên và mất dòng mới cuối tệp (dấu hiệu ghi lại từ một bản sao cũ trong bộ nhớ của trình soạn thảo hoặc phiên khác). Master **không** khẳng định tác nhân. Báo cáo cuối của Master cho v1.0.7 ("đã đổi tên cho đúng") mô tả đúng bản đã đóng gói nhưng **không** còn đúng với cây trên đĩa từ 20:10.

## 2. Cách xử lý (không ghi đè hồ sơ lịch sử)

- Cây "như tìm thấy" được chụp trước khi sửa: `out/wally_v108_snapshot_pre/source_tree_as_found_pre_V108.tar` (SHA-256 `79d0ee208e7b878c0138752f4ac25d0778059b1394833caa1bfdd3fd8d3291ab`) và `MANIFEST_SHA256_as_found.txt` (103 dòng). Tệp này khác manifest v1.0.7 ở đúng 1 dòng, như bảng trên.
- Candidate v1.0.7 vẫn được định nghĩa bởi **tar + manifest đã đóng gói** (phiên bản "6d855ba3…"), không phải bản trên đĩa lúc kiểm.
- Bản **v1.0.8** có manifest mới 105/105 khớp (xem `WALLY_V108_DOSSIER.md`); trong đó `V107RemediationTest.kt` mang lại tên và chú thích **đúng nguồn gốc** ("6 named in REVIEW_WALLY_V106 plus 8 reconstructed by Master, NOT the Reviewer's original 14").
- Bộ 14 mẫu **nguyên bản** do Reviewer khôi phục (REVIEW_WALLY_V107.md §3.2) có test và evidence **riêng**: `V108TransactionStatusTest` (mẫu `reviewerOriginal14`, kiểm trên ReplyGuard và pipeline sản xuất RECORD/MIXED/CHAT). Tệp probe `ADV_REPLYGUARD` gốc của v1.0.6 vẫn không có trong repo; bộ 14 câu hiện dùng là bộ do Reviewer **khôi phục nguyên văn** ở báo cáo v1.0.7.

## 3. Xác nhận phạm vi diff (v1.0.7 đóng gói → v1.0.8)

`out/wally_candidate_v108/CHANGED_FILES_vs_v1.0.7.txt` (12 tệp) + `NEW_FILES_vs_v1.0.7.txt` (2 tệp), 0 tệp xóa. Mã sản phẩm đổi: `GeminiProposalClient.kt`, `LedgerQueryEngine.kt` (thêm một trường), `WallyAiGuards.kt`, `TimeWalletViewModel.kt`, `WallyChatReducer.kt`, `ChatScreen.kt` + tệp mới `WallyTurnPolicy.kt`. Không đổi dependency, manifest ứng dụng, tài nguyên, Room, origin binding, Keystore, timer. Patch: `SOURCE_DIFF_vs_v1.0.7_packaged.patch`; so với cây như tìm thấy: `SOURCE_DIFF_vs_tree_as_found.patch`.
