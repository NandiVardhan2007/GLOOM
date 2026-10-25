# GLOOM Session Handoff & Continuity State

> **Last Updated**: 2026-10-25 10:45 IST  
> **Release Version**: 1.9.5 (versionCode 195)  
> **Target Device**: Motorola Edge 50 Pro (`ZD222NB59D`)  
> **Workspace Root**: `D:\Coding\GLOOM`  
> **Android Root**: `D:\Coding\GLOOM\Android`
> **Desktop Root**: `D:\Coding\GLOOM\Desktop`

---

## 1. Executive Summary & Current State
GLOOM is a cross-platform ecosystem consisting of:
1. **Android Dynamic Island Overlay**: Jetpack Compose overlay running under `GloomIslandAccessibilityService` (primary) and `GloomIslandOverlayService` (fallback), rendering Apple HIG Live Activities, hardware HUDs, notifications, media controls, and on-device voice assistant directly around the camera punch-hole cutout.
2. **GLOOM Desktop**: Tauri v2 + Rust + React 19 + TypeScript desktop notch, Apple-style magnifying dock, and ecosystem continuity bridge.

The codebase is **100% healthy, all desktop and Android unit tests pass, and both desktop and Android platforms compile cleanly without any regressions**:
- **Apple-Style Magnifying Dock**: Implemented with exact macOS visual behavior ($B=66\text{px}$, $M=116\text{px}$, $g=18\text{px}$, $R=3$, spring physics, launch bounce $0\to -28\to 0\to -10\to 0$, tooltip anchored to top edge, keyboard focusable hit slots, reduced motion support, two-layer anti-jitter architecture, and automated $N>15$ scale/x-offset performance optimization).
- **All 4 Desktop Stability Fixes Intact**: Login autostart purge, taskbar collision slap-down, mouse hook & media thread latency decoupling, and screen corners removal.
- **Verification**: `npm test --prefix Desktop` (8 unit tests pass), `npm run build --prefix Desktop` (0 errors), `cargo test` (78 Rust tests pass), and `Android: testDebugUnitTest` (BUILD SUCCESSFUL).

---

## 2. Critical Bugs Solved & Architectural Invariants

### ⚠️ Bug 1: Left-Corner Jump Glitch on Island Collapse
* **Symptom**: When the expanded island collapsed back to the pill, it would suddenly jump to the top-left corner `(0, 0)` before centering again.
* **Root Cause**: The overlay window layout parameters were being resized horizontally from `MATCH_PARENT` down to `pillWidthPx` in `applyCollapsedDimensions()`. When shrinking the window, Android `WindowManager` reallocated the surface buffer and snapped it to `(0, 0)` before applying gravity.
* **Invariant Rule**: 
  * The overlay window width must **ALWAYS remain `WindowManager.LayoutParams.MATCH_PARENT`** with `gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL`, `x = 0`, `y = 0`.
  * Touch pass-through is handled via `setupTouchableRegion(view)`:
    * When collapsed (`!isExpanded`): Sets `TOUCHABLE_INSETS_REGION` (type 3) with a bounding `Rect(left, top, right, bottom)` strictly enclosing the pill.
    * When expanded (`isExpanded`): Sets `TOUCHABLE_INSETS_FRAME` (type 0) to allow full card interaction.
  * **DO NOT** change `layoutParams.width` to a fixed DP value when collapsed.

### ⚠️ Bug 2: Words Overflowing the Camera Cutout
* **Symptom**: Words (such as `"Silent"`, `"Mute on"`, `"Charging"`, or Bluetooth device names) rendered directly across the physical center punch-hole camera.
* **Root Cause**: System alert activities used an unpartitioned `Row` that placed text directly into the center of the island.
* **Invariant Rule**:
  * The central camera band (`Spacer(Modifier.width(cameraGap)).height(1.dp)`) must **ALWAYS remain completely empty** in both compact and alert states.
  * Content is strictly partitioned into:
    * **Leading Wing (Left)**: Icon / glyph (bell, charging bolt + %, headphones).
    * **Camera Gap (Center)**: Zero content, zero text.
    * **Trailing Wing (Right)**: Status badge (`SILENT` / `RING`, liquid progress fill bar, or `CONNECTED` badge).
  * Both wing containers MUST include `.clipToBounds()` and width bounds (`Modifier.widthIn(max = wingWidth - inset)`) so text never spills into the camera gap.

### ⚠️ Bug 3: Island Auto-Collapsing Before Typing & Keyboard (IME) Not Appearing
* **Symptom**: 
  1. When tapping to reply to an expanded notification, the Dynamic Island would collapse before the user could finish typing.
  2. Tapping the reply field did not open the Android software keyboard (IME).
* **Root Cause**:
  1. The overlay window was permanently created with `FLAG_NOT_FOCUSABLE`. In Android, windows with `FLAG_NOT_FOCUSABLE` cannot receive keyboard focus or request the IME.
  2. When a notification arrived, an auto-collapse job (`notificationJob`) was scheduled for 4 seconds (`autoCollapseDelaySec`) and unconditionally set `activeNotification = null` and called `collapseIsland()`, wiping out the card while the user was interacting with it.
* **Solution Implemented**:
  1. **Dynamic Input Focus Switching (`setInputFocusEnabled`)**:
     - When the reply input field is tapped/focused, `onInputFocusChanged(true)` is fired.
     - Strips `FLAG_NOT_FOCUSABLE`: `layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()`.
     - Sets `layoutParams.softInputMode = SOFT_INPUT_ADJUST_RESIZE or SOFT_INPUT_STATE_ALWAYS_VISIBLE` and `layoutParams.height = MATCH_PARENT`.
     - Calls `windowManager.updateViewLayout(view, layoutParams)`.
  2. **Auto-Collapse Elimination During Reply**:
     - `notificationJob?.cancel()` is invoked immediately whenever the island is expanded or `isInputFocusEnabled` is true.
     - The island will **NEVER auto-collapse while the user has the reply box open or is typing**.

### ⚠️ Bug 4: Overlay Blocking Control Panel, Notifications & Screen Sides/Top
* **Symptom**: 
  1. Swiping down from the top-right to pull down the Motorola Control Center (Quick Settings) or top-left for Notifications failed or was intercepted.
  2. Touches on the sides of the screen or above the Dynamic Island were intercepted by an invisible overlay.
* **Root Cause**:
  1. Hidden API restriction on Android 14/15: Reflection on `ViewTreeObserver.addOnComputeInternalInsetsListener` and `InternalInsetsInfo` threw exceptions when `targetSdk = 35`, which was silently swallowed by `catch (_: Exception)`.
  2. `setupTouchableRegion(view)` was called before `addOverlayView()`, registering on a temporary `mFloatingTreeObserver` that was discarded upon window attach.
* **Solution Implemented**:
  1. **HiddenApiBypass**: Added `org.lsposed.hiddenapibypass:hiddenapibypass:4.3` and initialized in `GloomApplication.onCreate()` via `HiddenApiBypass.addHiddenApiExemptions("")`.
  2. **Live ViewTreeObserver Attachment**: Registered `setupTouchableRegion` using `view.addOnAttachStateChangeListener`, guaranteeing registration onto the live `ViewRootImpl.mTreeObserver`.
  3. **Precision Insets**:
     - Collapsed: `Rect(left, top, right, bottom)` where `top = posYPx.coerceAtLeast(0)` (keeping the top edge above the island 100% free) and width strictly encloses the pill.
     - Verified in `dumpsys window windows`: `touchableRegion=SkRegion((334,5,885,142))` on the 1220x2712 screen.

### ⚠️ Bug 5: Voice Assistant Hardware, Wi-Fi, PDF, & Real-Time Context Failures (The 4 Screenshots)
* **Symptom**:
  1. *"could you tell me what is the current time"* -> AI responded *"I don't have access to real-time data, so I can't provide the current time."*
  2. *"I am asking you to tell me the list of the all the pdf that are available"* -> AI responded *"I can't access files on your device."*
  3. *"increase the volume to 100%"* -> AI gave manual button instructions instead of setting volume.
  4. *"could you tell me for which Wi-Fi I am connecting"* -> AI responded *"I don't have direct access to your device, but you can see the current Wi-Fi name in Settings..."*
* **Root Cause**:
  1. `CommandRouter.cleanSpeech()` failed to strip user phrasing fillers like `"could you tell me"`, `"i am asking you to tell me"`, `"for which"`.
  2. `LocalBrain.isTimeQuestion` used exact string equality (`==`) rather than natural pattern matching.
  3. No volume adjustments, Wi-Fi status queries, or PDF file queries existed in `CommandRouter.kt` or `LocalBrain.kt`.
  4. `AiRouter.request` system prompt had zero live device context (no time, battery, Wi-Fi SSID, or volume level).
* **Solution Implemented in v1.9.4**:
  1. **Expanded `cleanSpeech()` Fillers**: Added `"i am asking you to tell me"`, `"could you tell me"`, `"can you tell me"`, `"tell me"`, `"what is the"`, `"show me"`, etc.
  2. **Volume Controls in `SystemTools.kt` & `CommandRouter.kt`**:
     - `setVolumePercent(context, percent)`: Directly sets media stream volume (0..100%) via `AudioManager.setStreamVolume` with `FLAG_SHOW_UI`.
     - `adjustVolume(context, direction)` & `muteVolume(context, mute)`: Adjusts volume up/down and mutes.
     - `getVolumeStatus(context)`: Returns current media volume percentage.
     - Handles `"increase the volume to 100%"`, `"set volume to 50%"`, `"volume up/down"`, `"mute"`.
  3. **Live Wi-Fi Status in `SystemTools.kt` & `CommandRouter.kt`**:
     - `getWifiStatus(context)`: Resolves connected Wi-Fi SSID via `ConnectivityManager` & `WifiManager`.
     - `openWifiSettings(context)`: Directly launches Wi-Fi settings.
  4. **PDF & Document Search in `SystemTools.kt` & `CommandRouter.kt`**:
     - `listPdfFiles(context, query)`: Queries `MediaStore.Files` (`application/pdf`) and fallback scans Downloads and Documents.
     - Formats results with file names and sizes in KB/MB.
  5. **Live Device Grounding in System Prompt**:
     - In both `GloomIslandAccessibilityService` and `GloomIslandOverlayService`, `AiRouter.request` injects:
       `"Current live phone status: $timeContext, $batteryContext, $wifiContext, $volContext."`
     - Remote LLM calls always know the exact real-time clock, battery level, connected Wi-Fi, and volume level.

### ⚠️ Bug 6: Inline Reply Focus Stealing & PendingIntent Revocation
* **Symptom**:
  1. Tapping the reply field in expanded notifications failed to open or immediately closed the keyboard.
  2. Sending inline reply failed on WhatsApp / Telegram / SMS.
* **Root Cause**:
  1. In `setInputFocusEnabled(true)`, calling `view.requestFocus()` on the root `ComposeView` focused `AndroidComposeView`, causing Compose to blur its child `BasicTextField`. This fired `BasicTextField.onFocusChanged(false) -> onInputFocusChanged(false) -> setInputFocusEnabled(false)` in an infinite cancellation loop.
  2. In `GloomNotificationListener.kt`, calling `cancelNotification(sbn.key)` immediately upon arrival destroyed the underlying `PendingIntent` for messaging apps before the user replied.
* **Solution Implemented in v1.9.4**:
  1. Removed `view.requestFocus()` from `setInputFocusEnabled(true)`.
  2. In `AppleNotificationActivity`, used `coroutineScope.launch { delay(80); focusRequester.requestFocus(); keyboardController?.show() }` to reliably attach the IME.
  3. Prevented transient Compose focus shifts from triggering premature `onInputFocusChanged(false)`.
  4. In `GloomNotificationListener.kt`, clearable notifications with an active `replyAction` are kept alive until the reply is sent, after which `cancelNotificationSafe(notificationKey)` safely dismisses the shade copy.

---

## 3. Implemented Features Catalog

### Category A: System Status Alerts & Hardware HUDs
- **Ringer Switch HUD**: Detects ringer mode changes, shows oscillating bell animation (red for Silent, orange for Ring) on leading wing, empty camera gap in center, and `SILENT`/`RING` pill badge on trailing wing.
- **Battery Charging HUD**: Detects `ACTION_POWER_CONNECTED`, displays pulsing green lightning bolt with battery percentage on leading wing, clear camera gap, and liquid gradient fill bar on trailing wing.
- **Bluetooth Audio HUD**: Detects `BluetoothDevice.ACTION_ACL_CONNECTED` / `DISCONNECTED`, displays cyan headphones on leading wing, clear camera gap, and cyan `CONNECTED` status badge on trailing wing.

### Category B: Live Activities & Ongoing Background Services
- **Live Countdown Timer (`AppleTimerActivity`)**:
  - Circular progress countdown sweep ring (`Stroke` arc in `Apple.systemOrange`).
  - Large digital countdown readout (`formattedTime` in `MM:SS`).
  - Transport controls: Pause / Resume button (`GloomIcons.PlayFill` / `PauseFill`) and Cancel button.
  - In collapsed state: Leading wing shows orange timer icon, trailing wing shows live ticking `MM:SS`.
- **Media Player Live Activity**: Scrub bar, bouncy waveform equalizer, album artwork, track title/artist, transport controls (Play/Pause, Next, Prev, Seek).
- **Direct Inline Notification Reply (`AppleNotificationActivity`)**:
  - Official installed app icons extracted from `PackageManager`.
  - App-specific color themes and layouts (WhatsApp, Instagram, Snapchat, Gmail, Telegram, SMS, Outlook).
  - One-tap app launch on header tap.
  - Working inline text reply with software keyboard presentation, send button, and "Reply Sent" confirmation.

### Category E: Apple Fluid Physics & Dynamic Theme
- **Apple HIG Fluid Spring Curves**:
  - Critically damped spring physics: `spring(dampingRatio = 0.82f, stiffness = 380f)` for island size, height, and corner radius morphs.
- **Interactive Touch Scale (WWDC Designing Fluid Interfaces)**:
  - Dips to `0.97f` scale on pointer-down using `spring(dampingRatio = 0.82f, stiffness = 420f)` and springs back to `1.0f` on release.
- **Complete Catalog of 51 Visual Themes**:
  - Implemented in `IslandThemePalettes.kt`:
    - Classic Black, Minimal Gray, Midnight, Amethyst, Sunset, Neon Cyber, Royal Gold, Ocean Breeze, Crimson Fire, Deep Space, Forest Rain, The Matrix, Cyber Pink, Pure White, Northern Lights, Vampire Red, Emerald City, Cosmic Void, Rose Gold, Ice Cold, Hot Lava, Bumblebee, Slate, Rainbow, Toxic Green, Ruby Red, Deep Sea, Pumpkin, Influencer, Glitch, Miami Vice, Zen Garden, etc.
  - Multiple fill styles: `SOLID`, `LINEAR_GRADIENT`, `HORIZONTAL_GRADIENT`, `RADIAL_GRADIENT`, `SWEEP_GRADIENT`.
  - Radiant neon outline borders along capsule squircle path.
  - 20 Custom Gradient Pairs (`CUSTOM_GRADIENT_PAIRS`) selectable via `selected_gradient`.

---

## 4. Key File Map & Roles

| File Path | Role |
| :--- | :--- |
| `Android/app/src/main/kotlin/com/gloom/island/automation/SystemTools.kt` | Device volume controls, MediaStore PDF file search, Wi-Fi SSID status and settings launcher. |
| `Android/app/src/main/kotlin/com/gloom/island/automation/CommandRouter.kt` | Spoken command routing, expanded filler stripping (`cleanSpeech`), volume regex patterns, Wi-Fi queries, PDF searches. |
| `Android/app/src/main/kotlin/com/gloom/island/automation/LocalBrain.kt` | Deterministic on-device question answering for time, date, battery, Wi-Fi, volume, and PDF listing. |
| `Android/app/src/main/kotlin/com/gloom/island/automation/AiPlanner.kt` | Action verb detection and hardware/file action classification. |
| `Android/app/src/main/kotlin/com/gloom/island/service/GloomIslandAccessibilityService.kt` | Primary overlay service. Manages window params (`MATCH_PARENT`), `setupTouchableRegion` with attached `ViewTreeObserver`, `setInputFocusEnabled`, hardware alert receiver, and live device context injection in `AiRouter.request`. |
| `Android/app/src/main/kotlin/com/gloom/island/service/GloomIslandOverlayService.kt` | Fallback overlay service. Shares the identical `MATCH_PARENT`, `setInputFocusEnabled`, and touchable insets architecture. |
| `Android/app/src/main/kotlin/com/gloom/island/service/GloomNotificationListener.kt` | Captures notifications, media sessions, official app icon bitmaps, and safe delayed notification cancellation for inline replies. |
| `Android/app/src/main/kotlin/com/gloom/island/ui/GloomIslandApp.kt` | Root Compose container. Handles Apple HIG fluid spring physics (`dampingRatio = 0.82f, stiffness = 380f`), interactive touch scale (0.97f), dynamic theme background brush fills, and glowing borders. |
| `Android/app/src/main/kotlin/com/gloom/island/ui/IslandVisualComponents.kt` | Contains `IslandThemePalettes` (51 themes + 20 gradients), `AppleNotificationActivity` with inline reply `FocusRequester` and IME actions, `AppleSystemAlertActivity`, `AppleTimerActivity`, and `AppleMusicActivity`. |
| `Android/app/src/test/kotlin/com/gloom/island/automation/VoiceAssistantRoutingTest.kt` | Unit tests verifying recognition of volume, Wi-Fi, PDF listing, and time questions from natural user speech. |

---

## 5. Build, Test & Deployment Quick Reference

```powershell
# 1. Compile Kotlin
cd D:\Coding\GLOOM\Android
.\gradlew.bat compileDebugKotlin

# 2. Run All Unit Tests
.\gradlew.bat testDebugUnitTest

# 3. Assemble Debug APK
.\gradlew.bat assembleDebug

# 4. Install to Connected Phone (Motorola Edge 50 Pro)
adb -s ZD222NB59D install -r D:\Coding\GLOOM\Android\app\build\outputs\apk\debug\app-debug.apk

# 5. Launch Main Activity
adb -s ZD222NB59D shell am start -n com.gloom.island/.MainActivity

# 6. Verify Touchable Insets on Device
adb -s ZD222NB59D shell dumpsys window windows | Select-String -Pattern "com.gloom.island.*touchableRegion"

# 7. Keep Knowledge Graph Current
python -m graphify update .
```

---

## 6. GLOOM Desktop: Critical Bug Fixes & Architecture (Desktop v1.9.2)

### ⚠️ Issue 1 Solved: "Launch GLOOM on Login" Setting Inconsistency
* **Root Cause**:
  1. `Desktop/src/App.tsx` force-enabled autostart on first run whenever `localStorage.getItem("gloom-first-run") === null`, overriding the user's saved preference in `settings.json`.
  2. `@tauri-apps/plugin-autostart`'s `disable()` only deleted `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` with key `"gloom"`. If GLOOM was installed via Inno Setup (`installer/gloom.iss`), a shortcut was placed into `{userstartup}` (`%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\GLOOM Dynamic Island.lnk`), which the plugin never inspected or cleaned.
  3. Task Manager's `StartupApproved\Run` and alternate registry keys (`"GLOOM"`, `"GLOOM-Island"`, `"GLOOM Dynamic Island"`) were left orphaned.
* **Solution Implemented**:
  1. **Comprehensive Purge Module (`Desktop/src-tauri/src/autostart.rs`)**: `disable_all_autostart(&app)` removes all registry keys from `HKCU\Run` and `HKCU\StartupApproved\Run` (`"gloom"`, `"GLOOM"`, `"GLOOM-Island"`, `"GLOOM Dynamic Island"`), purges matching `.lnk` shortcuts from User and Common Startup folders, and deletes scheduled tasks.
  2. **Single Source of Truth**: Commands `get_autostart_status` and `set_autostart_status` persist `"gloom-autostart": true/false` directly into `settings.json`.
  3. **Startup Enforcement**: In `Desktop/src-tauri/src/main.rs` setup hook, if `gloom-autostart` is `"false"`, `disable_all_autostart` runs immediately at boot to guarantee no rogue entries survive Windows restarts.
  4. **Installer Default**: Changed `installer/gloom.iss` `startupicon` task flag to `Flags: unchecked`.

### ⚠️ Issue 2 Solved: Taskbar and Dock Glitches & Broadcast Storm Feedback Loops
* **Root Cause**:
  1. `any_native_taskbar_visible()` checked only `IsWindowVisible(hwnd).as_bool()`. On Windows, `Shell_TrayWnd` retains `WS_VISIBLE` even when parked offscreen at `(-10000, -10000)`, causing `any_native_taskbar_visible()` to return `true` continuously.
  2. The 150ms periodic check repeatedly called `set_taskbar_visibility(false, false)`.
  3. `set_taskbar_visibility` broadcast `WM_SETTINGCHANGE` to `HWND_BROADCAST` on every tick. This broadcast stalled all top-level windows and caused Explorer to re-show `Shell_TrayWnd`, triggering `taskbar_event_proc` in an infinite feedback loop.
* **Solution Implemented**:
  1. **Strict Placed Rect Check**: `any_native_taskbar_visible()` now verifies `IsWindowVisible(hwnd).as_bool() && placed_taskbar_rect(hwnd).is_some()`. Parked taskbars at `(-10000, -10000)` return `None` and are recognized as hidden.
  2. **Quiet Slap-Down (`enforce_taskbar_hidden`)**: A dedicated helper directly hides and positions unhidden taskbar windows offscreen without broadcasting `WM_SETTINGCHANGE` or writing to the registry.
  3. **Targeted Shell Notifications**: Replaced `HWND_BROADCAST` with `tray_hwnd` directly, and tracked `LAST_APPLIED_TASKBAR_VISIBILITY` so redundant visibility calls exit immediately.
  4. **Loop Elimination**: Both `taskbar_event_proc` and the 150ms periodic check now use `enforce_taskbar_hidden()`, completely eliminating flickering and freezes.

### ⚠️ Issue 3 Solved: Dock and Island Freezing & Low-Level Latency
* **Root Cause**:
  1. In `run_mouse_hook_thread`, idle mouse periods >750ms were falsely flagged as dropped hooks, reinstalling `WH_MOUSE_LL` every second (verified in `diagnostics.log`).
  2. `mouse_hook_proc` synchronously executed `is_start_menu_open()` on every `WM_LBUTTONDOWN` across the entire OS, invoking `OpenProcess`, `QueryFullProcessImageNameW`, and `CloseHandle` inside the low-level hook procedure, exceeding hook timeouts and causing Windows to drop the hook.
  3. In `setup_system_worker`, WinRT media properties and thumbnail reading (`TryGetMediaPropertiesAsync().get()`, `LoadAsync().get()`) ran on the same thread servicing `rx.try_recv()`, blocking volume, play/pause, and visibility commands.
  4. React `updateNotchRegion` in `App.tsx` and `sendDockRegion` in `Dock.tsx` ran `measureElements` with DOM queries on every single render cycle without dependencies.
* **Solution Implemented**:
  1. **Stabilized Mouse Hook**: Provided module `HINSTANCE` to `SetWindowsHookExW` and added a 3-consecutive-interval filter before reinstalling.
  2. **Fast Foreground Start Detection**: Replaced process queries with `is_start_foreground_fast()` using lightweight `GetClassNameW` and restricted checks strictly to presses inside dock bounds.
  3. **Decoupled Media Polling Thread**: Separated media thumbnail and session metadata loading into its own dedicated background thread (`handle_media`), leaving the command handling and volume loop unblocked.
  4. **Optimized Region Hooks**: Added proper dependency arrays to `useEffect` in `App.tsx` and `Dock.tsx`, ending DOM measurement thrashing.

### ⚠️ Issue 4 Solved: Curved Display Feature Completely Removed
* Removed "Screen Corners" row from `Desktop/src/settings/AppearanceTab.tsx` and cleaned props interface.
* Removed `cornersEnabled` and `toggleCorners` from `Desktop/src/Settings.tsx`.
* Removed `cornersEnabled`, `toggleCorners`, and `"gloom-corners-enabled"` from `Desktop/src/settings/useSettings.ts`.
* Removed `settingsCornersEnabled` state, JSX elements (`.screen-corner.top-left`, `.screen-corner.top-right`), and region measurements from `Desktop/src/App.tsx`.
* Removed `.screen-corner`, `.top-left`, `.top-right`, `.bottom-left`, `.bottom-right` CSS rules from `Desktop/src/App.css`.
* Removed dead `bottom-corners` webview window lookup from `Desktop/src-tauri/src/services.rs`.

### 7. Desktop Verification Summary
* **Rust Check & Tests**: `cargo test --manifest-path Desktop/src-tauri/Cargo.toml` -> **78 passed, 0 failed, 1 ignored**.
* **Frontend Build**: `npm run build --prefix Desktop` (`tsc && vite build`) -> **Built in 2.68s with 0 errors**.
* **Frontend Unit Tests**: `npm test --prefix Desktop` (`node --test tests/*.test.mjs`) -> **8 passed, 0 failed**.
* **Android Unit Tests**: `cd Android; .\gradlew.bat testDebugUnitTest` -> **BUILD SUCCESSFUL in 16s**.

---

## 8. Apple-Style Magnifying Dock Implementation

### 1. Architectural Overview & Two-Layer Anti-Jitter Architecture
To prevent the feedback loop / hover jitter inherent in standard mouse-hover animations (where scaling elements move under the pointer, changing the hovered element index), the dock is split into two independent layers:
1. **Visual Layer (`.dock-visual`)**:
   - `display: flex; justify-content: center; align-items: flex-end; gap: 18px; pointer-events: none;`
   - Bottom-aligned so that icons grow upward.
   - Symmetrically centered within the dock shelf; widens as icons grow.
   - Houses the animated squircle icons (`border-radius: 22%`), custom start/task view icons, active running indicators (`WindowDots`), window thumbnail previews (`.preview-tooltip` with `pointer-events: auto`), and the tooltip anchored 8px above the top edge (`bottom: calc(100% + 8px)`).
2. **Hit Layer (`.dock-hit`)**:
   - `position: absolute; inset: 0; display: flex; justify-content: center; align-items: flex-end; pointer-events: none;`
   - Contains fixed-width invisible slots (`width = SLOT = 84px`, matching $B + g$) that **never move or resize**.
   - Slots have `pointer-events: auto`, handling `onMouseEnter`, `onFocus`, `onClick`, `onContextMenu`, and `onAuxClick`.
   - Hover index is a pure, immutable function of pointer X, eliminating hover jitter and gap dead zones.

### 2. Mathematical Model & Tuning Constants (`Desktop/src/dockConfig.ts`)
All tuning constants and mathematical functions are exposed in `DOCK_CONFIG`:
* `BASE = 66` (resting icon size in px)
* `MAX = 116` (magnified size on hover, ~1.75x)
* `FALLOFF = 3` (falloff radius $R$ in icons)
* `GAP = 18` (gap between icons at rest)
* `SLOT = 84` ($B + g$)
* `SPRING = { type: "spring", stiffness: 400, damping: 28, mass: 0.6 }` (Apple HIG responsive spring with subtle overshoot)
* `REDUCED_MOTION = { type: "tween", duration: 0.15, ease: "easeOut" }`
* `BOUNCE = { y: [0, -28, 0, -10, 0], transition: { duration: 0.6, times: [0, 0.3, 0.6, 0.8, 1], ease: "easeOut" } }`
* `MODE = "discrete"` (supports `"discrete"` and `"continuous"`)
* `FALLOFF_TYPE = "linear"` (supports `"linear"` and `"cosine"`)
* `CORNER_RADIUS_RATIO = 0.22` (~22% Apple squircle radius)
* `PERF_THRESHOLD = 15` (switches from width/height animation to scale+xOffset when $N > 15$)

#### Formulas:
* **Discrete Mode (Default)**:
  $$d_i = |i - h|, \quad t_i = \max(0, 1 - d_i / R), \quad s_i = B + (M - B) \cdot t_i$$
  - At distance 0: 116 px
  - At distance 1: 99 px (~98-99 px)
  - At distance 2: 83 px
  - At distance 3+: 66 px
* **Continuous Mode**:
  $$p = (\text{cursorX} - \text{dockLeft}) / \text{SLOT} - 0.5, \quad d_i = |i - p|$$
* **Cosine Falloff Variant**:
  $$t_i = d_i < R ? \cos\left(\frac{\pi d_i}{2R}\right) : 0, \quad s_i = B + (M - B) \cdot t_i$$
* **Performance Mode ($N > 15$)**:
  $$xOffset_i = C_{i,\text{ideal}} - C_{i,\text{resting}}$$
  Transforms icons via `scale: s_i / B` and `x: xOffset_i` with `transformOrigin: "bottom center"`, preserving exact spatial widening without browser layout reflows.

### 3. Key Feature Implementations
1. **Bottom-Aligned Upward Growth**:
   - Dock shelf stays grounded at the bottom edge at 82px height.
   - Visual row is bottom-aligned (`align-items: flex-end`), allowing magnified icons to project upward out of the shelf into desktop space.
   - `dockBandHeightRef` set to minimum $260\text{px} \times \text{scale}$ to guarantee that OS window clipping never clips magnified icons, bounce apexes, or tooltips.
2. **Launch Bounce Animation**:
   - Driven by Framer Motion `useAnimationControls`.
   - Bounces Y: `0 → -28 → 0 → -10 → 0` over 0.6s.
   - Replays reliably on repeated clicks.
   - Disabled automatically when `prefers-reduced-motion` is detected.
3. **Anchored Tooltip Label**:
   - Anchored 8px above the top edge (`bottom: calc(100% + 8px)`).
   - Follows icon growth and launch bounce dynamically.
   - Apple HIG frosted glass theming with full Light/Dark mode support.
4. **Keyboard Accessibility**:
   - Each hit slot is keyboard focusable (`tabIndex={0}`, `role="button"`).
   - Tabbing into a slot sets hover position, triggering spring magnification and tooltip display.
   - Pressing Enter or Space triggers the launch bounce and executes the item launch action.
5. **Preserved Existing Features**:
   - Start menu button & custom icons.
   - Task View toggle.
   - Pinned and unpinned running applications with multiple-window indicators (`WindowDots`).
   - Window thumbnail hover previews (`preview-tooltip`).
   - Left and right dock extras (drives, recycle bin, custom folders).
   - Win+1..9 keyboard shortcuts mapped to pinned items.
   - Context menus and middle-click handlers.

### 4. Files Created / Modified
* `Desktop/src/dockConfig.ts` (new): Configuration, math algorithms, and tuning constants.
* `Desktop/src/components/MagnifyingDock.tsx` (new): Visual + hit layer component with launch bounce, accessibility, and window previews.
* `Desktop/src/Dock.tsx` (modified): Unified dock entries data pipeline, region bounds expansion, and integration.
* `Desktop/src/Dock.css` (modified): Apple HIG styles for magnifying dock, squircle icons, hit slots, and tooltips.
* `Desktop/tests/dockConfig.test.mjs` (new): Unit tests covering math formulas, discrete/continuous modes, cosine falloff, and x-offsets.
* `Desktop/package.json` (modified): Added `"test"` script for running node unit tests.

