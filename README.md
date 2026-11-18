<div align="center">

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo.svg">
    <source media="(prefers-color-scheme: light)" srcset="assets/logo-dark.svg">
    <img src="assets/logo.svg" width="140" height="140" alt="GLOOM Logo" style="filter: drop-shadow(0 0 24px rgba(255, 255, 255, 0.25));" />
  </picture>

  # G L O O M

  ### Next-Generation Dynamic Island, Fluid Desktop Dock & Desktop Companion
  **A seamless, glanceable activity surface, fluid desktop dock & acrylic window suite crafted for Windows 11 and Windows 10**

  <p align="center">
    <a href="https://github.com/NandiVardhan2007/GLOOM/releases/latest"><img src="https://img.shields.io/github/v/release/NandiVardhan2007/GLOOM?style=for-the-badge&color=007aff&label=Release&logo=github" alt="Latest Release" /></a>
    <a href="https://github.com/NandiVardhan2007/GLOOM/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/NandiVardhan2007/GLOOM/build.yml?style=for-the-badge&label=Build&logo=githubactions&logoColor=white" alt="Build Status" /></a>
    <a href="https://microsoft.com/windows"><img src="https://img.shields.io/badge/Windows-11%20%7C%2010-0078d4?style=for-the-badge&logo=windows&logoColor=white" alt="Windows Desktop" /></a>
    <a href="https://tauri.app"><img src="https://img.shields.io/badge/Desktop-Tauri%20v2%20%7C%20Rust-ffc13b?style=for-the-badge&logo=tauri&logoColor=black" alt="Tauri 2" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-success?style=for-the-badge" alt="MIT License" /></a>
  </p>

  <p align="center">
    <a href="#-what-is-gloom">What is GLOOM</a> •
    <a href="#-apple-design--dynamic-island-features">Dynamic Island</a> •
    <a href="#-on-demand-acrylic-windows--calendar-sync">Acrylic Windows</a> •
    <a href="#-cross-platform-desktop-power">Windows Desktop Power</a> •
    <a href="#-dual-island-styles-notch--pill">Island Styles</a> •
    <a href="#-installation--quickstart">Install</a> •
    <a href="#-building-from-source">Build from Source</a> •
    <a href="#-support--sponsorship">Sponsor</a>
  </p>

</div>

---

## ⚡ What is GLOOM?

**GLOOM** brings the fluid elegance of Apple's **iOS Dynamic Island** and the tactile delight of a fluid desktop dock to **Windows 11 and Windows 10**. It sits unobtrusively at the periphery of your screen — expanding with fluid spring physics when active and melting into the bezel when idle.

Engineered with Rust, Tauri v2, React 19, and Framer Motion, GLOOM adheres strictly to Apple's **Human Interface Guidelines (HIG)**: true OLED black materials, specular micro-rim illumination, 40px glass diffusion, organic phosphor green waveforms, and critically damped harmonic springs.

> [!NOTE]
> **Independent Implementation**: GLOOM is an independent overlay application engineered in Rust, Tauri, and React 19. It does not utilize Apple proprietary code or require macOS/iOS.

---

## 🍏 Apple Design & Dynamic Island Features

<table>
  <tr>
    <td width="50%">
      <h3 align="center">🏝️ Apple Dynamic Island (iOS 18 HIG)</h3>
      <ul>
        <li><b>Pure OLED Glass Materials</b>: True <code>#000000</code> background with heavy glass backdrop blur (<code>blur(40px) saturate(210%)</code>) and specular top micro-rim light reflection.</li>
        <li><b>Harmonic Spring Physics</b>: Synchronized physics model (<code>stiffness: 380, damping: 32, mass: 0.8</code>) governing width, height, position, and squircle corner radii with instant pointer-down tap response (<code>whileTap={{ scale: 0.97 }}</code>).</li>
        <li><b>Intuitive Gestural Interaction</b>: Click the capsule to expand, click the header to toggle closed, click the desktop backdrop or press <kbd>Esc</kbd> to dismiss. Zero accidental collapses during hover or control navigation.</li>
        <li><b>Phosphor Lime Equalizer</b>: Reactive audio visualizer streaming live frequency harmonics in glowing phosphor green gradients (<code>#30D158</code> to <code>#34C759</code>).</li>
        <li><b>Apple Music Experience</b>: Continuous album art squircle, dynamic dual-radial artwork backlight aura with breathing playback pulse, native <b>LRCLIB synced lyrics engine</b> with multi-tier waterfall resolution, proximity-based dynamic typography (active, near, distant), shimmering skeleton loader, track marquee, elapsed/remaining time stamps, and mouse wheel volume scrubbing.</li>
        <li><b>Tactile Sound Engine</b>: Synthesized zero-latency Web Audio micro-transients for snaps, ticks, switches, and pops.</li>
        <li><b>Multi-Mode Visualizer</b>: Switch between Phosphor Bars, Liquid Wave, and Breathing Halo animations.</li>
        <li><b>Unified Productivity Drawer</b>: 3-tab Apple drawer with Framer Motion sliding glass pill indicator, integrating File Stash, Win32 Clipboard History ring buffer (last 15 items), and Persistent Quick Scratchpad with dynamic keyboard focus management.</li>
        <li><b>Live Activity System</b>: Real-time browser download watcher tracking active downloads with animated progress shimmers.</li>
        <li><b>SF Pro Typography</b>: Optical tracking and tabular numbers (<code>tabular-nums</code>) across clocks, timers, volume sliders, and telemetry indicators.</li>
      </ul>
    </td>
    <td width="50%">
      <h3 align="center">🖥️ Cross-Platform Desktop Power</h3>
      <ul>
        <li><b>Multi-Platform Linux Support</b>: Native X11 and Wayland (XWayland) integration on <b>Zorin OS</b>, <b>Ubuntu</b>, and <b>Debian</b> via WebKitGTK and Cairo input regions.</li>
        <li><b>Zero-Warning Clean Console</b>: Intercepted and silenced benign FUSE <code>statvfs</code> and Tracker3 IPC messages for clean, noise-free execution.</li>
        <li><b>Window Region Clipping</b>: Pixel-accurate click pass-through outside the pill capsule; captures full input when expanded for controls and outside-click dismissal.</li>
        <li><b>Fullscreen Intelligence</b>: Auto-suppresses when full-screen 3D games, presentations, or video players gain focus.</li>
        <li><b>Glanceable Control Center</b>: iOS 18 glass tiles for quick Wi-Fi management, Bluetooth devices, volume slider, brightness control, and battery telemetry.</li>
        <li><b>macOS-Style Magnifying Dock</b>: Optional fluid dock for Windows featuring true continuous magnification ($B=42\text{px}$, $M=72\text{px}$, $g=12\text{px}$), launch bounce, and smooth app launching.</li>
      </ul>
    </td>
  </tr>
</table>

---

## 🪟 On-Demand Acrylic Windows & Calendar Sync

GLOOM features a dedicated multi-window desktop companion architecture instantiated on-demand with native Windows 11 acrylic materials:

- **📅 Calendar Surface (`calendar.html`)**:
  - Full-fidelity monthly agenda and event viewer powered by native Rust synchronization (`caldav.rs`, `ics.rs`, `calendar_sync.rs`).
  - Seamless CalDAV integration with iCloud, Google Calendar, Fastmail, Nextcloud, and public ICS web feeds.
  - Windows Credential Manager integration for secure token and password storage.
- **⚙️ Comprehensive Settings Suite (`settings.html`)**:
  - 11 dedicated configuration tabs: **General**, **Appearance**, **Notch**, **Dock**, **Overlays**, **Calendar Sync**, **Agent Intro**, **Agent Sounds**, **Diagnostics**, **Processes**, and **About**.
  - **Live Fuzzy Search Engine**: Instant multi-keyword search across all settings with deep linking directly to target rows.
  - Granular opacity, saturation, brightness, and accent color pickers with real-time preview.
- **🚀 Dual-Theme Vector SVG Branding**:
  - 100% vector SVG rendering using `assets/logo.svg` across all surfaces.
  - **Adaptive Contrast**: Automatically switches to dark vector icon (`assets/logo-dark.svg` / `/gloom-dark.svg`) on light backgrounds, and light vector icon (`/gloom.svg`) on dark/black OLED backgrounds.
  - Zero raster pixelation on high-DPI displays.

---

## 🎨 Dual Island Styles: Notch & Pill

GLOOM adapts to your personal setup with two primary geometries:

```
       [ FLUSH BEZEL NOTCH ]                        [ FLOATING PILL ]
       
   ─────────────────────────────────           ┌───────────────────────────────┐
   ───────┐                 ┌───────           │                               │
           \   GLOOM NOTCH   /                 │       (  GLOOM ISLAND  )      │
            └───────────────┘                  │                               │
```

1. **Bezel Notch Mode (Default)**:
   - Anchored flush against the screen's top bezel (`Y = 0`).
   - Features **C1-continuous cubic-bezier concave shoulder flares** on both sides, matching modern MacBook hardware aesthetics.
2. **Floating Pill Mode**:
   - Compact capsule sitting with a crisp `10px` offset below the top edge.
   - Continuous squircle curvature (`22px` radius resting, morphing to `34–38px` expanded).
   - Ideal for centered multi-monitor setups, ultrawides, and clean minimalist desktops.

---

## 📁 Repository Structure

The codebase is organized into clean, focused directories:

```
GLOOM/
├── Desktop/                  # Desktop Tauri Workspace (Windows & Linux)
│   ├── src/                  # React 19 + TypeScript Frontend
│   │   ├── components/       # DynamicIsland, QuickPanel, StatusWidgets
│   │   ├── settings/         # Appearance, Notch, General tabs
│   │   └── icons/            # Apple SF-inspired SVG icons
│   └── src-tauri/            # Rust Backend (Cross-Platform)
│       ├── src/              # App entrypoint, state, updater, audio
│       └── src/platform/
│           └── linux/        # Native Linux (Cairo regions, wpctl, bluetoothctl, GTK)
├── installer/                # Inno Setup Windows Installer Configuration
├── scripts/                  # Version stamping & release automation
└── tests/                    # Configuration and geometry test suites
```

---

## 🛠️ Installation & Quickstart

### 🪟 Windows 11 / 10

#### 1. Dedicated Setup Wizard (.exe)
Download **[`GLOOM-Island-Setup.exe`](https://github.com/NandiVardhan2007/GLOOM/releases/latest)** from the latest GitHub Release. Run the setup wizard to install GLOOM with Start Menu integration, desktop shortcut, and seamless background auto-updating.

#### 2. Quick One-Liner (PowerShell)
Open PowerShell and run:

```powershell
irm https://raw.githubusercontent.com/NandiVardhan2007/GLOOM/main/scripts/install.ps1 | iex
```

---

### 🧹 Clean Uninstallation

To remove GLOOM and all associated caches, configurations, and shortcuts without leaving any leftovers:

```powershell
irm https://raw.githubusercontent.com/NandiVardhan2007/GLOOM/main/scripts/uninstall.ps1 | iex
```

---

## 🔨 Building from Source

### Prerequisites
- **Node.js**: v20 or v22
- **Rust Toolchain**: Stable (`rustup update stable`)
- **System Dependencies**: Visual Studio C++ Build Tools, Inno Setup 6 (optional, for `.exe` installer)

### Build Instructions (Windows)
```powershell
# 1. Install Node dependencies & build frontend
cd Desktop
npm install
npm test
npm run build

# 2. Compile Windows release executable
cargo build --release --manifest-path src-tauri/Cargo.toml

# 3. Optional: Compile Inno Setup installer wizard
iscc ..\installer\gloom.iss
```

---

## 💖 Support & Sponsorship

GLOOM is free and open-source software built with craft and precision. If GLOOM enhances your daily workflow, consider sponsoring the project to support continuous development!

<div align="center">

### 🇮🇳 UPI Support (India & Global UPI)

Scan or send via any UPI application (Google Pay, PhonePe, Paytm, BHIM):

```
┌──────────────────────────────────────────┐
│                                          │
│   UPI ID:  7337419275-2@ybl              │
│   Name:    GLOOM Development Project     │
│                                          │
└──────────────────────────────────────────┘
```

<a href="upi://pay?pa=7337419275-2@ybl&pn=GLOOM&cu=INR">
  <img src="https://img.shields.io/badge/Pay%20via-UPI%20%7C%207337419275--2%40ybl-2563eb?style=for-the-badge&logo=googlepay&logoColor=white" alt="UPI Donation" />
</a>

</div>

---

## 📄 License

This repository is licensed under the terms of the **[MIT License](LICENSE)**.

```
Copyright (c) 2026 GLOOM Contributors
```
