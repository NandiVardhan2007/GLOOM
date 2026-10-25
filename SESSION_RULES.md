# GLOOM Session Operating Rules & Anti-Hallucination Guidelines

> **Status**: MANDATORY & ALWAYS-ACTIVE  
> **Applies To**: Every AI assistant, pair programmer, and developer operating in the GLOOM repository.  
> **Repository Root**: `D:\Coding\GLOOM`

---

## 1. Prime Directives (Non-Negotiable)

1. **Strict Adherence to `INSTRUCTIONS.md`**:
   - Every session **MUST strictly follow `INSTRUCTIONS.md`** without exception.
   - Never violate core architectural invariants:
     * Overlay window width MUST ALWAYS be `WindowManager.LayoutParams.MATCH_PARENT`.
     * Zero text, glyphs, or content across the central camera punch-hole cutout band (`Spacer(Modifier.width(cameraGap))`).
     * Official installed app icons ONLY for all notification activities.
     * Dynamic input focus switching (`setInputFocusEnabled`) and soft keyboard (IME) lifecycle management.
     * Apple HIG fluid physics: `spring(dampingRatio = 0.82f, stiffness = 380f)` and interactive touch scale (`0.97f`).

2. **Mandatory End-of-Session `HANDOFF.md` Update**:
   - At the conclusion of **EVERY SINGLE SESSION**, the agent **MUST AND SHOULD UPDATE `HANDOFF.md`**.
   - `HANDOFF.md` is the single source of truth for session continuity. It must document:
     * Exactly what was implemented or fixed during the session.
     * Any new architectural decisions, bug root causes, and invariants established.
     * Verification results (tests passed, build status, device deployment).
     * The immediate next steps for the incoming session.

3. **Zero Hallucination Protocol**:
   - **Verify Before Stating**: Never guess or speculate on file paths, method names, or component states. Read the actual files or query `graphify`.
   - **No Ghost APIs**: Use only verified Android SDK, Jetpack Compose, Tauri, or project APIs. If unsure whether a class or function exists, inspect the source or grep first.
   - **Check Connected Devices**: Before claiming an app is installed, query `adb devices` and verify the exit code of `adb install -r`.
   - **Ask Rather Than Assume**: If a user requirement is ambiguous or underspecified, clarify before implementing speculative code.

---

## 2. Session Lifecycle: Step-by-Step Procedure

### Phase 1: Session Kickoff & Orientation
At the beginning of any session or prompt:
1. **Read `HANDOFF.md`**: Understand the latest state, ongoing tasks, recent bug fixes, and active commitments.
2. **Read `INSTRUCTIONS.md`**: Refresh the architectural rules, invariants, and styling standards.
3. **Consult `graphify`**:
   - For codebase navigation or architecture questions, first run `python -m graphify query "<question>"`.
   - Check `graphify path "<A>" "<B>"` for relationship traces.

### Phase 2: Implementation & Surgical Execution
1. **Apply Karpathy Guidelines**:
   - Make minimal, targeted, drop-in changes. Do not refactor unrelated code.
   - Preserve all existing comments, docstrings, and architectural explanations.
   - Minimum code that solves the user's problem — nothing speculative.
2. **Respect the Target Device Hardware**:
   - Target mobile device is the **Motorola Edge 50 Pro** (`ZD222NB59D`).
   - Hardware cutout is a centered top camera hole. Never let text overflow the center gap.

### Phase 3: Verification & Quality Gate
Never report completion without executing the verification pipeline:
```powershell
# 1. Desktop Verification
npm test --prefix Desktop
npm run build --prefix Desktop
cargo test --manifest-path Desktop/src-tauri/Cargo.toml

# 2. Android Compilation & Unit Tests
cd D:\Coding\GLOOM\Android
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest

# 3. Assemble Debug APK
.\gradlew.bat assembleDebug

# 4. Stream Install to Device (if connected)
adb -s ZD222NB59D install -r D:\Coding\GLOOM\Android\app\build\outputs\apk\debug\app-debug.apk

# 5. Launch Main Activity
adb -s ZD222NB59D shell am start -n com.gloom.island/.MainActivity
```

### Phase 4: Session Wrap-Up (MANDATORY)
1. **Update `graphify` AST Graph**:
   ```powershell
   python -m graphify update .
   ```
2. **Update `HANDOFF.md`**:
   - Document all changes, verified features, and device deployment state.
3. **Verify `INSTRUCTIONS.md` and `SKILLS_USAGE.md`**:
   - If new patterns or tools were introduced, ensure they are documented.
4. **Present Concise User Summary**:
   - Highlight what was solved, key decisions made, and the current device state.
   - Point the user to `HANDOFF.md` for full continuity details.

---

## 3. Quick Checklist for AI Agents

- [ ] Did I read `HANDOFF.md` at the start of the session?
- [ ] Did I strictly obey all invariants in `INSTRUCTIONS.md`?
- [ ] Did I avoid hallucinating non-existent classes, flags, or methods?
- [ ] Did I execute `testDebugUnitTest` and verify all tests pass?
- [ ] Did I install the updated APK to the connected device?
- [ ] Did I run `python -m graphify update .`?
- [ ] **Did I update `HANDOFF.md` before finishing?**
