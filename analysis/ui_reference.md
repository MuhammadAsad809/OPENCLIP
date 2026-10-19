# FlipaClip 5.0.2 UI/UX Reference & Interaction Patterns

## 1. Studio / Stage Layout Architecture

The main drawing and animation workspace in FlipaClip is defined by `res/layout/activity_stage_v2.xml`.

```
+-------------------------------------------------------------------+
| [<- Back]  [Project Name]   [Ruler]  [Undo]  [Redo]  [Zoom/Fit]  [:] |  <-- Top Toolbar
+----+--------------------------------------------------------------+
| [B]|                                                              |
| [E]|                                                              |
| [L]|                      DRAWING CANVAS                          |
| [F]|                  (StageCanvasView)                           |
| [C]|                                                              |
| [T]|                                                              |
+----+--------------------------------------------------------------+
| [Frame 1] [Frame 2] [Frame 3] [Frame 4*] [+] | [Play] [Layers (3)]|  <-- Bottom Timeline
+-------------------------------------------------------------------+
```

### 1.1 Top Toolbar Components
- **Back Button**: Prompts autosave and returns to Project Gallery.
- **Project Title**: Shows active project name; tapping opens quick rename or project settings.
- **Ruler Tool**: Toggles geometric guides (Straight line, Circle/Oval, Square, Mirror).
- **Undo / Redo Buttons**: Enabled/disabled based on history stack state.
- **Zoom / Pan Reset**: Resets canvas zoom and translation to fit viewport.
- **Overflow Menu (`...`)**:
  - Project Settings (Canvas size, background, FPS)
  - Frames Viewer (Grid overview of all animation frames)
  - Onion Skin Settings
  - Grid Toggle / Settings
  - Add Video / Audio
  - Make Movie (Export dialog)

### 1.2 Drawing Toolbar (Left/Side Rail)
- **Brush / Pen Tool (`TOOL_ID_DRAW = 9`)**:
  - Tapping when active opens brush popup (Brush type selector, size slider, opacity slider).
- **Eraser Tool**:
  - Size slider, hardness/edge feather slider, opacity slider.
- **Lasso Tool (`TOOL_ID_LASSO = 7`)**:
  - Freehand selection loop -> shows transform bounding box with scale, rotate, flip H, flip V, duplicate, delete.
- **Paint Bucket / Fill Tool (`TOOL_ID_FLOOD_FILL = 5`)**:
  - Color tolerance slider.
- **Color Swatch**:
  - Tapping opens HSV Color Wheel, hex/RGB input, alpha slider, and swatch palette.
- **Text Tool (`TOOL_ID_TEXT = 10`)** & **Image Tool (`TOOL_ID_IMAGE = 8`)**.

### 1.3 Bottom Timeline & Animation Navigation
- **Frame Thumbnail Strip**: Horizontal scrollable list (`RecyclerView` / `LazyRow`) showing rendered thumbnail previews of each frame.
- **Active Frame Indicator**: Highlighting current frame with orange border and frame index counter (e.g. `4 / 12`).
- **Add Frame Button (`+`)**: Inserts a blank frame immediately following the current frame. Long-press options: Paste frame, Clone frame.
- **Playback Controls**:
  - Play / Pause button (`Fc2Player` clock).
  - Loop toggle (Repeat endlessly vs. Play once).
- **Layers Button**:
  - Displays currently selected layer number and total layers badge (e.g., `L1 / 3`).
  - Tapping opens the Layer Management Bottom Sheet.

---

## 2. Modal Dialogs & Key User Flows

### 2.1 New Project Creation Flow
1. User taps floating `+` button on Home screen.
2. Dialog appears with fields:
   - **Project Name** (default: "My Movie #X")
   - **Canvas Preset Picker**:
     - YouTube 720p (1280x720) [16:9]
     - YouTube 1080p (1920x1080) [16:9]
     - TikTok 720p (720x1280) [9:16]
     - TikTok 1080p (1080x1920) [9:16]
     - Instagram (1080x1080) [1:1]
     - Instagram Portrait (1080x1350) [4:5]
     - Facebook (1280x720)
     - Custom (User-specified Width & Height)
   - **Background Selector**: Color palette, paper textures, or camera photo.
   - **FPS Slider**: 1 to 30 FPS with animated preview character demonstrating playback speed.
3. User taps "Create Project" -> Canvas initializes with Frame 1 and Layer 1.

### 2.2 Layer Management Panel
- Card list of layers stacked vertically from top layer (foreground) to bottom layer (background).
- Each layer row contains:
  - Layer name (editable on tap).
  - Visibility toggle (Eye icon).
  - Lock toggle (Padlock icon).
  - Opacity slider (0% to 100%).
  - Blend mode selector (Normal, Multiply, Screen, Overlay, etc.).
  - Reorder handle (Drag-and-drop to change layer z-order).
  - Merge down & Delete actions.
- Action to "Add Layer" (No artificial limit in our app!).

### 2.3 Onion Skin Settings Dialog (`OnionSettings`)
- **Master Onion Switch**: Enabled / Disabled.
- **Frames Before Slider**: 1 to 5 frames before current frame.
- **Frames After Slider**: 1 to 5 frames after current frame.
- **Opacity Gradient**: Start opacity (e.g., 50%) to End opacity (e.g., 20%).
- **Colored Onion Skin Toggle**:
  - When enabled: Past frames tinted Red, Future frames tinted Green.
  - When disabled: Monochromatic opacity tint.
- **Loop Onion Toggle**: Show wrap-around frames when on first or last frame.

### 2.4 Make Movie / Export Workflow
- Screen: `BuildMovieActivity` / `BuildMovieSettingsFragment`
- Options:
  - Movie title
  - Format: MP4 (H.264 video), GIF (Animated GIF), PNG Sequence
  - Resolution preset (Same as canvas or scaled down)
  - Frame rate override
  - Background inclusion (Include background vs. Transparent PNG/GIF)
  - Watermark toggle: In FlipaClip locked behind Paywall. **In our app: ZERO WATERMARK BY DEFAULT!**
- Output progress bar with frame-by-frame rendering counter and Cancel button.
- Completion dialog with "Open", "Share", and "Saved to Movies" notification.
