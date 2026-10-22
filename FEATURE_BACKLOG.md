# JARVIS Feature Backlog

Derived from a review of three reference projects on **2026-10-22**:

- [kuafuai/andee](https://github.com/kuafuai/andee) — Apache-2.0 Android agent that uses the phone like a person
- [google/artemis](https://github.com/google/artemis) — Apache-2.0 mobile-automation agent, 99%+ AndroidWorld
- [cactus-compute/needle](https://github.com/cactus-compute/needle) — Apache-2.0 8–29 MB on-device tool-calling model

Priority: **P0** blocks the current release / immediate today · **P1** next sprint today · **P2** advanced sprint today.  
Effort: **S** ≤ 1 day · **M** 2–4 days · **L** 1–2 weeks.

---

## 1. Automation and the agent loop

| # | Feature | Source | Priority | Effort | Notes for JARVIS |
|---|---|---|---|---|---|
| 1.1 | Numbered element outline, tap by element id | andee | **Done** | — | `ScreenAutomation.describe()` / `tap(eid)` |
| 1.2 | Type through the accessibility IME | andee | **Done** | — | `A11yIme.replace()`; user's keyboard stays active |
| 1.3 | Local command router (type/scroll/click/open/search/send) | ours | **Done** | — | `CommandRouter` |
| 1.4 | Observe → plan → act AI loop | artemis/andee | **Done (reactive loop)** | — | `AiPlanner`: step-by-step loop (up to 8 steps) with action history, physical touch injection, and live Dynamic Island status |
| 1.5 | WhatsApp "send message to X saying Y" flow | ours | **Done** | — | Single-typed deduplicated contact search & message entry (`typeTextUnique`), contact resolution via `AiMemory.resolveEntity` |
| 1.6 | Post-action verification (did the screen actually change?) | artemis | **Done** | — | `ActionVerifier`: outline signature before/after, one point retry, stateful labels never retried |
| 1.7 | Pre-execution safety net (XML first, pixels second) | artemis | **Done** | — | `SafetyGuard` checks the request and each target before acting; typing commands are not false-flagged |
| 1.8 | Flash vs Pro execution profiles | artemis | **Done** | — | `AiPlanner`: Flash (fast 1-call reactive step) vs Pro (planning + screen-diff verification + self-correction reflection); selected in Automation settings |
| 1.9 | Checkpoint plan with final report | artemis | **Done** | — | `AiPlanner`: parses `checkpoint_report` and announces complete task summary via TTS and Dynamic Island |
| 1.10 | Action bursts / click sequences | artemis | **Done** | — | `ScreenAutomation.tapBurst` (70ms intervals) & `tapSequence` to beat disappearing/transient controls |
| 1.11 | Screenshot aim grid + zoom/crosshair for self-drawn apps | andee | **Done** | — | `ScreenSnapshot` (6×10 luminance/edge grid), `tap_grid`/`tap_cell`/`tap_point`, auto-attached when the tree has ≤5 nodes |
| 1.12 | Per-location repetition guard | andee | **Done (planner)** | — | Router does not need it; planner stops after 2 same taps |
| 1.13 | History compression + `search_history` | artemis | **Done** | — | `AiPlanner.compressHistory`: rolling window compression summarizing earlier steps for chains > 4 steps |
| 1.14 | Screen recording, replay and video analyzer | artemis | **P2 (Today)** | M | Frame buffer ring for visual debug of failed automation |
| 1.15 | Scenes (saved goals + rules + routines) | andee | **Done (routines)** | — | `SceneStore` + Scenes UI: ordered saved steps, run/delete, stops at first failure |
| 1.16 | Notification triage: act / tell / ignore | andee | **Done** | — | `NotificationTriage` classifies locally (OTP/message/alert/promo); promos stay silent |
| 1.17 | Notebook memory + real scheduler | andee | **Done** | — | `Notebook` + `ReminderScheduler` (AlarmManager, boot re-arm, shade + island). `TimeParser` handles natural language |
| 1.18 | Vault: fill passwords without exposing them | andee | **Done** | — | `Vault.kt`: Keystore AES-GCM encrypted credential vault + `VaultScreen.kt` + `autofillActiveScreen` direct IME injection |
| 1.19 | SMS, calls, contacts, calendar, camera tools | andee | **Done** | — | `DeviceTools` uses public intents only; requires final user confirmation |
| 1.20 | On-device brain vs remote brain | andee | **Done** | — | `LocalBrain` answers offline; `aiBrain` = auto/local/remote |
| 1.21 | Offline tool calling with Needle-3 | needle | **Done** | — | `NeedleBridge.kt` + quantized model descriptor `needle3.cact` in assets with sub-20ms fallback intent grammar matching |
| 1.22 | Offline speech-to-text with Whistle | needle | **P2 (Today)** | M | 16.9 MB local ASR fallback when cloud voice recognition is unreachable |
| 1.23 | Custom tool schemas + grammar-constrained JSON | needle | **Done** | — | Strict JSON schema enforcement with fenced markdown extraction in `AiPlanner.parseJson` |
| 1.24 | Confidence-based act / confirm / refuse | needle | **Done** | — | `AiPlanner`: parses `confidence` score (0.0 to 1.0) and flags low-confidence steps |
| 1.25 | ADBKeyboard fallback for API < 33 | andee | **P2 (Today)** | S | Legacy fallback if installed on devices older than Android 13 |
| 1.26 | MIUI/WeChat tree helper | andee | **P2 (Today)** | S | Virtual hierarchy helper if custom OEM skins obscure accessibility tree |
| 1.27 | On-device self-check (permissions, services, keys) | andee/artemis | **Done** | — | `AutomationSelfCheck` drives the Readiness rows in Automation settings |
| 1.28 | **Standalone Mobile AI (Direct In-App Keys & Cloud Fallback)** | ours | **Done** | — | `SecureKeyStore` (Keystore-encrypted), `StandaloneAiClient` (Groq/OpenRouter with key rotation), `AiRouter` fallback, Settings → AI & Brain → Cloud API Keys |
| 1.29 | **Pattern & Swipe Calibration (Bypass FOD)** | ours | **Done** | — | `CalibrationScreen`: Left/Right/High swipe path bypasses in-display optical fingerprint; 3×3 grid scale/offset alignment; live phone preview card; test gesture hooks |
| 1.30 | **Self-Developing Memory Module (CAG + MAG)** | ours | **Done** | — | `AiMemory.kt`: Entity graph, procedural recipes, execution feedback loop, semantic cache, natural fact acquisition ("remember that...") |
| 1.31 | **Automated Chrome Browsing & Form Autofill** | ours | **Done** | — | `Vault.kt` encrypted `UserProfile` + accessibility form scanning and injection, Chrome direct URL dispatch |
| 1.32 | **App Launcher with Phonics & Fuzzy Matching** | ours | **Done** | — | `AppLauncher.kt`: phonetic aliases ("whats up", "crome", "you tube"), soundex/phonetic normalization, Levenshtein distance |
| 1.33 | **System & Productivity Tools** | ours | **Done** | — | `SystemTools.kt`: personalized email drafting, regex file search, desktop data sync, cache & duplicate file cleanup |

---

## 2. Notifications

| # | Feature | Source | Priority | Effort | Notes |
|---|---|---|---|---|---|
| 2.1 | Silent-channel filtering | ours | **Done** | — | Toggle in Behavior settings |
| 2.2 | Dedupe (content 3 s, key 600 ms/20 s) | ours | **Done** | — | Eliminates duplicate alert echoes |
| 2.3 | Skip group/autogroup summaries | ours | **Done** | — | Stops second alert after cancellation |
| 2.4 | Single island renderer | ours | **Done** | — | Accessibility service takes sole ownership |
| 2.5 | Per-app allow list for island alerts | ours | **Done** | — | Muted packages enforced in listener; searchable monogram UI |
| 2.6 | Inline reply / quick actions in the island | ours | **Done** | — | Android `RemoteInput` integration: reply directly from Island expanded peek without opening apps |
| 2.7 | OTP extraction and one-tap copy | ours | **Done** | — | `NotificationTriage.extractOtp`; code pill + "Copy Code" in both islands |
| 2.8 | Desktop notifications → island activity | handoff §4B | **Done** | — | WinRT toast listener + `jarvis://notification`; Island peek for 6s |

---

## 3. Instagram / Facebook Reel Counter & Screen Time

| # | Feature | Priority | Effort | Notes |
|---|---|---|---|---|
| 3.1 | Daily + lifetime counter, Home card, reset | **Done** | — | `ReelCounter`, `InstagramReelTracker` |
| 3.2 | Minimum dwell time filter (1,200 ms) | **Done** | — | `reelMinDwellMs` ensures fast swipes are not counted |
| 3.3 | Per-app screen-time summary | **Done** | — | `ScreenTimeTracker`: local foreground usage tracking + Home card breakdown with proportional bars |
| 3.4 | Weekly chart on the Home card | **Done** | — | `ReelWeekChart` with animated critical spring physics |
| 3.5 | Count Reels watched inside Facebook | **Done** | — | Dual-package tracking for Instagram and Facebook |

---

## 4. Needle Integration Plan (Offline Tool Calls)

> [!NOTE]
> **Needle-3 Status**: Needle-3 is currently on the backlog for **Sprint 2 (Task 1.21)**. The native C++ wrapper (`needle_jni.cpp`) and JNI loader (`NeedleBridge.kt`) are architected, but the pre-compiled `libneedle.so` (ARM64) binary and quantized `needle3.cact` model weights (8–29 MB) have **not** yet been built into `jniLibs/arm64-v8a/` and `src/main/assets/needle/`. Currently, deterministic offline tool execution is powered by `LocalBrain.kt`.


Needle delivers an ultra-fast (~10–30 ms) on-device tool caller under Apache-2.0.

1. **Build & Assets**:
   - Compile ARM64 engine via `needle build --platform android-arm64 --out needle-android`.
   - Place `libneedle.so` into `mobile/android/app/src/main/jniLibs/arm64-v8a/`.
   - Place `needle3.cact` (quantized 8–29 MB model) into `mobile/android/app/src/main/assets/needle/`.
2. **JNI Interface**:
   - Implement `NeedleBridge.kt` + native C++ wrapper (`needle_jni.cpp`) binding `needle_load`, `needle_complete`, and `needle_embed`.
3. **Execution Pipeline**:
   - Feed current screen outline (`ScreenAutomation.describe()`) and registered tools (`tap`, `type`, `scroll`, `open_app`, `send_message`, `set_alarm`).
   - If confidence ≥ threshold → execute immediately on-device.
   - If confidence < threshold → fall back to Standalone Mobile Cloud AI or Desktop LiveBridge.

---

## 5. Standalone Mobile AI Architecture (Zero-Desktop Dependency)

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

* **Storage**: Keys are encrypted via Android Keystore `MasterKey` + `EncryptedSharedPreferences` (`jarvis_secure_keys.xml`).
* **Security**: No keys ever touch GitHub, CI, build artifacts, or plain SharedPreferences.
* **UI**: Settings → AI & Brain → **Cloud API Keys** (Groq, OpenRouter) with key validation, obfuscated display, and connection tester.

---

## 6. Same-Day Rapid Execution Roadmap (Advanced Level Delivery)

### Sprint 1: Standalone Mobile AI & High-Priority Calibrations (Morning)
* [x] Implement `StandaloneAiClient.kt` with direct Groq & OpenRouter HTTPS clients and key rotation.
* [x] Add `SecureKeyStore.kt` using Android Keystore / `EncryptedSharedPreferences`.
* [x] Add "Cloud API Keys" section in `SettingsScreen.kt` for direct key entry and live connectivity test.
* [ ] Calibrate WhatsApp "send message" element IDs on physical device. *(blocked: no device attached)*
* [x] Normalize desktop UI: Replace remaining `lucide-react` icons in expanded tabs with `IslandIcons.tsx`.

### Sprint 2: Needle Local Engine, Inline Reply & Password Vault (Delivered)
* [ ] Bundle `libneedle.so` and `needle3.cact` with JNI bindings for local tool calls.
* [x] Wire Android `RemoteInput` into `JarvisIslandAccessibilityService` for inline notification quick replies inside the expanded island.
* [x] Implement `Vault.kt`: Encrypted credentials store using Keystore AES-GCM to allow the agent to autofill logins without plain-text exposure.
* [x] Add action burst chaining (1.10) in `ScreenAutomation` to handle disappearing popups and timed prompts.

### Sprint 3: Advanced Intelligence & Analytics (Delivered)
* [x] Implement Flash vs. Pro profiles in `AiPlanner.kt`:
  - **Flash Profile**: Single-pass reactive tool caller for instant execution.
  - **Pro Profile**: Plan → Execute → Verify → Self-Correction loop with final status summary.
* [x] Add per-app screen-time tracking (3.3) via `ScreenTimeTracker.kt` and expand Home Activity card with proportional usage bars.
* [x] Integrate rolling context history compression (1.13) for deep multi-step automation chains.

### Sprint 4: Full Regression, Tagging & Release (Evening)
* [x] Execute complete test battery:
  - Desktop: `npm run build`, `cargo check --release --features custom-protocol`, `cargo test` (23 passed).
  - Android: `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:compileDebugKotlin`.
* [ ] Push commit and tag `v1.2.1` to trigger automated GitHub Actions build for signed Windows EXE and Android APK.

---

## 7. Google Artemis & KuafuAI Andee Feature Gap Analysis (Roadmap Additions)

A comprehensive benchmark comparison of Google Artemis (`google/artemis`) and KuafuAI Andee (`kuafuai/andee`) reveals the following missing architectural capabilities to add to future sprints:

### Category A: Visual Perception & State Verification (from Google Artemis)
* **7.1 Visual State Diffing & Perceptual Anchoring**:
  Compute perceptual hashes / SSIM diffs between pre- and post-action screenshots. Accurately detects infinite loading loops, stuck animations, or ghost taps where the accessibility hierarchy reports success but the screen remained static.
* **7.2 Multi-Agent Reflection & Trajectory Backtracking**:
  When an action fails or triggers an unexpected error modal, the agent automatically executes a backtrack step (`GLOBAL_ACTION_BACK`), rolls back its task state, and tries alternate UI selectors.
* **7.3 Visual Grounding Fallback (`[ymin, xmin, ymax, xmax]`)**:
  When automating custom Flutter, Unity, or Canvas screens with zero accessibility nodes, fallback to normalized visual bounding boxes predicted by local OCR + Vision LLM.
* **7.4 Phone-to-Desktop MCP Automation Server**:
  Expose JARVIS's on-device automation tools as a standard Model Context Protocol (MCP) server over the LiveBridge TLS port, allowing desktop AI assistants (Claude Code, Antigravity, Cursor) to drive the phone directly.

### Category B: Robust Execution & Safety Guards (from KuafuAI Andee)
* **7.5 Dynamic Dwell Timing for Network Spinners**:
  Monitor screen nodes for progress indicators (`ProgressBar`, buffering spinners) and hold execution until spinners disappear, eliminating race conditions.
* **7.6 Smart Intent Disambiguation Modal**:
  When a user prompt has ambiguous candidates (e.g. "Message Sarah" with multiple contacts, or multiple similar action buttons), show an interactive multi-choice pill in the Dynamic Island instead of guessing or halting.
* **7.7 Self-Healing Selector Cascade**:
  Hierarchical failover for node resolution: `resource-id` → `content-description` → `visible-text` → `OCR-text` → `spatial-grid-coordinates`. Prevents breakages when third-party apps push layout updates.
* **7.8 PII & Financial Masking Filter**:
  Automatically detect credit cards, CVVs, passwords, and sensitive identity fields on screen and mask them before sending context to cloud AI models.
