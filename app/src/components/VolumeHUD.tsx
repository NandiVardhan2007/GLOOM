import React from "react";
import { Volume2, VolumeX, Volume1 } from "lucide-react";

interface VolumeHUDProps {
  volume: number; // 0.0 to 1.0
  isMuted: boolean;
}

export const VolumeHUD: React.FC<VolumeHUDProps> = ({ volume, isMuted }) => {
  const percent = Math.round(volume * 100);

  const getIcon = () => {
    if (isMuted || percent === 0) return <VolumeX size={16} color="#f87171" />;
    if (percent < 50) return <Volume1 size={16} color="#0a84ff" />;
    return <Volume2 size={16} color="#0a84ff" />;
  };

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        width: "100%",
        height: "100%",
        padding: "0 16px",
        gap: "12px",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
        {getIcon()}
        <span
          style={{
            fontSize: "12px",
            fontWeight: 600,
            color: "rgba(255, 255, 255, 0.9)",
          }}
        >
          {isMuted ? "Muted" : "Volume"}
        </span>
      </div>

      {/* Progress track */}
      <div
        style={{
          flex: 1,
          height: "6px",
          borderRadius: "3px",
          background: "rgba(255, 255, 255, 0.15)",
          overflow: "hidden",
          position: "relative",
        }}
      >
        <div
          style={{
            height: "100%",
            width: `${isMuted ? 0 : percent}%`,
            borderRadius: "3px",
            background: "linear-gradient(90deg, #0a84ff 0%, #818cf8 100%)",
            transition: "width 0.12s ease-out",
          }}
        />
      </div>

      <span
        className="tabular-nums"
        style={{
          fontSize: "12px",
          fontWeight: 700,
          color: "rgba(255, 255, 255, 0.95)",
          minWidth: "32px",
          textAlign: "right",
        }}
      >
        {isMuted ? "0%" : `${percent}%`}
      </span>
    </div>
  );
};
