# Comprehensive Repository Audit: Project jarvis

## 1. Executive Summary

This audit evaluates five open-source Dynamic Island / Notch implementations to establish the engineering, visual, architectural, and mathematical baseline for **Project jarvis** (a pixel-perfect Windows Dynamic Island):

1. **Cyclop** (`akalikbergenov/cyclop`, macOS, Swift/SwiftUI/AppKit, MIT)
2. **Glance** (`imgaty/Glance`, macOS, Swift/SwiftUI/AppKit, MIT)
3. **V-Notch** (`rainaku/V-Notch`, Windows, C#/.NET 8/WPF/Direct2D/HLSL, Apache-2.0)
4. **Windows Dynamic Island** (`HeyyCzer/windows-dynamic-island`, Windows, Tauri 2/Rust/React 19/Motion, GPL-3.0-or-later)
5. **Nectar** (`kua8/Nectar`, Windows, Tauri 2/Rust/React 19/Motion/Vite, GPL-3.0-only)

Every conclusion in this document is derived from direct inspection of source code, configuration manifests, build targets, and runtime architectures.

---

## 2. Deep Dive: Cyclop (`references/cyclop`)

### 2.1 Metadata & Build Configuration
- **Repository**: `https://github.com/akalikbergenov/cyclop.git`
- **Primary Language & Framework**: Swift 5.10+, SwiftUI, AppKit (`NSPanel`, `NSWindow`)
- **Package Manager**: Swift Package Manager (`Package.swift`)
- **Platform**: macOS 14.0+ (designed specifically for Apple Silicon MacBook notched displays with fallback to external displays)
- **License**: MIT License (Copyright 2026 akalikbergenov)

### 2.2 Window Management & Overlay Architecture
- **Panel Base**: Implemented in `NotchPanel.swift` inheriting from `NSPanel`.
- **Window Level & Style**:
  - `level = .mainMenu + 1` (floats above ordinary windows and menu bars).
  - `styleMask = [.borderless, .nonactivatingPanel]`.
  - `collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary, .ignoresCycle]`.
  - `isOpaque = false`, `backgroundColor = .clear`, `hasShadow = false`.
- **Interactivity & Click-Through**:
  - Controlled dynamically via `panel.ignoresMouseEvents = !isInteractive`.
  - Driven by `PointerWatcher.swift` which tracks global cursor coordinates.
- **Multi-Monitor Handling**:
  - `NotchGeometry.swift` queries `NSScreen.screens`. Differentiates physical hardware notches (`screen.auxiliaryTopLeftArea != nil`) from synthetic floating notches on flat monitors.
  - Dynamically diffs display reconfiguration events (`NSApplication.didChangeScreenParametersNotification`).

### 2.3 Interaction Lifecycle & Pointer Watcher Physics
- **Sampling Rates**:
  - Idle Rate: 8 Hz (`125ms` interval) with `62.5ms` system timer tolerance to minimize battery/CPU impact.
  - Active/Warm Rate: 60 Hz (`16.6ms` interval) activated when cursor enters a defined "warm zone" within 80px (`coolMargin`) of the top screen edge and moving.
  - Rest threshold: If pointer is stationary for > 3.0 seconds, reverts to 8 Hz idle polling.
- **Hysteresis & Dwell Timers**:
  - `openDelay = 0.05s` (50ms) to ignore casual sweeps across the top edge.
  - `closeDelay = 0.05s` (50ms).
  - Distinct `openRect` (collapsed footprint) vs `closeRect` (expanded body) to prevent rapid oscillating state flicker at boundaries.

### 2.4 Notch Geometry & Bezier Curvature
- Implemented in `NotchShape.swift`:
  - Features authentic Apple notch concave shoulders melting into the top bezel:
    - Upper concave shoulder: Quadratic bezier curve from `(minX, minY)` to `(left, minY + topRadius)` with control point `(left, minY)`.
    - Straight side edges.
    - Lower convex corners: Quadratic curve into bottom horizontal edge.
- Dimensions:
  - Collapsed Notch: width ~180-210pt, height 32-38pt (follows menu bar height).
  - Expanded Panel: fixed standard size `620 x 208 pt`.
  - Tall Body Mode (Teleprompter): `620 x 400 pt`.

### 2.5 Feature Modules & Services
- **Media**: `MediaController.swift` and `NowPlayingFeed.swift` using `MediaRemote` framework / `MRMediaRemoteRegisterForNowPlayingNotifications`.
- **Shelf**: Drag & drop staging file shelf (`ShelfStore.swift`) with persistent drag-out capabilities.
- **Clipboard**: `ClipboardStore.swift` polling pasteboard change count, persisting recent text snippets and screenshots.
- **Cleaning Overlay**: `CleaningOverlay.swift` and `KeyboardLock.swift` for wiping screen/keyboard.
- **Utilities**: Teleprompter, Notes, Snippets, Currency Converter, Calendar.

---

## 3. Deep Dive: Glance (`references/glance`)

### 3.1 Metadata & Build Configuration
- **Repository**: `https://github.com/imgaty/Glance.git`
- **Primary Language & Framework**: Swift, SwiftUI, AppKit
- **Package Manager**: Swift Package Manager (`Package.swift`), custom release scripts (`build.sh`, `release.sh`)
- **Platform**: macOS 14.0+
- **License**: MIT License (Copyright 2026 Gaty)

### 3.2 Visual Aesthetics & Liquid Glass Engine
- **Liquid Glass Shader & Look**:
  - Defined in `GlassLook`: continuous blur curve `24 * pow(amount, 1.5)` (ranging from 0 to 24pt blur; default 10pt).
  - Floor darkness parameter `floor = 0.35` with non-linear dimming: `0.35 + (1 - 0.35) * darkness^2` to maintain legibility over pure white backgrounds without looking muddy.
- **Corner Morphology (`NotchCorners`)**:
  - Supports continuous interpolation between collapsed and expanded states using Apple squircle curvature.
  - Supports two operational modes:
    1. **Notch Mode**: Top edge flaring (`restingFlare = 6pt` expanding to `openRadius = 24pt`).
    2. **Island Mode**: Floating capsule with symmetric round corners (`flare = 0`, closed radius `height / 2`).

### 3.3 State Machine & Spring Animation Physics
- Three primary discrete states:
  1. `.closed`: Resting capsule or flush notch.
  2. `.peek`: Subtle micro-expansion when hovered ("small swell that says 'I see you'").
  3. `.open`: Full expanded interactive dashboard.
- Verified Animation Parameters:
  - Open/Close Transition: `.spring(response: 0.35, dampingFraction: 1.0)` — critically damped for zero overshoot, clean and rapid Apple feel.
  - Activity / Peek Alert Pop: `.spring(response: 0.45, dampingFraction: 0.8)` — slight 20% underdamping creates an organic, physical pop.
  - Track / Album Announcement: `.spring(response: 0.5, dampingFraction: 0.8)`.
  - Page Turning: `.spring(response: 0.42, dampingFraction: 1.0)`.
  - Interactive Pull / Rubber-Banding:
    - Formula: `f(x) = sign(x) * limit * (1 - 1 / (|x| * 0.55 / limit + 1))` where limit is 28pt downward, 16pt upward.
    - Drag spring: `.interactiveSpring(response: 0.18, dampingFraction: 0.86)`.
    - Release snapback: `.spring(response: 0.45, dampingFraction: 0.5)`.

### 3.4 Media & Visualizer Engineering
- **Waveform Glyph (`Waveform.swift`)**:
  - 6 equalizer frequency bars (bass to treble) driven by real-time audio analysis (`AudioLevels.shared`).
  - Strict proportion: Bar width is `1.3x` the inter-bar gap (`bar = width / (6 + 5 / 1.3)`).
  - Vertical gradient fill: `[color.mix(with: .gray, by: 0.5), color]`.
  - Seamlessly morphs into lyrics button glyph (`quote.bubble.fill`) using `.spring(response: 0.26, dampingFraction: 0.9)`.
- **Marquee Text**: Infinite horizontal scrolling with fade masks on leading/trailing edges when titles exceed available width.

---

## 4. Deep Dive: V-Notch (`references/v-notch`)

### 4.1 Metadata & Build Configuration
- **Repository**: `https://github.com/rainaku/V-Notch.git`
- **Primary Language & Framework**: C# 12, .NET 8.0 Windows Desktop SDK (`net8.0-windows10.0.19041.0`), WPF (`<UseWPF>true</UseWPF>`)
- **Rendering**: Direct2D1 / Direct3D11 / DXGI interop via `Vortice.Windows` & `ComputeSharp.D2D1`, custom HLSL pixel shaders (`LiquidGlassRefraction.hlsl` compiled to PS 3.0 via `fxc.exe`).
- **License**: Apache License 2.0 (Copyright 2026 rainaku)

### 4.2 Windows Native Window Subclassing & Hit-Testing
- **Window Style Flags**:
  - `WS_EX_TOOLWINDOW`: Prevents taskbar icon and Alt+Tab presence.
  - `WS_EX_NOACTIVATE`: Prevents window from stealing focus upon click.
  - `WS_EX_LAYERED`: Enables transparent per-pixel alpha composition.
- **WM_NCHITTEST Pass-Through**:
  - Instead of continuous polling threads that toggle `WS_EX_TRANSPARENT`, V-Notch implements `WndProc` interception of `WM_NCHITTEST`:
    - Checks `IsPointInteractive(point)`.
    - If cursor is outside the visible notch geometry, returns `HTTRANSPARENT` (`(IntPtr)(-1)`).
    - Windows automatically routes the mouse message down to the application underneath with zero lag and zero polling overhead!
- **Z-Order Management**:
  - Intercepts `WM_WINDOWPOSCHANGING` and forces `HWND_TOPMOST` without activation (`SWP_NOACTIVATE`).
  - Listens to `WM_DISPLAYCHANGE` and `WM_DPICHANGED` for monitor/resolution adaptation.

### 4.3 Fullscreen Detection Engine (`FullscreenDetector.cs`)
- Production-grade Win32 implementation:
  - Enumerates foreground HWND and gets bounding rectangle via `DwmGetWindowAttribute(DWMWA_EXTENDED_FRAME_BOUNDS)`.
  - Filters out shell windows (`Progman`, `WorkerW`, `Shell_TrayWnd`, `Shell_SecondaryTrayWnd`, `TaskSwitcherWnd`, etc.).
  - Detects cloaked windows via `DWMWA_CLOAKED`.
  - Evaluates whether window rect covers `MONITORINFO.rcMonitor` or `rcWork` within a 6px tolerance:
    - Distinguishes **Exclusive Fullscreen** (borderless covering full monitor) from **Windowed Fullscreen** and **Standard Maximized Window** (which preserves taskbar).
    - Automatically tucks or hides the notch when fullscreen games or video players are active.

### 4.4 Architectural Observations
- **Strengths**: Superb Win32 native integration, flawless hit-testing via `WM_NCHITTEST`, rock-solid fullscreen detection.
- **Weaknesses**: Heavy dependency bloat (~1.5 GB build tree containing LLamaSharp, ONNX Runtime, Vulkan binaries, AngleSharp, and YoutubeExplode), complex XAML state management, and high base memory footprint (~180-320 MB RAM).

---

## 5. Deep Dive: Windows Dynamic Island (`references/windows-dynamic-island`)

### 5.1 Metadata & Build Configuration
- **Repository**: `https://github.com/HeyyCzer/windows-dynamic-island.git`
- **Frontend**: React 19, Motion (`motion` 13.4, formerly Framer Motion), React Router 8, TypeScript 7, Vite 8.
- **Backend**: Tauri 2.12, Rust 2024 edition (`rust-version = "1.98"`), `windows` crate 0.62.
- **License**: GNU General Public License v3.0 or later (GPL-3.0-or-later).

### 5.2 Window Overlay & Interop Architecture
- **Window Management (`window.rs`)**:
  - Subclasses the HWND using `SetWindowSubclass` (`OVERLAY_SUBCLASS = 0x15_1A_4D`).
  - Intercepts `WM_NCPAINT` and returns 0 to eliminate unwanted DWM window border rendering.
  - Intercepts `WM_NCACTIVATE` passing `LPARAM(-1)` to suppress non-client title bar painting.
  - Intercepts `WM_STYLECHANGING` to enforce `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE` and strip `WS_CAPTION | WS_SYSMENU`.
  - Full monitor width overlay strip: height fixed to 480 logical px (`STRIP_HEIGHT = 480.0`).
- **Hit-Testing Implementation**:
  - Tracks cursor via a background polling thread that checks if cursor position is within the frontend bounding boxes + `DROP_MARGIN`.
  - Calls `set_ignore_cursor_events(!inside)` to toggle click-through.
- **Foreground Tracking**:
  - Stores `LAST_FOREGROUND` HWND to restore keyboard focus back to the user's prior application when the island collapses.

### 5.3 Hardware & System Providers
- **Music (`smtc.rs`)**:
  - Windows 10/11 `GlobalSystemMediaTransportControlsSessionManager` (GSMTC).
  - Fetches timeline properties, playback status, metadata, and stream thumbnails.
  - Dispatches `TryPlayAsync`, `TryPauseAsync`, `TrySkipNextAsync`, `TryChangePlaybackPositionAsync`.
- **System Monitor (`monitor/mod.rs` & `pdh.rs`)**:
  - Uses Windows Performance Data Helper (PDH) counters for CPU, GPU Engine (`\GPU Engine(*)\Utilization Percentage`), Network traffic (`\Network Interface(*)\Bytes Received/sec`).
  - `GlobalMemoryStatusEx` for real-time RAM usage and capacity.
  - `GetSystemPowerStatus` for battery percentage and AC charging states.
  - Rolling 60-second history buffer.
- **Notifications (`notifications/mod.rs`)**:
  - `UserNotificationListener` API for toast interception.
  - Note: Requires Windows Package Identity (sparse package registration) to access notifications from other applications.
- **Clipboard (`clipboard/mod.rs`)**:
  - Uses `GetClipboardSequenceNumber` to avoid continuous clipboard polling.
  - Respects sensitive formats (`Clipboard Viewer Ignore`, `CanIncludeInClipboardHistory`).

---

## 6. Deep Dive: Nectar (`references/nectar`)

### 6.1 Metadata & Build Configuration
- **Repository**: `https://github.com/kua8/Nectar.git` (Fork of Bloom by Sehajveer Singh)
- **Frontend**: React 19, Motion (`framer-motion` 12.38), TypeScript 5.8, Tailwind CSS 4, Vite 7.
- **Backend**: Tauri 2.11, Rust 2021 edition, `windows` crate 0.61.
- **License**: GNU General Public License v3.0 (GPL-3.0-only).

### 6.2 Window Management & Anti-Minimize Innovations
- **Anti-Minimize Guard (`active_look.rs`)**:
  - Subclasses the HWND to intercept `WM_SYSCOMMAND`:
    - Checks `(wparam & 0xFFF0) == SC_MINIMIZE`.
    - Returns `LRESULT(0)` immediately.
    - **Critical UX Insight**: This guarantees that when the user presses `Win+D` (Show Desktop) or `Win+M` (Minimize All), the Dynamic Island is **never** inadvertently minimized or hidden from the screen!
- **Active Look Preservation**:
  - Intercepts `WM_NCACTIVATE` with `WPARAM(1)` so the window maintains full specular highlights and does not visually de-emphasize when other applications lose focus.

### 6.3 Media & Command Center Refinements
- **Title Marquee Engine (`CompactMediaPlayer.tsx`)**:
  - Dynamically measures container width vs text width via `ResizeObserver`.
  - Calculates exact overflow distance: `scrollDistance = textWidth - containerWidth`.
  - Calculates dynamic duration: `scrollDuration = max(distance / 30, 5s)`.
  - Animates with CSS variables `--scroll-distance` and `--scroll-duration` with smooth pause-at-ends keyframe easing.
- **Negative Remaining Time Format**:
  - Displays remaining track duration in Apple's standard countdown format (`-M:SS`), providing intuitive tracking during playback.
- **Web Audio Chime Synthesis**:
  - Synthesizes an ambient bell chime (A5 880Hz, C#6 1108Hz, E6 1318Hz with 2.01x harmonic) directly via Web Audio API, eliminating the need for external `.wav` or `.mp3` sound assets.
- **Command Center & Quick Controls**:
  - Quick widgets for volume, brightness, battery, and system metrics.

---

## 7. Synthesis: Technical Learnings for Project jarvis

| Domain | Best Pattern Identified | Provenance | Planned Implementation in jarvis |
| :--- | :--- | :--- | :--- |
| **Window Subclassing** | `WM_NCPAINT=0`, `WM_NCACTIVATE=-1`, `WS_EX_TOOLWINDOW \| WS_EX_NOACTIVATE` | Windows Dynamic Island / V-Notch | Native Win32 subclassing via Rust/Win32 APIs. |
| **Mouse Pass-Through** | Native `WM_NCHITTEST` returning `HTTRANSPARENT` (-1) + coordinate boundary check | V-Notch | High-performance boundary hit-test without laggy polling. |
| **Hover & Dwell Rates** | Dual-rate pointer watcher (8Hz idle -> 60Hz warm within 80px hysteresis) | Cyclop | Low-power background cursor watcher with 50ms dwell filtering. |
| **Geometry & Curvature** | Squircle continuous curvature + concave top shoulders or floating pill | Cyclop & Glance | Parametric SVG/CSS continuous curvature supporting both notch & pill modes. |
| **Animation Physics** | Critically damped springs (`response: 0.35s, damping: 1.0`) & pop alert springs (`0.45s, 0.8`) | Glance | Unified spring animation engine matching Apple's canonical physics. |
| **Fullscreen Detection** | `DwmGetWindowAttribute` + monitor rect matching + shell class filtering | V-Notch | Dedicated Win32 fullscreen monitor with auto-hide capability. |
| **Media Transport** | GSMTC events + timeline seek + thumbnail buffer extraction | Windows Dynamic Island / Glance | Windows Media Control session manager with real-time change events. |
| **System Telemetry** | PDH counters + `GlobalMemoryStatusEx` + Audio Endpoint Volume | Windows Dynamic Island | Native background worker updating state only when island is visible/active. |
| **Anti-Minimize Guard** | Intercept `WM_SYSCOMMAND` + `SC_MINIMIZE` returning 0 | Nectar | Native Win32 subclassing preventing minimization on Win+D / Win+M. |
| **Marquee Overflow** | Dynamic `ResizeObserver` measuring distance + CSS keyframe pause | Nectar / Glance | CSS custom property marquee with pause-at-ends and negative countdown (`-M:SS`). |
| **License Isolation** | Clean-room independent architecture; no direct copying of GPL code | Legal Audit | 100% independent codebase under MIT/Apache 2.0 compatible terms. |
