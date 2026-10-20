# Feature Matrix & Cross-Project Comparison: Project jarvis

## 1. Comparative Matrix

| Feature | Cyclop (macOS) | Glance (macOS) | V-Notch (Windows) | Windows Dynamic Island (Windows) | Nectar (Windows) | Project jarvis Target Spec |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Collapsed Island / Notch** | Physical/Synthetic notch (`620x38`) with top shoulders | Notch with flare OR Floating Pill capsule | Curved top notch overlay | Floating pill or top notch | Top notch or floating island pill | Both modes: Understated Top Notch & Floating Pill |
| **Expanded Panel** | Fixed tabbed dashboard (`620x208` or `620x400`) | Multi-page fluid panel with page nudge | Tabbed widgets & Spotlight AI window | Modular widget deck with dynamic height | Multi-mode widget deck & quick controls | Fluid expanding container (`min: 440px`, `max: 580px`, `height: 196-240px`) |
| **Motion & Springs** | SwiftUI default spring / linear animations | Spring physics (`0.35s / 1.0` and `0.45s / 0.8`) + rubber-band | WPF Storyboard / DoubleAnimation easing | Framer Motion layout springs | Framer Motion layout springs | Centralized Spring Engine matching canonical Apple curves |
| **Pointer & Hover Dwell** | 8Hz idle / 60Hz warm pointer watcher; 50ms dwell | AppKit tracking rects & drag interaction | WPF MouseEnter/Leave with delay timer | 100ms cursor polling thread | 50ms hover dwell & trailing throttle | Dual-rate pointer watcher (8Hz/60Hz) with 50ms dwell & hysteresis |
| **Click-Through Handling** | `ignoresMouseEvents` toggling | `ignoresMouseEvents = true` + controls enabled | `WM_NCHITTEST` returning `HTTRANSPARENT` (-1) | `set_ignore_cursor_events(!inside)` | Coordinate bounding box toggling | Optimized `WM_NCHITTEST` / `set_ignore_cursor_events` hybrid |
| **Media Metadata & SMTC** | macOS `MediaRemote` private framework | macOS `NowPlaying` APIs | GSMTC via WinRT / `Windows.Media.Control` | GSMTC via Rust `windows` crate | GSMTC via WinRT session manager | GSMTC session manager with event-driven metadata |
| **Media Playback Controls** | Play/Pause, Next, Previous | Play/Pause, Next, Prev, Scrub bar, Volume | Play/Pause, Next, Prev, Volume, Seek | Play/Pause, Next, Prev, Seek, App Focus | Play/Pause, Next, Prev, Seek, Output Dev | Play/Pause, Previous, Next, Seek slider, Volume scroll |
| **Album Art & Palette** | Artwork image extraction | High-res art with palette extraction for dynamic accent | Embedded thumbnail image | Base64 thumbnail extraction | Base64 art with key transitions | Non-jittering smooth thumbnail loader + fallback art |
| **Live Audio Visualizer** | None (Static media) | 6-bar dynamic waveform (1.3x gap ratio) | HLSL pixel shader audio ripples | Simple audio level | 5-band RustFFT audio visualizer | 6-bar dynamic responsive audio equalizer waveform |
| **System Telemetry** | None | System audio volume & output device | CPU, RAM, GPU, Battery, Network | CPU, RAM, GPU, Battery, Network (PDH) | CPU, RAM, Disk, Net I/O, Battery | Real-time CPU, RAM, Battery (charging/percent), Network |
| **Volume Control** | None | AppKit volume slider & device switcher | Windows CoreAudio endpoint volume | Win32 CoreAudio endpoint volume | Custom CoreAudio volume HUD overlay | Smooth volume HUD overlay + interactive volume control |
| **Anti-Minimize (Win+D)** | N/A (macOS) | N/A (macOS) | Custom topmost loop | Window subclassing | `WM_SYSCOMMAND` + `SC_MINIMIZE` intercept | Native Win32 `SC_MINIMIZE` interception |
| **Clipboard History** | In-memory text + image vault | In-memory tray staging | Text clipboard history | Text + PNG files with password exclusion | None | Safe in-memory bounded clipboard history (opt-in) |
| **File Shelf / Staging** | Persistent drag-and-drop file shelf | Tray file staging with AirDrop link | None | Drag & drop staging shelf | None | Safe drag-and-drop file shelf with instant drag-out |
| **Notifications** | None | Song announcement wings | Custom toast alerts | Intercepts Windows toasts via package | Toast alerts | Rich activity alerts (battery, volume, media, timers) |
| **Fullscreen Detection** | Spaces change observer | `fullScreenAuxiliary` collection behavior | Comprehensive Win32 `FullscreenDetector` | Maximized/fullscreen avoidance monitor | Process-based fullscreen detection | Advanced Win32 Fullscreen auto-hide & tuck policy |
| **Multi-Display & DPI** | Multi-screen panel instances (`NSScreen`) | Multi-screen support with display selector | Screen geometry & DPI scale calculator | Monitor selector + auto-fallback to primary | Per-monitor settings & positioning | Dynamic per-monitor DPI scaling & work-area adaptation |
| **Settings & Customization** | Settings tab in notch | Dedicated Settings window with live mockups | Extensive XAML settings window | In-island Settings view + web config | Dedicated HTML settings window | Elegant In-Island & dedicated Settings manager |
| **Startup & System Tray** | Launch at login | Launch at login + menu bar icon | Windows Registry Run key + NotifyIcon | Windows autostart plugin + System tray | Windows autostart + Tray menu | Native tray icon with quick actions + Startup toggle |
| **Resource Efficiency** | Low (~35 MB RAM, <0.2% idle CPU) | Low (~45 MB RAM, <0.3% idle CPU) | Moderate-High (180-320 MB RAM, 1-3% CPU) | Low-Moderate (60-95 MB RAM, <0.5% idle CPU) | Low-Moderate (65-90 MB RAM, <0.5% CPU) | Strict target: <40 MB RAM, <0.1% idle CPU, zero idle loop |
| **Licensing** | MIT License | MIT License | Apache-2.0 License | GPL-3.0-or-later License | GPL-3.0-only License | MIT / Clean-Room Independent Implementation |

---

## 2. In-Depth Feature Analysis

### 2.1 Collapsed & Expanded Geometry
- **Cyclop**: Fixed width notch with authentic concave shoulders melting into the screen edge. Works well on Mac displays, but on Windows 10/11 flat displays, a floating pill option is essential.
- **Glance**: Elegantly solves this by offering both **Notch** mode and **Island** (pill) mode. Its corner squircle mathematics (`NotchCorners`) smoothly interpolate flare and radius during expansion.
- **jarvis Synthesis**: jarvis will provide a toggleable mode:
  1. *Dynamic Island (Floating Pill)*: Resting at `width: 220px, height: 36px`, rounded `18px`, offset `10px` below the top screen edge.
  2. *Dynamic Notch (Flush)*: Resting at `width: 200px, height: 32px`, with top concave flares blending into the upper bezel.
  Both expand into an elegant squircle panel (`width: 580px, height: 220px`).

### 2.2 Animation & Motion Dynamics
- **Glance** is the gold standard for animation timing among the four references. Its use of `spring(response: 0.35, dampingFraction: 1.0)` eliminates rubbery oscillation while providing authentic fluid inertia.
- **V-Notch** uses standard WPF cubic easings which can feel mechanical.
- **Windows Dynamic Island** uses Framer Motion, which provides smooth spring physics in the browser engine.
- **jarvis Synthesis**: Centralized spring configuration:
  - `transition: { type: "spring", stiffness: 350, damping: 30, mass: 0.8 }` (equivalent to `response: 0.35s, dampingFraction: 1.0`).
  - Micro-pops (alerts): `{ type: "spring", stiffness: 300, damping: 20, mass: 0.7 }`.

### 2.3 Media & Audio Visualizer
- **Glance** features the definitive waveform visualization: 6 dynamic frequency bars whose widths are `1.3x` the gap, filled with an accent gradient derived from the album art, which spring-morphs into a lyrics/controls glyph.
- **Windows Dynamic Island** provides clean GSMTC integration via WinRT `GlobalSystemMediaTransportControlsSessionManager`.
- **jarvis Synthesis**:
  - Live GSMTC media control (play, pause, next, previous, scrub/seek, volume).
  - High-fidelity 6-bar audio visualizer animated during playback.
  - Smooth metadata transition without layout jumps or flickering.

### 2.4 System Activities & Telemetry
- **Windows Dynamic Island** has the strongest Windows system monitoring implementation via PDH performance counters and `GlobalMemoryStatusEx`.
- **V-Notch** includes volume and battery monitoring.
- **jarvis Synthesis**:
  - Battery percentage, AC charging indicator, low-battery alerts.
  - Master audio volume overlay and interactive slider.
  - Real-time CPU, RAM, and network speed gauges with 60s sparkline history.

### 2.5 Window Subclassing & Hit-Testing
- **V-Notch** has the best hit-testing model: `WM_NCHITTEST` returning `HTTRANSPARENT` (-1) when the pointer is outside the notch geometry, bypassing expensive polling loops.
- **Windows Dynamic Island** provides the best DWM transparent window styles: `WM_NCPAINT = 0`, `WM_NCACTIVATE = -1`, `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE | WS_EX_LAYERED`.
- **jarvis Synthesis**: Combine both techniques!
  - Native Win32 window styles: borderless, transparent, non-activating, no taskbar/Alt-Tab presence.
  - Active hit-testing that allows click-through to underlying applications everywhere outside the rendered island silhouette.

---

## 3. Verification Criteria
For every feature implemented in jarvis:
1. **Geometry**: Measured in logical and physical pixels at 100%, 125%, 150%, 200% DPI.
2. **Hit-Testing**: Verified with underlying desktop icons, browser windows, and clicks directly surrounding the island.
3. **Performance**: Idle CPU measured via Task Manager / Performance Monitor (< 0.2% CPU); memory under 80 MB.
4. **Media Responsiveness**: Transport commands dispatched within < 100ms.
5. **Robustness**: Graceful handling when no media player is open or when displays change.
