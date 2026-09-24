# MochiStitch 🍡

**MochiStitch** (`com.mochistitch.app`) is an Android native app that merges comic or webtoon pages into continuous vertical long-strip images. Built with Kotlin, Jetpack Compose, and Material 3.

One engine principle defines the build: **a cut line never crosses ink** (balloons, panels, text). Oversized pages are split only at the center of edge-free row bands (pure-Kotlin scan, no OpenCV/ML); output-file boundaries avoid pixel-continuous page pairs; forced seam cuts and gapless pages are flagged for review in the preview.

---

## ✨ Features

- **Vertical stitching**: stack full pages vertically, scaled proportionally to the strip width — zero source pixels are discarded.
- **Safe splitting**: oversized pages are cut only through edge-free rows (never through balloons), and continuous page pairs are kept in the same output file; risky seams are flagged in the preview.
- **Page-boundary splitting**: split the output into multiple files (`WHOLE`, `MAX_HEIGHT`, or `PAGES_PER_PACK` rules).
- **Archive input**: open `.zip`, `.cbz`, `.rar`, `.cbr`, `.7z`, and `.cb7` directly — pages stream to disk so large archives stay memory-safe.
- **Archive output**: ZIP/CBZ packaging with MediaStore delivery (Pictures/MochiStitch folder on Android 10+, FileProvider share on older devices).
- **Batch queue**: import several comics, set a packaging format per title, and process them all in one run.
- **Memory conscious**: RGB_565 bitmaps, bounded decode sampling, region-decode edge checks, and small on-screen previews instead of full-height strips held in RAM.
- **Material 3 UI**: four bottom tabs (Input, Result, Queue, Settings) with count badges, light/dark/system theming.

---

## 🏗️ Architecture

```text
MochiStitch
 ├── :app            # 4-tab shell (Input/Result/Queue/Settings), ViewModel, publishing
 ├── :core-common    # ComicProject batch model
 ├── :core-imaging   # SeamScan, PageGrouper, StripRenderer, StripBuilder, FileNamer
 ├── :core-archive   # ZIP/CBZ/RAR/CBR/7Z read + ZIP/CBZ write
 ├── :core-settings  # DataStore-backed StitchSettings
 └── :core-ui        # M3 theme, PageStrip, SettingsPanel, SlicePreview
```

---

## 🚀 Getting Started & Build Instructions

### Prerequisites

- **JDK**: Java 17
- **Android SDK**: Compile SDK 35, Min SDK 24
- **Gradle**: 8.x (via the Gradle Wrapper `./gradlew`)

### Building the Project

1. **Clone the repository**:
   ```bash
   git clone https://github.com/tamvanpr/MochiStitch.git
   cd MochiStitch
   ```

2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Run Unit Tests**:
   ```bash
   ./gradlew test
   ```

4. **Full Verification Suite**:
   ```bash
   ./gradlew clean assembleDebug test --no-daemon
   ```

Builds are verified by GitHub Actions CI (`./gradlew test assembleDebug`) on every push and pull request.

Setiap build menghasilkan 5 APK: `universal`, `armeabi-v7a`, `arm64-v8a`, `x86`, dan `x86_64` — unduh yang sesuai perangkatmu dari artefak Actions atau rilis GitHub.

---

## 🔑 Release Signing

Release APK signing is managed via environment variables. If environment variables are omitted, the build falls back gracefully to debug signing for testing:

- `KEYSTORE_FILE`: Path to `.jks` or `.keystore` file
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias
- `KEY_PASSWORD`: Key password

```bash
export KEYSTORE_FILE="/path/to/release.keystore"
export KEYSTORE_PASSWORD="your_password"
export KEY_ALIAS="your_alias"
export KEY_PASSWORD="your_key_password"

./gradlew assembleRelease
```

---

## 📄 License

MochiStitch is released under open-source standards. See project repository details for licensing terms.
