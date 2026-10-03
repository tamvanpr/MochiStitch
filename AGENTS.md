# MochiStitch — Agent Context

Proyek: MochiStitch (`com.mochistitch.app`) v6 — rombak total mesin potong.
Platform: Android native, Kotlin + Jetpack Compose
Purpose: Penggabung halaman komik vertikal (strip webtoon).

## Arsitektur v7 (rencana potong global di atas profil baris)

Prinsip: garis potong hanya boleh lewat baris yang benar-benar polos.
Seluruh halaman dipindai dulu pada lebar kecil (SCAN_WIDTH 360px) dengan
RowScanner (busy = ada selisih luminans antar-piksel melewati ambang, ATAU
rentang min-max baris melewati ambang → menangkap balon, kotak dialog, kotak
system, dan teks luar balon sekaligus). Profil baris semua halaman digabung
menjadi satu profil panjang, lalu CutPlanner membuat titik potong GLOBAL —
batas antar-halaman tidak lagi menjadi potongan paksa. Potongan yang tidak
punya celah polos ditandai `forced` dan ditandai untuk tinjau manual.
Margin vertikal (dilasi) dipetakan dari CutStrictness: Longgar 6, Seimbang 10,
Akurat 14. RowScanner/CutPlanner murni Kotlin + array (unit-testable, tanpa
Bitmap); tidak ada ML Kit. OpenCV hanya tersisa untuk pencocokan template
banner (BannerOcv), bukan untuk deteksi balon.

Tambahan v7b (anti-bocor):
- Pindai memakai nearest-neighbor (tanpa filter) pada lebar 480px agar garis
  tipis/ekor balon tidak terhapus blur downscale.
- Gambar diagnostik per strip (`debug_NNN.png`: strip 240px + garis
  potong hijau/merah, MochiStitch.md §1): tombol "Lihat deteksi" di
  dialog zoom hasil; file yatim ikut dibersihkan cache.
- Setiap titik potong terencana diverifikasi ulang pada ROI resolusi
  penuh ±320 baris (ala SmartSplitEngine ai_studio_code): sibuk +
  interior terkurung (skala 1/2) + zona teks skala-penuh, lalu geser ke
  baris bebas terdekat (<=300px); bila tak ada tandai forced.
- Rencana potong berjenjang per jendela: baris terakhir berzona bersih
  dalam 1/5 batas di bawah target (lantai 80%) > darurat di bawah batas
  (DITANDAI) > tengah celah terlebar > overshoot di awal pita (maks ~10%)
  > tinta paling sedikit (DITANDAI).
- Cek rentang baris butuh minimal 1 tepi; cek median butuh populasi
  dominan >=40% atau simpangan terpusat (gradasi mulus bukan konten).
- API bitmap (inSampleSize power-of-2, decodeRegion, inJustDecodeBounds)
  terverifikasi via Context7 (/websites/developer_android_reference).
- Batas lunak HARGA MATI tanpa penahanan (hardCap warisan tak dipakai).
- Keep-Out Mask (Tier 0 CUT-SAFETY): daerah terang terkurung (dalam balon)
  dihitung via flood fill dari tepi pada salinan 240px, lalu OR ke profil
  busy; hanya komponen terkurung KECIL (<=20% luas gambar) yang dihitung —
  bingkai panel raksasa dikecualikan agar isi panel tetap boleh dipotong;
  potongan paksa yang jatuh di zona larangan DIBATALKAN (halaman dibiarkan
  utuh/kelebihan tinggi + ditandai).
- Zona teks: baris "terstruktur" (>=3 run gelap dengan run terpanjang
  >=4px = kalimat; arsir = 1-2 run; screentone = run 1-3px) dikelompokkan bila celah <= 16 baris lalu
  diperlebar ±12 (dinding + ekor balon). Zona ini hanya memblokir
  perencanaan; yang DIBATALKAN hanya potongan paksa tepat di interior
  terkurung (flood fill).
- Pemetaan potong global ke koordinat sumber memakai offset halaman
  (koordinat lokal vs global pernah tertukar sehingga potongan terencana
  di luar halaman pertama terbuang diam-diam).
- Potong di TENGAH celah, bukan ujungnya (anti menempel balon).
- continuityMap = touchesEdge saja (goresan tegak >=10px ATAU garis datar
  >=1/4 lebar di 16 baris tepi): aliran art mulus SENGAJA tak di-link
  (putus di situ tak terlihat). Tepi halaman tak-bersentuhan = safeBreak
  (putus diam-diam); hanya tepi bersentuhan yang ditandai seamCut.
- Batas antar-berkas output HANYA boleh jatuh di tepi potongan terencana
  (`Seg.cutTop` → `Sheet.safeBreak`): batas lunak HARGA MATI, tanpa
  penahanan melewati batas. Tepi tak-terencana yang terpaksa diputus
  (termasuk sisa pemutusan mundur) ditandai `seamCut` untuk tinjau manual.
  Satu-satunya yang boleh lewat: segmen tunggal melebihi batas
  (`tallSingle`, dibiarkan utuh + ditandai — fisika).

## Arsitektur v6 (potong hanya di tempat aman — perbaikan akar masalah)

Satu prinsip mesin: garis potong tidak pernah melintasi tinta (balon,
panel, teks). Rombak v6 memperbaiki 4 cacat v5 yang menyebabkan
"potong pada area yang tidak seharusnya":

1. Paper-aware: baris gelap merata (border horizontal/balok hitam) kini
   DITOLAK meski variasinya horizontal nol (v5 meloloskan). Median baris
   dibandingkan ke estimasi kertas; jauh dari kertas = tinta.
2. Cek vertikal: tiap baris dibandingkan ke tetangga atas/bawah
   (rowsVertSafe). Border panel 1-2px yang mendatar hanya terlihat di
   sumbu vertikal — v5 buta terhadapnya.
3. Potong tengah + margin: potongan = titik tengah pita aman dengan
   BAND_MARGIN 4px dari tiap sisi tinta (v5 memotong di tepi pita, 1px
   dari tinta, lalu drift rounding mengiris tinta).
4. Seam benar: continuityMap hanya membandingkan baris-baris yang
   bersebelahan di seam (seamContinues), bukan patch-penuh-vs-penuh yang
   berjarak 48px (v5 hampir tidak pernah pinning dengan benar).
5. Render region-decode per placement (bukan full-decode + crop float):
   tanpa drift 1-2px, tanpa OOM halaman raksasa. Partisi sumber dijamin
   eksak (sBot[i] = sTop[i+1]).

Halaman raksasa dipotong HANYA di tengah pita aman (SeamScan, Kotlin
murni — tanpa OpenCV/ML; pindai resolusi penuh ≤20MP agar garis tipis
tak lolos; MIN_BAND 16). Bila tak ada celah dalam jendela, potongan
boleh lewat batas sedikit (overflow) demi celah aman. Batas antar-berkas
output diusahakan tidak jatuh di pasangan halaman yang bersambung piksel
— pinning hanya bila kedua tepi mengandung konten (margin putih-vs-putih
bukan sambungan), sampai batas keras 1,25x batas lunak. Bila batas paksa
jatuh di sambungan, pemutusan mundur ke batas aman terakhir dalam
berkas; hanya sambungan penuh yang terpaksa diputus dan ditandai sebagai
flag tinjau.

- `app/` — 4 tab bawah (Masuk, Hasil, Antrean, Setelan); edge-to-edge;
  satu Scaffold (TopAppBar + NavigationBar); tombol kembali ke tab Masuk;
  badge jumlah di tab Hasil/Antrean; izin tulis API <= 28 dipinta sebelum
  terbit; share via FileProvider (API < 29)
- `core-imaging/` — SeamScan v6 (rowIsSafe+paper/rowsVertSafe/combineSafe/
  findBands/planCuts-tengah/seamContinues, murni array, unit-testable),
  PageGrouper (safeBreak + hardCap + seamCut), StripRenderer (Placement
  src-rect; decodeSampled; edgePatch region; renderStrip region-decode),
  StripBuilder (pindai vertikal + continuityMap seam + partisi eksak +
  crop banner dua lapis: template-match OpenCV (port setia bannercut2:
  resize+equalize+Canny+dark-mask, 5 sinyal terbobot, ambang web) per
  halaman; lapis template berjalan selalu (cocok = banner); gerbang
  konsistensi BannerGate dorman (tak ada lagi sumber ber-banner sejak
  fitur unduh mentah dihapus); selalu ada catatan
  keputusan), BannerGate, BannerTemplate, BannerOcv (opencv:4.5.3),
  FileNamer. Preview hasil diskalakan ke <=1280px.
- `core-settings/` — DataStore; tanpa smartCut/strictness/paper/fit
- `core-ui/` — tema M3, PageStrip (thumb Fit), SettingsPanel,
  SlicePreview (kemasan dipilih sebelum tombol Terbitkan)
- `core-archive/` — baca ZIP/CBZ (java.util.zip), RAR/CBR (junrar),
  7Z/CB7 (commons-compress + xz); tulis ZIP/CBZ; `unpackTo` streaming
  ke disk, satu folder unik per impor
- `core-common/` — ComicProject, BannerPolicy.
- `core-common/` — ComicProject (antrean batch; halaman disinkronkan saat rakit)

## Aturan Penting
- Build hanya diverifikasi via GitHub Actions CI/CD (`./gradlew test assembleDebug`)
- API baru WAJIB diverifikasi via Context7 sebelum dipakai (jangan dari ingatan)
- Format gambar default: JPG; pembungkus default: ZIP
- Tidak ada UI horizontal di mana pun
- Input arsip: ZIP/CBZ/RAR/CBR/7Z/CB7 via core-archive
- Output arsip: Terbitkan (tab Hasil) SELALU ikut Setelan
  (`{series}_ch{chapter}.zip/cbz`, tanpa timestamp); batch per komik
  (arsip asal = basename sama, manual = judulnya, tanpa timestamp)
- Bitmap config: RGB_565 — hemat memory
- Tiap build menghasilkan 5 APK: universal, armeabi-v7a, arm64-v8a, x86, x86_64
- Commit message dalam bahasa Indonesia
