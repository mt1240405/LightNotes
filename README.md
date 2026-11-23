# LightNotes

A lightweight, offline-first native Android Technical Notebook & Developer Scratchpad (`com.lightnotes.app`) built with pure **Kotlin/JVM** and an optimized **C++17 NDK** search ranker (**zero Rust/Cargo**, **zero OEM framework dependencies**). LightNotes delivers an authentic **Realme / ColorOS** flat OLED aesthetic (`#000000` pure dark / `#F7F7F9` light, `#FFB200` amber-gold accents, `1dp` hairlines, zero gradients) with up to **144Hz high refresh rate** display support across universal Android 8.0+ devices (API 26–35).

[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026--35)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg)](https://kotlinlang.org)
[![Material 3 Expressive](https://img.shields.io/badge/Material%203-1.14.0%20Expressive-orange.svg)](https://github.com/material-components/material-components-android)
[![APK Size](https://img.shields.io/badge/APK%20Size-11.67%20MB-brightgreen.svg)](app/build/outputs/apk/debug/app-debug.apk)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

---

## Overview & Design Philosophy

LightNotes was engineered to provide a high-performance alternative to both resource-heavy Electron/WebView notebook apps and proprietary OEM-locked tools:
1. **Universal Android Compatibility:** Zero dependency on proprietary `com.oplus.*` system services or system UID permissions (`sharedUserId`). Runs natively on any Android phone, tablet, or foldable running Android 8.0 (API 26) through Android 15 (API 35).
2. **Lean Binary & Runtime Footprint:** Total debug APK binary size is **11.67 MB** (12,238,703 bytes). No Rust toolchains, no SQLCipher bloat, and no background network telemetry.
3. **Flat ColorOS / Realme Visual Identity:** Strict Material 3 Expressive styling with pure `#000000` OLED background, `#141416` container, `#FFB200` amber primary, `1dp` outline strokes, `12dp` rounded corners, and zero bulky pill buttons or gradients.
4. **144Hz Display Rate Optimization:** Seamless display mode switching via `HighRefreshRateHelper` utilizing `preferredDisplayModeId` (API 30+) and `requestedFrameRate` (API 35+) for silky scrolling and fluid keyboard animations.

---

## Key Features

### 1. Everyday Notes & Editor UI
- **Material 3 Expressive Contextual Top Bar:**
  - *Reading/Preview Mode:* Contextual actions: `Back`, `Edit` (`btnToggleMode`), `Share`, and `More`. `Undo` and `Redo` are hidden.
  - *Editing Mode:* Displays `Back`, `Undo`, `Redo`, `Preview` (`btnToggleMode`), and `More`. `Share` is hidden.
  - *Single Pin Indicator:* Unified, duplicate-free note pinning represented exclusively by `ivInlinePin` in the note header.
- **Single-Line Metadata Bar:** Positioned cleanly above the title with 12dp top padding: timestamp, character/word count, folder indicator, and pin toggle.
- **Two-State Dynamic Bottom Toolbar:**
  - *State A (Keyboard Closed):* Centered floating pill action bar (`layoutFloatingToolbar`): `AI Assist`, `Checklist`, `Voice Note`, and `+` (Quick Insert).
  - *State B (Keyboard Open):* Docked flat 6-icon bar (`layoutDockedToolbar`) flush above the IME: `AI Assist`, `/` (Slash Commands), `Checklist`, `Aa` (Formatting toggle), `Voice Note`, and `+` (Quick Insert).
  - *Expandable Formatting Sub-Row (`layoutFormattingRow`):* One-tap access to Headings (H1/H2/H3), Bold, Italic, Strikethrough, Bullet Lists, Numbered Lists, Task Lists, Blockquotes, Links, and Dividers.
- **Interactive Checkboxes in Preview Mode:** Direct touch event handling on Markwon `TaskListSpan` lines toggles `- [ ]` $\leftrightarrow$ `- [x]` immediately, preserving scroll offset and persisting changes to Room database without switching to edit mode.
- **Floating Text Selection Bar:** Quick-formatting popover on text selection for color highlights (Amber, Coral, Blue, Green, Gray), Bold, Underline, Strikethrough, and Code.
- **Adaptive Organization:** 2-column staggered grid or 1-column list with horizontal folder tab navigation (All, Pinned, Favorites, User Folders, Archived, Trash).

### 2. Markdown, Math & Diagram Engines
- **Native Spannable Markwon Pipeline (`4.6.2`):** Direct canvas rendering using `MarkwonInlineParserPlugin`, `TablePlugin`, `TaskListPlugin`, `StrikethroughPlugin`, `HtmlPlugin`, `LinkifyPlugin`, `CoilImagesPlugin`, and `SyntaxHighlightPlugin` (`Prism4j` + `LightNotesGrammarLocator`).
- **Dual Math Rendering:**
  - *Native JLaTeXMath Android (`0.2.0`):* Zero-latency inline (`$E=mc^2$`) and block (`$$\int x dx$$`) formula rendering directly in `TextView` spans without WebViews.
  - *Offline KaTeX Engine:* Lazy-loaded offscreen renderer with local assets (`file:///android_asset/katex/`) and an LRU bitmap cache (`LruCache<String, Bitmap>`).
- **Offline Mermaid Diagrams:** Lazy-loaded offscreen renderer (`file:///android_asset/mermaid/`) supporting flowcharts, sequence diagrams, and class diagrams rendered to cached bitmaps.

### 3. Advanced Technical Workspace (Settings-Controlled)
*Cleanly gated behind the "Advanced Power-User Menus" toggle in Settings to preserve simplicity for casual note-takers:*
- **Math Formula Studio (`MathStudioBottomSheet`):** Interactive formula builder with categories for sums & limits, calculus, set theory & domains, piecewise functions, matrices, physics constants, Greek alphabet, and logic symbols with live preview.
- **Visual Table Builder (`TableBuilderBottomSheet`):** Configurable row/column matrix builder generating clean GitHub-Flavored Markdown tables.
- **Callouts & Code Blocks (`CalloutCodeBottomSheet`):** One-tap insertion of callout admonitions (`> [!NOTE]`, `> [!TIP]`, `> [!WARNING]`), syntax-highlighted code blocks (`kotlin`, `python`, `cpp`, `java`, `sql`, `json`, `bash`), and footnotes (`[^1]`).
- **Developer Workspace (`DevToolsBottomSheet`):** Built-in scratchpad utilities:
  - *JSON/XML Tab:* Minification, pretty-printing, and syntax validation.
  - *Regex Tester:* Real-time regex pattern compilation with colored span match highlighting.
  - *Codec Tools:* Base64 encoding/decoding, URL encoding/decoding, and Unix Epoch timestamp converter.
  - *Text Diff:* Side-by-side / unified line-level text diff viewer.
- **Knowledge Graph & Navigation (`BacklinksTocBottomSheet`):**
  - `#tags` indexed via Room FTS4 and dedicated `note_tags` table.
  - `[[Wikilinks]]` with auto-creation of missing destination notes and bidirectional backlink detection.
  - Live Table of Contents (TOC) generated from note heading structure with direct jump-to-heading navigation.

### 4. Security, Multimedia & Portability
- **AndroidX Biometric Lock (`BiometricHelper`):** Biometric fingerprint/face and device credential authentication protecting individual notes and settings. Settings toggle is protected by an authentication challenge before deactivation.
- **Encrypted `.enotes` Container (`BackupService`):** Password-derived AES-256-GCM authenticated encryption (`PBKDF2WithHmacSHA256`, 10,000 iterations, 16-byte random salt, 12-byte random IV, 128-bit auth tag) with `"ENOTES1"` magic header.
- **HeyTap / ColorOS Importer (`HeyTapImporter`):** One-click import for `heytap-notes-backup.json` backups exported from Realme/OPPO/OnePlus devices, converting proprietary HTML to Markdown and parsing ordinal timestamps (`Jul 28th, 2024`).
- **PDF Export Studio with LaTeX Typesetting & Advanced CSS Menu (`WebViewPdfExporter`, `PdfExportBottomSheet`):**
  - *Live Unsaved Content Pipeline:* Reads live real-time strings directly from the title and editor/preview fields upon opening Share or PDF Export, dispatching an immediate parallel Room database sync.
  - *Full-Bleed OLED Black (`#000000`):* Chromium print compositor background fill protection (`@page { background-color: #000000; }`, `* { -webkit-print-color-adjust: exact !important; }`) with `#F2F2F5` text and `#FFB200` accents, ensuring zero uncolored white borders.
  - *Inline SVG Checkboxes & Margin Protection:* Task list items rendered as clean flexbox rows (`display: flex; gap: 8pt;`) with crisp `11pt × 11pt` inline SVGs (`#FFB200` fill with black checkmark when checked, `1.6pt` border when unchecked) with zero negative margins and strict word-break overflow protection.
  - *LaTeX Academic Typesetting Preset:* Classical serif typography (`"Latin Modern Roman", "Computer Modern", Georgia, "Noto Serif", serif`), auto-hyphenated text justification (`text-align: justify; hyphens: auto;`), `22mm` margins, `0.6pt` section rule underlines, centered display math equations, and formal title/date header. Also provides Clean Modern (ColorOS) and Technical Spec presets.
  - *Dedicated Advanced CSS Styling Menu:* Live monospace print stylesheet editor with persistence in `PreferencesRepository`, Reset to Preset, and one-tap CSS snippets (`Two-Column Layout`, `Numbered Academic Headings`, `IEEE Bordered Tables`, `Draft Watermark`, `Page Break Before H1`).
  - *Offline Math & Document Geometry:* Offline KaTeX CSS/JS rendering, paper themes (Clean Light, Warm Sepia, Dark OLED), page sizes (A4, Letter, Legal, A5, Custom mm), and running page number footers (`@bottom-right`).
- **Scheduled Automated Backups (`AutoBackupWorker`):** WorkManager periodic task creating scheduled JSON/markdown backups to user-selected Storage Access Framework (SAF) folder URIs.
- **Live Markdown Mirroring (`MarkdownMirrorService`):** Background conflict-safe mirroring of notes into `.md` files on external storage.
- **Attachment Storage (`AttachmentManager`):** SHA-256 deduplicated attachment management in internal storage, with native `PdfRenderer` first-page thumbnail generation.
- **Voice Notes (`VoiceNoteRecorder`):** Low-latency voice memo recording (MPEG-4 AAC 44.1kHz, 128 kbps) with inline duration tracking and playback.
- **QR Code Sharing (`QrCodeHelper`):** Standalone ZXing-powered QR code generator and scanner for quick text snippet transfer.
- **Bring-Your-Own-Key REST AI Assist (`AiService`):** Native `HttpURLConnection` client for OpenAI-compatible REST endpoints (customizable model and endpoint in Settings, zero external AI SDKs).

---

## Tech Stack & Dependency Table

All dependencies are verified from [app/build.gradle.kts](app/build.gradle.kts) and [CMakeLists.txt](app/src/main/cpp/CMakeLists.txt):

| Category | Component / Coordinate | Version | Purpose |
|---|---|---|---|
| **Core & AndroidX** | `androidx.core:core-ktx` | `1.15.0` (resolves to `1.16.0`) | Modern Android KTX extensions & IME APIs |
| **AppCompat** | `androidx.appcompat:appcompat` | `1.7.0` | Base activity and theme support |
| **Material Design** | `com.google.android.material:material` | `1.14.0` | Material 3 Expressive components, Outlined cards |
| **Layouts** | `androidx.constraintlayout:constraintlayout` | `2.1.4` | Constraint-based responsive layouts |
| **Lists** | `androidx.recyclerview:recyclerview` | `1.3.2` | Folder tabs and staggered notes grid |
| **Lifecycle** | `androidx.lifecycle:lifecycle-viewmodel-ktx` | `2.8.4` | Coroutine-scoped ViewModels |
| **Lifecycle** | `androidx.lifecycle:lifecycle-livedata-ktx` | `2.8.4` | Reactive state observers |
| **Lifecycle** | `androidx.lifecycle:lifecycle-runtime-ktx` | `2.8.4` | Lifecycle-aware coroutine scopes |
| **Activity** | `androidx.activity:activity-ktx` | `1.9.1` | Modern Activity result contracts & Edge-to-Edge |
| **Coroutines** | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.8.1` | UI and background asynchronous processing |
| **Coroutines** | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.8.1` | Core coroutine primitives |
| **Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.6.3` | Fast JSON payload serialization |
| **DataStore** | `androidx.datastore:datastore-preferences` | `1.1.1` | Reactive preference storage replacing SharedPreferences |
| **WorkManager** | `androidx.work:work-runtime-ktx` | `2.9.1` | Background periodic automated backups |
| **Biometrics** | `androidx.biometric:biometric` | `1.1.0` | BiometricPrompt authentication |
| **Storage (SAF)** | `androidx.documentfile:documentfile` | `1.0.1` | Storage Access Framework tree and file operations |
| **Image Loading** | `io.coil-kt:coil` | `2.6.0` | Kotlin-first image decoding & caching |
| **QR Code** | `com.google.zxing:core` | `3.5.3` | QR code generation and decoding |
| **Database** | `androidx.room:room-runtime` | `2.6.1` | SQLite object mapping with Flow support |
| **Database** | `androidx.room:room-ktx` | `2.6.1` | Kotlin Coroutines extensions for Room |
| **Database Compiler**| `androidx.room:room-compiler` | `2.6.1` | Room annotation processor (KAPT) |
| **Markdown Core** | `io.noties.markwon:core` | `4.6.2` | Native Spannable Markdown renderer |
| **Markdown Tables** | `io.noties.markwon:ext-tables` | `4.6.2` | GFM table rendering |
| **Markdown Strikethrough** | `io.noties.markwon:ext-strikethrough` | `4.6.2` | `~~strikethrough~~` span plugin |
| **Markdown Task Lists** | `io.noties.markwon:ext-tasklist` | `4.6.2` | TaskListSpan checkbox rendering |
| **Markdown LaTeX** | `io.noties.markwon:ext-latex` | `4.6.2` | MathSpan integration |
| **Markdown Inline Parser** | `io.noties.markwon:inline-parser` | `4.6.2` | Inline syntax parser (strictly required by LaTeX plugin) |
| **Markdown HTML** | `io.noties.markwon:html` | `4.6.2` | HTML tag sanitization and rendering |
| **Markdown Linkify** | `io.noties.markwon:linkify` | `4.6.2` | Automatic URL and web address detection |
| **Markdown Images** | `io.noties.markwon:image-coil` | `4.6.2` | Inline image rendering backed by Coil |
| **Markdown Syntax** | `io.noties.markwon:syntax-highlight` | `4.6.2` | Code block highlighting via Prism4j |
| **Prism4j** | `io.noties:prism4j` | `2.0.0` | Pure JVM syntax highlighter |
| **LaTeX Math** | `ru.noties:jlatexmath-android` | `0.2.0` | Native JVM LaTeX formula rendering |
| **Native NDK** | `C++17 (libnotes_search.so)` | `CMake 3.22.1` | C++17 JNI search ranker (`-O3 -flto`) |
| **Testing** | `junit:junit` | `4.13.2` | Core JVM test runner |
| **Testing** | `org.robolectric:robolectric` | `4.18.1` | Full Android framework simulation on JVM |
| **Testing** | `androidx.test.ext:junit` | `1.3.0` | AndroidX test extensions for JUnit |
| **Testing** | `androidx.test.espresso:espresso-core` | `3.6.1` | UI assertions on JVM / Android |
| **Testing** | `androidx.test:core-ktx` | `1.6.1` | Kotlin test core utilities (`ActivityScenario`) |

---

## Build, Test & Install Instructions

### Prerequisites
1. **JDK 17:** Ensure `JAVA_HOME` points to a valid JDK 17 installation (e.g. Microsoft OpenJDK 17).
2. **Android SDK:** Installed with `platforms;android-35` and `build-tools;34.0.0` or `35.0.0`.
3. **Android NDK (Optional for native build):** Version `25.x` or `26.x`.

### Execution Commands (PowerShell)

```powershell
# Navigate to the project root
cd .\rea-sandbox\LightNotes

# 1. Run Automated Robolectric + Espresso JVM Regression Suite
.\gradlew.bat testDebugUnitTest

# 2. Compile Debug APK
.\gradlew.bat assembleDebug

# 3. Install to Connected Android Device via ADB
& "C:\Users\tanma\Downloads\platform-tools-latest-windows\platform-tools\adb.exe" install -r -d ./app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```
LightNotes/
├── app/
│   ├── build.gradle.kts                        # Gradle build configuration & dependencies
│   ├── proguard-rules.pro                      # R8 code shrinking and ProGuard rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml             # Edge-to-edge manifest & FileProvider
│       │   ├── cpp/                            # C++17 NDK Search Subsystem
│       │   │   ├── CMakeLists.txt              # CMake configuration (-O3, -flto, C++17)
│       │   │   └── native_search.cpp           # JNI candidate note relevance ranker
│       │   ├── assets/                         # Offline Web & Math Assets
│       │   │   ├── katex/                      # Offline KaTeX (katex.min.js, katex.min.css)
│       │   │   └── mermaid/                    # Offline Mermaid (mermaid.html)
│       │   ├── java/com/lightnotes/app/        # Kotlin Application Source
│       │   │   ├── LightNotesApplication.kt    # Dynamic Colors application class
│       │   │   ├── ai/                         # BYOK REST AI Assistant
│       │   │   │   ├── AiAssistBottomSheet.kt  # AI prompts bottom sheet UI
│       │   │   │   ├── AiPreferences.kt        # AI endpoint, key, and model store
│       │   │   │   └── AiService.kt            # Native HttpURLConnection OpenAI client
│       │   │   ├── attachments/                # Media & Document Attachments
│       │   │   │   └── AttachmentManager.kt    # SHA-256 deduplicated file storage & PdfRenderer
│       │   │   ├── audio/                      # Audio Notes Subsystem
│       │   │   │   └── VoiceNoteRecorder.kt    # Low-latency AAC/M4A voice memo recorder
│       │   │   ├── backup/                     # Migration, Storage & Cryptography
│       │   │   │   ├── AutoBackupWorker.kt     # WorkManager periodic SAF backup worker
│       │   │   │   ├── BackupService.kt        # AES-256-GCM + PBKDF2 .enotes backup service
│       │   │   │   ├── HeyTapImporter.kt       # ColorOS/HeyTap HTML & ordinal date parser
│       │   │   │   └── NoteBackupPayload.kt    # Serializable backup data wrappers
│       │   │   ├── data/                       # Room Persistence & DataStore
│       │   │   │   ├── AppDatabase.kt          # Room Database (v2, Migration 1->2)
│       │   │   │   ├── AppPreferences.kt       # Fast SharedPreferences UI caches
│       │   │   │   ├── AttachmentEntity.kt     # attachments table
│       │   │   │   ├── FolderDao.kt            # Folder CRUD operations
│       │   │   │   ├── FolderEntity.kt         # folders table
│       │   │   │   ├── NoteDao.kt              # Reactive Flow queries & FTS4 MATCH
│       │   │   │   ├── NoteEntity.kt           # notes table
│       │   │   │   ├── NoteFtsEntity.kt        # notes_fts FTS4 virtual table
│       │   │   │   ├── NoteLinkEntity.kt       # note_links table (bidirectional wikilinks)
│       │   │   │   ├── NoteTagEntity.kt        # note_tags table (indexed #tags)
│       │   │   │   ├── PreferencesRepository.kt# Jetpack DataStore preferences repository
│       │   │   │   └── TodoEntity.kt           # todos table
│       │   │   ├── editor/                     # Editor State & Undo/Redo Engine
│       │   │   │   └── EditHistoryManager.kt   # Coalescing undo/redo state manager (450ms)
│       │   │   ├── export/                     # Printing & SAF File Mirroring
│       │   │   │   ├── MarkdownMirrorService.kt# Real-time conflict-safe SAF .md mirror
│       │   │   │   ├── NativePdfExporter.kt    # Canvas-level PdfDocument exporter
│       │   │   │   └── WebViewPdfExporter.kt   # PrintManager HTML+KaTeX PDF printer
│       │   │   ├── markdown/                   # Markdown & Math Rendering Pipeline
│       │   │   │   ├── KatexRenderer.kt        # Lazy offscreen KaTeX renderer + LRU cache
│       │   │   │   ├── LightNotesGrammarLocator.kt # Prism4j syntax grammar definitions
│       │   │   │   ├── MarkdownEngine.kt       # Markwon pipeline & task list regex toggle
│       │   │   │   └── MermaidRenderer.kt      # Lazy offscreen Mermaid renderer + LRU cache
│       │   │   ├── search/                     # Hybrid Search Subsystem
│       │   │   │   └── NativeSearchOptimizer.kt# JNI bridge with Kotlin fallback ranker
│       │   │   ├── security/                   # Biometric Security
│       │   │   │   └── BiometricHelper.kt      # AndroidX BiometricPrompt helper
│       │   │   ├── sharing/                    # Offline QR Sharing
│       │   │   │   └── QrCodeHelper.kt         # ZXing QR code encoder and decoder
│       │   │   └── ui/                         # Activities, Adapters & Bottom Sheets
│       │   │       ├── FolderTabAdapter.kt     # Horizontal folder filter tabs adapter
│       │   │       ├── HighRefreshRateHelper.kt# 144Hz display rate optimizer
│       │   │       ├── MainActivity.kt         # Staggered notes grid & search
│       │   │       ├── NoteEditorActivity.kt   # Dual-mode editor & two-state toolbars
│       │   │       ├── NotesAdapter.kt         # Note cards adapter (Outlined M3 cards)
│       │   │       ├── SettingsActivity.kt     # App preferences, backups, and biometrics
│       │   │       └── dialogs/                # Bottom Sheet Dialogs
│       │   │           ├── BacklinksTocBottomSheet.kt # TOC outline & backlinks
│       │   │           ├── CalloutCodeBottomSheet.kt  # Callout & code block templates
│       │   │           ├── DevToolsBottomSheet.kt     # JSON/XML/Regex/Codec/Diff tools
│       │   │           ├── MathStudioBottomSheet.kt   # Interactive math notation studio
│       │   │           ├── PdfExportBottomSheet.kt    # Typesetting & print configuration
│       │   │           ├── RealmeShareBottomSheet.kt  # ColorOS-style sharing sheet
│       │   │           └── TableBuilderBottomSheet.kt # Visual Markdown table builder
│       │   └── res/                            # Resources & Themes
│       │       ├── drawable/                   # Vector drawables & theme shapes
│       │       ├── layout/                     # Material 3 XML layouts
│       │       ├── values/                     # Strings, dimensions, light theme (#F7F7F9)
│       │       ├── values-night/               # Pure OLED dark theme (#000000)
│       │       └── xml/                        # FileProvider paths configuration
│       └── test/                               # JVM Automated Regression Test Suite
│           └── java/com/lightnotes/app/
│               ├── EditorRegressionTest.kt     # Markdown, undo/redo, gating, pin & task tests
│               └── SecurityAndBackupTest.kt    # Biometric gate & AES-GCM backup roundtrip tests
├── docs/
│   └── ARCHITECTURE.md                         # Deep Technical Architecture Reference
├── build.gradle.kts                            # Root build script
├── gradle.properties                           # Gradle daemon & JVM memory configurations
├── settings.gradle.kts                         # Repository definitions & modules
└── README.md                                   # This file
```

---

## Architectural Deep Dive

For detailed technical specifications, database schema definitions, JNI search scoring mathematics, encryption format specs, and lifecycle sequence diagrams, consult **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

---

## License

This project is open-source software licensed under the [MIT License](LICENSE).
