# JARVIS — Design System & Interaction Architecture (DESIGN.md)

> **Design Paradigm**: Apple-inspired Dynamic Island-style overlay & glanceable activity surface  
> **Motion Standard**: Spring physics and reduced-motion linear transitions  
> **Visual Aesthetic**: OLED pitch black (`#000000`), glassmorphic panels, specular hairline borders, high-contrast fallbacks

---

## 1. Product Positioning & Design Standards

JARVIS draws inspiration from modern glanceable interface designs—notably Apple's Dynamic Island and Live Activities—and adapts them to the technical realities of Windows desktop and Android mobile environments.

### Specification Delineation
1. **Official Apple Guidance**: Applicable strictly as a reference point for genuine Apple platforms (iOS/iPadOS) implementing ActivityKit and WidgetKit.
2. **JARVIS Design Tokens**: Cross-platform design tokens defined in this repository for pitch black surfaces, functional glass, specular borders, and semantic typography.
3. **Windows Desktop Adaptations**: Borderless layered window with DPI awareness, work area (`rcWork`) constraints, negative multi-monitor coordinates, and fullscreen suppression.
4. **Android Cutout-Aware Adaptations**: Hardware camera cutout integration using `WindowInsets.getDisplayCutout()`, non-blocking touch geometry, and TalkBack accessibility.
5. **Future iOS Architecture**: A potential future Swift target using genuine ActivityKit/WidgetKit APIs.

---

## 2. Cross-Platform Presentation State Model

The JARVIS interface is driven by a 13-state semantic presentation machine:

```
┌────────────────────────────────────────────────────────┐
│  [Leading: 20x20 Icon]        [Center: Live Metric]       [Trailing: Waveform]  │  ◄ Compact (36px / 38dp)
└────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│  [Leading: Icon/Art]    Title & Subtitle Glance         [Trailing: Stat] │  ◄ Peek (44px / 44dp)
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│  ┌───────────┐  Track Title (Primary 14pt Semibold)                      │
│  │           │  Artist Name (Secondary 12pt, 70% White)                  │
│  │  Artwork  │  ──────────────────────────────────────────               │  ◄ Expanded Activity
│  │  Squircle │  0:42 ━━━━━━━●───────────────────── -2:18 (Tabular Nums)  │
│  └───────────┘  [◄◄]        [ ▶ / ❚❚ ]        [►►]        [Output]       │
│  ──────────────────────────────────────────────────────────────────────  │
│  [Dashboard] [Media] [Bridge] [Controls] [Bluetooth] [Timer] [Stats] [Net]│
└──────────────────────────────────────────────────────────────────────────┘
```

### Semantic States
1. **`idle` / `compact`**: Base resting state. Compact pill displaying clock, battery status, or minimal active indicator.
2. **`peek`**: Transient 2.5-second glanceable card (320×44 px) displaying new song titles, timer alerts, or file transfer events.
3. **`minimal`**: Auxiliary circular or pill badge for secondary concurrent tasks.
4. **`expandedActivity`**: Rich interactive card with playback scrubber, volume slider, and tactile transport controls.
5. **`expandedControl`**: Multi-tab system dashboard (Hardware telemetry, Network speeds, Notepad, Bluetooth, Settings).
6. **`loading`**: Subtle animated loading shimmer for asynchronous requests.
7. **`reconnecting`**: Amber status badge during LiveBridge reconnection attempts.
8. **`error`**: Red specular rim and alert message for failed transfers or validation issues.
9. **`completed`**: Green confirmation badge for completed file transfers or timer completions.
10. **`dismissed`**: User-collapsed state returning to compact presentation.
11. **`expired`**: Timer or notification timeout transition.
12. **`suppressed`**: Auto-hidden when a full-screen application or presentation is foregrounded.

---

## 3. Materials & Design Tokens

JARVIS implements a unified token system mapped via CSS custom properties and Jetpack Compose tokens:

| Token | CSS Variable | Fallback Spec | Usage |
|:---|:---|:---|:---|
| Pitch Black | `--island-pitch-black` | `#000000` | Base compact pill and camera cutout cover |
| Functional Glass | `--island-glass-bg` | `rgba(13, 14, 20, 0.94)` | Expanded panel with 32px blur & 190% saturation |
| Opaque Surface | `--island-glass-opaque`| `#0d0e14` | High-contrast and reduced-transparency fallback |
| Hairline Border | `--island-border` | `rgba(255, 255, 255, 0.14)` | Outer subtle perimeter |
| Specular Rim | `--island-border-specular` | `rgba(255, 255, 255, 0.28)` | Inset top specular highlight |
| Focus Ring | `--island-focus-ring` | `#007aff` | 2px accessible keyboard focus outline |
| Divider | `--island-divider` | `rgba(255, 255, 255, 0.08)` | Section separation lines |

### High Contrast & Accessibility
- High contrast modes replace translucent glass with solid opaque `#0d0e14` and increase border visibility to `rgba(255, 255, 255, 0.4)`.
- All controls feature `:focus-visible` outlines (`2px solid var(--island-focus-ring)` with `2px` offset).

---

## 4. Typography System

To ensure cross-platform compatibility and avoid proprietary Apple font licensing issues on Windows and Android, JARVIS utilizes native platform typography stacks:

### Font Stacks
- **Windows Desktop**:
  ```css
  font-family: "Segoe UI Variable Text", "Segoe UI", -apple-system, BlinkMacSystemFont, Roboto, system-ui, sans-serif;
  ```
- **Android Mobile**:
  ```kotlin
  FontFamily.Default // Uses Android system fonts (Roboto, Roboto Flex)
  ```
- **Monospace & Telemetry**:
  ```css
  font-family: Consolas, "SF Mono", Menlo, Monaco, monospace;
  font-variant-numeric: tabular-nums;
  ```

---

## 5. Color Tokens

| Token | Value | Role |
|:---|:---|:---|
| Primary Accent | `#007AFF` | Primary links, keyboard focus rings, active states |
| Bridge Cyan | `#32ADE6` | LiveBridge status and file transfer indicators |
| Activity Green | `#34C759` | Battery charging indicator, connected status, audio waveforms |
| Timer Orange | `#FF9500` | Pomodoro timers, focus countdowns, alerts |
| Alert Red | `#FF3B30` | Disconnect warnings, error states, exit buttons |
| Primary Text | `#FFFFFF` | Headings, active values, high-contrast labels |
| Secondary Text| `rgba(255, 255, 255, 0.72)` | Subtitles, artists, descriptions |
| Tertiary Text | `rgba(255, 255, 255, 0.45)` | Timestamps, muted details, unit labels |

---

## 6. Motion & Physics Engine

JARVIS transitions use natural spring physics with an instant reduced-motion alternative:

```typescript
export const MOTION_PHYSICS = {
  // Snappy expansion
  expand: {
    type: "spring",
    stiffness: 260,
    damping: 20,
    mass: 0.8,
  },
  // Bouncy collapse
  collapse: {
    type: "spring",
    stiffness: 165,
    damping: 14,
    mass: 0.95,
  },
  // Transient peek glance
  peek: {
    type: "spring",
    stiffness: 500,
    damping: 26,
    mass: 0.5,
  },
  // Reduced-motion accessible alternative
  reducedMotion: {
    duration: 0.08,
    ease: "linear",
  },
};
```

---

## 7. Platform Integration Contracts

### Windows Desktop
- **Monitor Placement**: Query `GetMonitorInfoW` and align strictly within `rcWork` to prevent overlapping top-docked taskbars.
- **Negative Coordinates**: Supports secondary monitors placed to the left or above the primary monitor.
- **Hot-Plug Recovery**: Re-evaluates monitor bounds when display geometry changes.
- **Full-Screen Suppression**: Automatically suppresses the island overlay when games or presentation apps enter foreground fullscreen mode.

### Android Mobile
- **Live DisplayCutout**: Binds to `WindowInsets.getDisplayCutout()` to dynamically detect punch-holes and notches across vendors.
- **Bounded Overlay Geometry**: Ensures the expanded card does not exceed `dpToPx(expandedHeight + positionY + 24)`, allowing unobstructed touch access to underlying applications.
- **TalkBack Semantics**: Full accessibility semantics, custom click labels, and high-contrast support.
