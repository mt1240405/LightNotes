# LightNotes Lite — Complete User Guide
**Product Promise:** *Open -> Write -> Read -> Share*  
**Core Standard:** *Local-first now. Sync-ready by design. Cloud when needed.*

Welcome to **LightNotes Lite**, a fast, private, zero-bloat technical notebook and developer scratchpad for Android. LightNotes Lite runs 100% on native Kotlin/JVM, Android Views, and Material 3 without WebViews, heavy JavaScript bridges, or mandatory cloud lock-in.

---

## Table of Contents
1. [Getting Started](#1-getting-started)
2. [Markdown Editing & Interactive Checklists](#2-markdown-editing--interactive-checklists)
3. [The `/` Slash Commands Menu](#3-the--slash-commands-menu)
4. [Markdown Tables & Inline Images](#4-markdown-tables--inline-images)
5. [Document Export & Native Sharing](#5-document-export--native-sharing)
6. [Folders & FTS4 Full-Text Search](#6-folders--fts4-full-text-search)
7. [Batch Multi-Select & ZIP Archive Export](#7-batch-multi-select--zip-archive-export)
8. [Backups & Encryption (JSON & .enotes)](#8-backups--encryption-json--enotes)
9. [Biometric App Lock](#9-biometric-app-lock)
10. [BYOK AI Assistant Configuration](#10-byok-ai-assistant-configuration)

---

## 1. Getting Started

### Installation & Launch
- Download the official `app-release.apk` from the [GitHub Releases](https://github.com/mt1240405/LightNotes/releases) page.
- LightNotes Lite requires Android 8.0 (API 26) or higher.
- The release APK is under 3 MB and uses zero external tracking or background battery polling.

### Quick Tour of the Interface
- **Notes List (Home Screen):** View all active notes in a clean linear card layout. Pinned notes display an amber bookmark indicator.
- **Top Bar:** Quick view switcher (List vs Staggered Grid), Multi-Select button, and Settings gear icon.
- **Search Bar:** Real-time SQLite FTS4 full-text search with title-weighted ranking.
- **Folder Tabs:** Horizontally scrollable chips to filter notes by folder or view All notes.
- **New Note FAB:** Amber circular action button (`+`) in the bottom right corner.

---

## 2. Markdown Editing & Interactive Checklists

### Writing in the Editor
- **Autosave:** Edits to title and body are debounced and saved automatically to the local database. You never have to manually hit "Save".
- **Undo / Redo:** Dedicated Undo (`↶`) and Redo (`↷`) buttons are pinned to the left of the bottom toolbar for quick thumb access, with support for physical keyboard shortcuts (`Ctrl+Z`, `Ctrl+Y`).
- **Formatting Toolbar:** Easily insert Bold (`**text**`), Italic (`*text*`), Headings (`#`), Bullet Lists (`- `), Numbered Lists (`1. `), Checklists (`- [ ] `), Code Blocks (` ``` `), Quotes (`> `), Links, and Images.

### Interactive Task Checklists
- Create checklist items using standard Markdown syntax:
  ```markdown
  - [ ] Review sync architecture
  - [x] Room database migration verified
  ```
- **Live Preview Checkbox Toggling:** In Preview mode (`Preview` button in the top toolbar), tap any checkbox directly to toggle it between checked (`[x]`) and unchecked (`[ ]`). The underlying raw Markdown content is updated immediately, preserving scroll position and triggering autosave.
- **Home Screen Progress Badge:** Note cards with checklists show a progress chip (e.g., `3/5`) that turns green once all items are completed.

---

## 3. The `/` Slash Commands Menu

Typing `/` at the beginning of a line or after a space opens a compact Material 3 popup for rapid formatting insertions without reaching for the bottom toolbar:

| Command | Action | Inserted Template |
|:---|:---|:---|
| `/H1` | H1 Section Title | `# ` |
| `/H2` | H2 Subsection Heading | `## ` |
| `/To-do` | Interactive Task Checkbox | `- [ ] ` |
| `/Bullet` | Bulleted List Item | `- ` |
| `/Number` | Numbered List Item | `1. ` |
| `/Image` | Insert Local Photo | Opens system photo picker |
| `/Link` | Markdown Link | `[text](url)` |
| `/Code` | Multi-line Code Block | ` ```\n\n``` ` |
| `/Quote` | Blockquote | `> ` |
| `/AI` | Trigger AI Assistant | Prompts AI action on note |

---

## 4. Markdown Tables & Inline Images

### Markdown Tables
LightNotes Lite includes a native `TableLayout` renderer that overcomes single-TextView table clipping limitations:
- **Clean Column Wrapping:** Wide tables automatically distribute column widths with proportional weighting (`layout_weight = 1f`) and text auto-wrapping.
- **Multi-line Cells:** `<br>`, `<br/>`, and `<br />` tags inside cells are converted into real line breaks (`\n`).
- **Autolinks:** Angle-bracket URLs (`<https://example.com>`) are automatically formatted as standard Markdown links.
- **Horizontal Scrolling:** In Preview mode, wide multi-column tables sit inside smooth native `HorizontalScrollView` containers with crisp 1dp hairline borders.

Example table:
```markdown
| Feature | Supported | Notes |
|:---|:---:|:---|
| Native Markwon | Yes | Zero WebViews |
| Room v3 Sync | Yes | Outbox delta logging |
| OLED Dark Mode | Yes | True #000000 canvas |
```

### Inline Images
- Tap the **Image** icon in the formatting bar or type `/Image` to select any picture using Android's native photo picker.
- Images are copied locally to your device's private app storage (`filesDir/images/`) and referenced via `![image](file://...)`.
- **Zero Black Gap Rendering:** Standalone images are rendered as dedicated native `ImageView`s (`adjustViewBounds = true`, `scaleType = FIT_CENTER`), eliminating empty black margins above and below previewed pictures.

---

## 5. Document Export & Native Sharing

Tap the **Share** icon in the editor toolbar to export and share your note in 5 different native formats:

1. **Plain Text (`.txt`):** Strips Markdown formatting tokens and shares clean readable text via Android's native Sharesheet.
2. **Markdown Document (`.md`):** Saves the note as a standard UTF-8 `.md` file, ready for Obsidian, Logseq, GitHub, or desktop text editors.
3. **Rendered Image (`.png`):** Generates a crisp 1080px high-resolution note card with title, metadata, divider, and formatted text.
4. **Share as PDF (Light / Print):**
   - Generates a standard A4 printable PDF document ($595 \times 842\text{ pt}$).
   - Clean white page background (`#FFFFFF`), `#111111` typography, and light gray styled tables.
   - Intelligent line-bottom and row-bottom split detection prevents cutting text or table rows across page breaks.
5. **Share as PDF (OLED Dark):**
   - Full dark-theme PDF with zero-pixel `#000000` page backgrounds.
   - `#F5F5F7` off-white typography, `#8E8E93` metadata, `#1C1C1E` / `#121214` table rows, `#2C2C2E` borders, and `#FFB200` amber link accents.
   - Ideal for reading on OLED phones, tablets, or dark-mode PDF viewers.

---

## 6. Folders & FTS4 Full-Text Search

### Organizing with Folders
- Assign any note to a folder via the overflow menu (`⋮` -> `Move to folder...`).
- Filter notes on the Home Screen by tapping folder tabs (`All`, `Work`, `Personal`, `Ideas`, etc.).
- Folders support custom colors and persistent metadata.

### SQLite FTS4 Full-Text Search
- Type any keyword in the search bar on the Home Screen.
- Search queries execute against a native SQLite FTS4 virtual table with match ranking.
- **Title-Weighted Ranking:** Notes matching the query in their title rank strictly higher than notes matching only in the body.
- Pinned notes and recently updated notes receive subtle priority boosts.

---

## 7. Batch Multi-Select & ZIP Archive Export

To organize or export multiple notes simultaneously:

### Entering Selection Mode
- **Method 1:** Tap the **Select** checkmark button in the Home Screen top toolbar.
- **Method 2:** Long-press any note card on the Home Screen.

### Selection Toolbar Actions
Once selection mode is active, the top bar transforms into a contextual action bar:
- **Selected Counter:** Shows exact count of selected items (e.g. `3 selected`).
- **Select All / Deselect All:** One-tap toggle to select all notes currently visible in the active filter.
- **Pin / Unpin Selected:** Batch pin or unpin all selected notes.
- **Delete Selected:** Move all selected notes to Trash.
- **Share Selected (ZIP Archive):**
  - **No Combined Document Merging:** Each note is rendered individually into its own clean file (`Note_Title.pdf`, `Note_Title_2.pdf` for duplicate titles).
  - **Format Picker:** Choose your desired export format:
    - Markdown (`.md`)
    - Plain Text (`.txt`)
    - PDF Document (Light / Print)
    - PDF Document (OLED Dark)
    - Rendered Image (`.png`)
  - All individually rendered files are compressed into a single archive (`lightnotes_export_<timestamp>.zip`) using Android's native `java.util.zip.ZipOutputStream` (zero third-party dependencies) and shared via the native Sharesheet.

---

## 8. Backups & Encryption (JSON & .enotes)

Open **Settings** from the Home Screen toolbar to access backup and restore tools:

### Portable JSON Backup (`.json`)
- Exports all active notes, folders, and tags into an open standard JSON format.
- Compatible with desktop scripts and automated converters.
- Non-destructive import: Preserves newer modifications if timestamp conflicts occur.

### Encrypted Backup (`.enotes`)
- Encrypts your entire database using **AES-256-GCM** authenticated encryption with PBKDF2 key derivation (65,536 iterations).
- Protects your data at rest when backing up to Google Drive, USB drives, or public cloud storage.
- Restoring requires the exact passphrase entered during export.

### HeyTap / ColorOS Importer
- Import notes exported from ColorOS / Realme UI / OPPO HeyTap Notes app (`.zip` or `.txt` formats).

---

## 9. Biometric App Lock

For private journals, credentials, or sensitive work notes:
- Navigate to **Settings -> Security -> Biometric Lock**.
- Enable **Biometric App Lock**.
- LightNotes Lite utilizes the Android Jetpack `BiometricPrompt` framework to require fingerprint, face unlock, or device PIN before displaying the note library.
- When backgrounded or switched, the app immediately re-secures itself.

---

## 10. BYOK AI Assistant Configuration

LightNotes Lite includes a lightweight, zero-dependency Bring-Your-Own-Key (BYOK) AI client. It is **completely disabled by default** and contains zero third-party AI SDKs.

### Setup Instructions
1. Open **Settings -> AI Assistant (BYOK)**.
2. Enter your **API Key**.
3. Choose or customize your **API Endpoint**:
   - **OpenAI:** `https://api.openai.com/v1/chat/completions` (Model: `gpt-4o-mini`)
   - **Groq (Ultra-Fast):** `https://api.groq.com/openai/v1/chat/completions` (Model: `llama-3.3-70b-versatile`)
   - **Gemini (OpenAI Compatibility):** `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` (Model: `gemini-1.5-flash`)
   - **OpenRouter:** `https://openrouter.ai/api/v1/chat/completions` (Model: `google/gemini-2.0-flash-exp:free`)
   - **Local Ollama:** `http://10.0.2.2:11434/v1/chat/completions` (Android Emulator) or your LAN IP.
4. Set your preferred **Model Name**.

### Using AI in the Editor
When an API key is configured, the overflow menu displays **AI Assistant**:
- **Summarize:** Generates a concise summary bullet list of your note.
- **Fix Grammar & Tone:** Polishes spelling, grammar, and flow while keeping your original meaning intact.
- **Format as Clean Markdown:** Formats messy raw notes with clean headings, bullet lists, and tables.
- **Interactive Review:** Preview AI output with diff options before applying: *Replace Selection*, *Replace Note*, or *Append to Note*.
