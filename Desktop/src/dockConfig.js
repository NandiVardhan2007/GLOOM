/**
 * Apple-style Magnifying Dock Configuration & Tuning Constants.
 * Reference: macOS dock measured from screenshots.
 */

export const DOCK_CONFIG = {
	// Resting icon size in pixels (calibrated to GLOOM's native dock size at 100% scale)
	BASE: 42,
	// Maximum magnified icon size on hover in pixels (~1.71x)
	MAX: 72,
	// Falloff radius in icons
	FALLOFF: 3,
	// Gap between icons at rest (12px matches classic GLOOM dock gap)
	GAP: 12,
	// Hit slot size (icon base + gap share)
	get SLOT() {
		return this.BASE + this.GAP; // 54
	},
	// Spring physics for icon magnification (Apple HIG: responsive, slight overshoot, no linear tweens)
	SPRING: {
		type: "spring",
		stiffness: 400,
		damping: 28,
		mass: 0.6
	},
	// Reduced-motion fallback (no overshoot, subtle easeOut)
	REDUCED_MOTION: {
		type: "tween",
		duration: 0.15,
		ease: "easeOut"
	},
	// Launch bounce keyframes: 0 -> -28 -> 0 -> -10 -> 0 over ~0.6s
	BOUNCE: {
		y: [0, -28, 0, -10, 0],
		transition: {
			duration: 0.6,
			times: [0, 0.3, 0.6, 0.8, 1],
			ease: "easeOut"
		}
	},
	// Mode: 'discrete' (default macOS slot feel) or 'continuous' (fractional pointer X)
	MODE: "discrete",
	// Falloff curve: 'linear' (default) or 'cosine'
	FALLOFF_TYPE: "linear",
	// Corner radius ratio (macOS squircle is ~22% of icon size)
	CORNER_RADIUS_RATIO: 0.22,
	// Performance threshold: switch from width/height animation to scale+xOffset when count > 15
	PERF_THRESHOLD: 15
};

/**
 * Calculates the magnified size for an icon at index `i` given hover position `pos`.
 */
export function sizeFor(i, pos, config = DOCK_CONFIG) {
	if (pos === null) return config.BASE;
	const d = Math.abs(i - pos);
	if (d >= config.FALLOFF) return config.BASE;

	let t = 0;
	if (config.FALLOFF_TYPE === "cosine") {
		t = Math.cos((Math.PI * d) / (2 * config.FALLOFF));
	} else {
		t = Math.max(0, 1 - d / config.FALLOFF);
	}

	return config.BASE + (config.MAX - config.BASE) * t;
}

/**
 * For performance mode (N > 15):
 * Computes the X offset needed when icons use transform: scale() with transformOrigin: "bottom center"
 * so that neighbors are pushed apart just like flexbox layout shift.
 */
export function xOffsetFor(i, sizes, config = DOCK_CONFIG) {
	const n = sizes.length;
	if (n <= 1) return 0;

	// Resting row width & resting center of icon i
	const restingRowWidth = n * config.BASE + (n - 1) * config.GAP;
	const restingCenterI = i * config.SLOT + config.BASE / 2;
	const restingFromCenter = restingCenterI - restingRowWidth / 2;

	// Ideal expanded row width & ideal center of icon i
	let idealCenterI = sizes[0] / 2;
	for (let k = 1; k <= i; k++) {
		idealCenterI += (sizes[k - 1] + sizes[k]) / 2 + config.GAP;
	}
	const totalExpandedWidth =
		sizes.reduce((acc, s) => acc + s, 0) + (n - 1) * config.GAP;
	const idealFromCenter = idealCenterI - totalExpandedWidth / 2;

	return idealFromCenter - restingFromCenter;
}
