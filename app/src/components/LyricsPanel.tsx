import React, { useEffect, useRef } from "react";
import { MediaState } from "../types";
import { useLyrics } from "../hooks/useLyrics";

interface LyricsPanelProps {
  media: MediaState;
  onBack?: () => void;
  subtitle?: string;
  compact?: boolean;
}

export const LyricsPanel: React.FC<LyricsPanelProps> = ({
  media,
  onBack,
  subtitle,
  compact = false,
}) => {
  const { lines, synced, status, activeIndex, retry } = useLyrics(media);
  const activeLineRef = useRef<HTMLParagraphElement | null>(null);

  useEffect(() => {
    if (activeIndex < 0 || !activeLineRef.current) return;
    activeLineRef.current.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [activeIndex]);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        width: "100%",
        height: "100%",
        minHeight: 0,
        background: "rgba(255, 255, 255, 0.055)",
        borderRadius: "14px",
        padding: compact ? "10px 12px" : "12px 14px",
        boxSizing: "border-box",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexShrink: 0,
          gap: "8px",
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: "12px", fontWeight: 600, color: "#f5f5f7" }}>Lyrics</div>
          <div
            style={{
              fontSize: "11px",
              color: "rgba(235, 235, 245, 0.55)",
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
          >
            {media.title}
            {media.artist ? ` — ${media.artist}` : ""}
            {subtitle ? ` · ${subtitle}` : ""}
          </div>
        </div>
        {onBack && (
          <button
            onClick={onBack}
            style={{
              background: "rgba(120, 120, 128, 0.20)",
              border: "none",
              color: "rgba(235, 235, 245, 0.85)",
              padding: "4px 10px",
              borderRadius: "7px",
              fontSize: "11px",
              fontWeight: 500,
              cursor: "pointer",
              flexShrink: 0,
            }}
          >
            Back
          </button>
        )}
      </div>

      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          gap: "6px",
          paddingTop: "2px",
          maskImage:
            "linear-gradient(180deg, transparent 0, #000 12px, #000 calc(100% - 12px), transparent 100%)",
          WebkitMaskImage:
            "linear-gradient(180deg, transparent 0, #000 12px, #000 calc(100% - 12px), transparent 100%)",
        }}
      >
        {status === "loading" && (
          <p style={{ margin: 0, fontSize: "12px", color: "rgba(235, 235, 245, 0.55)" }}>
            Loading lyrics…
          </p>
        )}
        {(status === "empty" || status === "error") && (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "8px",
              alignItems: "flex-start",
            }}
          >
            <p style={{ margin: 0, fontSize: "12px", color: "rgba(235, 235, 245, 0.55)" }}>
              {status === "error"
                ? "Couldn’t load lyrics for this track."
                : "No lyrics found for this track."}
            </p>
            <button
              onClick={retry}
              style={{
                background: "rgba(120, 120, 128, 0.20)",
                border: "none",
                color: "#0a84ff",
                padding: "4px 10px",
                borderRadius: "7px",
                fontSize: "11px",
                fontWeight: 500,
                cursor: "pointer",
              }}
            >
              Try again
            </button>
          </div>
        )}
        {status === "ready" &&
          lines.map((line, idx) => {
            const isActive = synced && idx === activeIndex;
            const isPast = synced && idx < activeIndex;
            return (
              <p
                key={`${line.timeMs}-${idx}`}
                ref={isActive ? activeLineRef : undefined}
                style={{
                  margin: 0,
                  fontSize: isActive ? "14px" : "12.5px",
                  fontWeight: isActive ? 600 : 400,
                  lineHeight: 1.45,
                  letterSpacing: "-0.005em",
                  color: isActive
                    ? "#f5f5f7"
                    : isPast
                    ? "rgba(235, 235, 245, 0.38)"
                    : "rgba(235, 235, 245, 0.62)",
                  transform: isActive ? "scale(1.01)" : "none",
                  transformOrigin: "left center",
                  transition:
                    "color var(--duration-base) var(--ease-out), opacity var(--duration-base) var(--ease-out), transform var(--duration-base) var(--ease-out)",
                }}
              >
                {line.text}
              </p>
            );
          })}
      </div>
    </div>
  );
};
