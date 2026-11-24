# LightNotes Lite — User Guide

Welcome to **LightNotes Lite**, a fast, distraction-free notebook built for everyday writing, checklists, and private note-taking on your phone. Your notes save automatically on your device with no account required.

---

## 1. Creating & Editing Notes
- **Start Writing:** Tap the amber **`+`** button on the home screen to create a new note. Your title and text save automatically as you type.
- **Undo & Redo:** Use the pinned **Undo (`↶`)** and **Redo (`↷`)** buttons on the left side of the bottom bar to step backward or forward anytime.
- **Formatting Bar:** Scroll the bottom toolbar to add **Bold**, *Italic*, Headings, Bullet Lists, Numbered Lists, Checklists, Links, Quotes, Code Blocks, or Photos.
- **Quick `/` Menu:** Type **`/`** anywhere on a new line (or after a space) to open a quick-insert menu for headings, to-do checkboxes, lists, photos, quotes, and more.

## 2. Preview & Checklists
- **Switch Edit / Preview:** Tap **Preview** (eye icon) at the top of the editor to view formatted headings, tables, links, and photos cleanly. Tap **Edit** to keep writing.
- **Interactive Checklists:** Add to-do items using the checklist button (or `- [ ] `). In **Preview** mode, tap any checkbox directly to check or uncheck it—your note updates and saves right away. Note cards on the home screen also show your checklist progress (such as `2/5`).
- **Add Gallery Photos:** Tap the **Image** button in the formatting bar (or type `/Image`) to pick a photo from your phone's gallery and place it in your note.

## 3. Organizing & Finding Notes
- **Folders:** Tap the tabs across the top of the home screen (`All Notes`, `Favorites`, `Archive`, `Trash`, or your custom folders) to filter your notes. To move a note, open it, tap **`⋮`**, and choose **Move to folder...**.
- **Pin & Favorite:** Pin important notes so they always stay at the top of your list, or mark them as favorites. Toggle either from the editor's **`⋮`** menu (or tap the pin icon next to the date).
- **Home Screen Shortcuts:** Open a note and tap **`⋮` -> Add to Home screen** to put a one-tap shortcut straight onto your phone's home screen.
- **Archive & Trash:** Archive finished notes to keep your main list tidy without deleting them. Deleted notes move to **Trash**, where you can restore them or delete them permanently.
- **Instant Search:** Type in the home screen search bar to find notes immediately (title matches appear first), or tap **`⋮` -> Find in note** inside a note.
- **List or Grid View:** Tap the grid/list button in the top-right bar on the home screen to switch layouts.

## 4. Batch Actions
Organize or share multiple notes at once from the home screen:
1. **Enter Selection Mode:** Long-press any note card, or tap the **checkmark** icon in the top-right bar.
2. **Pick Notes:** Tap cards to select or deselect them, or tap the **Select All** checkmark in the top bar.
3. **Choose an Action:**
   - **Pin / Unpin** (or **Restore** when viewing Trash) all selected notes in one tap.
   - **Delete** selected notes to move them to Trash (or permanently delete them from Trash).
   - **Share as ZIP:** Tap the **Share** icon to save or send all selected notes together in one `.zip` file—each note stays as its own separate file inside the ZIP (choose Markdown, Plain Text, PDF Light, PDF OLED Dark, or PNG Image).

## 5. Sharing & Exporting
Open any note and tap the **Share** icon at the top to export or send it in five formats:
- **Plain Text:** Shares a clean, unformatted text copy.
- **Markdown (`.md`):** Exports a standard Markdown file ready for other note apps and editors.
- **Image (`.png`):** Creates a crisp, high-resolution image of your formatted note.
- **PDF (Light / Print):** Generates a clean white-page A4 PDF ideal for printing or sharing, complete with formatted tables and embedded photos.
- **PDF (OLED Dark):** Generates a pitch-black A4 PDF designed for comfortable dark-mode reading on phone and tablet screens.

## 6. Backups & Privacy
Open **Settings** (gear icon on the home screen) to customize themes, lock the app, and manage backups:
- **Theme:** Switch between **System Default**, **Light**, and **OLED Dark**.
- **Fingerprint / Face Lock:** Turn on **Biometric App Lock** under Security to require your fingerprint, face unlock, or device PIN when opening the app.
- **JSON Backup:** Tap **Export JSON Backup** to save a portable copy of your notes and folders, and **Restore JSON Backup** to merge notes back without overwriting newer edits.
- **Password-Protected Backup (`.enotes`):** Tap **Export Encrypted Backup (.enotes)** to lock your backup with a passphrase before saving it to cloud storage or another device. Use **Restore Encrypted Backup (.enotes)** with the same passphrase to restore it.
- **Import Realme / HeyTap Notes:** Moving from Realme or OPPO Notes? Tap **Import Realme / HeyTap Backup** and select your exported backup file to bring your notes over.

## 7. Optional AI Assistant
The AI helper is **off by default** and never sends anything unless you add your own key in **Settings -> AI Assistant (Optional BYOK)**:
1. Tap **API Key** and paste a key from a provider of your choice (many offer free tiers):
   - **Groq:** Endpoint `https://api.groq.com/openai/v1/chat/completions` (e.g., `llama-3.3-70b-versatile`)
   - **Gemini:** Endpoint `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` (e.g., `gemini-2.5-flash`)
   - **OpenRouter:** Endpoint `https://openrouter.ai/api/v1/chat/completions`
   - **Ollama (Local):** Point **API Endpoint URL** to your local server (e.g., `http://localhost:11434/v1/chat/completions`)
2. Set the **API Endpoint URL** and **Model Name** to match your provider.
3. Open any note and tap **`⋮` -> AI Assistant** (or type `/AI`) to use **Summarize Note**, **Fix Grammar & Tone**, or **Format as Clean Markdown**. You can review the result first and choose to replace a selection, replace the whole note, or append it.
