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
} from "lucide-react";
import { Waveform } from "./Waveform";

interface MusicRoomViewProps {
  media: MediaState;
  volume: number;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (ms: number) => void;
  onSetVolume: (vol: number) => void;
}

export const MusicRoomView: React.FC<MusicRoomViewProps> = ({
  media,
  volume,
  onTogglePlay,
  onNext,
  onPrev,
  onSeek,
  onSetVolume,
}) => {
  const [showLyrics, setShowLyrics] = useState<boolean>(false);
  const [outputDevice, setOutputDevice] = useState<string>("Speakers");
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

  const cycleAudioOutput = () => {
    const devices = ["Speakers", "Headphones", "System Output"];
    const idx = (devices.indexOf(outputDevice) + 1) % devices.length;
    setOutputDevice(devices[idx]);
  };

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "1.1fr 0.9fr",
        gap: "12px",
        width: "100%",
        height: "100%",
        boxSizing: "border-box",
      }}
    >
      {/* ── Left Player Card ── */}
      <div
        style={{
          background: "rgba(255, 255, 255, 0.04)",
          border: "1px solid rgba(255, 255, 255, 0.08)",
          borderRadius: "16px",
          padding: "12px 14px",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          boxSizing: "border-box",
        }}
      >
        {/* Track Header */}
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
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
              <Music size={22} color="rgba(255, 255, 255, 0.5)" />
            )}
          </div>

          <div style={{ display: "flex", flexDirection: "column", minWidth: 0, flex: 1 }}>
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
              {hasTrack ? media.title : "Not playing"}
            </span>
            <span
              style={{
                fontSize: "12px",
                color: "rgba(255, 255, 255, 0.55)",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
                marginTop: "2px",
              }}
            >
              {hasTrack
                ? media.artist || media.album || "Unknown Artist"
                : "Choose something you love"}
            </span>
          </div>
        </div>

        {/* Scrub Bar */}
        <div style={{ display: "flex", flexDirection: "column", gap: "4px", margin: "4px 0" }}>
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
              fontSize: "10px",
              fontWeight: 600,
              color: "rgba(255, 255, 255, 0.4)",
              fontVariantNumeric: "tabular-nums",
            }}
          >
            <span>{formatTime(media.positionMs)}</span>
            <span>{media.durationMs > 0 ? formatTime(media.durationMs) : "--:--"}</span>
          </div>
        </div>

        {/* Transport Controls */}
        <div
          style={{
            display: "flex",
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
            }}
            title="Previous track"
          >
            <SkipBack size={18} fill="currentColor" />
          </button>

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
            }}
            title={media.playing ? "Pause" : "Play"}
          >
            {media.playing ? (
              <Pause size={18} fill="#000000" color="#000000" />
            ) : (
              <Play size={18} fill="#000000" color="#000000" style={{ marginLeft: "2px" }} />
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
            }}
            title="Next track"
          >
            <SkipForward size={18} fill="currentColor" />
          </button>
        </div>

        {/* Bottom Toolbar: Audio Device, Lyrics */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            paddingTop: "6px",
            borderTop: "1px solid rgba(255, 255, 255, 0.06)",
          }}
        >
          {/* Audio Output Selector */}
          <button
            onClick={cycleAudioOutput}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "5px",
              background: "rgba(255, 255, 255, 0.06)",
              border: "none",
              borderRadius: "8px",
              padding: "4px 8px",
              color: "#38bdf8",
              fontSize: "11px",
              fontWeight: 600,
              cursor: "pointer",
            }}
            title="Click to switch audio output device"
          >
            <Volume2 size={12} color="#38bdf8" />
            <span style={{ maxWidth: "90px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {outputDevice}
            </span>
          </button>

          {/* Fullscreen / Ambient */}
          <button
            onClick={() => setShowLyrics(!showLyrics)}
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              background: "rgba(255, 255, 255, 0.06)",
              border: "none",
              borderRadius: "8px",
              padding: "4px 8px",
              color: "#cbd5e1",
              fontSize: "11px",
              fontWeight: 600,
              cursor: "pointer",
            }}
            title="Toggle Lyrics / Room"
          >
            <Mic2 size={12} />
            <span>Lyrics</span>
          </button>
        </div>
      </div>

      {/* ── Right Column: Listening Room / Waveform ── */}
      <div
        style={{
          background: "rgba(255, 255, 255, 0.04)",
          border: "1px solid rgba(255, 255, 255, 0.08)",
          borderRadius: "16px",
          padding: "14px",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          textAlign: "center",
          boxSizing: "border-box",
        }}
      >
        {hasTrack ? (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "8px",
              width: "100%",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <Music size={20} color="#38bdf8" />
              <Waveform isPlaying={media.playing} width={40} height={20} />
            </div>
            <span style={{ fontSize: "14px", fontWeight: 700, color: "#ffffff" }}>
              Now Playing
            </span>
            <span
              style={{
                fontSize: "12px",
                color: "rgba(255, 255, 255, 0.6)",
                maxWidth: "220px",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {media.album || media.artist || media.title}
            </span>

            {/* Quick Volume Slider in Listening Room */}
            <div style={{ display: "flex", alignItems: "center", gap: "8px", width: "80%", marginTop: "6px" }}>
              <Volume2 size={13} color="rgba(255, 255, 255, 0.5)" />
              <input
                type="range"
                min="0"
                max="1"
                step="0.01"
                value={volume}
                onChange={(e) => onSetVolume(parseFloat(e.target.value))}
                style={{
                  flex: 1,
                  accentColor: "#38bdf8",
                  cursor: "pointer",
                }}
              />
            </div>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "8px" }}>
            <Music size={28} color="rgba(255, 255, 255, 0.35)" />
            <span style={{ fontSize: "16px", fontWeight: 700, color: "#ffffff", letterSpacing: "-0.01em" }}>
              Your listening room
            </span>
            <span
              style={{
                fontSize: "12px",
                color: "rgba(255, 255, 255, 0.5)",
                maxWidth: "210px",
                lineHeight: "1.4",
              }}
            >
              Play a track in your music app to begin.
            </span>
          </div>
        )}
      </div>
    </div>
  );
};
