#  JARVIS Dynamic Island — Apple Design System (DESIGN.md)

> **Design Standard**: Apple Human Interface Guidelines (HIG) • Live Activities & Dynamic Island  
> **Motion Standard**: Emil Kowalski Fluid Interfaces (`apple-design`) • Physical Spring Mechanics  
> **Visual Aesthetic**: True Pitch Black (`#000000`), Liquid Glassmorphism, SF Pro Typography, Specular Hairline Borders

---

## 1. Core Philosophy: Think Like an Apple Developer

Every element, transition, and pixel in the JARVIS Dynamic Island ecosystem is designed according to the core principles formulated by Apple's Human Interface Guidelines and fluid interface engineering:

1. **Kill Latency (Zero Perceptible Delay)**  
   Direct visual and haptic feedback triggers instantly on `pointer-down` / `touch-down` rather than waiting for `pointer-up`. Micro-scales down to `0.98` provide immediate physical confirmation of contact.

2. **Direct Manipulation & Natural Springs**  
   Rigid linear or CSS `ease-in` curves are strictly banned. All transitions use damped harmonic spring physics with real mass, stiffness, and rebound so elements behave like tactile physical objects.

3. **Seamless Hardware Integration (Zero Bleed)**  
   Dynamic Island surfaces blend imperceptibly into device hardware cutouts (MacBook notch, camera punch-hole, Windows desktop top edge) using true pitch OLED black (`#000000`), preventing any light bleed or seam artifacts.

4. **Interruptibility**  
   Any transition can be interrupted at any point in its trajectory without snapping, glitching, or restarting.

---

## 2. Dynamic Island Presentation Hierarchy (Apple HIG)

According to the official [Apple Live Activities HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities), the Dynamic Island operates across three primary presentation modes:

```
┌────────────────────────────────────────────────────────┐
│  [Leading: 20x20 Icon]        [Center: Live Metric]       [Trailing: Waveform]  │  ◄ Compact Presentation
└────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│  ┌───────────┐  Track Title (SF Pro 14pt Bold)                           │
│  │           │  Artist Name (SF Pro 12pt Regular, 60% White)            │
│  │  Artwork  │  ──────────────────────────────────────────               │  ◄ Expanded Presentation
│  │  Squircle │  0:42 ━━━━━━━●───────────────────── -2:18 (Tabular Nums)  │    (Live Activity)
│  └───────────┘  [◄◄]        [ ▶ / ❚❚ ]        [►►]        [AirPlay]      │
│  ──────────────────────────────────────────────────────────────────────  │
│  [Dashboard] [Media] [Bridge] [Controls] [Bluetooth] [Timer] [Stats] [Net]│
└──────────────────────────────────────────────────────────────────────────┘
```

### A. Compact Presentation
- **Leading Element**: Instant context identifier (Album artwork thumbnail `18x18px` with `5px` rounded corners, or glowing green/amber battery ring).
- **Center Capsule**: Recessed pill housing tabular live clock or focus timer countdown.
- **Trailing Element**: Dynamic live state metric (Real-time animated audio equalizer bars, WiFi transfer activity, or battery percentage).
- **Physical Bounds**: `180px - 240px` width, `34px - 38px` height, continuous squircle radius `17px - 19px`.

### B. Minimal Presentation
- Displays in detached split format when concurrent background activities run.

### C. Expanded Presentation (Live Activity)
- Unfolds fluidly from the pill with an organic spring rebound (`stiffness: 260, damping: 20`).
- Album artwork rendered with Apple squircle continuous curvature (`16px`).
- Live scrubber bar with precision seeking, elapsed and remaining time in SF Pro Tabular figures (`tnum`).
- High-craft playback controls with Apple SF Symbols aesthetic.
- Glassmorphic navigation capsule switching cleanly between LiveBridge Phone Companion, Hardware Stats, Network speeds, and System controls.

---

## 3. Materials & Glassmorphism Tokens

| Material Token | Description | CSS / Compose Spec |
|:---|:---|:---|
| `apple-pitch-black` | True OLED base (island body) | `#000000` |
| `apple-glass-surface` | Expanded panel background | `rgba(13, 14, 20, 0.88)` with `backdrop-filter: blur(32px) saturate(190%)` |
| `apple-specular-rim` | Specular inner hairline edge | `inset 0 0.5px 0.5px 0 rgba(255, 255, 255, 0.28), inset 0 -0.5px 0.5px 0 rgba(0, 0, 0, 0.6)` |
| `apple-border` | Hairline outer boundary | `0.5px solid rgba(255, 255, 255, 0.14)` |
| `apple-island-shadow` | Ambient depth drop shadow | `0 24px 50px -4px rgba(0, 0, 0, 0.95), 0 8px 24px -2px rgba(0, 0, 0, 0.8)` |
| `apple-glow-accent` | Subtle active aura | `0 0 16px rgba(0, 210, 255, 0.35)` |

---

## 4. Typography System (San Francisco Pro)

- **Primary Font Stack**:
  ```css
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "SF Pro", system-ui, sans-serif;
  ```
- **Monospace & Telemetry**:
  ```css
  font-family: "SF Mono", Menlo, Consolas, Monaco, monospace;
  font-feature-settings: "tnum" 1;
  font-variant-numeric: tabular-nums;
  ```
- **Optical Tracking (Letter-Spacing)**:
  - Display / Large Titles (≥ 20px): `-0.025em` to `-0.035em`
  - Subheadings (14px - 18px): `-0.015em`
  - Body Text (11px - 13px): `-0.005em`
  - Micro Badges & Caps (≤ 10px): `+0.04em` (high legibility)

---

## 5. Apple Color Palette

| Token | Hex | Role |
|:---|:---|:---|
| `SystemBlue` | `#007AFF` | Primary links, selections, info |
| `SystemCyan` | `#32ADE6` | JARVIS primary accent & LiveBridge state |
| `SystemGreen` | `#34C759` | Battery charging, connected status, audio wave |
| `SystemOrange` | `#FF9500` | Timers, focus sessions, active countdowns |
| `SystemPurple` | `#AF52DE` | Pro features, creative tools |
| `SystemRed` | `#FF3B30` | Mute, disconnect, critical alerts |
| `SystemGray1` | `#1C1C1E` | Card backgrounds |
| `SystemGray2` | `#2C2C2E` | Secondary container fills |
| `SystemGray6` | `rgba(255, 255, 255, 0.08)` | Interactive button capsules |

---

## 6. Motion Physics (Emil Kowalski `apple-design` Spec)

```typescript
export const MOTION_PHYSICS = {
  // Snappy, organic expansion
  expand: {
    type: "spring",
    stiffness: 260,
    damping: 20,
    mass: 0.8,
  },
  // Bouncy, smooth collapse with momentum settle
  collapse: {
    type: "spring",
    stiffness: 165,
    damping: 14,
    mass: 0.95,
  },
  // Direct touch/click response
  tapFeedback: {
    scale: 0.98,
    transition: { duration: 0.08 },
  },
  // Seamless tab indicator slider
  tabSwitch: {
    type: "spring",
    stiffness: 480,
    damping: 30,
    mass: 0.55,
  },
};
```

---

## 7. Motorola Edge 50 Pro Hardware Calibration & Grounding

The mobile dynamic island is calibrated with pixel-perfection for the **Motorola Edge 50 Pro**:

- **Display Dimensions**: 1220 × 2712 px (6.7-inch pOLED, 446 PPI, 144 Hz)
- **Active Area**: 69.8 mm × 155.1 mm (Density Factor: ~`2.7875` dp scale)
- **Punch-Hole Physical Position**:
  - Horizontal Center: `610 px` (exactly 50.0% dead center across 1220 px display)
  - Vertical Center: `52 px` (~`18.6 dp` from active screen top edge)
  - Physical Hole Diameter: `48 px` to `52 px` (~`17.2 dp` to `18.6 dp`)
- **Dynamic Island Placement**:
  - `topMargin`: `2 px` (per user specification, island starts at `2 px`)
  - `pillHeight`: `38 dp` (~`106 px`), enveloping the punch hole from `Y = 5.5 px` to `Y = 111.5 px`
  - `cutoutClearance`: `32 dp` dead-zone center gap, protecting physical optics with `7 dp` pitch-black margins on both sides
  - `pillWidth`: `184 dp`, providing balanced leading and trailing status wings across the `437 dp` display
  - `expandedDimensions`: `380 dp × 260 dp`, fitting within the screen with `28.5 dp` side gutters

