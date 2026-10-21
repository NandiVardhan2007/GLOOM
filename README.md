#  JARVIS Dynamic Island & Connected Ecosystem
### Apple-Grade Live Activities, Dynamic Island & Continuity for Windows and Android

[![Latest Release](https://img.shields.io/github/v/release/NandiVardhan2007/JARVIS?color=007aff&label=Release&logo=github)](https://github.com/NandiVardhan2007/JARVIS/releases/latest)
[![Windows 11 Compatible](https://img.shields.io/badge/Windows-11%20%7C%2010-0078d4.svg?logo=windows)](https://microsoft.com/windows)
[![Android Version](https://img.shields.io/badge/Android-9.0--15.0%20(API%2028%2B)-3ddc84.svg?logo=android)](https://developer.android.com)
[![Design Standard](https://img.shields.io/badge/Design-Apple%20HIG%20%7C%20Live%20Activities-black.svg?logo=apple)](DESIGN.md)
[![Architecture: Tauri 2](https://img.shields.io/badge/Desktop-Tauri%202%20%7C%20Rust%20%2B%20React%2019-ffc13b.svg?logo=tauri)](https://tauri.app)
[![Mobile: Jetpack Compose](https://img.shields.io/badge/Mobile-Jetpack%20Compose%20%7C%20Kotlin-4285f4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Build Status](https://github.com/NandiVardhan2007/JARVIS/actions/workflows/build.yml/badge.svg)](https://github.com/NandiVardhan2007/JARVIS/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**JARVIS** is an ultra-fluid, Apple-grade Dynamic Island and Connected Ecosystem designed for Windows 11/10 and Android companion devices. Built strictly according to the official **[Apple Human Interface Guidelines for Live Activities](https://developer.apple.com/design/human-interface-guidelines/live-activities)** and Emil Kowalski's fluid physical spring mechanics (`apple-design`), JARVIS brings true hardware punch-hole integration, glassmorphism, real-time audio waveforms, LiveBridge clipboard synchronization, and 5G local file beaming into a single unified companion.

---

## ⚡ Quick Start: 1-Line Windows Desktop Installer

Install JARVIS on Windows directly from PowerShell. This single line downloads the latest release binary from GitHub Releases, installs to `%LOCALAPPDATA%\JARVIS-Island`, creates Start Menu and Desktop shortcuts, and launches the app:

```powershell
powershell -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/NandiVardhan2007/JARVIS/main/install-desktop.ps1 | iex"
```

> **Uninstallation**: Running `uninstall.ps1` in `%LOCALAPPDATA%\JARVIS-Island` cleanly cleans all shortcuts and files.

---

## 📱 Mobile Companion (Android APK)

Download the signed release APK directly to your phone:

- **Direct Download**: **[JARVIS-Island-Release-v1.0.apk (Latest Release)](https://github.com/NandiVardhan2007/JARVIS/releases/latest)**
- **In-App Auto Updater**: Go to the **About** screen inside the mobile app and tap **Check for Updates** to detect and install releases directly from GitHub.

---

## 💎 Apple HIG Live Activities & Dynamic Island Design

JARVIS follows the complete presentation hierarchy specified by Apple's design system (documented in [DESIGN.md](DESIGN.md)):

```
┌────────────────────────────────────────────────────────────────────────┐
│ [compactLeading: 20x20]         [Center: Live Metric]        [compactTrailing: Waveform] │ ◄ Compact
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│  ┌───────────┐  Track Title (SF Pro 14pt Bold)                         │ ◄ .center
│  │  Artwork  │  Artist Name (SF Pro 12pt Regular, 60% White)          │
│  │  Squircle │  ──────────────────────────────────────────             │
│  │  (.leading│  0:42 ━━━━━━━●───────────────────── -2:18 (Tabular Nums)│ ◄ .bottom
│  └───────────┘  [◄◄]        [ ▶ / ❚❚ ]        [►►]        [AirPlay]    │
│  ────────────────────────────────────────────────────────────────────  │
│  [Dashboard] [Media] [Bridge] [Controls] [Bluetooth] [Timer] [Stats] [Net]│
└────────────────────────────────────────────────────────────────────────┘
```

1. **Compact Presentation**:
   - `compactLeading`: Context glyph or 18×18 squircle album thumbnail (`4.5px` continuous radius).
   - `compactTrailing`: Vibrant live audio visualizer equalizer (`#34C759` or accent cyan) or focus countdown timer.
   - `center`: Recessed pill capsule with live tabular clock or status.
2. **Minimal Presentation**:
   - Detached circular or capsule badge during concurrent multitasking activities.
3. **Expanded Presentation (4-Region Apple HIG Layout)**:
   - `.leading`: Continuous squircle album art / app avatar.
   - `.trailing`: Live equalizer bars or companion status badge.
   - `.center`: Typographic hierarchy with SF Pro Display Bold titles and SF Pro Text subtitles.
   - `.bottom`: Full-width scrub bar with elapsed and remaining timestamps in tabular numerals (`tnum`), flanked by tactile circular media transport buttons.
4. **Lock Screen Presentation**:
   - `14pt` standard layout padding conforming to Apple HIG.
   - Liquid dark translucent glass with specular hairline border (`0.5px solid rgba(255, 255, 255, 0.14)`).

---

## 🎯 Motorola Edge 50 Pro Hardware Calibration

The Android companion overlay is calibrated with pixel-perfection for the **Motorola Edge 50 Pro**:

- **Display**: 1220 × 2712 px, 446 PPI (6.7" pOLED, 144 Hz)
- **Punch-Hole Alignment**:
  - Horizontal Center: `610 px` (50.0% dead center across display)
  - Vertical Center: `52 px` (~`18.6 dp` from active screen top edge)
  - Hole Diameter: `48–52 px` (~`17.2–18.6 dp`)
- **Calibrated Island Metrics**:
  - **Top Margin**: `2 px` default margin as requested.
  - **Pill Height**: `38 dp` (~`106 px`), seamlessly enveloping the physical camera from `Y = 5.5 px` to `Y = 111.5 px`.
  - **Cutout Clearance Gap**: `32 dp` dead-zone, providing `7 dp` safe pitch-black margins on both sides of the physical lens.
  - **Wingspan**: `184 dp` closed width, `380 dp × 260 dp` expanded card with `28.5 dp` side gutters.
- **One-Tap Preset**: Accessible anytime in **Settings → Position & Alignment → Apply Motorola Edge 50 Pro Preset**.

---

## 🔗 LiveBridge Ecosystem Features

- **Bi-Directional Clipboard Sync**: Copy text on Windows and paste instantly on Android (and vice-versa) with zero friction.
- **Drag-to-Beam File Sharing**: Drag any file onto the Windows Dynamic Island to immediately beam it over local Wi-Fi Direct (Port 53391) to your Android companion without third-party cloud servers.
- **Dedicated Network Dashboard**: Monitor live download/upload throughput, Wi-Fi Direct interface stats, and companion socket ping directly inside the island ([NetworkView.tsx](app/src/components/NetworkView.tsx)).
- **Volume HUD**: Hardware volume changes trigger a non-focus-stealing temporary HUD pill.
- **Hardware Telemetry**: Real-time CPU%, RAM usage, GPU load, and disk metrics.

---

## 🛠️ Building From Source

### Prerequisites
- **Node.js**: v20 or v22
- **Rust**: Stable toolchain (`rustup update stable`)
- **Android SDK**: API 34+ / JDK 17

### 1. Build Desktop (Windows EXE)
```powershell
cd app
npm install
npm run build
cargo build --release --manifest-path src-tauri/Cargo.toml
```
The compiled binary will be located at `app/src-tauri/target/release/jarvis.exe`.

### 2. Build Mobile (Android APK)
```bash
cd mobile/android
./gradlew assembleRelease
```
The compiled signed APK will be located at `mobile/android/app/build/outputs/apk/release/app-release.apk`.

---

## 🚀 CI/CD & Automated GitHub Releases

Every push to `main` triggers a complete multi-platform CI/CD pipeline via GitHub Actions ([.github/workflows/build.yml](.github/workflows/build.yml)):
1. **Android Build**: Compiles release APK with zero warnings using JDK 17 and Android SDK.
2. **Windows Build**: Compiles release EXE with Rust and Vite frontend.
3. **Automated Publishing**: Automatically creates a new GitHub Release with an auto-incrementing tag (`v1.0.<run_number>`) and attaches both release binaries.

---

## 📄 License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.
