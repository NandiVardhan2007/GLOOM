import { useState, useEffect, useCallback, useRef } from "react";
import { motion, AnimatePresence, useAnimation } from "framer-motion";
import { invoke } from "@tauri-apps/api/core";
import { Visualizer } from "./App";
import { playTactileSound } from "./utils/audioFeedback.ts";
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
	isLoading,
	hasLyrics,
	title,
	artist,
	onSeekToLine,
	onClose
}: {
	lyrics: LyricLine[];
	activeLyricIndex: number;
	isLoading: boolean;
	hasLyrics: boolean;
	title: string;
	artist: string;
	onSeekToLine: (timeMs: number) => void;
	onClose: () => void;
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
					<span className="apple-karaoke-title">{title}</span>
					<span className="apple-karaoke-artist">{artist}</span>
				</div>
				<motion.button
					className="apple-karaoke-close-btn"
					onClick={(e) => {
						e.stopPropagation();
						onClose();
					}}
					whileTap={{ scale: 0.9 }}
					title="Close Lyrics"
				>
					✕
				</motion.button>
			</div>

			<div ref={listContainerRef} className="apple-karaoke-list">
				{isLoading ? (
					<div className="apple-karaoke-status">
						<span className="apple-karaoke-spinner" />
						<span>Searching synced lyrics...</span>
					</div>
				) : !hasLyrics ? (
					<div className="apple-karaoke-status">
						<MusicNoteIcon size={24} style={{ opacity: 0.4, marginBottom: 6 }} />
						<span>Lyrics not available for this track</span>
					</div>
				) : (
					lyrics.map((line, idx) => {
						const isActive = idx === activeLyricIndex;
						return (
							<motion.div
								key={`${line.timeMs}-${idx}`}
								ref={isActive ? activeLineRef : null}
								className={`apple-karaoke-line ${isActive ? "active" : ""}`}
								onClick={(e) => {
									e.stopPropagation();
									onSeekToLine(line.timeMs);
								}}
								whileHover={{ scale: 1.02 }}
								whileTap={{ scale: 0.97 }}
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

	// Live lyrics hook
	const { lyrics, activeLyric, activeLyricIndex, hasLyrics, isLoading } = useLyrics(
		mediaInfo.title,
		mediaInfo.artist,
		displayPosition
	);

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

	const toggleLyrics = () => {
		playTactileSound("switch");
		if (onLyricsExpandedChange) {
			onLyricsExpandedChange(!lyricsExpanded);
		}
	};

	// If full karaoke lyrics sheet is open, show the dedicated Apple Karaoke view
	if (lyricsExpanded) {
		return (
			<div className="apple-media-container lyrics-open">
				<AppleKaraokeSheet
					lyrics={lyrics}
					activeLyricIndex={activeLyricIndex}
					isLoading={isLoading}
					hasLyrics={hasLyrics}
					title={mediaInfo.title}
					artist={mediaInfo.artist}
					onSeekToLine={handleSeekToLine}
					onClose={toggleLyrics}
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
			<div className="apple-media-container apple-classic-view">
				{/* Ambient Artwork Glow */}
				{albumArtUrl && (
					<div
						className="apple-artwork-aura"
						style={{ backgroundImage: `url(${albumArtUrl})` }}
					/>
				)}

				<div className="apple-classic-main-row">
					{/* Album Artwork Squircle */}
					<div
						className="apple-album-art-wrap"
						onClick={(e) => {
							e.stopPropagation();
							onLayoutChange("compact");
						}}
						title="Click to switch to Compact layout"
					>
						<motion.div
							className="apple-album-art"
							whileHover={{ scale: 1.04 }}
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
								<TitleMarquee title={mediaInfo.title} />
								<span className="apple-artist">{mediaInfo.artist}</span>
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
		<div className="apple-media-container apple-compact-view">
			{/* Row 1: Mini Squircle Art + Title + Visualizer + Lyrics toggle */}
			<div className="apple-compact-top-row">
				<div
					className="apple-compact-art"
					onClick={(e) => {
						e.stopPropagation();
						onLayoutChange("classic");
					}}
					title="Click to switch to Classic layout"
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
					<TitleMarquee title={mediaInfo.title} />
					<span className="apple-artist">{mediaInfo.artist}</span>
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
