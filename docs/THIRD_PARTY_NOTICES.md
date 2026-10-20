# Third-Party Notices and Acknowledgements

Project jarvis is an original, clean-room implementation of a Windows Dynamic Island and Notch system. It was designed following a thorough architectural and visual analysis of several prominent open-source notch projects.

We gratefully acknowledge the technical insights and design patterns explored in the following reference projects:

---

### 1. Cyclop
- **Author**: akalikbergenov
- **Source**: https://github.com/akalikbergenov/cyclop
- **License**: MIT License
- **Copyright**: (c) 2026 akalikbergenov
- **Contribution to jarvis**: Pointer watcher dual-rate sampling (8Hz idle / 60Hz warm), hysteresis zones, and multi-display geometry caching.

---

### 2. Glance
- **Author**: Gaty
- **Source**: https://github.com/imgaty/Glance
- **License**: MIT License
- **Copyright**: (c) 2026 Gaty
- **Contribution to jarvis**: Canonical spring animation physics (`response: 0.35s`, `dampingFraction: 1.0`), discrete peek micro-swell interaction, 6-bar audio visualizer proportions (1.3x gap ratio), and rubber-band easing mathematics.

---

### 3. V-Notch
- **Author**: rainaku
- **Source**: https://github.com/rainaku/V-Notch
- **License**: Apache License 2.0
- **Copyright**: Copyright 2026 rainaku
- **Contribution to jarvis**: Win32 window subclassing patterns, `WM_NCHITTEST` pass-through concepts, and `FullscreenDetector` logic for filtering desktop and shell windows.

---

### 4. Windows Dynamic Island
- **Author**: HeyyCzer
- **Source**: https://github.com/HeyyCzer/windows-dynamic-island
- **License**: GNU General Public License v3.0 or later
- **Note**: Inspected as a reference for Windows GSMTC and PDH counter concepts. No source code, binary assets, or GPL-licensed materials were copied into Project jarvis. Project jarvis is an independent, clean-room implementation under the MIT license.

---

### 5. Nectar
- **Author**: kua8 (Fork of Bloom by Sehajveer Singh)
- **Source**: https://github.com/kua8/Nectar
- **License**: GNU General Public License v3.0
- **Note**: Inspected as a reference for anti-minimize subclassing (`WM_SYSCOMMAND` + `SC_MINIMIZE`), dynamic text marquee mechanics, and remaining countdown formatting (`-M:SS`). No source code, binary assets, or GPL-licensed materials were copied into Project jarvis. Project jarvis is an independent, clean-room implementation under the MIT license.
