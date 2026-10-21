export type IslandMode = "island" | "notch" | "bar";

export type IslandState = "closed" | "peek" | "expanded";

export type ActiveTab =
  | "dashboard"
  | "media"
  | "bridge"
  | "controls"
  | "bluetooth"
  | "notes"
  | "timer"
  | "system"
  | "network"
  | "clipboard"
  | "settings";

export interface BridgeDevice {
  id: string;
  name: string;
  deviceType: "android" | "ios" | "harmony";
  ip: string;
  port: number;
  batteryPercent: number;
  isCharging: boolean;
  wifiSsid: string;
  status: "connected" | "syncing" | "idle";
  lastSeenMs: number;
}

export interface BridgeActivity {
  id: string;
  appName: string;
  title: string;
  subtitle: string;
  icon: string;
  activityType: "timer" | "delivery" | "call" | "otp" | "media";
  progress?: number;
  timestampMs: number;
}

export interface BridgeTransfer {
  id: string;
  deviceName: string;
  fileName: string;
  fileSize: number;
  bytesTransferred: number;
  isUpload: boolean;
  progress: number;
  status: "transferring" | "completed" | "failed";
}

export type ConnectionState = "disconnected" | "discovering" | "pairing_requested" | "connected" | "reconnecting";

export interface ConnectionStatusPayload {
  state: ConnectionState;
  pairedDeviceId: string | null;
  pairedDeviceName: string | null;
  pingMs: number;
  lastHeartbeatMs: number;
}

export interface MediaState {
  playing: boolean;
  title: string;
  artist: string;
  album: string;
  positionMs: number;
  durationMs: number;
  thumbnail: string | null;
}

export interface SystemTelemetry {
  cpuPercent: number;
  ramUsedMb: number;
  ramTotalMb: number;
  batteryPercent: number | null;
  isCharging: boolean;
  hasBattery: boolean;
  diskFreeGb?: number;
  diskTotalGb?: number;
  netDownloadKbps?: number;
  netUploadKbps?: number;
}

export interface TimerState {
  mode: "pomodoro" | "stopwatch";
  isRunning: boolean;
  remainingSeconds: number;
  totalSeconds: number;
  elapsedMs: number;
  formattedTime: string;
}

export interface VolumeState {
  volume: number; // 0.0 to 1.0
  isMuted: boolean;
}

export interface ClipboardItem {
  id: number;
  text: string;
  preview: string;
  timestampMs: number;
}

export interface AppConfig {
  isPro?: boolean;
  // Geometry & Display
  mode: IslandMode;
  topOffset: number;
  islandWidth: number;
  islandHeight: number;
  expandedWidth: number;
  expandedHeight: number;
  islandScale: number;
  pillEdgeGap: number;
  clockFormat: "12h" | "24h";
  selectedMonitor: string;
  screenPosition: "top" | "left" | "right";

  // Behavior & Gestures
  autostart: boolean;
  hoverToShow: boolean;
  expandOnHover: boolean;
  avoidFullscreen: boolean;
  hideBehindMaximized: boolean;
  pinMode: boolean;
  lockDrag: boolean;

  // Appearance & Style
  theme: string;
  accentColor: string;
  bgColor: string;
  idleColor: string;
  cornerShape: "rounded" | "squared";
  panelStyle: "glass" | "dark_glass" | "solid";
  cardStyle: "light" | "dark" | "darker";
  glowIntensity: "off" | "subtle" | "strong";
  cycleColors: boolean;
  customBgUrl: string;
  bgAnimation: string;

  // Widgets visibility
  showMediaWidget: boolean;
  showAudioWaveform: boolean;
  showVolumeMixer: boolean;
  showAgentUsage: boolean;
  showAgentNotifications: boolean;
  showDropShelf: boolean;
  showClipboardHistory: boolean;
  showDownloads: boolean;
  showHardwareWidget: boolean;
  showNetworkWidget: boolean;
  showWeatherWidget: boolean;
  showPomodoro: boolean;
  showQuickTools: boolean;
  volumeHudEnabled: boolean;
  clipboardHistoryEnabled: boolean;
  reducedMotion: boolean;

  // Notchy / AI Companion
  notchyCompactIndicator: boolean;
  notchyQuietMode: boolean;
  notchyGlowStrength: "off" | "subtle" | "full";
  notchyCompletionDuration: number;
  notchyPreferredEditor: "auto" | "antigravity" | "code" | "cursor" | "explorer";
  notchyModel: string;
}

export interface HitRect {
  x: number;
  y: number;
  width: number;
  height: number;
}
