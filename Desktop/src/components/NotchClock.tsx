import { useEffect, useRef, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Play, Pause, RotateCcw, Flag } from "lucide-react";
import type { Stopwatch } from "../hooks/useStopwatch";
import { formatStopwatch, parseDuration } from "../clockFormat";
import { playTactileSound } from "../utils/audioFeedback";

type Tab = "timer" | "stopwatch";

const TAB_KEY = "gloom-clock-tab";
const PRESETS = [5, 15, 25, 50];

export function StopwatchReadout({
  base,
  startedAt,
  precise,
}: {
  base: number;
  startedAt: number | null;
  precise: boolean;
}) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (startedAt === null) return;
    const id = setInterval(() => setNow(Date.now()), precise ? 50 : 250);
    return () => clearInterval(id);
  }, [startedAt, precise]);

  const ms = base + (startedAt === null ? 0 : Math.max(0, Math.max(now, startedAt) - startedAt));
  return <>{formatStopwatch(ms, precise)}</>;
}

function remember(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    return;
  }
}

function readTab(): Tab {
  try {
    return localStorage.getItem(TAB_KEY) === "stopwatch" ? "stopwatch" : "timer";
  } catch {
    return "timer";
  }
}

interface Props {
  timerEnabled: boolean;
  stopwatchEnabled?: boolean;
  solo?: boolean;
  timerSeconds: number;
  isTimerRunning: boolean;
  formatTimerTime: (seconds: number) => string;
  onStartTimer: (seconds: number) => void;
  onToggleTimer: () => void;
  onResetTimer: () => void;
  stopwatch: Stopwatch;
}

export function NotchClock({
  timerEnabled,
  stopwatchEnabled = true,
  solo = false,
  timerSeconds,
  isTimerRunning,
  formatTimerTime,
  onStartTimer,
  onToggleTimer,
  onResetTimer,
  stopwatch,
}: Props) {
  const [savedTab, setSavedTab] = useState<Tab>(readTab);
  const [custom, setCustom] = useState("");
  const [invalid, setInvalid] = useState(false);
  const customRef = useRef<HTMLInputElement>(null);

  const tab: Tab =
    savedTab === "stopwatch" && stopwatchEnabled ? "stopwatch" : timerEnabled ? "timer" : "stopwatch";

  const pickTab = (next: Tab) => {
    playTactileSound("switch");
    setSavedTab(next);
    remember(TAB_KEY, next);
  };

  const submitCustom = (e: React.FormEvent) => {
    e.preventDefault();
    const seconds = parseDuration(custom);
    if (seconds === null) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setCustom("");
    playTactileSound("snap");
    onStartTimer(seconds);
  };

  const recentLaps = stopwatch.laps.slice(-3).map((total, i, list) => {
    const index = stopwatch.laps.length - list.length + i;
    const previous = index === 0 ? 0 : stopwatch.laps[index - 1];
    return { number: index + 1, split: total - previous };
  });

  return (
    <div className={`notch-clock-panel ${solo ? "solo" : ""}`} onClick={(e) => e.stopPropagation()}>
      {timerEnabled && stopwatchEnabled && (
        <div className="clock-tab-bar" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === "timer"}
            className={`clock-tab ${tab === "timer" ? "active" : ""}`}
            onClick={() => pickTab("timer")}
          >
            Timer
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "stopwatch"}
            className={`clock-tab ${tab === "stopwatch" ? "active" : ""}`}
            onClick={() => pickTab("stopwatch")}
          >
            Stopwatch
          </button>
        </div>
      )}

      {tab === "timer" ? (
        <div className="timer-view">
          <div className="clock-digits-hero">{formatTimerTime(timerSeconds)}</div>
          <div className="timer-presets-row">
            {PRESETS.map((mins) => (
              <button
                key={mins}
                type="button"
                className={`preset-pill ${timerSeconds === mins * 60 && isTimerRunning ? "active" : ""}`}
                onClick={() => {
                  playTactileSound("snap");
                  onStartTimer(mins * 60);
                }}
              >
                {mins}m
              </button>
            ))}
          </div>

          <form onSubmit={submitCustom} className="timer-custom-form">
            <input
              ref={customRef}
              type="text"
              className={`timer-custom-input ${invalid ? "invalid" : ""}`}
              placeholder="Custom: 12, 1:30 or 25:00"
              value={custom}
              onChange={(e) => {
                setCustom(e.target.value);
                setInvalid(false);
              }}
              onFocus={() => invoke("request_notch_keyboard_focus", { focus: true }).catch(() => {})}
              onBlur={() => invoke("request_notch_keyboard_focus", { focus: false }).catch(() => {})}
            />
            <button type="submit" className="timer-go-btn" disabled={!custom.trim()}>
              Set
            </button>
          </form>

          <div className="clock-controls-row">
            <button
              type="button"
              className={`clock-main-btn ${isTimerRunning ? "running" : ""}`}
              onClick={() => {
                playTactileSound("snap");
                onToggleTimer();
              }}
              title={isTimerRunning ? "Pause" : "Start"}
            >
              {isTimerRunning ? <Pause size={14} fill="currentColor" /> : <Play size={14} fill="currentColor" />}
              <span>{isTimerRunning ? "Pause" : "Start"}</span>
            </button>
            <button
              type="button"
              className="clock-reset-btn"
              onClick={() => {
                playTactileSound("tick");
                onResetTimer();
              }}
              title="Reset"
              disabled={timerSeconds === 0 && !isTimerRunning}
            >
              <RotateCcw size={14} strokeWidth={2.4} />
            </button>
          </div>
        </div>
      ) : (
        <div className="stopwatch-view">
          <div className="clock-digits-hero">
            <StopwatchReadout base={stopwatch.base} startedAt={stopwatch.startedAt} precise />
          </div>

          <div className="stopwatch-laps-box">
            {recentLaps.length === 0 ? (
              <span className="laps-empty-text">Laps will appear here</span>
            ) : (
              recentLaps.map((lap) => (
                <div key={lap.number} className="lap-item">
                  <span className="lap-index">Lap {lap.number}</span>
                  <span className="lap-split">+{formatStopwatch(lap.split, true)}</span>
                </div>
              ))
            )}
          </div>

          <div className="clock-controls-row">
            <button
              type="button"
              className={`clock-main-btn ${stopwatch.running ? "running" : ""}`}
              onClick={() => {
                playTactileSound("snap");
                stopwatch.toggle();
              }}
              title={stopwatch.running ? "Pause" : "Start"}
            >
              {stopwatch.running ? <Pause size={14} fill="currentColor" /> : <Play size={14} fill="currentColor" />}
              <span>{stopwatch.running ? "Pause" : "Start"}</span>
            </button>
            {stopwatch.running ? (
              <button
                type="button"
                className="clock-lap-btn"
                onClick={() => {
                  playTactileSound("tick");
                  stopwatch.lap();
                }}
                title="Lap"
              >
                <Flag size={13} strokeWidth={2.4} />
                <span>Lap</span>
              </button>
            ) : (
              <button
                type="button"
                className="clock-reset-btn"
                onClick={() => {
                  playTactileSound("tick");
                  stopwatch.reset();
                }}
                title="Reset"
                disabled={stopwatch.base === 0 && stopwatch.startedAt === null}
              >
                <RotateCcw size={14} strokeWidth={2.4} />
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
