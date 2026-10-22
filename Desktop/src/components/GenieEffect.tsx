import React, { useEffect, useRef } from "react";

export interface GenieTarget {
	x: number; // dock icon center X
	y: number; // dock icon top Y
	width?: number; // target width at dock (default 48px)
}

export interface WindowRect {
	x: number;
	y: number;
	width: number;
	height: number;
}

export type GenieStyle = "windhawk" | "suck";

export interface GenieEffectProps {
	imageSrc: string;
	windowRect: WindowRect;
	dockTarget: GenieTarget;
	direction: "minimize" | "restore";
	durationMs?: number;
	style?: GenieStyle;
	onComplete?: () => void;
}

/**
 * Pixel-faithful macOS / Windhawk Genie Effect for GLOOM Dock.
 * Mathematically deforms a window into a fluid cubic-bezier funnel
 * terminating at the active dock icon position.
 *
 * Slices the window into 60 horizontal deformation strips, with the bottom
 * leading the suck-in animation, authentic waist pinch, and 60 fps requestAnimationFrame.
 */
export const GenieEffect: React.FC<GenieEffectProps> = ({
	imageSrc,
	windowRect,
	dockTarget,
	direction,
	durationMs = 380,
	style = "windhawk",
	onComplete
}) => {
	const canvasRef = useRef<HTMLCanvasElement>(null);

	useEffect(() => {
		const canvas = canvasRef.current;
		if (!canvas) return;
		const ctx = canvas.getContext("2d");
		if (!ctx) return;

		const img = new Image();
		img.src = imageSrc;

		let animationFrameId: number;
		let startTime: number | null = null;

		const dpr = window.devicePixelRatio || 1;
		canvas.width = window.innerWidth * dpr;
		canvas.height = window.innerHeight * dpr;
		ctx.scale(dpr, dpr);

		const SLICES = 60; // 60 deformation slices for pixel-smooth mesh warp
		const targetW = dockTarget.width || 48;
		const targetX = dockTarget.x;
		const targetY = dockTarget.y;

		const srcCenter = windowRect.x + windowRect.width / 2;
		const srcTop = windowRect.y;
		const srcW = windowRect.width;
		const srcH = windowRect.height;

		const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 2.8);
		const easeInCubic = (t: number) => Math.pow(t, 2.8);

		const render = (now: number) => {
			if (!startTime) startTime = now;
			const elapsed = now - startTime;
			const rawProgress = Math.min(elapsed / durationMs, 1);

			// Progress goes 0 -> 1 for minimize, 1 -> 0 for restore
			const p = direction === "minimize" ? rawProgress : 1 - rawProgress;

			ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

			if (rawProgress >= 1) {
				if (direction === "restore") {
					ctx.drawImage(img, windowRect.x, windowRect.y, srcW, srcH);
				}
				onComplete?.();
				return;
			}

			// Render slices from top (v=0) to bottom (v=1)
			for (let i = 0; i < SLICES; i++) {
				const v = i / SLICES; // Vertical fraction 0.0 (top) to 1.0 (bottom)
				const nextV = (i + 1) / SLICES;

				// In Windhawk / macOS Genie:
				// The bottom slices lead the funnel suck-in, while the top lags behind.
				const leadFactor = style === "windhawk" ? Math.pow(v, 1.35) : Math.pow(Math.abs(v - 0.5) * 2, 1.2);
				const sliceT = Math.max(
					0,
					Math.min(1, p * (1 + 0.38 * leadFactor) - 0.38 * (1 - leadFactor))
				);
				const easedT = direction === "minimize" ? easeInCubic(sliceT) : easeOutCubic(sliceT);

				// Compute vertical Y position
				// Bottom reaches targetY, top collapses down as p -> 1
				const startY = srcTop + v * srcH;
				const currentY = startY + (targetY - startY) * easedT;

				const nextStartY = srcTop + nextV * srcH;
				const nextCurrentY = nextStartY + (targetY - nextStartY) * easedT;
				const sliceHeight = Math.max(1, nextCurrentY - currentY);

				// Compute horizontal center X via cubic curve towards dock icon
				const spineT = Math.pow(sliceT, 1.22);
				const currentCenterX = srcCenter + (targetX - srcCenter) * spineT;

				// Compute width with signature genie waist pinch:
				// Pinch is strongest in the lower middle section, funneling to targetW
				const pinchWave = Math.sin(Math.PI * Math.pow(v, 0.72));
				const pinchAmount = easedT * (0.64 + 0.36 * pinchWave);
				const currentW = Math.max(
					targetW * (1 - p * 0.45),
					srcW * (1 - pinchAmount) + targetW * pinchAmount
				);

				const currentX = currentCenterX - currentW / 2;

				// Source image slice coordinates
				const sy = v * img.height;
				const sh = (nextV - v) * img.height;

				// Opacity & subtle fade near final collapse
				ctx.globalAlpha = Math.max(0.05, 1 - Math.pow(p, 4.5) * 0.95);

				// Draw slice
				ctx.drawImage(img, 0, sy, img.width, sh, currentX, currentY, currentW, sliceHeight);
			}

			animationFrameId = requestAnimationFrame(render);
		};

		img.onload = () => {
			animationFrameId = requestAnimationFrame(render);
		};

		return () => {
			if (animationFrameId) cancelAnimationFrame(animationFrameId);
		};
	}, [imageSrc, windowRect, dockTarget, direction, durationMs, style, onComplete]);

	return (
		<canvas
			ref={canvasRef}
			style={{
				position: "fixed",
				top: 0,
				left: 0,
				width: "100vw",
				height: "100vh",
				pointerEvents: "none",
				zIndex: 99999
			}}
		/>
	);
};
