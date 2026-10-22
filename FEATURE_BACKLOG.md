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
| 1.5 | WhatsApp "send message to X saying Y" flow | ours | **P0 (Today)** | S | Verified logic; needs live UI element label calibration on device |
| 1.6 | Post-action verification (did the screen actually change?) | artemis | **Done** | — | `ActionVerifier`: outline signature before/after, one point retry, stateful labels never retried |
| 1.7 | Pre-execution safety net (XML first, pixels second) | artemis | **Done** | — | `SafetyGuard` checks the request and each target before acting; typing commands are not false-flagged |
| 1.8 | Flash vs Pro execution profiles | artemis | **P1 (Today)** | S/M | Flash = 1 model call per step; Pro = plan + checker agent loop in `AiPlanner` |
| 1.9 | Checkpoint plan with final report | artemis | **P2 (Today)** | S | Planner/Operator/Checker step reporting via TTS and Island |
| 1.10 | Action bursts / click sequences | artemis | **P1 (Today)** | S | Chain taps in rapid succession to beat transient Android controls |
| 1.11 | Screenshot aim grid + zoom/crosshair for self-drawn apps | andee | **Done** | — | `ScreenSnapshot` (6×10 luminance/edge grid), `tap_grid`/`tap_cell`/`tap_point`, auto-attached when the tree has ≤5 nodes |
| 1.12 | Per-location repetition guard | andee | **Done (planner)** | — | Router does not need it; planner stops after 2 same taps |
| 1.13 | History compression + `search_history` | artemis | **P2 (Today)** | S | Rolling window context summarizer for multi-step task chains |
| 1.14 | Screen recording, replay and video analyzer | artemis | **P2 (Today)** | M | Frame buffer ring for visual debug of failed automation |
| 1.15 | Scenes (saved goals + rules + routines) | andee | **Done (routines)** | — | `SceneStore` + Scenes UI: ordered saved steps, run/delete, stops at first failure |
| 1.16 | Notification triage: act / tell / ignore | andee | **Done** | — | `NotificationTriage` classifies locally (OTP/message/alert/promo); promos stay silent |
| 1.17 | Notebook memory + real scheduler | andee | **Done** | — | `Notebook` + `ReminderScheduler` (AlarmManager, boot re-arm, shade + island). `TimeParser` handles natural language |
| 1.18 | Vault: fill passwords without exposing them | andee | **P1 (Today)** | S/M | Keystore AES-GCM; encrypted credential store for automated form logins |
| 1.19 | SMS, calls, contacts, calendar, camera tools | andee | **Done** | — | `DeviceTools` uses public intents only; requires final user confirmation |
| 1.20 | On-device brain vs remote brain | andee | **Done** | — | `LocalBrain` answers offline; `aiBrain` = auto/local/remote |
| 1.21 | Offline tool calling with Needle | needle | **P1 (Today)** | M | ARM64 `libneedle.so` + `needle3.cact` in assets with JNI bindings for zero-network tool dispatch |
| 1.22 | Offline speech-to-text with Whistle | needle | **P2 (Today)** | M | 16.9 MB local ASR fallback when cloud voice recognition is unreachable |
| 1.23 | Custom tool schemas + grammar-constrained JSON | needle | **P1 (Today)** | S | Grammar enforcement to prevent malformed LLM tool calls |
| 1.24 | Confidence-based act / confirm / refuse | needle | **P1 (Today)** | S | Calibrated threshold: low confidence prompts user before execution |
| 1.25 | ADBKeyboard fallback for API < 33 | andee | **P2 (Today)** | S | Legacy fallback if installed on devices older than Android 13 |
| 1.26 | MIUI/WeChat tree helper | andee | **P2 (Today)** | S | Virtual hierarchy helper if custom OEM skins obscure accessibility tree |
| 1.27 | On-device self-check (permissions, services, keys) | andee/artemis | **Done** | — | `AutomationSelfCheck` drives the Readiness rows in Automation settings |
| 1.28 | **Standalone Mobile AI (Direct In-App Keys & Cloud Fallback)** | ours | **Done** | — | `SecureKeyStore` (Keystore-encrypted), `StandaloneAiClient` (Groq/OpenRouter with key rotation), `AiRouter` fallback, Settings → AI & Brain → Cloud API Keys |
| 1.29 | **Pattern & Swipe Calibration (Bypass FOD)** | ours | **Done** | — | `CalibrationScreen`: Left/Right/High swipe path bypasses in-display optical fingerprint; 3×3 grid scale/offset alignment; live phone preview card; test gesture hooks |

---

## 2. Notifications

| # | Feature | Source | Priority | Effort | Notes |
|---|---|---|---|---|---|
| 2.1 | Silent-channel filtering | ours | **Done** | — | Toggle in Behavior settings |
| 2.2 | Dedupe (content 3 s, key 600 ms/20 s) | ours | **Done** | — | Eliminates duplicate alert echoes |
| 2.3 | Skip group/autogroup summaries | ours | **Done** | — | Stops second alert after cancellation |
| 2.4 | Single island renderer | ours | **Done** | — | Accessibility service takes sole ownership |
| 2.5 | Per-app allow list for island alerts | ours | **Done** | — | Muted packages enforced in listener; searchable monogram UI |
| 2.6 | Inline reply / quick actions in the island | ours | **P1 (Today)** | S/M | Android `RemoteInput` integration to reply directly from Island expanded peek |
| 2.7 | OTP extraction and one-tap copy | ours | **Done** | — | `NotificationTriage.extractOtp`; code pill + "Copy Code" in both islands |
| 2.8 | Desktop notifications → island activity | handoff §4B | **Done** | — | WinRT toast listener + `jarvis://notification`; Island peek for 6s |

---

## 3. Instagram / Facebook Reel Counter

| # | Feature | Priority | Effort | Notes |
|---|---|---|---|---|
| 3.1 | Daily + lifetime counter, Home card, reset | **Done** | — | `ReelCounter`, `InstagramReelTracker` |
| 3.2 | Minimum dwell time filter (1,200 ms) | **Done** | — | `reelMinDwellMs` ensures fast swipes are not counted |
| 3.3 | Per-app screen-time summary | **P2 (Today)** | S/M | Aggregated daily usage breakdown across social and media apps |
| 3.4 | Weekly chart on the Home card | **Done** | — | `ReelWeekChart` with animated critical spring physics |
| 3.5 | Count Reels watched inside Facebook | **Done** | — | Dual-package tracking for Instagram and Facebook |

---

## 4. Needle Integration Plan (Offline Tool Calls)

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

### Sprint 2: Needle Local Engine, Inline Reply & Password Vault (Midday)
* [ ] Bundle `libneedle.so` and `needle3.cact` with JNI bindings for local tool calls.
* [ ] Wire Android `RemoteInput` into `JarvisIslandAccessibilityService` for inline notification quick replies inside the expanded island.
* [ ] Implement `Vault.kt`: Encrypted credentials store using Keystore AES-GCM to allow the agent to autofill logins without plain-text exposure.
* [ ] Add action burst chaining (1.10) in `ScreenAutomation` to handle disappearing popups and timed prompts.

### Sprint 3: Advanced Intelligence & Analytics (Afternoon)
* [ ] Implement Flash vs. Pro profiles in `AiPlanner.kt`:
  - **Flash Profile**: Single-pass reactive tool caller for instant execution.
  - **Pro Profile**: Plan → Execute → Verify → Self-Correction loop with final status summary.
* [ ] Add per-app screen-time tracking (3.3) to `ReelCounter.kt` and expand Home Activity card.
* [ ] Integrate rolling context history compression (1.13) for deep multi-step automation chains.

### Sprint 4: Full Regression, Tagging & Release (Evening)
* [ ] Execute complete test battery:
  - Desktop: `npm run build`, `cargo check --release --features custom-protocol`, `cargo test`.
  - Android: `./gradlew :app:assembleRelease`, `./gradlew :app:testDebugUnitTest`.
* [ ] Push commit and tag `v1.2.0` to trigger automated GitHub Actions build for signed Windows EXE and Android APK.
