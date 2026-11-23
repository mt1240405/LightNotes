# LightNotes Lite

> **Open -> Write -> Read -> Share**  
> *Local-first now. Sync-ready by design. Cloud when needed.*

An ultra-fast, offline-first personal technical notebook and developer scratchpad for Android. Pure Kotlin/JVM, Android Views, Material 3 Expressive aesthetics, flat surfaces, subtle 1dp borders, light & pure OLED `#000000` dark modes, and amber gold accents (`#FFB200`).

---

## ⚡ The Bloat Problem & The LightNotes Solution

Default manufacturer note applications (ColorOS Notes, Samsung Notes, Apple Notes, MIUI Notes) have quietly evolved into multi-hundred-megabyte webview-heavy portals laden with cloud lock-in, proprietary backup formats, sluggish startup times, and intrusive accounts.

| Characteristic | Stock OEM Notes (ColorOS, Samsung, Apple) | LightNotes Lite |
|:---|:---|:---|
| **Release APK Size** | 70 MB – 140 MB | **~2.47 MB** (Verified R8 + Resource Shrinking) |
| **Debug APK Size** | 100 MB – 180 MB | **~7.99 MB** |
| **Startup to Typing** | 1,200ms – 2,500ms (cloud sync checks, account banners) | **< 100ms** (immediate text surface) |
| **Core Workflow** | Heavy Chromium WebViews, JavaScript bridges, analytics | **100% Pure Kotlin Android Views + Native Markwon** |
| **Native Toolchains** | Heavy C++ / Rust shared libraries, Chromium runtimes | **Zero Rust/Cargo, Zero NDK binaries** |
| **Data Ownership** | Proprietary cloud sync; export locked to account dumps | **Local SQLite + Portable `lightnotes-backup` v1 JSON** |
| **Display Refresh** | Clamped to 60Hz or jittery 90Hz | **Native 144Hz Peak Refresh Rate** |
| **Sharing** | Slow export menus, cloud links, watermarked PDFs | **Native Plain Text & Markdown Sharesheet (< 100ms)** |

---

## 🚀 Key Features

* **Instant Note Capture:** Launch directly into an editable text surface with zero layout jumps, zero keyboard flicker, and zero clipped titles.
* **Non-Blocking Debounced Autosave:** Changes mutate in-memory state immediately, incrementing document revisions and committing to Room in the background (~400ms debounce).
* **Single Adaptive Chrome:** Minimal, high-clarity top bar (`Back`, `Preview / Edit`, `Share`, `Overflow Menu`) and a single compact formatting bar docked above the IME.
* **Fast Native Markdown Preview:** Native Markwon rendering without WebViews. Supports headings, bold, italic, strikethrough, bulleted/numbered lists, blockquotes, code blocks, links, and **interactive checklists** (`- [ ]` / `- [x]`).
* **First-Class Native Sharing (< 100ms):** Reads live in-memory edit buffers immediately without requiring save-and-close. Share as clean plain text or raw `.md` source.
* **Offline SQLite FTS4 Search:** Fast as-you-type search ranking title matches strictly above body matches, prioritizing pinned and recent notes.
* **Stage A Sync-Ready Architecture:** 4-layer separation (`App UI`, `Core`, `Storage`, `Platform Services`), Room v3 schema with non-destructive migration, UUID `globalId` tracking, monotonically increasing document revisions, and transactional `sync_outbox` logging.
* **Portable Versioned Backup:** Export and import notes with the standard `lightnotes-backup` v1 JSON format or passphrase-protected AES-256-GCM `.enotes` archives.

---

## 📦 Verified APK Size & Metrics

| Build Variant | Baseline Size | LightNotes Lite v1.0 | Size Reduction | Target Verification |
|:---|:---|:---|:---|:---|
| **Debug APK (`app-debug.apk`)** | 13.05 MB (13,681,634 B) | **7.99 MB** (8,377,268 B) | **-38.8%** (-5.06 MB) | Clean debug build |
| **Release APK (`app-release.apk`)** | 3.56 MB (3,727,818 B) | **2.47 MB** (2,586,741 B) | **-30.6%** (-1.08 MB) | **STRICTLY < 10 MB (PASS)** |

---

## 🛠️ Dependency & Toolchain Pruning

To achieve an ultra-light release footprint and instant startup times, all heavy out-of-scope subsystems were eliminated:

| Subsystem Pruned | Previous State | Replacement / Current State |
|:---|:---|:---|
| **C++17 NDK Subsystem** | `CMakeLists.txt`, `native_search.cpp` | Pure Kotlin title-weighted `SearchRanker` + SQLite FTS4 |
| **Math Engines** | `JLaTeXMathAndroid`, KaTeX assets, Math Studio | Clean native text & Markwon core |
| **Diagram Engine** | Mermaid JS WebView bridge & HTML assets | Markdown code blocks & plaintext tables |
| **PDF Studio** | Chromium `WebViewPdfExporter` & custom CSS editor | Native plain text & `.md` Sharesheet |
| **QR Code Utility** | ZXing barcode library | Android Sharesheet (`Intent.ACTION_SEND`) |
| **Background Daemons** | `WorkManager` background workers | On-demand Storage Access Framework (SAF) |
| **Image Loading** | `Coil` image loader library | Native drawables & vector SVGs |

---

## 📂 Portable Backup Specification (`lightnotes-backup` v1)

LightNotes backups adhere to an open, portable JSON schema:

```json
{
  "format": "lightnotes-backup",
  "version": 1,
  "exportedAt": "2026-11-24T00:00:00Z",
  "notes": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Architecture Overview",
      "content": "# Architecture Overview\nSystem design notes...",
      "createdAt": 1700000000000,
      "updatedAt": 1700005000000,
      "folder": "Engineering",
      "pinned": true,
      "favorite": false
    }
  ],
  "folders": [
    {
      "id": "330e8400-e29b-41d4-a716-446655440011",
      "name": "Engineering",
      "color": "#FFB200",
      "createdAt": 1700000000000
    }
  ]
}
```

Optional **AES-256-GCM** `.enotes` encrypted archives are generated using standard `javax.crypto` (PBKDF2WithHmacSHA256, 10,000 iterations, 16-byte random salt, 12-byte IV) with zero external dependency overhead.

---

## 🧪 Automated JVM Test Suite

Run the full Robolectric + Espresso JVM regression suite:

```powershell
.\gradlew.bat testDebugUnitTest
```

100% of the automated tests pass across 5 suites:
1. `EditorRegressionTest.kt`: Markdown rendering, malformed input resilience, undo/redo cursor restoration, interactive checkbox toggling, formatting wrap/insert, physical keyboard shortcuts (`Ctrl+B/I/Z/Y`), and live unsaved text sharing.
2. `SearchAndFolderTest.kt`: FTS4 search ranking (title matches strictly above body matches), exclusion of deleted notes, folder filtering, and note lifecycle.
3. `SyncOutboxAndMigrationTest.kt`: Non-destructive Room `MIGRATION_2_3` from v2 (100% data preservation, UUID backfilling, legacy table retention) and atomic outbox logging.
4. `BackupAndSecurityTest.kt`: JSON backup round-trip, AES-256-GCM `.enotes` encrypted export/import, conflict safety, and biometric authentication gate.
5. `PerformanceBenchmarkTest.kt`: Search ranking latency across 1,200 notes (< 100ms) and 5,000-character Markdown rendering benchmark.

---

## 🔨 Building & Installing

### Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk` (~7.99 MB)

### Build Release APK (R8 Code & Resource Shrinking)
```powershell
.\gradlew.bat assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk` (**~2.47 MB**)

### Install via ADB
```powershell
adb install -r -d ./app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ Project Directory Structure

```
LightNotes/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/lightnotes/app/
│   │   │   │   ├── core/
│   │   │   │   │   ├── model/         # Portable Domain Models (Note, Folder, SyncState, SyncOutboxEvent)
│   │   │   │   │   ├── repository/    # NoteRepository & FolderRepository interfaces
│   │   │   │   │   └── search/        # Pure Kotlin SearchRanker (title-weighted scoring)
│   │   │   │   ├── storage/
│   │   │   │   │   └── repository/    # LocalNoteRepository & LocalFolderRepository (Room + Outbox)
│   │   │   │   ├── data/              # Room v3 Entities, DAOs, AppDatabase & Migrations
│   │   │   │   ├── platform/          # DeviceIdentityProvider & PreferencesRepository (DataStore)
│   │   │   │   ├── markdown/          # Native Markwon MarkdownEngine & Checkbox toggler
│   │   │   │   ├── editor/            # EditHistoryManager (bounded typing coalescing)
│   │   │   │   ├── backup/            # BackupService (JSON & AES-GCM) & HeyTapImporter
│   │   │   │   ├── security/          # BiometricHelper (BiometricPrompt)
│   │   │   │   └── ui/                # MainActivity, NoteEditorActivity, SettingsActivity, ViewModels
│   │   │   ├── res/                   # Flat ColorOS layout XMLs, vector drawables, OLED dark theme
│   │   │   └── AndroidManifest.xml
│   │   └── test/java/com/lightnotes/app/ # 5 Robolectric + Espresso JVM Test Suites
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── docs/
│   ├── ARCHITECTURE.md                # Complete 4-Layer Architecture Guide
│   └── SYNC_ARCHITECTURE.md           # 11 Sync-Ready REST & Stage A-E Specifications
└── README.md
```
