# System Architecture: Project jarvis

## 1. Architectural Overview

Project jarvis is engineered as a high-performance, native-feeling Windows Dynamic Island and Notch application for Windows 11 and Windows 10.

It combines a native **Rust Core** for low-level Win32 window management, DWM transparency, hardware monitoring, and media session integration, with a hardware-accelerated **React 19 / TypeScript / Motion Frontend** hosted in an edge-to-edge transparent WebView2 overlay.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PROJECT jarvis ARCHITECTURE                     │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                      FRONTEND (TypeScript / React 19)                  │
│                                                                        │
│  ┌──────────────────┐  ┌─────────────────────┐  ┌───────────────────┐  │
│  │ Motion Engine    │  │ Island Silhouette   │  │ Module Deck       │  │
│  │ (Framer Motion / │  │ (Squircle / Capsule │  │ - Media Player    │  │
│  │  Canonical Spg.) │  │  Notch / Pill Mode) │  │ - System Telemetry│  │
│  └─────────┬────────┘  └──────────┬──────────┘  │ - Volume / Battery│  │
│            │                      │             │ - File Shelf      │  │
│            └───────────────┬──────┴─────────────│ - Clipboard Vault │  │
│                            │                    └─────────┬─────────┘  │
│                            ▼                              │            │
│               ┌─────────────────────────┐                 │            │
│               │ State Machine & Router  │◄────────────────┘            │
│               │ (Closed, Peek, Expanded)│                              │
│               └────────────┬────────────┘                              │
└────────────────────────────┼───────────────────────────────────────────┘
                             │ Tauri 2 IPC (Events & Invoke)
┌────────────────────────────┼───────────────────────────────────────────┐
│                            ▼                                           │
│                     RUST BACKEND CORE                                  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Window Overlay Engine                                            │  │
│  │ - SetWindowSubclass (WM_NCPAINT, WM_NCACTIVATE, WM_STYLECHANGING)│  │
│  │ - Hit-Test Controller (set_ignore_cursor_events / click-through) │  │
│  │ - Topmost Z-Order Watchdog (HWND_TOPMOST, SWP_NOACTIVATE)        │  │
│  │ - Multi-Monitor & DPI Scaling Engine (EnumDisplayMonitors)       │  │
│  │ - Fullscreen Detection Engine (DwmGetWindowAttribute)            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Hardware & OS Service Providers                                  │  │
│  │ - GSMTC Media Session Manager (Track, Artist, Art, Seek, Controls│  │
│  │ - CoreAudio Endpoint Volume (Real-time scalar volume + HUD)      │  │
│  │ - System Telemetry (GlobalMemoryStatusEx, PDH CPU/GPU, Battery)  │  │
│  │ - Clipboard Watcher (GetClipboardSequenceNumber + privacy filter)│  │
│  │ - System Tray & Startup Registry Integration                     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Window Overlay & Native Subclassing Engine

The application window is created as an edge-to-edge transparent overlay positioned horizontally across the target monitor at the top edge.

### 2.1 Window Flags & Styles
To ensure the island never behaves like a normal desktop window:
1. `WS_EX_TOOLWINDOW`: Prevents the window from ever displaying an icon on the Windows Taskbar or appearing in the Alt+Tab switcher.
2. `WS_EX_NOACTIVATE`: Prevents clicking buttons on the island from taking window focus away from the active work application (word processor, code editor, game, browser).
3. `WS_EX_LAYERED`: Enables transparent per-pixel 32-bit RGBA hardware composition.
4. Strips `WS_CAPTION` and `WS_SYSMENU` to eliminate window borders.

### 2.2 Window Subclass Procedure (`overlay_proc`)
Registered via `SetWindowSubclass`:
- `WM_NCPAINT`: Handled and suppressed (`return LRESULT(0)`). This prevents Windows DWM from drawing default window borders or title bar graphics across the transparent canvas.
- `WM_NCACTIVATE`: Passed with `LPARAM(-1)` so `DefWindowProc` never modifies non-client rendering upon focus shifts.
- `WM_STYLECHANGING`: Enforces the retention of `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` across OS theme changes.

### 2.3 Dual-Rate Hit-Testing & Click-Through
Following the proven pattern identified in our audit of Cyclop and V-Notch:
- When the cursor is outside the visible island silhouette, the window is marked transparent to input (`set_ignore_cursor_events(true)` / `WS_EX_TRANSPARENT`), allowing clicks to pass through instantly to underlying windows with zero interference.
- The pointer watcher runs at an energy-efficient **8 Hz** when the cursor is idle or distant from the top edge. When the cursor enters the top **80px warm zone**, sampling seamlessly accelerates to **60 Hz** to guarantee instantaneous response.
- A 50ms dwell timer (`openDelay` / `closeDelay`) prevents casual cursor sweeps across the top screen edge from falsely expanding the island.

---

## 3. Modular Feature Providers

Each provider operates as an isolated, asynchronous background service communicating with the central event hub:

### 3.1 Media Provider (`media.rs`)
- Interacts with `Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager`.
- Subscribes to playback changes (`PlaybackInfoChanged`), timeline adjustments (`TimelinePropertiesChanged`), and metadata updates (`MediaPropertiesChanged`).
- Safely reads track title, artist, album name, position, duration, and extracts the album thumbnail as a memory buffer/data URI.
- Supports commands: `Play`, `Pause`, `Toggle`, `Next`, `Previous`, `Seek(ms)`.

### 3.2 System Telemetry Provider (`system.rs`)
- **Memory**: Calls `GlobalMemoryStatusEx` for total physical RAM and available RAM.
- **Power**: Calls `GetSystemPowerStatus` for AC line status, battery percentage, and charging state.
- **CPU**: Samples `PdhOpenQuery` / `\Processor Information(_Total)\% Processor Utility` (or high-resolution system times).
- Throttles updates to 1s when active, 5s when collapsed, and 0s when monitor display is off.

### 3.3 Audio Volume Provider (`volume.rs`)
- Interfaces with Windows CoreAudio `IMMDeviceEnumerator` and `IAudioEndpointVolume`.
- Listens to volume adjustments made via hardware keys, mice, or keyboards.
- Triggers a transient high-priority "Volume HUD" micro-pill in the island that displays current percentage and mute state, auto-dismissing after 2 seconds.

### 3.4 Fullscreen Guard (`fullscreen.rs`)
- Detects when the active foreground window is in exclusive fullscreen or borderless fullscreen on the island's display.
- Temporarily hides or tucks the island upwards to ensure games, presentations, and video players are never obscured.

---

## 4. Frontend State Machine & Motion Architecture

### 4.1 Island State Graph
```
┌──────────────────┐
│     CLOSED       │◄─────────────────────────────┐
│ (Pill / Notch)   │                              │
└────────┬─────────┘                              │
         │ Hover (50ms dwell)                     │
         ▼                                        │ Pointer leaves /
┌──────────────────┐                              │ Click outside
│      PEEK        │                              │
│ (Micro-Swell)    │                              │
└────────┬─────────┘                              │
         │ Click / Drag                           │
         ▼                                        │
┌──────────────────┐                              │
│     EXPANDED     │──────────────────────────────┘
│ (Full Dashboard) │
└──────────────────┘
```

### 4.2 Dynamic Geometry Modes
- **Island Mode (Floating Capsule)**: Ideal for centered desktop setups without physical camera cutouts.
- **Notch Mode (Flush Bezel)**: Features concave shoulders blending seamlessly into the physical top monitor frame.

---

## 5. IPC Contract & Data Formats

### 5.1 Rust -> Frontend Events
- `jarvis://media-update`: `{ playing: bool, title: string, artist: string, album: string, position_ms: u64, duration_ms: u64, thumbnail: Option<string> }`
- `jarvis://system-update`: `{ cpu_percent: f32, ram_used_mb: u64, ram_total_mb: u64, battery_percent: Option<u8>, is_charging: bool }`
- `jarvis://volume-update`: `{ volume: f32, is_muted: bool }`
- `jarvis://fullscreen-change`: `{ is_fullscreen: bool }`

### 5.2 Frontend -> Rust Commands
- `jarvis_media_control(action: string, payload: Option<Value>)`
- `jarvis_set_volume(volume: f32)`
- `jarvis_update_hit_rects(rects: Vec<Rect>)`
- `jarvis_get_monitors() -> Vec<MonitorInfo>`
- `jarvis_set_config(config: AppConfig)`
