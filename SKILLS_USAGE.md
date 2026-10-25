# GLOOM Skills & Tools Usage Guide

> **Purpose**: Detailed operational guide for all AI agent skills and external tooling installed in the GLOOM repository. Use this document to execute skills accurately without confusion or hallucination.

---

## 1. `graphify` (Codebase Knowledge Graph)

### Overview
`graphify` analyzes the entire multi-language codebase (Kotlin, Rust, TypeScript, Python, XML, Shell) and compiles an interconnected knowledge graph with community clustering, god nodes, and dependency paths into `graphify-out/`.

### Directory Layout
* Graph definition & node edges: `graphify-out/graph.json`
* Interactive visual web graph: `graphify-out/graph.html`
* Architectural summary: `graphify-out/GRAPH_REPORT.md`
* Manifest & labels: `graphify-out/manifest.json`

### CLI Execution
`graphify` is installed in Python environment and executable via `python -m graphify`:

```powershell
# Query graph for architecture or symbol connections
python -m graphify query "<question>"

# Find shortest dependency path between two components
python -m graphify path "<NodeA>" "<NodeB>"

# Detailed concept breakdown
python -m graphify explain "<concept>"

# Update graph after modifying code files (AST-only, no API cost)
python -m graphify update .
```

### ⚠️ Python 3.14 Compatibility Note
In Python 3.14, `@dataclass(init=False, slots=True)` in `networkx/utils/configs.py` triggers an `AttributeError` when inspecting `__annotate__` on `wrapper_descriptor`. The file `C:\Users\nandu\AppData\Roaming\Python\Python314\site-packages\networkx\utils\configs.py` is configured with `slots=False` to ensure smooth execution without crashing.

---

## 2. `apple-design` (Fluid Interface & Physical Motion)

### Overview
Located at `.agents/skills/apple-design/`. Translates Apple's interface guidelines, spring mechanics, typography, and optical hierarchies to the web and Android UI.

### Core Principles Applied in GLOOM
1. **Apple HIG Fluid Spring Curves**:
   * Uses Apple's signature critically damped spring: `spring(dampingRatio = 0.82f, stiffness = 380f)` (response ≈ 0.35s) for all island morphing, card expansion, and collapse transitions.
2. **Interactive Touch Scale (WWDC Designing Fluid Interfaces)**:
   * Instantaneous touch response: island compresses to `0.97f` scale on pointer-down using `spring(dampingRatio = 0.82f, stiffness = 420f)` and springs back to `1.0f` on release.
3. **Tabular Numerics**:
   * Timers, percentages, and counters MUST use tabular/fixed-width figures (`IslandType.compactNumber`) to prevent text jitter during ticking.
4. **Restraint & Materials**:
   * Content is never crowded; secondary text uses `Apple.secondaryLabel` (`Color(0x99EBEBF5)`), and borders use sub-pixel translucency (`0x1FFFFFFF`) or theme-specific radiant neon strokes.

---

## 3. `karpathy-guidelines` (LLM Coding Integrity)

### Overview
Located at `.agents/skills/karpathy-guidelines/`. Reduces common LLM coding pitfalls by enforcing disciplined software engineering:
1. **Surgical Changes**: Avoid rewriting large untouched blocks of code. Make targeted drop-in edits.
2. **Preserve Comments & Docstrings**: Never delete existing architectural notes, comments, or explanations unless explicitly instructed.
3. **Verify Before Declaring Victory**: Never assume a build or test passes without running the compiler and test suite directly.

---

## 4. Android Toolchain & ADB Execution

### Environment Paths
* **ADB Binary**: `adb` on system PATH (or `C:\Users\nandu\AppData\Local\Android\Sdk\platform-tools\adb.exe`)
* **Gradle Wrapper**: `D:\Coding\GLOOM\Android\gradlew.bat`
* **Output APK**: `D:\Coding\GLOOM\Android\app\build\outputs\apk\debug\app-debug.apk`

### Standard ADB Workflows
```powershell
# 1. Check connected devices
adb devices -l

# 2. Re-install APK without losing data
adb -s ZD222NB59D install -r D:\Coding\GLOOM\Android\app\build\outputs\apk\debug\app-debug.apk

# 3. Clean install (if signatures mismatch)
adb -s ZD222NB59D uninstall com.gloom.island
adb -s ZD222NB59D install D:\Coding\GLOOM\Android\app\build\outputs\apk\debug\app-debug.apk

# 4. Launch main activity
adb -s ZD222NB59D shell am start -n com.gloom.island/.MainActivity

# 5. Broadcast test events
adb -s ZD222NB59D shell am broadcast -a com.gloom.island.ACTION_TEST_MUSIC
adb -s ZD222NB59D shell am broadcast -a com.gloom.island.ACTION_TOGGLE_EXPAND
adb -s ZD222NB59D shell am broadcast -a com.gloom.island.ACTION_TEST_BEAM
```
