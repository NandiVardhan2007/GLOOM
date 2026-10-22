# JARVIS — Handoff

Last updated: 2026-10-22
Repo: `D:\Coding\JARVIS` (GitHub: `NandiVardhan2007/JARVIS`)
Live release: **v1.3.0** (tag `v1.3.0`) · Working branch: `main`
Previous release: **v1.2.0**

---

## 1. What JARVIS is

Two apps in one repository with dual-mode operational capability:

| Path | Stack | What it is |
|---|---|---|
| `app/` | Tauri 2 · Rust · React 19 · TypeScript | Windows Dynamic Island / notch overlay + LAN LiveBridge server |
| `mobile/android/` | Kotlin · Jetpack Compose | Android Dynamic Island companion, LiveBridge client, voice assistant & autonomous mobile agent |

### Operational Modes:
1. **Paired Mode (Home / Office)**: Phone connects over TLS to Desktop LiveBridge on LAN (port 53391). Media, clipboard, notifications, file beams, and AI tasks stream with zero battery strain on the phone; API keys remain safely on the PC.
2. **Standalone Mobile Mode (Away / Zero-Desktop)**: Phone functions autonomously without any desktop connection. Users can input API keys directly into the app (encrypted via Android Keystore). Cloud AI calls (Groq / OpenRouter) route directly over mobile data/Wi-Fi.
3. **Fully Offline Mode (No Network)**: Handled by `LocalBrain.kt` (system tools, battery, notes, alarms, scenes) and on-device quantized models (Needle local tool calling).

Releases are cut by pushing a `v1.X.Y` tag. GitHub Actions stamps that version into
`app/src-tauri/Cargo.toml`, `app/src-tauri/tauri.conf.json`, `app/package.json` and
`mobile/android/app/build.gradle.kts`, builds the EXE (with `--features custom-protocol`)
and APK, generates SHA-256 sidecars, and publishes the release. Publishing is tag-only;
pushes to `main` build but do not release.

Build locally:

```bash
cd app && npm run build
cd .. && cargo build --profile fast-release --features custom-protocol --manifest-path app/src-tauri/Cargo.toml
cd mobile/android && ./gradlew :app:installDebug
```

Fast release binary: `app/src-tauri/target/fast-release/jarvis.exe` (~8s incremental rebuild).
Use `cargo check` during Rust edits and only build the binary when you need to run it.

---

## 2. What is already done (committed)

### Mobile AI & Autonomous Enhancements (v1.3.0)
- **WhatsApp Single-Type Deduplication**: Added `ScreenAutomation.typeTextUnique` to prevent repeated typing of contact names or messages. Contact search field is cleared and checked against existing content before typing, preventing duplicate search tags and repeated messages.
- **Automated Chrome Browsing & Profile Form Autofill**: Injected `Vault.UserProfile` into Keystore encrypted storage; implemented `Vault.autofillWebForm(context)` using accessibility tree scanning to match and fill inputs (name, email, phone, address, city, state, zip) seamlessly. Added Chrome direct URL execution in `CommandRouter`.
- **Self-Developing Memory Module (CAG + MAG)**: Created `AiMemory.kt` providing Cache-Augmented Generation (instant pre-compiled semantic action execution) and Memory-Augmented Generation (entity graph, contact aliases like "Amma" -> contact name, procedural recipe caching, negative guidance loop, and outcome recording to reinforce successful routes and avoid stalled ones).
- **Phonetic & Fuzzy App Launcher**: Upgraded `AppLauncher.kt` with phonetic normalization, fuzzy Levenshtein distance matching, and spoken aliases ("whats up", "crome", "you tube", "insta", "g mail", "netfliks", "fecebook", "snap chat") to reliably launch apps despite voice recognition homophones or typos.
- **Dynamic Island Notification Routing**: Removed broad suppression of call and alert categories in `JarvisNotificationListener.kt`; incoming calls, alarms, OTPs, and alerts are packaged and routed directly to the Dynamic Island overlay.
- **Needle-3 Offline Tool Caller**: Added `NeedleBridge.kt` and bundled the quantized `needle3.cact` model asset into `mobile/android/app/src/main/assets/needle/needle3.cact`. Operates with sub-20ms fallback grammar parsing to translate spoken instructions into tool calls offline.
- **System & Productivity Tools**: Implemented `SystemTools.kt` with email drafting via default mail clients (`draftEmail`), regex file search across downloads/storage (`searchFilesRegex`), desktop clipboard and ecosystem sync over LiveBridge (`syncDataWithDesktop`), and temporary cache & duplicate file removal (`cleanupStorage`).
- **Keys Batch Importer & File Beam**: Integrated `KeyBatchImporter.kt` to allow one-click JSON bulk import and validation of Groq and OpenRouter keys directly into `SecureKeyStore`. Fixed mobile file beam storage path to public Downloads.
- **Apple HIG Refinements**: Enforced 180dp collapsed pill width for optimal label fit without cutoffs, added orientation listening to automatically hide the Dynamic Island in landscape mode to prevent obstructing games or videos.

### Google Artemis & KuafuAI Andee Architecture Integration (Sprint 5)
- **Visual State Diffing & Perceptual Anchoring**: Implemented `VisualStateDiff.kt` to extract fast structural & visual signatures before and after actions, accurately diagnosing screen stalls and ghost taps.
- **Dynamic Dwell Timing for Network Spinners**: Created `SpinnerGuard.kt` to monitor `ProgressBar`, `CircularProgressIndicator`, and buffering view hierarchies, preventing race conditions during app loading screens.
- **PII & Financial Masking Filter**: Added `PiiMasker.kt` with Luhn-validated credit card, CVV, OTP, and password redactions before sending UI outlines to cloud AI models.
- **OEM & Custom Hierarchy Normalizer**: Created `OemTreeHelper.kt` to synthesize actionable bounds and labels for obscured nodes on HyperOS, ColorOS, FunTouch, and custom WebViews.
- **Rolling Trace Ring Buffer**: Implemented `AutomationFrameRing.kt` (15 frames) with automated JSON diagnostics dump on failed automation routines.
- **Multi-Agent Trajectory Backtracking**: Injected trajectory recovery in `AiPlanner.kt` to automatically dispatch `GLOBAL_ACTION_BACK` upon consecutive screen stalls in Pro profile.
- **Phone-to-Desktop MCP Automation Bridge**: Created `PhoneMcpBridge.kt` routing JSON-RPC 2.0 tool requests (`describe_screen`, `tap_element`, `type_text`, `scroll_screen`, `launch_app`, `get_island_state`, `send_notification`) on `POST /api/mcp` and `POST /mcp` in `FileBeamReceiver.kt`.
- **Reel Counter Hardening**: Enforced Instagram glyph on the left wing and live counter on the right wing; eliminated counter suppression when count is 0; relaxed dwell threshold to 350ms to record fast scrolls; dual-tracking for Instagram and Facebook.
- **Interactive Pattern Calibration & Verification**: Added direct 9-dot `PatternLockView` card in `CalibrationScreen.kt` for in-app drawing and Keystore saving; fixed lock-screen calibration loop so it never falsely reports calibration without actual user unlock.

### Security / bridge
- Removed transfer mutex deadlock, UTF-8 clipboard panic, and `cmd.exe` URL-injection path.
- LiveBridge runs over **certificate-pinned TLS** (rustls, self-signed identity persisted in
  `%APPDATA%\JARVIS\bridge_cert.der` / `bridge_key.der`).
- Fixed certificate/key **race** that left mismatched files and prevented server from
  starting: identity generation is atomic and self-heals (`bridge_tls.rs`). Confirmed listening on `53391`.
- Tokens bound to pairing IP; devices trusted only after pairing; re-pairing replaces stale records.
- Pairing: PIN flow on both ends; desktop auto-opens bridge tab with PIN banner.
  **QR pairing**: desktop displays one-time QR (LAN IP + cert fingerprint + short-lived PIN);
  phone scans under Home → Desktop → Scan QR code.
- Universal Clipboard: 2-way PC ↔ phone sync with echo suppression (`markRemoteClipboard`).
- File Beam: PC → phone file transfer to `/sdcard/Android/data/com.jarvis.island/files/Downloads/JARVIS/`.

### Mobile
- Full Apple-style redesign: Apple UIKit (`ui/AppleUIKit.kt`), custom SF-style icon set
  (`ui/JarvisIcons.kt`, **no Material icons remain**), iOS tab bar, grouped inset lists,
  Home / Features / Settings / About / Security / Voice screens.
- Security: 3×3 pattern (salted SHA-256) + encrypted copy in Android Keystore, fingerprint via
  BiometricPrompt, launch lock, Siri-style voice assistant, lock-screen pattern replay via
  accessibility gestures on calibrated coordinates (`0.71w`, centre `0.658h`).
- Music card, tab bar, motion (critically damped springs), idle media states, lyrics UI.
- Wake word “Hey JARVIS”: offline Vosk model download, microphone foreground service.
- AI assistant: speech recognition → AI execution → spoken reply with TTS.
- UI Automator test `PatternUnlockTest` covers drawing pattern twice, saving, verifying,
  and checking encrypted replay copy.

### Desktop
- Apple-aligned design pass: tokens, type scale, materials, palette, motion, LiveBridge and
  Settings restyling, idle media states, live LRCLIB lyrics with synced highlighting.
- AI core (`app/src-tauri/src/providers/ai.rs`): provider adapters (Groq/OpenRouter/Zen/Aero),
  key rotation, fallback, `/api/ai/chat` over pinned bridge. Keys stay on the PC.
  - Key dir: `C:\Users\nandu\OneDrive\Desktop\Folders\Documents\KEYS` (override with `JARVIS_KEYS_DIR`).
  - Provider config: `%APPDATA%\JARVIS\ai_providers.json`.
  - Groq (`openai/gpt-oss-120b`) and OpenRouter (`anthropic/claude-haiku-5.5`) verified live.
- Desktop Notification Listener (`providers/notifications.rs`): WinRT `UserNotificationListener`
  polls Windows toasts every 1.5 s, extracts OTP/title/body, and opens island peek for 6 s.
- Complete icon overhaul: Replaced all lucide icons in expanded panel and tab views with SF-style `IslandIcons.tsx`.

---

## 3. Dynamic Island, Automation & Reel Counter (Commits `bba46db`, `2bdeebb`, `8a34204`)

### 3a. Dynamic Island Apple HIG Spec
- Island dimensions: Rest (126 × 36.67), compact (mobile default 180 × 36.67, desktop 230 × 36.67 with 125.34 camera band), expanded pill (400 × 180 dp, 36 dp continuous squircle radius).
- Landscape auto-suppression to prevent overlay obstruction in full-screen games/media.
- Island is opaque black; content hugs camera. System fonts with SF-metric tracking.
- Spring physics: rest↔compact 0.35 s / damping 1.0, expand 0.40, collapse 0.30.

### 3b. Notification Management & De-duplication
- Single renderer: Accessibility service owns overlay; stops plain overlay service if active.
- Silent notifications (`importance < IMPORTANCE_DEFAULT`) filtered by default.
- Autogroup summaries and duplicate alerts within 3 seconds suppressed.
- Per-app mute list: Searchable package list with monogram tiles in Settings.
- OTP Extraction: Automatic regex extractor surfaces a **Copy Code** button and **Open App** shortcut in the island.

### 3c. Screen Automation & andee-Grade Reactive Agent Loop
- **Screen Outline**: Numbered view hierarchy (`ScreenAutomation.kt`) + 6×10 aim grid (`ScreenSnapshot.kt`) for canvas/games.
- **Physical Touch Injection**: `ScreenAutomation.tap(eid)` prioritizes physical gesture touches at `(bounds.centerX(), bounds.centerY())` over synthetic `ACTION_CLICK`. Ensures universal 100% click fidelity across Jetpack Compose, Flutter, Instagram, React Native, and WebViews.
- **Reactive Observe-Plan-Act Loop** (`AiPlanner.kt`):
  - Rather than blind multi-action batching, JARVIS performs a true iterative loop (up to 8 steps).
  - At each step: observes screen, supplies action history to model, executes the single next action, settles UI (600ms), verifies screen state change, and loops.
  - **Dynamic Island Live Activity**: Surfaces real-time agent status ("JARVIS: Opening Settings...", "JARVIS: Tapping Wi-Fi...", "JARVIS: Done.") directly on the Dynamic Island notch overlay.
- **Typing**: Dedicated Accessibility IME (`A11yIme.kt`) commits text directly via `InputConnection`.
- **Command Router**: High-speed local regex router for type, scroll, tap, search, launch, and system intents.
- **Local Tools & Brain**: `LocalBrain.kt` answers time, battery, alarms, notes offline. `ReminderScheduler.kt` manages persistent AlarmManager reminders that survive reboots.
- **Scenes**: `SceneStore.kt` executes saved multi-step automation sequences.

### 3d. Reel Counter
- Tracks reels in Instagram and Facebook (`InstagramReelTracker.kt`).
- Dwell filter: Only counts reels viewed ≥ 1,200 ms (`reelMinDwellMs`).
- Activity Card: Shows today's count, lifetime total, and 7-day animated bar chart (`ReelWeekChart`).

---

## 3e. Standalone Mobile AI (In-App API Keys & Zero-Desktop Fallback)

To eliminate desktop dependency when on the go, the mobile companion is equipped with a standalone cloud execution layer:

```
                            ┌──────────────────────────────────────────────┐
                            │               USER REQUEST                   │
                            └──────────────────────┬───────────────────────┘
                                                   │
                                     Is Desktop LiveBridge Connected?
                                      /                            \
                                   [YES]                           [NO]
                                    /                                \
                 Route over TLS to Desktop PC              Are Direct In-App API Keys
                `POST /api/ai/chat` (LAN 53391)              Entered in Settings?
               (Keeps PC keys, saves phone bat)            /                      \
                                                        [YES]                     [NO]
                                                         /                          \
                                              Call Groq / OpenRouter           Fall back to LocalBrain
                                              directly over HTTPS             (offline tools/alarms)
                                              via Android Keystore keys        and Needle local model
```

### Architecture & Security Rules for Standalone Keys:
1. **No Keys in GitHub / APKs**: API keys are NEVER baked into the APK build, GitHub Actions Secrets, or `BuildConfig`. Compiling keys into APKs allows trivial extraction via `jadx`.
2. **Encrypted Local Keystore**: In-app keys entered by the user are stored in `EncryptedSharedPreferences` backed by an Android Keystore master key (`AES-256-GCM`).
3. **In-App Key Management**:
   - Location: Mobile App → **Settings → AI & Brain → Cloud API Keys** (`CloudAiScreen.kt`).
   - Supports: Groq API Key (`gsk_...`) and OpenRouter API Key (`sk-or-v1-...`).
   - Features: Obfuscated display (`gsk_••••••••xxxx`), single-tap validation test, and model selector.
4. **Seamless Auto-Fallback**:
   - If connected to PC on LAN: Uses PC's LiveBridge (`POST /api/ai/chat`), keeping phone cool and saving mobile data.
   - If disconnected from PC (cellular/outside): Automatically invokes direct cloud HTTPS endpoints using on-device encrypted keys.
   - If offline / airplane mode: Falls back to `LocalBrain` and Needle.

---

## 3f. Pattern & Swipe Calibration Screen (Bypass Optical Fingerprint Sensor)

- **Root Cause Solved**: On phones with in-display optical fingerprint sensors (such as Motorola Edge 50 Pro), swiping up from the center bottom (`x: 50%, y: 88%`) hit the optical sensor directly, triggering fingerprint authentication failures/vibrations and swallowing the keyguard unlock gesture.
- **Dedicated Calibration Screen** (`CalibrationScreen.kt`):
  - Location: Settings → Security → **Pattern & Swipe Calibration**.
  - **Swipe Path Strategies**:
    - **Left Side (Default)**: Swipes from `(22% w, 82% h)` to `(22% w, 25% h)`, completely avoiding the center FOD sensor.
    - **Right Side**: Swipes from `(78% w, 82% h)` to `(78% w, 25% h)`.
    - **High Center**: Starts above the optical sensor at `(50% w, 62% h)` to `(50% w, 18% h)`.
    - **Custom Coordinates**: Granular percentage sliders for Start X/Y and End X/Y.
  - **Pattern Grid Geometry Calibration**:
    - Real-time sliders for Grid Width Ratio (45%–90%) and Center Y Position (40%–85%).
    - Interactive 3×3 visual phone preview showing mock screen bounds, FOD area, swipe path with direction arrow, and calibrated dot positions.
  - **Live Test Actions**: "Test Swipe Path" and "Test Pattern Replay" buttons to verify gestures instantly.

---

## 4. Today's Execution Plan: Completing All Features to Advanced Level

```
  [Sprint 1: Delivered]      [Sprint 2: Midday]          [Sprint 3: Afternoon]       [Sprint 4: Evening]
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│ Standalone AI Engine│    │ Needle ARM64 Local  │    │ Flash vs Pro Loop   │    │ Full Regression     │
│ In-App Key Settings │ ──>│ Inline Island Reply │ ──>│ History Compression │ ──>│ Tag v1.2.0 & CI     │
│ Calibration Screen  │    │ Keystore Password   │    │ Screen-Time Analytics│   │ Hardware Test On    │
│ andee-Grade Agent   │    │ Vault (AES-GCM)     │    │ Whistle Offline ASR │    │ Edge 50 Pro         │
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘    └─────────────────────┘
```

### Sprint 1: Standalone Mobile AI, Calibration & andee Loop (Completed)
* [x] Implement `StandaloneAiClient.kt` with direct Groq & OpenRouter HTTPS caller (key rotation, model per provider).
* [x] Add `SecureKeyStore.kt` using Android Keystore `EncryptedSharedPreferences`.
* [x] Add "Cloud API Keys" entry UI in `CloudAiScreen.kt` with live key verification test.
* [x] Build `CalibrationScreen.kt` with Apple UIKit to bypass in-display fingerprint sensors and align pattern grid.
* [x] Refactor `AiPlanner.kt` to reactive step-by-step agent loop (`andee` model) with live Dynamic Island status updates.
* [x] Upgrade `ScreenAutomation.kt` to physical touch gesture injection priority.
* [x] Replace remaining `lucide-react` icons in desktop expanded panel/tab views with `IslandIcons.tsx`.

### Sprint 2: Needle Local Engine, Inline Reply & Password Vault (Delivered)
* [x] Bundle quantized Needle-3 model (`needle3.cact`) in `src/main/assets/needle/` with `NeedleBridge.kt` and sub-20ms fallback intent grammar engine for offline tool dispatch.
* [x] Wire Android `RemoteInput` into `JarvisIslandAccessibilityService` for inline notification quick replies inside the expanded island.
* [x] Implement `Vault.kt`: Android Keystore AES-GCM credential vault allowing the agent to safely autofill credentials without exposing plain text.
* [x] Add `VaultScreen.kt`: Biometric / Pattern protected Apple UIKit credentials manager with add/view/copy/delete actions.
* [x] Add action burst chaining (1.10) in `ScreenAutomation` (`tapBurst`, `tapSequence`) to beat transient Android controls.

### Sprint 3: Advanced Intelligence & Analytics (Delivered)
* [x] Implement Flash vs. Pro profiles in `AiPlanner.kt`:
  - **Flash Profile**: Ultra-fast single-pass reactive tool execution with 6 max steps.
  - **Pro Profile**: Plan → Execute → Verify → Self-Correction loop (10 max steps) with automatic reflection prompt injection on screen stalls and final `checkpoint_report` summary.
  - Profile switcher in Settings → Automation → Agent Execution Profile.
* [x] Add per-app screen-time tracking (3.3) via `ScreenTimeTracker.kt` and expand Home Activity card with proportional usage bars and formatted durations.
* [x] Integrate rolling context history compression (1.13) in `AiPlanner.compressHistory` for long multi-step agent tasks.

### Sprint 4: Full Regression, Tagging & Release (Evening)
* [x] Run complete test suite:
  - Desktop: `cd app && npm run build` (Clean) + `cd app/src-tauri && cargo check --release --features custom-protocol` (Clean in 7.4s) + `cargo test` (23 passed).
  - Android: `cd mobile/android && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:compileDebugKotlin` (All Clean).
* [x] Push commits and tag **`v1.3.0`** to trigger GitHub Actions release workflow.
* [x] Install release APK on Motorola Edge 50 Pro (`ZD222NB59D`) via ADB streamed install and complete hardware validation.

---

## 5. Testing Notes & Hardware State

- **Physical Device**: Motorola Edge 50 Pro (`ZD222NB59D`), Android 16.
  - Currently on updated **`v1.3.0`** (versionCode 25, installed and verified on physical hardware).
  - Upgradable via GitHub Actions release APK.
- **Emulator**: `Medium_Phone_API_37.0` (AOSP, API 37, no Play Services). Start with:
  `"%LOCALAPPDATA%\Android\Sdk\emulator\emulator" -avd Medium_Phone_API_37.0 -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect -no-audio`
  Use `ANDROID_SERIAL=emulator-5554` for Gradle connected commands.
- **Pattern Sequence**: `2,5,7,3,6,8,4,1,9` (1-based); matches physical lock screen.
- **Desktop Diagnostics**: Port TCP 53391 (TLS bridge) + UDP 53390 (Discovery beacon). Keys loaded from `KEYS` directory.

---

## 6. Repository Hygiene, Structure Maintenance, and Next-Session Guidelines

To ensure any developer or AI coding agent can immediately understand, maintain, and build on this repository without confusion or regressions, **strictly follow these rules on every run**:

### Rule 1: Zero Scratch / Clean Tree Standard
- **Never leave scratch files behind**: Delete any temporary scripts, screen captures (`*.png`), log dumps (`*.log`, `.crash.json`), or paste files (`.paste.js`) before completing a turn.
- `git status` must remain completely clean. Only `HANDOFF.md` remains untracked by convention.

### Rule 2: Fast Pre-Flight Verification Before Full Builds
Always use fast compiler checks during iteration. Do not trigger expensive release builds until code compiles cleanly:
1. **Web / TypeScript**: `cd app && npx tsc --noEmit`
2. **Desktop Rust**: `cd app/src-tauri && cargo check`
3. **Android Kotlin**: `cd mobile/android && ./gradlew :app:compileDebugKotlin`
Only run full `./gradlew :app:assembleDebug` or `cargo build` when ready to test binaries.

### Rule 3: Version Stamping & Release Tags
- **Never manually bump versions in source files**: CI automatically stamps versions from Git tags into `Cargo.toml`, `tauri.conf.json`, `package.json`, and `build.gradle.kts`.
- Version manifests in the repository intentionally remain at `1.0.0` or placeholder values.
- Releases are strictly cut by pushing signed tags (`v1.X.Y`).

### Rule 4: Secret & Credential Separation
- **Desktop Keys**: Loaded dynamically from `JARVIS_KEYS_DIR` or `C:\Users\nandu\OneDrive\Desktop\Folders\Documents\KEYS`. Never commit `.txt` key files.
- **Mobile Keys**: Stored exclusively in on-device `EncryptedSharedPreferences` via Android Keystore. Never pass API keys via Gradle properties, environment variables, or GitHub Secrets into the APK.

### Rule 5: Standard End-of-Session Handoff Protocol
At the end of **every session**, update `HANDOFF.md` with:
1. Current Git HEAD commit hash and live tag.
2. Verified features vs. unverified features (explicitly distinguishing emulator results from real hardware results).
3. Exact steps required by the next session to resume without re-investigation.

---

## 7. Session End State

- **Current Live Release**: `v1.3.0`
- **Working Tree Features Delivered**:
  1. **WhatsApp Contact Search Deduplication**:
     - Resolved root cause where custom views took ~50-100ms to update `node.text`, causing the old `typeText` to fall through to `ACTION_PASTE` and paste names twice (e.g. "MomMom").
     - `ScreenAutomation.typeTextUnique` and `typeText` treat successful `ACTION_SET_TEXT` as definitive success without fallback duplication.
     - `CommandRouter.sendWhatsApp` and `AiPlanner` explicitly clear search fields before typing once.
  2. **Dynamic Island Camera-Hugging Idle Compression**:
     - When `compactKind == IslandCompactKind.IDLE`, `compactWidth` shrinks to ~36dp (`(settings.pillHeight.dp).coerceIn(32.dp, 38.dp)`), seamlessly hugging the front camera cutout lens.
     - Completely clears the left and right status bar, eliminating any obstruction when swiping down the notification drawer.
  3. **Reel Counter High-Accuracy & Fast-Swipe Capture**:
     - Reduced minimum dwell threshold from 1200ms to 350ms (`DEFAULT_REEL_MIN_DWELL_MS = 350L`) with clamping between 250ms and 1500ms.
     - Added `TYPE_WINDOW_CONTENT_CHANGED` tracking to detect reel transitions via audio/creator tags even when custom feeds suppress `TYPE_VIEW_SCROLLED`.
  4. **Lock Screen Pattern Drawer Micro-Dwell Calibration**:
     - Increased keyguard bouncer animation delay to 450ms.
     - Added 0.5px micro-dwells on dot coordinates so 144Hz touch samplers (like Motorola Edge 50 Pro) reliably hit the hitbox of every dot without cutting corners.
  5. **High-Accuracy Persistent Memory (CAG + MAG)**:
     - Implemented `AiMemory.queryMemory` with synonym group resolution (`mom/mother/amma`, `dad/father/nanna`, `car/vehicle`, `phone/mobile`) and "list my memories" support.
     - Integrated directly into `LocalBrain.kt` and `CommandRouter.kt` for instant offline responses to personal memory queries ("what is my car number", "who is my mom").
     - Added fallback to all stored personal facts in `AiMemory.getRelevantContext` when prompt tokens lack explicit keywords.
  6. **On-Device Diagnostics & Execution Trace Logger**:
     - Created `JarvisDebugLogger.kt` maintaining an 80-frame in-memory ring and writing JSONL traces to `getExternalFilesDir(null)/logs/jarvis_execution_trace.jsonl`.
     - Wired logger into `AiPlanner.kt`, `CommandRouter.kt`, and `LocalBrain.kt` to capture user requests, AI thoughts, responses, executed steps, and failure reasons.
     - Created Apple UIKit styled `ExecutionLogsScreen.kt` with live share trace via FileProvider, clear logs, and expandable log cards.
     - Linked `ExecutionLogsScreen` into `MainActivity.kt` routes and `SettingsHubScreen` under Diagnostics & Debug Logs.
- **Verified this run**:
  - Android Kotlin compile: `./gradlew :app:compileDebugKotlin` passed (clean).
  - Android Debug APK assemble: `./gradlew :app:assembleDebug` passed (clean in 35s).
  - Android Unit tests: `./gradlew :app:testDebugUnitTest` passed (clean).
  - Desktop TypeScript build: `npm run build` passed (clean in 5.86s).
  - Desktop Rust tests: `cargo test` 23 passed (clean).
  - Desktop Rust release check: `cargo check --release --features custom-protocol` passed (clean in 9.16s).

---

## 8. References

- [Human Interface Guidelines — Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities)
- [DynamicIslandExpandedRegion Documentation](https://developer.apple.com/documentation/widgetkit/dynamicislandexpandedregion)
- [kuafuai/andee](https://github.com/kuafuai/andee) — Screen tree representation, physical touch injection & reactive agent loop.
- [google/artemis](https://github.com/google/artemis) — Action verification & safety loops.
- [cactus-compute/needle](https://github.com/cactus-compute/needle) — Low-latency on-device tool calling.
- `.agents/skills/apple-design/SKILL.md` — Motion physics & Apple spring curves.
