# JARVIS Ecosystem — System Architecture

**Document Version:** 1.0.0  
**Last Updated:** 2026-10-20  
**Status:** Approved & Living Document  

---

## 1. High-Level Vision & System Boundaries

JARVIS is a unified, personal AI ecosystem that seamlessly connects **Windows 11** and **Android** devices into a single, cohesive operating experience. It is structured into three coordinated layers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                              JARVIS AI                                 │
│  • Task Planner & Natural Language Engine   • Multi-Model Router       │
│  • Desktop Computer Use Tool Registry       • Mobile Accessibility Bot │
│  • Human-in-the-Loop Approval Controller    • Context & Memory Store   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                           JARVIS Connect                               │
│  • P2P TLS/Authenticated Transport          • UDP Discovery Beacon     │
│  • Versioned Protocol Schema (v1.0)         • Universal 2-Way Clipboard│
│  • High-Speed LAN File Streamer (AirDrop)   • Notification Mirroring   │
│  • Cross-Device Remote Media & Telemetry    • Task State Syncer        │
└──────────────────┬──────────────────────────────────┬──────────────────┘
                   │                                  │
┌──────────────────▼───────────────┐  ┌───────────────▼──────────────────┐
│     JARVIS Desktop (Windows)     │  │     JARVIS Companion (Android)   │
│  • Transparent DWM Dynamic Island│  │  • DisplayCutout Camera Overlay  │
│  • In-Island Settings & Cards    │  │  • Jetpack Compose Island Pill   │
│  • Win32 / UIA Desktop Automation│  │  • Android Accessibility Service │
│  • Pass-Through Hit-Testing      │  │  • Live Activities & OTP Beamer  │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 JARVIS Desktop (Windows Application)
- **Path:** [`d:\Coding\JARVIS\Desktop`](file:///d:/Coding/JARVIS/Desktop) (aliased to `app/`)
- **Technology Stack:**
  - **Core Runtime:** Tauri 2.x with Rust backend.
  - **Frontend UI:** React 19, TypeScript, Vite, Framer Motion (`motion/react`), Lucide React.
  - **OS Integration:** Windows API (`windows` crate v0.62) for Win32 window styles, transparent click pass-through (`WM_NCHITTEST`), DWM blur/refraction, System Media Transport Controls (SMTC), WASAPI audio endpoints, display brightness (DDC/CI & WMI), global hotkeys.
- **Key Modules:**
  - `window.rs`: Transparent layered window with multi-monitor positioning and dynamic hit rects.
  - `providers/media.rs`: Real-time media session monitoring and SMTC control.
  - `providers/system.rs`: CPU, RAM, Battery, Disk, and Network telemetry collection.
  - `providers/clipboard.rs`: Win32 clipboard sequence tracking with deduplication.
  - `providers/livebridge.rs`: Cross-device communication hub (UDP beacon on `53390`, TCP/HTTP on `53391`).
  - `components/Island.tsx`: Critically damped fluid spring container (`stiffness: 460`, `damping: 34`, `mass: 0.6`).
  - `components/ExpandedPanel.tsx`: Tabbed navigation (Dashboard, Media, Live Bridge, Notchy & AI, Controls, Bluetooth, Clipboard, Focus Timer, Activity, Network).
  - `components/SettingsView.tsx`: In-island 860×520 configuration suite.
  - `components/LiveBridgeView.tsx`: Cross-device connection status, universal AirDrop file beaming, and OTP quick-copy.

### 2.2 JARVIS Companion (Android Application)
- **Path:** [`d:\Coding\JARVIS\Android`](file:///d:/Coding/JARVIS/Android) (aliased to `mobile/android`)
- **Technology Stack:**
  - **Language:** Kotlin 2.x.
  - **UI Framework:** Jetpack Compose + WindowManager System Overlay.
  - **Target SDK:** Android 14+ (API 34/35), Min SDK 26 (Android 8.0).
- **Key Modules:**
  - `cutout/CutoutDetector.kt`: Discovers hardware `DisplayCutout` coordinates, hole-punch geometry, and notch offsets.
  - `motion/IslandMotionPhysics.kt`: Android `PathInterpolator` curves matching desktop fluid spring expansion.
  - `service/JarvisIslandOverlayService.kt`: Persistent floating overlay using `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE`.
  - `service/JarvisNotificationListener.kt`: Captures media sessions, OTP codes, navigation steps, and active timers.
  - `bridge/JarvisLiveBridgeClient.kt`: Auto-discovery via UDP beacon on port `53390`, bidirectional clipboard push, and file streaming.

### 2.3 JARVIS Connect (Shared Communication Protocol)
- **Transport:**
  - **Discovery:** Multicast UDP beacon (`255.255.255.255:53390`) broadcasting `{"type": "jarvis_beacon", "name": "...", "port": 53391}`.
  - **Data Channel:** Local TCP stream / HTTP REST with TLS encryption on port `53391`.
- **Event Types:**
  - `device.paired`, `device.presence`, `connection.status`
  - `file.transfer.request`, `file.transfer.progress`, `file.transfer.completed`, `file.transfer.failed`
  - `clipboard.updated` (includes origin device ID to prevent infinite sync loops)
  - `notification.received`, `notification.action`
  - `media.state.changed`
  - `ai.task.started`, `ai.task.progress`, `ai.task.completed`, `ai.task.failed`, `ai.approval.required`

### 2.4 JARVIS AI (Desktop & Mobile Agent Layer)
- **Tool Registry Architecture:**
  - Typed input and output schemas with parameter validation.
  - Risk categorization: `Read-Only`, `Low-Risk Interactive`, `Consequential / High-Impact` (requiring human confirmation).
- **Execution Drivers:**
  - **Desktop:** Windows UI Automation (UIA) via structured tree inspection, with fallback to Win32 mouse/keyboard automation.
  - **Web:** Playwright browser automation with deterministic locators.
  - **Mobile:** Android Accessibility Service (`AccessibilityNodeInfo`) inspection, action dispatching, and intent launches.
- **Safety Boundary:** AI model inference is decoupled from privileged system execution; no unrestricted raw shell access is granted.

---

## 3. Data Flow & Security Boundaries

```
[User Action / Voice / Click]
               │
               ▼
┌──────────────────────────────┐
│     Dynamic Island UI        │
└──────────────┬───────────────┘
               │ Invokes Typed IPC Command
               ▼
┌──────────────────────────────┐
│     Rust / Native Backend    │
└──────┬────────────────┬──────┘
       │                │
       │ Local Actions  │ Network Stream (TLS / Port 53391)
       ▼                ▼
┌──────────────┐ ┌────────────────────────────────────────┐
│ Win32 / UIA  │ │ JARVIS Connect Protocol Engine         │
│ Telemetry    │ ├──────────────────┬─────────────────────┤
│ Media / Audio│ │  Origin Tracker  │ Dedup & Replay Guard│
└──────────────┘ └────────┬─────────┴─────────────────────┘
                          │ Encrypted WiFi P2P
                          ▼
                 ┌────────────────────────────────────────┐
                 │ Mobile LiveBridge Client (Android)     │
                 └────────┬───────────────────────────────┘
                          │
                          ▼
                 ┌────────────────────────────────────────┐
                 │ Android WindowManager / Overlay        │
                 └────────────────────────────────────────┘
```

---

## 4. Technology Decisions & Rationale

| Layer | Selected Technology | Alternative Evaluated | Reason for Decision |
|---|---|---|---|
| **Desktop Shell** | Tauri 2 (Rust) + React 19 | Electron | 40MB memory footprint vs 350MB+ in Electron; native Windows DWM transparency support; instant startup. |
| **Desktop UI Animation** | Framer Motion with critically-damped spring | Pure CSS / Web Animations API | Native physics-based velocity tracking and interruption handling during interactive hover sweeps. |
| **Mobile Core** | Kotlin + Jetpack Compose | Flutter / React Native | Direct low-level access to `DisplayCutout`, `WindowManager.LayoutParams`, and `NotificationListenerService` without JNI bridging overhead. |
| **Local Transfer** | LocalSend-style HTTP stream + UDP discovery | Cloud Relay / WebRTC | 100% offline functionality, maximum LAN throughput (100+ MB/s over WiFi 6), zero cloud hosting costs. |
| **Automation** | Windows UI Automation (UIA) + Playwright | Raw coordinates | Immune to screen scaling, window position shifts, and DPI changes. |
