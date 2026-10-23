import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { reloadIfMirrorWasStale } from "./hooks/settingsMirror";

// Convert a hex color string to rgba with a specified alpha
function hexToRgba(hex: string, alpha: number): string {
	hex = hex.replace("#", "");
	if (hex.length === 3) {
		hex = hex
			.split("")
			.map((char) => char + char)
			.join("");
	}
	const r = parseInt(hex.substring(0, 2), 16) || 0;
	const g = parseInt(hex.substring(2, 4), 16) || 0;
	const b = parseInt(hex.substring(4, 6), 16) || 0;
	return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

export interface HSL {
	h: number;
	s: number;
	l: number;
}

// Convert Hex to HSL
export function hexToHsl(hex: string): HSL {
	hex = hex.replace("#", "");
	if (hex.length === 3) {
		hex = hex
			.split("")
			.map((char) => char + char)
			.join("");
	}
	const r = (parseInt(hex.substring(0, 2), 16) || 0) / 255;
	const g = (parseInt(hex.substring(2, 4), 16) || 0) / 255;
	const b = (parseInt(hex.substring(4, 6), 16) || 0) / 255;

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

	return { h: Math.round(h * 360), s: Math.round(s * 100), l: Math.round(l * 100) };
}

// Convert HSL back to Hex color string
function hslToHex(h: number, s: number, l: number): string {
	s /= 100;
	l /= 100;
	const c = (1 - Math.abs(2 * l - 1)) * s;
	const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
	const m = l - c / 2;
	let r = 0,
		g = 0,
		b = 0;

	if (0 <= h && h < 60) {
		r = c;
		g = x;
		b = 0;
	} else if (60 <= h && h < 120) {
		r = x;
		g = c;
		b = 0;
	} else if (120 <= h && h < 180) {
		r = 0;
		g = c;
		b = x;
	} else if (180 <= h && h < 240) {
		r = 0;
		g = x;
		b = c;
	} else if (240 <= h && h < 300) {
		r = x;
		g = 0;
		b = c;
	} else if (300 <= h && h < 360) {
		r = c;
		g = 0;
		b = x;
	}

	const toHex = (val: number) => {
		const hexVal = Math.round((val + m) * 255).toString(16);
		return hexVal.length === 1 ? "0" + hexVal : hexVal;
	};

	return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

export async function applyTheme(
	mode: string,
	customColor: string,
	opacity?: number,
	saturation?: number,
	brightness?: number
) {
	const root = document.documentElement;

	if (opacity === undefined || opacity === null) {
		const cachedOpacity = localStorage.getItem("gloom-theme-opacity");
		opacity = cachedOpacity !== null ? parseFloat(cachedOpacity) : 0.8;
	}

	if (saturation === undefined || saturation === null) {
		const cachedSaturation = localStorage.getItem("gloom-theme-saturation");
		saturation = cachedSaturation !== null ? parseFloat(cachedSaturation) : 0.5;
	}

	if (brightness === undefined || brightness === null) {
		const cachedBrightness = localStorage.getItem("gloom-theme-brightness");
		brightness = cachedBrightness !== null ? parseFloat(cachedBrightness) : 0.15;
	}

	// Resolve "system" mode dynamically
	let resolvedMode = mode;
	if (mode === "system") {
		const isDark =
			typeof window !== "undefined" &&
			window.matchMedia &&
			window.matchMedia("(prefers-color-scheme: dark)").matches;
		resolvedMode = isDark ? "dark" : "light";
	}

	const op = opacity;
	const opExpanded = Math.min(1.0, opacity + 0.12);

	if (resolvedMode === "light") {
		root.style.setProperty("--gloom-bg", `rgba(255, 255, 255, ${op})`);
		root.style.setProperty("--gloom-bg-expanded", `rgba(245, 245, 247, ${opExpanded})`);
		root.style.setProperty("--gloom-text", "#1c1c1e");
		root.style.setProperty("--gloom-text-muted", "rgba(28, 28, 30, 0.65)");
		root.style.setProperty("--gloom-border", "rgba(0, 0, 0, 0.12)");
		root.style.setProperty("--gloom-group-bg", "rgba(0, 0, 0, 0.04)");
		root.style.setProperty("--gloom-accent", "#007aff");
		root.style.setProperty("--gloom-scrollbar-thumb", "rgba(0, 0, 0, 0.15)");
		root.classList.add("light-mode");
		root.classList.remove("dark-mode");
		root.classList.add("theme-light");
		root.classList.remove("theme-dark", "theme-custom", "theme-adaptive");
	} else if (resolvedMode === "custom") {
		const hsl = hexToHsl(customColor);
		hsl.s = saturation * 100;
		hsl.l = brightness * 100;
		const finalBgColor = hslToHex(hsl.h, hsl.s, hsl.l);
		const isLight = brightness > 0.55;

		root.style.setProperty("--gloom-bg", hexToRgba(finalBgColor, op));
		root.style.setProperty("--gloom-bg-expanded", hexToRgba(finalBgColor, opExpanded));
		root.style.setProperty("--gloom-text", isLight ? "#1c1c1e" : "#ffffff");
		root.style.setProperty(
			"--gloom-text-muted",
			isLight ? "rgba(28, 28, 30, 0.65)" : "rgba(255, 255, 255, 0.65)"
		);
		root.style.setProperty(
			"--gloom-border",
			isLight ? "rgba(0, 0, 0, 0.12)" : "rgba(255, 255, 255, 0.12)"
		);
		root.style.setProperty(
			"--gloom-group-bg",
			isLight ? "rgba(0, 0, 0, 0.04)" : "rgba(255, 255, 255, 0.04)"
		);
		root.style.setProperty("--gloom-accent", customColor);
		root.style.setProperty(
			"--gloom-scrollbar-thumb",
			isLight ? "rgba(0, 0, 0, 0.15)" : "rgba(255, 255, 255, 0.15)"
		);
		root.classList.add("theme-custom");
		root.classList.remove("theme-light", "theme-dark", "theme-adaptive");
		if (isLight) {
			root.classList.add("light-mode");
			root.classList.remove("dark-mode");
		} else {
			root.classList.add("dark-mode");
			root.classList.remove("light-mode");
		}
	} else if (resolvedMode === "adaptive") {
		try {
			const accentHex = await invoke<string>("get_system_accent_color");
			const hsl = hexToHsl(accentHex);
			hsl.s = saturation * 100;
			hsl.l = brightness * 100;
			const finalBgColor = hslToHex(hsl.h, hsl.s, hsl.l);
			const isLight = brightness > 0.55;

			root.style.setProperty("--gloom-bg", hexToRgba(finalBgColor, op));
			root.style.setProperty("--gloom-bg-expanded", hexToRgba(finalBgColor, opExpanded));
			root.style.setProperty("--gloom-text", isLight ? "#1c1c1e" : "#ffffff");
			root.style.setProperty(
				"--gloom-text-muted",
				isLight ? "rgba(28, 28, 30, 0.65)" : "rgba(255, 255, 255, 0.65)"
			);
			root.style.setProperty(
				"--gloom-border",
				isLight ? "rgba(0, 0, 0, 0.12)" : "rgba(255, 255, 255, 0.12)"
			);
			root.style.setProperty(
				"--gloom-group-bg",
				isLight ? "rgba(0, 0, 0, 0.04)" : "rgba(255, 255, 255, 0.04)"
			);
			root.style.setProperty("--gloom-accent", accentHex);
			root.style.setProperty(
				"--gloom-scrollbar-thumb",
				isLight ? "rgba(0, 0, 0, 0.15)" : "rgba(255, 255, 255, 0.15)"
			);
			root.classList.add("theme-adaptive");
			root.classList.remove("theme-light", "theme-dark", "theme-custom");
			if (isLight) {
				root.classList.add("light-mode");
				root.classList.remove("dark-mode");
			} else {
				root.classList.add("dark-mode");
				root.classList.remove("light-mode");
			}
		} catch (e) {
			console.error("Failed to apply adaptive theme:", e);
			applyTheme("dark", customColor, op, saturation, brightness);
		}
	} else {
		// default/dark
		root.style.setProperty("--gloom-bg", `rgba(0, 0, 0, ${op})`);
		root.style.setProperty("--gloom-bg-expanded", `rgba(0, 0, 0, ${opExpanded})`);
		root.style.setProperty("--gloom-text", "#ffffff");
		root.style.setProperty("--gloom-text-muted", "rgba(255, 255, 255, 0.6)");
		root.style.setProperty("--gloom-border", "rgba(255, 255, 255, 0.1)");
		root.style.setProperty("--gloom-group-bg", "rgba(255, 255, 255, 0.04)");
		root.style.setProperty("--gloom-accent", "#007aff");
		root.style.setProperty("--gloom-scrollbar-thumb", "rgba(255, 255, 255, 0.1)");
		root.classList.add("dark-mode");
		root.classList.remove("light-mode");
		root.classList.add("theme-dark");
		root.classList.remove("theme-light", "theme-custom", "theme-adaptive");
	}
}

export function initTheme() {
	const syncMode = localStorage.getItem("gloom-theme-mode") || "dark";
	const syncColor = localStorage.getItem("gloom-theme-color") || "#007aff";
	const syncOpacityVal = localStorage.getItem("gloom-theme-opacity");
	const syncOpacity = syncOpacityVal !== null ? parseFloat(syncOpacityVal) : 0.8;
	const syncSaturationVal = localStorage.getItem("gloom-theme-saturation");
	const syncSaturation = syncSaturationVal !== null ? parseFloat(syncSaturationVal) : 0.5;
	const syncBrightnessVal = localStorage.getItem("gloom-theme-brightness");
	const syncBrightness = syncBrightnessVal !== null ? parseFloat(syncBrightnessVal) : 0.15;

	// Fast synchronous draw using cached localStorage
	applyTheme(syncMode, syncColor, syncOpacity, syncSaturation, syncBrightness);

	// Sync settings.json in background to avoid flickers
	invoke<Record<string, any>>("load_settings")
		.then(async (settings) => {
			if (await reloadIfMirrorWasStale(settings)) return;
			const mode = settings["gloom-theme-mode"] ? String(settings["gloom-theme-mode"]) : syncMode;
			const color = settings["gloom-theme-color"]
				? String(settings["gloom-theme-color"])
				: syncColor;
			const opacityVal = settings["gloom-theme-opacity"]
				? parseFloat(String(settings["gloom-theme-opacity"]))
				: syncOpacity;
			const saturationVal = settings["gloom-theme-saturation"]
				? parseFloat(String(settings["gloom-theme-saturation"]))
				: syncSaturation;
			const brightnessVal = settings["gloom-theme-brightness"]
				? parseFloat(String(settings["gloom-theme-brightness"]))
				: syncBrightness;

			if (
				mode !== syncMode ||
				color !== syncColor ||
				opacityVal !== syncOpacity ||
				saturationVal !== syncSaturation ||
				brightnessVal !== syncBrightness
			) {
				localStorage.setItem("gloom-theme-mode", mode);
				localStorage.setItem("gloom-theme-color", color);
				localStorage.setItem("gloom-theme-opacity", String(opacityVal));
				localStorage.setItem("gloom-theme-saturation", String(saturationVal));
				localStorage.setItem("gloom-theme-brightness", String(brightnessVal));
				applyTheme(mode, color, opacityVal, saturationVal, brightnessVal);
			}
		})
		.catch(console.error);

	// Helper: read all theme values from localStorage and apply
	const applyThemeFromStorage = () => {
		const mode = localStorage.getItem("gloom-theme-mode") || "dark";
		const color = localStorage.getItem("gloom-theme-color") || "#007aff";
		const opacity = parseFloat(localStorage.getItem("gloom-theme-opacity") || "0.80");
		const saturation = parseFloat(localStorage.getItem("gloom-theme-saturation") || "0.50");
		const brightness = parseFloat(localStorage.getItem("gloom-theme-brightness") || "0.15");
		applyTheme(mode, color, opacity, saturation, brightness);
	};

	const gloomThemeKeys = [
		"gloom-theme-mode",
		"gloom-theme-color",
		"gloom-theme-opacity",
		"gloom-theme-saturation",
		"gloom-theme-brightness"
	];

	// Listen to setting changes broadcasted from settings window
	const settingsPromise = listen<{ key: string; value: any }>("settings-changed", (event) => {
		const { key, value } = event.payload;
		if (gloomThemeKeys.includes(key)) {
			if (value !== null && value !== undefined) {
				localStorage.setItem(key, String(value));
			} else {
				localStorage.removeItem(key);
			}
			applyThemeFromStorage();
		}
	});

	// Listen to system accent color updates in case of adaptive theme
	const accentPromise = listen<string>("system-accent-changed", (event) => {
		const currentMode = localStorage.getItem("gloom-theme-mode") || "dark";
		if (currentMode === "adaptive") {
			const opacity = parseFloat(localStorage.getItem("gloom-theme-opacity") || "0.80");
			const saturation = parseFloat(localStorage.getItem("gloom-theme-saturation") || "0.50");
			const brightness = parseFloat(localStorage.getItem("gloom-theme-brightness") || "0.15");
			applyTheme("adaptive", event.payload, opacity, saturation, brightness);
		}
	});

	// Listen to external edits (e.g. settings.json edited outside the app)
	const externalPromise = listen<{ key: string; value: any }>(
		"settings-external-changed",
		(event) => {
			const { key, value } = event.payload;
			if (gloomThemeKeys.includes(key)) {
				if (value !== null && value !== undefined) {
					localStorage.setItem(key, String(value));
				} else {
					localStorage.removeItem(key);
				}
				applyThemeFromStorage();
			}
		}
	);

	// React to OS system dark/light theme changes if mode is "system"
	const onSystemThemeChange = () => {
		const currentMode = localStorage.getItem("gloom-theme-mode") || "dark";
		if (currentMode === "system") {
			applyThemeFromStorage();
		}
	};
	let mediaWatcher: MediaQueryList | null = null;
	if (typeof window !== "undefined" && window.matchMedia) {
		mediaWatcher = window.matchMedia("(prefers-color-scheme: dark)");
		mediaWatcher.addEventListener("change", onSystemThemeChange);
	}

	return () => {
		settingsPromise.then((f) => f());
		accentPromise.then((f) => f());
		externalPromise.then((f) => f());
		if (mediaWatcher) {
			mediaWatcher.removeEventListener("change", onSystemThemeChange);
		}
	};
}
