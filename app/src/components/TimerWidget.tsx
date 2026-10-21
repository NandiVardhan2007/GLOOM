import React, { useState, useEffect } from "react";
import { Play, Pause, RotateCcw, Plus, Minus, Flag } from "lucide-react";
import { TimerState } from "../types";
import {
  PersistentTimerData,
  subscribeTimer,
  startPomodoro,
  pausePomodoro,
  resetPomodoro,
  setPomodoroPreset,
  adjustPomodoroMinutes,
  startStopwatch,
  pauseStopwatch,
  resetStopwatch,
  addStopwatchLap,
  setTimerSubMode,
  toTimerState,
} from "../utils/timerStore";

interface TimerWidgetProps {
  onTimerUpdate?: (state: TimerState) => void;
}

export const TimerWidget: React.FC<TimerWidgetProps> = ({ onTimerUpdate }) => {
  const [timerData, setTimerData] = useState<PersistentTimerData>({
    subMode: "pomodoro",
    totalSeconds: 25 * 60,
    remainingSeconds: 25 * 60,
    isTimerRunning: false,
    targetEndTime: null,
    stopwatchMs: 0,
    isStopwatchRunning: false,
    stopwatchStartTimestamp: null,
    laps: [],
  });

  useEffect(() => {
    return subscribeTimer((data) => {
      setTimerData(data);
      if (onTimerUpdate) {
        onTimerUpdate(toTimerState(data));
      }
    });
  }, [onTimerUpdate]);

  const {
    subMode,
    totalSeconds,
    remainingSeconds,
    isTimerRunning,
    stopwatchMs,
    isStopwatchRunning,
    laps,
  } = timerData;

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
          background: "#2c2c2e",
          borderRadius: "14px",
          padding: "4px",
          width: "fit-content",
          margin: "0 auto",
          border: "0.5px solid rgba(255, 255, 255, 0.08)",
        }}
      >
        <button
          onClick={() => setTimerSubMode("pomodoro")}
          style={{
            padding: "5px 14px",
            borderRadius: "11px",
            border: "none",
            fontSize: "11px",
            fontWeight: 600,
            cursor: "pointer",
            background: subMode === "pomodoro" ? "#0a84ff" : "transparent",
            color: subMode === "pomodoro" ? "#ffffff" : "#94a3b8",
            transition: "all 0.15s ease",
          }}
        >
          Pomodoro
        </button>
        <button
          onClick={() => setTimerSubMode("stopwatch")}
          style={{
            padding: "5px 14px",
            borderRadius: "11px",
            border: "none",
            fontSize: "11px",
            fontWeight: 600,
            cursor: "pointer",
            background: subMode === "stopwatch" ? "#0a84ff" : "transparent",
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
              background: "#2c2c2e",
              borderRadius: "16px",
              border: "1px solid rgba(255, 255, 255, 0.1)",
            }}
          >
            <div
              style={{
                fontSize: "36px",
                fontWeight: 600,
                letterSpacing: "0.04em",
                fontVariantNumeric: "tabular-nums",
                color: remainingSeconds === 0 ? "#f87171" : "#ffffff",
                textShadow: isTimerRunning
                  ? "0 0 20px rgba(10, 132, 255, 0.4)"
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
                background: "#3a3a3c",
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
                      : "#0a84ff",
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
                  onClick={() => setPomodoroPreset(p.mins)}
                  style={{
                    padding: "5px 10px",
                    borderRadius: "8px",
                    border: "1px solid rgba(255, 255, 255, 0.12)",
                    background:
                      totalSeconds === p.mins * 60 ? "#0a84ff" : "#2c2c2e",
                    color: "#ffffff",
                    fontSize: "11px",
                    fontWeight: 600,
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
                onClick={() => adjustPomodoroMinutes(-1)}
                style={{
                  width: "30px",
                  height: "30px",
                  borderRadius: "50%",
                  border: "none",
                  background: "#3a3a3c",
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
                onClick={() => (isTimerRunning ? pausePomodoro() : startPomodoro())}
                style={{
                  width: "38px",
                  height: "38px",
                  borderRadius: "50%",
                  border: "none",
                  background: isTimerRunning ? "#dc2626" : "#0a84ff",
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
                onClick={() => adjustPomodoroMinutes(1)}
                style={{
                  width: "30px",
                  height: "30px",
                  borderRadius: "50%",
                  border: "none",
                  background: "#3a3a3c",
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
                onClick={resetPomodoro}
                style={{
                  width: "30px",
                  height: "30px",
                  borderRadius: "50%",
                  border: "none",
                  background: "#2c2c2e",
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
              background: "#2c2c2e",
              borderRadius: "16px",
              border: "1px solid rgba(255, 255, 255, 0.1)",
            }}
          >
            <div
              style={{
                fontSize: "34px",
                fontWeight: 600,
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
              onClick={() => (isStopwatchRunning ? pauseStopwatch() : startStopwatch())}
              style={{
                padding: "8px 20px",
                borderRadius: "20px",
                border: "none",
                background: isStopwatchRunning ? "#dc2626" : "#0a84ff",
                color: "#ffffff",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "6px",
                fontSize: "12px",
                fontWeight: 600,
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
              onClick={addStopwatchLap}
              disabled={!isStopwatchRunning}
              style={{
                padding: "8px 16px",
                borderRadius: "20px",
                border: "1px solid rgba(255, 255, 255, 0.15)",
                background: isStopwatchRunning ? "#2c2c2e" : "#14141a",
                color: isStopwatchRunning ? "#ffffff" : "#64748b",
                cursor: isStopwatchRunning ? "pointer" : "default",
                display: "flex",
                alignItems: "center",
                gap: "5px",
                fontSize: "12px",
                fontWeight: 600,
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
                background: "#2c2c2e",
                color: "#cbd5e1",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "5px",
                fontSize: "12px",
                fontWeight: 600,
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
                border: "0.5px solid rgba(255, 255, 255, 0.08)",
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
