import React from "react";

interface ChittiLogoProps {
  size?: number;
  className?: string;
  style?: React.CSSProperties;
}

export const ChittiLogo: React.FC<ChittiLogoProps> = ({
  size = 24,
  className,
  style,
}) => {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 100 100"
      width={size}
      height={size}
      className={className}
      style={{
        display: "inline-block",
        flexShrink: 0,
        ...style,
      }}
    >
      <defs>
        <radialGradient id="chitti-f" cx="0.36" cy="0.3" r="0.82">
          <stop offset="0%" stopColor="#FFD86B" />
          <stop offset="55%" stopColor="#F0B31C" />
          <stop offset="100%" stopColor="#B9860A" />
        </radialGradient>
        <linearGradient id="chitti-b" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#1C1C1F" />
          <stop offset="100%" stopColor="#050508" />
        </linearGradient>
      </defs>
      <rect width="100" height="100" rx="23" fill="url(#chitti-b)" />
      <circle cx="50" cy="50" r="31" fill="url(#chitti-f)" />
      <g transform="translate(50 50) skewX(-8) translate(-50 -50)">
        <rect x="38.5" y="38" width="7.5" height="17" rx="3.75" fill="#050508" />
        <rect x="54" y="38" width="7.5" height="17" rx="3.75" fill="#050508" />
      </g>
      <path
        d="M27.4 41.8 A24 24 0 0 1 41.8 27.4"
        stroke="#FFFFFF"
        strokeOpacity="0.4"
        strokeWidth="2"
        fill="none"
        strokeLinecap="round"
      />
    </svg>
  );
};
