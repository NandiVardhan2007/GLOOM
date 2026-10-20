# Visual & UX Analysis: Project jarvis

## 1. Design Philosophy

The visual ambition of **Project jarvis** is to create a Windows Dynamic Island that looks and feels like a native hardware-software fusion, elevating the Windows 11 Fluent Design language with Apple-grade micro-interactions, squircle curvature, and liquid glass optics.

---

## 2. Geometry & Proportions

### 2.1 Collapsed State (Idle)
jarvis provides two distinct visual personas to accommodate both bezel-flush designs and modern bezel-less monitors:

#### Variant A: Floating Dynamic Island (Pill Mode - Default for Windows)
- **Top Offset**: `12px` below the display's top edge (clear of physical camera bezels or display margins).
- **Resting Width**: `210px`.
- **Resting Height**: `34px`.
- **Corner Radius**: `17px` (full continuous capsule curve).
- **Visual Silhouette**: Jet black pill (`#000000`) with a subtle 1px border stroke (`rgba(255, 255, 255, 0.12)`) and a soft ambient drop shadow (`0 8px 24px rgba(0, 0, 0, 0.45)`).
- **Internal Elements**:
  - Left wing: Mini status indicator (e.g., current time `14:25` or compact app icon).
  - Center: Hardware camera / privacy dot (optional) or balanced divider.
  - Right wing: Dynamic activity glyph (e.g., mini 6-bar audio visualizer or battery indicator).

#### Variant B: Dynamic Notch (Flush Top Bezel Mode)
- **Top Offset**: `0px` (pinned to screen top).
- **Resting Width**: `190px`.
- **Resting Height**: `32px`.
- **Top Shoulder Flares**: Concave quadratic bezier curves (`radius: 8px`) flaring horizontally into the screen's top bezel.
- **Bottom Corner Radius**: `14px` convex rounded bottom.

### 2.2 Peek State (Hover Micro-Swell)
Derived from Glance's `.peek` state:
- When the pointer crosses into the island's hover bounds, the island swells slightly by `+12px` width and `+4px` height (`width: 222px, height: 38px`).
- Animation: Snappy micro-spring (`stiffness: 400, damping: 28`).
- Communicates responsiveness ("I see you") before the user clicks to expand.

### 2.3 Expanded State (Dashboard & Cards)
- **Expanded Width**: `560px` (comfortably fits media controls, scrubbing slider, track details, and system gauges).
- **Expanded Height**:
  - Compact Card (Media / Volume): `196px`.
  - Full Multi-Widget Dashboard: `230px`.
  - File Shelf / Clipboard mode: `260px`.
- **Expanded Corner Radius**: `24px` continuous squircle curvature.
- **Top Anchor Stability**: The top anchor remains fixed at `12px` (or `0px` in notch mode). The island expands downwards and symmetrically outwards from the horizontal center point, eliminating any perceived horizontal shift.

---

## 3. Optical Physics & Liquid Glass Material

### 3.1 Background Material Specification
Windows 11 Mica and Acrylic effects are often either too opaque or excessively grainy. jarvis implements a multi-layer composition:
1. **Backdrop Blur**: `backdrop-filter: blur(28px) saturate(180%)`.
2. **Glass Base**: Deep obsidian tint (`rgba(15, 15, 18, 0.82)`).
3. **Specularity & Inner Glow**:
   - `box-shadow: inset 0 1px 1px 0 rgba(255, 255, 255, 0.18), inset 0 -1px 1px 0 rgba(0, 0, 0, 0.4), 0 16px 40px rgba(0, 0, 0, 0.6)`.
4. **Border Stroke**: Clean, crisp sub-pixel border: `1px solid rgba(255, 255, 255, 0.10)`.

### 3.2 High-Contrast & Accessibility
- In accordance with WCAG 2.1 AA and Windows Accessibility settings:
  - When "Reduce Transparency" is detected or enabled in settings, background shifts to pure opaque matte black (`#0f0f12`) with white icons (`#ffffff`) and high contrast borders (`rgba(255, 255, 255, 0.3)`).

---

## 4. Typography & Iconography

### 4.1 Typeface Stack
- Primary: `"Segoe UI Variable Text"`, `system-ui`, `-apple-system`, `sans-serif`.
- Numeric Gauges / Timers: `"Segoe UI Variable Display"` with `font-variant-numeric: tabular-nums` to eliminate layout jitter during active time and counter increments.

### 4.2 Hierarchy & Scales
| Level | Font Size | Weight | Tracking | Color |
| :--- | :--- | :--- | :--- | :--- |
| **Track Title / Header** | `15px` | 600 (Semibold) | `-0.015em` | `#ffffff` |
| **Artist / Secondary** | `12px` | 400 (Regular) | `0em` | `rgba(255, 255, 255, 0.65)` |
| **Collapsed Pill Status**| `11px` | 600 (Semibold) | `0.01em` | `rgba(255, 255, 255, 0.90)` |
| **Timestamps / Badges**  | `10px` | 500 (Medium) | `0.02em` | `rgba(255, 255, 255, 0.50)` |
| **Metric Percentages**   | `12px` | 700 (Bold) | `-0.01em` | `#ffffff` |

### 4.3 Icon System
- Standard: Pure inline SVG vectors with consistent `1.5px` or `2.0px` stroke weight, rounded stroke caps (`stroke-linecap="round"`), and square 16x16 / 20x20 viewboxes.
- Emoji are strictly forbidden for core UI buttons, status indicators, and tabs.

---

## 5. Motion Architecture & Spring Curves

```
Motion Lifecycle Curve:
Collapsed (210x34) ──[Hover Peek]──> Peek (222x38) ──[Click/Expand]──> Expanded (560x220)
       ▲                                                                       │
       └─────────────────────────[Collapse]───────────────────────────────────┘
```

### 5.1 Physics Coefficients
Based on reverse-engineering analysis of Glance and Apple Dynamic Island specifications:

```typescript
export const MOTION_SPECS = {
  // Main open/close morphing transition
  morph: {
    type: "spring",
    stiffness: 340,
    damping: 30, // critically damped (zeta ~= 1.0)
    mass: 0.8,
  },
  // Quick micro-interactions (peek, hover)
  peek: {
    type: "spring",
    stiffness: 420,
    damping: 26,
    mass: 0.6,
  },
  // Pop alert notifications (volume change, battery low, new track)
  alertPop: {
    type: "spring",
    stiffness: 300,
    damping: 20, // slightly underdamped for pleasant organic pop
    mass: 0.7,
  },
  // Content cross-fade
  fade: {
    duration: 0.18,
    ease: [0.16, 1, 0.3, 1], // ease-out cubic
  },
  // Reduced motion fallback
  reducedMotion: {
    duration: 0.12,
    ease: "linear",
  }
};
```

---

## 6. Layout Stability & Anti-Jitter Engineering

To prevent visual layout jumps during runtime updates:
1. **Marquee Text Containers**: Fixed container width with CSS gradient mask fades (`mask-image: linear-gradient(90deg, transparent 0, #000 12px, #000 calc(100% - 12px), transparent 100%)`). Text wraps into horizontal animation only when content width exceeds container width.
2. **Tabular Numerics**: `tabular-nums` ensures that time progress counters (`01:09` -> `01:10`) maintain identical pixel width.
3. **Artwork Aspect Ratio Locking**: Album art containers are rigidly sized (`48x48px` or `64x64px`) with `border-radius: 12px` and smooth CSS opacity fade-in on load, avoiding reflow while images decode.
4. **Equalizer Waveform Bars**: 6 bars computed with fixed widths and gaps, clamped to `min-height: 3px` and `max-height: 18px`, preventing height collapse.
