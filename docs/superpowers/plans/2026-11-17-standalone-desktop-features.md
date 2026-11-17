# Standalone Desktop Evolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform GLOOM into a standalone, hyper-polished Windows desktop productivity and sensory overlay featuring a modular Dynamic Island card engine, multi-mode visualizers, artwork aura, Web Audio tactile sound effects, wheel volume scrubbing, a unified 3-tab productivity drawer (Files, Clipboard, Scratchpad), and live activities.

**Architecture:** A modular React 19 + TypeScript + Framer Motion card system inside the Dynamic Island connected to a lightweight Tauri v2 + Win32 Rust backend. Win32 provides driverless clipboard listening, temporary keyboard focus toggling, and downloads folder watching while the frontend synthesizes audio transients via the Web Audio API and renders reactive OLED backdrops.

**Tech Stack:** React 19, TypeScript, Framer Motion, Web Audio API, Canvas 2D API, Tauri v2, Rust (windows-rs 0.61, Win32 API).

**Spec:** [docs/superpowers/specs/2026-11-17-standalone-desktop-features-design.md](file:///d:/Coding/GLOOM/docs/superpowers/specs/2026-11-17-standalone-desktop-features-design.md)

## Global Constraints

- **Window Region Invariant**: The window region must **never** be set to full monitor width `(0, 0, window.innerWidth)`. Every card and drawer must bound its region strictly to `pillTargetRef.current` plus measured child bounds.
- **Title Bar Suppression**: Win32 window subclass in `utils.rs` returning 0 for `WM_NCCALCSIZE` and `WM_NCPAINT` must remain active under all focus and activation transitions.
- **Zero Heavy Audio Assets**: Auditory feedback must be synthesized via native Web Audio API transients (<20ms duration) with zero network or disk audio file dependencies.
- **Low CPU Consumption**: Background threads and visualizer animations must consume < 1.0% CPU on standard hardware.
- **Non-Breaking Dual Platform**: Linux command stubs must be maintained for any newly registered Tauri commands.

## Review Focus

1. **Clipboard History Memory & Privacy Leak**: Copying sensitive data or 100MB files into the clipboard must not cause high memory consumption; limit entries strictly to text under 64KB and cap list at 15 items.
2. **Keyboard Focus Deadlock on Scratchpad**: Toggling `WS_EX_NOACTIVATE` to type into the Scratchpad must reliably restore focus to the previous application window when closed or clicked outside.
3. **Download Monitor False Alarms**: The Downloads directory watcher must only trigger for active `.crdownload` or `.part` files, never for finished downloads or arbitrary file creation.
4. **Web Audio Autoplay Policy**: Web Audio `AudioContext` created in background before user interaction must be lazily resumed on the first user gesture without throwing DOM exceptions.
5. **Mouse Wheel Event Propagation**: Wheel volume scrubbing over the notch must call `e.preventDefault()` / `e.stopPropagation()` so underlying application scrollbars are not scrolled.

---

### Task 1: Web Audio Apple Tactile Sound Engine

**Files:**
- Create: `Desktop/src/utils/audioFeedback.ts`
- Test: `Desktop/tests/audioFeedback.test.mjs`
- Modify: `Desktop/src/App.tsx:1000-1030`

**Interfaces:**
- Produces:
  ```ts
  export type SoundFeedbackType = "snap" | "tick" | "switch" | "pop";
  export function playTactileSound(type: SoundFeedbackType): void;
  export function setSoundFeedbackEnabled(enabled: boolean): void;
  export function setSoundFeedbackVolume(volume: number): void;
  ```

- [ ] **Step 1: Write unit tests for audioFeedback configuration and synthesizer logic**
  - Verify sound types, volume bounds (0.0 to 1.0), and mute toggle.
- [ ] **Step 2: Run tests to verify failure**
  - Run: `node --test Desktop/tests/audioFeedback.test.mjs`
- [ ] **Step 3: Implement `audioFeedback.ts` using lazy Web Audio `AudioContext`**
  - Implement synthesis of `snap` (800Hz -> 200Hz glide, 18ms), `tick` (1200Hz, 8ms), and `switch` (440Hz triangle, 12ms) with exponential decay gain envelopes.
- [ ] **Step 4: Run test to verify it passes**
  - Run: `node --test Desktop/tests/audioFeedback.test.mjs`
- [ ] **Step 5: Connect sounds to island expansion and scrubber release in `App.tsx`**
- [ ] **Step 6: Commit**
  ```bash
  git add Desktop/src/utils/audioFeedback.ts Desktop/tests/audioFeedback.test.mjs Desktop/src/App.tsx
  git commit -m "feat(audio): add Web Audio synthesized tactile sound feedback engine"
  ```

---

### Task 2: Multi-Mode Audio Visualizer Engine

**Files:**
- Modify: `Desktop/src/App.tsx:295-325` (Visualizer component)
- Modify: `Desktop/src/App.css:1340-1380`
- Modify: `Desktop/src/CompactMediaPlayer.tsx:480-510`

**Interfaces:**
- Consumes: `isPlaying: boolean`, loopback audio frequency monitor.
- Produces:
  ```ts
  export type VisualizerMode = "bars" | "wave" | "halo";
  export function Visualizer({ isPlaying, mode, height, bars }: VisualizerProps): JSX.Element;
  ```

- [ ] **Step 1: Implement `wave` mode (SVG sine wave animation reacting to amplitude) and `halo` mode (radial pulsing glow)**
- [ ] **Step 2: Add mode toggle on click of visualizer pill (`bars` -> `wave` -> `halo`)**
- [ ] **Step 3: Persist choice in `localStorage` under `gloom-visualizer-mode`**
- [ ] **Step 4: Verify build and render**
  - Run: `npm run build --prefix Desktop`
- [ ] **Step 5: Commit**
  ```bash
  git add Desktop/src/App.tsx Desktop/src/App.css Desktop/src/CompactMediaPlayer.tsx
  git commit -m "feat(media): implement multi-mode audio visualizers (bars, liquid wave, halo)"
  ```

---

### Task 3: Dynamic Album Artwork Aura & Color Extractor

**Files:**
- Create: `Desktop/src/utils/colorExtractor.ts`
- Modify: `Desktop/src/App.css:2840-2860` (`.apple-artwork-aura`)
- Modify: `Desktop/src/CompactMediaPlayer.tsx:425-450`

**Interfaces:**
- Produces:
  ```ts
  export interface DominantPalette {
    primary: string;
    secondary: string;
    accent: string;
  }
  export function extractDominantPalette(imageUrl: string): Promise<DominantPalette>;
  ```

- [ ] **Step 1: Implement fast canvas pixel downsampling (4x4 grid) in `colorExtractor.ts` with memoization cache**
- [ ] **Step 2: Update `.apple-artwork-aura` to apply rich dual-radial gradients with extracted vibrant colors**
- [ ] **Step 3: Connect palette updates to album art change events**
- [ ] **Step 4: Verify clean Vite build**
  - Run: `npm run build --prefix Desktop`
- [ ] **Step 5: Commit**
  ```bash
  git add Desktop/src/utils/colorExtractor.ts Desktop/src/App.css Desktop/src/CompactMediaPlayer.tsx
  git commit -m "feat(media): dynamic album art aura glow with canvas color sampling"
  ```

---

### Task 4: Wheel Volume Manipulation on Collapsed Capsule & Expanded Player

**Files:**
- Modify: `Desktop/src/App.tsx:2780-2820`
- Modify: `Desktop/src/CompactMediaPlayer.tsx:430-460`

**Interfaces:**
- Consumes: `onVolumeChange(newVolume: number)`, `playTactileSound("tick")`

- [ ] **Step 1: Attach `onWheel` listener on `.gloom` pill and `.apple-media-container`**
- [ ] **Step 2: Smoothly adjust volume by ±0.03 delta with tactile `tick` sound feedback**
- [ ] **Step 3: Call `e.stopPropagation()` and `e.preventDefault()` to prevent desktop background scrolling**
- [ ] **Step 4: Verify volume HUD overlay triggers cleanly without layout jumps**
  - Run: `npm run build --prefix Desktop`
- [ ] **Step 5: Commit**
  ```bash
  git add Desktop/src/App.tsx Desktop/src/CompactMediaPlayer.tsx
  git commit -m "feat(interaction): add mouse wheel volume scrubbing with tactile audio feedback"
  ```

---

### Task 5: Win32 Clipboard History Listener & Rust Backend

**Files:**
- Modify: `Desktop/src-tauri/src/services.rs:200-300`
- Modify: `Desktop/src-tauri/src/commands.rs:4700-4730`
- Modify: `Desktop/src-tauri/src/platform/linux/commands.rs`
- Modify: `Desktop/src-tauri/src/main.rs:300-320`

**Interfaces:**
- Produces:
  ```rust
  #[tauri::command]
  pub fn get_clipboard_history() -> Result<Vec<ClipboardItem>, String>;
  #[tauri::command]
  pub fn clear_clipboard_history() -> Result<(), String>;
  ```

- [ ] **Step 1: Add Win32 `AddClipboardFormatListener` to message loop in `services.rs`**
- [ ] **Step 2: Capture text clips under 64KB, maintaining a thread-safe ring buffer of the last 15 items**
- [ ] **Step 3: Expose `get_clipboard_history` and `clear_clipboard_history` commands in `commands.rs` (with Linux stubs)**
- [ ] **Step 4: Register commands in `main.rs`**
- [ ] **Step 5: Commit**
  ```bash
  git add Desktop/src-tauri/src/services.rs Desktop/src-tauri/src/commands.rs Desktop/src-tauri/src/platform/linux/commands.rs Desktop/src-tauri/src/main.rs
  git commit -m "feat(clipboard): add Win32 clipboard format listener & history ring buffer"
  ```

---

### Task 6: Win32 Dynamic Keyboard Focus Management for Scratchpad

**Files:**
- Modify: `Desktop/src-tauri/src/commands.rs`
- Modify: `Desktop/src-tauri/src/platform/linux/commands.rs`
- Modify: `Desktop/src-tauri/src/main.rs`

**Interfaces:**
- Produces:
  ```rust
  #[tauri::command]
  pub fn request_notch_keyboard_focus(focus: bool) -> Result<(), String>;
  ```

- [ ] **Step 1: Implement `request_notch_keyboard_focus(focus: bool)` in `commands.rs`**
  - When `focus` is true: Store current foreground window (`GetForegroundWindow()`), temporarily toggle `WS_EX_NOACTIVATE` off, and call `SetForegroundWindow(main_hwnd)`.
  - When `focus` is false: Restore `WS_EX_NOACTIVATE` and restore foreground focus to stored HWND.
- [ ] **Step 2: Add Linux stub in `platform/linux/commands.rs`**
- [ ] **Step 3: Register command in `main.rs`**
- [ ] **Step 4: Commit**
  ```bash
  git add Desktop/src-tauri/src/commands.rs Desktop/src-tauri/src/platform/linux/commands.rs Desktop/src-tauri/src/main.rs
  git commit -m "feat(win32): implement dynamic keyboard focus switching for scratchpad"
  ```

---

### Task 7: Unified Productivity Drawer Component (Files, Clipboard, Scratchpad)

**Files:**
- Create: `Desktop/src/components/cards/ProductivityDrawer.tsx`
- Create: `Desktop/src/components/cards/ProductivityDrawer.css`
- Modify: `Desktop/src/App.tsx:3000-3050`

**Interfaces:**
- Consumes: Shelf items, `get_clipboard_history`, `request_notch_keyboard_focus`.
- Produces:
  ```tsx
  export function ProductivityDrawer({
    activeTab,
    onTabChange,
    onClose,
    shelfItems,
    ...
  }: ProductivityDrawerProps): JSX.Element;
  ```

- [ ] **Step 1: Build Apple-style segmented tabs (`Files`, `Clipboard`, `Scratchpad`)**
- [ ] **Step 2: Tab 1: Embed existing file shelf tiles, previews, and drag-out exports**
- [ ] **Step 3: Tab 2: Render clipboard history with one-click copy, timestamp, and clear button**
- [ ] **Step 4: Tab 3: Render persistent scratchpad textarea with autosave to localStorage and `request_notch_keyboard_focus` on focus/blur**
- [ ] **Step 5: Mount `ProductivityDrawer` in `App.tsx` for mode `"shelf"` / `"productivity"`**
- [ ] **Step 6: Verify build and tests**
  - Run: `npm test --prefix Desktop` && `npm run build --prefix Desktop`
- [ ] **Step 7: Commit**
  ```bash
  git add Desktop/src/components/cards/ProductivityDrawer.tsx Desktop/src/components/cards/ProductivityDrawer.css Desktop/src/App.tsx
  git commit -m "feat(ui): implement unified 3-tab productivity drawer (files, clipboard, scratchpad)"
  ```

---

### Task 8: Live Activity System & Browser Download Watcher

**Files:**
- Create: `Desktop/src/components/cards/LiveActivityBanner.tsx`
- Modify: `Desktop/src-tauri/src/services.rs`
- Modify: `Desktop/src/App.tsx`

**Interfaces:**
- Consumes: Background `.crdownload`/`.part` watcher events.
- Produces:
  ```tsx
  export interface LiveActivityItem {
    id: string;
    type: "download" | "timer" | "battery";
    title: string;
    progress?: number;
    detail: string;
  }
  ```

- [ ] **Step 1: Implement low-overhead directory watcher on `Downloads` folder detecting active partial downloads**
- [ ] **Step 2: Emit `live-activity-update` from Rust to frontend**
- [ ] **Step 3: Render interactive Live Activity floating capsule in Dynamic Island**
- [ ] **Step 4: Verify build and test suite**
  - Run: `npm run build --prefix Desktop`
- [ ] **Step 5: Commit**
  ```bash
  git add Desktop/src/components/cards/LiveActivityBanner.tsx Desktop/src-tauri/src/services.rs Desktop/src/App.tsx
  git commit -m "feat(notifications): add live activity banner system & browser download watcher"
  ```

---

### Task 9: Standalone Codebase & Documentation Cleanup

**Files:**
- Modify: `README.md`
- Modify: `HANDOFF.md`
- Modify: `Desktop/src/components/settings/SettingsView.tsx`

- [ ] **Step 1: Remove all outdated references to Android bridge / mobile continuity from README and Settings**
- [ ] **Step 2: Verify all 8/8 JS tests pass**
  - Run: `npm test --prefix Desktop`
- [ ] **Step 3: Run complete Vite production build**
  - Run: `npm run build --prefix Desktop`
- [ ] **Step 4: Update `HANDOFF.md` with standalone 2.4.0 roadmap**
- [ ] **Step 5: Commit**
  ```bash
  git add README.md HANDOFF.md Desktop/src/components/settings/SettingsView.tsx
  git commit -m "chore: purge mobile bridge references and finalize standalone desktop identity"
  ```
