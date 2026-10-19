# Project Storage & Serialization Format

## 1. Reverse-Engineered FlipaClip Internal Storage

FlipaClip stores project data in the app's internal storage (`/data/user/0/com.vblast.flipaclip/files/projects/<projectId>/`):

```
<project_directory>/
├── project.json              # Top-level project configuration
├── project.db                # SQLite database (tracks, keyframes, effects, audio metadata)
├── frames/                   # Layer artwork files
│   ├── f_0001_l_01.png       # Frame 1, Layer 1 (PNG or proprietary .fci)
│   ├── f_0001_l_02.png
│   └── ...
├── audio/                    # Audio recordings and imported sound assets
│   ├── track_1.m4a
│   └── ...
└── thumbs/                   # Pre-rendered frame thumbnails for timeline speed
    ├── thumb_0001.png
    └── ...
```

### 1.1 The `project.json` Structure
```json
{
  "version": 4,
  "projectId": 1729302194821,
  "title": "Bouncing Ball",
  "canvasWidth": 1920,
  "canvasHeight": 1080,
  "fps": 12,
  "frameCount": 24,
  "backgroundColor": -1,
  "backgroundAssetId": null,
  "hasAudio": false,
  "createdTimestamp": 1729302194821,
  "modifiedTimestamp": 1729302250100
}
```

### 1.2 The `project.db` Schema
SQLite tables confirmed in `libfc.so`:
- `schema_meta(module TEXT PRIMARY KEY, version INTEGER NOT NULL)`
- `project(id TEXT PRIMARY KEY, name TEXT, canvas_width INT, canvas_height INT, fps INT, has_audio INT, background_asset_id TEXT)`
- `track(id TEXT PRIMARY KEY, multi_track_id TEXT, ordered_position INT, thumbnail_asset_id TEXT, marked_for_delete INT)`
- `key_frame(id TEXT PRIMARY KEY, effect_id TEXT, frame_offset INT, track_time_duration REAL, marked_for_delete INT)`
- `asset(id TEXT PRIMARY KEY, refcount INT)`

---

## 2. Our Application's Clean-Room Project Format (`.fcanim` / Directory)

To ensure **100% offline-first reliability, human-inspectability, zero corruption risk, and seamless backup**, our application uses a clean JSON + standard PNG directory structure.

### 2.1 Project Directory Structure (`app_projects/<project_id>/`)
```
projects/<project_id>/
├── project.json              # Main project descriptor
├── thumbnail.png             # Project gallery card cover thumbnail
├── frames/                   # Individual frame layer raster files
│   ├── frame_000/
│   │   ├── layer_000.png     # Standard 32-bit ARGB PNG
│   │   ├── layer_001.png
│   │   └── thumb.png         # Fast timeline preview cache
│   ├── frame_001/
│   │   ├── layer_000.png
│   │   └── thumb.png
│   └── ...
└── audio/                    # Audio files for project soundtrack
    └── audio_track_0.wav
```

### 2.2 Schema of `project.json`
```json
{
  "formatVersion": 1,
  "id": "proj_a8f9c1e2",
  "name": "My First Animation",
  "canvasWidth": 1280,
  "canvasHeight": 720,
  "fps": 12,
  "frameCount": 12,
  "backgroundColor": "#FFFFFF",
  "backgroundType": "SOLID",
  "layers": [
    {
      "id": 0,
      "name": "Background",
      "visible": true,
      "locked": false,
      "opacity": 1.0,
      "blendMode": "NORMAL"
    },
    {
      "id": 1,
      "name": "Line Art",
      "visible": true,
      "locked": false,
      "opacity": 1.0,
      "blendMode": "NORMAL"
    }
  ],
  "audioTracks": [],
  "createdAt": 1729302194821,
  "updatedAt": 1729302250100
}
```

### 2.3 Atomic Autosave & Recovery
- **Write-ahead buffering**: Modifications to active drawing layer are written to a `.tmp` file and atomically renamed (`File.renameTo`) to prevent corruption during app crashes or interruptions.
- **LRU Frame Cache**: Active and nearby frames are cached in memory (ARGB_8888 Bitmaps); inactive frames are flushed to disk PNGs to maintain a low RAM footprint on low-end Android devices.
- **Exportable Backup Archive (`.zip` / `.fcanim`)**: Entire project directory can be zipped into a single file with one tap for easy sharing, backup, or transferring across devices.
