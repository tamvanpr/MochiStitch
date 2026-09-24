# MochiStitch — Agent Context

Proyek: MochiStitch (`com.mochistitch.app`) v6 — rombak total mesin potong.
Platform: Android native, Kotlin + Jetpack Compose
Purpose: Penggabung halaman komik vertikal (strip webtoon).

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
bukan sambungan), sampai batas keras 1,5x batas lunak. Bila batas paksa
jatuh di sambungan, pemutusan mundur ke batas aman terakhir dalam
berkas; hanya sambungan penuh yang terpaksa diputus dan ditandai sebagai
flag tinjau.

- `app/` — 4 tab bawah (Masuk, Hasil, Antrean, Setelan); edge-to-edge;
  satu Scaffold (TopAppBar + NavigationBar); tombol kembali ke tab Masuk;
  badge jumlah di tab Hasil/Antrean; izin tulis API <= 28 dipinta sebelum
  terbit; share via FileProvider (API < 29)
- `core-imaging/` — SeamScan v6 (rowIsSafe+paper/rowsVertSafe/combineSafe/
  findBands/planCuts-tengah/seamContinues, murni array, unit-testable),
  PageGrouper (pinning + hardCap + seamCut), StripRenderer (Placement
  src-rect; decodeSampled; edgePatch region; renderStrip region-decode),
  StripBuilder (pindai vertikal + continuityMap seam + partisi eksak +
  crop banner dua lapis: template-match OpenCV (port setia bannercut2:
  resize+equalize+Canny+dark-mask, 5 sinyal terbobot, ambang web) per
  halaman + NCC murni sebagai fallback bila native gagal + gerbang
  konsistensi BannerGate sebagai pendamping; selalu ada catatan
  keputusan), BannerGate, BannerTemplate, BannerOcv (opencv:4.5.3),
  FileNamer. Preview hasil diskalakan ke <=2048px.
- `core-settings/` — DataStore; tanpa smartCut/strictness/paper/fit
- `core-ui/` — tema M3, PageStrip (thumb Fit), SettingsPanel,
  SlicePreview (kemasan dipilih sebelum tombol Terbitkan)
- `core-archive/` — baca ZIP/CBZ (java.util.zip), RAR/CBR (junrar),
  7Z/CB7 (commons-compress + xz); tulis ZIP/CBZ; `unpackTo` streaming
  ke disk, satu folder unik per impor
- `core-download/` — unduhan mentah MODE GANDA via kontrak Trial Fetch
  (tanpa dependensi baru; HttpURLConnection + parser JSON mini + AES JCE):
  RawSources (15 sumber ID+EN + classifier + BannerPolicy + imageHeaders
  per-sumber), RawContract (3 bentuk JSON + buang banner utuh via metadata),
  WorkerDownloadApi (dipakai bila workerUrl diisi), DirectResolvers
  (resolve langsung tanpa worker: baozimh 6-host + kredensial app,
  wmanhua num/pasd, jjabtoon/jjaptoon/goodtoon/manwa/koudaimh-params-AES,
  mangadex at-home, mangapill, comick mirror, mangageko, demonic,
  likemanga, mangabats API, xcomic Qwik), RawCrypto (AES manwa/koudaimh),
  PageDownloader (streaming, konkurensi 3, retry 2x, transform dekripsi).
  Bytes langsung dari CDN; hasil otomatis shelve() ke antrean.
  Izin INTERNET; workerUrl opsional (kosong = mode langsung).
- `core-common/` — ComicProject (+sourceId unduhan), BannerPolicy.
- `core-common/` — ComicProject (antrean batch; halaman disinkronkan saat rakit)

## Aturan Penting
- Build hanya diverifikasi via GitHub Actions CI/CD (`./gradlew test assembleDebug`)
- API baru WAJIB diverifikasi via Context7 sebelum dipakai (jangan dari ingatan)
- Format gambar default: JPG; pembungkus default: ZIP
- Tidak ada UI horizontal di mana pun
- Input arsip: ZIP/CBZ/RAR/CBR/7Z/CB7 via core-archive
- Output arsip: Terbitkan (tab Hasil) SELALU ikut Setelan
  (`{series}_ch{chapter}.zip/cbz`, tanpa timestamp); batch per komik
  (arsip asal = basename sama, unduhan/manual = judulnya, tanpa timestamp)
- Bitmap config: RGB_565 — hemat memory
- Tiap build menghasilkan 5 APK: universal, armeabi-v7a, arm64-v8a, x86, x86_64
- Commit message dalam bahasa Indonesia
