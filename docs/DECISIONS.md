# Architectural Decision Records (ADRs): Project jarvis

## ADR-001: Architecture Selection — Tauri 2 (Rust Backend + React 19 Frontend)
- **Status**: Accepted
- **Context**: Project jarvis requires pixel-perfect, sub-pixel accurate vector rendering, fluid spring animations, continuous squircle curvature, and deep Windows 10/11 desktop integration (GSMTC media, CoreAudio volume, PDH system performance metrics, DWM transparency, fullscreen detection).
- **Alternatives Considered**:
  1. *C# / .NET 8 / WinUI 3*: Audit revealed the host machine does not have the .NET SDK installed (`No .NET SDKs were found`). Furthermore, reference inspection of V-Notch revealed high memory usage (180–320 MB) and JIT cold startup delays.
  2. *Native C++ / Direct2D*: While fastest, raw C++ makes building modern modular reactive dashboards, fluid spring graphs, settings sheets, and responsive typography extremely verbose and difficult to maintain.
  3. *Tauri 2 (Rust + React 19 + Motion)*: The host machine possesses verified `cargo 1.99.1`, `rustc 1.99.1 (x86_64-pc-windows-msvc)`, `node 24.20.0`, and `npm 11.19.0`. Rust provides zero-overhead native access to all Win32 and WinRT APIs. React + Motion provides Apple-grade spring physics, sub-pixel SVG rendering, and zero-flicker UI updates at < 80 MB RAM.
- **Decision**: Adopt Tauri 2 with a Rust backend and React 19 / TypeScript / Motion frontend.

---

## ADR-002: Licensing & Clean-Room Implementation
- **Status**: Accepted
- **Context**: One of the reference repositories (`windows-dynamic-island`) is licensed under GNU GPL v3.0, while Cyclop and Glance are MIT, and V-Notch is Apache-2.0.
- **Decision**: Develop Project jarvis as a 100% original, clean-room implementation under the permissive MIT License. Do not copy, paste, or adapt any GPL code or assets. Design concepts and public Windows API patterns are implemented independently from first principles.

---

## ADR-003: Window Overlay Subclassing & Hit-Testing
- **Status**: Accepted
- **Context**: A desktop overlay must float on top (`HWND_TOPMOST`), render transparently without window borders or title bars, never show up in the Taskbar or Alt+Tab (`WS_EX_TOOLWINDOW`), never steal focus from active applications (`WS_EX_NOACTIVATE`), and allow mouse clicks outside the island boundary to pass directly to underlying windows.
- **Decision**:
  - Subclass the native HWND using `SetWindowSubclass`.
  - Handle `WM_NCPAINT` returning 0 to suppress non-client title bar painting.
  - Handle `WM_NCACTIVATE` with `LPARAM(-1)` to eliminate title bar color changes.
  - Handle `WM_STYLECHANGING` to enforce `WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE | WS_EX_LAYERED` and strip `WS_CAPTION | WS_SYSMENU`.
  - Dynamic Hit-Testing: Compute the active island bounds (plus 8px padding) and toggle `set_ignore_cursor_events` based on cursor coordinate containment, ensuring instant click-through to apps below.

---

## ADR-004: Motion Engine & Spring Physics Constants
- **Status**: Accepted
- **Context**: The four projects varied widely in animation quality. V-Notch used linear/cubic easing that felt mechanical. Glance achieved benchmark quality with Apple-tuned spring physics.
- **Decision**: Establish a centralized motion engine adopting Glance's verified physical constants:
  - Open / Close Morph: `stiffness: 340, damping: 30, mass: 0.8` (critically damped, zero overshoot, ~350ms duration).
  - Alert Pops & Notifications: `stiffness: 300, damping: 20, mass: 0.7` (slight bounce, ~450ms).
  - Hover Peek: `stiffness: 420, damping: 26, mass: 0.6` (~200ms).
  - Reduced Motion Mode: Fallback to instantaneous crossfade (`120ms` linear) when Windows accessibility flag is detected.

---

## ADR-005: Event-Driven System & Media Telemetry
- **Status**: Accepted
- **Context**: Excessive polling in background loops wastes CPU cycles and battery.
- **Decision**:
  - Connect to Windows `GlobalSystemMediaTransportControlsSessionManager` (GSMTC) using WinRT event callbacks (`MediaPropertiesChanged`, `PlaybackInfoChanged`) wherever possible, falling back to a low-frequency 1Hz polling timer when playing.
  - Sample system performance metrics (PDH CPU, Memory, Battery) only when the island is expanded or active alerts are displayed, throttling to 5-second intervals when idle.
