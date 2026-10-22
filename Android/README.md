# GLOOM Mobile — Android Cutout-Aware Overlay & Autonomous Agent

An Apple-inspired Dynamic Island-style overlay for Android devices, powered by dynamic camera cutout detection, natural spring physics, TalkBack accessibility, an autonomous on-device mobile agent, and secure peer-to-peer LiveBridge connectivity with **GLOOM Desktop**.

> **Platform Notice**: GLOOM Mobile is an independent Android overlay application. It does not provide official Apple Dynamic Island or Apple Live Activities functionality. Official Apple Live Activities strictly require Apple ActivityKit and WidgetKit on iOS/iPadOS devices.

---

## 🌟 Key Features

- **Apple HIG Geometry & Proportions**:
  - Default collapsed pill width of `180 dp` with camera band clearance of `125 dp` and `52 dp` side wings.
  - Default expanded pill card of `400 × 180 dp` with `36 dp` continuous squircle corners.
  - Bounded touch geometry (`dpToPx(expandedHeight + positionY + 44)`) preventing accidental background touches.
  - **Landscape Auto-Hide**: Dynamic Island automatically suppresses itself when rotating into landscape mode to prevent obstructing full-screen video, games, and split-screen workflows.
- **Dynamic DisplayCutout Alignment**: Queries Android `WindowInsets.getDisplayCutout()` to automatically adapt to center punch-holes, corner cutouts, waterdrop notches, and no-cutout displays.
- **Hardware Preset Calibration**: Built-in profile calibrated for devices including the **Motorola Edge 50 Pro** (1220×2712 px, 50% horizontal center).
- **Autonomous Hands-On Agent Loop**:
  - **WhatsApp Single-Type Deduplication**: Contact names and messages are verified and deduplicated before typing (`typeTextUnique`), preventing repeated typing in search boxes or chat fields.
  - **Automated Web Browsing & Form Autofill**: Navigates URLs in Chrome and autofills web forms with encrypted profile information (`Vault.UserProfile`) using accessibility node matching.
  - **Self-Developing Memory (CAG + MAG)**: Memory-Augmented Generation retains contact aliases, personal facts, and procedural UI execution recipes. Cache-Augmented Generation provides sub-second semantic plan replay. Execution feedback loop reinforces successful execution paths.
  - **Phonetics & Fuzzy App Launcher**: Recognizes phonetic aliases ("whats up", "crome", "you tube", "insta", "g mail", "netfliks") and applies Levenshtein distance string matching to prevent launch errors.
  - **Needle-3 Offline Tool Caller**: Bundles quantized Cactus-3 model asset (`needle3.cact`) with a sub-20ms fallback grammar engine for zero-network intent execution.
  - **System Productivity Tools**: Personalized email drafting, regex-based file search, universal desktop clipboard/data sync, and cache & duplicate file cleanup.
  - **One-Click Keys Importer**: Supports importing `keys.json` with multi-key Groq and OpenRouter keys directly into Android Keystore encrypted storage (`SecureKeyStore`).
  - **FOD Optical Fingerprint Calibration**: Dedicated `CalibrationScreen` with Left/Right/High swipe routes that cleanly bypass in-display fingerprint sensors before pattern replay.
- **Secure Cross-Device LiveBridge**: Certificate-pinned TLS (rustls on port 53391) peer-to-peer sync with replay protection and SHA-256 file integrity validation.

---

## 🔒 Security Architecture

- **Keystore AES-256-GCM Credential Vault**: Cloud API keys (Groq, OpenRouter) and personal profile data are encrypted via Android Keystore `MasterKey` inside `EncryptedSharedPreferences`. Plaintext keys are never stored on disk or logged.
- **Scoped Network Traffic**: Cleartext communication is explicitly disabled globally (`android:usesCleartextTraffic="false"`) and restricted via `network_security_config.xml` strictly to local loopback and private RFC1918 subnets (`127.0.0.1`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
- **Token Authentication & Replay Protection**: All bridge interactions require an `X-Gloom-Auth-Token` matching desktop configuration, with request timestamp validation (≤60s clock skew) and sliding-window nonces.
- **Streaming SHA-256 Validation**: Incoming files are streamed and verified against `X-Gloom-Checksum-Sha256` directly into public Downloads without buffering entire files in memory.

---

## 📱 Project Architecture

```
Android/app/src/main/kotlin/com/gloom/island/
├── MainActivity.kt                      # Settings suite, calibration preview, and bridge status
├── service/
│   ├── GloomIslandOverlayService.kt    # Bounded WindowManager overlay on top of Android apps
│   ├── GloomIslandAccessibilityService.kt # Accessibility touch injector & fallback overlay
│   ├── GloomNotificationListener.kt    # Captures media, timers, messaging OTPs, and incoming calls
│   └── VoiceUnlockActivity.kt           # Screen-wake and pattern replay unlock controller
├── ai/
│   ├── AiMemory.kt                      # Self-developing memory module (CAG semantic cache + MAG entity graph)
│   ├── NeedleBridge.kt                  # Quantized Needle-3 offline tool caller & grammar engine
│   ├── KeyBatchImporter.kt              # Batch keys.json importer into EncryptedSharedPreferences
│   ├── StandaloneAiClient.kt            # Direct Groq & OpenRouter HTTPS caller with key rotation
│   └── AiRouter.kt                      # Fallback routing: Desktop LiveBridge → Direct Cloud → LocalBrain
├── automation/
│   ├── AiPlanner.kt                     # Reactive Observe → Plan → Act loop (Flash vs Pro profiles)
│   ├── CommandRouter.kt                 # Local deterministic command router (cleanSpeech + tool dispatch)
│   ├── ScreenAutomation.kt              # Numbered element outline, typeTextUnique, tapSequence, tapBurst
│   ├── SystemTools.kt                   # Email drafting, regex file search, desktop sync, storage cleanup
│   ├── InstagramReelTracker.kt          # Dwell-time validated Instagram reel counter
│   ├── ScreenTimeTracker.kt             # UsageStatsManager screen-time analytics
│   └── ActionVerifier.kt                # Post-action screen outline change verification
├── security/
│   └── Vault.kt                         # AES-256-GCM encrypted user profile and web form autofill
├── voice/
│   └── AppLauncher.kt                   # Phonetic aliases and Levenshtein fuzzy app launcher
├── cutout/
│   ├── CutoutDetector.kt                # Reads WindowInsets.displayCutout with no-cutout fallback
│   └── CutoutMetrics.kt                 # Coordinates, pill width, punch-hole radius & center offset
├── bridge/
│   ├── GloomLiveBridgeClient.kt        # Authenticated TLS connection & clipboard sync with Desktop
│   ├── SharedClipboardManager.kt        # Secure 2-way clipboard mirroring without cleartext logging
│   └── FileBeamReceiver.kt              # Streaming SHA-256 verified file beam receiver to Downloads
├── settings/
│   └── GloomIslandSettings.kt          # Persistent settings, geometry defaults, and legacy migration
└── ui/
    ├── AppleIslandSpec.kt               # HIG geometry tokens: 180dp collapsed, 400x180dp expanded, 36dp radius
    ├── GloomIslandApp.kt               # Main Jetpack Compose overlay host with landscape suppression
    ├── IslandVisualComponents.kt        # Apple-styled music, notification, call, and transfer cards
    ├── CalibrationScreen.kt             # Pattern unlock & FOD swipe calibration
    ├── CloudAiScreen.kt                 # Cloud API keys management and batch keys.json upload
    ├── VaultScreen.kt                   # Personal profile and credentials vault
    └── theme/GloomTokens.kt            # Obsidian black, functional glass, and dynamic accents
```

---

## 🛠️ Building & Testing

### Debug Build & Install
```bash
cd Android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Unit Tests
```bash
cd Android
./gradlew test
```

### Release Build
Release builds require CI secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`). Without these secrets, `assembleRelease` terminates safely with a configuration error.
