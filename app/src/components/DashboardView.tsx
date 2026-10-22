import React from "react";
import { invoke } from "@tauri-apps/api/core";
import { MediaState, SystemTelemetry, TimerState } from "../types";
import { ActivityIcon, ArrowDownIcon, ArrowUpIcon, BackwardIcon, CalculatorIcon, ChevronRightIcon, CoffeeIcon, ForwardIcon, MusicNoteIcon, PauseIcon, PlayIcon, ScissorsIcon } from "./IslandIcons";

interface DashboardViewProps {
  media: MediaState;
  telemetry: SystemTelemetry;
  timer?: TimerState | null;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (ms: number) => void;
  onSelectTab: (tab: "timer" | "system" | "media") => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  media,
  telemetry,
  timer,
  onTogglePlay,
  onNext,
  onPrev,
  onSeek,
  onSelectTab,
}) => {
  const hasTrack = !!(media.title || media.artist);

  const formatTime = (ms: number) => {
    if (ms <= 0) return "0:00";
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s < 10 ? "0" : ""}${s}`;
  };

  const progress =
    media.durationMs > 0 ? (media.positionMs / media.durationMs) * 100 : 0;

  const handleSeek = (e: React.MouseEvent<HTMLDivElement>) => {
    if (media.durationMs <= 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    onSeek(Math.floor(ratio * media.durationMs));
  };

  const handleToolAction = async (action: string) => {
    try {
      await invoke("jarvis_system_action", { action });
    } catch (err) {
      console.error(err);
    }
  };

  const formatNetSpeed = (kbps?: number) => {
    if (!kbps || kbps < 0.1) return "0.0 KB/s";
    if (kbps >= 1024) {
      return `${(kbps / 1024).toFixed(1)} MB/s`;
    }
    return `${kbps.toFixed(1)} KB/s`;
  };

  const ramPercent =
    telemetry.ramTotalMb > 0
      ? Math.round((telemetry.ramUsedMb / telemetry.ramTotalMb) * 100)
      : 0;

  const timerText = timer && timer.isRunning ? timer.formattedTime : "25:00";

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: "10px",
        width: "100%",
        height: "100%",
        boxSizing: "border-box",
      }}
    >
      {/* ── Left Card: Media Player ── */}
      <div
        style={{
          background: "rgba(255, 255, 255, 0.055)",
          borderRadius: "16px",
          padding: "14px 16px",
          display: "flex",
          flexDirection: "column",
          justifyContent: hasTrack ? "space-between" : "center",
          boxSizing: "border-box",
        }}
      >
        {/* Track Info Header */}
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          {/* Album Artwork */}
          <div
            style={{
              width: "52px",
              height: "52px",
              borderRadius: "12px",
              background: "rgba(255, 255, 255, 0.08)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              overflow: "hidden",
              flexShrink: 0,
            }}
          >
            {media.thumbnail ? (
              <img
                src={media.thumbnail}
                alt="Art"
                style={{ width: "100%", height: "100%", objectFit: "cover" }}
              />
            ) : (
              <MusicNoteIcon size={22} color="rgba(255, 255, 255, 0.45)" />
            )}
          </div>

          {/* Titles */}
          <div style={{ display: "flex", flexDirection: "column", minWidth: 0, flex: 1 }}>
            <span
              style={{
                fontSize: "15px",
                fontWeight: 600,
                color: "#f5f5f7",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
                letterSpacing: "-0.01em",
              }}
            >
              {hasTrack ? media.title : "Nothing Playing"}
            </span>
            <span
              style={{
                fontSize: "12px",
                color: "rgba(235, 235, 245, 0.58)",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
                marginTop: "2px",
              }}
            >
              {hasTrack
                ? media.artist || media.album || "Unknown Artist"
                : "Start playback in Spotify, Apple Music, or a browser."}
            </span>
          </div>
        </div>

        {/* Progress Scrubber */}
        {hasTrack && (
        <div style={{ display: "flex", flexDirection: "column", gap: "5px", margin: "6px 0" }}>
          <div
            onClick={handleSeek}
            style={{
              width: "100%",
              height: "4px",
              borderRadius: "2px",
              background: "rgba(255, 255, 255, 0.15)",
              cursor: media.durationMs > 0 ? "pointer" : "default",
              position: "relative",
            }}
          >
            <div
              style={{
                width: `${Math.min(100, Math.max(0, progress))}%`,
                height: "100%",
                borderRadius: "2px",
                background: "#ffffff",
                position: "relative",
              }}
            >
              {progress > 0 && (
                <div
                  style={{
                    position: "absolute",
                    right: "-4px",
                    top: "-3px",
                    width: "10px",
                    height: "10px",
                    borderRadius: "50%",
                    background: "#ffffff",
                    boxShadow: "0 0 8px rgba(255, 255, 255, 0.9)",
                  }}
                />
              )}
            </div>
          </div>

          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              fontSize: "11px",
              fontWeight: 600,
              color: "rgba(255, 255, 255, 0.4)",
              fontVariantNumeric: "tabular-nums",
            }}
          >
            <span>{formatTime(media.positionMs)}</span>
            <span>{media.durationMs > 0 ? formatTime(media.durationMs) : "--:--"}</span>
          </div>
        </div>
        )}

        {/* Playback Controls */}
        <div
          style={{
            display: hasTrack ? "flex" : "none",
            alignItems: "center",
            justifyContent: "center",
            gap: "28px",
          }}
        >
          <button
            onClick={onPrev}
            style={{
              background: "transparent",
              border: "none",
              color: "rgba(255, 255, 255, 0.7)",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              padding: "4px",
              transition: "color 0.15s ease",
            }}
            title="Previous track"
            onMouseEnter={(e) => (e.currentTarget.style.color = "#ffffff")}
            onMouseLeave={(e) => (e.currentTarget.style.color = "rgba(255, 255, 255, 0.7)")}
          >
            <BackwardIcon size={18} />
          </button>

          {/* Primary Play Button (Crisp White Circle) */}
          <button
            onClick={onTogglePlay}
            style={{
              width: "40px",
              height: "40px",
              borderRadius: "50%",
              background: "#ffffff",
              color: "#000000",
              border: "none",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              boxShadow: "0 4px 12px rgba(0, 0, 0, 0.5)",
              transition: "transform 0.12s ease",
            }}
            title={media.playing ? "Pause" : "Play"}
            onMouseDown={(e) => (e.currentTarget.style.transform = "scale(0.95)")}
            onMouseUp={(e) => (e.currentTarget.style.transform = "scale(1)")}
          >
            {media.playing ? (
              <PauseIcon size={18} color="#000000" />
            ) : (
              <PlayIcon size={18} color="#000000" style={{ marginLeft: "2px" }} />
            )}
          </button>

          <button
            onClick={onNext}
            style={{
              background: "transparent",
              border: "none",
              color: "rgba(255, 255, 255, 0.7)",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              padding: "4px",
              transition: "color 0.15s ease",
            }}
            title="Next track"
            onMouseEnter={(e) => (e.currentTarget.style.color = "#ffffff")}
            onMouseLeave={(e) => (e.currentTarget.style.color = "rgba(255, 255, 255, 0.7)")}
          >
            <ForwardIcon size={18} />
          </button>
        </div>
      </div>

      {/* ── Right Card: Focus Timer & Hardware Stats & Quick Tools ── */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: "8px",
          height: "100%",
        }}
      >
        {/* 1. Focus Timer Row */}
        <button
          onClick={() => onSelectTab("timer")}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            background: "rgba(255, 255, 255, 0.055)",
            border: "0.5px solid rgba(255, 255, 255, 0.08)",
            borderRadius: "14px",
            padding: "8px 12px",
            cursor: "pointer",
            color: "#ffffff",
            transition: "background 0.15s ease",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(255, 255, 255, 0.08)")}
          onMouseLeave={(e) => (e.currentTarget.style.background = "rgba(255, 255, 255, 0.055)")}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <CoffeeIcon size={15} color="#fbbf24" />
            <span style={{ fontSize: "13px", fontWeight: 600, color: "#ffffff" }}>
              Focus timer
            </span>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
            <span
              style={{
                fontSize: "13px",
                fontWeight: 600,
                color: "#ffffff",
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {timerText}
            </span>
            <ChevronRightIcon size={14} color="rgba(255, 255, 255, 0.4)" />
          </div>
        </button>

        {/* 2. Hardware Activity Card (CPU, RAM, Network) */}
        <div
          onClick={() => onSelectTab("system")}
          style={{
            background: "rgba(255, 255, 255, 0.055)",
            border: "0.5px solid rgba(255, 255, 255, 0.08)",
            borderRadius: "14px",
            padding: "9px 12px",
            display: "flex",
            flexDirection: "column",
            gap: "7px",
            cursor: "pointer",
          }}
          title="Click to view full Activity monitor"
        >
          {/* CPU Row */}
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span
              style={{
                fontSize: "11px",
                fontWeight: 600,
                color: "rgba(255, 255, 255, 0.7)",
                width: "28px",
              }}
            >
              CPU
            </span>
            <div
              style={{
                flex: 1,
                height: "6px",
                borderRadius: "3px",
                background: "rgba(255, 255, 255, 0.12)",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${Math.round(telemetry.cpuPercent)}%`,
                  height: "100%",
                  borderRadius: "3px",
                  background: "linear-gradient(90deg, #ff9f0a, #f97316)",
                  transition: "width 0.4s ease",
                }}
              />
            </div>
            <span
              style={{
                fontSize: "11px",
                fontWeight: 600,
                color: "#ffffff",
                width: "32px",
                textAlign: "right",
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {Math.round(telemetry.cpuPercent)}%
            </span>
          </div>

          {/* RAM Row */}
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span
              style={{
                fontSize: "11px",
                fontWeight: 600,
                color: "rgba(255, 255, 255, 0.7)",
                width: "28px",
              }}
            >
              RAM
            </span>
            <div
              style={{
                flex: 1,
                height: "6px",
                borderRadius: "3px",
                background: "rgba(255, 255, 255, 0.12)",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${ramPercent}%`,
                  height: "100%",
                  borderRadius: "3px",
                  background: "linear-gradient(90deg, #ff9f0a, #f97316)",
                  transition: "width 0.4s ease",
                }}
              />
            </div>
            <span
              style={{
                fontSize: "11px",
                fontWeight: 600,
                color: "#ffffff",
                width: "32px",
                textAlign: "right",
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {ramPercent}%
            </span>
          </div>

          {/* Network Row */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              fontSize: "11px",
              paddingTop: "2px",
              borderTop: "1px solid rgba(255, 255, 255, 0.05)",
            }}
          >
            <span style={{ fontWeight: 600, color: "rgba(255, 255, 255, 0.6)" }}>
              Network
            </span>
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "3px" }}>
                <ArrowDownIcon size={11} color="#34d399" />
                <span
                  style={{
                    fontSize: "11px",
                    fontWeight: 600,
                    color: "#34d399",
                    fontVariantNumeric: "tabular-nums",
                  }}
                >
                  {formatNetSpeed(telemetry.netDownloadKbps)}
                </span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "3px" }}>
                <ArrowUpIcon size={11} color="#60a5fa" />
                <span
                  style={{
                    fontSize: "11px",
                    fontWeight: 600,
                    color: "#60a5fa",
                    fontVariantNumeric: "tabular-nums",
                  }}
                >
                  {formatNetSpeed(telemetry.netUploadKbps)}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 3. Bottom Quick Tools: Calculator, Snipping Tool, Task Manager */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "8px" }}>
          <button
            onClick={() => handleToolAction("calc")}
            style={{
              background: "rgba(255, 255, 255, 0.055)",
              border: "0.5px solid rgba(255, 255, 255, 0.08)",
              borderRadius: "12px",
              padding: "7px 0",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              color: "rgba(255, 255, 255, 0.7)",
              transition: "all 0.15s ease",
            }}
            title="Calculator"
            onMouseEnter={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.1)";
              e.currentTarget.style.color = "#ffffff";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.055)";
              e.currentTarget.style.color = "rgba(255, 255, 255, 0.7)";
            }}
          >
            <CalculatorIcon size={15} />
          </button>

          <button
            onClick={() => handleToolAction("snip")}
            style={{
              background: "rgba(255, 255, 255, 0.055)",
              border: "0.5px solid rgba(255, 255, 255, 0.08)",
              borderRadius: "12px",
              padding: "7px 0",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              color: "rgba(255, 255, 255, 0.7)",
              transition: "all 0.15s ease",
            }}
            title="Snipping Tool / Screen Clip"
            onMouseEnter={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.1)";
              e.currentTarget.style.color = "#ffffff";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.055)";
              e.currentTarget.style.color = "rgba(255, 255, 255, 0.7)";
            }}
          >
            <ScissorsIcon size={15} />
          </button>

          <button
            onClick={() => handleToolAction("taskmgr")}
            style={{
              background: "rgba(255, 255, 255, 0.055)",
              border: "0.5px solid rgba(255, 255, 255, 0.08)",
              borderRadius: "12px",
              padding: "7px 0",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              color: "rgba(255, 255, 255, 0.7)",
              transition: "all 0.15s ease",
            }}
            title="Task Manager / Performance"
            onMouseEnter={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.1)";
              e.currentTarget.style.color = "#ffffff";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.055)";
              e.currentTarget.style.color = "rgba(255, 255, 255, 0.7)";
            }}
          >
            <ActivityIcon size={15} />
          </button>
        </div>
      </div>
    </div>
  );
};
