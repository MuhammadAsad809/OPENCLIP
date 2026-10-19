# FlipaClip 5.0.2 Native Engine & JNI Architecture

## 1. Native Libraries Overview

FlipaClip delegates its most performance-critical subsystems to native C/C++ shared libraries located under `lib/arm64-v8a/`:

1. **`libfc.so` (26.4 MB)**: Core proprietary engine.
2. **`libffmpeg.so` (11.1 MB)**: FFmpeg multimedia encoder/decoder.
3. **`libSDL2.so` (1.6 MB)**: Simple DirectMedia Layer 2 runtime.
4. **`libpairipcore.so` & `libsigner.so`**: Security integrity & anti-tamper.

---

## 2. Technical Breakdown of `libfc.so`

### 2.1 Graphics Engine: Google Skia + OpenGL ES
Inspection of `libfc.so` string and symbol tables reveals that it is built on top of **Google Skia**:
- References to `SkCanvas`, `SkPaint`, `SkImage`, `SkPath`, `SkBlendMode`, `SkMatrix`, `SkShadowUtils`, and `SkComposeImageFilter`.
- Surface allocation via `tryAllocPixels` and hardware texture backing via `EGL` and `libGLESv2.so`.
- Shaders and blend modes implemented via Skia SkSL and GPU fragment pipelines.

### 2.2 Dynamic JNI Registration (`JNI_OnLoad`)
`libfc.so` exports `JNI_OnLoad` (address offset `0x00e18734`) and dynamically registers native methods for Java/Kotlin classes using `RegisterNatives`. Demangled symbols reveal the exact subsystem registration functions:

```cpp
// Core Configuration & Studio
_Z32register_com_vblast_fclib_ConfigP7_JNIEnv
_Z39register_com_vblast_fclib_fc2_Fc2StudioP7_JNIEnv
_Z44register_com_vblast_fclib_fc2_Fc2SurfaceViewP7_JNIEnv
_Z48register_com_vblast_fclib_canvas_StageCanvasViewP7_JNIEnv

// Drawing & Canvas Tools
_Z47register_com_vblast_fclib_canvas_tools_DrawToolP7_JNIEnv
_Z48register_com_vblast_fclib_canvas_tools_LassoToolP7_JNIEnv
_Z52register_com_vblast_fclib_canvas_tools_FloodFillToolP7_JNIEnv
_Z53register_com_vblast_fclib_canvas_tools_EyeDropperToolP7_JNIEnv
_Z47register_com_vblast_fclib_canvas_tools_TextToolP7_JNIEnv
_Z48register_com_vblast_fclib_canvas_tools_ImageToolP7_JNIEnv
_Z52register_com_vblast_fclib_canvas_BrushArtworkBuilderP7_JNIEnv

// Animation, Layers & Playback
_Z46register_com_vblast_fclib_layers_LayersManagerP7_JNIEnv
_Z42register_com_vblast_fclib_io_FramesManagerP7_JNIEnv
_Z46register_com_vblast_fclib_fc2_player_Fc2PlayerP7_JNIEnv
_Z54register_com_vblast_fclib_fc2_project_Fc2ProjectEditorP7_JNIEnv

// Project I/O & Export
_Z42register_com_vblast_fclib_io_ProjectExportP7_JNIEnv
_Z42register_com_vblast_fclib_io_ProjectImportP7_JNIEnv
_Z40register_com_vblast_fclib_io_FileHandlerP7_JNIEnv
_Z40register_com_vblast_fclib_io_MediaImportP7_JNIEnv

// Audio Engine
_Z36register_com_vblast_fclib_audio_ClipP7_JNIEnv
_Z42register_com_vblast_fclib_audio_MultiTrackP7_JNIEnv
_Z46register_com_vblast_fclib_audio_WaveformReaderP7_JNIEnv
_Z47register_com_vblast_fclib_audio_WaveformBuilderP7_JNIEnv
_Z47register_com_vblast_fclib_audio_AudioTranscoderP7_JNIEnv
```

---

## 3. The `SDL2` and Audio Subsystem

`libSDL2.so` is loaded in `com.vblast.fclib.Config.init()`:
```java
System.loadLibrary("ffmpeg");
System.loadLibrary("SDL2");
System.loadLibrary("fc");
SDL.setupJNI();
SDL.initialize();
SDL.setContext(context);
```
Responsibilities of SDL2 in FlipaClip:
1. Low-level display surface lifecycle and event queues (`ISurfaceView.queueEvent`).
2. Audio synchronization via OpenSLES (`libOpenSLES.so`).
3. Cross-platform timing ticks for animation preview and audio playback synchronization.

---

## 4. Video & Media Pipeline (`libffmpeg.so`)

FlipaClip bundles a monolithic build of FFmpeg (`libffmpeg.so`, 11.1 MB) linked to `libfc.so`.
`ProjectExport.Builder` configures the native encoder with parameters such as:
- Output format (`OUTPUT_FORMAT_MP4 = 0`, `OUTPUT_FORMAT_GIF = 1`, `OUTPUT_FORMAT_PNG_SEQ = 2`)
- Video codec presets: `videoEncodePreset = "medium"`, `videoEncodeTune = "animation"`
- Bitrate and resolution scaling based on project canvas size.

---

## 5. Architectural Strategy for Our Clean-Room Application

Instead of shipping heavy, proprietary C++ binaries or external GPL-encumbered libraries, our application will implement a **pure Kotlin + Android Jetpack + Android Native MediaCodec** architecture:

1. **Graphics & Drawing**: High-performance hardware-accelerated Android 2D Canvas with custom stroke rasterizer and path smoothing (`quadTo` Bézier interpolation with velocity-based width falloff).
2. **Layer Compositing**: Multi-bitmap offscreen surface compositing with hardware PorterDuff blend modes.
3. **Playback Clock**: Android `Choreographer` frame callbacks tied to precise nanosecond timing for jitter-free 1–30 FPS playback.
4. **MP4 Video Encoding**: Native Android `MediaCodec` (H.264 / AVC hardware encoder) + `MediaMuxer`. This achieves zero-dependency, ultra-fast, hardware-accelerated video rendering directly supported on 100% of Android devices.
5. **GIF Encoding**: Native Java/Kotlin LZW Animated GIF encoder for crisp, watermark-free GIF animation exports.
