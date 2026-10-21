import React, { useEffect, useState } from "react";
import { Waveform } from "./Waveform";
import { MediaState, SystemTelemetry, TimerState } from "../types";
import { Music, Timer, Zap, Share2, Smartphone } from "lucide-react";

interface CollapsedPillProps {
  media: MediaState;
  telemetry: SystemTelemetry;
  timer?: TimerState | null;
  clockFormat?: "12h" | "24h";
  isDragging?: boolean;
  reducedMotion?: boolean;
}

export const CollapsedPill: React.FC<CollapsedPillProps> = ({
  media,
  telemetry,
  timer,
  clockFormat = "12h",
  isDragging = false,
  reducedMotion = false,
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
        padding: "0 10px",
        cursor: "pointer",
        userSelect: "none",
        backgroundColor: "#000000",
        color: "#ffffff",
        position: "relative",
        boxSizing: "border-box",
        fontFamily: "var(--font-sans, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, system-ui, sans-serif)",
      }}
      role="status"
      aria-label="JARVIS dynamic island collapsed status pill"
    >
      {/* ── Leading Presentation ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "6px",
          flexShrink: 0,
        }}
      >
        {isDragging ? (
          <div
            style={{
              width: "22px",
              height: "22px",
              borderRadius: "50%",
              backgroundColor: "rgba(50, 173, 230, 0.2)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Share2 size={12} color="#0a84ff" />
          </div>
        ) : isTimerActive ? (
          <div
            style={{
              width: "22px",
              height: "22px",
              borderRadius: "50%",
              backgroundColor: "rgba(255, 149, 0, 0.2)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Timer size={12} color="#ff9f0a" />
          </div>
        ) : isPlaying ? (
          media.thumbnail ? (
            <img
              src={media.thumbnail}
              alt="Art"
              style={{
                width: "20px",
                height: "20px",
                borderRadius: "5px",
                objectFit: "cover",
                boxShadow: "0 2px 6px rgba(0, 0, 0, 0.5)",
              }}
            />
          ) : (
            <div
              style={{
                width: "20px",
                height: "20px",
                borderRadius: "5px",
                backgroundColor: "#ff375f",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Music size={11} color="#ffffff" />
            </div>
          )
        ) : (
          /* Battery / Status Ring */
          <div
            style={{
              width: "18px",
              height: "18px",
              borderRadius: "50%",
              border: `2px solid ${telemetry.isCharging ? "#30d158" : "rgba(255, 255, 255, 0.25)"}`,
              
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              transition: "all 0.3s ease",
            }}
            title={`Battery: ${batteryPercent}% ${telemetry.isCharging ? "(Charging)" : ""}`}
          >
            {telemetry.isCharging ? (
              <Zap size={10} color="#30d158" fill="#30d158" />
            ) : (
              <span
                style={{
                  fontSize: "11px",
                  fontWeight: 600,
                  color: "rgba(255, 255, 255, 0.9)",
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                {batteryPercent}
              </span>
            )}
          </div>
        )}
      </div>

      {/* ── Center Presentation ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          overflow: "hidden",
          padding: "0 6px",
          flex: 1,
          textAlign: "center",
        }}
      >
        {isDragging ? (
          <span
            style={{
              fontSize: "11px",
              fontWeight: 600,
              color: "#0a84ff",
              letterSpacing: "-0.01em",
            }}
          >
            Drop to Beam
          </span>
        ) : isTimerActive ? (
          <span
            className="tabular-nums"
            style={{
              fontSize: "12px",
              fontWeight: 600,
              color: "#ff9f0a",
              letterSpacing: "-0.01em",
            }}
          >
            {timer.formattedTime}
          </span>
        ) : isPlaying && media.title ? (
          <div
            className="text-mask-fade"
            style={{ maxWidth: "120px" }}
            title={media.artist ? `${media.title} • ${media.artist}` : media.title}
          >
            <span
              style={{
                fontSize: "11px",
                fontWeight: 600,
                color: "#ffffff",
                whiteSpace: "nowrap",
                letterSpacing: "-0.01em",
              }}
            >
              {media.title}
            </span>
          </div>
        ) : (
          <span
            className="tabular-nums"
            style={{
              fontSize: "13px",
              fontWeight: 600,
              color: "rgba(245, 245, 247, 0.95)",
              letterSpacing: "-0.01em",
            }}
          >
            {time}
          </span>
        )}
      </div>

      {/* ── Trailing Presentation ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "flex-end",
          gap: "4px",
          flexShrink: 0,
        }}
      >
        {isDragging ? (
          <Smartphone size={12} color="#0a84ff" />
        ) : isPlaying ? (
          <Waveform
            isPlaying={true}
            width={18}
            height={11}
            color="#30d158"
            reducedMotion={reducedMotion}
          />
        ) : hasTrack ? (
          <Music size={13} color="rgba(255, 255, 255, 0.65)" />
        ) : (
          <span
            className="tabular-nums"
            style={{
              fontSize: "11.5px",
              fontWeight: 500,
              color: "rgba(255, 255, 255, 0.5)",
              letterSpacing: "0.02em",
            }}
          >
            {batteryPercent}%
          </span>
        )}
      </div>
    </div>
  );
};
