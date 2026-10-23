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

export type GenieStyle = "zorin" | "windhawk" | "suck";

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
 * Zorin OS & Compiz Magic Lamp Genie Effect for GLOOM Dock.
 * Mathematically models a fluid 3D cubic-bezier mesh deformation that
 * gracefully funnels and suctions windows into the active dock icon.
 *
 * Features:
 * - 120-slice deformation mesh for sub-pixel anti-aliased rendering.
 * - Dynamic cubic Bézier trajectory spine connecting source center to dock icon.
 * - Authentic Compiz liquid lag propagation and signature waist pinch.
 * - Volumetric lighting / specular highlight along the curvature.
 * - High-DPI canvas rendering with seamless subpixel overlapping.
 */
export const GenieEffect: React.FC<GenieEffectProps> = ({
	imageSrc,
	windowRect,
	dockTarget,
	direction,
	durationMs = 400,
	onComplete
}) => {
	const canvasRef = useRef<HTMLCanvasElement>(null);

	useEffect(() => {
		const canvas = canvasRef.current;
		if (!canvas) return;
		const ctx = canvas.getContext("2d", { alpha: true });
		if (!ctx) return;

		const img = new Image();
		img.src = imageSrc;

		let animationFrameId: number;
		let startTime: number | null = null;

		const dpr = window.devicePixelRatio || 1;
		canvas.width = Math.round(window.innerWidth * dpr);
		canvas.height = Math.round(window.innerHeight * dpr);
		ctx.scale(dpr, dpr);

		// 120 horizontal mesh slices for glass-smooth curve deformation
		const SLICES = 120;
		const targetW = dockTarget.width || 48;
		const targetX = dockTarget.x;
		const targetY = dockTarget.y;

		const srcCenter = windowRect.x + windowRect.width / 2;
		const srcTop = windowRect.y;
		const srcW = windowRect.width;
		const srcH = windowRect.height;

		// Cubic Bézier interpolation: B(t) = (1-t)^3*p0 + 3(1-t)^2*t*p1 + 3(1-t)*t^2*p2 + t^3*p3
		const bezier1D = (p0: number, p1: number, p2: number, p3: number, t: number): number => {
			const u = 1 - t;
			return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
		};

		// Fluid Compiz / Zorin OS ease timing
		const easeMinimize = (t: number): number => {
			// Gentle start, fluid liquid flow, crisp funnel into dock
			return t < 0.5
				? 4 * t * t * t
				: 1 - Math.pow(-2 * t + 2, 3) / 2;
		};

		const easeRestore = (t: number): number => {
			// Fluid fountain bloom out of dock with slight elastic settle
			const c4 = (2 * Math.PI) / 3;
			return t === 0 ? 0 : t === 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
		};

		const render = (now: number) => {
			if (!startTime) startTime = now;
			const elapsed = now - startTime;
			const rawT = Math.min(elapsed / durationMs, 1);

			// Progress: 0 (unminimized) -> 1 (fully in dock)
			const progress = direction === "minimize" ? rawT : 1 - rawT;

			ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

			if (rawT >= 1) {
				if (direction === "restore") {
					ctx.drawImage(img, windowRect.x, windowRect.y, srcW, srcH);
				}
				onComplete?.();
				return;
			}

			ctx.imageSmoothingEnabled = true;
			ctx.imageSmoothingQuality = "high";

			// Render slices from top (v=0) to bottom (v=1)
			for (let i = 0; i < SLICES; i++) {
				const v = i / SLICES; // Vertical fraction: 0.0 (top) to 1.0 (bottom)
				const nextV = (i + 1) / SLICES;

				// In Zorin OS Compiz Magic Lamp:
				// Slices propagate wave-like. The bottom leads towards the dock target,
				// pulling the rest of the window in like water down a drain.
				const lag = 0.44; // Bottom-to-top wave drag
				const sliceRawP = (progress - (1 - v) * lag) / (1 - lag);
				const sliceP = Math.max(0, Math.min(1, sliceRawP));
				const localT = direction === "minimize" ? easeMinimize(sliceP) : 1 - easeRestore(1 - sliceP);

				// Compute vertical Y position
				const startY = srcTop + v * srcH;
				const nextStartY = srcTop + nextV * srcH;

				// Bezier Control points for smooth vertical S-curve trajectory
				const totalDY = Math.abs(targetY - srcTop);
				const cp1Y = startY + (targetY > startY ? 0.42 : -0.42) * totalDY;
				const cp2Y = targetY - (targetY > startY ? 0.35 : -0.35) * totalDY;

				const currentY = bezier1D(startY, cp1Y, cp2Y, targetY, localT);
				const nextCurrentY = bezier1D(nextStartY, cp1Y, cp2Y, targetY, localT);

				// Subpixel height with 0.8px overlap to ensure seamless, solid surface
				const sliceHeight = Math.max(1.2, nextCurrentY - currentY + 0.8);

				// Compute horizontal center X via cubic curve towards dock icon
				const cp1X = srcCenter;
				const cp2X = targetX;
				const currentCenterX = bezier1D(srcCenter, cp1X, cp2X, targetX, localT);

				// Compiz Magic Lamp width deformation:
				// Forms an elegant funnel with a signature waist pinch in the lower-middle
				const pinchWave = Math.sin(Math.PI * Math.pow(v, 0.78));
				const pinchAmount = localT * (0.62 + 0.38 * pinchWave);
				const currentW = Math.max(
					targetW * (1 - progress * 0.3),
					srcW * (1 - pinchAmount) + targetW * pinchAmount
				);

				const currentX = currentCenterX - currentW / 2;

				// Source image slice mapping
				const sy = v * img.height;
				const sh = Math.max(1, (nextV - v) * img.height);

				// Fade out gently as window completes suction into dock
				ctx.globalAlpha = Math.max(0.04, 1 - Math.pow(progress, 4.0) * 0.96);

				// Draw transformed horizontal slice
				ctx.drawImage(img, 0, sy, img.width, sh, currentX, currentY, currentW, sliceHeight);

				// Volumetric specular sheen along the curve (Compiz lamp highlight)
				if (localT > 0.05 && localT < 0.92) {
					const sheenAlpha = 0.08 * Math.sin(Math.PI * localT) * (1 - Math.abs(v - 0.5) * 1.4);
					if (sheenAlpha > 0.005) {
						ctx.fillStyle = `rgba(255, 255, 255, ${sheenAlpha.toFixed(3)})`;
						ctx.fillRect(currentX, currentY, currentW, sliceHeight);
					}
				}
			}

			animationFrameId = requestAnimationFrame(render);
		};

		img.onload = () => {
			animationFrameId = requestAnimationFrame(render);
		};

		return () => {
			if (animationFrameId) cancelAnimationFrame(animationFrameId);
		};
	}, [imageSrc, windowRect, dockTarget, direction, durationMs, onComplete]);

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
