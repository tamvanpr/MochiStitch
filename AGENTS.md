# MochiStitch — Agent Context

Proyek: MochiStitch (`com.mochistitch.app`) v2 — rombak total dari nol.
Platform: Android native, Kotlin + Jetpack Compose
Purpose: Penggabung halaman komik vertikal (strip webtoon).

## Arsitektur v2 (page-aware, tanpa deteksi konten)

Pemotongan output dilakukan TEPAT di batas halaman asli yang memang
diketahui penggabung — tidak pernah di tengah konten. Tidak ada OpenCV,
tidak ada ambang kontur, tidak ada tebakan.

- `app/` — UI Compose + MainViewModel (impor arsip, bulk, pratinjau, ekspor)
- `core-imaging/` — MergeEngine vertikal, PageAwareSplitter, StitchProcessor,
  FilenameFormatter, ImageCompressor
- `core-mochismart/` — GutterScanner murni-piksel; HANYA untuk halaman tunggal
  yang melebihi batas (overflow): belah di baris kertas kosong
- `core-settings/` — DataStore; default wrapper = ZIP
- `core-ui/` — tema M3 hijau, daftar gambar, pengaturan, pratinjau
- `core-archive/` — tulis/baca ZIP/CBZ/RAR/CBR/7Z (junrar)
- `core-common/` — util + model StitchProject

## Aturan Penting
- Build hanya diverifikasi via GitHub Actions CI/CD (`./gradlew test assembleDebug`)
- Format gambar default: JPG; pembungkus default: ZIP
- Tidak ada kode/UI horizontal di mana pun (tidak ada enum arah)
- Input arsip: ZIP/CBZ/RAR/CBR/7Z via core-archive
- Output arsip dari sumber arsip = basename SAMA (tanpa timestamp)
- Bitmap config: RGB_565 — hemat memory
- Commit message dalam bahasa Indonesia
