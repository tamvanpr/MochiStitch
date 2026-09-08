# MochiStitch — Agent Context

Proyek: MochiStitch (`com.mochistitch.app`)
Platform: Android native, Kotlin + Jetpack Compose
Purpose: Merger gambar komik dengan fitur Mochi Smart (OpenCV edge detection)

## Struktur Modul
- `app/` — UI layer (Compose), ViewModel
- `core-imaging/` — Stitch/Merge/Split engine
- `core-mochismart/` — OpenCV contour detection
- `core-settings/` — Preference handling
- `core-ui/` — Reusable Compose components
- `core-archive/` — Archive/unzip utilities
- `core-common/` — Shared utilities

## Aturan Penting
- Build hanya diverifikasi via GitHub Actions CI/CD
- Default output format: JPG (bukan PNG)
- Bitmap config: RGB_565 (bukan ARGB_8888) — hemat memory
- Jangan touch `core-imaging` native C++ kecuali diminta
- Commit message dalam bahasa Indonesia
