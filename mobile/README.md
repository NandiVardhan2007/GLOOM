# JARVIS Mobile — Dynamic Island & Ecosystem Bridge

An ultra-fluid, Apple & HarmonyOS-grade Dynamic Island for Android and mobile devices, powered by camera cutout detection, morphing spring physics, and real-time P2P Live Bridge connectivity with **JARVIS Desktop**.

---

## 🌟 Key Features Decoded & Synthesized

Synthesized from the reference repositories:
- **`MaterialYou-Dynamic-Island`** (Angel-Studio): Android `WindowManager` overlay service (`TYPE_APPLICATION_OVERLAY`), decoupled plugin host architecture, `MediaSessionManager` active session hooks, and battery charging state broadcast receivers.
- **`SmartIsland`** (agupta07505): Automatic `DisplayCutout` coordinate detection (center hole-punch, left/right cutout, teardrop notch), `LiveActivityParser` (food delivery, active timers, sports scores), VoIP call heads-up notifications, and swipe gesture controls.
- **`expressive-cutout`** (EvanKoe): Liquid spring cutout morphing (`IslandMotion`), satellite secondary detached bubble (`IslandSatellite`), dominant app icon color extraction for adaptive dynamic island glow, and center shortcut execution (tap cutout for flashlight/camera).
- **`livebridge`** (appsfolder): P2P cross-device synchronization engine, UDP multicast peer discovery, background keep-alive lifecycle, and universal clipboard mirroring.

---

## 📱 Architecture Overview

```
mobile/android/app/src/main/kotlin/com/jarvis/island/
├── MainActivity.kt                      # Permission onboarding, cutout preview, and bridge status
├── service/
│   ├── JarvisIslandOverlayService.kt    # WindowManager floating overlay on top of all Android apps
│   └── JarvisNotificationListener.kt    # Captures media, timers, messaging OTPs, and incoming calls
├── cutout/
│   ├── CutoutDetector.kt                # Reads windowInsets.displayCutout for hardware camera coords
│   └── CutoutMetrics.kt                 # Coordinates, pill width, punch-hole radius & center offset
├── motion/
│   ├── IslandMotionPhysics.kt           # Apple/HarmonyOS-tuned critically damped springs & bezier paths
│   └── SatelliteBubble.kt               # Detached secondary bubble for simultaneous multi-activities
├── bridge/
│   ├── JarvisLiveBridgeClient.kt        # Local UDP broadcast discovery & TCP HTTP sync with Desktop
│   ├── SharedClipboardManager.kt        # 2-way instant clipboard mirroring between PC and Phone
│   └── FileBeamReceiver.kt              # High-speed local WiFi file beam receiver (100+ MB/s)
├── plugins/
│   ├── MediaPlugin.kt                   # Spotify, YouTube Music, Apple Music playback & waveforms
│   ├── BatteryPlugin.kt                 # SuperVOOC/Fast Charging animated green ring & power alerts
│   └── LiveActivityPlugin.kt            # Live countdown timers, Uber/delivery status, and OTP copy
└── ui/
    ├── JarvisIslandPill.kt              # Collapsed pill hugging the physical camera cutout
    ├── JarvisExpandedIsland.kt          # Large interactive card on tap/swipe-down
    └── theme/JarvisTokens.kt            # Obsidian glass, dynamic neon cyan accents, typography
```

---

## 🔗 Cross-Device Ecosystem Synchronization (Desktop <-> Mobile)

1. **Auto-Discovery via UDP Beacon**:
   - Desktop and Mobile broadcast on UDP port `53390` with payload `{"type": "jarvis_beacon", "name": "...", "port": 53391}`.
   - Devices pair instantly with zero manual IP entry required.
2. **Universal 2-Way Clipboard**:
   - Copy any text or link on Windows -> instantly appears in mobile Dynamic Island.
   - Copy any OTP or SMS code on Mobile -> immediately available in desktop clipboard shelf.
3. **High-Speed File Beaming (AirDrop Alternative)**:
   - Drop any file onto the desktop Dynamic Island -> streams to mobile phone storage over local WiFi.
   - Send photos from mobile gallery -> directly saved in `Downloads/JARVIS/` on PC.
