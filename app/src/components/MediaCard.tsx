import React, { useState } from "react";
import { MediaState } from "../types";
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Music,
  Volume2,
  Mic2,
  Headphones,
} from "lucide-react";
import { Waveform } from "./Waveform";

interface MediaCardProps {
  media: MediaState;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (ms: number) => void;
  volume: number;
  onSetVolume: (vol: number) => void;
}

export const MediaCard: React.FC<MediaCardProps> = ({
  media,
  onTogglePlay,
  onNext,
  onPrev,
  onSeek,
  volume,
  onSetVolume,
}) => {
  const [showLyrics, setShowLyrics] = useState<boolean>(false);

  const formatTime = (ms: number) => {
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s < 10 ? "0" : ""}${s}`;
  };

  const formatRemaining = (elapsedMs: number, durationMs: number) => {
    if (durationMs <= 0) return "-0:00";
    const remainingMs = Math.max(0, durationMs - elapsedMs);
    const totalSec = Math.floor(remainingMs / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `-${m}:${s < 10 ? "0" : ""}${s}`;
  };

  const progress =
    media.durationMs > 0 ? (media.positionMs / media.durationMs) * 100 : 0;

  const handleSeekClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (media.durationMs <= 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    onSeek(Math.floor(ratio * media.durationMs));
  };

  const handleVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onSetVolume(parseFloat(e.target.value));
  };

  // Sample or placeholder lyrics matching track state
  const mockLyrics = media.title
    ? [
        `♪ Now listening to "${media.title}"`,
        media.artist ? `by ${media.artist}` : "",
        "",
        "♪ [Verse 1]",
        "Floating high through the city lights",
        "Rhythms echo through the quiet night",
        "Caught in motion, feeling pure delight",
        "",
        "♪ [Chorus]",
        `Singing along with ${media.title}`,
        "Every second counting down in style",
        "JARVIS floating on the Windows screen",
        "Smoothest dynamic notch you've ever seen",
        "",
        "♪ [Outro]",
        "Echoes fading away...",
      ].filter((l) => l !== "")
    : ["No media track active.", "Play a song in Spotify, YouTube, or Apple Music."];

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "10px",
        width: "100%",
        height: "100%",
        padding: "2px 2px",
      }}
    >
      {showLyrics ? (
        /* Lyrics Sheet View */
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "8px",
            flex: 1,
            height: "140px",
            background: "#181820",
            borderRadius: "14px",
            padding: "10px 14px",
            border: "1px solid rgba(255, 255, 255, 0.12)",
            overflowY: "auto",
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              borderBottom: "1px solid #262632",
              paddingBottom: "6px",
            }}
          >
            <span
              style={{
                fontSize: "12px",
                fontWeight: 800,
                color: "#38bdf8",
                letterSpacing: "0.05em",
                textTransform: "uppercase",
              }}
            >
              Lyrics View
            </span>
            <button
              onClick={() => setShowLyrics(false)}
              style={{
                background: "#262632",
                border: "none",
                color: "#ffffff",
                padding: "3px 10px",
                borderRadius: "6px",
                fontSize: "11px",
                fontWeight: 700,
                cursor: "pointer",
              }}
            >
              Back
            </button>
          </div>

          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "4px",
              paddingTop: "4px",
            }}
          >
            {mockLyrics.map((line, idx) => (
              <p
                key={idx}
                style={{
                  margin: 0,
                  fontSize: idx === 1 || idx === 9 ? "13px" : "12px",
                  fontWeight: idx === 1 || idx === 9 ? 800 : 500,
                  color:
                    idx === 1 || idx === 9
                      ? "#ffffff"
                      : "#cbd5e1",
                  lineHeight: "1.5",
                  textShadow:
                    idx === 1 || idx === 9
                      ? "0 0 10px rgba(56, 189, 248, 0.5)"
                      : "none",
                }}
              >
                {line}
              </p>
            ))}
          </div>
        </div>
      ) : (
        /* Standard Media Card View */
        <>
          {/* Top row: Artwork & Track Metadata */}
          <div style={{ display: "flex", alignItems: "center", gap: "14px" }}>
            {/* Album Artwork */}
            <div
              style={{
                width: "56px",
                height: "56px",
                borderRadius: "14px",
                overflow: "hidden",
                background: "#1e1e28",
                border: "1px solid rgba(255, 255, 255, 0.16)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
                boxShadow: "0 8px 18px rgba(0, 0, 0, 0.4)",
              }}
            >
              {media.thumbnail ? (
                <img
                  src={media.thumbnail}
                  alt="Art"
                  style={{ width: "100%", height: "100%", objectFit: "cover" }}
                />
              ) : (
                <Music size={26} color="#818cf8" />
              )}
            </div>

            {/* Title & Artist */}
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                gap: "3px",
                flex: 1,
                minWidth: 0,
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <span
                  style={{
                    fontSize: "15px",
                    fontWeight: 700,
                    color: "#ffffff",
                    whiteSpace: "nowrap",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    letterSpacing: "-0.01em",
                  }}
                >
                  {media.title || "No Media Playing"}
                </span>
                {media.playing && <Waveform isPlaying={true} />}
              </div>

              <span
                style={{
                  fontSize: "12px",
                  color: "#cbd5e1",
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
              >
                {media.artist || "Launch Spotify, Apple Music, or Browser"}
              </span>

              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "6px",
                  marginTop: "2px",
                }}
              >
                <Headphones size={12} color="#38bdf8" />
                <span
                  style={{
                    fontSize: "11px",
                    color: "#94a3b8",
                    fontWeight: 500,
                  }}
                >
                  Windows Audio Device
                </span>
              </div>
            </div>

            {/* Lyrics toggle button */}
            <button
              onClick={() => setShowLyrics(true)}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "5px",
                padding: "6px 12px",
                borderRadius: "10px",
                border: "1px solid rgba(255, 255, 255, 0.14)",
                background: "#1e1e28",
                color: "#ffffff",
                fontSize: "11px",
                fontWeight: 700,
                cursor: "pointer",
                transition: "all 0.15s ease",
              }}
              title="View lyrics"
            >
              <Mic2 size={13} color="#38bdf8" />
              <span>Lyrics</span>
            </button>
          </div>

          {/* Progress scrubber */}
          <div style={{ display: "flex", flexDirection: "column", gap: "5px" }}>
            <div
              onClick={handleSeekClick}
              style={{
                width: "100%",
                height: "6px",
                borderRadius: "3px",
                background: "rgba(255, 255, 255, 0.18)",
                cursor: "pointer",
                position: "relative",
              }}
            >
              <div
                style={{
                  width: `${Math.min(100, Math.max(0, progress))}%`,
                  height: "100%",
                  borderRadius: "3px",
                  background:
                    "linear-gradient(90deg, #38bdf8 0%, #818cf8 100%)",
                }}
              />
            </div>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: "10px",
                color: "rgba(255, 255, 255, 0.6)",
              }}
              className="tabular-nums"
            >
              <span>{formatTime(media.positionMs)}</span>
              <span>{formatRemaining(media.positionMs, media.durationMs)}</span>
            </div>
          </div>
        </>
      )}

      {/* Controls row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          paddingTop: "2px",
        }}
      >
        {/* Playback Buttons */}
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <button
            onClick={onPrev}
            style={{
              background: "rgba(255, 255, 255, 0.08)",
              border: "none",
              color: "#ffffff",
              cursor: "pointer",
              padding: "7px",
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <SkipBack size={16} />
          </button>

          <button
            onClick={onTogglePlay}
            style={{
              width: "38px",
              height: "38px",
              borderRadius: "50%",
              background: "#ffffff",
              border: "none",
              color: "#000000",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              boxShadow: "0 4px 14px rgba(255, 255, 255, 0.35)",
            }}
          >
            {media.playing ? (
              <Pause size={18} fill="#000" />
            ) : (
              <Play size={18} fill="#000" style={{ marginLeft: "2px" }} />
            )}
          </button>

          <button
            onClick={onNext}
            style={{
              background: "rgba(255, 255, 255, 0.08)",
              border: "none",
              color: "#ffffff",
              cursor: "pointer",
              padding: "7px",
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <SkipForward size={16} />
          </button>
        </div>

        {/* Volume slider */}
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <Volume2 size={15} color="#38bdf8" />
          <input
            type="range"
            min="0"
            max="1"
            step="0.01"
            value={volume}
            onChange={handleVolumeChange}
            style={{
              width: "80px",
              accentColor: "#38bdf8",
              cursor: "pointer",
              height: "4px",
            }}
          />
        </div>
      </div>
    </div>
  );
};
