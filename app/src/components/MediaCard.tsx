import React, { useState } from "react";
import { MediaState } from "../types";
import { Waveform } from "./Waveform";
import { LyricsPanel } from "./LyricsPanel";
import { BackwardIcon, ForwardIcon, MicIcon, MusicNoteIcon, PauseIcon, PlayIcon, SpeakerIcon } from "./IslandIcons";

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
  const hasTrack = !!(media.title || media.artist);

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
        <LyricsPanel media={media} onBack={() => setShowLyrics(false)} compact />
      ) : (
        /* Standard Media Card View */
        <>
          {/* Top row: Artwork & Track Metadata */}
          <div style={{ display: "flex", alignItems: "center", gap: "14px", flex: hasTrack ? undefined : 1 }}>
            {hasTrack ? (
              <>
                <div
                  style={{
                    width: "56px",
                    height: "56px",
                    borderRadius: "12px",
                    overflow: "hidden",
                    background: "rgba(120, 120, 128, 0.18)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                  }}
                >
                  {media.thumbnail ? (
                    <img
                      src={media.thumbnail}
                      alt="Album art"
                      style={{ width: "100%", height: "100%", objectFit: "cover" }}
                    />
                  ) : (
                    <MusicNoteIcon size={24} strokeWidth={1.6} color="rgba(235, 235, 245, 0.55)" />
                  )}
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: "3px", flex: 1, minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
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
                      {media.title}
                    </span>
                    {media.playing && <Waveform isPlaying={true} />}
                  </div>
                  <span
                    style={{
                      fontSize: "12px",
                      color: "rgba(235, 235, 245, 0.6)",
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                    }}
                  >
                    {media.artist || media.album || "Unknown Artist"}
                  </span>
                </div>

                <button
                  onClick={() => setShowLyrics(true)}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "5px",
                    height: "30px",
                    padding: "0 11px",
                    borderRadius: "8px",
                    border: "none",
                    background: "rgba(120, 120, 128, 0.20)",
                    color: "rgba(235, 235, 245, 0.9)",
                    fontSize: "12px",
                    fontWeight: 500,
                    cursor: "pointer",
                    flexShrink: 0,
                  }}
                  title="View lyrics"
                >
                  <MicIcon size={13} strokeWidth={1.7} />
                  <span>Lyrics</span>
                </button>
              </>
            ) : (
              <div
                style={{
                  flex: 1,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "6px",
                  padding: "18px 0",
                  textAlign: "center",
                }}
              >
                <div
                  style={{
                    width: "46px",
                    height: "46px",
                    borderRadius: "13px",
                    background: "rgba(120, 120, 128, 0.16)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    marginBottom: "2px",
                  }}
                >
                  <MusicNoteIcon size={21} strokeWidth={1.6} color="rgba(235, 235, 245, 0.55)" />
                </div>
                <div style={{ fontSize: "15px", fontWeight: 600, color: "#f5f5f7" }}>
                  Nothing Playing
                </div>
                <div style={{ fontSize: "12px", color: "rgba(235, 235, 245, 0.55)" }}>
                  Start playback in Spotify, Apple Music, or a browser.
                </div>
              </div>
            )}
          </div>

          {/* Progress scrubber */}
          {hasTrack && (
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
                    "#f5f5f7",
                }}
              />
            </div>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: "11px",
                color: "rgba(255, 255, 255, 0.6)",
              }}
              className="tabular-nums"
            >
              <span>{formatTime(media.positionMs)}</span>
              <span>{formatRemaining(media.positionMs, media.durationMs)}</span>
            </div>
          </div>
          )}
        </>
      )}

      {/* Controls row */}
      <div
        style={{
          display: hasTrack ? "flex" : "none",
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
            <BackwardIcon size={16} />
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
              <PauseIcon size={18} />
            ) : (
              <PlayIcon size={18} style={{ marginLeft: "2px" }} />
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
            <ForwardIcon size={16} />
          </button>
        </div>

        {/* Volume slider */}
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <SpeakerIcon size={15} color="#0a84ff" />
          <input
            type="range"
            min="0"
            max="1"
            step="0.01"
            value={volume}
            onChange={handleVolumeChange}
            style={{
              width: "80px",
              accentColor: "#0a84ff",
              cursor: "pointer",
              height: "4px",
            }}
          />
        </div>
      </div>
    </div>
  );
};
