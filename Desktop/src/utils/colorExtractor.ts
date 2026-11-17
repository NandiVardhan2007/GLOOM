/**
 * Fast Canvas-based color palette extractor for Dynamic Island reactive artwork lighting.
 * Extracts dominant, secondary, and accent colors from album covers with in-memory memoization.
 */

export interface DominantPalette {
	primary: string;
	secondary: string;
	accent: string;
}

export const DEFAULT_PALETTE: DominantPalette = {
	primary: "rgba(48, 209, 88, 0.4)",
	secondary: "rgba(52, 199, 89, 0.25)",
	accent: "#30D158"
};

const paletteCache = new Map<string, DominantPalette>();

export function rgbToHsl(r: number, g: number, b: number): [number, number, number] {
	r /= 255;
	g /= 255;
	b /= 255;
	const max = Math.max(r, g, b);
	const min = Math.min(r, g, b);
	let h = 0;
	let s = 0;
	const l = (max + min) / 2;

	if (max !== min) {
		const d = max - min;
		s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
		switch (max) {
			case r:
				h = (g - b) / d + (g < b ? 6 : 0);
				break;
			case g:
				h = (b - r) / d + 2;
				break;
			case b:
				h = (r - g) / d + 4;
				break;
		}
		h /= 6;
	}

	return [h * 360, s, l];
}

/**
 * Extracts dominant and ambient secondary colors from an image URL.
 * Downsamples to a 16x16 canvas for sub-5ms extraction performance.
 */
export async function extractDominantPalette(imageUrl: string): Promise<DominantPalette> {
	if (!imageUrl) return DEFAULT_PALETTE;
	if (paletteCache.has(imageUrl)) {
		return paletteCache.get(imageUrl)!;
	}

	if (typeof window === "undefined" || typeof document === "undefined") {
		return DEFAULT_PALETTE;
	}

	return new Promise((resolve) => {
		const img = new Image();
		img.crossOrigin = "anonymous";

		img.onload = () => {
			try {
				const canvas = document.createElement("canvas");
				const size = 16;
				canvas.width = size;
				canvas.height = size;
				const ctx = canvas.getContext("2d");

				if (!ctx) {
					resolve(DEFAULT_PALETTE);
					return;
				}

				ctx.drawImage(img, 0, 0, size, size);
				const imgData = ctx.getImageData(0, 0, size, size).data;

				let totalR = 0;
				let totalG = 0;
				let totalB = 0;
				let count = 0;

				let maxSaturation = 0;
				let vibrantR = 48;
				let vibrantG = 209;
				let vibrantB = 88;

				for (let i = 0; i < imgData.length; i += 4) {
					const r = imgData[i];
					const g = imgData[i + 1];
					const b = imgData[i + 2];
					const a = imgData[i + 3];

					if (a < 128) continue; // skip transparent

					const [, s, l] = rgbToHsl(r, g, b);

					// Exclude near-black and near-white for dominant tone
					if (l > 0.12 && l < 0.88) {
						totalR += r;
						totalG += g;
						totalB += b;
						count++;

						if (s > maxSaturation) {
							maxSaturation = s;
							vibrantR = r;
							vibrantG = g;
							vibrantB = b;
						}
					}
				}

				if (count === 0) {
					paletteCache.set(imageUrl, DEFAULT_PALETTE);
					resolve(DEFAULT_PALETTE);
					return;
				}

				const domR = Math.round(totalR / count);
				const domG = Math.round(totalG / count);
				const domB = Math.round(totalB / count);

				const palette: DominantPalette = {
					primary: `rgba(${vibrantR}, ${vibrantG}, ${vibrantB}, 0.42)`,
					secondary: `rgba(${domR}, ${domG}, ${domB}, 0.28)`,
					accent: `rgb(${vibrantR}, ${vibrantG}, ${vibrantB})`
				};

				paletteCache.set(imageUrl, palette);
				resolve(palette);
			} catch {
				resolve(DEFAULT_PALETTE);
			}
		};

		img.onerror = () => {
			resolve(DEFAULT_PALETTE);
		};

		img.src = imageUrl;
	});
}
