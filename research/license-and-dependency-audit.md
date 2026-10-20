# License & Dependency Audit: Project jarvis

## 1. Upstream Repositories License Inventory

| Repository | Upstream Author | Declared License | Redistribution Obligations | Compatibility Risk with jarvis |
| :--- | :--- | :--- | :--- | :--- |
| **Cyclop** (`akalikbergenov/cyclop`) | akalikbergenov | **MIT License** | Preserve copyright & permission notice | **Compatible** (Permissive) |
| **Glance** (`imgaty/Glance`) | Gaty | **MIT License** | Preserve copyright & permission notice | **Compatible** (Permissive) |
| **V-Notch** (`rainaku/V-Notch`) | rainaku | **Apache License 2.0** | Preserve notice, license, attribution | **Compatible** (Permissive with patent grant) |
| **Windows Dynamic Island** (`HeyyCzer/windows-dynamic-island`) | HeyyCzer | **GNU General Public License v3.0 or later (GPL-3.0-or-later)** | Full source disclosure of all linked works, copyleft viral requirement | **INCOMPATIBLE with permissive licensing**. Direct code copying is strictly prohibited. |
| **Nectar** (`kua8/Nectar`) | kua8 (Fork of Bloom) | **GNU General Public License v3.0 (GPL-3.0-only)** | Full source disclosure of all linked works, copyleft viral requirement | **INCOMPATIBLE with permissive licensing**. Direct code copying is strictly prohibited. |

---

## 2. Legal Strategy: Clean-Room Independent Implementation

To ensure that **Project jarvis** is freely redistributable, enterprise-safe, and clean from any copyleft viral taint:
1. **Clean-Room Implementation**: jarvis does not copy, paste, or incorporate any source files, routines, or assets directly from `windows-dynamic-island` or any GPL-licensed code.
2. **Concept & Specification Synthesis**: Design concepts, public Windows API usage patterns (such as standard Win32 calls like `SetWindowSubclass`, `DwmExtendFrameIntoClientArea`, and standard WinRT `GlobalSystemMediaTransportControlsSessionManager`), and physical animation constants are extracted as ideas and re-implemented from first principles.
3. **Permissive Licensing**: Project jarvis is licensed under the permissive **MIT License**.
4. **Third-Party Attribution**: Explicit attribution to the original inspirational projects is maintained in `docs/THIRD_PARTY_NOTICES.md`.

---

## 3. Production Dependency Manifest for jarvis

To prevent the massive dependency bloat observed in V-Notch (which bundled ONNX Runtime, LLamaSharp, and Vulkan DLLs) and avoid untrusted transitive npm packages, jarvis enforces a minimal, audited dependency tree:

### 3.1 Rust Backend (`Cargo.toml`)
- `tauri` (v2.x): Application runtime, event bridge, and window manager. License: MIT / Apache-2.0.
- `windows` (v0.62+): Official Microsoft Win32 and WinRT API bindings. License: MIT / Apache-2.0.
- `serde`, `serde_json`: High-speed zero-copy serialization for IPC. License: MIT / Apache-2.0.
- `tokio`: Async runtime for non-blocking I/O and SMTC events. License: MIT.
- `log`, `env_logger`: Lightweight logging framework. License: MIT / Apache-2.0.

### 3.2 Frontend (`package.json`)
- `react`, `react-dom` (v19.x): Declarative component tree. License: MIT.
- `motion` (v13.x): Hardware-accelerated sub-pixel spring animation physics. License: MIT.
- `lucide-react`: Lightweight, clean, consistent vector SVG icon set. License: MIT.
- `vite` (v8.x), `typescript` (v5/v7.x): High-speed compile and bundling pipeline. License: MIT.

Every chosen production dependency uses a standard, vetted permissive license (MIT / Apache-2.0), ensuring 100% legal integrity.
