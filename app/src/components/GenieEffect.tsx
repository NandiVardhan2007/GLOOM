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

export interface GenieEffectProps {
	imageSrc: string;
	windowRect: WindowRect;
	dockTarget: GenieTarget;
	direction: "minimize" | "restore";
	durationMs?: number;
	onComplete?: () => void;
}

/**
 * macOS-style Genie Effect for GLOOM Dock.
 * Mathematically deforms a window into a fluid cubic-bezier funnel
 * terminating at the active dock icon.
 */
export const GenieEffect: React.FC<GenieEffectProps> = ({
	imageSrc,
	windowRect,
	dockTarget,
	direction,
	durationMs = 340,
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

		const SLICES = 50; // Number of horizontal deformation slices
		const targetW = dockTarget.width || 48;
		const targetX = dockTarget.x;
		const targetY = dockTarget.y;

		const srcCenter = windowRect.x + windowRect.width / 2;
		const srcTop = windowRect.y;
		const srcW = windowRect.width;
		const srcH = windowRect.height;

		const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);
		const easeInCubic = (t: number) => t * t * t;

		const render = (now: number) => {
			if (!startTime) startTime = now;
			const elapsed = now - startTime;
			const rawProgress = Math.min(elapsed / durationMs, 1);

			// Progress goes 0 -> 1 for minimize, 1 -> 0 for restore
			const p = direction === "minimize" ? rawProgress : 1 - rawProgress;

			ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

			if (rawProgress >= 1) {
				if (direction === "restore") {
					// Draw final static frame at restore end
					ctx.drawImage(img, windowRect.x, windowRect.y, srcW, srcH);
				}
				onComplete?.();
				return;
			}

			// Render the slices from top (0) to bottom (1)
			for (let i = 0; i < SLICES; i++) {
				const v = i / SLICES; // Vertical fraction 0.0 to 1.0
				const nextV = (i + 1) / SLICES;

				// In macOS Genie:
				// Bottom slices lead the suck-in animation, top slices lag behind.
				const leadFactor = Math.pow(v, 1.4);
				const sliceT = Math.max(0, Math.min(1, p * (1 + 0.35 * leadFactor) - 0.35 * (1 - leadFactor)));
				const easedT = direction === "minimize" ? easeInCubic(sliceT) : easeOutCubic(sliceT);

				// Compute vertical Y position
				// Bottom reaches targetY, top collapses down as p -> 1
				const startY = srcTop + v * srcH;
				const currentY = startY + (targetY - startY) * easedT;

				const nextStartY = srcTop + nextV * srcH;
				const nextCurrentY = nextStartY + (targetY - nextStartY) * easedT;
				const sliceHeight = Math.max(1, nextCurrentY - currentY);

				// Compute horizontal center X via cubic bezier curve towards dock icon
				const spineT = Math.pow(sliceT, 1.2);
				const currentCenterX = srcCenter + (targetX - srcCenter) * spineT;

				// Compute width with signature genie waist pinch:
				// Pinch is strongest in the lower middle section, funneling to targetW
				const pinchWave = Math.sin(Math.PI * Math.pow(v, 0.7));
				const pinchAmount = easedT * (0.6 + 0.4 * pinchWave);
				const currentW = Math.max(
					targetW * (1 - p * 0.5),
					srcW * (1 - pinchAmount) + targetW * pinchAmount
				);

				const currentX = currentCenterX - currentW / 2;

				// Source image slice coordinates
				const sy = v * img.height;
				const sh = (nextV - v) * img.height;

				// Opacity & subtle blur fade near final collapse
				ctx.globalAlpha = Math.max(0.1, 1 - Math.pow(p, 4) * 0.9);

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
