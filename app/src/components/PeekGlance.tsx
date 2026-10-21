import React from "react";
import { MediaState, SystemTelemetry, TimerState } from "../types";
import { Waveform } from "./Waveform";
import { Music, Timer, Zap, Bell } from "lucide-react";

interface PeekGlanceProps {
  media: MediaState;
  telemetry: SystemTelemetry;
  timer?: TimerState | null;
  message?: string | null;
  reducedMotion?: boolean;
}

export const PeekGlance: React.FC<PeekGlanceProps> = ({
  media,
  telemetry,
  timer,
  message,
  reducedMotion = false,
}) => {
  const isPlaying = media.playing;
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
        padding: "0 14px",
        cursor: "pointer",
        userSelect: "none",
        backgroundColor: "#000000",
        color: "#ffffff",
        position: "relative",
        boxSizing: "border-box",
        fontFamily: "var(--font-sans, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, system-ui, sans-serif)",
      }}
    >
      {/* Leading icon / art */}
      <div style={{ display: "flex", alignItems: "center", gap: "8px", flexShrink: 0 }}>
        {message ? (
          <div
            style={{
              width: "26px",
              height: "26px",
              borderRadius: "50%",
              backgroundColor: "rgba(50, 173, 230, 0.2)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Bell size={13} color="#32ade6" />
          </div>
        ) : isTimerActive ? (
          <div
            style={{
              width: "26px",
              height: "26px",
              borderRadius: "50%",
              backgroundColor: "rgba(255, 149, 0, 0.2)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Timer size={14} color="#ff9500" />
          </div>
        ) : isPlaying ? (
          media.thumbnail ? (
            <img
              src={media.thumbnail}
              alt="Track Artwork"
              style={{
                width: "26px",
                height: "26px",
                borderRadius: "6px",
                objectFit: "cover",
                boxShadow: "0 2px 8px rgba(0, 0, 0, 0.6)",
              }}
            />
          ) : (
            <div
              style={{
                width: "26px",
                height: "26px",
                borderRadius: "6px",
                backgroundColor: "#ff2d55",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Music size={13} color="#ffffff" />
            </div>
          )
        ) : (
          <div
            style={{
              width: "22px",
              height: "22px",
              borderRadius: "50%",
              border: `2px solid ${telemetry.isCharging ? "#34c759" : "rgba(255, 255, 255, 0.3)"}`,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            {telemetry.isCharging ? (
              <Zap size={11} color="#34c759" fill="#34c759" />
            ) : (
              <span
                style={{
                  fontSize: "11px",
                  fontWeight: 600,
                  color: "#ffffff",
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                {batteryPercent}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Center details */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          overflow: "hidden",
          padding: "0 8px",
          flex: 1,
          textAlign: "left",
          minWidth: 0,
        }}
      >
        {message ? (
          <span
            style={{
              fontSize: "12px",
              fontWeight: 600,
              color: "#ffffff",
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
          >
            {message}
          </span>
        ) : isTimerActive ? (
          <>
            <span
              style={{
                fontSize: "11px",
                fontWeight: 500,
                color: "rgba(255, 255, 255, 0.7)",
                
              }}
            >
              Focus Timer
            </span>
            <span
              className="tabular-nums"
              style={{
                fontSize: "13px",
                fontWeight: 600,
                color: "#ff9500",
                lineHeight: 1.1,
              }}
            >
              {timer.formattedTime}
            </span>
          </>
        ) : isPlaying && media.title ? (
          <>
            <span
              style={{
                fontSize: "12px",
                fontWeight: 600,
                color: "#ffffff",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              {media.title}
            </span>
            <span
              style={{
                fontSize: "11.5px",
                fontWeight: 400,
                color: "rgba(255, 255, 255, 0.65)",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              {media.artist || "Playing"}
            </span>
          </>
        ) : (
          <span
            style={{
              fontSize: "12px",
              fontWeight: 500,
              color: "rgba(255, 255, 255, 0.9)",
              letterSpacing: "-0.01em",
            }}
          >
            JARVIS Overlay
          </span>
        )}
      </div>

      {/* Trailing activity badge */}
      <div style={{ display: "flex", alignItems: "center", gap: "6px", flexShrink: 0 }}>
        {isPlaying ? (
          <Waveform
            isPlaying={true}
            width={22}
            height={13}
            color="#34c759"
            reducedMotion={reducedMotion}
          />
        ) : isTimerActive ? (
          <div
            style={{
              padding: "2px 6px",
              borderRadius: "4px",
              backgroundColor: "rgba(255, 149, 0, 0.15)",
              fontSize: "11px",
              fontWeight: 600,
              color: "#ff9500",
            }}
          >
            RUNNING
          </div>
        ) : (
          <span
            className="tabular-nums"
            style={{
              fontSize: "11px",
              fontWeight: 500,
              color: "rgba(255, 255, 255, 0.6)",
            }}
          >
            {batteryPercent}%
          </span>
        )}
      </div>
    </div>
  );
};
