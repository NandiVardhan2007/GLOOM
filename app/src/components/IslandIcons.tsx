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

export const SmartphoneIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="6.6" y="2.6" width="10.8" height="18.8" rx="2.4" />
    <path d="M10.4 5.6h3.2M11.2 18.4h1.6" />
  </Svg>
);

export const QrCodeIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="3.6" y="3.6" width="6" height="6" rx="1.1" />
    <rect x="14.4" y="3.6" width="6" height="6" rx="1.1" />
    <rect x="3.6" y="14.4" width="6" height="6" rx="1.1" />
    <path d="M14.4 14.4h2.2v2.2h-2.2zM18.2 18.2h2.2v2.2h-2.2zM14.4 20.4h1.4M20.4 14.4v1.4" />
  </Svg>
);

export const ShareIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M8.6 9.4H5.8a1.3 1.3 0 0 0-1.3 1.3v8a1.3 1.3 0 0 0 1.3 1.3h12.4a1.3 1.3 0 0 0 1.3-1.3v-8a1.3 1.3 0 0 0-1.3-1.3h-2.8" />
    <path d="M12 14.6V3.4M8.6 6.8 12 3.4l3.4 3.4" />
  </Svg>
);

export const CopyIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="8.4" y="8.4" width="11" height="11" rx="2.2" />
    <path d="M15.6 8.4V6.9a2.1 2.1 0 0 0-2.1-2.1H6.9a2.1 2.1 0 0 0-2.1 2.1v6.6a2.1 2.1 0 0 0 2.1 2.1h1.5" />
  </Svg>
);

export const FileTextIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M6.4 4.2h6.9l4.3 4.3v10a1.5 1.5 0 0 1-1.5 1.5H6.4a1.5 1.5 0 0 1-1.5-1.5V5.7a1.5 1.5 0 0 1 1.5-1.5z" />
    <path d="M13.1 4.2v4.3h4.3M8.2 12.2h6.4M8.2 15.4h6.4M8.2 18.6h3.8" />
  </Svg>
);

export const ClockIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12.4" r="7.8" />
    <path d="M12 8.2v4.5l2.9 1.8" />
  </Svg>
);

export const ShieldIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 3.4 5 6.2v5.2c0 4.2 2.9 7.4 7 9.2 4.1-1.8 7-5 7-9.2V6.2z" />
    <path d="M9.2 11.9 11.3 14l3.6-3.7" />
  </Svg>
);

export const FolderIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M3.8 7.2a1.6 1.6 0 0 1 1.6-1.6h4l2 2.2h7.2a1.6 1.6 0 0 1 1.6 1.6v9.4a1.6 1.6 0 0 1-1.6 1.6H5.4a1.6 1.6 0 0 1-1.6-1.6z" />
  </Svg>
);

export const GridIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="3.8" y="3.8" width="7" height="7" rx="1.6" />
    <rect x="13.2" y="3.8" width="7" height="7" rx="1.6" />
    <rect x="3.8" y="13.2" width="7" height="7" rx="1.6" />
    <rect x="13.2" y="13.2" width="7" height="7" rx="1.6" />
  </Svg>
);

export const SlidersIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4 7.2h16M4 12h16M4 16.8h16" />
    <circle cx="9" cy="7.2" r="1.9" />
    <circle cx="15.4" cy="12" r="1.9" />
    <circle cx="7.6" cy="16.8" r="1.9" />
  </Svg>
);

export const CoffeeIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M5.2 9.6h10.4v5.8a4.4 4.4 0 0 1-4.4 4.4h-1.6a4.4 4.4 0 0 1-4.4-4.4z" />
    <path d="M15.6 10.8h2a2.6 2.6 0 0 1 0 5.2h-2M7.8 6.6c.9-.8.9-1.6 0-2.4M11.6 6.6c.9-.8.9-1.6 0-2.4" />
  </Svg>
);

export const PinIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M9.2 3.8h5.6l-.8 5 3.2 3.4H6.8l3.2-3.4z" />
    <path d="M12 12.2v7.4" />
  </Svg>
);

export const PinOffIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M9.2 3.8h5.6l-.8 5 3.2 3.4H6.8l3.2-3.4z" />
    <path d="M12 12.2v7.4M4.6 4.4 19.4 19.6" />
  </Svg>
);

export const GearIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="3.1" />
    <path d="M12 3.2v2.4M12 18.4v2.4M3.2 12h2.4M18.4 12h2.4M5.8 5.8l1.7 1.7M16.5 16.5l1.7 1.7M18.2 5.8l-1.7 1.7M7.5 16.5l-1.7 1.7" />
  </Svg>
);

export const ActivityIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M3.2 12.6h3.4L9.2 6l3.4 12 2.4-5.4h5.8" />
  </Svg>
);

export const ChevronRightIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M9 4.8 16.2 12 9 19.2" />
  </Svg>
);

export const ChevronLeftIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M15 4.8 7.8 12 15 19.2" />
  </Svg>
);

export const ArrowDownIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 3.8v16.4M6.6 14.8 12 20.2l5.4-5.4" />
  </Svg>
);

export const ArrowUpIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 20.2V3.8M6.6 9.2 12 3.8l5.4 5.4" />
  </Svg>
);

export const CalculatorIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="4.4" y="2.8" width="15.2" height="18.4" rx="2.4" />
    <path d="M7.4 6.6h9.2v2.8H7.4zM8 13h.1M12 13h.1M16 13h.1M8 17h.1M12 17h.1M16 17h.1" />
  </Svg>
);

export const ScissorsIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="6.4" cy="7" r="2.6" />
    <circle cx="6.4" cy="17" r="2.6" />
    <path d="M8.6 8.6 19.4 17.8M8.6 15.4 19.4 6.2" />
  </Svg>
);

export const MicIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="9" y="2.8" width="6" height="11.2" rx="3" />
    <path d="M5.8 11.4a6.2 6.2 0 0 0 12.4 0M12 17.6v3.6M9.2 21.2h5.6" />
  </Svg>
);

export const SunIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="4.1" />
    <path d="M12 2.8v2.2M12 19v2.2M2.8 12H5M19 12h2.2M5.5 5.5l1.6 1.6M16.9 16.9l1.6 1.6M18.5 5.5l-1.6 1.6M7.1 16.9l-1.6 1.6" />
  </Svg>
);

export const WifiIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M3.4 9.4a13.2 13.2 0 0 1 17.2 0M6.2 12.8a9.2 9.2 0 0 1 11.6 0M9 16.2a5 5 0 0 1 6 0" />
    <circle cx="12" cy="19.4" r="1.2" />
  </Svg>
);

export const BluetoothIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M8.2 7.4 15.8 16.6 12 20.4V3.6l3.8 3.8L8.2 16.6" />
  </Svg>
);

export const BellOffIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 3.8a5.2 5.2 0 0 0-5.2 5.2v3.3L4.6 15.4h11.2M17.4 12.6V9A5.2 5.2 0 0 0 14 4.1" />
    <path d="M10 18.4a2.1 2.1 0 0 0 4 0M3.6 3.6l16.8 16.8" />
  </Svg>
);

export const LockIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="4.8" y="10.4" width="14.4" height="10.4" rx="2.2" />
    <path d="M8.2 10.4V7.8a3.8 3.8 0 0 1 7.6 0v2.6M12 14.4v2.4" />
  </Svg>
);

export const RotateCcwIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.4 5.6v5h5" />
    <path d="M4.9 10.4a7.6 7.6 0 1 1 1.8 6.2" />
  </Svg>
);

export const PlusIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 4.8v14.4M4.8 12h14.4" />
  </Svg>
);

export const MinusIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.8 12h14.4" />
  </Svg>
);

export const FlagIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M6.2 21V3.6M6.2 4.4h11.2l-2.1 4 2.1 4H6.2" />
  </Svg>
);

export const TrashIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.6 6.6h14.8M9.4 6.6V4.4h5.2v2.2M6.6 6.6l.9 12.8a1.8 1.8 0 0 0 1.8 1.6h5.4a1.8 1.8 0 0 0 1.8-1.6l.9-12.8" />
    <path d="M10.2 10.6v6.2M13.8 10.6v6.2" />
  </Svg>
);

export const CalendarIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="3.8" y="5" width="16.4" height="16" rx="2.2" />
    <path d="M3.8 9.6h16.4M8.2 2.8v4M15.8 2.8v4" />
  </Svg>
);

export const ListPlusIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.2 6.4h9.6M4.2 11.6h9.6M4.2 16.8h6.2M17.4 13.4v6.4M14.2 16.6h6.4" />
  </Svg>
);

export const GlobeIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="8.6" />
    <path d="M3.6 12h16.8M12 3.4c2.3 2.4 3.4 5.3 3.4 8.6S14.3 18.2 12 20.6C9.7 18.2 8.6 15.3 8.6 12S9.7 5.8 12 3.4z" />
  </Svg>
);

export const ExternalLinkIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M13.4 4.4h6.2v6.2M19.6 4.4l-8.4 8.4" />
    <path d="M18 14v4.6a1.6 1.6 0 0 1-1.6 1.6H5.8a1.6 1.6 0 0 1-1.6-1.6V8a1.6 1.6 0 0 1 1.6-1.6H10" />
  </Svg>
);

export const RefreshIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M19.6 5.4v5h-5" />
    <path d="M19.1 10.4a7.6 7.6 0 1 0-1.8 6.2" />
  </Svg>
);

export const PaletteIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 3.4a8.6 8.6 0 1 0 0 17.2c1.4 0 2.1-.9 2.1-2 0-1.4-1.1-2-1.1-3.1 0-1 .9-1.7 2.1-1.7h1.7a3.8 3.8 0 0 0 3.8-3.8c0-3.6-3.8-6.6-8.6-6.6z" />
    <circle cx="7.8" cy="11" r=".9" />
    <circle cx="10.6" cy="7.4" r=".9" />
    <circle cx="15.2" cy="7.8" r=".9" />
  </Svg>
);

export const BotIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="4.6" y="7.6" width="14.8" height="11.6" rx="3.2" />
    <path d="M12 3.4v4.2M9 20.2h6M9.2 12.2v1.6M14.8 12.2v1.6" />
    <circle cx="12" cy="3" r="1.2" />
  </Svg>
);

export const LightbulbIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M8.6 14.6a5.4 5.4 0 1 1 6.8 0c-.8.7-1.2 1.4-1.2 2.4H9.8c0-1-.4-1.7-1.2-2.4z" />
    <path d="M9.8 19.4h4.4M10.6 21.4h2.8" />
  </Svg>
);

export const InfoIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="8.6" />
    <path d="M12 10.8v5.4M12 7.6h.1" />
  </Svg>
);

export const MonitorIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="2.8" y="4.2" width="18.4" height="12.4" rx="1.8" />
    <path d="M8.6 20.2h6.8M12 16.6v3.6" />
  </Svg>
);

export const MaximizeIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M4.2 9V4.2H9M15 4.2h4.8V9M19.8 15v4.8H15M9 19.8H4.2V15" />
  </Svg>
);

export const CloudSunIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M8.4 7.2a3.2 3.2 0 0 1 5.9 1.4M6.2 4.4 5.4 3M3.4 7.2 2 6.8M8.4 3.4 8 1.8" />
    <path d="M8.6 18.6a3.4 3.4 0 0 1-.4-6.8 4.6 4.6 0 0 1 8.8 1 3.1 3.1 0 0 1-.6 5.8z" />
  </Svg>
);

export const DownloadIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 3.6v11.4M7.4 10.8 12 15.4l4.6-4.6M4.4 18.4v1.4a1.2 1.2 0 0 0 1.2 1.2h12.8a1.2 1.2 0 0 0 1.2-1.2v-1.4" />
  </Svg>
);

export const ClipboardListIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="5.2" y="4.6" width="13.6" height="16.6" rx="2.2" />
    <path d="M9 4.6V3.2h6v1.4M8.6 10.4h6.8M8.6 14h6.8M8.6 17.6h4.2" />
  </Svg>
);

export const PowerIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <path d="M12 3.2v8.6" />
    <path d="M7.4 6.2a7.4 7.4 0 1 0 9.2 0" />
  </Svg>
);

export const CpuIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="6.4" y="6.4" width="11.2" height="11.2" rx="1.8" />
    <rect x="10" y="10" width="4" height="4" rx=".8" />
    <path d="M9.4 3.2v3.2M14.6 3.2v3.2M9.4 17.6v3.2M14.6 17.6v3.2M3.2 9.4h3.2M3.2 14.6h3.2M17.6 9.4h3.2M17.6 14.6h3.2" />
  </Svg>
);

export const HardDriveIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="3.2" y="5" width="17.6" height="14" rx="2.4" />
    <path d="M3.6 12.6h16.8M16.8 15.6h.1" />
  </Svg>
);

export const BatteryIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="2.8" y="7.4" width="16.4" height="9.2" rx="2.4" />
    <path d="M21.2 10.6v2.8M5.6 10.2v3.6M8.4 10.2v3.6" />
  </Svg>
);

export const BatteryChargingIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="2.8" y="7.4" width="16.4" height="9.2" rx="2.4" />
    <path d="M21.2 10.6v2.8M11.6 8.6 8.4 12.2h3l-.8 3.2 3.2-3.8h-3z" />
  </Svg>
);

export const DatabaseIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <ellipse cx="12" cy="6" rx="7.4" ry="2.8" />
    <path d="M4.6 6v12c0 1.5 3.3 2.8 7.4 2.8s7.4-1.3 7.4-2.8V6M4.6 12c0 1.5 3.3 2.8 7.4 2.8s7.4-1.3 7.4-2.8" />
  </Svg>
);

export const ClipboardIcon: React.FC<IconProps> = (p) => (
  <Svg {...p}>
    <rect x="5.4" y="4.8" width="13.2" height="16.4" rx="2.2" />
    <path d="M9.2 4.8V3.4h5.6v1.4M8.8 10.4h6.4M8.8 14h6.4M8.8 17.6h4" />
  </Svg>
);



