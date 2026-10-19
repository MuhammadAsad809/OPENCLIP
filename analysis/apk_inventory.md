# FlipaClip 5.0.2 APK Inventory & Static Analysis Report

**Analysis Target:** `/storage/emulated/0/Download/FlipaClip_5.0.2_APKPure.apk`  
**Package:** `com.vblast.flipaclip`  
**Version:** `5.0.2` (VersionCode `950`)  
**Target SDK:** `36` | **Min SDK:** `24` (Android 7.0+) | **Compile SDK:** `36`  
**Architecture:** `arm64-v8a`  
**Date of Analysis:** October 2026  

---

## 1. Package Metadata & Multi-DEX Architecture

FlipaClip 5.0.2 is structured as a heavy Multi-DEX application comprising 14 `classes.dex` files (~90 MB total uncompressed DEX payload).

### DEX Distribution Summary
| DEX File | Size | `com.vblast` Classes | Key Contents |
| :--- | :--- | :--- | :--- |
| `classes.dex` | 6.7 MB | 22 | Entry points, AndroidX core, `feature_stage` shims |
| `classes5.dex` | 9.1 MB | 2,972 | **Core Animation & Engine**: `fclib`, `layers`, `canvas`, `tools`, `projects`, `buildmovie` |
| `classes6.dex` | 9.2 MB | 2,016 | **Domain & UI Logic**: ViewModels, Compose screens, repositories |
| `classes14.dex`| 7.8 MB | 69 | `fclib` native glue, stage canvas view bridges |
| `classes2..4, 7..13.dex` | ~57 MB | 0 | Third-party SDKs (AppLovin, MoEngage, Facebook, IronSource, ByteDance/TikTok, Firebase, Google Ads, UnityAds, Ogury) |

Total `com.vblast.*` classes: **5,079 classes**.

---

## 2. Permissions Declared

```xml
<!-- Core Capabilities -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Storage (Scoped Storage with Legacy Fallbacks) -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />

<!-- Billing & Proprietary Licensing -->
<uses-permission android:name="com.android.vending.BILLING" />
<uses-permission android:name="com.android.vending.CHECK_LICENSE" />
<uses-permission android:name="com.vblast.flipaclip.UNLOCKER" />
```

---

## 3. Core Activities & Application Entry Points

Main Entry: `com.vblast.feature_startup.presentation.StartupActivity`

### Primary User-Facing Activities
1. **Startup & Home:**
   - `com.vblast.feature_startup.presentation.StartupActivity` (Splash & initialization)
   - `com.vblast.feature_home.presentation.HomeActivity` (Project gallery, movies, challenges)
2. **Main Studio / Editor:**
   - `com.vblast.flipaclip.ui.stage.presentation.StageActivity`
   - `com.vblast.flipaclip.ui.stage.presentation.StageActivityV2` (Modern editor stage hosting `StageCanvasView`)
   - `com.vblast.feature_studio.presentation.activity.StudioActivity`
3. **Project & Export Workflows:**
   - `com.vblast.feature_projects.presentation.editproject.EditProjectActivity` (Project settings)
   - `com.vblast.feature_projects.presentation.buildmovie.BuildMovieActivity` (Movie export UI)
   - `com.vblast.feature_projects.presentation.imageeditor.ActivityImageEditor`
4. **Tool & Settings Dialogs:**
   - `com.vblast.feature_stage.presentation.OnionSettingsActivity`
   - `com.vblast.feature_stage.presentation.GridSettingsActivity`
   - `com.vblast.feature_stage.presentation.importvideo.ImportVideoActivity`
   - `com.vblast.feature_stage.presentation.importaudio.ImportAudioActivity`
   - `com.vblast.feature_settings.presentation.SettingsActivity`
5. **Services:**
   - `com.vblast.feature_projects.presentation.buildmovie.service.BuildMovieService` (Foreground background video render)

---

## 4. Native Libraries Inventory (`lib/arm64-v8a/`)

| Library | Size | Purpose & Technical Role |
| :--- | :--- | :--- |
| **`libfc.so`** | **26.4 MB** | **Core FlipaClip Engine**: Built on Google Skia + OpenGL ES. Handles stroke rasterization, brush physics, Skia canvas compositing, layer blending, project SQLite DB, and undo/redo state management. |
| **`libffmpeg.so`** | **11.1 MB** | Media encoding/decoding engine (H.264/MP4, audio transcoding, waveform analysis). |
| **`libSDL2.so`** | **1.6 MB** | SDL2 runtime providing low-level display surface, touch input dispatch, and OpenSLES audio. |
| **`libpairipcore.so`** | 625 KB | Google Play Integrity / anti-tamper security layer. |
| **`libsigner.so`** | 983 KB | APK signature validation and checksum integrity checks. |
| **`libcrashlytics*.so`** | ~1.3 MB | Firebase crash reporting. |
| **Ad / Analytics SOs** | ~2.5 MB | AppLovin, ByteDance/PGL, APM Insight native crash monitors. |

---

## 5. Assets & Presets Inventory

1. **Background Presets (`assets/bg_presets/`):**
   - **Paper textures**: `black_paper_pattern.png`, `chalk_paper.png`, `cream_paper_pattern.png`, `exclusive_paper_pattern.png`, `lined_paper_pattern.png`, `notebook_paper_pattern.png`, `old_paper.jpg`, `rice_paper_pattern.png`, `wrinkle_paper.jpg`
   - **Scene illustrations**: `back_garden.jpg`, `desert.jpg`, `living_room.jpg`, `street_corner.jpg`
   - **Patterns**: `asanoha`, `sayagata`, `seigaiha`, `swirl`, `tileable_wood`
2. **Brush Packages (`assets/brushes/*.fcb`):**
   - `.fcb` format is a ZIP archive containing `meta` (JSON), `data` (brush dynamics JSON), and `shape/*.fci` (raster stamp bitmaps).
   - Bundled brushes: Airbrush, Airbrush 2, Base pen, Calligraphy, Candy cane, Choppy pen, Crayon, Dash, Dip pen.
