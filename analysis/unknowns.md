# Unknowns, Proprietary Dependencies & Architectural Boundaries

## 1. Proprietary Components & Boundaries Identified

During reverse engineering of FlipaClip 5.0.2 (`com.vblast.flipaclip`), several closed-source proprietary mechanisms were identified:

### 1.1 Proprietary `.fci` Image Compression Format
- **Observation**: Frame and brush stamp files can be stored with extension `.fci` starting with magic bytes `\x89FCI\r\n\x1a\n`.
- **Limitation**: The exact proprietary compression dictionary / entropy algorithm inside `libfc.so` is not documented publicly and is closed-source.
- **Clean-Room Resolution**: Our application uses standard 32-bit ARGB PNG format (`Bitmap.CompressFormat.PNG`) and WebP (`Bitmap.CompressFormat.WEBP`). PNG provides lossless quality, universal platform interoperability, fast decoding via native `BitmapFactory`, and zero proprietary risk.

### 1.2 Anti-Tamper & Security Layers (`libpairipcore.so` & `libsigner.so`)
- **Observation**: FlipaClip incorporates Google Play Pairip protection and signature verification in native binaries to enforce license checking and prevent APK modification.
- **Clean-Room Resolution**: We are building a completely independent, original application from scratch with our own package name and original codebase. We do not modify or circumvent FlipaClip's licensing. Our application has zero licensing checks, zero anti-tamper overhead, and is 100% free by design.

### 1.3 Proprietary `.fcb` Brush Format Math
- **Observation**: Bundled brush packages (`assets/brushes/*.fcb`) contain complex multi-curve modifier maps (`blurModeAmount`, `mods`, `mk`, `mp`).
- **Clean-Room Resolution**: We implement standard digital art brush dynamics: quadratic Bézier stroke smoothing, pressure-to-width curves, velocity-to-opacity curves, and texture mask shaders.

### 1.4 Native JNI Registration Obfuscation
- **Observation**: Symbol names in `libfc.so` are partially stripped, and JNI methods are bound dynamically at runtime via `JNI_OnLoad`.
- **Clean-Room Resolution**: All native JNI interactions from the original app were mapped via string table references (`_Z.*register_com_vblast`) and decompiled JADX wrappers (`com.vblast.fclib.*`), providing a 100% complete view of the functional interface without needing to decompile stripped C++ assembly.

---

## 2. Dependency Risk Assessment

| Potential Dependency | License / Risk | Clean-Room Decision |
| :--- | :--- | :--- |
| **FFmpeg (`libffmpeg.so`)** | LGPL / GPL v3 (Large binary size ~11MB per ABI, complex licensing requirements) | **Replaced with Android `MediaCodec` + `MediaMuxer`**. Built directly into the Android OS, 0MB app size overhead, hardware-accelerated, 100% free. |
| **Google Skia C++** | Apache 2.0 (Massive C++ build requirement, 25MB+ per ABI) | **Replaced with Android Hardware-accelerated `Canvas` & `Paint`**. Android's 2D canvas is itself built on Google Skia inside Android userspace, delivering identical native rendering speed without bundling a redundant 25MB library. |
| **SDL2 (`libSDL2.so`)** | zlib license (Requires native NDK build and C++ lifecycle glue) | **Replaced with Native Android `View` and `Choreographer`**. Android's touch pipeline and `Choreographer` frame callbacks provide lower input latency and smoother lifecycle integration. |
