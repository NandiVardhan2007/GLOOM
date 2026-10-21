# JARVIS Mobile — Android Cutout-Aware Overlay & Ecosystem Bridge

An Apple-inspired Dynamic Island-style overlay for Android devices, powered by dynamic camera cutout detection, natural spring physics, TalkBack accessibility, and secure peer-to-peer LiveBridge connectivity with **JARVIS Desktop**.

> **Platform Notice**: JARVIS Mobile is an independent Android overlay application. It does not provide official Apple Dynamic Island or Apple Live Activities functionality. Official Apple Live Activities strictly require Apple ActivityKit and WidgetKit on iOS/iPadOS devices.

---

## 🌟 Key Features

- **Dynamic DisplayCutout Alignment**: Queries Android `WindowInsets.getDisplayCutout()` to automatically adapt to center punch-holes, corner cutouts, waterdrop notches, and no-cutout displays.
- **Bounded Overlay Geometry**: Expanded cards are strictly bounded (`dpToPx(expandedHeight + positionY + 24)`) so touches to underlying applications are never blocked.
- **Hardware Preset Calibration**: Built-in profile calibrated for devices including the **Motorola Edge 50 Pro** (1220×2712 px, 50% horizontal center).
- **Accessibility & Motion Options**: TalkBack semantics, high-contrast mode, and reduced-motion settings (smooth crossfades replacing spring bounces).
- **Secure Cross-Device LiveBridge**: Authenticated local P2P sync with replay protection and SHA-256 file integrity validation.

---

## 🔒 Security Architecture

- **Scoped Network Traffic**: Cleartext communication is explicitly disabled globally (`android:usesCleartextTraffic="false"`) and restricted via `network_security_config.xml` strictly to local loopback and private RFC1918 subnets (`127.0.0.1`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
- **Token Authentication**: All bridge interactions require an `X-Jarvis-Auth-Token` matching desktop configuration.
- **Replay Protection**: Validates request timestamps (≤60s clock skew) and sliding-window nonces to reject duplicate requests.
- **Streaming SHA-256 Validation**: Incoming files are streamed and verified against `X-Jarvis-Checksum-Sha256` without buffering entire files in memory.
- **Safe CI Release Signing**: Embedded credentials and hardcoded passwords have been completely removed; builds abort safely if release signing secrets are absent.

---

## 📱 Project Architecture

```
mobile/android/app/src/main/kotlin/com/jarvis/island/
├── MainActivity.kt                      # Permission onboarding, cutout preview, and bridge status
├── service/
│   ├── JarvisIslandOverlayService.kt    # Bounded WindowManager overlay on top of Android apps
│   ├── JarvisIslandAccessibilityService.kt # Fallback accessibility overlay
│   └── JarvisNotificationListener.kt    # Captures media, timers, messaging OTPs, and incoming calls
├── cutout/
│   ├── CutoutDetector.kt                # Reads WindowInsets.displayCutout with no-cutout fallback
│   └── CutoutMetrics.kt                 # Coordinates, pill width, punch-hole radius & center offset
├── motion/
│   ├── IslandMotionPhysics.kt           # Spring mechanics & reduced-motion interpolators
│   └── SatelliteBubble.kt               # Detached secondary bubble for concurrent activities
├── bridge/
│   ├── JarvisLiveBridgeClient.kt        # Authenticated UDP discovery & HTTP sync with Desktop
│   ├── SharedClipboardManager.kt        # Secure 2-way clipboard mirroring without cleartext logging
│   └── FileBeamReceiver.kt              # Streaming SHA-256 verified file beam receiver
├── settings/
│   └── JarvisIslandSettings.kt          # Persistent settings (reduced motion, high contrast, alignment)
├── plugins/
│   ├── MediaPlugin.kt                   # Media playback, active sessions, and waveform data
│   ├── BatteryPlugin.kt                 # Charging state indicator and battery percentage
│   └── LiveActivityPlugin.kt            # Timers, delivery notifications, and OTP previews
└── ui/
    ├── JarvisIslandApp.kt               # Main Jetpack Compose overlay host
    ├── JarvisIslandPill.kt              # Collapsed pill hugging the camera cutout
    ├── JarvisExpandedIsland.kt          # Bounded interactive card on tap/swipe
    └── theme/JarvisTokens.kt            # Obsidian black, functional glass, and dynamic accents
```

---

## 🛠️ Building & Testing

### Debug Build
```bash
cd mobile/android
./gradlew assembleDebug
```

### Unit Tests
```bash
cd mobile/android
./gradlew test
```

### Release Build
Release builds require CI secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`). Without these secrets, `assembleRelease` terminates safely with a configuration error.
