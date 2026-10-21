import React from "react";

/**
 * SF Symbols-style glyphs drawn on a 24pt grid with 1.8pt rounded strokes.
 * SF Symbols are licensed for Apple platforms, so these are original shapes
 * built to the same optical rules (see DESIGN.md).
 */

interface IconProps {
  size?: number;
  color?: string;
  strokeWidth?: number;
  filled?: boolean;
  style?: React.CSSProperties;
}

const Svg: React.FC<IconProps & { children: React.ReactNode; viewBox?: string }> = ({
  size = 16,
  color = "currentColor",
  strokeWidth = 1.8,
  filled = false,
  style,
  children,
  viewBox = "0 0 24 24",
}) => (
  <svg
    width={size}
    height={size}
    viewBox={viewBox}
    fill="none"
    aria-hidden="true"
    style={{ display: "block", flex: "none", ...style }}
  >
    <g
      stroke={filled ? "none" : color}
      fill={filled ? color : "none"}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {children}
    </g>
  </svg>
);

export const MusicNoteIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="8.4" cy="17.2" r="2.6" />
    <path d="M11 16.9V6.4l7.6-2.1v9.5" />
  </Svg>
);

export const TimerIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="13.2" r="7.6" />
    <path d="M12 9.2v4.2l2.8 1.7M9.6 2.8h4.8" />
  </Svg>
);

export const WaveformIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4 10.4v3.2M7.2 7.6v8.8M10.4 5.4v13.2M13.6 8.6v6.8M16.8 6.6v10.8M20 10.4v3.2" />
  </Svg>
);

export const BoltIcon: React.FC<IconProps> = (p) => (
  <Svg {...p} filled>
    <path d="M13.4 2.4 4.8 13.2h5.6l-1 8.4L19.2 10.6h-5.6z" />
  </Svg>
);

export const BellIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 3.8a5.2 5.2 0 0 0-5.2 5.2v3.3L4.6 15.4h14.8l-2.2-3.1V9A5.2 5.2 0 0 0 12 3.8z" />
    <path d="M10 18.4a2.1 2.1 0 0 0 4 0" />
  </Svg>
);

export const DownloadDocIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M6.2 4.4h7.2l4.4 4.4v9.2a1.6 1.6 0 0 1-1.6 1.6H6.2a1.6 1.6 0 0 1-1.6-1.6V6a1.6 1.6 0 0 1 1.6-1.6z" />
    <path d="M13.4 4.4v4.4h4.4M12 11v5.6M9.6 14.2 12 16.6l2.4-2.4" />
  </Svg>
);

export const UploadDocIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M6.2 4.4h7.2l4.4 4.4v9.2a1.6 1.6 0 0 1-1.6 1.6H6.2a1.6 1.6 0 0 1-1.6-1.6V6a1.6 1.6 0 0 1 1.6-1.6z" />
    <path d="M13.4 4.4v4.4h4.4M12 16.6V11M9.6 13.4 12 11l2.4 2.4" />
  </Svg>
);

export const LaptopIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M5.4 5.4h13.2a1.4 1.4 0 0 1 1.4 1.4v8H4V6.8a1.4 1.4 0 0 1 1.4-1.4z" />
    <path d="M2.8 17.2h18.4" />
  </Svg>
);

export const PlayIcon: React.FC<IconProps> = (p) => (
  <Svg {...p} filled>
    <path d="M8 4.8v14.4L19.6 12z" />
  </Svg>
);

export const PauseIcon: React.FC<IconProps> = (p) => (
  <Svg {...p} filled>
    <path d="M7.2 4.6h3.4v14.8H7.2zM13.4 4.6h3.4v14.8h-3.4z" />
  </Svg>
);

export const BackwardIcon: React.FC<IconProps> = (p) => (
  <Svg {...p} filled>
    <path d="M11.4 12 20 5.6v12.8zM4 5.6h2.4v12.8H4z" />
  </Svg>
);

export const ForwardIcon: React.FC<IconProps> = (p) => (
  <Svg {...p} filled>
    <path d="M12.6 12 4 18.4V5.6zM17.6 5.6H20v12.8h-2.4z" />
  </Svg>
);

export const CloseIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M6.2 6.2 17.8 17.8M17.8 6.2 6.2 17.8" strokeWidth={2} />
  </Svg>
);

export const CheckIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.6 12.6 9.6 17.6 19.6 7.2" />
  </Svg>
);

export const ChevronUpIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.8 15.2 12 8l7.2 7.2" />
  </Svg>
);

export const SpeakerIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.4 9.4h3.2L12 5.8v12.4l-4.4-3.6H4.4a.9.9 0 0 1-.9-.9v-3.4a.9.9 0 0 1 .9-.9z" />
    <path d="M15.2 9.2a4 4 0 0 1 0 5.6M17.8 6.6a7.6 7.6 0 0 1 0 10.8" />
  </Svg>
);

export const SpeakerMutedIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.4 9.4h3.2L12 5.8v12.4l-4.4-3.6H4.4a.9.9 0 0 1-.9-.9v-3.4a.9.9 0 0 1 .9-.9z" />
    <path d="M15.4 9.4l5.2 5.2M20.6 9.4l-5.2 5.2" />
  </Svg>
);

export const SparkleIcon: React.FC<IconProps> = (p) => (
  <Svg {...p} filled>
    <path d="M9.6 3.4l1.1 3 3 1.1-3 1.1-1.1 3-1.1-3-3-1.1 3-1.1z" />
    <path d="M17.2 11.6l.9 2.4 2.4.9-2.4.9-.9 2.4-.9-2.4-2.4-.9 2.4-.9z" />
  </Svg>
);
