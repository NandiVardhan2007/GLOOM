import React, { useState, useEffect, useRef } from "react";
import { Play, Pause, RotateCcw, Plus, Minus, Flag } from "lucide-react";
import { TimerState } from "../types";

interface TimerWidgetProps {
  onTimerUpdate?: (state: TimerState) => void;
}

export const playTimerChime = () => {
  try {
    const AudioContextClass =
      window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
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

export const TimerWidget: React.FC<TimerWidgetProps> = ({ onTimerUpdate }) => {
  const [subMode, setSubMode] = useState<"pomodoro" | "stopwatch">("pomodoro");

  // Pomodoro / Countdown state
  const [totalSeconds, setTotalSeconds] = useState<number>(25 * 60);
  const [remainingSeconds, setRemainingSeconds] = useState<number>(25 * 60);
  const [isTimerRunning, setIsTimerRunning] = useState<boolean>(false);

  // Stopwatch state
  const [stopwatchMs, setStopwatchMs] = useState<number>(0);
  const [isStopwatchRunning, setIsStopwatchRunning] = useState<boolean>(false);
  const [laps, setLaps] = useState<number[]>([]);

  const timerIntervalRef = useRef<number | null>(null);
  const stopwatchIntervalRef = useRef<number | null>(null);
  const stopwatchStartRef = useRef<number>(0);

  // Countdown timer effect
  useEffect(() => {
    if (isTimerRunning) {
      timerIntervalRef.current = window.setInterval(() => {
        setRemainingSeconds((prev) => {
          if (prev <= 1) {
            setIsTimerRunning(false);
            playTimerChime();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else if (timerIntervalRef.current) {
      window.clearInterval(timerIntervalRef.current);
    }

    return () => {
      if (timerIntervalRef.current) window.clearInterval(timerIntervalRef.current);
    };
  }, [isTimerRunning]);

  // Stopwatch effect
  useEffect(() => {
    if (isStopwatchRunning) {
      stopwatchStartRef.current = performance.now() - stopwatchMs;
      stopwatchIntervalRef.current = window.setInterval(() => {
        setStopwatchMs(performance.now() - stopwatchStartRef.current);
      }, 33);
    } else if (stopwatchIntervalRef.current) {
      window.clearInterval(stopwatchIntervalRef.current);
    }

    return () => {
      if (stopwatchIntervalRef.current) window.clearInterval(stopwatchIntervalRef.current);
    };
  }, [isStopwatchRunning]);

  // Sync state to parent
  useEffect(() => {
    if (onTimerUpdate) {
      const minutes = Math.floor(remainingSeconds / 60);
      const seconds = remainingSeconds % 60;
      const formatted = `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;

      onTimerUpdate({
        mode: subMode,
        isRunning: subMode === "pomodoro" ? isTimerRunning : isStopwatchRunning,
        remainingSeconds,
        totalSeconds,
        elapsedMs: stopwatchMs,
        formattedTime: formatted,
      });
    }
  }, [
    subMode,
    isTimerRunning,
    isStopwatchRunning,
    remainingSeconds,
    totalSeconds,
    stopwatchMs,
    onTimerUpdate,
  ]);

  const setPreset = (mins: number) => {
    setIsTimerRunning(false);
    const secs = mins * 60;
    setTotalSeconds(secs);
    setRemainingSeconds(secs);
  };

  const adjustMinutes = (delta: number) => {
    setIsTimerRunning(false);
    setRemainingSeconds((prev) => {
      const next = Math.max(60, prev + delta * 60);
      setTotalSeconds(next);
      return next;
    });
  };

  const resetTimer = () => {
    setIsTimerRunning(false);
    setRemainingSeconds(totalSeconds);
  };

  const toggleStopwatch = () => {
    setIsStopwatchRunning(!isStopwatchRunning);
  };

  const resetStopwatch = () => {
    setIsStopwatchRunning(false);
    setStopwatchMs(0);
    setLaps([]);
  };

  const addLap = () => {
    if (isStopwatchRunning) {
      setLaps([stopwatchMs, ...laps]);
    }
  };

  // Formatting helpers
  const formatTimerDisplay = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };

  const formatStopwatchDisplay = (ms: number) => {
    const totalSecs = Math.floor(ms / 1000);
    const m = Math.floor(totalSecs / 60);
    const s = totalSecs % 60;
    const centis = Math.floor((ms % 1000) / 10);
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}.${centis.toString().padStart(2, "0")}`;
  };

  const progress = totalSeconds > 0 ? (remainingSeconds / totalSeconds) * 100 : 0;

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "10px",
        height: "100%",
        paddingRight: "2px",
      }}
    >
      {/* Submode Switcher */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: "8px",
          background: "#181820",
          borderRadius: "14px",
          padding: "4px",
          width: "fit-content",
          margin: "0 auto",
          border: "1px solid rgba(255, 255, 255, 0.08)",
        }}
      >
        <button
          onClick={() => setSubMode("pomodoro")}
          style={{
            padding: "5px 14px",
            borderRadius: "11px",
            border: "none",
            fontSize: "11px",
            fontWeight: 700,
            cursor: "pointer",
            background: subMode === "pomodoro" ? "#0284c7" : "transparent",
            color: subMode === "pomodoro" ? "#ffffff" : "#94a3b8",
            transition: "all 0.15s ease",
          }}
        >
          Pomodoro
        </button>
        <button
          onClick={() => setSubMode("stopwatch")}
          style={{
            padding: "5px 14px",
            borderRadius: "11px",
            border: "none",
            fontSize: "11px",
            fontWeight: 700,
            cursor: "pointer",
            background: subMode === "stopwatch" ? "#0284c7" : "transparent",
            color: subMode === "stopwatch" ? "#ffffff" : "#94a3b8",
            transition: "all 0.15s ease",
          }}
        >
          Stopwatch
        </button>
      </div>

      {subMode === "pomodoro" ? (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: "10px",
            flex: 1,
          }}
        >
          {/* Digital Readout */}
          <div
            style={{
              position: "relative",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              width: "100%",
              padding: "10px 0",
              background: "#181820",
              borderRadius: "16px",
              border: "1px solid rgba(255, 255, 255, 0.1)",
            }}
          >
            <div
              style={{
                fontSize: "36px",
                fontWeight: 800,
                letterSpacing: "0.04em",
                fontVariantNumeric: "tabular-nums",
                color: remainingSeconds === 0 ? "#f87171" : "#ffffff",
                textShadow: isTimerRunning
                  ? "0 0 20px rgba(56, 189, 248, 0.4)"
                  : "none",
              }}
            >
              {formatTimerDisplay(remainingSeconds)}
            </div>

            {/* Progress bar */}
            <div
              style={{
                width: "80%",
                height: "5px",
                background: "#262632",
                borderRadius: "3px",
                marginTop: "6px",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${progress}%`,
                  height: "100%",
                  background:
                    remainingSeconds === 0
                      ? "#f87171"
                      : "linear-gradient(90deg, #38bdf8, #818cf8)",
                  transition: "width 0.3s ease",
                }}
              />
            </div>
          </div>

          {/* Controls: Preset Pills & Play/Pause */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              width: "100%",
              gap: "8px",
            }}
          >
            {/* Quick Presets */}
            <div style={{ display: "flex", gap: "5px" }}>
              {[
                { label: "5m", mins: 5 },
                { label: "15m", mins: 15 },
                { label: "25m", mins: 25 },
                { label: "45m", mins: 45 },
              ].map((p) => (
                <button
                  key={p.label}
                  onClick={() => setPreset(p.mins)}
                  style={{
                    padding: "5px 10px",
                    borderRadius: "8px",
                    border: "1px solid rgba(255, 255, 255, 0.12)",
                    background:
                      totalSeconds === p.mins * 60 ? "#0284c7" : "#1e1e28",
                    color: "#ffffff",
                    fontSize: "11px",
                    fontWeight: 700,
                    cursor: "pointer",
                  }}
                >
                  {p.label}
                </button>
              ))}
            </div>

            {/* Action buttons */}
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <button
                onClick={() => adjustMinutes(-1)}
                style={{
                  width: "30px",
                  height: "30px",
                  borderRadius: "50%",
                  border: "none",
                  background: "#262632",
                  color: "#ffffff",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
                title="-1 min"
              >
                <Minus size={14} />
              </button>

              <button
                onClick={() => setIsTimerRunning(!isTimerRunning)}
                style={{
                  width: "38px",
                  height: "38px",
                  borderRadius: "50%",
                  border: "none",
                  background: isTimerRunning ? "#dc2626" : "#0284c7",
                  color: "#ffffff",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  boxShadow: "0 0 14px rgba(2, 132, 199, 0.45)",
                }}
                title={isTimerRunning ? "Pause" : "Start"}
              >
                {isTimerRunning ? <Pause size={18} /> : <Play size={18} />}
              </button>

              <button
                onClick={() => adjustMinutes(1)}
                style={{
                  width: "30px",
                  height: "30px",
                  borderRadius: "50%",
                  border: "none",
                  background: "#262632",
                  color: "#ffffff",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
                title="+1 min"
              >
                <Plus size={14} />
              </button>

              <button
                onClick={resetTimer}
                style={{
                  width: "30px",
                  height: "30px",
                  borderRadius: "50%",
                  border: "none",
                  background: "#1e1e28",
                  color: "#94a3b8",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
                title="Reset"
              >
                <RotateCcw size={14} />
              </button>
            </div>
          </div>
        </div>
      ) : (
        /* Stopwatch Mode */
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: "10px",
            flex: 1,
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: "100%",
              padding: "12px 0",
              background: "#181820",
              borderRadius: "16px",
              border: "1px solid rgba(255, 255, 255, 0.1)",
            }}
          >
            <div
              style={{
                fontSize: "34px",
                fontWeight: 800,
                letterSpacing: "0.04em",
                fontVariantNumeric: "tabular-nums",
                color: "#ffffff",
              }}
            >
              {formatStopwatchDisplay(stopwatchMs)}
            </div>
          </div>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "12px",
            }}
          >
            <button
              onClick={toggleStopwatch}
              style={{
                padding: "8px 20px",
                borderRadius: "20px",
                border: "none",
                background: isStopwatchRunning ? "#dc2626" : "#0284c7",
                color: "#ffffff",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "6px",
                fontSize: "12px",
                fontWeight: 700,
              }}
            >
              {isStopwatchRunning ? (
                <>
                  <Pause size={14} /> Pause
                </>
              ) : (
                <>
                  <Play size={14} /> Start
                </>
              )}
            </button>

            <button
              onClick={addLap}
              disabled={!isStopwatchRunning}
              style={{
                padding: "8px 16px",
                borderRadius: "20px",
                border: "1px solid rgba(255, 255, 255, 0.15)",
                background: isStopwatchRunning ? "#1e1e28" : "#14141a",
                color: isStopwatchRunning ? "#ffffff" : "#64748b",
                cursor: isStopwatchRunning ? "pointer" : "default",
                display: "flex",
                alignItems: "center",
                gap: "5px",
                fontSize: "12px",
                fontWeight: 700,
              }}
            >
              <Flag size={13} /> Lap
            </button>

            <button
              onClick={resetStopwatch}
              style={{
                padding: "8px 14px",
                borderRadius: "20px",
                border: "none",
                background: "#1e1e28",
                color: "#cbd5e1",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "5px",
                fontSize: "12px",
                fontWeight: 700,
              }}
            >
              <RotateCcw size={13} /> Reset
            </button>
          </div>

          {/* Laps List */}
          {laps.length > 0 && (
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                gap: "4px",
                width: "100%",
                maxHeight: "56px",
                overflowY: "auto",
                background: "#121218",
                padding: "6px 10px",
                borderRadius: "8px",
                border: "1px solid rgba(255, 255, 255, 0.08)",
              }}
            >
              {laps.map((lap, i) => (
                <div
                  key={i}
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    fontSize: "11px",
                    fontWeight: 600,
                    color: "#cbd5e1",
                  }}
                >
                  <span>Lap {laps.length - i}</span>
                  <span style={{ fontVariantNumeric: "tabular-nums" }}>
                    {formatStopwatchDisplay(lap)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
