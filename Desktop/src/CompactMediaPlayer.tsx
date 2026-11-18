import { useState, useEffect, useCallback, useRef } from "react";
import { motion, AnimatePresence, useAnimation } from "framer-motion";
import { invoke } from "@tauri-apps/api/core";
import { Visualizer } from "./App";
import { playTactileSound } from "./utils/audioFeedback.ts";
import { extractDominantPalette, DEFAULT_PALETTE, DominantPalette } from "./utils/colorExtractor.ts";
import { useLyrics, LyricLine } from "./hooks/useLyrics";
import {
	PlayIcon,
	PauseIcon,
	SkipBackIcon,
	SkipForwardIcon,
	MusicNoteIcon,
	SpeakerIcon,
	HeadphonesIcon,
	VolumeLowIcon,
	VolumeHighIcon,
	LyricsIcon
} from "./icons";

export interface MediaInfo {
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

export interface CompactMediaPlayerProps {
	layout?: "classic" | "compact";
	mediaInfo: MediaInfo;
	albumArtUrl: string | null;
	albumArtKey: number;
	isPlaying: boolean;
	volume: number;
	volumeExpanded: boolean;
	onVolumeExpandedChange: (expanded: boolean) => void;
	lyricsExpanded?: boolean;
	onLyricsExpandedChange?: (expanded: boolean) => void;
	onTogglePlayPause: () => void;
	onVolumeChange: (vol: number) => void;
	prevFront: ReturnType<typeof useAnimation>;
	prevBack: ReturnType<typeof useAnimation>;
	nextFront: ReturnType<typeof useAnimation>;
	nextBack: ReturnType<typeof useAnimation>;
	onAnimatePrev: () => void;
	onAnimateNext: () => void;
	onLayoutChange: (layout: "classic" | "compact") => void;
}

const MARQUEE_SPEED = 30;
const MARQUEE_MIN_DURATION = 5;

export function TitleMarquee({ title }: { title: string }) {
	const textRef = useRef<HTMLSpanElement>(null);
	const containerRef = useRef<HTMLDivElement>(null);
	const [isOverflowing, setIsOverflowing] = useState(false);
	const [scrollDistance, setScrollDistance] = useState(0);
	const [scrollDuration, setScrollDuration] = useState(8);

	useEffect(() => {
		const check = () => {
			if (textRef.current && containerRef.current) {
				const textWidth = textRef.current.scrollWidth;
				const containerWidth = containerRef.current.clientWidth;
				const overflow = textWidth > containerWidth;
				setIsOverflowing(overflow);
				if (overflow) {
					const distance = textWidth - containerWidth;
					setScrollDistance(distance);
					setScrollDuration(Math.max(distance / MARQUEE_SPEED, MARQUEE_MIN_DURATION));
				}
			}
		};
		check();
		const observer = new ResizeObserver(check);
		if (containerRef.current) observer.observe(containerRef.current);
		return () => observer.disconnect();
	}, [title]);

	return (
		<div ref={containerRef} className="cmp-marquee-wrap">
			<span
				ref={textRef}
				className={`cmp-title ${isOverflowing ? "cmp-marquee" : ""}`}
				style={
					{
						"--scroll-distance": isOverflowing ? `-${scrollDistance}px` : undefined,
						"--scroll-duration": `${scrollDuration}s`
					} as React.CSSProperties
				}
			>
				{title}
			</span>
		</div>
	);
}

function formatTime(ms: number): string {
	if (ms <= 0 || !isFinite(ms)) return "0:00";
	const totalSeconds = Math.floor(ms / 1000);
	const minutes = Math.floor(totalSeconds / 60);
	const seconds = totalSeconds % 60;
	return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

function formatRemaining(elapsed: number, duration: number): string {
	if (!duration || duration <= 0 || !isFinite(duration)) return "-0:00";
	const remaining = Math.max(0, duration - elapsed);
	const totalSeconds = Math.floor(remaining / 1000);
	const minutes = Math.floor(totalSeconds / 60);
	const seconds = totalSeconds % 60;
	return `-${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function getCleanSourceAppName(sourceApp?: string): string {
	if (!sourceApp) return "Music";
	const lower = sourceApp.toLowerCase();
	if (lower.includes("spotify")) return "Spotify";
	if (lower.includes("applemusic") || lower.includes("itunes")) return "Apple Music";
	if (lower.includes("youtubemusic") || lower.includes("youtube")) return "YouTube Music";
	if (lower.includes("cider")) return "Cider";
	if (lower.includes("chrome")) return "Chrome";
	if (lower.includes("edge") || lower.includes("msedge")) return "Edge";
	if (lower.includes("brave")) return "Brave";
	if (lower.includes("firefox")) return "Firefox";
	return sourceApp.replace(/\.exe$/i, "").trim() || "Music";
}

/**
 * Apple Scrubber Bar with direct pointer manipulation (setPointerCapture),
 * responsive 1:1 tracking, and instant seek.
 */
function AppleScrubberBar({
	positionMs,
	durationMs,
	seekEnabled,
	onSeek
}: {
	positionMs: number;
	durationMs: number;
	seekEnabled: boolean;
	onSeek: (posMs: number) => void;
}) {
	const trackRef = useRef<HTMLDivElement>(null);
	const [isScrubbing, setIsScrubbing] = useState(false);
	const [scrubPosition, setScrubPosition] = useState(positionMs);

	const activePosition = isScrubbing ? scrubPosition : positionMs;
	const progressPercent = durationMs > 0 ? Math.min(100, Math.max(0, (activePosition / durationMs) * 100)) : 0;

	const calculatePositionFromPointer = (e: React.PointerEvent<HTMLDivElement>) => {
		if (!trackRef.current || durationMs <= 0) return 0;
		const rect = trackRef.current.getBoundingClientRect();
		const clientX = e.clientX;
		const fraction = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
		return fraction * durationMs;
	};

	const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
		if (!seekEnabled || durationMs <= 0) return;
		e.stopPropagation();
		e.preventDefault();
		try {
			e.currentTarget.setPointerCapture(e.pointerId);
		} catch {}
		setIsScrubbing(true);
		const newPos = calculatePositionFromPointer(e);
		setScrubPosition(newPos);
	};

	const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
		if (!isScrubbing) return;
		e.stopPropagation();
		const newPos = calculatePositionFromPointer(e);
		setScrubPosition(newPos);
	};

	const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
		if (!isScrubbing) return;
		e.stopPropagation();
		try {
			e.currentTarget.releasePointerCapture(e.pointerId);
		} catch {}
		const finalPos = calculatePositionFromPointer(e);
		setIsScrubbing(false);
		onSeek(finalPos);
	};

	const handlePointerCancel = (e: React.PointerEvent<HTMLDivElement>) => {
		if (!isScrubbing) return;
		try {
			e.currentTarget.releasePointerCapture(e.pointerId);
		} catch {}
		setIsScrubbing(false);
	};

	return (
		<div className="apple-scrubber-container">
			<span className="apple-scrubber-time">{formatTime(activePosition)}</span>
			<div
				ref={trackRef}
				className={`apple-scrubber-track-area ${isScrubbing ? "scrubbing" : ""}`}
				onPointerDown={handlePointerDown}
				onPointerMove={handlePointerMove}
				onPointerUp={handlePointerUp}
				onPointerCancel={handlePointerCancel}
			>
				<div className="apple-scrubber-rail">
					<div
						className="apple-scrubber-fill"
						style={{ width: `${progressPercent}%` }}
					/>
					<div
						className="apple-scrubber-thumb"
						style={{ left: `${progressPercent}%` }}
					/>
				</div>
			</div>
			<span className="apple-scrubber-time">{formatRemaining(activePosition, durationMs)}</span>
		</div>
	);
}

/**
 * Apple Music Karaoke Synced Lyrics Sheet
 * Features smooth auto-scrolling, bold white glow on active singing line,
 * and click-to-seek jump.
 */
function AppleKaraokeSheet({
	lyrics,
	activeLyricIndex,
	activeLyric,
	isLoading,
	hasLyrics,
	isStreamingFromApp,
	title,
	artist,
	subtitle,
	sourceAppName,
	albumArtUrl,
	palette,
	isPlaying,
	onSeekToLine,
	onClose,
	onRetry
}: {
	lyrics: LyricLine[];
	activeLyricIndex: number;
	activeLyric?: string | null;
	isLoading: boolean;
	hasLyrics: boolean;
	isStreamingFromApp?: boolean;
	title: string;
	artist: string;
	subtitle?: string;
	sourceAppName: string;
	albumArtUrl: string | null;
	palette: DominantPalette;
	isPlaying: boolean;
	onSeekToLine: (timeMs: number) => void;
	onClose: () => void;
	onRetry: () => void;
}) {
	const activeLineRef = useRef<HTMLDivElement>(null);
	const listContainerRef = useRef<HTMLDivElement>(null);

	// Smoothly scroll the singing line to the center with spring-like precision
	useEffect(() => {
		if (activeLineRef.current) {
			activeLineRef.current.scrollIntoView({
				behavior: "smooth",
				block: "center"
			});
		}
	}, [activeLyricIndex]);

	return (
		<motion.div
			className="apple-karaoke-sheet"
			initial={{ opacity: 0, y: 10 }}
			animate={{ opacity: 1, y: 0 }}
			exit={{ opacity: 0, y: 10 }}
			transition={{ type: "spring", stiffness: 450, damping: 28 }}
		>
			<div className="apple-karaoke-header">
				<div className="apple-karaoke-meta">
					<div className="apple-karaoke-title-row">
						{albumArtUrl && (
							<div className="apple-karaoke-art-mini">
								<img src={albumArtUrl} alt="" draggable={false} />
							</div>
						)}
						<span className="apple-karaoke-title">{title}</span>
						{subtitle && subtitle !== title && (
							<span className="apple-karaoke-sub-badge">{subtitle}</span>
						)}
						<span className="apple-karaoke-live-badge">
							<span className="apple-live-pulse-dot" />
							{isStreamingFromApp ? `Live • ${sourceAppName}` : sourceAppName}
						</span>
					</div>
					<span className="apple-karaoke-artist">{artist}</span>
				</div>
				<div className="apple-karaoke-header-actions">
					<Visualizer isPlaying={isPlaying} bars={3} height={14} />
					<motion.button
						className="apple-karaoke-close-btn"
						onClick={(e) => {
							e.stopPropagation();
							onClose();
						}}
						whileHover={{ scale: 1.08 }}
						whileTap={{ scale: 0.92 }}
						title="Close Lyrics"
					>
						<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round">
							<line x1="18" y1="6" x2="6" y2="18" />
							<line x1="6" y1="6" x2="18" y2="18" />
						</svg>
					</motion.button>
				</div>
			</div>

			<div ref={listContainerRef} className="apple-karaoke-list">
				{isLoading ? (
					<div className="apple-karaoke-loading-wrap">
						<div className="apple-karaoke-skeleton-line short shimmer" />
						<div className="apple-karaoke-skeleton-line long shimmer" />
						<div className="apple-karaoke-skeleton-line medium shimmer" />
						<div className="apple-karaoke-skeleton-line long shimmer" />
						<div className="apple-karaoke-skeleton-line short shimmer" />
						<span className="apple-karaoke-loading-text">Syncing live lyrics…</span>
					</div>
				) : !hasLyrics ? (
					<div className="apple-karaoke-status">
						<div className="apple-karaoke-status-icon-wrap">
							<Visualizer isPlaying={isPlaying} bars={5} height={22} />
						</div>
						<span className="apple-karaoke-status-title">Now Playing</span>
						<span className="apple-karaoke-status-query">{title}</span>
						<motion.button
							className="apple-karaoke-retry-btn"
							onClick={(e) => {
								e.stopPropagation();
								playTactileSound("snap");
								onRetry();
							}}
							whileHover={{ scale: 1.05 }}
							whileTap={{ scale: 0.95 }}
							title="Search lyrics online"
						>
							<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: 6 }}>
								<circle cx="11" cy="11" r="8" />
								<line x1="21" y1="21" x2="16.65" y2="16.65" />
							</svg>
							Search Lyrics Online
						</motion.button>
					</div>
				) : lyrics.length === 1 && isStreamingFromApp ? (
					<div className="apple-karaoke-spotlight-wrap">
						<motion.div
							className="apple-karaoke-spotlight-line"
							initial={{ scale: 0.96, opacity: 0 }}
							animate={{ scale: 1, opacity: 1 }}
							key={activeLyric || lyrics[0].text}
							style={{
								textShadow: `0 0 20px ${palette?.primary ? `${palette.primary}cc` : "rgba(255, 255, 255, 0.75)"}, 0 0 40px rgba(255, 255, 255, 0.35)`
							}}
						>
							{activeLyric || lyrics[0].text}
						</motion.div>
						<span className="apple-karaoke-spotlight-hint">
							<span className="apple-live-pulse-dot" />
							Live from {sourceAppName}
						</span>
					</div>
				) : (
					lyrics.map((line, idx) => {
						const dist = Math.abs(idx - activeLyricIndex);
						const isActive = idx === activeLyricIndex;
						const proximityClass = isActive ? "active" : dist === 1 ? "near" : "distant";
						return (
							<motion.div
								key={`${line.timeMs}-${idx}`}
								ref={isActive ? activeLineRef : null}
								className={`apple-karaoke-line ${proximityClass}`}
								style={
									isActive && palette?.primary
										? {
												textShadow: `0 0 16px ${palette.primary}cc, 0 0 32px rgba(255, 255, 255, 0.35)`
										  }
										: undefined
								}
								onClick={(e) => {
									e.stopPropagation();
									playTactileSound("snap");
									onSeekToLine(line.timeMs);
								}}
								whileHover={{ scale: 1.02 }}
								whileTap={{ scale: 0.98 }}
							>
								{line.text}
							</motion.div>
						);
					})
				)}
			</div>
		</motion.div>
	);
}

export function CompactMediaPlayer({
	layout = "classic",
	mediaInfo,
	albumArtUrl,
	albumArtKey,
	isPlaying,
	volume,
	volumeExpanded,
	onVolumeExpandedChange,
	lyricsExpanded = false,
	onLyricsExpandedChange,
	onTogglePlayPause,
	onVolumeChange,
	prevFront,
	prevBack,
	nextFront,
	nextBack,
	onAnimatePrev,
	onAnimateNext,
	onLayoutChange
}: CompactMediaPlayerProps) {
	const positionMs = mediaInfo.position_ms ?? 0;
	const durationMs = mediaInfo.duration_ms ?? 0;
	const seekEnabled = mediaInfo.seek_enabled ?? true;
	const positionUpdatedAt = mediaInfo.position_updated_at ?? 0;

	// Single source of truth: last known backend position + when it was received
	const backendPosRef = useRef(positionMs);
	const backendTimeRef = useRef(positionUpdatedAt || Date.now());
	const isSeekingRef = useRef(false);

	const [palette, setPalette] = useState<DominantPalette>(DEFAULT_PALETTE);

	useEffect(() => {
		if (albumArtUrl) {
			extractDominantPalette(albumArtUrl).then(setPalette).catch(() => {});
		} else {
			setPalette(DEFAULT_PALETTE);
		}
	}, [albumArtUrl]);

	useEffect(() => {
		if (!isSeekingRef.current) {
			backendPosRef.current = positionMs;
			backendTimeRef.current = positionUpdatedAt || Date.now();
		}
	}, [positionMs, positionUpdatedAt]);

	// Live extrapolated position
	const [displayPosition, setDisplayPosition] = useState(() => {
		const elapsed = Date.now() - (positionUpdatedAt || Date.now());
		return Math.min(positionMs + (isPlaying ? elapsed : 0), durationMs || Infinity);
	});

	// Fast 100ms extrapolation for live synced lyrics
	useEffect(() => {
		const tick = () => {
			if (isSeekingRef.current) return;
			const elapsed = isPlaying ? Date.now() - backendTimeRef.current : 0;
			const pos = Math.min(backendPosRef.current + elapsed, durationMs || Infinity);
			setDisplayPosition(pos);
		};
		tick();
		const interval = setInterval(tick, 100);
		return () => clearInterval(interval);
	}, [isPlaying, durationMs]);

	// Live lyrics hook with duration-based precision candidate matching & live player stream
	const {
		lyrics,
		activeLyric,
		activeLyricIndex,
		hasLyrics,
		isLoading,
		isStreamingFromApp,
		cleanTitle,
		cleanArtist,
		subtitle,
		retryFetch
	} = useLyrics(
		mediaInfo.title,
		mediaInfo.artist,
		displayPosition,
		durationMs,
		mediaInfo.subtitle
	);

	const sourceAppName = getCleanSourceAppName(mediaInfo.source_app);

	const [albumHovered, setAlbumHovered] = useState(false);
	const [albumRotation, setAlbumRotation] = useState({ x: 0, y: 0 });
	const albumRef = useRef<HTMLDivElement>(null);

	const handleAlbumPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
		if (albumRef.current) {
			const rect = albumRef.current.getBoundingClientRect();
			const centerX = rect.left + rect.width / 2;
			const centerY = rect.top + rect.height / 2;
			const deltaX = e.clientX - centerX;
			const deltaY = e.clientY - centerY;
			const maxDistance = Math.sqrt(rect.width * rect.width + rect.height * rect.height) / 2 || 1;
			const angleX = (deltaY / maxDistance) * 14;
			const angleY = (deltaX / maxDistance) * -14;
			setAlbumRotation({ x: angleX, y: angleY });
		}
	};

	const handleSeek = useCallback(
		(seekPos: number) => {
			backendPosRef.current = seekPos;
			backendTimeRef.current = Date.now();
			setDisplayPosition(seekPos);
			playTactileSound("tick");
			invoke("media_seek", { positionMs: seekPos }).catch(() => {});
		},
		[]
	);

	const handleSeekToLine = useCallback(
		(timeMs: number) => {
			handleSeek(timeMs);
		},
		[handleSeek]
	);

	const toggleLyrics = useCallback(() => {
		playTactileSound("switch");
		if (onLyricsExpandedChange) {
			onLyricsExpandedChange(!lyricsExpanded);
		}
	}, [lyricsExpanded, onLyricsExpandedChange]);

	// Apple Keyboard direct manipulation (Space = play/pause, ArrowLeft/Right = ±5s, ArrowUp/Down = volume, M = mute, L = lyrics)
	useEffect(() => {
		const handleKeyDown = (e: KeyboardEvent) => {
			const target = e.target as HTMLElement | null;
			if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) {
				return;
			}
			if (e.code === "Space") {
				e.preventDefault();
				onTogglePlayPause();
			} else if (e.code === "ArrowLeft") {
				e.preventDefault();
				handleSeek(Math.max(0, displayPosition - 5000));
			} else if (e.code === "ArrowRight") {
				e.preventDefault();
				handleSeek(Math.min(durationMs || Infinity, displayPosition + 5000));
			} else if (e.code === "ArrowUp") {
				e.preventDefault();
				onVolumeChange(Math.min(1, volume + 0.05));
				playTactileSound("tick");
			} else if (e.code === "ArrowDown") {
				e.preventDefault();
				onVolumeChange(Math.max(0, volume - 0.05));
				playTactileSound("tick");
			} else if (e.code === "KeyL") {
				e.preventDefault();
				toggleLyrics();
			} else if (e.code === "KeyM") {
				e.preventDefault();
				onVolumeChange(volume > 0 ? 0 : 0.5);
				playTactileSound("switch");
			}
		};

		window.addEventListener("keydown", handleKeyDown);
		return () => window.removeEventListener("keydown", handleKeyDown);
	}, [displayPosition, durationMs, handleSeek, onTogglePlayPause, onVolumeChange, volume, toggleLyrics]);

	// If full karaoke lyrics sheet is open, show the dedicated Apple Karaoke view
	if (lyricsExpanded) {
		return (
			<div className="apple-media-container lyrics-open">
				{albumArtUrl && (
					<div
						className={`apple-artwork-aura ${isPlaying ? "playing" : "paused"}`}
						style={{
							backgroundImage: `radial-gradient(circle at 20% 20%, ${palette.primary} 0%, transparent 65%), radial-gradient(circle at 80% 80%, ${palette.secondary} 0%, transparent 65%), url(${albumArtUrl})`
						}}
					/>
				)}
				<AppleKaraokeSheet
					lyrics={lyrics}
					activeLyricIndex={activeLyricIndex}
					activeLyric={activeLyric}
					isLoading={isLoading}
					hasLyrics={hasLyrics}
					isStreamingFromApp={isStreamingFromApp}
					title={cleanTitle || mediaInfo.title}
					artist={cleanArtist || mediaInfo.artist}
					subtitle={subtitle}
					sourceAppName={sourceAppName}
					albumArtUrl={albumArtUrl}
					palette={palette}
					isPlaying={isPlaying}
					onSeekToLine={handleSeekToLine}
					onClose={toggleLyrics}
					onRetry={retryFetch}
				/>
				{durationMs > 0 && (
					<div className="apple-lyrics-bottom-bar">
						<AppleScrubberBar
							positionMs={displayPosition}
							durationMs={durationMs}
							seekEnabled={seekEnabled}
							onSeek={handleSeek}
						/>
					</div>
				)}
			</div>
		);
	}

	// Classic Layout (Side-by-side squircle album art + metadata + scrubber + transport)
	if (layout === "classic") {
		return (
			<div
				className="apple-media-container apple-classic-view"
				onWheel={(e) => {
					if (e.target instanceof HTMLElement && e.target.closest(".apple-karaoke-sheet")) return;
					e.stopPropagation();
					const step = e.deltaY < 0 ? 0.03 : -0.03;
					const newVol = Math.max(0, Math.min(1, volume + step));
					if (newVol !== volume) {
						onVolumeChange(newVol);
						playTactileSound("tick");
					}
				}}
			>
				{/* Ambient Artwork Glow with Dynamic Breathing Bloom */}
				{albumArtUrl && (
					<div
						className={`apple-artwork-aura ${isPlaying ? "playing" : "paused"}`}
						style={{
							backgroundImage: `radial-gradient(circle at 20% 50%, ${palette.primary} 0%, transparent 70%), radial-gradient(circle at 80% 50%, ${palette.secondary} 0%, transparent 65%), url(${albumArtUrl})`
						}}
					/>
				)}

				<div className="apple-classic-main-row">
					{/* Album Artwork Squircle */}
					<div
						ref={albumRef}
						className="apple-album-art-wrap"
						onPointerEnter={() => setAlbumHovered(true)}
						onPointerLeave={() => {
							setAlbumHovered(false);
							setAlbumRotation({ x: 0, y: 0 });
						}}
						onPointerMove={handleAlbumPointerMove}
						onClick={(e) => {
							e.stopPropagation();
							onLayoutChange("compact");
						}}
						title="Click to switch to Compact layout"
						style={{ perspective: "600px" }}
					>
						<motion.div
							className="apple-album-art"
							style={{
								transform: `perspective(600px) rotateX(${albumRotation.x}deg) rotateY(${albumRotation.y}deg) scale(${albumHovered ? 1.05 : 1})`,
								transformStyle: "preserve-3d",
								transition: albumHovered
									? "transform 0.08s ease-out"
									: "transform 0.35s cubic-bezier(0.16, 1, 0.3, 1)"
							}}
							whileTap={{ scale: 0.96 }}
						>
							<AnimatePresence mode="wait" initial={false}>
								{albumArtUrl ? (
									<motion.img
										key={`art-${albumArtKey}`}
										src={albumArtUrl}
										alt="Album Art"
										draggable={false}
										initial={{ opacity: 0, scale: 0.92 }}
										animate={{ opacity: 1, scale: 1 }}
										exit={{ opacity: 0, scale: 0.92 }}
										transition={{ duration: 0.25 }}
									/>
								) : (
									<motion.div
										key="placeholder"
										className="apple-art-placeholder"
										initial={{ opacity: 0 }}
										animate={{ opacity: 1 }}
										exit={{ opacity: 0 }}
									>
										<MusicNoteIcon size={32} />
									</motion.div>
								)}
							</AnimatePresence>
						</motion.div>
					</div>

					{/* Metadata + Scrubber + Controls */}
					<div className="apple-content-col">
						{/* Top Header: Title & Actions (Visualizer + Lyrics toggle) */}
						<div className="apple-header-row">
							<div className="apple-meta-info">
								<div className="apple-title-with-source">
									<TitleMarquee title={cleanTitle || mediaInfo.title} />
									<span className="apple-source-app-chip">
										<span className="apple-source-pulse" />
										{sourceAppName}
									</span>
								</div>
								<span className="apple-artist">
									{cleanArtist || mediaInfo.artist}
									{subtitle ? ` • ${subtitle}` : ""}
								</span>
							</div>
							<div className="apple-compact-right-actions">
								<Visualizer isPlaying={isPlaying} bars={4} height={16} />
								<motion.button
									className={`apple-btn-icon-mini ${lyricsExpanded ? "active" : ""}`}
									onClick={(e) => {
										e.stopPropagation();
										toggleLyrics();
									}}
									whileTap={{ scale: 0.9 }}
									title="Live Synced Lyrics"
								>
									<LyricsIcon size={16} active={lyricsExpanded} />
								</motion.button>
							</div>
						</div>

						{/* Live Synced Lyric Ticker (Apple Music style) */}
						{activeLyric && (
							<motion.div
								className="apple-live-lyric-pill"
								onClick={(e) => {
									e.stopPropagation();
									toggleLyrics();
								}}
								whileHover={{ scale: 1.02 }}
								whileTap={{ scale: 0.97 }}
								title="Click to expand Live Synced Lyrics"
							>
								<span className="apple-lyric-sparkle">♪</span>
								<AnimatePresence mode="wait">
									<motion.span
										key={activeLyric}
										initial={{ opacity: 0, y: 2 }}
										animate={{ opacity: 1, y: 0 }}
										exit={{ opacity: 0, y: -2 }}
										transition={{ duration: 0.18 }}
										className="apple-lyric-text"
									>
										{activeLyric}
									</motion.span>
								</AnimatePresence>
							</motion.div>
						)}

						{/* Scrubber Bar */}
						{durationMs > 0 && (
							<AppleScrubberBar
								positionMs={displayPosition}
								durationMs={durationMs}
								seekEnabled={seekEnabled}
								onSeek={handleSeek}
							/>
						)}

						{/* Controls Row */}
						<div className="apple-controls-row">
							<motion.button
								className={`apple-btn-icon ${volumeExpanded ? "active" : ""}`}
								onClick={(e) => {
									e.stopPropagation();
									onVolumeExpandedChange(!volumeExpanded);
								}}
								whileTap={{ scale: 0.9 }}
								title="Toggle Volume"
							>
								<SpeakerIcon size={20} muted={volume === 0} />
							</motion.button>

							<div className="apple-transport-group">
								<motion.button
									className="apple-btn-transport"
									onClick={(e) => {
										e.stopPropagation();
										onAnimatePrev();
									}}
									whileTap={{ scale: 0.9 }}
									title="Previous"
								>
									<div className="cmp-slide-wrap">
										<motion.div animate={prevBack} className="cmp-slide-layer">
											<SkipBackIcon size={26} />
										</motion.div>
										<motion.div animate={prevFront} className="cmp-slide-layer">
											<SkipBackIcon size={26} />
										</motion.div>
									</div>
								</motion.button>

								<motion.button
									className="apple-btn-playpause"
									onClick={(e) => {
										e.stopPropagation();
										onTogglePlayPause();
									}}
									whileTap={{ scale: 0.93 }}
									title={isPlaying ? "Pause" : "Play"}
								>
									<AnimatePresence mode="wait" initial={false}>
										<motion.div
											key={isPlaying ? "pause" : "play"}
											initial={{ opacity: 0, scale: 0.75 }}
											animate={{ opacity: 1, scale: 1 }}
											exit={{ opacity: 0, scale: 0.75 }}
											transition={{ duration: 0.12 }}
											style={{ display: "flex", alignItems: "center", justifyContent: "center" }}
										>
											{isPlaying ? <PauseIcon size={24} /> : <PlayIcon size={24} />}
										</motion.div>
									</AnimatePresence>
								</motion.button>

								<motion.button
									className="apple-btn-transport"
									onClick={(e) => {
										e.stopPropagation();
										onAnimateNext();
									}}
									whileTap={{ scale: 0.9 }}
									title="Next"
								>
									<div className="cmp-slide-wrap">
										<motion.div animate={nextBack} className="cmp-slide-layer">
											<SkipForwardIcon size={26} />
										</motion.div>
										<motion.div animate={nextFront} className="cmp-slide-layer">
											<SkipForwardIcon size={26} />
										</motion.div>
									</div>
								</motion.button>
							</div>

							<motion.button
								className="apple-btn-icon"
								onClick={(e) => {
									e.stopPropagation();
									invoke("open_sound_settings").catch(() => {});
								}}
								whileTap={{ scale: 0.9 }}
								title="Audio Output Device"
							>
								<HeadphonesIcon size={20} />
							</motion.button>
						</div>
					</div>
				</div>

				{/* Smooth Volume Slider Expansion */}
				<AnimatePresence>
					{volumeExpanded && (
						<motion.div
							className="apple-volume-dropdown"
							initial={{ height: 0, opacity: 0 }}
							animate={{ height: 32, opacity: 1 }}
							exit={{ height: 0, opacity: 0 }}
							transition={{ type: "spring", stiffness: 500, damping: 30 }}
						>
							<VolumeLowIcon size={13} style={{ opacity: 0.5, flexShrink: 0 }} />
							<div className="apple-volume-rail">
								<div className="apple-volume-fill" style={{ width: `${volume * 100}%` }} />
								<input
									type="range"
									className="apple-volume-input"
									min={0}
									max={1}
									step={0.01}
									value={volume}
									onChange={(e) => onVolumeChange(parseFloat(e.target.value))}
									onClick={(e) => e.stopPropagation()}
								/>
							</div>
							<VolumeHighIcon size={14} style={{ opacity: 0.5, flexShrink: 0 }} />
						</motion.div>
					)}
				</AnimatePresence>
			</div>
		);
	}

	// Compact Layout (Vertical stacked Dynamic Island view)
	return (
		<div
			className="apple-media-container apple-compact-view"
			onWheel={(e) => {
				if (e.target instanceof HTMLElement && e.target.closest(".apple-karaoke-sheet")) return;
				e.stopPropagation();
				const step = e.deltaY < 0 ? 0.03 : -0.03;
				const newVol = Math.max(0, Math.min(1, volume + step));
				if (newVol !== volume) {
					onVolumeChange(newVol);
					playTactileSound("tick");
				}
			}}
		>
			{/* Ambient Artwork Glow with Dynamic Breathing Bloom */}
			{albumArtUrl && (
				<div
					className={`apple-artwork-aura ${isPlaying ? "playing" : "paused"}`}
					style={{
						backgroundImage: `radial-gradient(circle at 20% 50%, ${palette.primary} 0%, transparent 70%), radial-gradient(circle at 80% 50%, ${palette.secondary} 0%, transparent 65%), url(${albumArtUrl})`
					}}
				/>
			)}

			{/* Row 1: Mini Squircle Art + Title + Visualizer + Lyrics toggle */}
			<div className="apple-compact-top-row">
				<div
					className="apple-compact-art"
					onClick={(e) => {
						e.stopPropagation();
						onLayoutChange("classic");
					}}
					title="Click to switch to Classic layout"
					style={{
						cursor: "pointer",
						transition: "transform 0.2s cubic-bezier(0.16, 1, 0.3, 1)"
					}}
					onPointerEnter={(e) => (e.currentTarget.style.transform = "scale(1.06)")}
					onPointerLeave={(e) => (e.currentTarget.style.transform = "scale(1)")}
				>
					<AnimatePresence mode="wait" initial={false}>
						{albumArtUrl ? (
							<motion.img
								key={`art-${albumArtKey}`}
								src={albumArtUrl}
								alt="Art"
								draggable={false}
								initial={{ opacity: 0 }}
								animate={{ opacity: 1 }}
								exit={{ opacity: 0 }}
								transition={{ duration: 0.2 }}
							/>
						) : (
							<div className="apple-art-placeholder-mini">
								<MusicNoteIcon size={20} />
							</div>
						)}
					</AnimatePresence>
				</div>

				<div className="apple-compact-meta">
					<div className="apple-title-with-source">
						<TitleMarquee title={cleanTitle || mediaInfo.title} />
						<span className="apple-source-app-chip">
							<span className="apple-source-pulse" />
							{sourceAppName}
						</span>
					</div>
					<span className="apple-artist">
						{cleanArtist || mediaInfo.artist}
						{subtitle ? ` • ${subtitle}` : ""}
					</span>
				</div>

				<div className="apple-compact-right-actions">
					<Visualizer isPlaying={isPlaying} bars={4} height={16} />
					<motion.button
						className={`apple-btn-icon-mini ${lyricsExpanded ? "active" : ""}`}
						onClick={(e) => {
							e.stopPropagation();
							toggleLyrics();
						}}
						whileTap={{ scale: 0.9 }}
						title="Lyrics"
					>
						<LyricsIcon size={16} active={lyricsExpanded} />
					</motion.button>
				</div>
			</div>

			{/* Row 2: Live Synced Lyric Ticker */}
			{activeLyric && (
				<motion.div
					className="apple-live-lyric-pill compact"
					onClick={(e) => {
						e.stopPropagation();
						toggleLyrics();
					}}
					whileHover={{ scale: 1.02 }}
					whileTap={{ scale: 0.97 }}
					title="Click to expand Lyrics"
				>
					<span className="apple-lyric-sparkle">♪</span>
					<AnimatePresence mode="wait">
						<motion.span
							key={activeLyric}
							initial={{ opacity: 0, y: 2 }}
							animate={{ opacity: 1, y: 0 }}
							exit={{ opacity: 0, y: -2 }}
							transition={{ duration: 0.15 }}
							className="apple-lyric-text"
						>
							{activeLyric}
						</motion.span>
					</AnimatePresence>
				</motion.div>
			)}

			{/* Row 3: Scrubber Bar */}
			{durationMs > 0 && (
				<AppleScrubberBar
					positionMs={displayPosition}
					durationMs={durationMs}
					seekEnabled={seekEnabled}
					onSeek={handleSeek}
				/>
			)}

			{/* Row 4: Controls */}
			<div className="apple-controls-row compact">
				<motion.button
					className={`apple-btn-icon ${volumeExpanded ? "active" : ""}`}
					onClick={(e) => {
						e.stopPropagation();
						onVolumeExpandedChange(!volumeExpanded);
					}}
					whileTap={{ scale: 0.9 }}
					title="Volume"
				>
					<SpeakerIcon size={20} muted={volume === 0} />
				</motion.button>

				<div className="apple-transport-group">
					<motion.button
						className="apple-btn-transport"
						onClick={(e) => {
							e.stopPropagation();
							onAnimatePrev();
						}}
						whileTap={{ scale: 0.9 }}
					>
						<div className="cmp-slide-wrap">
							<motion.div animate={prevBack} className="cmp-slide-layer">
								<SkipBackIcon size={26} />
							</motion.div>
							<motion.div animate={prevFront} className="cmp-slide-layer">
								<SkipBackIcon size={26} />
							</motion.div>
						</div>
					</motion.button>

					<motion.button
						className="apple-btn-playpause"
						onClick={(e) => {
							e.stopPropagation();
							onTogglePlayPause();
						}}
						whileTap={{ scale: 0.93 }}
					>
						<AnimatePresence mode="wait" initial={false}>
							<motion.div
								key={isPlaying ? "pause" : "play"}
								initial={{ opacity: 0, scale: 0.75 }}
								animate={{ opacity: 1, scale: 1 }}
								exit={{ opacity: 0, scale: 0.75 }}
								transition={{ duration: 0.12 }}
								style={{ display: "flex", alignItems: "center", justifyContent: "center" }}
							>
								{isPlaying ? <PauseIcon size={24} /> : <PlayIcon size={24} />}
							</motion.div>
						</AnimatePresence>
					</motion.button>

					<motion.button
						className="apple-btn-transport"
						onClick={(e) => {
							e.stopPropagation();
							onAnimateNext();
						}}
						whileTap={{ scale: 0.9 }}
					>
						<div className="cmp-slide-wrap">
							<motion.div animate={nextBack} className="cmp-slide-layer">
								<SkipForwardIcon size={26} />
							</motion.div>
							<motion.div animate={nextFront} className="cmp-slide-layer">
								<SkipForwardIcon size={26} />
							</motion.div>
						</div>
					</motion.button>
				</div>

				<motion.button
					className="apple-btn-icon"
					onClick={(e) => {
						e.stopPropagation();
						invoke("open_sound_settings").catch(() => {});
					}}
					whileTap={{ scale: 0.9 }}
					title="Audio Output Device"
				>
					<HeadphonesIcon size={20} />
				</motion.button>
			</div>

			{/* Volume expansion */}
			<AnimatePresence>
				{volumeExpanded && (
					<motion.div
						className="apple-volume-dropdown"
						initial={{ height: 0, opacity: 0 }}
						animate={{ height: 32, opacity: 1 }}
						exit={{ height: 0, opacity: 0 }}
						transition={{ type: "spring", stiffness: 500, damping: 30 }}
					>
						<VolumeLowIcon size={12} style={{ opacity: 0.5, flexShrink: 0 }} />
						<div className="apple-volume-rail">
							<div className="apple-volume-fill" style={{ width: `${volume * 100}%` }} />
							<input
								type="range"
								className="apple-volume-input"
								min={0}
								max={1}
								step={0.01}
								value={volume}
								onChange={(e) => onVolumeChange(parseFloat(e.target.value))}
								onClick={(e) => e.stopPropagation()}
							/>
						</div>
						<VolumeHighIcon size={14} style={{ opacity: 0.5, flexShrink: 0 }} />
					</motion.div>
				)}
			</AnimatePresence>
		</div>
	);
}
