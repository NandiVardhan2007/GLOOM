import React, { useState, useEffect } from "react";

interface ChittiMascotProps {
  size?: number;
  isPlaying?: boolean;
  status?: "connected" | "idle" | "listening";
  onClick?: () => void;
  accentColor?: string;
}

export const ChittiMascot: React.FC<ChittiMascotProps> = ({
  size = 28,
  isPlaying = false,
  status = "connected",
  onClick,
  accentColor = "#00f5d4",
}) => {
  const [isBlinking, setIsBlinking] = useState(false);

  // Natural periodic blinking
  useEffect(() => {
    const blinkInterval = setInterval(() => {
      setIsBlinking(true);
      setTimeout(() => setIsBlinking(false), 160);
    }, 3800);

    return () => clearInterval(blinkInterval);
  }, []);

  const eyeScaleY = isBlinking ? 0.15 : 1;

  return (
    <div
      onClick={onClick}
      style={{
        width: `${size}px`,
        height: `${size}px`,
        position: "relative",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        cursor: onClick ? "pointer" : "default",
        transform: isPlaying ? "scale(1.04)" : "scale(1)",
        transition: "transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1)",
        flexShrink: 0,
        userSelect: "none",
      }}
      title="JARVIS Chitti AI Companion"
    >
      <svg
        viewBox="0 0 100 100"
        width={size}
        height={size}
        style={{
          overflow: "visible",
          filter: isPlaying
            ? `drop-shadow(0 0 8px ${accentColor}80)`
            : "drop-shadow(0 2px 5px rgba(0,0,0,0.6))",
        }}
      >
        <defs>
          {/* Subtle Squircle Background Gradient */}
          <linearGradient id="chitti-bg-grad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#22232a" />
            <stop offset="100%" stopColor="#111216" />
          </linearGradient>

          {/* Neon Glow Filter */}
          <filter id="chitti-neon" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="0" stdDeviation="1.5" floodColor={accentColor} floodOpacity="0.7" />
          </filter>
        </defs>

        {/* Squircle (Curved Rounded Square) Body */}
        <rect
          x="4"
          y="4"
          width="92"
          height="92"
          rx="26"
          ry="26"
          fill="url(#chitti-bg-grad)"
          stroke="rgba(255, 255, 255, 0.12)"
          strokeWidth="2"
        />

        {/* ── Vector Mascot Face: ( • ‿ • ) ── */}
        <g filter="url(#chitti-neon)">
          {/* Left Bracket Cheek ( */}
          <path
            d="M 27 34 C 20 44, 20 56, 27 66"
            stroke={accentColor}
            strokeWidth="4.5"
            strokeLinecap="round"
            fill="none"
          />

          {/* Left Eye • */}
          <circle
            cx="39"
            cy="49"
            r={5.5 * eyeScaleY}
            fill={accentColor}
            style={{
              transition: "r 0.08s ease",
            }}
          />

          {/* Cute Smiling Mouth ‿ */}
          <path
            d="M 43 56 Q 50 64 57 56"
            stroke={accentColor}
            strokeWidth="4"
            strokeLinecap="round"
            fill="none"
          />

          {/* Right Eye • */}
          <circle
            cx="61"
            cy="49"
            r={5.5 * eyeScaleY}
            fill={accentColor}
            style={{
              transition: "r 0.08s ease",
            }}
          />

          {/* Right Bracket Cheek ) */}
          <path
            d="M 73 34 C 80 44, 80 56, 73 66"
            stroke={accentColor}
            strokeWidth="4.5"
            strokeLinecap="round"
            fill="none"
          />
        </g>
      </svg>

      {/* Live AI Status Dot */}
      {status === "connected" && (
        <span
          style={{
            position: "absolute",
            top: "-1px",
            right: "-1px",
            width: `${Math.max(6, Math.round(size * 0.24))}px`,
            height: `${Math.max(6, Math.round(size * 0.24))}px`,
            borderRadius: "50%",
            backgroundColor: isPlaying ? "#f59e0b" : "#22c55e",
            boxShadow: isPlaying ? "0 0 8px #f59e0b" : "0 0 8px #22c55e",
            border: "1.5px solid #000000",
          }}
        />
      )}
    </div>
  );
};
