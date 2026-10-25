import React, { useState, useEffect, useRef, useMemo, memo } from "react";
import { motion, useAnimationControls, useReducedMotion } from "framer-motion";
import { DOCK_CONFIG, sizeFor, xOffsetFor } from "../dockConfig";

export interface MagnifyingDockItem {
	id: string;
	label: string;
	type: "start" | "task-view" | "extra" | "app";
	icon?: string | null;
	customIconNode?: React.ReactNode;
	isGloomOrSettings?: boolean;
	isRunning?: boolean;
	windowCount?: number;
	isBackground?: boolean;
	onLaunch: () => void;
	onContextMenu?: (e: React.MouseEvent) => void;
	onMiddleClick?: (e: React.MouseEvent) => void;
}

export type DockEntry =
	| { kind: "item"; item: MagnifyingDockItem }
	| { kind: "separator"; id: string };

interface MagnifyingDockProps {
	entries: DockEntry[];
	continuous?: boolean;
	falloffType?: "linear" | "cosine";
	dockPreviewEnabled?: boolean;
	previewData?: {
		id: string;
		previews: { hwnd: number; title: string; image: string }[];
	} | null;
	onClosePreview?: (e: React.MouseEvent, hwnd: number) => void;
	onFocusWindow?: (hwnd: number) => void;
	onHoverChange?: (hoveredId: string | null) => void;
	onRowWidthChange?: (width: number) => void;
	onMouseEnterDock?: () => void;
	onMouseLeaveDock?: () => void;
}

/**
 * WindowDots indicator for active running apps.
 */
function WindowDots({ count, background }: { count: number; background?: boolean }) {
	return (
		<div
			className="window-dots"
			role="img"
			aria-label={background ? "Running in background" : `${count} open windows`}
		>
			{Array.from({ length: background ? 1 : Math.min(Math.max(count, 1), 3) }, (_, i) => (
				<div
					key={i}
					className={`active-indicator${background ? " background-indicator" : ""}`}
				/>
			))}
		</div>
	);
}

/**
 * Visual Dock Icon component.
 * Renders the animated icon, tooltip, and launch bounce.
 */
const VisualDockIcon = memo(function VisualDockIcon({
	item,
	size,
	scaleRatio,
	xOffset,
	isLargeDock,
	showTip,
	showPreview,
	previewData,
	onClosePreview,
	onFocusWindow,
	launchN,
	reducedMotion
}: {
	item: MagnifyingDockItem;
	size: number;
	scaleRatio: number;
	xOffset: number;
	isLargeDock: boolean;
	showTip: boolean;
	showPreview: boolean;
	previewData?: {
		id: string;
		previews: { hwnd: number; title: string; image: string }[];
	} | null;
	onClosePreview?: (e: React.MouseEvent, hwnd: number) => void;
	onFocusWindow?: (hwnd: number) => void;
	launchN: number;
	reducedMotion: boolean;
}) {
	const bounceControls = useAnimationControls();
	const prevLaunchN = useRef(0);

	useEffect(() => {
		if (launchN > prevLaunchN.current) {
			prevLaunchN.current = launchN;
			if (!reducedMotion) {
				bounceControls.start(DOCK_CONFIG.BOUNCE);
			}
		}
	}, [launchN, reducedMotion, bounceControls]);

	const transition = reducedMotion ? DOCK_CONFIG.REDUCED_MOTION : DOCK_CONFIG.SPRING;

	return (
		<motion.div animate={bounceControls} className="dock-icon-bounce-wrapper">
			<motion.div
				className="dock-icon"
				animate={
					isLargeDock
						? { scale: scaleRatio, x: xOffset }
						: { width: size, height: size }
				}
				transition={transition}
				style={
					isLargeDock
						? {
								transformOrigin: "bottom center",
								width: DOCK_CONFIG.BASE,
								height: DOCK_CONFIG.BASE
							}
						: undefined
				}
			>
				<div className="dock-icon-inner">
					{item.customIconNode ? (
						item.customIconNode
					) : item.icon ? (
						<img
							src={item.icon}
							alt={item.label}
							className={item.isGloomOrSettings ? "gloom-icon-img" : ""}
							draggable={false}
						/>
					) : (
						<div className="fallback-icon">{item.label[0] || "?"}</div>
					)}
				</div>

				{/* Tooltip anchored to top edge of icon */}
				{showTip && !showPreview && (
					<span className="dock-tip">{item.label}</span>
				)}

				{/* App Window Preview Tooltip */}
				{showPreview && previewData && (
					<div
						className={`preview-tooltip ${previewData.previews.length > 1 ? "multi" : ""}`}
						onClick={(e) => e.stopPropagation()}
					>
						<div className="preview-items">
							{previewData.previews.map((prev, idx) => (
								<div
									key={prev.hwnd}
									className="preview-item"
									onClick={() => onFocusWindow?.(prev.hwnd)}
								>
									<img src={prev.image} alt={`Preview ${idx}`} />
									<div className="preview-label">{prev.title || item.label}</div>
									<button
										className="preview-close-btn"
										onClick={(e) => {
											e.stopPropagation();
											onClosePreview?.(e, prev.hwnd);
										}}
										title="Close Window"
									>
										<svg
											width="10"
											height="10"
											viewBox="0 0 24 24"
											fill="none"
											stroke="currentColor"
											strokeWidth="3"
											strokeLinecap="round"
										>
											<line x1="18" y1="6" x2="6" y2="18"></line>
											<line x1="6" y1="6" x2="18" y2="18"></line>
										</svg>
									</button>
								</div>
							))}
						</div>
					</div>
				)}
			</motion.div>

			{/* Running indicator dots */}
			{item.isRunning && (
				<WindowDots
					count={item.windowCount ?? 1}
					background={item.isBackground}
				/>
			)}
		</motion.div>
	);
});

/**
 * MagnifyingDock Component.
 *
 * Implements the Apple-style Magnifying Dock with:
 * 1. VISUAL LAYER: Renders bottom-aligned icons that animate width/height with spring physics,
 *    widening the row and growing upward. Pointer-events are disabled.
 * 2. HIT LAYER: Fixed-width slots (width = SLOT = 84px) that never move or resize,
 *    handling hover, focus, click, middle-click, and context menu. Prevents all hover jitter.
 */
export const MagnifyingDock = memo(function MagnifyingDock({
	entries,
	continuous = DOCK_CONFIG.MODE === "continuous",
	falloffType = DOCK_CONFIG.FALLOFF_TYPE,
	dockPreviewEnabled = true,
	previewData = null,
	onClosePreview,
	onFocusWindow,
	onHoverChange,
	onRowWidthChange,
	onMouseEnterDock,
	onMouseLeaveDock
}: MagnifyingDockProps) {
	const shouldReduceMotion = useReducedMotion();
	const reducedMotion = !!shouldReduceMotion;

	// Hover position: null when not hovered, or index / fractional position
	const [pos, setPos] = useState<number | null>(null);
	// Launch bounce tracking: item id -> incrementing count
	const [launch, setLaunch] = useState<{ id: string; n: number } | null>(null);

	const hitContainerRef = useRef<HTMLDivElement>(null);

	// Extract magnifiable items to assign 0-indexed positions
	const magnifiableItems = useMemo(() => {
		return entries.filter(
			(e): e is { kind: "item"; item: MagnifyingDockItem } => e.kind === "item"
		);
	}, [entries]);

	const numMagnifiable = magnifiableItems.length;
	const isLargeDock = numMagnifiable > DOCK_CONFIG.PERF_THRESHOLD;

	// Map each item ID to its magnifiable index
	const itemIndexMap = useMemo(() => {
		const map = new Map<string, number>();
		magnifiableItems.forEach((entry, idx) => {
			map.set(entry.item.id, idx);
		});
		return map;
	}, [magnifiableItems]);

	const config = useMemo(() => {
		return {
			BASE: DOCK_CONFIG.BASE,
			MAX: DOCK_CONFIG.MAX,
			FALLOFF: DOCK_CONFIG.FALLOFF,
			FALLOFF_TYPE: falloffType
		};
	}, [falloffType]);

	// Compute sizes for all items
	const sizes = useMemo(() => {
		return magnifiableItems.map((_, i) => sizeFor(i, pos, config));
	}, [magnifiableItems, pos, config]);

	// Report hovered item id to parent for window preview fetching
	const hoveredIdx = pos === null ? null : Math.round(pos);
	useEffect(() => {
		if (hoveredIdx === null || hoveredIdx < 0 || hoveredIdx >= magnifiableItems.length) {
			onHoverChange?.(null);
		} else {
			const item = magnifiableItems[hoveredIdx]?.item;
			onHoverChange?.(item?.id ?? null);
		}
	}, [hoveredIdx, magnifiableItems, onHoverChange]);

	// Compute total visual row width and notify parent so the dock shelf widens smoothly
	useEffect(() => {
		const separatorCount = entries.filter((e) => e.kind === "separator").length;
		const separatorWidth = separatorCount * 17; // 1px + 16px margins
		let itemsWidth = 0;
		if (isLargeDock) {
			// In large dock, resting width is N * BASE + (N - 1) * GAP
			itemsWidth = numMagnifiable * DOCK_CONFIG.BASE + Math.max(0, numMagnifiable - 1) * DOCK_CONFIG.GAP;
		} else {
			itemsWidth = sizes.reduce((acc, s) => acc + s, 0) + Math.max(0, numMagnifiable - 1) * DOCK_CONFIG.GAP;
		}
		const totalWidth = itemsWidth + separatorWidth;
		onRowWidthChange?.(totalWidth);
	}, [sizes, entries, isLargeDock, numMagnifiable, onRowWidthChange]);

	const handleItemLaunch = (item: MagnifyingDockItem) => {
		setLaunch((prev) => ({
			id: item.id,
			n: (prev?.id === item.id ? prev.n : 0) + 1
		}));
		item.onLaunch();
	};

	const handleHitMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
		if (!continuous || !hitContainerRef.current) return;
		const r = hitContainerRef.current.getBoundingClientRect();
		// Compute fractional index under cursor: p = (cursorX - dockLeft) / SLOT - 0.5
		const p = (e.clientX - r.left) / DOCK_CONFIG.SLOT - 0.5;
		setPos(Math.max(0, Math.min(numMagnifiable - 1, p)));
	};

	return (
		<div
			className="magnifying-dock"
			onMouseEnter={() => onMouseEnterDock?.()}
			onMouseLeave={() => {
				setPos(null);
				onMouseLeaveDock?.();
			}}
		>
			{/* VISUAL LAYER (pointer-events: none) */}
			<div className="dock-visual" aria-hidden="true">
				{entries.map((entry) => {
					if (entry.kind === "separator") {
						return (
							<div
								key={entry.id}
								className="dock-app-divider"
								role="separator"
								aria-orientation="vertical"
							/>
						);
					}

					const item = entry.item;
					const idx = itemIndexMap.get(item.id) ?? 0;
					const size = sizes[idx] ?? DOCK_CONFIG.BASE;
					const scaleRatio = size / DOCK_CONFIG.BASE;
					const xOffset = isLargeDock ? xOffsetFor(idx, sizes) : 0;
					const isHovered = hoveredIdx === idx;
					const hasPreview =
						dockPreviewEnabled &&
						previewData &&
						previewData.id === item.id &&
						isHovered;

					return (
						<VisualDockIcon
							key={item.id}
							item={item}
							size={size}
							scaleRatio={scaleRatio}
							xOffset={xOffset}
							isLargeDock={isLargeDock}
							showTip={isHovered}
							showPreview={!!hasPreview}
							previewData={previewData}
							onClosePreview={onClosePreview}
							onFocusWindow={onFocusWindow}
							launchN={launch?.id === item.id ? launch.n : 0}
							reducedMotion={reducedMotion}
						/>
					);
				})}
			</div>

			{/* HIT LAYER (fixed slots, pointer-events: auto on slots) */}
			<div
				ref={hitContainerRef}
				className="dock-hit"
				onMouseMove={handleHitMouseMove}
			>
				{entries.map((entry) => {
					if (entry.kind === "separator") {
						return (
							<div
								key={`hit-${entry.id}`}
								className="dock-hit-separator"
								style={{ width: 17 }}
							/>
						);
					}

					const item = entry.item;
					const idx = itemIndexMap.get(item.id) ?? 0;

					return (
						<div
							key={`hit-${item.id}`}
							role="button"
							tabIndex={0}
							aria-label={item.label}
							className="dock-hit-slot"
							style={{ width: DOCK_CONFIG.SLOT }}
							onMouseEnter={!continuous ? () => setPos(idx) : undefined}
							onFocus={() => setPos(idx)}
							onBlur={() => setPos(null)}
							onClick={(e) => {
								e.stopPropagation();
								handleItemLaunch(item);
							}}
							onKeyDown={(e) => {
								if (e.key === "Enter" || e.key === " ") {
									e.preventDefault();
									handleItemLaunch(item);
								}
							}}
							onContextMenu={(e) => item.onContextMenu?.(e)}
							onMouseDown={(e) => {
								if (e.button === 1) e.preventDefault();
							}}
							onAuxClick={(e) => item.onMiddleClick?.(e)}
						/>
					);
				})}
			</div>
		</div>
	);
});
