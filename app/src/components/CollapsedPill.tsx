import React, { useEffect, useState } from "react";
import { Waveform } from "./Waveform";
import { MediaState, SystemTelemetry, TimerState } from "../types";
import { Music, Timer, Zap, Share2 } from "lucide-react";

interface CollapsedPillProps {
  media: MediaState;
  telemetry: SystemTelemetry;
  timer?: TimerState | null;
  clockFormat?: "12h" | "24h";
  isDragging?: boolean;
}

export const CollapsedPill: React.FC<CollapsedPillProps> = ({
  media,
  telemetry,
  timer,
  clockFormat = "12h",
  isDragging = false,
}) => {
  const [time, setTime] = useState<string>("");

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTime(
        now.toLocaleTimeString([], {
          hour: "numeric",
          minute: "2-digit",
          hour12: clockFormat === "12h",
        })
      );
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, [clockFormat]);

  const isPlaying = media.playing;
  const hasTrack = !!(media.title || media.artist);
  const isTimerActive = timer && timer.isRunning;
  const batteryPercent = telemetry.batteryPercent ?? 100;

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        width: "100%",
        height: "100%",
        padding: "0 12px",
        cursor: "pointer",
        userSelect: "none",
        backgroundColor: "#050508",
        color: "#ffffff",
        position: "relative",
      }}
    >
      {/* ── Left Item: Green Glowing Status / Battery Ring ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
        }}
        title={`Battery: ${batteryPercent}% ${telemetry.isCharging ? "(Charging)" : ""}`}
      >
        <div
          style={{
            width: "20px",
            height: "20px",
            borderRadius: "50%",
            border: "2.5px solid #22c55e",
            boxShadow: "0 0 10px rgba(34, 197, 94, 0.75), inset 0 0 4px rgba(34, 197, 94, 0.3)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            transition: "all 0.3s ease",
          }}
        >
          {telemetry.isCharging ? (
            <Zap size={10} color="#22c55e" fill="#22c55e" />
          ) : (
            <span style={{ width: "4px", height: "4px", borderRadius: "50%", backgroundColor: "#22c55e" }} />
          )}
        </div>
      </div>

      {/* ── Center Item: Recessed Darker Capsule with Live Time / Timer ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "rgba(255, 255, 255, 0.08)",
          border: "1px solid rgba(255, 255, 255, 0.06)",
          borderRadius: "9999px",
          padding: "3px 14px",
          minWidth: "86px",
          textAlign: "center",
          boxShadow: "inset 0 1px 3px rgba(0, 0, 0, 0.5)",
        }}
      >
        {isDragging ? (
          <div style={{ display: "flex", alignItems: "center", gap: "6px", color: "#38bdf8" }}>
            <Share2 size={12} color="#38bdf8" />
            <span
              style={{
                fontSize: "12px",
                fontWeight: 700,
                color: "#38bdf8",
                letterSpacing: "-0.01em",
              }}
            >
              Drop to Beam
            </span>
          </div>
        ) : isTimerActive ? (
          <div style={{ display: "flex", alignItems: "center", gap: "5px" }}>
            <Timer size={12} color="#fbbf24" />
            <span
              className="tabular-nums"
              style={{
                fontSize: "12px",
                fontWeight: 700,
                color: "#fbbf24",
                letterSpacing: "-0.01em",
              }}
            >
              {timer.formattedTime}
            </span>
          </div>
        ) : (
          <span
            className="tabular-nums"
            style={{
              fontSize: "12.5px",
              fontWeight: 600,
              color: "#ffffff",
              letterSpacing: "0.01em",
            }}
          >
            {time}
          </span>
        )}
      </div>

      {/* ── Right Item: White Music Note / Live Waveform / Thumbnail ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
        }}
      >
        {isPlaying ? (
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            {media.thumbnail ? (
              <img
                src={media.thumbnail}
                alt="Track"
                style={{
                  width: "18px",
                  height: "18px",
                  borderRadius: "5px",
                  objectFit: "cover",
                }}
              />
            ) : (
              <Music size={14} color="#ffffff" />
            )}
            <Waveform isPlaying={true} width={20} height={12} />
          </div>
        ) : hasTrack ? (
          <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
            <Music size={15} color="#ffffff" />
          </div>
        ) : (
          <span style={{ width: "14px" }} />
        )}
      </div>
    </div>
  );
};
