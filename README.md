# OpenClip: 100% Free 2D Frame-by-Frame Animation Studio for Android

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" height="120" alt="OpenClip Logo" />
</p>

**OpenClip** is a complete, independent, 100% free and open-source Android 2D frame-by-frame animation studio inspired by FlipaClip. It is built natively with **Kotlin**, **Jetpack Compose**, and a custom hardware-accelerated **2D Drawing & Compositing Canvas Engine**.

---

## 🌟 Core Product Policy: 100% Free Forever

- 🚫 **No Subscriptions**
- 🚫 **No Premium Tiers or In-App Purchases**
- 🚫 **No Locked Tools or Artificial Limits** (Unlimited Layers, Unlimited Frames)
- 🚫 **No Advertisements**
- 🚫 **No Mandatory Accounts or Cloud Logins**
- 🚫 **Zero Export Watermarks**
- 🚫 **100% Offline-First**: Works entirely on your Android device without requiring internet access.

---

## 🎨 Feature Matrix & Capabilities

### 1. Interactive Drawing & Canvas Engine
- **Touch & Stylus Support**: Sub-frame Bézier curve smoothing (`quadTo`) with velocity and pressure-sensitive width dynamics.
- **Pan & Zoom Workspace**: Two-finger pinch-to-zoom (0.2x to 8x) and smooth panning with instant viewport reset.
- **Brush Varieties**:
  - **Pen**: Crisp, solid line with smooth rounded stroke joints.
  - **Pencil**: Textured graphite sketch stroke with natural grain.
  - **Marker**: Semi-transparent highlighter wash.
  - **Airbrush**: Soft radial spray stroke for smooth shading.
- **Eraser**: Configurable size (5px to 200px) and feathering via hardware `PorterDuff.Mode.CLEAR`.
- **Ruler & Geometric Guides**:
  - Straight Line
  - Circle / Oval
  - Rectangle / Square
- **Scanline Flood Fill**: 4-way scanline queue flood fill with configurable color distance tolerance.
- **Color Picker**: Color swatches, HSV color wheel, alpha opacity, and real-time canvas Eyedropper tool.
- **Freehand Lasso Tool**: Path selection, bounding box detection, matrix translation, pinch-to-scale, and horizontal/vertical flip.
- **Undo / Redo History**: Memory-conscious snapshot history stack with instant state restoration.

### 2. Multi-Layer Engine
- **Unlimited Layers**: No artificial 3-layer caps.
- **Visibility Toggle**: Eye icon to hide/show individual layers.
- **Opacity Control**: 0% to 100% per-layer alpha blending.
- **Independent Drawing**: Draw on active layer while viewing composited upper and lower layers in real time.

### 3. Timeline & Animation Controls
- **Horizontal Frame Carousel**: Frame thumbnails with active frame highlight.
- **Frame Operations**: Add blank frame (`+`), Clone/Duplicate frame, Delete frame.
- **Onion Skinning**:
  - Configurable look-back frames (1 to 5 frames) and look-ahead frames (1 to 5 frames).
  - Opacity gradient falloff.
  - **Color-Coded Mode**: Red tint for past frames, Green tint for future frames.
- **Real-Time Playback**:
  - Configurable FPS (1 to 30 FPS).
  - Loop toggle (Endless repeat vs. Play once).
  - Real-time frame counter (`Current / Total`).

### 4. Zero-Watermark Export Engine
- **MP4 Video (H.264)**: Native Android `MediaCodec` + `MediaMuxer` hardware acceleration (blazing fast, no heavy GPL/FFmpeg bloat).
- **Animated GIF**: Built-in LZW Animated GIF encoder with looping and frame delay.
- **PNG Image Sequence**: All project frames exported as high-resolution PNGs bundled in a `.zip` archive.
- **Single Frame PNG**: Instant export of the current canvas frame to device gallery.
- **MediaStore Integration**: Exports are automatically saved to `Movies/OpenClip` and `Pictures/OpenClip`.

---

## 🏗️ Technical Architecture

```
com.openclip.studio/
├── core/
│   ├── model/                  # Data classes: Project, Frame, Layer, Tool, BrushConfig
│   ├── engine/                 # Custom interactive drawing and rendering engine
│   │   ├── DrawingCanvasView   # Custom Android View for low-latency touch & stylus drawing
│   │   ├── FloodFillEngine     # 4-way scanline queue flood fill algorithm
│   │   ├── LassoEngine         # Freehand path selection and matrix transformation
│   │   └── HistoryManager      # Command-based undo/redo snapshot ledger
│   └── export/                 # High-performance export pipelines
│       ├── Mp4Encoder          # Hardware-accelerated MediaCodec H.264/AVC pipeline
│       ├── GifEncoder          # Pure Kotlin LZW Animated GIF encoder
│       └── ExportPipeline      # Orchestrates format rendering & MediaStore publishing
├── data/
│   └── ProjectRepository       # Local project persistence (JSON metadata + PNG frame layers)
└── ui/
    ├── gallery/                # Home screen, project cards, and New Project dialog
    ├── editor/                 # Animation studio editor, top bar, tool rail, timeline, dialogs
    └── theme/                  # Dark studio palette (Orange accent, charcoal dark surfaces)
```

---

## 🚀 Building the APK via GitHub Actions

This repository includes an automated GitHub Actions CI/CD workflow (`.github/workflows/build-apk.yml`).

Every push to `main` automatically:
1. Sets up JDK 21 on `ubuntu-latest`.
2. Compiles the project with `./gradlew assembleDebug assembleRelease`.
3. Publishes the ready-to-install Android APK as a downloadable GitHub Actions artifact (`openclip-debug-apk`).

---

## 📄 License & Attribution

OpenClip is released under the **MIT License**.  
All trademarks, logos, and brand names are the property of their respective owners. OpenClip is an independent, clean-room implementation.
