# MochiStitch — Agent Context

Proyek: MochiStitch (`com.mochistitch.app`) v5 — rombak total.
Platform: Android native, Kotlin + Jetpack Compose
Purpose: Penggabung halaman komik vertikal (strip webtoon).

## Arsitektur v5 (potong hanya di tempat aman)

Satu prinsip mesin: garis potong tidak pernah melintasi tinta (balon,
panel, teks). Halaman raksasa dipotong HANYA di pusat pita baris
bebas-tepi (SeamScan, Kotlin murni — tanpa OpenCV/ML). Batas antar-berkas
output diusahakan tidak jatuh di pasangan halaman yang bersambung piksel
(pinning sampai batas keras); batas yang terpaksa jatuh di sambungan, dan
halaman yang tak punya celah aman, ditandai sebagai flag tinjau.

- `app/` — 4 tab bawah (Masuk, Hasil, Antrean, Setelan); edge-to-edge;
  satu Scaffold (TopAppBar + NavigationBar); tombol kembali ke tab Masuk;
  badge jumlah di tab Hasil/Antrean; izin tulis API <= 28 dipinta sebelum
  terbit; share via FileProvider (API < 29)
- `core-imaging/` — SeamScan (rowIsSafe/findBands/planCuts/rowsContinue,
  murni array, unit-testable), PageGrouper (pinning + hardCap + seamCut),
  StripRenderer (Placement src-rect; decodeSampled; edgeStrip region),
  StripBuilder (segmentasi aman + continuityMap), FileNamer.
  Preview hasil diskalakan ke <=2048px.
- `core-settings/` — DataStore; tanpa smartCut/strictness/paper/fit
- `core-ui/` — tema M3, PageStrip (thumb Fit), SettingsPanel,
  SlicePreview (kemasan dipilih sebelum tombol Terbitkan)
- `core-archive/` — baca ZIP/CBZ (java.util.zip), RAR/CBR (junrar),
  7Z/CB7 (commons-compress + xz); tulis ZIP/CBZ; `unpackTo` streaming
  ke disk, satu folder unik per impor
- `core-common/` — ComicProject (antrean batch; halaman disinkronkan saat rakit)

## Aturan Penting
- Build hanya diverifikasi via GitHub Actions CI/CD (`./gradlew test assembleDebug`)
- API baru WAJIB diverifikasi via Context7 sebelum dipakai (jangan dari ingatan)
- Format gambar default: JPG; pembungkus default: ZIP
- Tidak ada UI horizontal di mana pun
- Input arsip: ZIP/CBZ/RAR/CBR/7Z/CB7 via core-archive
- Output arsip dari sumber arsip = basename SAMA (tanpa timestamp)
- Bitmap config: RGB_565 — hemat memory
- Tiap build menghasilkan 5 APK: universal, armeabi-v7a, arm64-v8a, x86, x86_64
- Commit message dalam bahasa Indonesia
