# JARVIS — Cross-Platform Glanceable Activity Surface & Connected Ecosystem
### Apple-inspired Dynamic Island-style overlay for Windows and Android

[![Latest Release](https://img.shields.io/github/v/release/NandiVardhan2007/JARVIS?color=007aff&label=Release&logo=github)](https://github.com/NandiVardhan2007/JARVIS/releases/latest)
[![Windows Desktop](https://img.shields.io/badge/Windows-11%20%7C%2010%20Overlay-0078d4.svg?logo=windows)](https://microsoft.com/windows)
[![Android Mobile](https://img.shields.io/badge/Android-Cutout--Aware%20Overlay-3ddc84.svg?logo=android)](https://developer.android.com)
[![Design Standard](https://img.shields.io/badge/Design-Apple--Inspired%20Tokens-black.svg)](DESIGN.md)
[![Architecture: Tauri 2](https://img.shields.io/badge/Desktop-Tauri%202%20%7C%20Rust%20%2B%20React%2019-ffc13b.svg?logo=tauri)](https://tauri.app)
[![Mobile: Jetpack Compose](https://img.shields.io/badge/Mobile-Jetpack%20Compose%20%7C%20Kotlin-4285f4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Build Status](https://github.com/NandiVardhan2007/JARVIS/actions/workflows/build.yml/badge.svg)](https://github.com/NandiVardhan2007/JARVIS/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**JARVIS** is an Apple-inspired Dynamic Island-style overlay and cross-device glanceable activity surface designed for Windows 11/10 and Android devices. It provides fluid spring mechanics, hardware camera cutout alignment, glassmorphic styling with high-contrast fallbacks, audio waveforms, secure local clipboard synchronization, and authenticated file beaming across your devices.

> **Important Platform Notice**: JARVIS is an independent, Apple-inspired overlay application for Windows and Android. It does not provide official Apple Dynamic Island or Apple Live Activities functionality. Official Apple Live Activities strictly require Apple ActivityKit and WidgetKit on iOS/iPadOS devices. iOS is currently unsupported.

---

## 🏛️ Platform Architecture & Positioning

JARVIS cleanly separates platform adaptations to respect each operating system's native paradigms:

### 1. Windows Desktop Overlay
- **Layered Window Architecture**: Built on Tauri 2, Rust, and React 19. Runs as a borderless topmost overlay with dynamic hit-testing pass-through (`WS_EX_LAYERED`, `WS_EX_TRANSPARENT`).
- **DPI & Multi-Monitor Geometry**: Respects monitor work areas (`rcWork`) to avoid top-docked taskbars, handles negative virtual-screen multi-monitor coordinates, and adapts to DPI scaling changes.
- **Fullscreen Suppression**: Monitors foreground applications for fullscreen and borderless-fullscreen states (e.g., games, presentations) and automatically transitions to the `suppressed` state with zero pointer capture.
- **Keyboard Navigation & A11y**: Full keyboard navigation (`Tab`, `Enter`, `Space`, `Escape`), ARIA accessibility attributes (`role="region"`, `aria-label`), visible focus rings, and high-contrast styling.

### 2. Android Cutout-Aware Overlay
- **Window Manager Overlay**: Built in Kotlin and Jetpack Compose utilizing Android `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
- **Dynamic Cutout Adaptation**: Queries live `WindowInsets` and `DisplayCutout` coordinates for real-time camera alignment across center punch-holes, corner cutouts, waterdrop notches, and no-cutout displays.
- **Non-Blocking Touch Geometry**: Expanded activity cards occupy strictly bounded window bounds (`dpToPx(expandedHeight + positionY + 24)`), preventing background touch interception.
- **Accessibility & Motion**: TalkBack accessibility semantics, persistent high-contrast mode, and reduced-motion support (smooth crossfades replacing spring bounces).
- **Hardware Preset**: Calibrated hardware profile for devices including the Motorola Edge 50 Pro (1220×2712 px, 50% center punch-hole).

### 3. iOS Status
- **Currently Unsupported**: No iOS source tree exists within this repository.
- **Future Architecture**: A genuine iOS implementation will require a native Swift target utilizing ActivityKit, WidgetKit, and dynamic Live Activity push tokens.

---

## 🔒 Security & Release Architecture

### 1. Hardened Desktop/Mobile LiveBridge
- **Mutual Token Authentication**: Every API request requires a shared `X-Jarvis-Auth-Token` (or HTTP Bearer token). Unauthenticated requests are rejected with `401 Unauthorized`.
- **Replay Protection**: Enforces timestamp validation (`X-Jarvis-Timestamp`) with maximum 60-second clock skew and a sliding-window nonce cache (`X-Jarvis-Nonce`).
- **Strict Payload Limits**: Maximum 1 MB for control payloads; 2 GB streaming limit for file transfers with active cancellation (`/api/transfer/cancel`).
- **Streaming Integrity Verification**: Files are hashed on the fly via SHA-256 and checked against `X-Jarvis-Checksum-Sha256` before atomic promotion from `.tmp` storage.
- **Network Isolation**: Android enforces a strict `network_security_config.xml` restricting cleartext communication exclusively to local loopback and private RFC1918 subnets.
- **Privacy First**: Sensitive clipboard contents and file payloads are never logged to console or disk.

### 2. Safe Desktop Auto-Updater
- **Approved Release Host Enforcement**: Downloads are strictly restricted to official repository releases (`github.com/NandiVardhan2007/JARVIS/releases/download/` and `objects.githubusercontent.com`) over HTTPS.
- **Cryptographic & Binary Verification**: Updates verify SHA-256 checksums from signed manifests and validate PE executable headers (`MZ` magic bytes) before installation.
- **Downgrade Protection**: Rejects older or equal versions using semantic version parsing.
- **Atomic Swap with Rollback**: Replaces the binary via an atomic non-elevated script with automatic `.old` file restoration if replacement fails, eliminating unverified execution policies.

### 3. Safe Release Signing
- **No Embedded Secrets**: Hardcoded release passwords and embedded repository keystores have been completely removed.
- **Safe CI Failure**: Builds explicitly abort with descriptive errors if release signing secrets are not supplied in CI, preventing unsigned or insecure fallbacks.

---

## 📐 Presentation State Architecture

JARVIS defines a 13-state cross-platform semantic presentation contract:

| State | Purpose | Windows Adaptation | Android Adaptation |
|:---|:---|:---|:---|
| `idle` / `compact` | Primary glanceable state | 210×36 pill hugging screen edge | Cutout-enveloping pill (38 dp height) |
| `peek` | Transient glance (2.5s) | 320×44 card previewing track/timer/alert | Quick notification / activity preview |
| `minimal` | Secondary multitasking badge | Collapsed auxiliary pill | Detached satellite bubble |
| `expandedActivity` | Interactive media/timer | 420×190 glass panel with transport controls | Bounded expanded card (380×260 dp) |
| `expandedControl` | Full system command center | Multi-tab dashboard and settings suite | Settings & controls overlay |
| `loading` | Asynchronous task in progress | Indeterminate progress shimmer | Progress indicator |
| `reconnecting` | Bridge connection retry | Amber status indicator | Amber reconnect badge |
| `error` | Transfer or auth failure | Red specular rim and alert text | Transient error banner |
| `completed` | Successful transfer / timer end | Green confirmation badge | Haptic confirmation & badge |
| `dismissed` | Dismissed by user | Return to `idle` | Collapse to cutout |
| `expired` | Activity timer finished | Pulsing amber ring | Audible/visual notification |
| `suppressed` | Fullscreen app active | Zero-height, zero-opacity, pass-through | Auto-hidden during full-screen games |

---

## 📱 Mobile Companion (Android)

- **Direct Download**: **[JARVIS-Island-Release (Latest Release)](https://github.com/NandiVardhan2007/JARVIS/releases/latest)**
- **Cutout Calibration**: Works automatically on punch-hole and notch displays with an optional preset for Motorola Edge 50 Pro.
- **In-App Updater**: Verified download and installation over HTTPS from official release channels.
- **Background Keep-Alive**: Foreground overlay service with proper notification channels and battery optimization settings.

---

## 🛠️ Building From Source

### Prerequisites
- **Node.js**: v20 or v22
- **Rust**: Stable toolchain (`rustup update stable`)
- **Android SDK**: API 34+ / JDK 17

### 1. Build Desktop (Windows)
```powershell
cd app
npm install
npm run build
cargo build --release --manifest-path src-tauri/Cargo.toml
```
The compiled binary will be located at `app/src-tauri/target/release/jarvis.exe`.

### 2. Build Mobile (Android)
```bash
cd mobile/android
./gradlew assembleDebug
```
For release builds, configure CI signing secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`).

---

## 📄 License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.
