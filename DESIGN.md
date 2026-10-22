# JARVIS — Design System & Interaction Architecture

> **Design paradigm**: Apple Dynamic Island + Live Activities, adapted to Windows and Android
> **Reference**: Apple Human Interface Guidelines — Live Activities (October 2026)
> **Surface**: opaque `#000000` island, SF-metric typography on the platform system face
> **Motion**: response/damping springs, critically damped by default

---

## 1. Specification delineation

1. **Apple HIG** is the source of truth for geometry, typography, materials and motion.
2. **JARVIS tokens** adapt those values to a desktop overlay and an Android cutout.
3. **No Apple assets are bundled.** SF Pro, SF Symbols and Apple motion curves are
   proprietary; JARVIS uses the platform system font and an original SF-style vector
   set drawn on the same 24 pt grid with 1.8 pt rounded strokes.
4. Apple's exact values are used where they translate directly: island sizes, camera
   band, corner radius, content margins, artwork size, spring response and damping.

---

## 2. Geometry (Apple HIG, iOS 393 × 852 class)

| Presentation | Size (pt/dp) | Radius |
|---|---|---|
| Island at rest | 126 × 36.67 | capsule (18.335) |
| Compact leading / trailing | 52.33 × 36.67 each | capsule |
| Camera band | 125.34 × 36.67 | — |
| Compact total (Mobile) | 180 × 36.67 | capsule (18.335) |
| Compact total (Desktop) | 230 × 36.67 | capsule (18.335) |
| Minimal | 36.67–45 × 36.67 | capsule |
| Expanded pill | 400 × 180 | 36 (continuous squircle) |
| Content margin | 14 | — |
| Expanded artwork | 44 | 10 |
| Compact artwork | 22 | 6.5 |

The island is always opaque black. It cannot be tinted. In dark appearance a key line
is drawn around the expanded island (`rgba(255,255,255,0.10–0.12)`); the compact
capsule has no border, only a hairline specular edge so it reads on a dark desktop.

Content hugs the camera: no padding between the leading/trailing elements and the
TrueDepth band, and 11 pt of inset from the rounded outer ends. Expanded content
starts below the camera band (34–44 pt) so text is never covered.

On Android the physical punch-hole is smaller than TrueDepth; the island keeps
Apple's proportions and covers the hole inside the camera band. Furthermore, the
mobile overlay automatically suppresses itself in landscape orientation to avoid
blocking games, video viewports, or full-screen navigation.

### Presentation state machine

```
rest (126) ──activity──▶ compact (180/230) ──hover / long-press──▶ expanded (400 × 180)
    ▲                            │                                        │
    └──────────ended─────────────┘◀──────────────dismiss / tap outside────┘
```

- **rest** — empty black capsule. No clock, no battery, no debug text.
- **compact** — one Live Activity: leading glyph/artwork, trailing value/waveform.
- **expanded** — Apple's Live Activity card: 44 pt artwork, title/subtitle,
  progress, at most one primary interactive row.
- **notification / transfer / timer / media / charging** are the activity kinds;
  one is shown at a time, alerts take priority.

---

## 3. Materials & color

| Token | Value | Role |
|---|---|---|
| Island surface | `#000000` | Compact and expanded shell, always opaque |
| Raised card | `#1C1C1E` | Rows inside the expanded app panel |
| Key line | `rgba(255,255,255,0.10)` | Expanded perimeter, dark appearance |
| Specular edge | `rgba(255,255,255,0.14)` | Inset top highlight on the shell |
| Primary label | `#FFFFFF` | Titles, active values |
| Secondary label | `rgba(235,235,245,0.60)` | Artists, subtitles |
| Tertiary label | `rgba(235,235,245,0.30)` | Times, units, hints |
| Blue | `#0A84FF` | Primary actions, pairing |
| Green | `#30D158` | Playing, charging, connected |
| Orange | `#FF9F0A` | Timer |
| Red | `#FF453A` | Failure, destructive |
| Pink | `#FF375F` | Fallback artwork |
| Cyan | `#64D2FF` | Bridge secondary accent |

Apple's island background cannot be customized. Theme presets tint **content**
(accents, progress, key line), never the shell.

---

## 4. Typography

The HIG recommends the system font, which already ships optical sizing and
legibility tuning. JARVIS does not bundle SF Pro.

```
Windows : -apple-system, "SF Pro Text", "Segoe UI Variable Text", "Segoe UI", ...
Android : FontFamily.Default (Roboto / Roboto Flex)
macOS   : -apple-system → SF Pro
```

Size-specific tracking and leading:

| Role | Size | Weight | Tracking | Leading |
|---|---|---|---|---|
| Compact value | 13 | Semibold | −0.08 | 16 |
| Expanded title | 15 | Semibold | −0.24 | 20 |
| Expanded subtitle | 13 | Regular | −0.08 | 18 |
| Caption (uppercase) | 11 | Semibold | +0.06 | 13 |
| Times / units | 11 | Medium | +0.06 | 13 |
| Buttons | 13 | Semibold | −0.08 | 16 |

Rules:

- Use medium or heavier for glanceable text; small text only where it is not essential.
- Tabular numerals for clocks, timers, percentages and transfer progress.
- Never center a long title in the compact presentation; the wings are 52 pt.
- Respect the platform text-size setting; spacing is in dp/rem, not hard-coded layout.

---

## 5. Motion

Apple describes springs as **response** (seconds to target) and **damping ratio**.
The implementation converts these to stiffness/damping:

```
omega = 2π / response
stiffness = omega² · mass
damping   = 2 · dampingRatio · omega · mass
```

| Interaction | Response | Damping | Notes |
|---|---|---|---|
| Rest ↔ compact | 0.35 | 1.0 | No overshoot |
| Compact → expanded | 0.40 | 1.0 | Slightly slower, graceful |
| Expanded → compact | 0.30 | 1.0 | Quicker return |
| Hover glance | 0.33 | 1.0 | Pointer equivalent of touch-and-hold |
| Momentum / flick | 0.40 | 0.8 | Only when the gesture carried velocity |
| Content replace | 160 ms | — | Fade + 0.97 scale, Apple's replace transition |

Rules:

- Every size change is a spring from the current on-screen value, so it is
  interruptible and reversible mid-flight.
- The shell animates width, height, corner radius and top together; content
  crossfades inside the moving surface.
- Feedback starts on pointer-down (`whileTap` / immediate haptic), not on release.
- Reduced motion replaces travel with a 90–120 ms crossfade.
- Live Activity animations must stay under two seconds; nothing here exceeds 0.4 s.

---

## 6. Platform integration

### Windows
- Borderless, transparent, always-on-top window; global hit-testing is restricted to
  the island rectangle via `jarvis_update_hit_rects`.
- Continuous corners use CSS `corner-shape: squircle` where Chromium supports it, with
  a circular `border-radius` fallback.
- Fullscreen suppression and multi-monitor work-area clamping are unchanged.
- Hover is the pointer equivalent of touch-and-hold: 300 ms to the expanded card.
  Click opens the full app panel (Apple: tap opens the app).

### Android
- `WindowManager` overlay with `TYPE_APPLICATION_OVERLAY`; the window height is
  `positionY + presentationHeight + shadow`.
- The camera cutout sits inside the 125.34 dp camera band; content lives in the
  52.33 dp wings.
- Haptics fire on expand, collapse and long-press. Semantics expose the island as a
  single status region with a tap-to-expand description.
- The overlay never dims the screen; tapping outside the expanded card collapses it.

---

## 7. Accessibility

- High contrast raises the key line to `rgba(255,255,255,0.40)`.
- Reduced motion and reduced transparency are honored on both platforms.
- Focus ring on the desktop is `2px solid #0A84FF` with a 2 px offset.
- The compact presentation is announced as one status region, not as separate glyphs.
