import React from "react";
import { ISLAND_COLOR, ISLAND_TYPE } from "../design/island";
import { SpeakerIcon, SpeakerMutedIcon } from "./IslandIcons";

interface VolumeHUDProps {
  volume: number;
  isMuted: boolean;
}

/** Apple's volume HUD: a compact capsule with a speaker glyph and a bar. */
export const VolumeHUD: React.FC<VolumeHUDProps> = ({ volume, isMuted }) => {
  const percent = Math.round(volume * 100);
  const value = isMuted ? 0 : percent;
  const accent = isMuted || percent === 0 ? ISLAND_COLOR.tertiary : ISLAND_COLOR.label;

  return (
    <div
      role="status"
      aria-label={isMuted ? "Muted" : `Volume ${percent} percent`}
      style={{
        display: "flex",
        alignItems: "center",
        width: "100%",
        height: "100%",
        padding: "0 12px",
        gap: 10,
        boxSizing: "border-box",
        fontFamily: "var(--font-sans)",
      }}
    >
      {isMuted || percent === 0 ? (
        <SpeakerMutedIcon size={15} color={accent} strokeWidth={1.9} />
      ) : (
        <SpeakerIcon size={15} color={accent} strokeWidth={1.9} />
      )}

      <div
        style={{
          flex: 1,
          height: 5,
          borderRadius: 999,
          background: "rgba(255, 255, 255, 0.16)",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            width: `${value}%`,
            height: "100%",
            borderRadius: 999,
            background: "#ffffff",
            transition: "width 120ms cubic-bezier(0.22, 1, 0.36, 1)",
          }}
        />
      </div>

      <span
        style={{
          ...ISLAND_TYPE.compactNumber,
          fontSize: 12,
          color: ISLAND_COLOR.secondary,
          minWidth: 30,
          textAlign: "right",
        }}
      >
        {value}%
      </span>
    </div>
  );
};
