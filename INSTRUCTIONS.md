# GLOOM Architecture & Development Instructions

> **Purpose**: This document specifies the non-negotiable architectural rules, design standards, and constraints for GLOOM. Any agent working on this codebase MUST follow these instructions strictly without deviation.

---

## 1. Window Management & Overlay Invariants (CRITICAL)

### Invariant 1: MATCH_PARENT Overlay Width & Precision Touch Insets
* **Rule**: In both `GloomIslandAccessibilityService.kt` and `GloomIslandOverlayService.kt`, the overlay window's `WindowManager.LayoutParams.width` MUST ALWAYS BE `WindowManager.LayoutParams.MATCH_PARENT`.
* **Gravity & Coordinates**: `gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL`, `x = 0`, `y = 0`.
* **Why**: Modifying window width between expanded (`MATCH_PARENT`) and collapsed (`pillWidthPx`) forces Android SurfaceFlinger to reallocate the window buffer and reposition it, causing a visible visual glitch where the pill snaps to the top-left `(0, 0)` corner before centering.
* **Touch Event Pass-Through via Attached Insets**:
  * On Android 9 through 15 (targetSdk 35), hidden API restrictions on `ViewTreeObserver.addOnComputeInternalInsetsListener` are bypassed at process start via `HiddenApiBypass.addHiddenApiExemptions("")` in `GloomApplication`.
  * `setupTouchableRegion(view)` MUST be registered on the live, attached `ViewTreeObserver` (using `view.addOnAttachStateChangeListener` if not yet attached).
  * When collapsed (`!isExpanded`): Sets `TOUCHABLE_INSETS_REGION` (type 3) with a bounding `Rect(left, top, right, bottom)`:
    - `top = posYPx.coerceAtLeast(0)` (keeping the top edge above the island 100% free for status bar swipe-down gestures).
    - `bottom = posYPx + pillHeightPx`.
    - `left` and `right` strictly bound the pill (`currentPillWidth = pillWidth + 16`).
    - All touches to the left, right, and top of the pill pass directly through to the underlying application, system status bar, control panel (Quick Settings), and notification shade.
  * When expanded (`isExpanded`): Sets `TOUCHABLE_INSETS_FRAME` (type 0) to allow full card interaction and tapping outside to dismiss.

### Invariant 2: Dynamic Input Focus & Software Keyboard (IME) Management
* **Rule**: Overlay windows default to `FLAG_NOT_FOCUSABLE` so touches and keyboard input pass seamlessly to underlying apps.
* **Inline Reply & Text Entry**:
  * When any text field (such as inline reply in `AppleNotificationActivity`) requires input focus, the overlay service MUST dynamically toggle input focus via `setInputFocusEnabled(true)`.
  * Removing `FLAG_NOT_FOCUSABLE` (`layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()`) and setting `layoutParams.softInputMode = SOFT_INPUT_ADJUST_RESIZE or SOFT_INPUT_STATE_ALWAYS_VISIBLE` allows Android's `InputMethodManager` to attach to `ComposeView` and display the software keyboard.
  * Window height is elevated to `MATCH_PARENT` during active input so outside touch gestures cleanly dismiss the keyboard and re-apply `FLAG_NOT_FOCUSABLE`.
* **Auto-Collapse Protection & Lifecycle**:
  * An expanded card with user interaction or an active reply action MUST NEVER auto-collapse mid-typing.
  * `notificationJob?.cancel()` MUST be called whenever the island is expanded or input focus is granted.
  * Auto-expanded notifications collapse back after `autoCollapseDelaySec` (minimum 3.5s) if the user does not open the reply input.
  * Device unlock (`handleUserPresent()`) immediately collapses any lingering expansion, requests layout to refresh insets, and restores clean status bar pass-through.

---

## 2. Dynamic Island Camera Cutout & Alignment Rules

### Invariant 3: Zero Content in the Central Camera Band
* **Rule**: Android devices like the Motorola Edge 50 Pro have a physical camera punch-hole cutout centered at the top of the display.
* **Layout Structure**: Every collapsed, compact, and alert activity inside the Dynamic Island MUST adhere to a three-part layout:
  1. **Leading Wing (Left)**: Contains leading icons, app artwork, or glyphs. Must be bounded with `.clipToBounds()`.
  2. **Camera Band (Center)**: A dedicated `Spacer(Modifier.width(cameraGap).height(1.dp))` that is 100% empty. NEVER place text, columns, or glyphs across this spacer.
  3. **Trailing Wing (Right)**: Contains badges, indicators, timers, or battery gauges. Must be bounded with `.clipToBounds()` and have `TextOverflow.Ellipsis` with width constraints (`Modifier.widthIn(max = wingWidth - inset)`).
* **Reference**: See `AppleCompactContent` and `AppleSystemAlertActivity` in `IslandVisualComponents.kt`.

---

## 3. Official App Icons Mandate

### Invariant 4: Official App Icons Only
* **Rule**: In both the collapsed Dynamic Island wings and expanded notification cards, **the official installed app icon MUST be displayed**.
* **Method**:
  * Extract using `packageManager.getApplicationIcon(packageName)` in `GloomNotificationListener.kt` or `IslandVisualComponents.kt`.
  * Convert `Drawable` to `Bitmap` if necessary (handling `BitmapDrawable`, `AdaptiveIconDrawable`, and generic vectors).
  * Render using `Image(bitmap = officialAppIcon.asImageBitmap(), ...)` with circular or squircle clipping.
  * DO NOT fall back to generic vector icons unless the OS fails to resolve the app package.

---

## 4. Design & Motion System (Apple HIG & Fluid Physics)

### Apple HIG Fluid Spring Physics
* Island morphing (width, height, corner radius):
  * Critically damped spring physics: `spring(dampingRatio = 0.82f, stiffness = 380f)` for fluid responsiveness without overshoot jitter.
  * Reduced motion fallback: `tween(durationMillis = 90, easing = FastOutSlowInEasing)`.
* Interactive Touch Scale (WWDC Designing Fluid Interfaces):
  * Dips to `0.97f` scale on pointer-down using `spring(dampingRatio = 0.82f, stiffness = 420f)` and springs back to `1.0f` on release.

### Dynamic Themes (51 Presets & 20 Gradient Pairs)
* Defined in `IslandThemePalettes.kt`:
  * Fill types: `SOLID`, `LINEAR_GRADIENT`, `HORIZONTAL_GRADIENT`, `RADIAL_GRADIENT`, `SWEEP_GRADIENT`.
  * Built-in themes: Classic Black, Minimal Gray, Midnight, Amethyst, Sunset, Neon Cyber, Royal Gold, Ocean Breeze, Crimson Fire, Deep Space, Forest Rain, The Matrix, Cyber Pink, Pure White, Northern Lights, etc.
  * Glowing borders: Rendered along `shape.createHairlineBorderPath` with customizable neon stroke widths (0.5dp to 2.0dp) and optional outer aura glow when `borderGlow` is enabled.

---

## 5. Voice Assistant Architecture
* **Placement**: The voice assistant lives directly inside the Dynamic Island (NOT in a detached full-screen or corner overlay).
* **Activation**: Triggered by long-press on the Dynamic Island, wake word ("Hey Gloom" / "Jarvis"), or automated router commands.
* **Component**: `AppleVoiceAssistantActivity` in `GloomIslandApp.kt` handles listening waveform, thinking state with radial glow, speech transcript, reply rendering, and action buttons.

---

## 6. Desktop Architectural Invariants

### Invariant 5: Two-Layer Magnifying Dock Architecture
* **Rule**: The desktop dock MUST maintain strict separation between the Visual Layer and Hit Layer:
  1. **Visual Layer (`.dock-visual`)**:
     - `pointer-events: none` and `align-items: flex-end`.
     - Animates size upward ($B=66\text{px} \to M=116\text{px}$) with Apple HIG spring physics (`stiffness = 400`, `damping = 28`, `mass = 0.6`).
     - Contains the squircle icons (`border-radius: 22%`), running dots (`WindowDots`), window thumbnail previews, and anchored tooltips (`bottom: calc(100% + 8px)`).
  2. **Hit Layer (`.dock-hit`)**:
     - Overlaid row of fixed-width slots ($\text{SLOT} = 84\text{px}$) that **never move or resize**.
     - Handles hover (`onMouseEnter`), keyboard focus (`onFocus`, `tabIndex = 0`), click, middle-click, and context menu.
     - Eliminates all hover jitter and feedback loops.
* **Tuning Constants Single Source of Truth**:
  - All dock constants ($B$, $M$, $R$, $g$, spring parameters, bounce keyframes, falloff curves) MUST reside in `Desktop/src/dockConfig.ts` (`DOCK_CONFIG`).
* **High-Density Scaling ($N > 15$)**:
  - When the dock contains more than 15 items, the visual layer MUST automatically switch to scale (`scale: s_i / B`) and X-offset GPU transforms with `transformOrigin: "bottom center"`, preserving layout shift without DOM reflow overhead.
* **Window Region Clearance**:
  - The OS click-through window region bottom band MUST be at least $260\text{px} \times \text{scale}$ to prevent Windows from clipping magnified icons, tooltips, or the $-28\text{px}$ launch bounce.
* **Autostart Purge**:
  - When autostart is disabled, `disable_all_autostart()` in `autostart.rs` purges registry keys from `HKCU\Run` and `HKCU\StartupApproved\Run`, shortcut `.lnk` files in startup folders, and scheduled tasks.
* **Taskbar Quiet Slap-Down**:
  - Use `enforce_taskbar_hidden()` quietly without `HWND_BROADCAST` to prevent Explorer broadcast feedback loops.

---

## 7. Pre-Commit Verification Checklist

Before reporting completion on any coding task in GLOOM, always run:
1. **Desktop Verification**:
   - `npm test --prefix Desktop` — Verify frontend dockConfig unit tests pass.
   - `npm run build --prefix Desktop` (`tsc && vite build`) — Verify clean TypeScript compilation and production bundle.
   - `cargo test --manifest-path Desktop/src-tauri/Cargo.toml` — Verify all Rust unit tests pass.
2. **Android Verification**:
   - `cd D:\Coding\GLOOM\Android`
   - `.\gradlew.bat compileDebugKotlin` — Verify zero compilation errors.
   - `.\gradlew.bat testDebugUnitTest` — Verify all unit test suites pass (`SingleOverlayCoordinatorTest`, `IslandThemePalettesTest`, `NotificationStyleRegistryTest`, `VoiceAssistantRoutingTest`).
   - `.\gradlew.bat assembleDebug` — Build the debug APK.
   - `adb devices` — Check device connection; if device is present, run `adb -s <id> install -r Android/app/build/outputs/apk/debug/app-debug.apk`.
3. **Documentation & Graphify**:
   - Run `python -m graphify update .` to keep the AST knowledge graph updated.
   - Update `HANDOFF.md`, `INSTRUCTIONS.md`, and `SESSION_RULES.md`.
