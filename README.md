# GLOOM — Cross-Platform Glanceable Activity Surface & Connected Ecosystem
### Apple-inspired Dynamic Island-style overlay for Windows and Android

[![Latest Release](https://img.shields.io/github/v/release/NandiVardhan2007/GLOOM?color=007aff&label=Release&logo=github)](https://github.com/NandiVardhan2007/GLOOM/releases/latest)
[![Windows Desktop](https://img.shields.io/badge/Windows-11%20%7C%2010%20Overlay-0078d4.svg?logo=windows)](https://microsoft.com/windows)
[![Android Mobile](https://img.shields.io/badge/Android-Cutout--Aware%20Overlay-3ddc84.svg?logo=android)](https://developer.android.com)
[![Design Standard](https://img.shields.io/badge/Design-Apple--Inspired%20Tokens-black.svg)](DESIGN.md)
[![Architecture: Tauri 2](https://img.shields.io/badge/Desktop-Tauri%202%20%7C%20Rust%20%2B%20React%2019-ffc13b.svg?logo=tauri)](https://tauri.app)
[![Mobile: Jetpack Compose](https://img.shields.io/badge/Mobile-Jetpack%20Compose%20%7C%20Kotlin-4285f4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Build Status](https://github.com/NandiVardhan2007/GLOOM/actions/workflows/build.yml/badge.svg)](https://github.com/NandiVardhan2007/GLOOM/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**GLOOM** is an Apple-inspired Dynamic Island-style overlay and cross-device glanceable activity surface designed for Windows 11/10 and Android devices. It provides fluid spring mechanics, hardware camera cutout alignment, glassmorphic styling with high-contrast fallbacks, audio waveforms, secure local clipboard synchronization, authenticated file beaming, and an autonomous mobile agent with on-device memory and tool calling.

> **Important Platform Notice**: GLOOM is an independent, Apple-inspired overlay application for Windows and Android. It does not provide official Apple Dynamic Island or Apple Live Activities functionality. Official Apple Live Activities strictly require Apple ActivityKit and WidgetKit on iOS/iPadOS devices. iOS is currently unsupported.

---

## 🏛️ Platform Architecture & Features

### 1. Windows Desktop Overlay
- **Layered Window Architecture**: Built on Tauri 2, Rust, and React 19. Runs as a borderless topmost overlay with dynamic hit-testing pass-through (`WS_EX_LAYERED`, `WS_EX_TRANSPARENT`).
- **DPI & Multi-Monitor Geometry**: Respects monitor work areas (`rcWork`) to avoid top-docked taskbars, handles negative virtual-screen multi-monitor coordinates, and adapts to DPI scaling changes.
- **Fullscreen Suppression**: Monitors foreground applications for fullscreen and borderless-fullscreen states (e.g., games, presentations) and automatically transitions to the `suppressed` state with zero pointer capture.
- **Keyboard Navigation & A11y**: Full keyboard navigation (`Tab`, `Enter`, `Space`, `Escape`), ARIA accessibility attributes (`role="region"`, `aria-label`), visible focus rings, and high-contrast styling.
- **Apple HIG Geometry**: Native collapsed capsule (`230×37 px` or compact `180×37 px`), peek cards (`400×180 px`, `36 px` corner radius), and large expanded dashboard (`880×400 px`).

### 2. Android Cutout-Aware Overlay & Autonomous Mobile AI
- **Window Manager Overlay**: Built in Kotlin and Jetpack Compose utilizing Android `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
- **Stable Apple HIG Geometry**: Maintains a stable collapsed capsule width of `180 dp` (camera band clearance `125 dp`, wings `52 dp`) and expanded card geometry of `400 × 180 dp` with `36 dp` squircle corner radius without jarring collapse into the camera cutout.
- **Dynamic Cutout Adaptation**: Queries live `WindowInsets` and `DisplayCutout` coordinates for real-time camera alignment across center punch-holes, corner cutouts, waterdrop notches, and no-cutout displays.
- **Landscape Auto-Hide**: Automatically suppresses the Dynamic Island overlay when rotating into landscape mode to prevent obstructing full-screen video, games, or split-screen apps.
- **Autonomous Hands-On Agent (v1.5.0)**:
  - **Sub-2.5s Direct WhatsApp Automation**: Fully deterministic search and chat opening targeting contact name centers (`[250..600]px`), avoiding profile photos/avatars and Unread filter pills, typing directly into verified chat entry nodes (`com.whatsapp:id/entry`) without Meta AI false-positives.
  - **Needle-3 Offline Tool Caller**: Fully wired on-device grammar-constrained tool caller (`NeedleBridge.kt` + `needle3.cact`) providing sub-25ms intent prediction before cloud LLM invocation with zero network overhead.
  - **Lock Screen Pattern Unlock & Calibration**: Verified on physical hardware with default 0.80 grid ratio and 0.67 center-Y calibration; draws smooth 9-point gestures with micro-dwell and reliably dismisses Keyguard.
  - **Instagram Reel Counter Hardening**: Excludes settings and non-reel screens; rejects reverse scroll gestures to ensure accurate viewing counts.
  - **Automated Web Browsing & Form Autofill**: Navigates URLs in Chrome and autofills web forms with encrypted profile information (`Vault.UserProfile`) using accessibility node matching.
  - **Self-Developing Memory (CAG + MAG)**: Memory-Augmented Generation retains contact aliases (e.g. Amma `950864289`), personal facts, and procedural UI execution recipes with action verb guardrails preventing memory interception during task execution.
  - **Phonetics & Fuzzy App Launcher**: Recognizes phonetic aliases ("whats up", "crome", "you tube", "insta", "g mail", "netfliks") and applies Levenshtein distance string matching to prevent launch errors.
  - **System Productivity Tools**: Personalized email drafting, regex-based file search, universal desktop clipboard/data sync, and cache & duplicate file cleanup.
  - **One-Click Keys Importer**: Supports importing `keys.json` with multi-key Groq and OpenRouter keys directly into Android Keystore encrypted storage (`SecureKeyStore`).
  - **On-Device Diagnostics Logger**: Local JSONL execution trace and in-app Apple UIKit inspection viewer under **Settings → Automation → Execution Logs & Diagnostics** for auditing AI reasoning and touch gesture traces.
  - **FOD Optical Fingerprint Calibration**: Dedicated `CalibrationScreen` with Left/Right/High swipe routes that cleanly bypass in-display fingerprint sensors before pattern replay.

---

## 🔒 Security & Release Architecture

### 1. Hardened Desktop/Mobile LiveBridge
- **Certificate-Pinned TLS**: LiveBridge runs over mutual TLS (rustls on port 53391) with self-signed identity persisted in `%APPDATA%\GLOOM\bridge_cert.der` and `bridge_key.der`.
- **Mutual Token Authentication**: Every API request requires a shared `X-Gloom-Auth-Token` (or HTTP Bearer token). Unauthenticated requests are rejected with `401 Unauthorized`.
- **Replay Protection**: Enforces timestamp validation (`X-Gloom-Timestamp`) with maximum 60-second clock skew and a sliding-window nonce cache (`X-Gloom-Nonce`).
- **Strict Payload Limits**: Maximum 1 MB for control payloads; 2 GB streaming limit for file transfers with active cancellation (`/api/transfer/cancel`).
- **Streaming Integrity Verification**: Files are hashed on the fly via SHA-256 and checked against `X-Gloom-Checksum-Sha256` before atomic promotion to public Downloads.
- **Keystore Credential Isolation**: Cloud API keys and user profiles are stored in Android Keystore AES-256-GCM `EncryptedSharedPreferences`. No plaintext credentials ever touch storage, logs, or network payloads.

---

## 📐 Presentation State Architecture

GLOOM defines a 13-state cross-platform semantic presentation contract:

| State | Purpose | Windows Adaptation | Android Adaptation |
|:---|:---|:---|:---|
| `idle` / `compact` | Primary glanceable state | 230×37 px pill hugging screen top | 180×37 dp pill hugging camera cutout |
| `peek` | Transient glance (2.5s) | 400×180 px card previewing track/alert | 400×180 dp card previewing alert/OTP |
| `minimal` | Secondary multitasking badge | Collapsed auxiliary pill | Detached satellite bubble |
| `expandedActivity` | Interactive media/timer/calls | 400×180 px glass panel (36 px corners) | 400×180 dp card (36 dp corners) |
| `expandedControl` | Full system command center | Multi-tab dashboard and settings suite | Settings & controls overlay |
| `loading` | Asynchronous task in progress | Indeterminate progress shimmer | Progress indicator & island glow |
| `reconnecting` | Bridge connection retry | Amber status indicator | Amber reconnect badge |
| `error` | Transfer or auth failure | Red specular rim and alert text | Transient error banner |
| `completed` | Successful transfer / timer end | Green confirmation badge | Haptic confirmation & badge |
| `dismissed` | Dismissed by user | Return to `idle` | Collapse to cutout |
| `expired` | Activity timer finished | Pulsing amber ring | Audible/visual notification |
| `suppressed` | Fullscreen app / landscape active | Zero-height, zero-opacity, pass-through | Auto-hidden in landscape mode & games |

---

## 📱 Mobile Companion (Android)

- **Direct Download**: **[GLOOM-Island-Release (Latest Release)](https://github.com/NandiVardhan2007/GLOOM/releases/latest)**
- **Cutout Calibration**: Works automatically on punch-hole and notch displays with an optional preset for Motorola Edge 50 Pro.
- **In-App Keys Importer**: Supports importing `keys.json` with batch Groq and OpenRouter keys into Android Keystore encrypted storage.
- **Background Keep-Alive**: Foreground overlay service with proper notification channels, accessibility service hooks, and battery optimization settings.

---

## 🛠️ Building From Source

### Prerequisites
- **Node.js**: v20 or v22
- **Rust**: Stable toolchain (`rustup update stable`)
- **Android SDK**: API 35 / JDK 17

### 1. Build Desktop (Windows Setup Installer)
```powershell
cd app
npm install
npm run build
npm run tauri build
```
For the Inno Setup installer wizard:
```powershell
iscc installer/gloom.iss
```
The compiled Windows Setup installer wizard will be at `build-artifacts/GLOOM-Island-Setup.exe` (Inno Setup) or `app/src-tauri/target/release/bundle/nsis/GLOOM_1.5.0_x64-setup.exe` (Tauri NSIS). Standalone portable executables are replaced by official setup installers featuring Start Menu integration, desktop shortcuts, and an uninstaller.

### 2. Build Mobile (Android)
```bash
cd mobile/android
./gradlew assembleDebug
```
The compiled APK will be located at `mobile/android/app/build/outputs/apk/debug/app-debug.apk`. Install directly to a connected device via:
```bash
adb install -r mobile/android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.
