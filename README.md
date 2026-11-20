Grammar in Use — Native Android Reader

A lightweight, native Kotlin Android application for studying grammar lessons with synchronized section audio, interactive quizzes, and offline-first pack caching.

Designed for minimal APK size (~3–5 MB per ABI), zero external media bloat in the repository, and automated multi-APK release builds (arm64-v8a and armeabi-v7a) via GitHub Actions.

Features

Vertical Full-Height Reader: Custom ZoomablePageView with pinch-to-zoom, double-tap zoom, swipe navigation, warm eye-comfort mode, and one-tap fullscreen mode.

Direct Section Audio Playback: Tap directly on lesson sections (A, B, C, ...) or use quick section pills with native MediaPlayer controls (seek bar, -5s / +5s, variable playback speed 0.75x–1.5x, repeat, optional auto-next, and A–B shadowing loop).

Offline Pack Architecture: Downloads compact 30-unit ZIP bundles on demand via streaming ZipInputStream extraction and caches them locally for permanent offline access.

Progress Tracking & Study Tools: Remembers the last viewed page, tracks separate per-page and per-unit checklists, daily study streak, bookmarks, personal unit notes, and quick Answer Key popups.

Interactive Quizzes & Back-Matter Resources: Unit-linked multiple-choice (MCQ) and smart-graded written exercises, plus full access to Appendices, Additional Exercises, and Study Guide.

Tech Stack

Language: Kotlin (AndroidX + ViewBinding, zero heavy UI frameworks)

Audio & Storage: Native Android MediaPlayer, SharedPreferences, HttpURLConnection, and ZipInputStream

Build & CI: Gradle Kotlin DSL (AGP 8.7 / Kotlin 2.0), R8 code & resource shrinking, ABI splits (arm64-v8a & armeabi-v7a), and GitHub Actions

Asset Pipeline: Python 3 (pypdfium2, pdfplumber, Pillow) for user-supplied local book/audio preprocessing

Repository Structure

grammar-in-use/
├── .github/workflows/build-myket.yml   # Automated signed multi-APK CI workflow
├── app/
│   ├── build.gradle.kts                # ABI splits, R8 minification, release signing
│   └── src/main/
│       ├── assets/                     # Metadata JSON (book_data.json, quiz_data.json)
│       ├── java/ir/hiwa/grammarinuse/  # Native Kotlin source
│       └── res/                        # XML layouts, themes, drawables, mipmaps
├── scripts/
│   ├── prepare_book_assets.py          # Local preprocessing script (packs & metadata)
│   └── requirements.txt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties


Building Signed Release APKs (GitHub Actions)

No local Android Studio installation is required.

Generate an Android release keystore locally:

keytool -genkey -v -keystore release.keystore -alias grammar_key -keyalg RSA -keysize 2048 -validity 10000


Add the following secrets under Settings → Secrets and variables → Actions in your GitHub repository:

KEYSTORE_BASE64: Base64-encoded contents of release.keystore

KEYSTORE_PASSWORD: Keystore password

KEY_ALIAS: grammar_key

KEY_PASSWORD: Key password

Push to main (or trigger Build Myket Release APKs manually under the Actions tab).

Download myket-release-apks from the workflow artifacts containing:

app-arm64-v8a-release.apk (versionCode: 2001)

app-armeabi-v7a-release.apk (versionCode: 1001)

Local Asset Preparation

This repository contains source code only and does not host any copyrighted book PDFs, page images, or audio recordings. To prepare your own local study packs:

pip install -r scripts/requirements.txt
python scripts/prepare_book_assets.py --pdf "/path/to/book.pdf" --audio "/path/to/Audio CD"


Upload the generated arvan_packs/packs/*.zip files to your S3-compatible object storage bucket and set ArvanConfig.BASE_URL in app/src/main/java/ir/hiwa/grammarinuse/Models.kt.

License

This project's source code is released under the MIT License. Third-party study materials remain the property of their respective copyright holders.
