# English Grammar In Use (Android · Native Kotlin)

A lightweight (~3–5 MB), native Kotlin Android companion app for **English Grammar in Use (Fifth Edition)** by Raymond Murphy. Designed for crisp offline PDF reading, original Cambridge Audio CD playback, freehand annotations, interactive quizzes, and automated multi-APK release builds via GitHub Actions.

---

## Features (v2.0.0)

- **Native High-DPI PDF Reader**: Renders lossless sliced PDF packs via Android's built-in `PdfRenderer` with pinch-to-zoom, double-tap zoom, and dynamic high-DPI re-rendering when zoomed in.
- **Original Audio Playback**: Plays official Cambridge Audio CD tracks (`A`–`F`) by tapping directly on lesson sections or bottom pills. Includes seek bar, `-5s / +5s`, speed control (`0.75x–1.5x`), repeat, optional Auto-Next, and `A–B` shadowing loop. Automatically pauses when the screen turns off.
- **Paint-Style Pen & Movable Notes**: Draw, highlight, erase, pick colors & stroke widths, undo/clear, and place movable/editable sticky text notes anywhere on any page (1-finger draw/note, 2-finger zoom & pan) — automatically saved per page.
- **Unit Quizzes & Exact Answer Key**:
  - **Multiple Choice** (Study Guide questions with restored `________` blanks).
  - **Fill in the Blank** (extracted exercise prompts with live blank preview and flexible contraction grading, e.g., `I'll` = `I will`).
  - **Exact Answer Key Viewer**: Zoomable high-DPI crops directly from the book's *Key to Exercises* preserving exact bold, italic, and indentation layout.
- **Bilingual UI (English / Persian)**: Defaults to English (LTR) with instant in-app switching to Persian (RTL) in **Settings**.
- **Polished UX**: Minimalist animated Cambridge splash screen, first-launch 4-step quick tour, Android 15 Edge-to-Edge safe insets, one-tap Fullscreen & Eye-Comfort modes, per-page/per-unit vector checklists, Download Manager, and back-press exit confirmation.

---

## Project Structure

```text
grammar-in-use/
├── .github/workflows/build-myket.yml   # Automated signed multi-APK CI workflow
├── app/
│   ├── build.gradle.kts                # ABI splits (armeabi-v7a: 1002, arm64-v8a: 2002)
│   └── src/main/
│       ├── assets/                     # Generated book_data.json & quiz_data.json
│       ├── java/ir/hiwa/grammarinuse/  # Native Kotlin source files
│       └── res/                        # XML layouts, vector drawables, launcher icons
└── scripts/
    ├── prepare_book_assets.py          # Slices PDF packs, crops Answer Keys, builds JSONs
    └── requirements.txt
```

---

## Preparing Book Assets

> **Note:** Copyrighted PDF pages and Audio CD MP3s are not committed to this repository.

1. Install Python dependencies:
   ```bash
   pip install -r scripts/requirements.txt
   ```
2. Run the asset builder against your clean PDF and extracted Audio CD folder:
   ```bash
   python scripts/prepare_book_assets.py --pdf "path/to/book.pdf" --audio "path/to/Audio CD"
   ```
3. Upload the 6 generated ZIP packs (`arvan_packs/packs/pack_1.zip` .. `pack_5.zip`, `pack_resources.zip`) to the `packs/` folder of your public object storage bucket.
4. Commit and push the updated `app/src/main/assets/book_data.json` and `app/src/main/assets/quiz_data.json`.

---

## Building Signed Release APKs (GitHub Actions)

Configure these 4 repository secrets under **Settings → Secrets and variables → Actions**:
- `KEYSTORE_BASE64` — Base64-encoded `.keystore` file
- `KEYSTORE_PASSWORD` — Keystore password
- `KEY_ALIAS` — Signing key alias (e.g., `grammar_key`)
- `KEY_PASSWORD` — Key password

Every push to `main` (or manual workflow trigger) builds both signed APKs (`app-arm64-v8a-release.apk` and `app-armeabi-v7a-release.apk`) ready for release.
