import { TimerState } from "../types";

export interface PersistentTimerData {
  subMode: "pomodoro" | "stopwatch";
  totalSeconds: number;
  remainingSeconds: number;
  isTimerRunning: boolean;
  targetEndTime: number | null;
  stopwatchMs: number;
  isStopwatchRunning: boolean;
  stopwatchStartTimestamp: number | null;
  laps: number[];
}

let globalTimerState: PersistentTimerData = {
  subMode: "pomodoro",
  totalSeconds: 25 * 60,
  remainingSeconds: 25 * 60,
  isTimerRunning: false,
  targetEndTime: null,
  stopwatchMs: 0,
  isStopwatchRunning: false,
  stopwatchStartTimestamp: null,
  laps: [],
};

const listeners = new Set<(state: PersistentTimerData) => void>();

function notify() {
  listeners.forEach((l) => l({ ...globalTimerState }));
}

export const playTimerChime = () => {
  try {
    const AudioContextClass =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();

    osc1.type = "sine";
    osc1.frequency.setValueAtTime(880, ctx.currentTime);
    osc1.frequency.exponentialRampToValueAtTime(1760, ctx.currentTime + 0.5);

    osc2.type = "sine";
    osc2.frequency.setValueAtTime(1320, ctx.currentTime);
    osc2.frequency.exponentialRampToValueAtTime(2640, ctx.currentTime + 0.5);

    gain.gain.setValueAtTime(0.3, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 1.0);

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(ctx.destination);

    osc1.start();
    osc2.start();
    osc1.stop(ctx.currentTime + 1.0);
    osc2.stop(ctx.currentTime + 1.0);
  } catch (err) {
    console.warn("AudioContext chime unavailable:", err);
  }
};

let globalTickerInitialized = false;

export function ensureGlobalTimerTicker() {
  if (globalTickerInitialized) return;
  globalTickerInitialized = true;

  window.setInterval(() => {
    let changed = false;

    if (globalTimerState.isTimerRunning && globalTimerState.targetEndTime) {
      const now = Date.now();
      const diffSecs = Math.max(0, Math.ceil((globalTimerState.targetEndTime - now) / 1000));
      if (diffSecs !== globalTimerState.remainingSeconds) {
        globalTimerState.remainingSeconds = diffSecs;
        changed = true;
      }
      if (diffSecs <= 0) {
        globalTimerState.isTimerRunning = false;
        globalTimerState.targetEndTime = null;
        changed = true;
        playTimerChime();
      }
    }

    if (globalTimerState.isStopwatchRunning && globalTimerState.stopwatchStartTimestamp) {
      const now = performance.now();
      globalTimerState.stopwatchMs = now - globalTimerState.stopwatchStartTimestamp;
      changed = true;
    }

    if (changed) {
      notify();
    }
  }, 100);
}

export function getGlobalTimerState(): PersistentTimerData {
  return { ...globalTimerState };
}

export function subscribeTimer(callback: (state: PersistentTimerData) => void): () => void {
  ensureGlobalTimerTicker();
  listeners.add(callback);
  callback({ ...globalTimerState });
  return () => {
    listeners.delete(callback);
  };
}

export function setTimerSubMode(mode: "pomodoro" | "stopwatch") {
  globalTimerState.subMode = mode;
  notify();
}

export function startPomodoro() {
  if (globalTimerState.remainingSeconds <= 0) {
    globalTimerState.remainingSeconds = globalTimerState.totalSeconds;
  }
  globalTimerState.isTimerRunning = true;
  globalTimerState.targetEndTime = Date.now() + globalTimerState.remainingSeconds * 1000;
  ensureGlobalTimerTicker();
  notify();
}

export function pausePomodoro() {
  if (globalTimerState.isTimerRunning && globalTimerState.targetEndTime) {
    const diff = Math.max(0, Math.ceil((globalTimerState.targetEndTime - Date.now()) / 1000));
    globalTimerState.remainingSeconds = diff;
  }
  globalTimerState.isTimerRunning = false;
  globalTimerState.targetEndTime = null;
  notify();
}

export function resetPomodoro() {
  globalTimerState.isTimerRunning = false;
  globalTimerState.targetEndTime = null;
  globalTimerState.remainingSeconds = globalTimerState.totalSeconds;
  notify();
}

export function setPomodoroPreset(mins: number) {
  const secs = mins * 60;
  globalTimerState.isTimerRunning = false;
  globalTimerState.targetEndTime = null;
  globalTimerState.totalSeconds = secs;
  globalTimerState.remainingSeconds = secs;
  notify();
}

export function adjustPomodoroMinutes(delta: number) {
  pausePomodoro();
  const next = Math.max(60, globalTimerState.remainingSeconds + delta * 60);
  globalTimerState.totalSeconds = next;
  globalTimerState.remainingSeconds = next;
  notify();
}

export function startStopwatch() {
  globalTimerState.isStopwatchRunning = true;
  globalTimerState.stopwatchStartTimestamp = performance.now() - globalTimerState.stopwatchMs;
  ensureGlobalTimerTicker();
  notify();
}

export function pauseStopwatch() {
  globalTimerState.isStopwatchRunning = false;
  globalTimerState.stopwatchStartTimestamp = null;
  notify();
}

export function resetStopwatch() {
  globalTimerState.isStopwatchRunning = false;
  globalTimerState.stopwatchStartTimestamp = null;
  globalTimerState.stopwatchMs = 0;
  globalTimerState.laps = [];
  notify();
}

export function addStopwatchLap() {
  if (globalTimerState.isStopwatchRunning && globalTimerState.stopwatchMs > 0) {
    globalTimerState.laps = [globalTimerState.stopwatchMs, ...globalTimerState.laps];
    notify();
  }
}

export function toTimerState(data: PersistentTimerData): TimerState {
  const displaySeconds =
    data.subMode === "stopwatch"
      ? Math.floor(data.stopwatchMs / 1000)
      : data.remainingSeconds;
  const minutes = Math.floor(displaySeconds / 60);
  const seconds = displaySeconds % 60;
  const formatted = `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;

  return {
    mode: data.subMode,
    isRunning: data.subMode === "pomodoro" ? data.isTimerRunning : data.isStopwatchRunning,
    remainingSeconds: data.remainingSeconds,
    totalSeconds: data.totalSeconds,
    elapsedMs: data.stopwatchMs,
    formattedTime: formatted,
  };
}
