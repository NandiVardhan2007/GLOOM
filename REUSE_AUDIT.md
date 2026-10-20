# JARVIS Ecosystem — Open-Source Reuse & License Audit

**Document Version:** 1.0.0  
**Last Updated:** 2026-10-20  
**Status:** Approved & Living Document  

---

## 1. Policy & Classification Methodology

Every open-source candidate evaluated from [`references.md`](file:///d:/Coding/JARVIS/references.md) is categorized into one of five classification tiers:

1. **`reuse candidate`**: Permissive license (MIT, Apache-2.0, BSD), clean architecture, suitable for direct library inclusion or adaptation.
2. **`protocol/architecture reference`**: Design, payload schema, or interaction model studied and adapted cleanly behind JARVIS interfaces (no copy-pasting of code).
3. **`UI reference`**: Visual layout, animation curves, and styling patterns used for aesthetic inspiration.
4. **`research prototype`**: Evaluated for technical feasibility, benchmarks, or test tooling.
5. **`not suitable`**: Incompatible license (copyleft conflicts without isolation), unmaintained, or redundant.

---

## 2. Detailed Repository Audit Matrix

| Repository | Declared License | Classification | Reusable Elements & Usage Strategy |
|---|---|---|---|
| **[KDE Connect](https://github.com/KDE/kdeconnect-android)** | GPL-2.0 | `protocol/architecture reference` | Study TLS pairing handshake, packet types (`kdeconnect.clipboard`, `kdeconnect.telephony`), and device capability discovery. Implement clean, independent Rust/Kotlin protocol. |
| **[LocalSend](https://github.com/localsend/localsend)** | GPL-3.0 | `protocol/architecture reference` | Study HTTP/REST chunked file transfer endpoints, multicast UDP discovery beacon on LAN, and receiver confirmation dialog UX. |
| **[Sefirah](https://github.com/shrimqy/Sefirah)** | GPL-3.0 | `protocol/architecture reference` | Reference for Windows-first Android integration, SMS/call sync ideas, and Shizuku/ADB clipboard access patterns. |
| **[MaterialYou-Dynamic-Island](https://github.com/Angel-Studio/MaterialYou-Dynamic-Island)** | Apache-2.0 | `reuse candidate` | Android `WindowManager` overlay service (`TYPE_APPLICATION_OVERLAY`), decoupled plugin host architecture, and battery receiver. |
| **[SmartIsland](https://github.com/agupta07505/SmartIsland)** | MIT / Apache-2.0 | `reuse candidate` | Hardware `DisplayCutout` coordinate detection, Live Activity parsing, and gesture sensitivity. |
| **[expressive-cutout](https://github.com/EvanKoe/expressive-cutout)** | Apache-2.0 | `reuse candidate` | Morphing bezier paths with corner tension around physical camera hole (`IslandMotion`), satellite secondary detached bubble (`IslandSatellite`). |
| **[livebridge](https://github.com/appsfolder/livebridge)** | MIT | `reuse candidate` | Android notification listener pipeline, live activity dictionary, and OTP quick-copying. |
| **[Microsoft UFO](https://github.com/microsoft/UFO)** | MIT | `reuse candidate` | Windows UI Automation (UIA) tree navigation, multi-step task decomposition, and application state verification. |
| **[pywinauto](https://github.com/pywinauto/pywinauto)** | BSD-3-Clause | `reuse candidate` | Win32 and UI Automation control finding, typing, clicking, and window geometry. |
| **[FlaUI](https://github.com/FlaUI/FlaUI)** | MIT | `protocol/architecture reference` | UIA3 patterns and COM interface usage for Windows UI accessibility. |
| **[Playwright](https://github.com/microsoft/playwright)** | Apache-2.0 | `reuse candidate` | Deterministic browser automation, locator engine, and form autofill. |
| **[Model Context Protocol (MCP)](https://github.com/modelcontextprotocol/python-sdk)** | MIT | `reuse candidate` | Typed tool interface schema for exposing JARVIS capabilities to AI agents. |
| **[OmniParser](https://github.com/microsoft/OmniParser)** | MIT | `research prototype` | Visual screenshot parsing for custom-drawn canvas or remote desktop controls without UIA metadata. |
| **[whisper.cpp](https://github.com/ggml-org/whisper.cpp)** | MIT | `research prototype` | On-device lightweight speech-to-text inference for voice input. |
| **[DynamicWin](https://github.com/FlorianButz/DynamicWin)** | CC BY-SA 4.0 | `UI reference` | Visual widgets and file tray concept. Do not copy code due to non-software CC BY-SA terms. |
| **[WinIsland](https://github.com/WinIslandProject/WinIsland)** | GPL-3.0 | `UI reference` | Aesthetic and animation reference for Windows Dynamic Island. |

---

## 3. Attribution & License Compliance Plan

- **Permissive Libraries (MIT, Apache-2.0, BSD-3):** Retain all original copyright notices and license headers in third-party notice files (`THIRD_PARTY_NOTICES.md`).
- **GPL References (KDE Connect, LocalSend, WinIsland):** No GPL source code is directly copied or linked into the proprietary application core; only independent protocol implementations following clean-room engineering standards are used.
