import { motion, AnimatePresence, useAnimation } from "framer-motion";
import { useEffect, useLayoutEffect, useState, useCallback, useMemo, useRef, memo } from "react";
import { listen } from "@tauri-apps/api/event";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import { getVersion } from "@tauri-apps/api/app";
import type { UpdateCheckResult } from "./updater";
import { batteryTooltip, type BatteryDetails } from "./batteryTooltip";
import {
	createRegionSender,
	measureElements,
	notchPillBox,
	type RegionRect
} from "./windowRegion";
import "./App.css";
import { initTheme } from "./theme";
import {
	VolumeLowIcon,
	MusicNoteIcon
} from "./icons";
import { CompactMediaPlayer } from "./CompactMediaPlayer";
import { extractLiveLyricFromApp } from "./hooks/useLyrics";
import { playTactileSound } from "./utils/audioFeedback.ts";
import { useWeather } from "./hooks/useWeather";
import { useSettingsSync } from "./hooks/useSettingsSync";
import { useTrailingThrottle } from "./hooks/useTrailingThrottle";
import { useAnnouncement } from "./hooks/useAnnouncement";
import { reloadIfMirrorWasStale } from "./hooks/settingsMirror";
import { openUrl } from "@tauri-apps/plugin-opener";
import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import { startDrag } from "@crabnebula/tauri-plugin-drag";
import { NotchFlares } from "./components/NotchFlares";
import type { WidgetConfig } from "./components/StatusWidgetConfig";
import { ProductivityDrawer } from "./components/cards/ProductivityDrawer";
import { LiveActivityBanner, type LiveActivityItem } from "./components/cards/LiveActivityBanner";
import { SurfaceShape } from "./components/SurfaceShape";
import { NotchProgressBorder } from "./components/NotchProgressBorder";
import { NotchTour } from "./components/NotchTour";
import { IslandNavBar, type IslandTabId } from "./components/IslandNavBar";
import { SystemMonitorCard } from "./components/cards/SystemMonitorCard";
import { NotchClock } from "./components/NotchClock";
import { useStopwatch } from "./hooks/useStopwatch";
import { ConnectionsView } from "./components/ConnectionsView";
import {
	Cpu,
	MemoryStick,
	HardDrive,
	ArrowUpDown,
	BellRing,
	Megaphone,
	Type,
	X,
	ChevronLeft,
	ChevronRight,
	Inbox,
	Music,
	Folder,
	Lock,
	Download
} from "lucide-react";

/**
 * The notch has two kinds of content:
 * - Panels are tools the user chooses. A click opens the last-used panel by
 *   default, or a selected fixed panel when panel memory is turned off.
 * - Live activities (music, a set timer, a filled shelf) show a compact form in
 *   the pill or a bubble beside it, and opening one shows its own view, which
 *   stands alone (no panel arrows) and is never remembered as the last panel.
 *   Dragging files onto the notch also opens the shelf. Wheel/swipe switches
 *   between tool panels, including when a live activity occupies the pill.
 */
type NotchPanel = "calendar" | "command-center";
const NOTCH_PANELS: readonly NotchPanel[] = ["calendar", "command-center"];
const asNotchPanel = (value: unknown): NotchPanel | null =>
	NOTCH_PANELS.includes(value as NotchPanel) ? (value as NotchPanel) : null;

// Completion chime for the Pomodoro timer. Synthesized with the Web Audio API
// so no audio asset is needed; created on the Start click so the webview's
// autoplay policy lets it play when the timer ends.
let timerChimeCtx: AudioContext | null = null;

const getTimerChimeCtx = (): AudioContext | null => {
	try {
		if (!timerChimeCtx) timerChimeCtx = new AudioContext();
		if (timerChimeCtx.state === "suspended") timerChimeCtx.resume().catch(() => {});
		return timerChimeCtx;
	} catch {
		return null;
	}
};

const playTimerChime = () => {
	const ctx = getTimerChimeCtx();
	if (!ctx) return;
	const start = ctx.currentTime + 0.02;
	const master = ctx.createGain();
	master.gain.value = 0.45;
	master.connect(ctx.destination);

	// Soft rising bell arpeggio (A5–C#6–E6) with a quiet octave harmonic.
	const notes = [
		{ freq: 880.0, at: 0 },
		{ freq: 1108.73, at: 0.18 },
		{ freq: 1318.51, at: 0.36 }
	];
	notes.forEach(({ freq, at }) => {
		const osc = ctx.createOscillator();
		const harmonic = ctx.createOscillator();
		const gain = ctx.createGain();
		const harmonicGain = ctx.createGain();
		osc.type = "sine";
		osc.frequency.value = freq;
		harmonic.type = "sine";
		harmonic.frequency.value = freq * 2.01;
		harmonicGain.gain.value = 0.12;
		gain.gain.setValueAtTime(0.0001, start + at);
		gain.gain.exponentialRampToValueAtTime(0.32, start + at + 0.02);
		gain.gain.exponentialRampToValueAtTime(0.0001, start + at + 1.4);
		osc.connect(gain);
		harmonic.connect(harmonicGain);
		harmonicGain.connect(gain);
		gain.connect(master);
		osc.start(start + at);
		harmonic.start(start + at);
		osc.stop(start + at + 1.5);
		harmonic.stop(start + at + 1.5);
	});
};

// Simple SVG icons
function WifiIcon({ enabled, connected }: { enabled: boolean; connected: boolean }) {
	return (
		<svg
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
			opacity={!enabled ? 0.4 : connected ? 1 : 0.7}
		>
			<path d="M5 12.55a11 11 0 0 1 14.08 0" />
			<path d="M1.42 9a16 16 0 0 1 21.16 0" />
			<path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
			<line x1="12" y1="20" x2="12.01" y2="20" />
		</svg>
	);
}

function TrayIcon() {
	return (
		<svg
			width="14"
			height="14"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<rect x="3" y="3" width="6" height="6" rx="1" />
			<rect x="15" y="3" width="6" height="6" rx="1" />
			<rect x="15" y="15" width="6" height="6" rx="1" />
			<rect x="3" y="15" width="6" height="6" rx="1" />
		</svg>
	);
}

function BatteryIcon({
	charging,
	level,
	threshold = 20
}: {
	charging: boolean;
	level: number;
	threshold?: number;
}) {
	const percentage = Math.min(Math.max(level, 0), 100);

	return (
		<div
			style={{
				display: "flex",
				alignItems: "center",
				position: "relative",
				height: "14px",
				justifyContent: "center"
			}}
		>
			<svg width="20" height="10" viewBox="0 0 20 10" fill="none">
				{/* Battery Shell - Centered at 9px within 20px width, ignoring the tip's offset */}
				<rect
					x="2"
					y="0.75"
					width="14"
					height="8.5"
					rx="2.4"
					stroke="currentColor"
					strokeOpacity={0.35}
					strokeWidth="1.1"
				/>
				{/* Battery Tip */}
				<path
					d="M17.5 3.5V6.5"
					stroke="currentColor"
					strokeOpacity={0.35}
					strokeWidth="1.2"
					strokeLinecap="round"
				/>
				{/* Fill */}
				<rect
					x="3.8"
					y="2.5"
					width={Math.max(0.5, (percentage / 100) * 10.4)}
					height="5"
					rx="1"
					fill={charging ? "#32D74B" : percentage <= threshold ? "#FF453A" : "white"}
				/>
			</svg>
			{/* Charging Bolt - Centered on the battery body */}
			{charging && (
				<div
					style={{
						position: "absolute",
						top: "50%",
						left: "9px",
						transform: "translate(-50%, -50%)",
						color: "white",
						filter: "drop-shadow(0px 0px 1.5px rgba(0,0,0,0.8))"
					}}
				>
					<svg width="7" height="10" viewBox="0 0 8 12" fill="currentColor">
						<path d="M4.5 0L0 7H3.5L2.5 12L8 5H4.5L5.5 0H4.5Z" />
					</svg>
				</div>
			)}
		</div>
	);
}

function GreenDownArrowIcon() {
	return (
		<div
			style={{
				display: "inline-flex",
				alignItems: "center",
				justifyContent: "center",
				width: "18px",
				height: "18px",
				borderRadius: "50%",
				background: "rgba(50, 215, 75, 0.15)",
				border: "1px solid rgba(50, 215, 75, 0.35)",
				boxShadow: "0 0 8px rgba(50, 215, 75, 0.25)",
				flexShrink: 0,
				verticalAlign: "middle"
			}}
		>
			<svg
				width="10"
				height="10"
				viewBox="0 0 24 24"
				fill="none"
				stroke="#32D74B"
				strokeWidth="3"
				strokeLinecap="round"
				strokeLinejoin="round"
			>
				<line x1="12" y1="4" x2="12" y2="16"></line>
				<polyline points="18 10 12 16 6 10"></polyline>
				<line x1="6" y1="20" x2="18" y2="20"></line>
			</svg>
		</div>
	);
}

export type VisualizerMode = "bars" | "wave" | "halo";

export const Visualizer = memo(function Visualizer({
	isPlaying,
	bars = 5,
	height = 20,
	mode: controlledMode,
	onModeChange
}: {
	isPlaying: boolean;
	bars?: number;
	height?: number;
	mode?: VisualizerMode;
	onModeChange?: (mode: VisualizerMode) => void;
}) {
	const [localMode, setLocalMode] = useState<VisualizerMode>(() => {
		if (typeof localStorage !== "undefined") {
			const saved = localStorage.getItem("gloom-visualizer-mode");
			if (saved === "wave" || saved === "halo") return saved;
		}
		return "bars";
	});
	const activeMode = controlledMode ?? localMode;

	const cycleMode = (e: React.MouseEvent) => {
		e.stopPropagation();
		playTactileSound("switch");
		const next: VisualizerMode = activeMode === "bars" ? "wave" : activeMode === "wave" ? "halo" : "bars";
		setLocalMode(next);
		if (typeof localStorage !== "undefined") {
			localStorage.setItem("gloom-visualizer-mode", next);
		}
		if (onModeChange) onModeChange(next);
	};

	const [audioData, setAudioData] = useState<number[]>(new Array(bars).fill(0.18));

	useEffect(() => {
		if (!isPlaying) {
			setAudioData(new Array(bars).fill(0.18));
			return;
		}

		const unlisten = listen<{ frequencies: number[] }>("audio-visualization", (event) => {
			let data = event.payload.frequencies;
			if (data.length > bars) data = data.slice(0, bars);
			while (data.length < bars) data.push(0.18);
			setAudioData(data);
		});

		return () => {
			unlisten.then((fn) => fn());
		};
	}, [isPlaying, bars]);

	const avgAmplitude = audioData.reduce((acc, v) => acc + v, 0) / (audioData.length || 1);

	return (
		<div
			className="visualizer-interactive-wrapper"
			onClick={cycleMode}
			title={`Visualizer: ${activeMode} (click to switch)`}
			style={{ cursor: "pointer", display: "inline-flex", alignItems: "center", justifyContent: "center" }}
		>
			{activeMode === "bars" && (
				<div
					className="visualizer-horizontal"
					style={{ height: `${height}px`, width: `${bars * 6}px` }}
				>
					{audioData.map((value, i) => (
						<motion.div
							key={i}
							className="bar-horizontal"
							animate={{
								scaleY: isPlaying ? Math.max(0.2, value) : 0.1,
								opacity: isPlaying ? 0.95 : 0.5
							}}
							transition={{
								type: "spring",
								stiffness: 600,
								damping: 30,
								mass: 0.5
							}}
						/>
					))}
				</div>
			)}

			{activeMode === "wave" && (
				<svg
					width={bars * 6.5}
					height={height}
					viewBox="0 0 28 16"
					style={{ overflow: "visible" }}
				>
					<defs>
						<linearGradient id="wave-phosphor-grad" x1="0" y1="0" x2="1" y2="0">
							<stop offset="0%" stopColor="#30D158" />
							<stop offset="100%" stopColor="#34C759" />
						</linearGradient>
					</defs>
					<motion.path
						d={
							isPlaying
								? `M 0 8 Q 7 ${8 - avgAmplitude * 10} 14 8 T 28 8`
								: "M 0 8 L 28 8"
						}
						fill="none"
						stroke="url(#wave-phosphor-grad)"
						strokeWidth="2.2"
						strokeLinecap="round"
						animate={{
							d: isPlaying
								? [
										`M 0 8 Q 7 ${8 - avgAmplitude * 12} 14 8 T 28 8`,
										`M 0 8 Q 7 ${8 + avgAmplitude * 12} 14 8 T 28 8`,
										`M 0 8 Q 7 ${8 - avgAmplitude * 12} 14 8 T 28 8`
									]
								: "M 0 8 L 28 8"
						}}
						transition={{ repeat: Infinity, duration: 0.45, ease: "easeInOut" }}
						style={{ filter: "drop-shadow(0 0 4px rgba(48, 209, 88, 0.6))" }}
					/>
				</svg>
			)}

			{activeMode === "halo" && (
				<div
					style={{
						width: `${height}px`,
						height: `${height}px`,
						position: "relative",
						display: "flex",
						alignItems: "center",
						justifyContent: "center"
					}}
				>
					<motion.div
						style={{
							position: "absolute",
							inset: 0,
							borderRadius: "50%",
							border: "1.5px solid #30D158",
							boxShadow: "0 0 8px rgba(48, 209, 88, 0.7)"
						}}
						animate={{
							scale: isPlaying ? [1, 1 + avgAmplitude * 0.4, 1] : 1,
							opacity: isPlaying ? [0.6, 1, 0.6] : 0.3
						}}
						transition={{ repeat: Infinity, duration: 0.5, ease: "easeInOut" }}
					/>
					<div
						style={{
							width: 4,
							height: 4,
							borderRadius: "50%",
							background: "#30D158",
							boxShadow: "0 0 4px #30D158"
						}}
					/>
				</div>
			)}
		</div>
	);
});

// Number of week rows the current month occupies. Calendar mode sizes the
// notch to the month so five-row months stay compact and six-row months never
// clip the last row.
const calendarMonthRows = (() => {
	const now = new Date();
	const firstWeekday = new Date(now.getFullYear(), now.getMonth(), 1).getDay();
	const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
	return Math.ceil((firstWeekday + daysInMonth) / 7);
})();

interface MediaInfo {
	title: string;
	artist: string;
	subtitle?: string;
	source_app?: string;
	is_playing: boolean;
	has_media: boolean;
	artwork?: string[];
	position_ms?: number;
	duration_ms?: number;
	seek_enabled?: boolean;
	position_updated_at?: number;
}

interface WifiStatus {
	enabled: boolean;
	connected: boolean;
	ssid: string | null;
}

// A shelf file's preview from the backend: a picture when one can be made,
// else the opening text of a document or text file, and whether it's a folder
// (for the placeholder icon).
interface ShelfPreview {
	thumb: string | null;
	text: string | null;
	folder: boolean;
}

interface ShelfItem {
	id: string;
	type: "file" | "text";
	name: string;
	path?: string;
	text?: string;
	timestamp: number;
}

type GloomMode =
	| "music"
	| "calendar"
	| "command-center"
	| "announcement"
	| "status"
	| "shelf"
	| "wifi"
	| "bluetooth";

// The shelf is a short-lived stash: one row, newest first, oldest evicted.
const SHELF_MAX_ITEMS = 10;


const isLinux = typeof navigator !== "undefined" && navigator.userAgent.toLowerCase().includes("linux");

function App() {
	useEffect(() => {
		return initTheme();
	}, []);

	const [time, setTime] = useState("");
	const [isHovered, setIsHovered] = useState(false);
	// Expanded into a panel by a click. Hover alone only peeks.
	const [isOpen, setIsOpen] = useState(false);
	const isOpenRef = useRef(false);
	isOpenRef.current = isOpen;
	const [isReady, setIsReady] = useState(false);
	const [scale, setScale] = useState(() =>
		parseFloat(localStorage.getItem("gloom-scale") || "1.0")
	);
	const [timeFormat24h, setTimeFormat24h] = useState(
		() => localStorage.getItem("gloom-time-format-24h") === "true"
	);

	const [batteryLevel, setBatteryLevel] = useState(100);
	const [isCharging, setIsCharging] = useState(false);
	// Fetched when the pointer reaches the battery widget, for its tooltip.
	const [batteryDetails, setBatteryDetails] = useState<BatteryDetails | null>(null);
	// The status widget whose tooltip is open, the side of the pill it hangs
	// from, and the distance from the widget to just past that edge.
	const [statusTooltip, setStatusTooltip] = useState<{
		id: string;
		side: "below" | "left" | "right";
		offset: number;
	} | null>(null);
	const statusTooltipTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	// Width the hovered status strip needs so neither side runs into the clock.
	const [statusFitWidth, setStatusFitWidth] = useState(0);
	const [showPowerPulse, setShowPowerPulse] = useState(false);
	const [showLowBatteryPulse, setShowLowBatteryPulse] = useState(false);
	const [lowBatteryThreshold, setLowBatteryThreshold] = useState(() =>
		parseInt(localStorage.getItem("gloom-low-battery-threshold") || "20")
	);
	const [keyLockNotice, setKeyLockNotice] = useState<{ key: string; enabled: boolean } | null>(null);
	const keyLockTimeoutRef = useRef<any>(null);
	const [showChargingBeams, setShowChargingBeams] = useState(false);
	const prevChargingRef = useRef<boolean | null>(null);
	const powerPulseTimeoutRef = useRef<any>(null);
	const lowBatteryPulseShownRef = useRef<boolean>(false);

	const [eventPeek, setEventPeek] = useState(false);
	const eventPeekTimeoutRef = useRef<any>(null);
	const updatePulseTimerRef = useRef<any>(null);
	const triggerEventPeek = useCallback((duration = 3000) => {
		setEventPeek(true);
		if (eventPeekTimeoutRef.current) clearTimeout(eventPeekTimeoutRef.current);
		eventPeekTimeoutRef.current = setTimeout(() => setEventPeek(false), duration);
	}, []);

	const [notchMode, setNotchMode] = useState(() => {
		const raw = localStorage.getItem("gloom-notch-mode");
		if (!raw) return "fixed";
		if (raw === "auto-hide") return "smart";
		return raw;
	});

	const [avoidFullscreenGames, setAvoidFullscreenGames] = useState(() => {
		return localStorage.getItem("gloom-avoid-fullscreen-games") !== "false";
	});
	const [isFullscreenGame, setIsFullscreenGame] = useState(false);

	const [islandStyle, setIslandStyle] = useState<"notch" | "pill">(() => {
		return (localStorage.getItem("gloom-island-style") as "notch" | "pill") || "notch";
	});

	useEffect(() => {
		if (isReady && prevChargingRef.current !== null && prevChargingRef.current !== isCharging) {
			setShowPowerPulse(true);
			if (isCharging) {
				setShowChargingBeams(true);
				setTimeout(() => setShowChargingBeams(false), 3200);
			}
			if (notchMode === "peek") triggerEventPeek(4000);
			if (powerPulseTimeoutRef.current) clearTimeout(powerPulseTimeoutRef.current);
			powerPulseTimeoutRef.current = setTimeout(() => {
				setShowPowerPulse(false);
			}, 4000);
		}
		prevChargingRef.current = isCharging;
	}, [isCharging, isReady, notchMode, triggerEventPeek]);

	useEffect(() => {
		// Trigger pulse when dropping below threshold while discharging
		if (
			isReady &&
			batteryLevel <= lowBatteryThreshold &&
			!isCharging &&
			!lowBatteryPulseShownRef.current
		) {
			setShowLowBatteryPulse(true);
			if (notchMode === "peek") triggerEventPeek(5000);
			lowBatteryPulseShownRef.current = true;
			setTimeout(() => setShowLowBatteryPulse(false), 5000);
		}

		// Reset the "shown" state if battery is charged or threshold is lowered
		if (isCharging || batteryLevel > lowBatteryThreshold) {
			lowBatteryPulseShownRef.current = false;
		}
	}, [batteryLevel, isCharging, lowBatteryThreshold, isReady, notchMode, triggerEventPeek]);

	// Weather state (managed by useWeather hook)

	// Media state
	const [isPlaying, setIsPlaying] = useState(false);
	const [mediaInfo, setMediaInfo] = useState<MediaInfo>({
		title: "",
		artist: "",
		is_playing: false,
		has_media: false
	});
	const [albumArtUrl, setAlbumArtUrl] = useState<string | null>(null);
	const [albumArtKey, setAlbumArtKey] = useState(0);
	const [volume, setVolume] = useState(0.5);
	const [isMuted, setIsMuted] = useState(false);
	// Sliders show 0 while muted, matching the overlay HUD.
	const displayVolume = isMuted ? 0 : volume;
	// Null until the first successful fetch so the pill can't flash a wrong
	// default on startup.
	const [wifiStatus, setWifiStatus] = useState<WifiStatus | null>(null);
	const wifiEnabled = wifiStatus?.enabled ?? false;
	const wifiConnected = wifiStatus?.connected ?? false;
	const [bluetoothEnabled, setBluetoothEnabled] = useState(true);
	// Names of connected Bluetooth devices, for the controls tile.
	const [btConnectedNames, setBtConnectedNames] = useState<string[]>([]);
	const [batterySaverEnabled, setBatterySaverEnabled] = useState(false);
	const [currentBrightness, setCurrentBrightness] = useState(50);

	// System metrics for status widgets
	const [cpuUsage, setCpuUsage] = useState(0);
	const [ramUsage, setRamUsage] = useState(0);
	const [diskSpace, setDiskSpace] = useState(0);
	const [netUpSpeed, setNetUpSpeed] = useState(0);
	const [netDownSpeed, setNetDownSpeed] = useState(0);
	const [statusWidgets, setStatusWidgets] = useState<WidgetConfig>({
		left: ["weather"],
		right: ["battery"]
	});

	const [windowLabel, setWindowLabel] = useState<string>("");
	useEffect(() => {
		setWindowLabel(getCurrentWebviewWindow().label);
	}, []);

	// Update state
	const [updateAvailable, setUpdateAvailable] = useState(false);
	const [showUpdateIndicator, setShowUpdateIndicator] = useState(
		() => localStorage.getItem("gloom-show-update-indicator") !== "false"
	);
	const [showUpdatePulse, setShowUpdatePulse] = useState(false);

	useEffect(() => {
		if (windowLabel !== "main") return;

		let unlisten: (() => void) | undefined;
		let disposed = false;

		listen<UpdateCheckResult>("update-available", (event) => {
			setUpdateAvailable(event.payload.available);
			if (!event.payload.available) {
				setShowUpdatePulse(false);
				if (updatePulseTimerRef.current) clearTimeout(updatePulseTimerRef.current);
				return;
			}
			setShowUpdatePulse(true);
			if (notchMode === "peek") triggerEventPeek(6000);
			if (updatePulseTimerRef.current) clearTimeout(updatePulseTimerRef.current);
			updatePulseTimerRef.current = setTimeout(() => {
				setShowUpdatePulse(false);
			}, 6000);
		}).then((fn) => {
			if (disposed) fn();
			else unlisten = fn;
		});

		invoke<UpdateCheckResult>("get_update_state")
			.then((state) => {
				// Only restore a badge; clearing is left to check results so a
				// late mount read cannot race an "update-available" event.
				if (state.available) setUpdateAvailable(true);
			})
			.catch((e) => console.error("Failed to read update state:", e));

		return () => {
			disposed = true;
			unlisten?.();
			if (updatePulseTimerRef.current) clearTimeout(updatePulseTimerRef.current);
		};
	}, [windowLabel, notchMode, triggerEventPeek]);

	// Remote announcement shown as a persistent notch card until dismissed.
	const {
		announcement,
		dismissed: announcementDismissed,
		dismiss: dismissAnnouncement
	} = useAnnouncement();
	const announcementShownRef = useRef<string | null>(null);
	const announcementOpen = !!announcement && !announcementDismissed;
	const announcementOpenRef = useRef(false);
	announcementOpenRef.current = announcementOpen;

	const [isVisible, setIsVisible] = useState(true);
	const [isImpacted, setIsImpacted] = useState(false);
	const [isExpanded, setIsExpanded] = useState(false);
	const startupAnimating = false;

	const [dockMode, setDockMode] = useState(() => {
		const raw = localStorage.getItem("gloom-dock-mode") || "smart";
		if (raw === "auto-hide") return "smart";
		return raw;
	});
	const [dndActive, setDndActive] = useState(false);
	const [dockEnabled, setDockEnabled] = useState(
		() => localStorage.getItem("gloom-dock-enabled") !== "false"
	);
	const [isNotchHovered, setIsNotchHovered] = useState(false);

	const [isEdgeHovered, setIsEdgeHovered] = useState(false);
	const [isOverlapped, setIsOverlapped] = useState(false);
	const [interactionState, setInteractionState] = useState<"active" | "grace" | "none">("none");
	const gloomRef = useRef<HTMLDivElement>(null);
	// Measured to fit the hovered status strip; see statusFitWidth.
	const leftWidgetsRef = useRef<HTMLDivElement>(null);
	const rightWidgetsRef = useRef<HTMLDivElement>(null);
	const timeCenterRef = useRef<HTMLDivElement>(null);
	const musicBubbleRef = useRef<HTMLButtonElement>(null);
	const shelfBubbleRef = useRef<HTMLButtonElement>(null);
	const downloadBubbleRef = useRef<HTMLButtonElement>(null);
	const [liveActivities, setLiveActivities] = useState<LiveActivityItem[]>([]);
	const reportNotchRect = useRef<() => void>(() => {});
	// The pill's target box in window CSS px, and the sender that applies the
	// notch window's region (holding earlier regions while it shrinks).
	const pillTargetRef = useRef<RegionRect | null>(null);
	const sendNotchRegion = useMemo(
		() =>
			createRegionSender((rects) => {
				invoke("set_window_region", { rects }).catch(() => {});
			}),
		[]
	);

	// Recomputes the notch window's region from the pill's target box and the
	// floating parts drawn around it, and sends it if it changed. Reads only
	// refs and the DOM, so it is safe to call from any callback.
	const updateNotchRegion = () => {
		const wanted: RegionRect[] = [];
		if (pillTargetRef.current) wanted.push(pillTargetRef.current);
		wanted.push(
			...measureElements([
				[".activity-bubble", 16],
				[".status-tooltip", 28],
				[".notch-panel-arrow", 16],
				[".expanded-music-container", 24],
				[".apple-karaoke-sheet", 24],
				[".control-center-panel", 24],
				[".shelf-panel", 24],
				[".productivity-drawer-container", 24],
				[".live-activity-capsule", 20],
				[".calendar-panel", 24],
				[".timer-panel", 24]
			])
		);
		// Always keep the top-edge peek sensor region active so hovering the top edge reveals the notch/pill
		const sensorWidth = Math.max(380, (pillTargetWidth + 100) * scale);
		wanted.push({
			x: Math.round((window.innerWidth - sensorWidth) / 2),
			y: 0,
			width: Math.round(sensorWidth),
			height: Math.round(16 * scale)
		});
		sendNotchRegion(wanted);
	};
	const [systemSettingsReady, setSystemSettingsReady] = useState(false);
	const [waitForStartupSplash, setWaitForStartupSplash] = useState(false);
	const initialSystemSyncDone = useRef(false);
	const splashDone = useRef(false);
	const systemSyncQueue = useRef<Promise<void>>(Promise.resolve());
	const appliedDock = useRef<{ enabled: boolean; mode: string } | null>(null);
	const appliedNotchMode = useRef<string | null>(null);

	const isAnyInteraction = isHovered || isNotchHovered || isEdgeHovered;
	const isHidden =
		!startupAnimating &&
		((avoidFullscreenGames && isFullscreenGame && interactionState === "none") ||
			(notchMode === "smart" && isOverlapped && interactionState === "none") ||
			(notchMode === "peek" && interactionState === "none" && !eventPeek));

	useEffect(() => {
		if (isAnyInteraction) {
			setInteractionState("active");
		} else if (interactionState !== "none") {
			setInteractionState("grace");
			const timer = setTimeout(() => setInteractionState("none"), 800);
			return () => clearTimeout(timer);
		}
	}, [isAnyInteraction]);

	useEffect(() => {
		if (windowLabel === "main") {
			invoke("set_notch_hovered", { hovered: isNotchHovered }).catch(() => {});
		}
	}, [isNotchHovered, windowLabel]);

	// Tell the backend whether the notch is actually on screen so a hidden
	// notch (smart/peek mode) stays click-through instead of swallowing clicks
	// in its footprint.
	useEffect(() => {
		if (windowLabel === "main") {
			invoke("set_notch_visible", { visible: isVisible && !isHidden }).catch(() => {});
		}
	}, [isVisible, isHidden, windowLabel]);

	useEffect(() => {
		const updateRect = () => {
			if (gloomRef.current && windowLabel === "main") {
				// Activity bubbles sit beside the pill and must stay clickable too.
				const rects = [gloomRef.current, musicBubbleRef.current, shelfBubbleRef.current, downloadBubbleRef.current]
					.filter((el): el is NonNullable<typeof el> => el !== null)
					.map((el) => el.getBoundingClientRect());
				const left = Math.min(...rects.map((r) => r.left));
				const top = Math.min(...rects.map((r) => r.top));
				const right = Math.max(...rects.map((r) => r.right));
				const bottom = Math.max(...rects.map((r) => r.bottom));
				invoke("update_notch_rect", {
					rect: {
						x: Math.round(left),
						y: Math.round(top),
						width: Math.round(right - left),
						height: Math.round(bottom - top)
					}
				}).catch(() => {});
				// Bubbles finishing their entrance land here too.
				updateNotchRegion();
			}
		};
		reportNotchRect.current = updateRect;

		updateRect();
		window.addEventListener("resize", updateRect);
		const observer = new ResizeObserver(updateRect);
		if (gloomRef.current) observer.observe(gloomRef.current);

		return () => {
			window.removeEventListener("resize", updateRect);
			observer.disconnect();
		};
	}, [isExpanded, isHidden, windowLabel, scale]);

	useEffect(() => {
		if (!windowLabel) return;

		// Only animate the main top-bar
		if (windowLabel !== "main") {
			setIsReady(true);
			setIsImpacted(true);
			setIsExpanded(true);
			return;
		}

		// Instant fluid startup: reveal the Apple Dynamic Island immediately on launch
		setIsReady(true);
		setIsImpacted(true);
		setIsExpanded(true);
		invoke("setup_shelf_drop").catch(() => {});
	}, [windowLabel]);

	// Settings state
	const [settingsWeatherEnabled, setSettingsWeatherEnabled] = useState(
		() => localStorage.getItem("gloom-weather-enabled") !== "false"
	);
	const [settingsCalendarEnabled, setSettingsCalendarEnabled] = useState(
		() => localStorage.getItem("gloom-calendar-enabled") !== "false"
	);
	const [settingsTimerSoundEnabled, setSettingsTimerSoundEnabled] = useState(
		() => localStorage.getItem("gloom-timer-sound-enabled") !== "false"
	);
	const [settingsMusicModeEnabled, setSettingsMusicModeEnabled] = useState(
		() => localStorage.getItem("gloom-music-mode-enabled") !== "false"
	);
	const [rememberPanelEnabled, setRememberPanelEnabled] = useState(
		() => (localStorage.getItem("gloom-notch-remember-panel") ??
			localStorage.getItem("gloom-notch-click-to-open")) !== "false"
	);
	const rememberPanelEnabledRef = useRef(rememberPanelEnabled);
	rememberPanelEnabledRef.current = rememberPanelEnabled;
	const [defaultPanel, setDefaultPanel] = useState<NotchPanel>(() =>
		asNotchPanel(localStorage.getItem("gloom-notch-default-panel")) ?? "calendar"
	);
	// The panel the open notch was last left on. Opening the notch returns to it,
	// across restarts too (gloom-notch-last-panel).
	const [lastPanel, setLastPanel] = useState<NotchPanel | null>(() =>
		asNotchPanel(localStorage.getItem("gloom-notch-last-panel"))
	);
	const [settingsMusicCompactNotch, setSettingsMusicCompactNotch] = useState(
		() => localStorage.getItem("gloom-music-compact-notch") !== "false"
	);
	const [shelfPersist, setShelfPersist] = useState(
		() => localStorage.getItem("gloom-shelf-persist") === "true"
	);
	const [settingsVisualizerEnabled, setSettingsVisualizerEnabled] = useState(
		() => localStorage.getItem("gloom-visualizer-enabled") !== "false"
	);
	const [settingsAlbumArtEnabled, setSettingsAlbumArtEnabled] = useState(
		() => localStorage.getItem("gloom-media-album-art-enabled") !== "false"
	);
	const [settingsAmbienceEnabled, setSettingsAmbienceEnabled] = useState(
		() => localStorage.getItem("gloom-media-ambience-enabled") !== "false"
	);
	const [settingsCompactGlowEnabled, setSettingsCompactGlowEnabled] = useState(
		() => localStorage.getItem("gloom-media-compact-glow-enabled") !== "false"
	);
	const [mediaLayout, setMediaLayout] = useState<"classic" | "compact">(
		() => (localStorage.getItem("gloom-media-layout") as "classic" | "compact") || "compact"
	);
	const [compactVolumeExpanded, setCompactVolumeExpanded] = useState(false);
	const [classicVolumeExpanded, setClassicVolumeExpanded] = useState(false);
	const [lyricsExpanded, setLyricsExpanded] = useState(false);

	const [activeIslandTab, setActiveIslandTab] = useState<IslandTabId>("media");
	const [connectionsDrilldown, setConnectionsDrilldown] = useState<"none" | "wifi" | "bluetooth" | "audio">("none");
	const [tourActive, setTourActive] = useState(false);
	const [tourStep, setTourStep] = useState(0);
	const [tourDone, setTourDone] = useState(false);
	const stopwatch = useStopwatch();

	useEffect(() => {
		const unlisten = listen("start-tour", () => {
			setTourStep(0);
			setTourDone(false);
			setTourActive(true);
			setIsOpen(true);
		});
		return () => {
			unlisten.then((f) => f());
		};
	}, []);

	// Weather hook
	const {
		temperature,
		weatherCondition,
		weatherIcon: WeatherIcon,
		cityName,
		tempUnit
	} = useWeather(settingsWeatherEnabled);

	useEffect(() => {
		if (!isOpen) return;
		const handleNavKey = (e: KeyboardEvent) => {
			if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
			if (e.key === "1") setActiveIslandTab("media");
			else if (e.key === "2") setActiveIslandTab("focus");
			else if (e.key === "3") {
				setActiveIslandTab("controls");
				setConnectionsDrilldown("none");
			} else if (e.key === "4") setActiveIslandTab("productivity");
			else if (e.key === "5") setActiveIslandTab("system");
		};
		window.addEventListener("keydown", handleNavKey);
		return () => window.removeEventListener("keydown", handleNavKey);
	}, [isOpen]);

	useEffect(() => {
		if (!windowLabel) return;
		let cancelled = false;

		invoke("load_settings")
			.then(async (settings: any) => {
				if (await reloadIfMirrorWasStale(settings)) return;
				if (cancelled) return;
				const getVal = (key: string, fallback: string | null = null) => {
					const val = settings[key];
					if (val !== undefined && val !== null) return String(val);
					const local = localStorage.getItem(key);
					if (local !== null) return local;
					return fallback;
				};

				setSettingsWeatherEnabled(getVal("gloom-weather-enabled", "true") !== "false");
				setSettingsCalendarEnabled(getVal("gloom-calendar-enabled", "true") !== "false");
				setSettingsTimerSoundEnabled(getVal("gloom-timer-sound-enabled", "true") !== "false");
				setSettingsMusicModeEnabled(getVal("gloom-music-mode-enabled", "true") !== "false");
				setRememberPanelEnabled(
					(getVal("gloom-notch-remember-panel") ?? getVal("gloom-notch-click-to-open", "true")) !== "false"
				);
				setDefaultPanel(asNotchPanel(getVal("gloom-notch-default-panel")) ?? "calendar");
				setShelfPersist(getVal("gloom-shelf-persist", "false") === "true");
				setSettingsMusicCompactNotch(getVal("gloom-music-compact-notch", "true") !== "false");
				setLastPanel(asNotchPanel(getVal("gloom-notch-last-panel")));
				const viz =
					getVal("gloom-media-visualizer-enabled") ?? getVal("gloom-visualizer-enabled", "true");
				setSettingsVisualizerEnabled(viz !== "false");
				setSettingsAlbumArtEnabled(getVal("gloom-media-album-art-enabled", "true") !== "false");
				setSettingsAmbienceEnabled(getVal("gloom-media-ambience-enabled", "true") !== "false");
				setSettingsCompactGlowEnabled(
					getVal("gloom-media-compact-glow-enabled", "true") !== "false"
				);
				setTimeFormat24h(getVal("gloom-time-format-24h") === "true");

				const thresholdStr = getVal("gloom-low-battery-threshold", "20");
				if (thresholdStr) setLowBatteryThreshold(parseInt(thresholdStr as string));

				const avoidGames = getVal("gloom-avoid-fullscreen-games", "true");
				setAvoidFullscreenGames(avoidGames !== "false");

				const styleVal = getVal("gloom-island-style", "pill");
				setIslandStyle(styleVal === "notch" ? "notch" : "pill");

				const nMode = getVal("gloom-notch-mode", "fixed");
				if (nMode) {
					const mapped = nMode === "auto-hide" ? "smart" : nMode;
					setNotchMode(mapped);
				}

				if (windowLabel === "main") {
					const firstRun = localStorage.getItem("gloom-first-run") === null;
					if (firstRun) {
						localStorage.setItem("gloom-first-run", "done");
					}
					const rawDockMode = getVal("gloom-dock-mode", "smart") as string;
					const dockMode = rawDockMode === "auto-hide" ? "smart" : rawDockMode;
					setDockMode(dockMode);
					setDockEnabled(getVal("gloom-dock-enabled", "true") === "true");
					const storedVersion = localStorage.getItem("gloom-app-version");
					let waitForSplash = firstRun || storedVersion === null;
					if (!waitForSplash) {
						try {
							waitForSplash = storedVersion !== (await getVersion());
						} catch {
							// Version lookup is optional; continue startup without a splash.
						}
					}
					if (cancelled) return;
					setWaitForStartupSplash(waitForSplash);
					setSystemSettingsReady(true);
				}

				const scaleVal = getVal("gloom-scale");
				if (scaleVal !== null) setScale(parseFloat(scaleVal));

				const widgetsVal = getVal("gloom-status-widgets");
				if (widgetsVal) {
					try {
						const parsed = JSON.parse(widgetsVal);
						if (parsed && Array.isArray(parsed.left) && Array.isArray(parsed.right)) {
							setStatusWidgets(parsed);
						}
					} catch {}
				}
			})
			.catch((error) => {
				console.error(error);
				if (!cancelled && windowLabel === "main") setSystemSettingsReady(true);
			});
		return () => {
			cancelled = true;
		};
	}, [windowLabel]); // eslint-disable-line react-hooks/exhaustive-deps

	useEffect(() => {
		// Disable context menu globally
		const preventContext = (e: MouseEvent) => e.preventDefault();
		document.addEventListener("contextmenu", preventContext);

		const unlistenVisibility = listen<boolean>("visibility-change", (event) => {
			setIsVisible(event.payload);
		});

		const unlistenNotchOverlap = listen<boolean>("notch-overlap", (event) => {
			setIsOverlapped(event.payload);
		});

		const unlistenNotchEdgeHover = listen<boolean>("notch-edge-hover", (event) => {
			setIsEdgeHovered(event.payload);
		});

		// The backend emits this when the window flips to click-through while
		// the webview still thinks the pointer is inside the notch (it cannot
		// receive the mouseleave once the window is click-through).
		const unlistenNotchPointerLeft = listen("notch-pointer-left", () => {
			releaseNotchHover();
		});

		// Live Activity updates (active browser downloads)
		const unlistenLiveActivities = listen<LiveActivityItem[]>("live-activity-update", (event) => {
			if (Array.isArray(event.payload)) {
				setLiveActivities(event.payload);
			}
		});

		return () => {
			unlistenVisibility.then((f) => f());
			unlistenNotchOverlap.then((f) => f());
			unlistenNotchEdgeHover.then((f) => f());
			unlistenNotchPointerLeft.then((f) => f());
			unlistenLiveActivities.then((f) => f());
			document.removeEventListener("contextmenu", preventContext);
		};
	}, [windowLabel]);

	// Sync expanded notch state to backend so the mouse hook can catch outside clicks
	useEffect(() => {
		invoke("set_notch_open", { open: isOpen }).catch(() => {});
	}, [isOpen]);

	// Settings sync — consolidated dispatch for all settings events
	useSettingsSync(
		{
			"gloom-weather-enabled": setSettingsWeatherEnabled,
			"gloom-calendar-enabled": setSettingsCalendarEnabled,
			"gloom-timer-sound-enabled": setSettingsTimerSoundEnabled,
			"gloom-music-mode-enabled": setSettingsMusicModeEnabled,
			"gloom-notch-remember-panel": setRememberPanelEnabled,
			"gloom-notch-default-panel": (v: unknown) =>
				setDefaultPanel(asNotchPanel(v) ?? "calendar"),
			"gloom-shelf-persist": setShelfPersist,
			"gloom-music-compact-notch": setSettingsMusicCompactNotch,
			"gloom-notch-last-panel": (v: unknown) => setLastPanel(asNotchPanel(v)),
			"gloom-media-visualizer-enabled": setSettingsVisualizerEnabled,
			"gloom-visualizer-enabled": setSettingsVisualizerEnabled,
			"gloom-media-album-art-enabled": setSettingsAlbumArtEnabled,
			"gloom-media-ambience-enabled": setSettingsAmbienceEnabled,
			"gloom-media-compact-glow-enabled": setSettingsCompactGlowEnabled,
			"gloom-media-layout": setMediaLayout,
			"gloom-scale": setScale,
			"gloom-low-battery-threshold": setLowBatteryThreshold,
			"gloom-dock-enabled": setDockEnabled,
			"gloom-dock-mode": setDockMode,
			"gloom-notch-mode": setNotchMode,
			"gloom-status-widgets": (value) => {
				try {
					const parsed = JSON.parse(value);
					if (parsed && Array.isArray(parsed.left) && Array.isArray(parsed.right)) {
						setStatusWidgets(parsed);
					}
				} catch {}
			},
			"gloom-show-update-indicator": (value) => setShowUpdateIndicator(String(value) === "true"),
			"gloom-time-format-24h": setTimeFormat24h,
			"gloom-avoid-fullscreen-games": (v: unknown) => setAvoidFullscreenGames(v !== "false"),
			"gloom-island-style": (v: unknown) => setIslandStyle(String(v) === "notch" ? "notch" : "pill")
		},
		[windowLabel]
	);

	// One owner applies the latest window settings. Superseded splash timers and
	// retries are cancelled; an in-flight command finishes before the new state
	// is applied, so an old startup retry cannot overwrite a user change.
	useEffect(() => {
		if (windowLabel !== "main" || !systemSettingsReady) return;

		let cancelled = false;
		let started = false;
		let attempts = 0;
		let startTimer: number | undefined;
		let splashTimer: number | undefined;
		let retryTimer: number | undefined;
		let settleTimer: number | undefined;
		let unlistenSplash: (() => void) | undefined;

		const reconcile = () => {
			systemSyncQueue.current = systemSyncQueue.current
				.catch(() => {})
				.then(async () => {
					if (cancelled) return;
					try {
						if (!isLinux && dockEnabled) {
							if (!appliedDock.current?.enabled) {
								await invoke("init_dock", { mode: dockMode });
								appliedDock.current = { enabled: true, mode: dockMode };
							} else if (appliedDock.current.mode !== dockMode) {
								await invoke("change_dock_mode", { mode: dockMode });
								appliedDock.current = { enabled: true, mode: dockMode };
							}
						} else if (!isLinux && appliedDock.current?.enabled !== false) {
							await invoke("toggle_dock", { enable: false });
							appliedDock.current = { enabled: false, mode: dockMode };
						}
						if (cancelled) return;
						if (appliedNotchMode.current !== notchMode) {
							await invoke("change_notch_mode", { mode: notchMode });
							appliedNotchMode.current = notchMode;
						}
						if (cancelled) return;
						await invoke("sync_appbar");
						if (cancelled) return;
						const wasInitialSync = !initialSystemSyncDone.current;
						initialSystemSyncDone.current = true;
						if (wasInitialSync) {
							settleTimer = window.setTimeout(() => {
								if (!cancelled) {
									systemSyncQueue.current = systemSyncQueue.current
										.catch(() => {})
										.then(() => invoke<void>("sync_appbar"))
										.catch((error) => console.error("AppBar settle failed", error));
								}
							}, 1000);
						}
					} catch (error) {
						console.error("Window reconciliation failed", error);
						if (!cancelled && attempts++ < 1) {
							retryTimer = window.setTimeout(reconcile, 1000);
						}
					}
				});
		};

		const start = () => {
			if (cancelled || started) return;
			started = true;
			splashDone.current = true;
			if (splashTimer !== undefined) window.clearTimeout(splashTimer);
			unlistenSplash?.();
			startTimer = window.setTimeout(reconcile, initialSystemSyncDone.current ? 0 : 400);
		};

		if (waitForStartupSplash && !splashDone.current && !initialSystemSyncDone.current) {
			void listen("splash-done", start)
				.then((unlisten) => {
					if (cancelled || started) unlisten();
					else unlistenSplash = unlisten;
				})
				.catch(() => start());
			splashTimer = window.setTimeout(start, 6000);
		} else {
			start();
		}

		return () => {
			cancelled = true;
			for (const timer of [startTimer, splashTimer, retryTimer, settleTimer]) {
				if (timer !== undefined) window.clearTimeout(timer);
			}
			unlistenSplash?.();
		};
	}, [windowLabel, systemSettingsReady, waitForStartupSplash, dockEnabled, dockMode, notchMode]);

	// Gloom mode state: 'music', 'calendar', 'command-center', 'announcement', 'shelf', or 'status'
	const [gloomMode, setGloomMode] = useState<GloomMode>("status");

	// Shelf: files and selected text stashed in the notch via drag-and-drop.
	// Files are stored as plain paths (references, never copies). Session-scoped
	// by default; the gloom-shelf-persist setting keeps items across restarts.
	const [shelfItems, setShelfItems] = useState<ShelfItem[]>(() => {
		if (localStorage.getItem("gloom-shelf-persist") !== "true") return [];
		try {
			const saved = localStorage.getItem("gloom-shelf-items");
			const parsed = saved ? JSON.parse(saved) : [];
			if (!Array.isArray(parsed)) return [];
			return parsed
				.filter((i): i is ShelfItem => {
					if (typeof i?.id !== "string" || typeof i?.name !== "string") return false;
					// Items stored before text support have no `type`; treat as files.
					if (i.type === "text") return typeof i.text === "string";
					return typeof i.path === "string";
				})
				.map((i) => ({ ...i, type: i.type === "text" ? "text" : "file" }));
		} catch {
			return [];
		}
	});
	useEffect(() => {
		try {
			if (shelfPersist) {
				localStorage.setItem("gloom-shelf-items", JSON.stringify(shelfItems));
			} else {
				localStorage.removeItem("gloom-shelf-items");
			}
		} catch {
			/* storage full or unavailable — shelf just won't persist */
		}
	}, [shelfItems, shelfPersist]);

	// Drop file references whose files are gone (moved/deleted/drive unplugged)
	// once at startup, so a persisted shelf does not accumulate dead entries.
	// Text items are always kept.
	const shelfPrunedRef = useRef(false);
	useEffect(() => {
		if (shelfPrunedRef.current) return;
		shelfPrunedRef.current = true;
		const filePaths = shelfItems
			.filter((i) => i.type === "file" && i.path)
			.map((i) => i.path as string);
		if (filePaths.length === 0) return;
		invoke<string[]>("shelf_prune", { paths: filePaths })
			.then((existing) => {
				const alive = new Set(existing);
				setShelfItems((prev) => {
					const kept = prev.filter(
						(i) => i.type !== "file" || (i.path !== undefined && alive.has(i.path))
					);
					return kept.length === prev.length ? prev : kept;
				});
			})
			.catch(() => {});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	// Previews are generated lazily per path and cached. Images, PDFs, Office
	// files with a saved thumbnail, and anything Windows can thumbnail (videos,
	// folders...) show a picture; documents and text files show their opening
	// text; the rest fall back to an icon for their type. Text items render
	// their content.
	const [shelfThumbs, setShelfThumbs] = useState<Record<string, ShelfPreview>>({});
	const shelfThumbPending = useRef<Set<string>>(new Set());
	useEffect(() => {
		for (const item of shelfItems) {
			if (item.type !== "file" || !item.path) continue;
			const path = item.path;
			if (shelfThumbs[path] !== undefined || shelfThumbPending.current.has(path)) continue;
			shelfThumbPending.current.add(path);
			// The item may be removed while this is in flight; don't resurrect
			// a stale cache entry.
			const storeThumb = (preview: ShelfPreview) =>
				setShelfThumbs((prev) => {
					if (!shelfItemsRef.current.some((i) => i.path === path)) return prev;
					return { ...prev, [path]: preview };
				});
			invoke<ShelfPreview>("shelf_thumbnail", { path })
				.then(storeThumb)
				.catch(() => storeThumb({ thumb: null, text: null, folder: false }))
				.finally(() => shelfThumbPending.current.delete(path));
		}
	}, [shelfItems, shelfThumbs]);

	// Drop cached thumbnails for items that left the shelf so the cache cannot
	// grow without bound over a long session (each entry is a data URI).
	useEffect(() => {
		const current = new Set(shelfItems.filter((i) => i.path).map((i) => i.path as string));
		setShelfThumbs((prev) => {
			const keys = Object.keys(prev);
			if (keys.every((k) => current.has(k))) return prev;
			const next: Record<string, ShelfPreview> = {};
			for (const k of keys) {
				if (current.has(k)) next[k] = prev[k];
			}
			return next;
		});
	}, [shelfItems]);

	// Native drag preview for items without a thumbnail.
	const [shelfDragIcon, setShelfDragIcon] = useState<string | null>(null);
	useEffect(() => {
		invoke<string | null>("shelf_drag_icon")
			.then(setShelfDragIcon)
			.catch(() => {});
	}, []);

	// Picking a file back up is a drag-out gesture: press, move past a small
	// threshold, and the OS takes over the drag. A press without movement is a
	// plain click that opens the file (or copies text items).
	const shelfPointerRef = useRef<{ id: string; x: number; y: number; dragging: boolean } | null>(
		null
	);
	// Timestamp of the last drop onto the notch. A drop recorded during our
	// own drag-out means the item came back to the notch (pointer still over
	// it), which decides whether the drag cleanup clears the hover flags.
	const shelfSelfDropAtRef = useRef(0);
	// True while our own drag-out is running, so the drag-enter we receive when
	// it starts over the notch does not flash the hover outline or re-open it.
	const shelfSelfDragRef = useRef(false);
	const handleShelfPointerDown = (e: React.PointerEvent<HTMLDivElement>, item: ShelfItem) => {
		if (e.button !== 0) return;
		// Text items have no native drag-out; a click copies them.
		if (item.type !== "file" || !item.path) return;
		// The remove button must keep receiving its own click; capturing the
		// pointer on the tile would retarget it.
		if ((e.target as HTMLElement).closest(".shelf-tile-remove")) return;
		e.stopPropagation();
		e.currentTarget.setPointerCapture(e.pointerId);
		shelfPointerRef.current = { id: item.id, x: e.clientX, y: e.clientY, dragging: false };
	};
	const handleShelfPointerMove = (e: React.PointerEvent<HTMLDivElement>, item: ShelfItem) => {
		const st = shelfPointerRef.current;
		if (!st || st.id !== item.id || st.dragging) return;
		if (Math.hypot(e.clientX - st.x, e.clientY - st.y) < 6) return;
		if (!item.path) return;
		const path = item.path;
		const icon = shelfThumbs[path]?.thumb ?? shelfDragIcon;
		if (!icon) return;
		st.dragging = true;
		// Picking an item up is a deliberate act; don't let the post-drop
		// auto-close fire mid-drag.
		clearShelfCollapse();
		shelfSelfDragRef.current = true;
		const dragStartedAt = Date.now();
		startDrag({ item: [path], icon })
			.catch((err) => console.error("[shelf] drag out failed:", path, err))
			.finally(() => {
				shelfSelfDragRef.current = false;
				// A drop recorded during this drag means it landed back on the
				// notch, so the pointer is still over it. Otherwise the native
				// drag owned the pointer and the webview never saw it leave —
				// release the hover state (flags AND the resting mode) or the
				// shelf panel stays expanded. The command also makes the
				// backend re-evaluate its edge-hover state immediately.
				const droppedOnSelf = shelfSelfDropAtRef.current >= dragStartedAt;
				if (!droppedOnSelf) {
					releaseNotchHover();
					invoke("set_notch_hovered", { hovered: false }).catch(() => {});
				}
			});
	};
	const handleShelfTileClick = (e: React.MouseEvent<HTMLDivElement>, item: ShelfItem) => {
		e.stopPropagation();
		const st = shelfPointerRef.current;
		shelfPointerRef.current = null;
		// Only this tile's own drag suppresses the click; a stale flag from a
		// previous tile's drag-out must not swallow it.
		if (st && st.id === item.id && st.dragging) return;
		openShelfItem(item);
	};

	// Wheel scrolling for the shelf strip: ease towards a target instead of
	// jumping, so wheel and trackpad input both feel fluid.
	const shelfScrollTargetRef = useRef<number | null>(null);
	const shelfGridRef = useRef<HTMLDivElement>(null);
	// Whether the shelf row overflows, so it gets room for its scrollbar.
	const [shelfOverflowing, setShelfOverflowing] = useState(false);
	// Measured against the open shelf's width (380px minus its 14px side
	// padding) rather than the live one, which is still growing while the
	// notch animates open. A removed tile stays in the row until its exit
	// animation ends, so the row is measured again then.
	const measureShelfOverflow = useCallback(() => {
		const el = shelfGridRef.current;
		setShelfOverflowing(!!el && el.scrollWidth > 380 - 28 + 1);
	}, []);
	useLayoutEffect(measureShelfOverflow, [shelfItems, gloomMode, measureShelfOverflow]);
	const shelfScrollRafRef = useRef<number | null>(null);
	// The wheel scrolls the row from anywhere over the shelf view, not just
	// over the tiles. Mice that report lines instead of pixels are scaled up.
	const handleShelfWheel = (e: React.WheelEvent<HTMLDivElement>) => {
		const el = shelfGridRef.current;
		if (!el) return;
		const max = el.scrollWidth - el.clientWidth;
		if (max <= 0) return;
		const unit = e.deltaMode === 1 ? 40 : e.deltaMode === 2 ? el.clientWidth : 1;
		const base = shelfScrollTargetRef.current ?? el.scrollLeft;
		shelfScrollTargetRef.current = Math.max(0, Math.min(max, base + (e.deltaY + e.deltaX) * unit));
		if (shelfScrollRafRef.current !== null) return;
		const step = () => {
			const target = shelfScrollTargetRef.current;
			if (target === null) {
				shelfScrollRafRef.current = null;
				return;
			}
			const diff = target - el.scrollLeft;
			if (Math.abs(diff) < 0.5) {
				el.scrollLeft = target;
				shelfScrollTargetRef.current = null;
				shelfScrollRafRef.current = null;
				return;
			}
			const before = el.scrollLeft;
			el.scrollLeft += diff * 0.22;
			// At fractional display scales a small step can round back to the
			// same position; finish the move instead of stalling short of it.
			if (el.scrollLeft === before) {
				el.scrollLeft = target;
				shelfScrollTargetRef.current = null;
				shelfScrollRafRef.current = null;
				return;
			}
			shelfScrollRafRef.current = requestAnimationFrame(step);
		};
		shelfScrollRafRef.current = requestAnimationFrame(step);
	};

	// The shelf closes itself shortly after a drop so the notch does not stay
	// expanded. Hovering it cancels the timer; leaving collapses it.
	const shelfCollapseTimer = useRef<number | null>(null);
	// What the collapsed notch shows (see collapsedMode). Refs let the drop
	// listener and the backend pointer-left event, both mounted once, act on
	// the current state.
	const restingModeRef = useRef<GloomMode>("status");
	const gloomModeRef = useRef<GloomMode>("status");
	const rememberPanelRef = useRef<(panel: NotchPanel) => void>(() => {});
	const notchPinnedRef = useRef(false);
	const releaseDeferredRef = useRef(false);

	// Shared hover-release, called on DOM hover end.
	// Only clears transient hover states. Does NOT collapse an explicitly opened modal island.
	const releaseNotchHover = () => {
		if (notchPinnedRef.current) {
			releaseDeferredRef.current = true;
			return;
		}
		setIsHovered(false);
		setIsNotchHovered(false);
	};

	const closeNotch = useCallback(() => {
		if (isOpenRef.current) {
			const panel = asNotchPanel(gloomModeRef.current);
			if (panel && rememberPanelEnabledRef.current) rememberPanelRef.current(panel);
			setIsOpen(false);
			playTactileSound("pop");
		}
		setGloomMode(restingModeRef.current);
	}, []);

	useEffect(() => {
		const handleKeyDown = (e: KeyboardEvent) => {
			if (e.key === "Escape" && isOpenRef.current) {
				closeNotch();
			}
		};
		window.addEventListener("keydown", handleKeyDown);
		return () => window.removeEventListener("keydown", handleKeyDown);
	}, [closeNotch]);

	// The low-level mouse hook emits this when clicking outside the notch
	// while it is expanded, smoothly dismissing the panel.
	useEffect(() => {
		const unlisten = listen("notch-dismiss", () => {
			if (isOpenRef.current) {
				closeNotch();
			}
		});
		return () => {
			unlisten.then((fn) => fn());
		};
	}, [closeNotch]);

	const clearShelfCollapse = () => {
		if (shelfCollapseTimer.current !== null) {
			window.clearTimeout(shelfCollapseTimer.current);
			shelfCollapseTimer.current = null;
		}
	};

	const collapseShelfSoon = (delay = 1800) => {
		clearShelfCollapse();
		shelfCollapseTimer.current = window.setTimeout(() => {
			shelfCollapseTimer.current = null;
			if (gloomModeRef.current !== "shelf") return;
			setIsOpen(false);
			setGloomMode(restingModeRef.current);
		}, delay);
	};

	const removeShelfItem = (id: string) => {
		const next = shelfItems.filter((i) => i.id !== id);
		setShelfItems(next);
		if (next.length === 0 && gloomMode === "shelf") {
			// Nothing left to show: an open notch moves to the remembered panel,
			// a closed one to its resting mode, instead of an empty shelf.
			clearShelfCollapse();
			setGloomMode(isOpen ? openingPanel() : restingModeRef.current);
		}
	};

	// A full shelf never evicts silently: whatever fits is added and the rest
	// is reported. Reads through a ref so the drop listener (mounted once) sees
	// the current list.
	const shelfItemsRef = useRef<ShelfItem[]>(shelfItems);
	shelfItemsRef.current = shelfItems;
	// Tiles that were just added get an entrance animation; ones restored when
	// the panel reopens do not (the panel itself already fades in).
	const shelfNewIdsRef = useRef<Set<string>>(new Set());
	const markShelfNew = (ids: string[]) => {
		for (const id of ids) shelfNewIdsRef.current.add(id);
		window.setTimeout(() => {
			for (const id of ids) shelfNewIdsRef.current.delete(id);
		}, 900);
	};
	const [shelfNotice, setShelfNotice] = useState<string | null>(null);
	const shelfNoticeTimer = useRef<number | null>(null);
	const showShelfNotice = (message: string) => {
		setShelfNotice(message);
		if (shelfNoticeTimer.current !== null) window.clearTimeout(shelfNoticeTimer.current);
		shelfNoticeTimer.current = window.setTimeout(() => {
			shelfNoticeTimer.current = null;
			setShelfNotice(null);
		}, 2600);
	};

	const addShelfItems = (paths: string[]) => {
		if (paths.length === 0) return;
		const prev = shelfItemsRef.current;
		const existing = new Set(prev.filter((i) => i.path).map((i) => i.path));
		const fresh: ShelfItem[] = [];
		for (const p of paths) {
			if (existing.has(p)) continue;
			existing.add(p);
			fresh.push({
				id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
				type: "file",
				name: p.split("\\").pop()?.split("/").pop() || p,
				path: p,
				timestamp: Date.now()
			});
		}
		if (fresh.length === 0) return;

		const room = Math.max(0, SHELF_MAX_ITEMS - prev.length);
		if (room === 0) {
			showShelfNotice(`Shelf is full (${SHELF_MAX_ITEMS}) — remove an item first`);
			return;
		}
		if (fresh.length > room) {
			showShelfNotice(`Shelf is full — added ${room} of ${fresh.length} items`);
		}
		const added = fresh.slice(0, room);
		markShelfNew(added.map((i) => i.id));
		setShelfItems([...added, ...prev]);
	};

	const addShelfText = (text: string) => {
		const prev = shelfItemsRef.current;
		if (prev.some((i) => i.type === "text" && i.text === text)) return;
		if (prev.length >= SHELF_MAX_ITEMS) {
			showShelfNotice(`Shelf is full (${SHELF_MAX_ITEMS}) — remove an item first`);
			return;
		}
		const item: ShelfItem = {
			id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
			type: "text",
			name: text.trim().replace(/\s+/g, " ").slice(0, 48),
			text,
			timestamp: Date.now()
		};
		markShelfNew([item.id]);
		setShelfItems([item, ...prev]);
	};

	// Files open in their default app; text items are copied to the clipboard
	// so they can be pasted anywhere. A file that no longer exists is reported
	// and dropped from the shelf.
	const openShelfItem = (item: ShelfItem) => {
		if (item.type === "text") {
			if (!item.text) return;
			writeText(item.text)
				.then(() => showShelfNotice("Copied to clipboard"))
				.catch((e) => console.error("[shelf] copy failed:", e));
			return;
		}
		if (item.path) {
			const path = item.path;
			invoke("shelf_open", { path }).catch(async (e) => {
				console.error("[shelf] open failed:", path, e);
				// Only a confirmed-missing file leaves the shelf; a transient
				// failure (e.g. no association) must not remove it.
				const alive = await invoke<string[]>("shelf_prune", { paths: [path] }).catch(() => null);
				if (alive && alive.length === 0) {
					showShelfNotice("File no longer exists — removed from shelf");
					removeShelfItem(item.id);
				} else {
					showShelfNotice("Couldn't open the file");
				}
			});
		}
	};

	// Shelf drag-and-drop plumbing. The backend registers its own OLE drop
	// target (src-tauri/src/shelf.rs) because wry's target can go stale when
	// WebView2 recreates its render child; it emits the shelf-drag-* events
	// below. Drag-enter switches to the shelf panel (restoring the previous
	// mode on cancel); drop stashes the real file paths.
	const [isDragHovering, setIsDragHovering] = useState(false);
	const shelfPrevModeRef = useRef<GloomMode>("status");
	const shelfPrevOpenRef = useRef(false);
	const shelfDragActiveRef = useRef(false);
	useEffect(() => {
		let unlisteners: (() => void)[] = [];
		let cancelled = false;

		const enter = (event: { payload: { paths?: string[]; text?: string } }) => {
			// Our own drag-out starts with the cursor over the notch; ignore
			// the enter it causes instead of flashing the hover state.
			if (shelfSelfDragRef.current) return;
			console.info("[shelf] drag-enter:", event.payload.paths ?? event.payload.text);
			setIsDragHovering(true);
			const prev = gloomModeRef.current;
			if (prev === "announcement" || prev === "shelf") return;
			shelfPrevModeRef.current = prev;
			shelfPrevOpenRef.current = isOpenRef.current;
			shelfDragActiveRef.current = true;
			setIsOpen(true);
			setGloomMode("shelf");
		};
		const leave = () => {
			setIsDragHovering(false);
			if (shelfDragActiveRef.current) {
				shelfDragActiveRef.current = false;
				if (gloomModeRef.current === "shelf") {
					setIsOpen(shelfPrevOpenRef.current);
					setGloomMode(shelfPrevModeRef.current);
				}
			}
		};
		const drop = (event: { payload: { paths?: string[]; text?: string } }) => {
			setIsDragHovering(false);
			shelfDragActiveRef.current = false;
			shelfSelfDropAtRef.current = Date.now();
			const paths = event.payload.paths ?? [];
			const text = event.payload.text;
			console.info("[shelf] drop:", paths.length > 0 ? paths : text);
			if (paths.length === 0 && !text) return;
			if (paths.length > 0) addShelfItems(paths);
			if (text) addShelfText(text);
			if (gloomModeRef.current !== "announcement") {
				setIsOpen(true);
				setGloomMode("shelf");
			}
			collapseShelfSoon();
		};

		Promise.all([
			listen<{ paths?: string[]; text?: string }>("shelf-drag-enter", enter),
			listen("shelf-drag-leave", leave),
			listen<{ paths?: string[]; text?: string }>("shelf-drag-drop", drop),
			listen<{ path?: string; name?: string }>("bridge-file-received", (event) => {
				const { path, name } = event.payload;
				if (path) {
					addShelfItems([path]);
					showShelfNotice(`Received "${name || "file"}" from Mobile Island`);
					setGloomMode("shelf");
					collapseShelfSoon(4000);
				}
			})
		])
			.then((fns) => {
				if (cancelled) {
					fns.forEach((fn) => fn());
				} else {
					unlisteners = fns;
				}
			})
			.catch((e) => console.error("[shelf] drag-drop listener failed:", e));

		invoke("setup_shelf_drop").catch((e) => console.error("[shelf] drop target setup failed:", e));

		return () => {
			cancelled = true;
			unlisteners.forEach((fn) => fn());
		};
	}, []);

	// Open the notch on an unseen announcement; stays open until dismissed.
	useEffect(() => {
		if (!announcement || announcementDismissed) return;
		if (announcementShownRef.current === announcement.id) return;
		announcementShownRef.current = announcement.id;
		setGloomMode("announcement");
		if (notchMode === "peek") triggerEventPeek(6000);
	}, [announcement, announcementDismissed, notchMode, triggerEventPeek]);

	// Leave the announcement view once it is dismissed (or gone).
	useEffect(() => {
		if (gloomMode === "announcement" && (!announcement || announcementDismissed)) {
			setGloomMode(mediaInfo.has_media && isPlaying ? "music" : "status");
		}
	}, [gloomMode, announcement, announcementDismissed, mediaInfo.has_media, isPlaying]);

	// Window height is now kept constant to prevent rendering layout lag and sharp corners

	const lastScrollTime = useRef(0);
	const handleWheel = (e: React.WheelEvent) => {
		const target = e.target as HTMLElement;
		if (
			target.closest(".calendar-grid") ||
			target.closest(".timer-column") ||
			target.closest(".shelf-content") ||
			target.closest(".qp-list") ||
			target.closest(".apple-karaoke-sheet")
		) {
			return;
		}

		// When playing music or in music mode, vertical wheel controls volume directly
		if (isMusicMode && (isPlaying || isOpen) && Math.abs(e.deltaY) > Math.abs(e.deltaX) && Math.abs(e.deltaY) >= 3) {
			e.stopPropagation();
			const step = e.deltaY < 0 ? 0.03 : -0.03;
			const newVol = Math.max(0, Math.min(1, volume + step));
			if (newVol !== volume) {
				handleVolumeChange(newVol);
				playTactileSound("tick");
			}
			return;
		}

		const now = Date.now();
		if (now - lastScrollTime.current < 250) return;

		// Use absolute values to detect horizontal swipe gestures on trackpad
		const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
		if (Math.abs(delta) < 5) return; // Ignore tiny movements

		if (cycleGloomMode(delta > 0 ? 1 : -1)) lastScrollTime.current = now;
	};

	function panelAvailable(panel: NotchPanel) {
		if (panel === "calendar") return settingsCalendarEnabled;
		return true;
	}

	// Wheel and swipe navigate panels, even from a collapsed live activity.
	// Music and shelf remain entry points through their activity affordances.
	const cycleGloomMode = (direction: 1 | -1) => {
		if (gloomMode === "announcement") return false;
		const panels = NOTCH_PANELS.filter(panelAvailable);
		const currentIndex = panels.indexOf(gloomMode as NotchPanel);
		const nextMode = currentIndex === -1
			? openingPanel()
			: panels[(currentIndex + direction + panels.length) % panels.length];
		setIsOpen(true);
		setGloomMode(nextMode);
		return true;
	};

	// Arrows and wheel both stay within panels once one is open.
	const cyclePanel = (direction: 1 | -1) => {
		const panels = NOTCH_PANELS.filter(panelAvailable);
		const index = panels.indexOf(gloomMode as NotchPanel);
		if (index === -1 || panels.length < 2) return;
		setGloomMode(panels[(index + direction + panels.length) % panels.length]);
	};

	// Timer state
	const [timerSeconds, setTimerSeconds] = useState(0);
	const [isTimerRunning, setIsTimerRunning] = useState(false);
	const [isTimerFinished, setIsTimerFinished] = useState(false);
	const [lastDurationSeconds, setLastDurationSeconds] = useState(() => {
		const stored = parseInt(localStorage.getItem("gloom-timer-last-duration") || "", 10);
		return Number.isFinite(stored) && stored > 0 ? stored : 25 * 60;
	});
	const timerIntervalRef = useRef<any>(null);
	const prevTimerFinishedRef = useRef(false);

	const formatTimerTime = (totalSeconds: number) => {
		const mins = Math.floor(Math.abs(totalSeconds) / 60);
		const secs = Math.abs(totalSeconds) % 60;
		return `${mins}:${secs.toString().padStart(2, "0")}`;
	};

	const startTimer = (seconds: number) => {
		// Unlock audio here (user gesture) so the completion chime can play later.
		getTimerChimeCtx();
		setTimerSeconds(seconds);
		setLastDurationSeconds(seconds);
		localStorage.setItem("gloom-timer-last-duration", String(seconds));
		setIsTimerRunning(true);
		setIsTimerFinished(false);
	};

	const resetTimer = () => {
		setIsTimerRunning(false);
		setTimerSeconds(0);
		setIsTimerFinished(false);
	};

	// Idle/finished play restarts the last duration; paused play resumes.
	const handlePrimaryTimerAction = () => {
		if (isTimerRunning) {
			setIsTimerRunning(false);
		} else if (timerSeconds > 0) {
			getTimerChimeCtx();
			setIsTimerRunning(true);
		} else {
			startTimer(lastDurationSeconds);
		}
	};

	useEffect(() => {
		if (isTimerRunning && timerSeconds > 0) {
			timerIntervalRef.current = setInterval(() => {
				setTimerSeconds((s) => s - 1);
			}, 1000);
		} else if (isTimerRunning && timerSeconds === 0) {
			setIsTimerRunning(false);
			setIsTimerFinished(true);
		}
		return () => {
			if (timerIntervalRef.current) clearInterval(timerIntervalRef.current);
		};
	}, [isTimerRunning, timerSeconds === 0]);

	// Completion moment: chime once and reveal the notch in peek mode.
	useEffect(() => {
		if (isTimerFinished && !prevTimerFinishedRef.current) {
			if (settingsTimerSoundEnabled) playTimerChime();
			if (notchMode === "peek") triggerEventPeek(6000);
		}
		prevTimerFinishedRef.current = isTimerFinished;
	}, [isTimerFinished, settingsTimerSoundEnabled, notchMode, triggerEventPeek]);

	const lastTrackRef = useRef<string | null>(null);
	const lastPlayingRef = useRef<boolean>(false);
	const [musicActivityVisible, setMusicActivityVisible] = useState(false);

	// Keep a paused media session visible briefly so it can be resumed, then
	// clear its compact activity even if Windows retains the media session.
	useEffect(() => {
		if (!settingsMusicModeEnabled || !mediaInfo.has_media) {
			setMusicActivityVisible(false);
			return;
		}
		setMusicActivityVisible(true);
		if (isPlaying) return;
		const timeout = window.setTimeout(() => setMusicActivityVisible(false), 5000);
		return () => window.clearTimeout(timeout);
	}, [settingsMusicModeEnabled, mediaInfo.has_media, isPlaying]);

	// Like the Dynamic Island, the collapsed notch shows one live activity at a
	// time. Music fades a few seconds after playback stops. A set timer
	// (running, paused or just finished) takes the pill, and music then moves to
	// its own bubble beside it.
	const timerActive = timerSeconds > 0 || isTimerFinished;
	const musicActivity =
		settingsMusicModeEnabled && settingsMusicCompactNotch && mediaInfo.has_media && musicActivityVisible;
	const collapsedMode = musicActivity && !timerActive ? "music" : "status";
	const showMusicBubble = musicActivity && timerActive && !isOpen && isVisible && !isHidden;
	const showShelfBubble =
		shelfItems.length > 0 && !isOpen && isVisible && !isHidden && gloomMode !== "announcement";
	const showDownloadBubble =
		liveActivities.length > 0 && !isOpen && isVisible && !isHidden && gloomMode !== "announcement";
	restingModeRef.current = collapsedMode;
	gloomModeRef.current = gloomMode;

	// A click opens the last-used panel when memory is enabled, otherwise the
	// selected default. The controls panel is always available as a fallback.
	const openingPanel = (): NotchPanel => {
		const preferred = rememberPanelEnabled ? lastPanel ?? defaultPanel : defaultPanel;
		return panelAvailable(preferred) ? preferred : "command-center";
	};

	// Expands the notch into a panel, or into an activity's own view.
	const openNotch = (view: NotchPanel | "music" | "shelf") => {
		if (gloomMode === "announcement") return;
		playTactileSound("snap");
		setIsOpen(true);
		if (view === "music") {
			setActiveIslandTab("media");
		} else if (view === "shelf") {
			setActiveIslandTab("productivity");
		} else if (view === "calendar") {
			setActiveIslandTab("focus");
		} else if (view === "command-center") {
			setActiveIslandTab("controls");
		}
		setGloomMode(view);
	};

	const rememberPanel = (panel: NotchPanel) => {
		if (panel === lastPanel) return;
		setLastPanel(panel);
		localStorage.setItem("gloom-notch-last-panel", panel);
		invoke("save_setting", { key: "gloom-notch-last-panel", value: panel }).catch(console.error);
	};
	rememberPanelRef.current = rememberPanel;

	// Called by views that take typed input; see releaseNotchHover.
	const setNotchPinned = useCallback((pinned: boolean) => {
		notchPinnedRef.current = pinned;
		if (!pinned && releaseDeferredRef.current) {
			releaseDeferredRef.current = false;
			releaseNotchHover();
		}
		// releaseNotchHover only touches refs and state setters.
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	// In peek mode, briefly show the notch when a new track starts or playback
	// resumes. The notch itself never switches views for media; the collapsed
	// pill follows the live-activity rule below.
	useEffect(() => {
		const { cleanTitle } = extractLiveLyricFromApp(mediaInfo.title, mediaInfo.subtitle, mediaInfo.artist);
		const targetTrack = cleanTitle || mediaInfo.title;
		const isNewTrackWhilePlaying = targetTrack !== lastTrackRef.current && isPlaying;
		const justStartedPlaying = isPlaying && !lastPlayingRef.current;

		// Peek notch on media events (play start or track change)
		if (notchMode === "peek" && (isNewTrackWhilePlaying || justStartedPlaying)) {
			triggerEventPeek(3000);
		}

		lastTrackRef.current = targetTrack;
		lastPlayingRef.current = isPlaying;
	}, [isPlaying, mediaInfo.title, mediaInfo.subtitle, mediaInfo.artist, notchMode, triggerEventPeek]);

	// Reset gloom mode when calendar setting is disabled
	useEffect(() => {
		if (!settingsCalendarEnabled && gloomMode === "calendar") {
			setGloomMode(isOpen ? openingPanel() : collapsedMode);
		}
	}, [settingsCalendarEnabled, gloomMode, isOpen]);

	// Leave the music view when music is turned off or the player ends its media
	// session: an open notch falls back to the remembered panel.
	useEffect(() => {
		if (gloomMode === "music" && !(settingsMusicModeEnabled && mediaInfo.has_media)) {
			setGloomMode(isOpen ? openingPanel() : collapsedMode);
		}
	}, [settingsMusicModeEnabled, mediaInfo.has_media, gloomMode, isOpen]);

	// The collapsed notch always shows its current live activity: music while a
	// session exists, the timer (with music in its bubble) once one is set, and
	// the clock otherwise. An open notch is left alone.
	useEffect(() => {
		if (isOpen || announcementOpenRef.current) return;
		if ((gloomMode === "status" || gloomMode === "music") && gloomMode !== collapsedMode) {
			setGloomMode(collapsedMode);
		}
	}, [collapsedMode, gloomMode, isOpen]);

	// Update time
	useEffect(() => {
		const updateTime = () => {
			const now = new Date();
			setTime(
				now.toLocaleTimeString([], {
					hour: "2-digit",
					minute: "2-digit",
					hour12: !timeFormat24h
				})
			);
		};

		updateTime();
		const interval = setInterval(updateTime, 1000);

		return () => {
			clearInterval(interval);
		};
	}, [timeFormat24h]);

	// Battery API
	useEffect(() => {
		let battery: any = null;

		const initBattery = async () => {
			try {
				battery = await (navigator as any).getBattery();

				const updateBattery = () => {
					setBatteryLevel(Math.round(battery.level * 100));
					setIsCharging(battery.charging);
				};

				updateBattery();

				battery.addEventListener("levelchange", updateBattery);
				battery.addEventListener("chargingchange", updateBattery);

				return () => {
					battery.removeEventListener("levelchange", updateBattery);
					battery.removeEventListener("chargingchange", updateBattery);
				};
			} catch (e) {
				// Battery API not supported
			}
		};

		initBattery();
	}, []);

	// Listen for Volume Changes
	useEffect(() => {
		const unlisten = listen<{ volume: number; is_muted: boolean }>("volume-change", (event) => {
			setVolume(event.payload.volume);
			setIsMuted(event.payload.is_muted);
		});
		return () => {
			unlisten.then((fn) => fn());
		};
	}, []);

	// Fullscreen game & presentation detection (Hey Nixi D3D parity)
	useEffect(() => {
		const unlisten = listen<{ is_fullscreen: boolean; reason: string }>(
			"fullscreen-game-changed",
			(event) => {
				setIsFullscreenGame(event.payload.is_fullscreen);
			}
		);
		invoke<{ is_fullscreen: boolean; reason: string }>("get_fullscreen_game_state")
			.then((state) => setIsFullscreenGame(state.is_fullscreen))
			.catch(() => {});

		return () => {
			unlisten.then((fn) => fn());
		};
	}, []);

	// Listen for Key Lock (Caps Lock / Num Lock) Changes from Dynamic Island Engine
	useEffect(() => {
		const unlisten = listen<{ key: string; enabled: boolean }>("key-lock-changed", (event) => {
			setKeyLockNotice(event.payload);
			if (notchMode === "peek") triggerEventPeek(2500);
			if (keyLockTimeoutRef.current) clearTimeout(keyLockTimeoutRef.current);
			keyLockTimeoutRef.current = setTimeout(() => {
				setKeyLockNotice(null);
			}, 2200);
		});
		return () => {
			unlisten.then((fn) => fn());
		};
	}, [notchMode, triggerEventPeek]);

	// Wi-Fi is a three-state control: Off, on-but-not-connected, Connected.
	// The radio state alone can't tell those apart, so get_wifi_status returns
	// both flags, plus the connected network's name. Returns false so the startup loader knows to retry.
	const refreshWifiStatus = useCallback(async (): Promise<boolean> => {
		try {
			setWifiStatus(await invoke<WifiStatus>("get_wifi_status"));
			return true;
		} catch {
			return false;
		}
	}, []);

	// Seed the Wi-Fi pill at startup. The first call can fail while the WLAN
	// service is still coming up, so retry instead of leaving the pill stuck on
	// its default.
	useEffect(() => {
		let cancelled = false;
		let retryTimer: ReturnType<typeof setTimeout> | undefined;
		const initializeWifiStatus = async (attempt: number) => {
			const loaded = await refreshWifiStatus();
			if (!loaded && !cancelled && attempt < 3) {
				retryTimer = setTimeout(() => void initializeWifiStatus(attempt + 1), (attempt + 1) * 1000);
			}
		};
		void initializeWifiStatus(0);
		return () => {
			cancelled = true;
			if (retryTimer) clearTimeout(retryTimer);
		};
	}, [refreshWifiStatus]);

	// Connected Bluetooth devices for the tile, refreshed whenever the backend
	// reports a device connecting, disconnecting, pairing or being removed.
	useEffect(() => {
		if (!bluetoothEnabled) {
			setBtConnectedNames([]);
			return;
		}
		const refresh = () =>
			invoke<{ name: string; connected: boolean }[]>("bluetooth_list_devices")
				.then((devices) =>
					setBtConnectedNames(devices.filter((d) => d.connected).map((d) => d.name))
				)
				.catch(() => {});
		let debounce: ReturnType<typeof setTimeout> | undefined;
		void refresh();
		const unlisten = listen("bluetooth-changed", () => {
			clearTimeout(debounce);
			debounce = setTimeout(() => void refresh(), 400);
		});
		return () => {
			clearTimeout(debounce);
			unlisten.then((f) => f());
		};
	}, [bluetoothEnabled]);

	// The backend reports connection changes and finished scans as they
	// happen, so the pill follows without waiting for the next poll.
	useEffect(() => {
		let debounce: ReturnType<typeof setTimeout> | undefined;
		const unlisten = listen("wifi-changed", () => {
			clearTimeout(debounce);
			debounce = setTimeout(() => void refreshWifiStatus(), 300);
		});
		return () => {
			clearTimeout(debounce);
			unlisten.then((f) => f());
		};
	}, [refreshWifiStatus]);

	// Load bluetooth/volume/brightness state on mount
	useEffect(() => {
		invoke<boolean>("get_bluetooth_state")
			.then(setBluetoothEnabled)
			.catch(() => {});
		invoke<boolean>("get_battery_saver_state")
			.then(setBatterySaverEnabled)
			.catch(() => {});
		invoke<{ volume: number; is_muted: boolean }>("get_volume_state")
			.then((state) => {
				setVolume(state.volume);
				setIsMuted(state.is_muted);
			})
			.catch(() => {});
		invoke<number>("get_brightness")
			.then(setCurrentBrightness)
			.catch(() => {});

		// Poll battery saver state every 5s (since we can't listen for changes)
		const interval = setInterval(() => {
			invoke<boolean>("get_battery_saver_state")
				.then(setBatterySaverEnabled)
				.catch(() => {});
		}, 5000);
		return () => clearInterval(interval);
	}, []);

	// Refresh Wi-Fi status while the command center is open so changes made
	// outside Gloom (keyboard toggle, Windows quick settings) show up.
	useEffect(() => {
		if (gloomMode !== "command-center") return;
		void refreshWifiStatus();
		const interval = setInterval(() => void refreshWifiStatus(), 5000);
		return () => clearInterval(interval);
	}, [gloomMode, refreshWifiStatus]);

	// Poll system metrics for status widgets
	useEffect(() => {
		const fetchMetrics = () => {
			invoke<number>("get_cpu_usage")
				.then(setCpuUsage)
				.catch((e) => console.warn("CPU:", e));
			invoke<number>("get_ram_usage")
				.then(setRamUsage)
				.catch((e) => console.warn("RAM:", e));
			invoke<number>("get_disk_space")
				.then(setDiskSpace)
				.catch((e) => console.warn("Disk:", e));
			invoke<[number, number]>("get_network_speed")
				.then(([up, down]) => {
					setNetUpSpeed(up);
					setNetDownSpeed(down);
				})
				.catch(() => {});
		};
		fetchMetrics();
		const interval = setInterval(fetchMetrics, 3000);
		return () => clearInterval(interval);
	}, []);

	// Listen for brightness changes
	useEffect(() => {
		const unlisten = listen<{ brightness: number }>("brightness-change", (event) => {
			setCurrentBrightness(event.payload.brightness);
		});
		return () => {
			unlisten.then((fn) => fn());
		};
	}, []);

	// Native Windows Media Controls - Listen for updates from background worker
	useEffect(() => {
		const unlisten = listen<MediaInfo>("media-update", (event) => {
			const info = event.payload;
			if (!info) return;

			setMediaInfo((prev) => {
				// Find if artwork changed by checking the first element
				const prevArt = prev.artwork?.[0];
				const nextArt = info.artwork?.[0];
				const artChanged = prevArt !== nextArt;

				if (
					prev.title === info.title &&
					prev.artist === info.artist &&
					prev.subtitle === info.subtitle &&
					prev.source_app === info.source_app &&
					prev.is_playing === info.is_playing &&
					prev.has_media === info.has_media &&
					!artChanged &&
					prev.position_ms === info.position_ms &&
					prev.duration_ms === info.duration_ms
				) {
					return prev;
				}

				// Update playing state separately for the hook triggers
				setIsPlaying(info.is_playing);

				if (info.artwork && info.artwork.length > 0) {
					const newArt = info.artwork[0];
					setAlbumArtUrl((prev) => {
						if (prev !== newArt) {
							setAlbumArtKey((k) => k + 1);
							return newArt;
						}
						return prev;
					});
				} else {
					setAlbumArtUrl(null);
				}

				return info;
			});
		});

		return () => {
			unlisten.then((fn) => fn());
		};
	}, []);

	// Media controls via Tauri commands
	/* Unused saveAndBroadcast removed to fix TS build error */
	const togglePlayPause = useCallback(async () => {
		try {
			await invoke("media_play_pause");
			setIsPlaying(!isPlaying);
		} catch (e) {
			console.error("Failed to toggle play/pause:", e);
		}
	}, [isPlaying]);

	const skipNext = useCallback(async () => {
		try {
			await invoke("media_next");
		} catch (e) {
			console.error("Failed to skip next:", e);
		}
	}, []);

	const skipPrevious = useCallback(async () => {
		try {
			await invoke("media_previous");
		} catch (e) {
			console.error("Failed to skip previous:", e);
		}
	}, []);

	// Slide-push animation for prev/next buttons
	const prevFront = useAnimation();
	const prevBack = useAnimation();
	const nextFront = useAnimation();
	const nextBack = useAnimation();

	const animatePrev = useCallback(async () => {
		prevFront.set({ x: 0 });
		prevBack.set({ x: 30 });
		prevFront.start({ x: -30, transition: { duration: 0.18, ease: [0.4, 0, 0.2, 1] } });
		await prevBack.start({ x: 0, transition: { duration: 0.18, ease: [0.4, 0, 0.2, 1] } });
		skipPrevious();
	}, [skipPrevious, prevFront, prevBack]);

	const animateNext = useCallback(async () => {
		nextFront.set({ x: 0 });
		nextBack.set({ x: -30 });
		nextFront.start({ x: 30, transition: { duration: 0.18, ease: [0.4, 0, 0.2, 1] } });
		await nextBack.start({ x: 0, transition: { duration: 0.18, ease: [0.4, 0, 0.2, 1] } });
		skipNext();
	}, [skipNext, nextFront, nextBack]);

	const sendVolume = useTrailingThrottle((newVol: number) => {
		invoke("set_volume", { volume: newVol }).catch(() => {});
	}, 50);

	const handleVolumeChange = useCallback(
		(newVol: number) => {
			setVolume(newVol);
			// set_volume unmutes for any non-zero level.
			setIsMuted(newVol === 0);
			sendVolume(newVol);
		},
		[sendVolume]
	);

	// WiFi toggle
	const toggleWifi = useCallback(async () => {
		const previous = wifiStatus;
		const newState = !(previous?.enabled ?? false);
		setWifiStatus({
			enabled: newState,
			connected: newState ? (previous?.connected ?? false) : false,
			ssid: newState ? (previous?.ssid ?? null) : null
		});
		try {
			await invoke("set_wifi_state", { enabled: newState });
			// The radio and association take a moment to settle.
			setTimeout(() => void refreshWifiStatus(), 800);
		} catch (e) {
			setWifiStatus(previous);
			console.error("Failed to toggle WiFi:", e);
		}
	}, [wifiStatus, refreshWifiStatus]);

	// Bluetooth toggle
	const toggleBluetooth = useCallback(async () => {
		const newState = !bluetoothEnabled;
		setBluetoothEnabled(newState);
		try {
			await invoke("set_bluetooth_state", { enabled: newState });
		} catch (e) {
			setBluetoothEnabled(!newState);
			console.error("Failed to toggle Bluetooth:", e);
		}
	}, [bluetoothEnabled]);

	// The notch never takes focus, and native `title` tooltips only show for an
	// active window, so the status widgets draw their own. A tooltip opens
	// after a tooltip-like delay, by which time the battery details fetched on
	// enter are in. Under the status strip it hangs below the pill; once the
	// notch is open the pill is the tall panel, so it sits beside the pill on
	// the widget's side instead, level with the widget.
	const showStatusTooltip = useCallback(
		(id: string, widget: HTMLElement) => {
			if (statusTooltipTimerRef.current) clearTimeout(statusTooltipTimerRef.current);
			statusTooltipTimerRef.current = setTimeout(() => {
				const pill = gloomRef.current?.getBoundingClientRect();
				const own = widget.getBoundingClientRect();
				// Client rects are zoomed by the notch scale; offsets are not.
				if (!pill) {
					setStatusTooltip({ id, side: "below", offset: 8 });
				} else if (!isOpen) {
					const gap = (pill.bottom - own.bottom) / scale;
					setStatusTooltip({ id, side: "below", offset: Math.max(0, gap) + 8 });
				} else if (own.left + own.width / 2 >= pill.left + pill.width / 2) {
					const gap = (pill.right - own.right) / scale;
					setStatusTooltip({ id, side: "right", offset: Math.max(0, gap) + 10 });
				} else {
					const gap = (own.left - pill.left) / scale;
					setStatusTooltip({ id, side: "left", offset: Math.max(0, gap) + 10 });
				}
			}, 400);
		},
		[scale, isOpen]
	);

	const hideStatusTooltip = useCallback(() => {
		if (statusTooltipTimerRef.current) clearTimeout(statusTooltipTimerRef.current);
		statusTooltipTimerRef.current = null;
		setStatusTooltip(null);
	}, []);

	// Widgets unmount when the notch collapses, without a mouse leave, and an
	// open card is placed for the notch's shape when it opened.
	useEffect(() => {
		hideStatusTooltip();
	}, [isHovered, isOpen, hideStatusTooltip]);

	// Both sides of the status strip get equal width around the centered
	// clock, so the notch must fit the clock plus twice the wider side. Widget
	// widths vary (network speed especially), so they are measured rather than
	// counted. The width only grows during a hover, so a live label getting
	// shorter doesn't make the notch jitter. Layout widths are unaffected by
	// the notch scale.
	useLayoutEffect(() => {
		if (!isHovered || gloomMode !== "status") {
			if (statusFitWidth !== 0) setStatusFitWidth(0);
			return;
		}
		// offsetWidth, not scrollWidth: an open tooltip card overflows its
		// widget and must not widen the notch.
		const side = Math.max(
			leftWidgetsRef.current?.offsetWidth ?? 0,
			rightWidgetsRef.current?.offsetWidth ?? 0
		);
		const clock = timeCenterRef.current?.offsetWidth ?? 0;
		// The pill's 12px padding on each side, and a 10px gap to the clock.
		const needed = Math.ceil(clock + 2 * (side + 10) + 24);
		if (side > 0 && needed > statusFitWidth) setStatusFitWidth(needed);
	});

	const refreshBatteryDetails = useCallback(() => {
		invoke<BatteryDetails>("get_battery_details")
			.then(setBatteryDetails)
			.catch((err) => console.error("Failed to read battery details:", err));
	}, []);

	// Battery Saver - opens settings (no public API to toggle without admin)
	const openBatterySaverSettings = useCallback(async () => {
		try {
			await invoke("open_battery_saver_settings");
		} catch (e) {
			console.error("Failed to open Battery Saver settings:", e);
		}
	}, []);

	// Brightness change with throttling
	const sendBrightness = useTrailingThrottle((newVal: number) => {
		invoke("set_brightness", { brightness: newVal }).catch(() => {});
	}, 50);

	const handleBrightnessChange = useCallback(
		(newVal: number) => {
			setCurrentBrightness(newVal);
			sendBrightness(newVal);
		},
		[sendBrightness]
	);

	// Open system tray (unhide taskbar and invoke Win+B)
	const openSystemTray = useCallback(async (e: React.MouseEvent) => {
		e.stopPropagation();
		try {
			await invoke("open_system_tray");
		} catch (e) {
			console.error("Failed to open system tray:", e);
		}
	}, []);

	const openSettingsWindow = useCallback(async () => {
		try {
			await invoke("open_settings_window");
		} catch (e) {
			console.error("Failed to open settings window:", e);
		}
	}, []);

	// Right-click opens the tile's own view, the same as its › button.
	const handleWifiRightClick = useCallback((e: React.MouseEvent) => {
		e.preventDefault();
		e.stopPropagation();
		setGloomMode("wifi");
	}, []);

	const toggleDockModeSetting = useCallback(
		async (e: React.MouseEvent) => {
			e.stopPropagation();
			const nextMode = dockMode === "fixed" ? "smart" : dockMode === "smart" ? "peek" : "fixed";
			setDockMode(nextMode);
			localStorage.setItem("gloom-dock-mode", nextMode);
			invoke("save_setting", { key: "gloom-dock-mode", value: nextMode }).catch(console.error);
			try {
				await invoke("change_dock_mode", { mode: nextMode });
			} catch (err) {
				console.error("Failed to change dock mode:", err);
			}
		},
		[dockMode]
	);

	const toggleNotchModeSetting = useCallback(
		async (e: React.MouseEvent) => {
			e.stopPropagation();
			const nextMode = notchMode === "fixed" ? "smart" : notchMode === "smart" ? "peek" : "fixed";
			setNotchMode(nextMode);
			localStorage.setItem("gloom-notch-mode", nextMode);
			invoke("save_setting", { key: "gloom-notch-mode", value: nextMode }).catch(console.error);
			try {
				await invoke("change_notch_mode", { mode: nextMode });
			} catch (err) {
				console.error("Failed to change notch mode:", err);
			}
		},
		[notchMode]
	);

	const handleBluetoothRightClick = useCallback((e: React.MouseEvent) => {
		e.preventDefault();
		e.stopPropagation();
		setGloomMode("bluetooth");
	}, []);

	const toggleCalendarMode = (e: React.MouseEvent) => {
		e.stopPropagation();
		if (isTimerFinished) {
			resetTimer();
			return;
		}
		// The time is part of the notch: a click opens it like anywhere else.
		// Once open, it is a shortcut to the calendar.
		if (!isOpen) {
			openNotch(openingPanel());
		} else if (settingsCalendarEnabled) {
			setGloomMode("calendar");
		}
	};

	// Where a status tooltip card sits and how it slides in, by placement.
	const statusTooltipMotion = (side: "below" | "left" | "right", offset: number) => {
		if (side === "below") {
			return {
				style: { top: `calc(100% + ${offset}px)`, left: "50%" },
				hidden: { opacity: 0, x: "-50%", y: -4 },
				shown: { opacity: 1, x: "-50%", y: 0 }
			};
		}
		// Top-aligned with the widget rather than centered on it: the widgets
		// sit near the top of the screen, which would cut a centered card off.
		const nudge = side === "right" ? -4 : 4;
		return {
			style: { top: -8, [side === "right" ? "left" : "right"]: `calc(100% + ${offset}px)` },
			hidden: { opacity: 0, x: nudge, y: 0 },
			shown: { opacity: 1, x: 0, y: 0 }
		};
	};

	// A status widget with its tooltip card, which hangs outside the pill.
	const statusWidget = (
		id: string,
		tooltip: string,
		content: React.ReactNode,
		onEnter?: () => void
	) => (
		<div
			className="passive-feature"
			key={id}
			onMouseEnter={(e) => {
				onEnter?.();
				showStatusTooltip(id, e.currentTarget);
			}}
			onMouseLeave={hideStatusTooltip}
		>
			{content}
			<AnimatePresence>
				{statusTooltip?.id === id &&
					(() => {
						const placed = statusTooltipMotion(statusTooltip.side, statusTooltip.offset);
						return (
							<motion.div
								className="status-tooltip"
								style={placed.style}
								initial={placed.hidden}
								animate={placed.shown}
								exit={placed.hidden}
								transition={{ duration: 0.15 }}
							>
								{tooltip}
							</motion.div>
						);
					})()}
			</AnimatePresence>
		</div>
	);

	// Render a status widget by ID
	const renderStatusWidget = (id: string) => {
		switch (id) {
			case "weather":
				if (!settingsWeatherEnabled || temperature === null) return null;
				return statusWidget(
					"weather",
					cityName ? `${weatherCondition} — ${cityName}` : weatherCondition,
					<>
						<WeatherIcon size={12} strokeWidth={2.2} />
						<span className="label">
							{temperature}°{tempUnit === "fahrenheit" ? "F" : "C"}
						</span>
					</>
				);
			case "battery":
				return statusWidget(
					"battery",
					batteryTooltip(
						batteryDetails,
						{ level: batteryLevel, charging: isCharging },
						batterySaverEnabled
					),
					<>
						<BatteryIcon
							charging={isCharging}
							level={batteryLevel}
							threshold={lowBatteryThreshold}
						/>
						<span className="label">{batteryLevel}%</span>
					</>,
					refreshBatteryDetails
				);
			case "cpu":
				return statusWidget(
					"cpu",
					"CPU Usage",
					<>
						<Cpu size={12} strokeWidth={2} />
						<span className="label">{cpuUsage}%</span>
					</>
				);
			case "ram":
				return statusWidget(
					"ram",
					"RAM Usage",
					<>
						<MemoryStick size={12} strokeWidth={2} />
						<span className="label">{Math.round(ramUsage)}%</span>
					</>
				);
			case "disk":
				return statusWidget(
					"disk",
					"Free Disk Space",
					<>
						<HardDrive size={12} strokeWidth={2} />
						<span className="label">{diskSpace}GB</span>
					</>
				);
			case "net":
				return statusWidget(
					"net",
					"Network Speed",
					<>
						<ArrowUpDown size={12} strokeWidth={2} />
						<span className="label">
							↑{formatBytes(netUpSpeed)} ↓{formatBytes(netDownSpeed)}
						</span>
					</>
				);
			default:
				return null;
		}
	};

	const formatBytes = (bytes: number) => {
		if (bytes < 1024) return `${bytes}B`;
		if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}K`;
		return `${(bytes / (1024 * 1024)).toFixed(1)}M`;
	};

	// Music mode shows any time we have media info (playing or paused) and music mode setting is enabled
	const isMusicMode = mediaInfo.has_media && gloomMode === "music" && settingsMusicModeEnabled;
	// A media session remains reachable on hover after its activity fades, or
	// whenever the compact music display is disabled.
	const showMusicOnStatusHover =
		!isOpen && isHovered && gloomMode === "status" && settingsMusicModeEnabled &&
		mediaInfo.has_media && (!musicActivityVisible || !settingsMusicCompactNotch);
	const showCompactMusicIcons = isMusicMode || showMusicOnStatusHover;

	// The 24-hour clock is ~16px narrower than the 12-hour one.
	const clock24hTrim = timeFormat24h ? 16 : 0;

	// Calculate width dynamically based on enabled features
	const getDynamicWidth = () => {
		if (gloomMode === "announcement" && announcement && !announcementDismissed) return 380;
		if (isOpen) {
			if (activeIslandTab === "media") {
				if (lyricsExpanded) return 440;
				return mediaLayout === "compact" ? 360 : 400;
			}
			if (activeIslandTab === "focus") return 490;
			if (activeIslandTab === "controls") return connectionsDrilldown !== "none" ? 380 : 360;
			if (activeIslandTab === "productivity") return 430;
			if (activeIslandTab === "system") return 420;
			return 380;
		}
		if (showMusicOnStatusHover) {
			return 170 + (settingsVisualizerEnabled ? 30 : 0);
		}
		if (gloomMode === "status" && isHovered) {
			const totalWidgets = statusWidgets.left.length + statusWidgets.right.length;
			return Math.max(Math.min(200 + totalWidgets * 50, 380) - clock24hTrim, statusFitWidth);
		}
		if (isMusicMode && isOpen) {
			if (lyricsExpanded) return 420;
			return mediaLayout === "compact" ? 330 : 380;
		}
		if ((showPowerPulse || showLowBatteryPulse || showUpdatePulse) && !isHovered) return 200;

		let w = 140 - clock24hTrim;
		if (isMusicMode) {
			if (settingsVisualizerEnabled && isPlaying) w += 30;
			if (settingsAlbumArtEnabled) w += 30;
		}

		return w;
	};

	const getDynamicHeight = () => {
		if (!isExpanded || !isVisible || isHidden) {
			return isImpacted ? 28.9 : 44.2;
		}
		// Announcement card: body is line-clamped, so a fixed size fits both cases.
		if (gloomMode === "announcement" && announcement && !announcementDismissed) {
			return announcement.url ? 168 : 148;
		}
		if (isOpen) {
			if (activeIslandTab === "media") {
				if (lyricsExpanded) return 340;
				const hasProgressBar = (mediaInfo.duration_ms ?? 0) > 0;
				if (mediaLayout === "compact") {
					let h = hasProgressBar ? 210 : 180;
					if (compactVolumeExpanded) h += 34;
					return h;
				} else {
					let h = hasProgressBar ? 220 : 190;
					if (classicVolumeExpanded) h += 34;
					return h;
				}
			}
			if (activeIslandTab === "focus") {
				return calendarMonthRows >= 6 ? 380 : 350;
			}
			if (activeIslandTab === "controls") {
				return connectionsDrilldown !== "none" ? 360 : 290;
			}
			if (activeIslandTab === "productivity") {
				return 280;
			}
			if (activeIslandTab === "system") {
				return 340;
			}
			return 280;
		}
		// Sized to the calendar's week-row count plus the timer's fixed content.
		if (gloomMode === "calendar") return calendarMonthRows >= 6 ? 320 : 288;
		// Unified Productivity Drawer (Files, Clipboard History, Scratchpad)
		if (gloomMode === "shelf") {
			return 220;
		}
		if (gloomMode === "command-center") return isOpen ? 230 : 36;
		if (gloomMode === "wifi" || gloomMode === "bluetooth") return 300;
		if (gloomMode === "status") return 36;
		return 36;
	};

	const isCalendarMode = gloomMode === "calendar";
	// Arrows switch between the two tool panels; wheel/swipe can also open one
	// directly from the collapsed pill or a live activity.
	const showPanelArrows =
		isOpen && asNotchPanel(gloomMode) !== null && NOTCH_PANELS.filter(panelAvailable).length > 1;

	// Close media player expansions when notch is closed, unhovered, or mode changes
	useEffect(() => {
		if (!isOpen || !isMusicMode) {
			setCompactVolumeExpanded(false);
			setClassicVolumeExpanded(false);
			setLyricsExpanded(false);
		}
	}, [isOpen, isMusicMode]);

	// The pill's target geometry: the animation springs toward it, and the
	// window region below is cut from it, so the region is already at the
	// final size while the pill animates.
	const isPill = islandStyle === "pill";
	const pillYOffset = isPill ? 10 : 0;
	// In fixed mode or notch mode, the notch/pill must be anchored flush to the screen's top edge (y = 0) with zero gap.
	const pillTargetY = !isReady ? 250 : isVisible ? (isHidden ? -120 : pillYOffset) : -150;
	const pillTargetWidth = !isReady
		? 34
		: isExpanded && isVisible && !isHidden
			? getDynamicWidth()
			: isImpacted
				? 39.1
				: 30.6;
	const pillTargetHeight = !isReady ? 34 : getDynamicHeight();
	const pillRadius = isOpen
		? (isCalendarMode ? 32 : 26)
		: (isPill ? 22 : 20);
	// Shadow and the edge flares beside the pill.
	pillTargetRef.current = notchPillBox(
		window.innerWidth,
		scale,
		Math.max(0, pillTargetY - 4),
		pillTargetWidth,
		pillTargetHeight + (isPill ? 4 : 0),
		24
	);

	// Clip the notch window to what it draws (see window_region.rs): outside
	// these rectangles the window takes no clicks, whatever its click-through
	// state. Sent after every render, but only when the region changes.
	useEffect(() => {
		if (windowLabel === "main") updateNotchRegion();
	}, [pillTargetWidth, pillTargetHeight, isExpanded, isOpen, isHidden, isVisible, interactionState, musicActivity, shelfItems.length, statusWidget, islandStyle, isPill]);

	return (
		<div
			className="screen"
			style={{ overflow: "hidden" }}
			onClick={() => {
				if (isOpen) {
					closeNotch();
				}
			}}
		>

			{/* Top-edge peek sensor: reveals the Dynamic Island / Notch immediately when cursor touches top screen edge */}
			<div
				className="top-edge-peek-sensor"
				onMouseEnter={() => {
					setInteractionState("active");
					triggerEventPeek(4000);
				}}
				onMouseMove={() => {
					setInteractionState("active");
				}}
				style={{
					position: "fixed",
					top: 0,
					left: "50%",
					transform: "translateX(-50%)",
					width: Math.max(380, pillTargetWidth + 100),
					height: 16,
					zIndex: 99999,
					pointerEvents: "auto",
					background: "transparent"
				}}
			/>

			<div
				style={{
					zoom: scale,
					width: "100%",
					display: "flex",
					justifyContent: "center",
					position: "relative"
				}}
			>
				<AnimatePresence>
					{keyLockNotice && (
						<motion.div
							key="key-lock-pill"
							className="key-lock-pill-hud"
							initial={{ opacity: 0, y: -8, scale: 0.92 }}
							animate={{ opacity: 1, y: 0, scale: 1 }}
							exit={{ opacity: 0, y: -8, scale: 0.92 }}
							transition={{ type: "spring", stiffness: 450, damping: 30 }}
						>
							<div className="key-lock-icon">
								<Type size={12} strokeWidth={2.4} />
							</div>
							<span className="key-lock-label">{keyLockNotice.key}:</span>
							<span className={`key-lock-state ${keyLockNotice.enabled ? "enabled" : "disabled"}`}>
								{keyLockNotice.enabled ? "On" : "Off"}
							</span>
						</motion.div>
					)}
				</AnimatePresence>
				<motion.div
					ref={gloomRef}
					className={`gloom ${isHovered ? "expanded" : ""} ${isImpacted ? "is-impacted" : ""} ${isDragHovering ? "drag-hover" : ""} ${isPill ? "pill-mode" : "notch-mode"}`}
					onMouseEnter={() => setIsNotchHovered(true)}
					onMouseLeave={() => setIsNotchHovered(false)}
					onWheel={handleWheel}
					whileTap={{ scale: 0.97 }}
					initial={{
						y: 250,
						width: 30.6,
						height: 44.2,
						borderTopLeftRadius: isPill ? 22 : 0,
						borderTopRightRadius: isPill ? 22 : 0,
						borderBottomLeftRadius: 22,
						borderBottomRightRadius: 22,
						scaleX: 1,
						scaleY: 1,
						opacity: 0
					}}
					animate={{
						y: pillTargetY,
						width: pillTargetWidth,
						height: pillTargetHeight,
						opacity: isVisible ? 1 : 0,
						scaleX: 1,
						scaleY: 1,
						borderTopLeftRadius: isPill ? pillRadius : 0,
						borderTopRightRadius: isPill ? pillRadius : 0,
						borderBottomLeftRadius: pillRadius,
						borderBottomRightRadius: pillRadius,
						filter: isVisible ? "blur(0px)" : "blur(8px)",
						pointerEvents: isVisible ? "auto" : "none"
					}}
					onClick={(e) => {
						e.stopPropagation();
						if (isOpen) {
							closeNotch();
						} else {
							openNotch(isMusicMode ? "music" : openingPanel());
						}
					}}
					onHoverStart={() => {
						releaseDeferredRef.current = false;
						setIsHovered(true);
						// Hovering the shelf cancels its post-drop auto-close.
						if (gloomMode === "shelf") clearShelfCollapse();
					}}
					onHoverEnd={releaseNotchHover}
					style={{ originY: 0 }}
					transition={{
						width: { type: "spring", stiffness: 360, damping: 30, mass: 0.8 },
						height: { type: "spring", stiffness: 360, damping: 30, mass: 0.8 },
						y: { type: "spring", stiffness: 420, damping: 32, mass: 0.8, restDelta: 0.001 },
						opacity: { duration: 0.18 },
						borderTopLeftRadius: { type: "spring", stiffness: 360, damping: 30, mass: 0.8 },
						borderTopRightRadius: { type: "spring", stiffness: 360, damping: 30, mass: 0.8 },
						borderBottomLeftRadius: { type: "spring", stiffness: 360, damping: 30, mass: 0.8 },
						borderBottomRightRadius: { type: "spring", stiffness: 360, damping: 30, mass: 0.8 },
						default: { type: "spring", stiffness: 360, damping: 30, mass: 0.8 }
					}}
				>
					{showChargingBeams && (
						<div className="charging-beams-container">
							<div className="charging-beam left" />
							<div className="charging-beam right" />
							<div className="charging-halo-glow" />
						</div>
					)}
					<SurfaceShape
						edge="top"
						flare={!isPill && pillTargetY <= 2 ? 14 : 0}
						radius={pillRadius}
						edgeRadius={pillRadius}
					/>
					<NotchFlares visible={!isPill && isVisible && !isHidden && pillTargetY <= 2} />

					<NotchProgressBorder
						progress={
							isMusicMode && (mediaInfo.duration_ms ?? 0) > 0
								? Math.min(100, Math.max(0, ((mediaInfo.position_ms ?? 0) / (mediaInfo.duration_ms || 1)) * 100))
								: (timerSeconds > 0 && lastDurationSeconds > 0 ? ((lastDurationSeconds - timerSeconds) / lastDurationSeconds) * 100 : 0)
						}
						topRadius={isPill ? pillRadius : 0}
						flare={!isPill && pillTargetY <= 2 ? 14 : 0}
						bottomRadius={pillRadius}
						paused={isMusicMode ? !isPlaying : !isTimerRunning}
						finished={isTimerFinished}
					/>

					<AnimatePresence>
						{isMusicMode && settingsAmbienceEnabled && albumArtUrl && isOpen && !isCalendarMode && (
							<motion.div
								className="notch-ambient-glow"
								initial={{ opacity: 0 }}
								animate={{ opacity: 1 }}
								exit={{ opacity: 0 }}
								transition={{ duration: 0.15 }}
							>
								<AnimatePresence mode="wait">
									<motion.img
										key={albumArtUrl}
										src={albumArtUrl}
										alt=""
										draggable={false}
										initial={{ opacity: 0, scale: 1.1 }}
										animate={{ opacity: 1, scale: 1.8 }}
										exit={{ opacity: 0 }}
										transition={{ duration: 0.3 }}
									/>
								</AnimatePresence>
							</motion.div>
						)}
					</AnimatePresence>
					<AnimatePresence mode="wait">
						{isExpanded && (
							<motion.div
								key="gloom-content"
								initial={{ opacity: 0 }}
								animate={{ opacity: 1 }}
								exit={{ opacity: 0 }}
								transition={{ duration: 0.1 }}
								style={{
									width: "100%",
									height: "100%",
									display: "flex",
									flexDirection: "column",
									alignItems: "center",
									position: "relative",
									borderRadius: "inherit"
								}}
							>
								{/* Segmented Island Navigation Bar with Active Badges */}
								{isOpen && (
									<IslandNavBar
										activeTab={activeIslandTab}
										onTabChange={(tab) => {
											setActiveIslandTab(tab);
											setConnectionsDrilldown("none");
										}}
										isMusicPlaying={mediaInfo.has_media && isPlaying}
										timerActive={timerSeconds > 0}
										timerSeconds={timerSeconds}
										productivityCount={shelfItems.length}
									/>
								)}

								{/* Faster Waiting Transition Area */}
								<AnimatePresence mode="wait">
									{isOpen ? (
										<motion.div
											key="island-expanded-view"
											style={{
												width: "100%",
												display: "flex",
												flexDirection: "column",
												alignItems: "center"
											}}
										>
											{activeIslandTab === "media" && (
												<motion.div
													key="tab-media"
													className="expanded-music-container"
													initial={{ opacity: 0, scale: 0.98 }}
													animate={{ opacity: 1, scale: 1 }}
													exit={{ opacity: 0, scale: 0.98, transition: { duration: 0.1 } }}
													transition={{ type: "spring", stiffness: 500, damping: 30 }}
													style={{ width: "100%" }}
												>
													{mediaInfo.has_media ? (
														<CompactMediaPlayer
															layout={mediaLayout}
															mediaInfo={mediaInfo}
															albumArtUrl={albumArtUrl}
															albumArtKey={albumArtKey}
															isPlaying={isPlaying}
															volume={displayVolume}
															volumeExpanded={mediaLayout === "compact" ? compactVolumeExpanded : classicVolumeExpanded}
															onVolumeExpandedChange={mediaLayout === "compact" ? setCompactVolumeExpanded : setClassicVolumeExpanded}
															lyricsExpanded={lyricsExpanded}
															onLyricsExpandedChange={setLyricsExpanded}
															onTogglePlayPause={togglePlayPause}
															onVolumeChange={handleVolumeChange}
															prevFront={prevFront}
															prevBack={prevBack}
															nextFront={nextFront}
															nextBack={nextBack}
															onAnimatePrev={animatePrev}
															onAnimateNext={animateNext}
															onLayoutChange={(layout) => {
																setMediaLayout(layout);
																localStorage.setItem("gloom-media-layout", layout);
																invoke("save_setting", {
																	key: "gloom-media-layout",
																	value: layout
																}).catch(console.error);
																window.dispatchEvent(
																	new CustomEvent("settings-changed", {
																		detail: { key: "media-layout", value: layout }
																	})
																);
															}}
														/>
													) : (
														<div className="apple-no-media-card">
															<MusicNoteIcon size={36} />
															<div className="no-media-title">No Media Playing</div>
															<p className="no-media-desc">Play tracks in Spotify, Apple Music, or your browser</p>
														</div>
													)}
												</motion.div>
											)}

											{activeIslandTab === "focus" && (
												<motion.div
													key="tab-focus"
													className="calendar-timer-content split-view"
													onClick={(e) => e.stopPropagation()}
													initial={{ opacity: 0 }}
													animate={{ opacity: 1 }}
													exit={{ opacity: 0, filter: "blur(4px)", transition: { duration: 0.1 } }}
													transition={{ type: "spring", stiffness: 400, damping: 30 }}
													style={{ width: "100%" }}
												>
													{settingsCalendarEnabled && (
														<div className="calendar-column">
															<Calendar />
														</div>
													)}
													<div className="timer-column" style={{ width: settingsCalendarEnabled ? undefined : "100%" }}>
														<NotchClock
															timerEnabled={true}
															stopwatchEnabled={true}
															solo={!settingsCalendarEnabled}
															timerSeconds={timerSeconds}
															isTimerRunning={isTimerRunning}
															formatTimerTime={formatTimerTime}
															onStartTimer={startTimer}
															onToggleTimer={handlePrimaryTimerAction}
															onResetTimer={resetTimer}
															stopwatch={stopwatch}
														/>
													</div>
												</motion.div>
											)}

											{activeIslandTab === "controls" && (
												<motion.div
													key="tab-controls"
													initial={{ opacity: 0 }}
													animate={{ opacity: 1 }}
													exit={{ opacity: 0, filter: "blur(4px)", transition: { duration: 0.1 } }}
													transition={{ type: "spring", stiffness: 400, damping: 30 }}
													style={{ width: "100%", height: "100%" }}
													onClick={(e) => e.stopPropagation()}
												>
													{connectionsDrilldown !== "none" ? (
														<ConnectionsView
															kind={connectionsDrilldown}
															onBack={() => setConnectionsDrilldown("none")}
															onOpenWindowsSettings={() => invoke("open_settings_window").catch(() => {})}
															onPinChange={setNotchPinned}
														/>
													) : (
														<div className="command-center-content-minimal">
															{/* Pills Grid */}
															<div className="cc-pills-grid">
																{/* Wi-Fi Pill */}
																<div
																	className={`cc-pill-tile ${wifiEnabled ? "active" : ""}`}
																	onClick={(e) => {
																		e.stopPropagation();
																		toggleWifi();
																	}}
																	onContextMenu={handleWifiRightClick}
																	title="Left-click to toggle, Right-click for networks"
																>
																	<div className="cc-pill-icon-wrapper">
																		<WifiIcon enabled={wifiEnabled} connected={wifiConnected} />
																	</div>
																	<div className="cc-pill-info">
																		<span className="cc-pill-title">Wi-Fi</span>
																		<span className="cc-pill-status">
																			{wifiStatus === null
																				? "…"
																				: !wifiEnabled
																					? "Off"
																					: wifiConnected
																						? (wifiStatus?.ssid ?? "Connected")
																						: "Not connected"}
																		</span>
																	</div>
																	<button
																		className="cc-pill-more"
																		aria-label="Wi-Fi networks"
																		title="Wi-Fi networks"
																		onClick={(e) => {
																			e.stopPropagation();
																			setConnectionsDrilldown("wifi");
																		}}
																	>
																		<ChevronRight size={14} />
																	</button>
																</div>

																{/* Platform Pill: Power on Linux, Dock Mode on Windows */}
																{isLinux ? (
																	<div
																		className="cc-pill-tile"
																		onClick={() => openBatterySaverSettings()}
																		title="Battery / Power Status"
																	>
																		<div className="cc-pill-icon-wrapper">
																			<BatterySaverIcon />
																		</div>
																		<div className="cc-pill-info">
																			<span className="cc-pill-title">Power</span>
																			<span className="cc-pill-status">
																				{batteryLevel !== null
																					? `${Math.round(batteryLevel)}%${isCharging ? " ⚡" : ""}`
																					: "AC Power"}
																			</span>
																		</div>
																	</div>
																) : (
																	<div
																		className={`cc-pill-tile ${dockMode === "fixed" ? "active" : ""}`}
																		onClick={toggleDockModeSetting}
																		title="Cycle dock mode: Fixed / Smart / Peek"
																	>
																		<div className="cc-pill-icon-wrapper">
																			<DockIcon />
																		</div>
																		<div className="cc-pill-info">
																			<span className="cc-pill-title">Dock Mode</span>
																			<span className="cc-pill-status">
																				{dockMode === "fixed"
																					? "Fixed"
																					: dockMode === "smart"
																						? "Smart"
																						: "Peek"}
																			</span>
																		</div>
																	</div>
																)}

																{/* Bluetooth Pill */}
																<div
																	className={`cc-pill-tile ${bluetoothEnabled ? "active" : ""}`}
																	onClick={(e) => {
																		e.stopPropagation();
																		toggleBluetooth();
																	}}
																	onContextMenu={handleBluetoothRightClick}
																	title="Left-click to toggle, Right-click for devices"
																>
																	<div className="cc-pill-icon-wrapper">
																		<BluetoothIcon />
																	</div>
																	<div className="cc-pill-info">
																		<span className="cc-pill-title">Bluetooth</span>
																		<span className="cc-pill-status">
																			{!bluetoothEnabled
																				? "Off"
																				: btConnectedNames.length === 1
																					? btConnectedNames[0]
																					: btConnectedNames.length > 1
																						? `${btConnectedNames.length} connected`
																						: "On"}
																		</span>
																	</div>
																	<button
																		className="cc-pill-more"
																		aria-label="Bluetooth devices"
																		title="Bluetooth devices"
																		onClick={(e) => {
																			e.stopPropagation();
																			setConnectionsDrilldown("bluetooth");
																		}}
																	>
																		<ChevronRight size={14} />
																	</button>
																</div>

																{/* Audio Outputs Pill */}
																<div
																	className="cc-pill-tile"
																	onClick={(e) => {
																		e.stopPropagation();
																		setConnectionsDrilldown("audio");
																	}}
																	title="Audio outputs and per-app volumes"
																>
																	<div className="cc-pill-icon-wrapper">
																		<VolumeLowIcon />
																	</div>
																	<div className="cc-pill-info">
																		<span className="cc-pill-title">Audio</span>
																		<span className="cc-pill-status">Outputs & Apps</span>
																	</div>
																	<button
																		className="cc-pill-more"
																		aria-label="Audio devices"
																		title="Audio devices"
																		onClick={(e) => {
																			e.stopPropagation();
																			setConnectionsDrilldown("audio");
																		}}
																	>
																		<ChevronRight size={14} />
																	</button>
																</div>

																{/* Notch Mode Pill */}
																<div
																	className={`cc-pill-tile ${notchMode === "fixed" ? "active" : ""}`}
																	onClick={toggleNotchModeSetting}
																	title="Cycle notch mode: Fixed / Smart / Peek"
																>
																	<div className="cc-pill-icon-wrapper">
																		<NotchIcon />
																	</div>
																	<div className="cc-pill-info">
																		<span className="cc-pill-title">Notch Mode</span>
																		<span className="cc-pill-status">
																			{notchMode === "fixed"
																				? "Fixed"
																				: notchMode === "smart"
																					? "Smart"
																					: "Peek"}
																		</span>
																	</div>
																</div>
															</div>

															{/* Circular Actions Row */}
															<div className="cc-circular-actions-row">
																<button
																	className={`cc-circular-btn ${dndActive ? "active" : ""}`}
																	onClick={(e) => {
																		e.stopPropagation();
																		setDndActive((prev) => !prev);
																	}}
																	title={`Focus / DND: ${dndActive ? "On" : "Off"}`}
																>
																	<MoonIcon />
																</button>
																<button
																	className={`cc-circular-btn ${batterySaverEnabled ? "active" : ""}`}
																	onClick={(e) => {
																		e.stopPropagation();
																		openBatterySaverSettings();
																	}}
																	title={`Energy Saver: ${batterySaverEnabled ? "On" : "Off"} — Click to open Settings`}
																>
																	<BatterySaverIcon />
																</button>
																<button
																	className="cc-circular-btn"
																	onClick={(e) => {
																		e.stopPropagation();
																		openSystemTray(e);
																	}}
																	title="System Tray"
																>
																	<TrayIcon />
																</button>
																<button
																	className="cc-circular-btn"
																	onClick={(e) => {
																		e.stopPropagation();
																		invoke("open_notification_center");
																	}}
																	title="Notification Center"
																>
																	<BellIcon />
																</button>
																<button
																	className="cc-circular-btn"
																	onClick={(e) => {
																		e.stopPropagation();
																		openSettingsWindow();
																	}}
																	title="GLOOM Settings"
																>
																	<SettingsIcon />
																</button>
																<button
																	className="cc-circular-btn"
																	onClick={(e) => {
																		e.stopPropagation();
																		invoke("open_system_action", { action: "task-manager" });
																	}}
																	title="Task Manager"
																>
																	<Cpu size={14} />
																</button>
																<button
																	className="cc-circular-btn"
																	onClick={(e) => {
																		e.stopPropagation();
																		invoke("open_system_action", { action: "file-explorer" });
																	}}
																	title="File Explorer"
																>
																	<Folder size={14} />
																</button>
																<button
																	className="cc-circular-btn"
																	onClick={(e) => {
																		e.stopPropagation();
																		invoke("open_system_action", { action: "lock-screen" });
																	}}
																	title="Lock PC"
																>
																	<Lock size={14} />
																</button>
																<button
																	className="cc-circular-btn"
																	onClick={(e) => {
																		e.stopPropagation();
																		invoke("restart_gloom");
																	}}
																	title="Restart GLOOM"
																>
																	<ReloadIcon />
																</button>
															</div>

															{/* Classic Sliders Area */}
															<div className="cc-classic-sliders-area">
																{/* Volume Slider */}
																<div className="cc-classic-slider-row">
																	<div className="cc-classic-slider-label">
																		<VolumeLowIcon style={{ opacity: 0.5 }} />
																		<span>Volume</span>
																	</div>
																	<div className="cc-classic-slider-track">
																		<input
																			type="range"
																			min="0"
																			max="1"
																			step="0.01"
																			value={displayVolume}
																			onChange={(e) => handleVolumeChange(parseFloat(e.target.value))}
																			onPointerDown={(e) => e.stopPropagation()}
																			onClick={(e) => e.stopPropagation()}
																			className="cc-classic-input"
																		/>
																		<div
																			className="cc-classic-fill"
																			style={{ width: `${displayVolume * 100}%` }}
																		/>
																	</div>
																	<span className="cc-classic-percentage">
																		{Math.round(displayVolume * 100)}%
																	</span>
																</div>

																{/* Brightness Slider */}
																<div className="cc-classic-slider-row">
																	<div className="cc-classic-slider-label">
																		<BrightnessLowIcon />
																		<span>Brightness</span>
																	</div>
																	<div className="cc-classic-slider-track">
																		<input
																			type="range"
																			min="0"
																			max="100"
																			step="1"
																			value={currentBrightness}
																			onChange={(e) => handleBrightnessChange(parseInt(e.target.value))}
																			onPointerDown={(e) => e.stopPropagation()}
																			onClick={(e) => e.stopPropagation()}
																			className="cc-classic-input"
																		/>
																		<div
																			className="cc-classic-fill brightness"
																			style={{ width: `${currentBrightness}%` }}
																		/>
																	</div>
																	<span className="cc-classic-percentage">
																		{currentBrightness}%
																	</span>
																</div>
															</div>
														</div>
													)}
												</motion.div>
											)}

											{activeIslandTab === "productivity" && (
												<motion.div
													key="tab-productivity"
													initial={{ opacity: 0 }}
													animate={{ opacity: 1 }}
													exit={{ opacity: 0, filter: "blur(4px)", transition: { duration: 0.1 } }}
													transition={{ type: "spring", stiffness: 400, damping: 30 }}
													style={{ width: "100%", height: "100%" }}
												>
													<ProductivityDrawer
														shelfItems={shelfItems}
														shelfThumbs={shelfThumbs}
														shelfNotice={shelfNotice}
														shelfOverflowing={shelfOverflowing}
														shelfGridRef={shelfGridRef}
														measureShelfOverflow={measureShelfOverflow}
														shelfNewIdsRef={shelfNewIdsRef}
														handleShelfPointerDown={handleShelfPointerDown}
														handleShelfPointerMove={handleShelfPointerMove}
														handleShelfTileClick={handleShelfTileClick}
														removeShelfItem={removeShelfItem}
														handleShelfWheel={handleShelfWheel}
													/>
												</motion.div>
											)}

											{activeIslandTab === "system" && (
												<motion.div
													key="tab-system"
													initial={{ opacity: 0 }}
													animate={{ opacity: 1 }}
													exit={{ opacity: 0, filter: "blur(4px)", transition: { duration: 0.1 } }}
													transition={{ type: "spring", stiffness: 400, damping: 30 }}
													style={{ width: "100%", height: "100%" }}
													onClick={(e) => e.stopPropagation()}
												>
													<SystemMonitorCard />
												</motion.div>
											)}
										</motion.div>
									) : (
										<motion.div
											key="standard-view-group"
											initial={{ opacity: 0, y: -5 }}
											animate={{ opacity: 1, y: 0 }}
											exit={{ opacity: 0, y: 5, transition: { duration: 0.1 } }}
											transition={{ duration: 0.2 }}
											style={{ width: "100%" }}
										>
											<div className="main-row">
												<AnimatePresence mode="wait">
													{(showPowerPulse || showLowBatteryPulse || showUpdatePulse) &&
													!isHovered ? (
														showUpdatePulse ? (
															<motion.div
																key="update-pulse-view"
																initial={{ opacity: 0, scale: 0.95, filter: "blur(4px)" }}
																animate={{ opacity: 1, scale: 1, filter: "blur(0px)" }}
																exit={{ opacity: 0, scale: 1.05, filter: "blur(4px)" }}
																className="power-pulse-content"
															>
																<GreenDownArrowIcon />
																<span className="label" style={{ color: "#32D74B" }}>
																	Update Available
																</span>
															</motion.div>
														) : (
															<motion.div
																key="pulse-view"
																initial={{ opacity: 0, scale: 0.95, filter: "blur(4px)" }}
																animate={{ opacity: 1, scale: 1, filter: "blur(0px)" }}
																exit={{ opacity: 0, scale: 1.05, filter: "blur(4px)" }}
																className="power-pulse-content"
															>
																{updateAvailable && <GreenDownArrowIcon />}
																<BatteryIcon
																	charging={isCharging}
																	level={batteryLevel}
																	threshold={lowBatteryThreshold}
																/>
																<span
																	className="label"
																	style={{ color: showLowBatteryPulse ? "#FF453A" : "inherit" }}
																>
																	{showLowBatteryPulse
																		? "Low Battery"
																		: isCharging
																			? "Charging"
																			: "On Battery"}{" "}
																	• {batteryLevel}%
																</span>
															</motion.div>
														)
													) : (
														<motion.div
															key="standard-view"
															className="main-row-inner"
															initial={{ opacity: 0 }}
															animate={{ opacity: 1 }}
															exit={{ opacity: 0 }}
														>
															{/* Left: music on hover while a paused session remains, otherwise status widgets */}
															<div className="side-content left">
																{showCompactMusicIcons && settingsVisualizerEnabled ? (
																	<AnimatePresence>
																		{settingsVisualizerEnabled && (
																			<motion.div
																				key="visualizer"
																				initial={{ scale: 0.8, opacity: 0 }}
																				animate={{ scale: 1, opacity: 1 }}
																				exit={{ scale: 0.8, opacity: 0 }}
																			>
																				<Visualizer isPlaying={isPlaying} />
																			</motion.div>
																		)}
																	</AnimatePresence>
																) : !showCompactMusicIcons && isHovered && statusWidgets.left.length > 0 ? (
																	<motion.div
																		key="left-widgets"
																		ref={leftWidgetsRef}
																		className="passive-features-group"
																		initial={{ opacity: 0 }}
																		animate={{ opacity: 1 }}
																		exit={{ opacity: 0 }}
																		transition={{ duration: 0.2 }}
																	>
																		{statusWidgets.left.map(renderStatusWidget)}
																	</motion.div>
																) : null}
															</div>

															{/* Center - Time (always visible) */}
															<div className="time-center" ref={timeCenterRef}>
																<AnimatePresence>
																	{showPanelArrows && (
																		<motion.button
																			key="panel-prev"
																			type="button"
																			className="panel-arrow prev"
																			aria-label="Previous panel"
																			initial={{ opacity: 0, x: 4 }}
																			animate={{ opacity: 1, x: 0 }}
																			exit={{ opacity: 0, x: 4 }}
																			transition={{ duration: 0.15 }}
																			onClick={(e) => {
																				e.stopPropagation();
																				cyclePanel(-1);
																			}}
																		>
																			<ChevronLeft size={12} strokeWidth={2.5} />
																		</motion.button>
																	)}
																</AnimatePresence>
																<div className="time-pill-center">
																	<div className="time-flip-container" onClick={toggleCalendarMode}>
																		<AnimatePresence initial={false}>
																			{timerSeconds > 0 || isTimerFinished ? (
																				<motion.span
																					key="timer"
																					className={`time compact-timer ${isTimerFinished ? "timer-finished" : ""}`}
																					initial={{ rotateX: -90, opacity: 0 }}
																					animate={{ rotateX: 0, opacity: 1 }}
																					exit={{ rotateX: 90, opacity: 0 }}
																					transition={{ type: "spring", stiffness: 600, damping: 30 }}
																				>
																					{isTimerFinished && (
																						<BellRing
																							size={13}
																							strokeWidth={2.5}
																							className="timer-bell"
																						/>
																					)}
																					{formatTimerTime(timerSeconds)}
																				</motion.span>
																			) : (
																				<motion.span
																					key="clock"
																					className="time"
																					initial={{ rotateX: -90, opacity: 0 }}
																					animate={{ rotateX: 0, opacity: 1 }}
																					exit={{ rotateX: 90, opacity: 0 }}
																					transition={{ type: "spring", stiffness: 600, damping: 30 }}
																				>
																					{time}
																				</motion.span>
																			)}
																		</AnimatePresence>
																	</div>
																</div>
																<AnimatePresence>
																	{showPanelArrows && (
																		<motion.button
																			key="panel-next"
																			type="button"
																			className="panel-arrow next"
																			aria-label="Next panel"
																			initial={{ opacity: 0, x: -4 }}
																			animate={{ opacity: 1, x: 0 }}
																			exit={{ opacity: 0, x: -4 }}
																			transition={{ duration: 0.15 }}
																			onClick={(e) => {
																				e.stopPropagation();
																				cyclePanel(1);
																			}}
																		>
																			<ChevronRight size={12} strokeWidth={2.5} />
																		</motion.button>
																	)}
																</AnimatePresence>
																{updateAvailable && showUpdateIndicator && (
																	<div className="update-dot" />
																)}
															</div>

															{/* Right: album art on a retained music session, otherwise status widgets */}
															<div className="side-content right">
																{showCompactMusicIcons && (settingsAlbumArtEnabled || showMusicOnStatusHover) ? (
																	<AnimatePresence mode="wait">
																		<motion.div
																			key="album-art"
																			className="album-art-glow-wrapper"
																			initial={{ opacity: 0, scale: 0.8 }}
																			animate={{ opacity: 1, scale: 1 }}
																			exit={{ opacity: 0, y: -20, scale: 0.8, filter: "blur(8px)" }}
																			transition={{ duration: 0.12 }}
																		>
																			{settingsAlbumArtEnabled && albumArtUrl && settingsCompactGlowEnabled && (
																				<img
																					src={albumArtUrl}
																					alt=""
																					className="album-art-glow-bg"
																					draggable={false}
																				/>
																			)}
																			<button
																				className={`album-art album-art-activity${!isPlaying ? " paused" : ""}`}
																				aria-label="Open music"
																				onClick={(e) => {
																					e.stopPropagation();
																					openNotch("music");
																				}}
																				onContextMenu={(e) => {
																					e.preventDefault();
																					e.stopPropagation();
																					skipPrevious();
																				}}
																			>
																				<div className="album-art-inner">
																					<AnimatePresence mode="wait" initial={false}>
																						{settingsAlbumArtEnabled && albumArtUrl ? (
																							<motion.img
																								key={`compact-art-${albumArtKey}`}
																								src={albumArtUrl}
																								alt="Art"
																								draggable={false}
																								initial={{ rotateY: 90, opacity: 0 }}
																								animate={{ rotateY: 0, opacity: 1 }}
																								exit={{ rotateY: -90, opacity: 0 }}
																								transition={{ duration: 0.25, ease: "easeInOut" }}
																								style={{
																									width: "100%",
																									height: "100%",
																									objectFit: "cover"
																								}}
																							/>
																						) : (
																							<motion.div
																								key="compact-placeholder"
																								className="album-art-placeholder"
																								initial={{ rotateY: 90, opacity: 0 }}
																								animate={{ rotateY: 0, opacity: 1 }}
																								exit={{ rotateY: -90, opacity: 0 }}
																								transition={{ duration: 0.25, ease: "easeInOut" }}
																							>
																								<MusicNoteIcon className="music-placeholder-svg-small" />
																							</motion.div>
																						)}
																					</AnimatePresence>
																				</div>
																			</button>
																		</motion.div>
																	</AnimatePresence>
																) : !showCompactMusicIcons && isHovered && statusWidgets.right.length > 0 ? (
																	<motion.div
																		key="right-widgets"
																		ref={rightWidgetsRef}
																		className="passive-features-group"
																		initial={{ opacity: 0 }}
																		animate={{ opacity: 1 }}
																		exit={{ opacity: 0 }}
																		transition={{ duration: 0.2 }}
																	>
																		{statusWidgets.right.map(renderStatusWidget)}
																	</motion.div>
																) : null}
															</div>
														</motion.div>
													)}
												</AnimatePresence>
											</div>
										</motion.div>
									)}
								</AnimatePresence>

								{/* Announcement Card */}
								<AnimatePresence>
									{gloomMode === "announcement" && announcement && !announcementDismissed && (
										<motion.div
											className={`announcement-content severity-${announcement.severity}`}
											onClick={(e) => e.stopPropagation()}
											initial={{ opacity: 0 }}
											animate={{ opacity: 1 }}
											exit={{
												opacity: 0,
												filter: "blur(4px)",
												transition: { duration: 0.1 }
											}}
											transition={{ type: "spring", stiffness: 400, damping: 30 }}
										>
											<div className="announcement-header">
												<div className="announcement-heading">
													<Megaphone size={14} className="announcement-icon" />
													<span className="announcement-title">{announcement.title}</span>
												</div>
												<button
													className="announcement-close"
													onClick={dismissAnnouncement}
													title="Dismiss"
												>
													<X size={13} strokeWidth={2.2} />
												</button>
											</div>
											{announcement.body && (
												<p className="announcement-body">{announcement.body}</p>
											)}
											{announcement.url && (
												<button
													className="announcement-link"
													onClick={() => openUrl(announcement.url!)}
												>
													Learn more
												</button>
											)}
										</motion.div>
									)}
								</AnimatePresence>

							</motion.div>
						)}
					</AnimatePresence>
				</motion.div>

				{/* Interactive Feature Tour */}
				{tourActive && (
					<NotchTour
						step={tourStep}
						done={tourDone}
						dockEnabled={dockEnabled}
						onStep={(next) => {
							setTourStep(next);
							setTourDone(false);
						}}
						onClose={() => setTourActive(false)}
						onAction={(action) => {
							if (action === "control-center") {
								setActiveIslandTab("controls");
							} else if (action === "settings") {
								openSettingsWindow();
							}
						}}
					/>
				)}
				{/* The shelf's items, as a bubble on the left of the collapsed pill */}
				<AnimatePresence onExitComplete={() => reportNotchRect.current()}>
					{showShelfBubble && (
						<motion.button
							key="shelf-bubble"
							ref={shelfBubbleRef}
							type="button"
							className={`activity-bubble shelf-bubble ${isHovered ? "expanded" : ""}`}
							aria-label={`Open shelf: ${shelfItems.length} item${shelfItems.length === 1 ? "" : "s"}`}
							// Anchored at the pill's centre, offset past its left edge.
							style={{ right: "50%" }}
							initial={{ opacity: 0, scale: 0.4, x: 28, marginRight: getDynamicWidth() / 2 + 8 }}
							animate={{ opacity: 1, scale: 1, x: 0, marginRight: getDynamicWidth() / 2 + 8 }}
							exit={{ opacity: 0, scale: 0.4, x: 28 }}
							transition={{
								type: "spring",
								stiffness: 400,
								damping: 31,
								opacity: { duration: 0.15 }
							}}
							onAnimationComplete={() => reportNotchRect.current()}
							onClick={(e) => {
								e.stopPropagation();
								setIsHovered(true);
								openNotch("shelf");
							}}
						>
							<Inbox size={12} strokeWidth={2.4} />
							<span>{shelfItems.length}</span>
						</motion.button>
					)}
				</AnimatePresence>
				{/* Music in its own bubble while a timer holds the collapsed pill */}
				<AnimatePresence onExitComplete={() => reportNotchRect.current()}>
					{showMusicBubble && (
						<motion.button
							key="music-bubble"
							ref={musicBubbleRef}
							type="button"
							className={`activity-bubble ${isHovered ? "expanded" : ""}`}
							aria-label={mediaInfo.title ? `Open music: ${mediaInfo.title}` : "Open music"}
							// Anchored at the pill's centre, offset past its right edge.
							style={{ left: "50%" }}
							initial={{ opacity: 0, scale: 0.4, x: -28, marginLeft: getDynamicWidth() / 2 + 8 }}
							animate={{ opacity: 1, scale: 1, x: 0, marginLeft: getDynamicWidth() / 2 + 8 }}
							exit={{ opacity: 0, scale: 0.4, x: -28 }}
							transition={{
								type: "spring",
								stiffness: 400,
								damping: 31,
								opacity: { duration: 0.15 }
							}}
							onAnimationComplete={() => reportNotchRect.current()}
							onClick={(e) => {
								e.stopPropagation();
								setIsHovered(true);
								openNotch("music");
							}}
						>
							{settingsAlbumArtEnabled && albumArtUrl ? (
								<img src={albumArtUrl} alt="" draggable={false} />
							) : (
								<Music size={14} strokeWidth={2.2} />
							)}
						</motion.button>
					)}
				</AnimatePresence>
				{/* Active Browser Download Live Activity Bubble */}
				<AnimatePresence onExitComplete={() => reportNotchRect.current()}>
					{showDownloadBubble && (
						<motion.button
							key="download-bubble"
							ref={downloadBubbleRef}
							type="button"
							className={`activity-bubble download-bubble ${isHovered ? "expanded" : ""}`}
							aria-label={`Downloading: ${liveActivities[0]?.title ?? ""}`}
							style={{ left: "50%" }}
							initial={{
								opacity: 0,
								scale: 0.4,
								x: -28,
								marginLeft: getDynamicWidth() / 2 + (showMusicBubble ? 48 : 8)
							}}
							animate={{
								opacity: 1,
								scale: 1,
								x: 0,
								marginLeft: getDynamicWidth() / 2 + (showMusicBubble ? 48 : 8)
							}}
							exit={{ opacity: 0, scale: 0.4, x: -28 }}
							transition={{
								type: "spring",
								stiffness: 400,
								damping: 31,
								opacity: { duration: 0.15 }
							}}
							onAnimationComplete={() => reportNotchRect.current()}
							onClick={(e) => {
								e.stopPropagation();
								playTactileSound("snap");
								invoke("open_system_action", { action: "file-explorer" }).catch(() => {});
							}}
						>
							<Download size={13} strokeWidth={2.4} className="download-bubble-icon" />
							<span>{liveActivities[0]?.detail ?? "Downloading"}</span>
						</motion.button>
					)}
				</AnimatePresence>

				{/* Floating Live Activity Banner for expanded notch view */}
				{isOpen && liveActivities.length > 0 && (
					<div
						className="live-activity-banner-anchor"
						style={{
							position: "absolute",
							top: getDynamicHeight() + 10,
							left: "50%",
							transform: "translateX(-50%)",
							zIndex: 10
						}}
					>
						<LiveActivityBanner
							activities={liveActivities}
							onOpenDownloads={() => invoke("open_system_action", { action: "file-explorer" }).catch(() => {})}
							onDismiss={(id) => setLiveActivities((prev) => prev.filter((a) => a.id !== id))}
						/>
					</div>
				)}
			</div>
		</div>
	);
}

function Calendar() {
	const [date] = useState(new Date());

	const daysInMonth = (year: number, month: number) => new Date(year, month + 1, 0).getDate();
	const firstDayOfMonth = (year: number, month: number) => new Date(year, month, 1).getDay();

	const currentMonth = date.getMonth();
	const currentYear = date.getFullYear();
	const monthName = date.toLocaleString("default", { month: "long" });

	const totalDays = daysInMonth(currentYear, currentMonth);
	const startDay = firstDayOfMonth(currentYear, currentMonth);
	const days = [];

	// Padding for start of month
	for (let i = 0; i < startDay; i++) {
		days.push(<div key={`empty-${i}`} className="calendar-day empty" />);
	}

	// Actual days
	const today = new Date().getDate();
	const isCurrentMonth =
		new Date().getMonth() === currentMonth && new Date().getFullYear() === currentYear;

	for (let i = 1; i <= totalDays; i++) {
		days.push(
			<div key={i} className={`calendar-day ${isCurrentMonth && i === today ? "today" : ""}`}>
				{i}
			</div>
		);
	}

	return (
		<div className="calendar-container">
			<div className="calendar-header">
				<span className="month-year">
					{monthName} {currentYear}
				</span>
				<span className="calendar-month-progress" title={`Day ${today} of ${totalDays} (${Math.round((today / totalDays) * 100)}%)`}>
					{Math.round((today / totalDays) * 100)}%
				</span>
			</div>
			<div className="calendar-grid">
				{["S", "M", "T", "W", "T", "F", "S"].map((d, i) => (
					<div key={`${d}-${i}`} className="day-name">
						{d}
					</div>
				))}
				{days}
			</div>
		</div>
	);
}

function BluetoothIcon() {
	return (
		<svg
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d="M6.5 6.5l11 11L12 23V1l5.5 5.5-11 11" />
		</svg>
	);
}

function SettingsIcon() {
	return (
		<svg
			width="14"
			height="14"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
			style={{ opacity: 0.9 }}
		>
			<circle cx="12" cy="12" r="3" />
			<path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
		</svg>
	);
}

function BrightnessLowIcon() {
	return (
		<svg
			width="12"
			height="12"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2"
			strokeLinecap="round"
			strokeLinejoin="round"
			style={{ opacity: 0.5 }}
		>
			<circle cx="12" cy="12" r="5" fill="currentColor" />
		</svg>
	);
}

function MoonIcon() {
	return (
		<svg
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
		</svg>
	);
}

function DockIcon() {
	return (
		<svg
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<rect x="2" y="14" width="20" height="8" rx="2" />
			<line x1="6" y1="18" x2="6.01" y2="18" strokeWidth="3.5" strokeLinecap="round" />
			<line x1="10" y1="18" x2="10.01" y2="18" strokeWidth="3.5" strokeLinecap="round" />
			<line x1="14" y1="18" x2="14.01" y2="18" strokeWidth="3.5" strokeLinecap="round" />
			<line x1="18" y1="18" x2="18.01" y2="18" strokeWidth="3.5" strokeLinecap="round" />
		</svg>
	);
}

function NotchIcon() {
	return (
		<svg
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d="M4 3h16a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" />
			<path d="M9 9v4a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2V9" />
		</svg>
	);
}

function BellIcon() {
	return (
		<svg
			width="14"
			height="14"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9z" />
			<path d="M13.73 21a2 2 0 0 1-3.46 0" />
		</svg>
	);
}

function ReloadIcon() {
	return (
		<svg
			width="14"
			height="14"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
		</svg>
	);
}

function BatterySaverIcon() {
	return (
		<svg
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2.5"
			strokeLinecap="round"
			strokeLinejoin="round"
		>
			<rect x="2" y="7" width="16" height="10" rx="2" />
			<path d="M22 11v2" />
			<path d="M6 12h4l2-3v6l-2-3H6" />
		</svg>
	);
}

export default App;
