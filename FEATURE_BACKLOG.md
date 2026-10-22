# JARVIS Feature Backlog

Derived from a review of three reference projects on **2026-10-22**:

- [kuafuai/andee](https://github.com/kuafuai/andee) — Apache-2.0 Android agent that uses the phone like a person
- [google/artemis](https://github.com/google/artemis) — Apache-2.0 mobile-automation agent, 99%+ AndroidWorld
- [cactus-compute/needle](https://github.com/cactus-compute/needle) — Apache-2.0 8–29 MB on-device tool-calling model

Priority: **P0** blocks the current release · **P1** next · **P2** later.
Effort: **S** ≤ 1 day · **M** 2–4 days · **L** 1–2 weeks.

---

## 1. Automation and the agent loop

| # | Feature | Source | Priority | Effort | Notes for JARVIS |
|---|---|---|---|---|---|
| 1.1 | Numbered element outline, tap by element id | andee | **Done** | — | `ScreenAutomation.describe()` / `tap(eid)` |
| 1.2 | Type through the accessibility IME | andee | **Done** | — | `A11yIme.replace()`; user's keyboard stays active |
| 1.3 | Local command router (type/scroll/click/open/search/send) | ours | **Done** | — | `CommandRouter` |
| 1.4 | Observe → plan → act AI loop | artemis | **Done (v1)** | — | `AiPlanner`, 2 rounds × 5 actions |
| 1.5 | WhatsApp "send message to X saying Y" flow | ours | **Needs device test** | S | Works only where WhatsApp's labels match |
| 1.6 | Post-action verification (did the screen actually change?) | artemis | P1 | M | Compare outline/package before and after a tap; retry once |
| 1.7 | Pre-execution safety net (XML first, pixels second) | artemis | P1 | M | Refuse taps on destructive/financial targets |
| 1.8 | Flash vs Pro execution profiles | artemis | P2 | M | Flash = 1 model call per step; Pro = plan + checker |
| 1.9 | Checkpoint plan with final report | artemis | P2 | L | Planner/Operator/Checker agents |
| 1.10 | Action bursts / click sequences | artemis | P2 | S | Chain taps to beat transient controls |
| 1.11 | Screenshot aim grid + zoom/crosshair for self-drawn apps | andee | P1 | M | WebViews/Flutter/canvas where the tree is empty |
| 1.12 | Per-location repetition guard | andee | **Done (planner)** | — | Router does not need it; planner stops after 2 same taps |
| 1.13 | History compression + `search_history` | artemis | P2 | L | Needed only for long tasks |
| 1.14 | Screen recording, replay and video analyzer | artemis | P2 | L | Excellent for debugging failed automation |
| 1.15 | Scenes (saved goals + rules + routines) | andee | P1 | L | "Keep an eye on my messages", "book it when it opens" |
| 1.16 | Notification triage: act / tell / ignore | andee | P1 | M | Cheap model call per alert; biased toward silence |
| 1.17 | Notebook memory + real scheduler | andee | P1 | L | "Remind me" must schedule before saying it will |
| 1.18 | Vault: fill passwords without exposing them | andee | P2 | M | Keystore AES-GCM; `localOnly` tools |
| 1.19 | SMS, calls, contacts, calendar, camera tools | andee | P1 | M | Public Android APIs only |
| 1.20 | On-device brain vs remote brain | andee | P1 | M | `brain = local` is Andee's default; fits JARVIS |
| 1.21 | Offline tool calling with Needle | needle | P1 | M/L | Android engine exists; see §4 |
| 1.22 | Offline speech-to-text with Whistle | needle | P2 | M | 16.9 MB, 7 languages; replaces cloud ASR |
| 1.23 | Custom tool schemas + grammar-constrained JSON | needle | P2 | M | No malformed actions |
| 1.24 | Confidence-based act / confirm / refuse | needle | P2 | S | Calibrated head in the model |
| 1.25 | ADBKeyboard fallback for API < 33 | andee | P2 | S | Requires a PC; A11y IME covers Android 13+ |
| 1.26 | MIUI/WeChat tree helper | andee | P2 | S | Only if the user's ROM hides the tree |
| 1.27 | On-device self-check (permissions, services, keys) | andee/artemis | P1 | S | Show why automation will not work before a command |

## 2. Notifications

| # | Feature | Source | Priority | Effort | Notes |
|---|---|---|---|---|---|
| 2.1 | Silent-channel filtering | ours | **Done** | — | Toggle in Behavior |
| 2.2 | Dedupe (content 3 s, key 600 ms/20 s) | ours | **Done** | — | Survives reposts under new keys |
| 2.3 | Skip group/autogroup summaries | ours | **Done** | — | Stops the second alert after cancellation |
| 2.4 | Single island renderer | ours | **Done** | — | Accessibility service wins |
| 2.5 | Per-app allow list for island alerts | ours | P1 | S | `EnabledAppsSettingsScreen` exists; enforce it |
| 2.6 | Inline reply / quick actions in the island | ours | P2 | M | RemoteInput |
| 2.7 | OTP extraction and one-tap copy | ours | P2 | S | Pattern already parsed, currently unused |
| 2.8 | Desktop notifications → island activity | handoff §4B | P1 | S | Desktop side only |

## 3. Instagram reel counter

| # | Feature | Priority | Effort | Notes |
|---|---|---|---|---|
| 3.1 | Daily + lifetime counter, Home card, reset | **Done** | — | `ReelCounter`, `InstagramReelTracker` |
| 3.2 | Only count reels with a minimum dwell time | P1 | S | Avoids counting flicked-through reels |
| 3.3 | Per-app screen-time summary (Instagram, YouTube, etc.) | P2 | M | Reuse the accessibility event stream |
| 3.4 | Weekly chart on the Home card | P2 | S | Store 7 days, draw with Compose Canvas |
| 3.5 | Count Reels watched inside the Facebook app | P2 | S | Same heuristic, different package |

## 4. Needle integration plan (offline tool calls)

Needle ships real Android engines and an Apache-2.0 license, so this is
feasible without a server.

1. `pip install cactus-needle`; run `needle build --platform android-arm64 --out needle-android`
   (also `android-armv7` if 32-bit devices matter).
2. Copy `libneedle.so` into `mobile/android/app/src/main/jniLibs/arm64-v8a/` and
   `needle3.cact` into `src/main/assets/needle/`.
3. Add a small JNI wrapper for `needle_load`, `needle_complete` and `needle_embed`
   (see `llms.txt` → "Porting Needle 3").
4. Define JARVIS tools as JSON schemas: `tap`, `type`, `scroll`, `open_app`,
   `send_message`, `set_alarm`, `search`. Feed the current element outline as
   context.
5. Use Needle for the **first** pass (offline, ~tens of ms); fall back to the
   desktop model only when `confidence` is below the product threshold.
6. Optional: fine-tune on our own transcripts with
   `needle finetune` → `needle build --lora`.

Expected benefit: voice commands work with no desktop, no network and no key,
and the desktop model is used only for open-ended conversation.

## 5. Features worth copying wholesale (later)

| Feature | Source | Why it matters |
|---|---|---|
| Character with moods and expressions | andee | The island is already the "face"; moods would make JARVIS feel alive |
| Artifacts / generated pages ("Made for you") | andee | Save plans, comparisons and meeting notes |
| Meeting recorder + minutes | andee | High-value, uses existing ASR/TTS |
| Robot-dog / USB body | andee | Out of scope for JARVIS, but the tool contract is a good model |
| Web visual test console | artemis | Watch the phone screen and dispatch tasks from the desktop |
| MCP server | artemis | Let Claude Code/Windsurf drive the phone |
| Python SDK + pytest assertions | artemis | Regression tests for automation |
| AndroidWorld benchmark harness | artemis | Objective measure of our automation quality |
| Fine-tuning platform | needle | Product-specific tool accuracy |
| Text embeddings | needle | Local semantic search over notes/history |

## 6. Suggested order for the next three sessions

1. **Release v1.1.1** and verify on the phone: notification card, silent
   filter, tap/type/scroll, reel counter, WhatsApp send.
2. **Automation quality:** 1.6 post-action verification, 1.7 safety net,
   1.11 screenshot aim grid, 1.27 self-check. These turn best-effort actions
   into reliable ones.
3. **Agent depth:** 1.15 scenes, 1.16 notification triage, 1.17 notebook +
   scheduler, then 1.21 Needle for offline tool calls.
