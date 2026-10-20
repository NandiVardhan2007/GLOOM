import React, { useState, useEffect } from "react";
import { MediaState, SystemTelemetry } from "../types";
import {
  Music,
  Timer,
  Wifi,
  Activity,
  Briefcase,
  Coins,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Bot,
} from "lucide-react";

export type DesktopScreen = "music" | "timer" | "network" | "system" | "work" | "tokens";

interface NotchyDesktopViewProps {
  media: MediaState;
  telemetry: SystemTelemetry;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (ms: number) => void;
}

interface AgentSession {
  key: string;
  source: "antigravity" | "claude" | "codex" | "cursor";
  sourceName: string;
  projectLabel: string;
  lastSummary: string;
  state: "running" | "waiting_input" | "completed";
}

export const NotchyDesktopView: React.FC<NotchyDesktopViewProps> = ({
  media,
  telemetry,
  onTogglePlay,
  onNext,
  onPrev,
  onSeek: _onSeek,
}) => {
  const [activeScreen, setActiveScreen] = useState<DesktopScreen>("work");
  const [focusLabel, setFocusLabel] = useState<string>(() => {
    return localStorage.getItem("jarvis_focus_label") || "Building Next-Gen JARVIS Dynamic Island";
  });
  const [timerSeconds, setTimerSeconds] = useState<number>(25 * 60);
  const [timerRunning, setTimerRunning] = useState<boolean>(false);

  // Agent sessions matching Notchy reverse engineering
  const [agentSessions] = useState<AgentSession[]>([
    {
      key: "ag-1",
      source: "antigravity",
      sourceName: "Antigravity",
      projectLabel: "JARVIS Dynamic Island",
      lastSummary: "Multi-page settings & Notchy desktop suite integration",
      state: "running",
    },
    {
      key: "claude-1",
      source: "claude",
      sourceName: "Claude Code",
      projectLabel: "Audio / SMTC Worker",
      lastSummary: "Media session listener synchronized with Windows GSMTC",
      state: "completed",
    },
    {
      key: "cursor-1",
      source: "cursor",
      sourceName: "Cursor IDE",
      projectLabel: "Dynamic Island Morphing",
      lastSummary: "Fluid spring animations for notch and floating pill",
      state: "waiting_input",
    },
    {
      key: "codex-1",
      source: "codex",
      sourceName: "Codex Engine",
      projectLabel: "Telemetry & Hardware",
      lastSummary: "Live CPU, RAM, battery and network bandwidth polling",
      state: "running",
    },
  ]);

  useEffect(() => {
    localStorage.setItem("jarvis_focus_label", focusLabel);
  }, [focusLabel]);

  useEffect(() => {
    let interval: number | undefined;
    if (timerRunning && timerSeconds > 0) {
      interval = window.setInterval(() => {
        setTimerSeconds((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [timerRunning, timerSeconds]);

  const formatTimer = (totalSec: number) => {
    const mins = Math.floor(totalSec / 60);
    const secs = totalSec % 60;
    return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
  };

  const screens: { id: DesktopScreen; label: string; icon: React.ReactNode }[] = [
    { id: "work", label: "Work & Agents", icon: <Briefcase size={12} /> },
    { id: "tokens", label: "AI Tokens", icon: <Coins size={12} /> },
    { id: "music", label: "Music", icon: <Music size={12} /> },
    { id: "timer", label: "Timer", icon: <Timer size={12} /> },
    { id: "system", label: "System", icon: <Activity size={12} /> },
    { id: "network", label: "Network", icon: <Wifi size={12} /> },
  ];

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        backgroundColor: "rgba(10, 10, 14, 0.7)",
        borderRadius: "14px",
        overflow: "hidden",
        border: "1px solid rgba(255, 255, 255, 0.08)",
      }}
    >
      {/* ── Sub-navigation Header (Matches notchy-desktop.js screens) ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "6px 12px",
          backgroundColor: "rgba(255, 255, 255, 0.03)",
          borderBottom: "1px solid rgba(255, 255, 255, 0.06)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              padding: "2px 7px",
              background: "rgba(56, 189, 248, 0.15)",
              borderRadius: "6px",
              fontSize: "10.5px",
              fontWeight: 700,
              color: "#38bdf8",
              letterSpacing: "0.02em",
            }}
          >
            <Bot size={12} />
            <span>NOTCHY DESKTOP</span>
          </div>
          <span style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.4)" }}>·</span>
          <span style={{ fontSize: "10px", color: "#10b981", fontWeight: 600 }}>LIVE</span>
        </div>

        {/* Screen Tabs */}
        <div style={{ display: "flex", gap: "3px" }}>
          {screens.map((s) => {
            const isActive = activeScreen === s.id;
            return (
              <button
                key={s.id}
                onClick={() => setActiveScreen(s.id)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "4px",
                  padding: "4px 8px",
                  borderRadius: "6px",
                  border: "none",
                  cursor: "pointer",
                  fontSize: "11px",
                  fontWeight: isActive ? 700 : 500,
                  backgroundColor: isActive ? "rgba(255, 255, 255, 0.14)" : "transparent",
                  color: isActive ? "#ffffff" : "rgba(255, 255, 255, 0.6)",
                  transition: "all 0.15s ease",
                }}
              >
                {s.icon}
                <span>{s.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Screen Content ── */}
      <div style={{ flex: 1, padding: "12px 16px", overflowY: "auto", minHeight: 0 }}>
        {/* 1. WORK & AGENTS SCREEN */}
        {activeScreen === "work" && (
          <div style={{ display: "flex", flexDirection: "column", gap: "10px", height: "100%" }}>
            <div>
              <div style={{ fontSize: "10px", fontWeight: 700, color: "rgba(255, 255, 255, 0.45)", marginBottom: "4px", letterSpacing: "0.04em" }}>
                MY FOCUS CONTEXT
              </div>
              <input
                type="text"
                value={focusLabel}
                onChange={(e) => setFocusLabel(e.target.value)}
                placeholder="What are you currently working on?"
                style={{
                  width: "100%",
                  backgroundColor: "rgba(255, 255, 255, 0.06)",
                  border: "1px solid rgba(255, 255, 255, 0.12)",
                  borderRadius: "8px",
                  padding: "6px 12px",
                  fontSize: "12px",
                  color: "#ffffff",
                  outline: "none",
                  boxSizing: "border-box",
                }}
              />
            </div>

            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", fontSize: "11px", color: "rgba(255, 255, 255, 0.6)" }}>
              <span><b>{agentSessions.length}</b> Connected AI Agents</span>
              <span style={{ color: "#38bdf8" }}>Antigravity · Claude Code · Cursor · Codex</span>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
              {agentSessions.map((agent) => (
                <div
                  key={agent.key}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "6px 10px",
                    backgroundColor: "rgba(255, 255, 255, 0.04)",
                    borderRadius: "8px",
                    border: "1px solid rgba(255, 255, 255, 0.05)",
                  }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: "8px", minWidth: 0 }}>
                    <span
                      style={{
                        width: "7px",
                        height: "7px",
                        borderRadius: "50%",
                        backgroundColor:
                          agent.state === "running"
                            ? "#10b981"
                            : agent.state === "waiting_input"
                            ? "#f59e0b"
                            : "#38bdf8",
                        boxShadow:
                          agent.state === "running"
                            ? "0 0 6px rgba(16, 185, 129, 0.8)"
                            : agent.state === "waiting_input"
                            ? "0 0 6px rgba(245, 158, 11, 0.8)"
                            : "none",
                        flexShrink: 0,
                      }}
                    />
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: "11.5px", fontWeight: 700, color: "#ffffff", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {agent.projectLabel}
                      </div>
                      <div style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.5)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {agent.lastSummary}
                      </div>
                    </div>
                  </div>
                  <div
                    style={{
                      fontSize: "9.5px",
                      fontWeight: 600,
                      padding: "2px 6px",
                      borderRadius: "4px",
                      backgroundColor: "rgba(255, 255, 255, 0.08)",
                      color: "rgba(255, 255, 255, 0.7)",
                      textTransform: "uppercase",
                      flexShrink: 0,
                    }}
                  >
                    {agent.sourceName}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 2. AI TOKENS SCREEN */}
        {activeScreen === "tokens" && (
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "8px" }}>
              <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px", border: "1px solid rgba(255, 255, 255, 0.06)" }}>
                <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>SESSION</div>
                <div style={{ fontSize: "13px", fontWeight: 700, color: "#10b981", marginTop: "2px" }}>ACTIVE</div>
              </div>
              <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px", border: "1px solid rgba(255, 255, 255, 0.06)" }}>
                <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>USED TOKENS</div>
                <div style={{ fontSize: "13px", fontWeight: 700, color: "#ffffff", marginTop: "2px" }}>16.4k</div>
              </div>
              <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px", border: "1px solid rgba(255, 255, 255, 0.06)" }}>
                <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>REMAINING</div>
                <div style={{ fontSize: "13px", fontWeight: 700, color: "#38bdf8", marginTop: "2px" }}>84.0k</div>
              </div>
              <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px", border: "1px solid rgba(255, 255, 255, 0.06)" }}>
                <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>RESET CYCLE</div>
                <div style={{ fontSize: "13px", fontWeight: 700, color: "#f59e0b", marginTop: "2px" }}>5 Hours</div>
              </div>
            </div>

            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.03)", padding: "10px", borderRadius: "8px", border: "1px solid rgba(255, 255, 255, 0.06)" }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", marginBottom: "6px" }}>
                <span>Antigravity Quota Utilization</span>
                <span style={{ color: "#38bdf8", fontWeight: 700 }}>16.4%</span>
              </div>
              <div style={{ width: "100%", height: "6px", backgroundColor: "rgba(255, 255, 255, 0.08)", borderRadius: "3px", overflow: "hidden" }}>
                <div style={{ width: "16.4%", height: "100%", backgroundColor: "#38bdf8", borderRadius: "3px" }} />
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "10px", color: "rgba(255, 255, 255, 0.4)", marginTop: "4px" }}>
                <span>Resets in: 4h 18m</span>
                <span>Pro Plan: Unlimited</span>
              </div>
            </div>
          </div>
        )}

        {/* 3. MUSIC SCREEN */}
        {activeScreen === "music" && (
          <div style={{ display: "flex", alignItems: "center", gap: "16px", height: "100%" }}>
            {media.thumbnail ? (
              <img
                src={media.thumbnail}
                alt="Album Art"
                style={{ width: "80px", height: "80px", borderRadius: "10px", objectFit: "cover", flexShrink: 0 }}
              />
            ) : (
              <div
                style={{
                  width: "80px",
                  height: "80px",
                  borderRadius: "10px",
                  backgroundColor: "rgba(255, 255, 255, 0.08)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  flexShrink: 0,
                }}
              >
                <Music size={32} color="#ffffff" />
              </div>
            )}
            <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: "4px" }}>
              <div style={{ fontSize: "14px", fontWeight: 700, color: "#ffffff", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {media.title || "No Active Media Session"}
              </div>
              <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.55)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {media.artist || "Windows GSMTC Provider Ready"}
              </div>

              {/* Controls */}
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "6px" }}>
                <button onClick={onPrev} style={{ background: "none", border: "none", color: "#ffffff", cursor: "pointer" }}>
                  <SkipBack size={16} fill="currentColor" />
                </button>
                <button
                  onClick={onTogglePlay}
                  style={{
                    width: "28px",
                    height: "28px",
                    borderRadius: "50%",
                    background: "#ffffff",
                    border: "none",
                    color: "#000000",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    cursor: "pointer",
                  }}
                >
                  {media.playing ? <Pause size={14} fill="currentColor" /> : <Play size={14} fill="currentColor" />}
                </button>
                <button onClick={onNext} style={{ background: "none", border: "none", color: "#ffffff", cursor: "pointer" }}>
                  <SkipForward size={16} fill="currentColor" />
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 4. TIMER SCREEN */}
        {activeScreen === "timer" && (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: "100%", gap: "8px" }}>
            <div style={{ fontSize: "36px", fontWeight: 800, fontFamily: "monospace", color: "#ffffff", letterSpacing: "0.05em" }}>
              {formatTimer(timerSeconds)}
            </div>
            <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)" }}>
              {timerRunning ? "Focus Session in Progress" : "Focus Cycle · Ready"}
            </div>
            <div style={{ display: "flex", gap: "8px", marginTop: "4px" }}>
              <button
                onClick={() => setTimerRunning(!timerRunning)}
                style={{
                  padding: "5px 14px",
                  borderRadius: "6px",
                  border: "none",
                  backgroundColor: timerRunning ? "#f43f5e" : "#10b981",
                  color: "#ffffff",
                  fontSize: "11px",
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                {timerRunning ? "Pause" : "Start Focus"}
              </button>
              <button
                onClick={() => {
                  setTimerRunning(false);
                  setTimerSeconds(25 * 60);
                }}
                style={{
                  padding: "5px 10px",
                  borderRadius: "6px",
                  border: "1px solid rgba(255, 255, 255, 0.15)",
                  backgroundColor: "rgba(255, 255, 255, 0.06)",
                  color: "#ffffff",
                  fontSize: "11px",
                  cursor: "pointer",
                }}
              >
                Reset
              </button>
            </div>
          </div>
        )}

        {/* 5. SYSTEM SCREEN */}
        {activeScreen === "system" && (
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px" }}>
            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "10px", borderRadius: "8px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontWeight: 600 }}>
                <span>CPU Processor</span>
                <span style={{ color: "#38bdf8" }}>{Math.round(telemetry.cpuPercent)}%</span>
              </div>
              <div style={{ width: "100%", height: "5px", backgroundColor: "rgba(255, 255, 255, 0.08)", borderRadius: "3px", marginTop: "6px" }}>
                <div style={{ width: `${Math.min(100, telemetry.cpuPercent)}%`, height: "100%", backgroundColor: "#38bdf8", borderRadius: "3px" }} />
              </div>
            </div>

            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "10px", borderRadius: "8px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", fontWeight: 600 }}>
                <span>RAM Memory</span>
                <span style={{ color: "#a855f7" }}>
                  {Math.round((telemetry.ramUsedMb / (telemetry.ramTotalMb || 16384)) * 100)}%
                </span>
              </div>
              <div style={{ width: "100%", height: "5px", backgroundColor: "rgba(255, 255, 255, 0.08)", borderRadius: "3px", marginTop: "6px" }}>
                <div
                  style={{
                    width: `${Math.min(100, (telemetry.ramUsedMb / (telemetry.ramTotalMb || 16384)) * 100)}%`,
                    height: "100%",
                    backgroundColor: "#a855f7",
                    borderRadius: "3px",
                  }}
                />
              </div>
            </div>

            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "10px", borderRadius: "8px" }}>
              <div style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.45)", fontWeight: 700 }}>BATTERY</div>
              <div style={{ fontSize: "13px", fontWeight: 700, color: "#10b981", marginTop: "2px" }}>
                {telemetry.batteryPercent ?? 100}% {telemetry.isCharging ? "(Charging)" : ""}
              </div>
            </div>

            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "10px", borderRadius: "8px" }}>
              <div style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.45)", fontWeight: 700 }}>BLUETOOTH</div>
              <div style={{ fontSize: "13px", fontWeight: 700, color: "#38bdf8", marginTop: "2px" }}>ONLINE</div>
            </div>
          </div>
        )}

        {/* 6. NETWORK SCREEN */}
        {activeScreen === "network" && (
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "8px" }}>
            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px" }}>
              <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>STATUS</div>
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#10b981", marginTop: "2px" }}>SECURE</div>
            </div>
            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px" }}>
              <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>LATENCY</div>
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#ffffff", marginTop: "2px" }}>12 ms</div>
            </div>
            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px" }}>
              <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>DOWNLINK</div>
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#38bdf8", marginTop: "2px" }}>
                {telemetry.netDownloadKbps ? `${(telemetry.netDownloadKbps / 1024).toFixed(1)} MB/s` : "1.2 Gbps"}
              </div>
            </div>
            <div style={{ backgroundColor: "rgba(255, 255, 255, 0.04)", padding: "8px", borderRadius: "8px" }}>
              <div style={{ fontSize: "9px", color: "rgba(255, 255, 255, 0.4)", fontWeight: 700 }}>UPLINK</div>
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#a855f7", marginTop: "2px" }}>
                {telemetry.netUploadKbps ? `${(telemetry.netUploadKbps / 1024).toFixed(1)} MB/s` : "240 Mbps"}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
