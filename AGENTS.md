# MochiStitch — Agent Context

Proyek: MochiStitch (`com.mochistitch.app`) v4 — rombak total.
Platform: Android native, Kotlin + Jetpack Compose
Purpose: Penggabung halaman komik vertikal (strip webtoon).

## Arsitektur v4 (tidak ada piksel sumber yang dibuang)

Satu invarian struktural: tidak ada kode yang memotong/meng-crop di dalam
halaman dalam keadaan apa pun. Pemotongan output HANYA tepat di batas
halaman (PageGrouper). Halaman tunggal melebihi batas dibiarkan utuh +
flag tinjau manual. Tidak ada OpenCV, deteksi, tebakan, atau mode crop.

- `app/` — alur 2 langkah (INPUT -> RESULT) + QUEUE + SETTINGS (ikon gir,
  kembali ke layar asal); edge-to-edge; satu Scaffold bersarang;
  tombol kembali sistem mengikuti alur layar;
  izin tulis API <= 28 dipinta sebelum terbit; share via FileProvider (API < 29)
- `core-imaging/` — StripRenderer (gambar utuh, drawBitmap penuh), PageGrouper,
  StripBuilder, FileNamer. Tanpa PageFit/CROP. Preview hasil diskalakan ke
  <=2048px agar tidak memegang strip raksasa di RAM.
- `core-settings/` — DataStore; tanpa smartCut/strictness/paper/fit (mati v4)
- `core-ui/` — tema M3, PageStrip (thumb Fit), SettingsPanel inline (tanpa
  Scaffold sendiri), SlicePreview (kemasan dipilih sebelum tombol Terbitkan)
- `core-archive/` — baca ZIP/CBZ (java.util.zip), RAR/CBR (junrar),
  7Z/CB7 (commons-compress + xz); tulis ZIP/CBZ; `unpackTo` streaming
  ke disk, satu folder unik per impor
- `core-common/` — ComicProject (antrean batch; halaman disinkronkan saat rakit)

## Aturan Penting
- Build hanya diverifikasi via GitHub Actions CI/CD (`./gradlew test assembleDebug`)
- Format gambar default: JPG; pembungkus default: ZIP
- Tidak ada kode/UI horizontal di mana pun (tidak ada enum arah)
- Input arsip: ZIP/CBZ/RAR/CBR/7Z/CB7 via core-archive
- Output arsip dari sumber arsip = basename SAMA (tanpa timestamp)
- Bitmap config: RGB_565 — hemat memory
- Commit message dalam bahasa Indonesia
