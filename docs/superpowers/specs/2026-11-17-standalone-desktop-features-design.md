# Design Specification: Standalone Desktop Evolution & Modular Island Engine

> **Document Path**: `docs/superpowers/specs/2026-11-17-standalone-desktop-features-design.md`  
> **Status**: Proposed  
> **Author**: GLOOM Engineering Team  
> **Target Release**: GLOOM Desktop 2.4.0 (Windows 10/11)

---

## 1. Intent & The "Why"
GLOOM has established a stable, fluid Dynamic Island and magnifying dock on Windows, recently reinforced by the permanent elimination of the native Windows title bar bug, outside-click pass-through via low-level mouse hooking, and an Apple-grade media player with live synced lyrics and direct scrubber control.

However, GLOOM previously carried legacy architectural overhead intended for cross-device mobile pairing ("ecosystem bridge"). By stripping out all mobile ecosystem complexity, GLOOM can refocus 100% of its resources on becoming the definitive, standalone, Apple-grade desktop productivity and sensory overlay for Windows.

This project introduces a **Modular Island Card Engine** and executes three high-impact feature upgrades:
1. **Media Immersion & Sensory Polish**: Multi-mode visualizers (bars, liquid waves, aura), reactive album artwork glow, direct mouse-wheel volume scrubbing, and Web-Audio synthesized tactile clicks.
2. **Productivity Drawer & Window Snapping**: A quick scratchpad/clipboard drawer in the notch, plus visual drop-zone window snapping.
3. **Live Activity Notification System & Legacy Cleanup**: Ephemeral system banners (active timers, download progress) and complete removal of all unused mobile bridge stubs.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **Self-Contained Standalone Architecture**: GLOOM operates entirely independently on Windows with zero cloud, remote bridge, or mobile device dependencies.
- **Modular Island Architecture**: Extract expanded panel rendering from the 4,100-line `App.tsx` into clean, testable card modules under `Desktop/src/components/cards/`.
- **Sensory & Visual Delight**: Support rich visualizer options, artwork-responsive ambient illumination, wheel volume control, and synthesized Apple-like tactile sound feedback.
- **Productivity Acceleration**: Instant access to recent clipboard history and temporary scratchpad notes without needing a separate heavyweight app.
- **Strict Invariant Maintenance**: Maintain 100% region clipping compliance—the window region must **never** be unclipped to `window.innerWidth`, preserving seamless desktop icon click-through and zero caption rendering.

### Non-Goals
- **No Mobile / Android Sync**: We are explicitly removing cross-device clipboard sync, remote device pairing, and Android accessibility bridge protocols.
- **No Heavy External Sound Libraries**: Sound effects must not require downloading massive audio files or external codecs; they will be synthesized instantaneously via the Web Audio API with zero latency.
- **No Heavy Tiling Window Manager Emulation**: Window snapping is lightweight and focused on standard split/maximize actions, not complex multi-monitor automatic tiling rules.

### Anti-Goals
- **No Window Region Creep**: Under no circumstances should any new drawer, snapping overlay, or card set the window region across the full screen width.
- **No Frame Drops or Audio Jitter**: Audio visualizer shaders and sound synthesizers must consume negligible CPU (<1%) and run at a consistent 60fps without hitching.

---

## 3. Approaches Considered & Trade-offs

### Approach 1: Modular Island Card Engine in 3 Staged Milestones (Selected)
- **Structure**: Break the expanded notch into distinct, pluggable cards (`MediaCard`, `ProductivityCard`, `LiveActivityCard`) managed by a unified `IslandCardEngine`.
- **Phasing**:
  - **Milestone 1**: Media Immersion & Sensory Polish (visualizers, album aura, wheel volume, Web Audio micro-clicks).
  - **Milestone 2**: Productivity Drawer (clipboard history, scratchpad notes, window snap targets).
  - **Milestone 3**: Live Activities & Clean-up of legacy bridge code.
- **Pros**: Zero architectural regressions, cleanly tested sub-modules, scalable, and keeps `App.tsx` manageable.
- **Cons**: Requires initial refactoring to decouple card rendering from `App.tsx`.

### Approach 2: Monolithic In-Place Layering (Rejected)
- **Structure**: Directly inject clipboard state, scratchpad inputs, window snap listeners, and Web Audio APIs directly into `App.tsx` and `CompactMediaPlayer.tsx`.
- **Pros**: Faster to start writing immediately.
- **Cons**: `App.tsx` already exceeds 4,100 lines. Compounding state transitions for clipboard, snapping, and media modes in one file risks regressions in window region calculations and focus management.

---

## 4. Technical Specifications & Architecture

### Milestone 1: Media Immersion & Sensory Polish

#### 1. Multi-Mode Audio Visualizer
* **Modes**:
  1. *Classic Phosphor Bars*: 4-5 discrete lime gradient bars (`#30D158` -> `#34C759`) driven by the existing loopback audio frequency monitor.
  2. *Liquid Wave*: Smooth cubic-bezier sine curve responding to low/mid frequency amplitudes.
  3. *Ambient Ring*: Subtle breathing halo border around the squircle album art.
* **Storage & Settings**: Persisted in `localStorage` under `gloom-media-visualizer-style` (`"bars"` | `"wave"` | `"halo"`).

#### 2. Dynamic Artwork Aura
* **Color Extraction**: Canvas-based 4x4 downsampled pixel reading on `albumArtUrl` to extract the dominant vibrant color and warm secondary tint.
* **Backdrop Shader**: Rendered in `.apple-artwork-aura` using `radial-gradient` and CSS blur (`blur(40px) saturate(220%) opacity(0.35)`), transforming the dynamic island into a glowing OLED surface.

#### 3. Wheel Volume Manipulation
* **Interaction**: Hovering over the collapsed Dynamic Island capsule or expanded media player and scrolling the mouse wheel (`onWheel`) adjusts volume up/down by ±2% smoothly, showing the temporary volume HUD overlay without requiring click expansion.

#### 4. Synthesized Apple Tactile Sound Effects (Web Audio API)
* **Design**: Zero-asset audio engine using `AudioContext`.
* **Sound Profiles**:
  - *Snap / Expand*: 800Hz -> 200Hz frequency glide transient, duration 18ms, exponential gain decay.
  - *Release / Tick*: 1200Hz high-frequency sine click, duration 8ms, gain 0.15.
  - *Switch*: Warm 440Hz triangle chirp, duration 12ms.
* **Setting**: Master toggle `gloom-sound-effects-enabled` with volume level slider (default 50%, easily muted).

---

### Milestone 2: Unified Productivity Drawer & Desktop Utilities

#### 1. Unified 3-Tab Productivity Drawer
* **Design & Architecture**:
  - The existing single-purpose "Shelf" evolves into a comprehensive **Unified Productivity Drawer** with three sleek Apple-style segmented tabs:
    1. **Files / Stash (Shelf)**: Existing drag-and-drop file staging, thumbnail previews, and drag-out export.
    2. **Clipboard History**: Tracks the last 15 copied text clips in memory using Win32 `AddClipboardFormatListener`. Features one-click copy, preview tooltip, and search filter.
    3. **Quick Scratchpad**: A persistent plain-text note area with instant autosave to local settings, offering quick thoughts, links, and scratch notes right inside the notch.
* **Safe Keyboard Focus Management**:
  - Main window normally maintains `WS_EX_NOACTIVATE` to prevent focus stealing.
  - When the user focuses the Scratchpad or Clipboard search bar, a dedicated Rust command (`request_notch_keyboard_focus`) temporarily activates the window (`SetForegroundWindow`), then safely returns focus to the previous application HWND upon panel close or escape.
* **Region Safety**: Card width standard 380px, dynamic height 240px–320px depending on tab. Strictly bounded to `pillTargetRef.current`.

#### 2. Native System Harmony (Windows 11)
* To avoid UI collisions and input races with Windows 11's native top-center Snap Bar, GLOOM does not hijack the top-center edge for window dragging. Instead, GLOOM focuses its top-center presence on the fluid Dynamic Island, media controls, and the Unified Productivity Drawer.

---

### Milestone 3: Live Activity System & Standalone Polish

#### 1. Live Activity Notifications
* **Architecture**: Ephemeral notification stack displayed as Apple HIG Live Activity capsules:
  - System timer active countdown (already present, elevated to prominent live activity pill).
  - Browser download progress: Monitored via a low-overhead native directory watcher (`ReadDirectoryChangesW`) on `KNOWNFOLDERID_Downloads` watching `.crdownload` and `.part` file growth across Chrome, Brave, and Edge.
  - Low battery / AC power plug & unplug feedback capsules.
* Each capsule features interactive dismiss or tap-to-expand.

#### 2. Standalone Codebase Clarity
* Maintain local Bluetooth headphone and speaker audio pairing in `bluetooth.rs` at 100% functionality.
* Eliminate dead references and documentation indicating mobile/Android continuity to reflect GLOOM's true identity as a dedicated, standalone Windows desktop application.

---

## 5. Verification & Quality Gates
1. **Compilation**:
   - `npm run build --prefix Desktop` (TypeScript + Vite bundle compiles cleanly with 0 errors).
   - Rust: `cargo check` compiles with 0 warnings.
2. **Unit Tests**:
   - `npm test --prefix Desktop` (all tests pass).
3. **Performance Gate**:
   - CPU usage of audio visualizer + Web Audio synthesizer must remain < 1.0% on standard hardware.
4. **Window Region Invariant**:
   - Window region rects must never expose `(0, 0, window.innerWidth)` or intercept clicks outside the island bounds.
