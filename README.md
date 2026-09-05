# MochiStitch 🍡

**MochiStitch** (`com.mochistitch.app`) is a modern, high-performance Android native comic merger app designed for manga, webtoon, and comic readers and creators. Built with Kotlin, Jetpack Compose, Material 3, and OpenCV.

---

## ✨ Features

- **Comic Stitching & Merging**: Merge multiple comic or manga page images vertically or horizontally into continuous long-strip images.
- **MochiSmart (OpenCV Integration)**: Intelligent seam detection and edge analysis powered by native OpenCV (`org.opencv:opencv`) to automatically identify optimal split and join points across panels.
- **Archive Support**: Direct extraction and reading from `.cbz`, `.zip`, and custom image folders without manual unpacking.
- **Memory & OOM Optimized**: High-performance streaming image pipeline utilizing downsampling (`inSampleSize`) and explicit bitmap lifecycle management to handle high-resolution image batches safely on mobile devices.
- **Customizable Output**: Adjust stitch direction, gaps, background colors, and export formats (JPEG, PNG, WEBP) with customizable quality settings.
- **Modern Material 3 UI**: Clean, adaptive user interface with support for dark mode, custom adaptive launcher icons, and smooth Compose transitions.

---

## 🏗️ Architecture

MochiStitch uses a modular architecture separating concerns into focused core modules:

```text
MochiStitch
 ├── :app              # Application entry point, navigation, and activity setup
 ├── :core-common      # Models, extensions, and common utility functions
 ├── :core-imaging     # Stitching engine, bitmap processing pipeline, downsampling & canvas rendering
 ├── :core-mochismart  # OpenCV native wrapper for seam detection and smart contour analysis
 ├── :core-archive    # CBZ/ZIP archive parser and streaming file reader
 ├── :core-settings   # Jetpack DataStore preferences for persistent user settings
 └── :core-ui         # Material 3 design system, Compose theme, and shared UI components
```

---

## 🚀 Getting Started & Build Instructions

### Prerequisites

- **JDK**: Java 17
- **Android SDK**: Compile SDK 35, Min SDK 24
- **Gradle**: 8.x (using Gradle Wrapper `./gradlew`)

### Building the Project

1. **Clone the repository**:
   ```bash
   git clone https://github.com/mochistitch/mochistitch.git
   cd mochistitch
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
