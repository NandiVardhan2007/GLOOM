import { useState, useEffect, useCallback, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen, emit } from "@tauri-apps/api/event";
import { enable, disable, isEnabled } from "@tauri-apps/plugin-autostart";
import { getVersion } from "@tauri-apps/api/app";
import type { UpdateCheckResult } from "../updater";
import { useSettingsSync } from "../hooks/useSettingsSync";
import { reloadIfMirrorWasStale } from "../hooks/settingsMirror";
import { hexToHsl } from "../theme";
import {
	addCustomFolder,
	removeCustomFolder,
	toggleEnabledFolder,
	toggleHiddenDrive,
	DEFAULT_ENABLED_FOLDERS
} from "../dockExtras";
import type { WidgetConfig } from "./types";

function readBool(val: string | null): boolean {
	return val === "true";
}

export function useSettings() {
	const [saveError, setSaveError] = useState(
		() => sessionStorage.getItem("gloom-settings-save-error") !== null
	);
	const saveQueue = useRef<Promise<void>>(Promise.resolve());
	const saveFailed = useRef(false);
	const [autostart, setAutostart] = useState(false);
	const [weatherEnabled, setWeatherEnabled] = useState(true);
	const [calendarEnabled, setCalendarEnabled] = useState(true);
	const [timerSoundEnabled, setTimerSoundEnabled] = useState(
		() => localStorage.getItem("gloom-timer-sound-enabled") !== "false"
	);
	const [musicModeEnabled, setMusicModeEnabled] = useState(true);
	const [notchRememberPanel, setNotchRememberPanel] = useState(
		() => (localStorage.getItem("gloom-notch-remember-panel") ??
			localStorage.getItem("gloom-notch-click-to-open")) !== "false"
	);
	const [notchDefaultPanel, setNotchDefaultPanel] = useState<"calendar" | "command-center">(
		() => localStorage.getItem("gloom-notch-default-panel") === "command-center"
			? "command-center" : "calendar"
	);
	const [musicCompactNotch, setMusicCompactNotch] = useState(true);
	const [shelfPersist, setShelfPersist] = useState(
		() => localStorage.getItem("gloom-shelf-persist") === "true"
	);
	const [volumeOverlayEnabled, setVolumeOverlayEnabled] = useState(true);
	const [volumeEdgeEnabled, setVolumeEdgeEnabled] = useState(
		() => localStorage.getItem("gloom-volume-edge-enabled") !== "false"
	);
	const [brightnessOverlayEnabled, setBrightnessOverlayEnabled] = useState(
		() => localStorage.getItem("gloom-brightness-overlay-enabled") !== "false"
	);
	const [brightnessEdgeEnabled, setBrightnessEdgeEnabled] = useState(
		() => localStorage.getItem("gloom-brightness-edge-enabled") !== "false"
	);
	const [mediaAmbienceEnabled, setMediaAmbienceEnabled] = useState(true);
	const [mediaCompactGlowEnabled, setMediaCompactGlowEnabled] = useState(true);
	const [mediaLayout, setMediaLayout] = useState<"classic" | "compact">(
		() => (localStorage.getItem("gloom-media-layout") as "classic" | "compact") || "classic"
	);
	const [cornersEnabled, setCornersEnabled] = useState(
		() => localStorage.getItem("gloom-corners-enabled") === "true"
	);
	const [showUpdateIndicator, setShowUpdateIndicator] = useState(
		() => localStorage.getItem("gloom-show-update-indicator") !== "false"
	);
	const [timeFormat24h, setTimeFormat24h] = useState(
		() => localStorage.getItem("gloom-time-format-24h") === "true"
	);
	const [tempUnitFahrenheit, setTempUnitFahrenheit] = useState(false);
	const [cityName, setCityName] = useState("");
	const [citySearchResults, setCitySearchResults] = useState<
		Array<{ name: string; country: string; latitude: number; longitude: number }>
	>([]);
	const [showCityDropdown, setShowCityDropdown] = useState(false);
	const [statusWidgets, setStatusWidgets] = useState<WidgetConfig>({
		left: ["weather"],
		right: ["battery"]
	});
	const [dockEnabled, setDockEnabled] = useState(true);
	const [dockPreviewEnabled, setDockPreviewEnabled] = useState(true);
	const [dockIconOnly, setDockIconOnly] = useState(
		() => localStorage.getItem("gloom-dock-icon-only") === "true"
	);
	const [dockSeparatorEnabled, setDockSeparatorEnabled] = useState(
		() => localStorage.getItem("gloom-dock-separator-enabled") !== "false"
	);
	const [startIcon, setStartIcon] = useState(
		() => localStorage.getItem("gloom-start-icon") || "default"
	);
	const [dockAdaptive, setDockAdaptive] = useState(
		() => localStorage.getItem("gloom-dock-adaptive") === "true"
	);
	const [dockWinNumberEnabled, setDockWinNumberEnabled] = useState(
		() => localStorage.getItem("gloom-dock-win-number-enabled") !== "false"
	);
	const [dockExtrasPosition, setDockExtrasPosition] = useState(
		() => localStorage.getItem("gloom-dock-extras-position") || "off"
	);
	const [dockExtrasDrives, setDockExtrasDrives] = useState(
		() => localStorage.getItem("gloom-dock-extras-drives") !== "false"
	);
	const [dockExtrasHiddenDrives, setDockExtrasHiddenDrives] = useState(
		() => localStorage.getItem("gloom-dock-extras-hidden-drives") || ""
	);
	const [dockExtrasRecycleBin, setDockExtrasRecycleBin] = useState(
		() => localStorage.getItem("gloom-dock-extras-recycle-bin") !== "false"
	);
	// Off unless turned on, so existing docks don't change.
	const [dockTaskView, setDockTaskView] = useState(
		() => localStorage.getItem("gloom-dock-task-view") === "true"
	);
	const [dockExtrasFolders, setDockExtrasFolders] = useState(
		() => localStorage.getItem("gloom-dock-extras-folders") ?? DEFAULT_ENABLED_FOLDERS
	);
	const [dockExtrasCustomFolders, setDockExtrasCustomFolders] = useState(
		() => localStorage.getItem("gloom-dock-extras-custom-folders") || "[]"
	);
	const [dockMode, setDockMode] = useState(() => {
		// "smart" is the fresh-install default — keep in step with App.tsx,
		// Dock.tsx, and the backend fallback in commands.rs.
		const raw = localStorage.getItem("gloom-dock-mode") || "smart";
		return raw === "auto-hide" ? "smart" : raw;
	});
	const [dockEdgeDelay, setDockEdgeDelay] = useState(() => {
		const val = localStorage.getItem("gloom-dock-edge-delay");
		return val !== null ? parseInt(val) : 200;
	});
	const [notchMode, setNotchMode] = useState("fixed");
	const [notchEdgeDelay, setNotchEdgeDelay] = useState(() => {
		const val = localStorage.getItem("gloom-notch-edge-delay");
		return val !== null ? parseInt(val) : 200;
	});
	const [notchDisplay, setNotchDisplay] = useState<string>(
		() => localStorage.getItem("gloom-notch-display") || "primary"
	);
	const [dockDisplay, setDockDisplay] = useState<string>(
		() => localStorage.getItem("gloom-dock-display") || "primary"
	);
	const [islandStyle, setIslandStyle] = useState<"notch" | "pill">(() => {
		const val = localStorage.getItem("gloom-island-style");
		return val === "pill" ? "pill" : "notch";
	});
	const [lowBatteryThreshold, setLowBatteryThreshold] = useState(20);
	const [updateStatus, setUpdateStatus] = useState<
		"idle" | "checking" | "available" | "uptodate" | "error" | "downloading" | "installing"
	>("idle");
	const [updateVersion, setUpdateVersion] = useState("");
	const [appVersion, setAppVersion] = useState("");
	const [autoUpdate, setAutoUpdate] = useState(
		() => localStorage.getItem("gloom-auto-update") === "true"
	);
	const [scale, setScale] = useState(() =>
		parseFloat(localStorage.getItem("gloom-scale") || "1.0")
	);
	const [themeMode, setThemeMode] = useState(
		() => localStorage.getItem("gloom-theme-mode") || "dark"
	);
	const [themeColor, setThemeColor] = useState(
		() => localStorage.getItem("gloom-theme-color") || "#007aff"
	);
	const [themeOpacity, setThemeOpacity] = useState(() => {
		const val = localStorage.getItem("gloom-theme-opacity");
		return val !== null ? parseFloat(val) : 0.8;
	});
	const [themeSaturation, setThemeSaturation] = useState(() => {
		const val = localStorage.getItem("gloom-theme-saturation");
		return val !== null ? parseFloat(val) : 0.5;
	});
	const [themeBrightness, setThemeBrightness] = useState(() => {
		const val = localStorage.getItem("gloom-theme-brightness");
		return val !== null ? parseFloat(val) : 0.15;
	});
	const [exportStatus, setExportStatus] = useState<"idle" | "exporting" | "success" | "error">(
		"idle"
	);
	const [importStatus, setImportStatus] = useState<"idle" | "importing" | "success" | "error">(
		"idle"
	);

	// ── Load all settings from backend + localStorage ──
	const loadAllSettings = useCallback(async () => {
		try {
			const settings: Record<string, string> = await invoke("load_settings");
			if (await reloadIfMirrorWasStale(settings)) return;
			const getVal = (key: string) => {
				const val = settings[key];
				if (val !== undefined && val !== null) return String(val);
				return localStorage.getItem(key);
			};

			const apply = <T>(
				val: string | null,
				setter: (v: T) => void,
				transform: (v: string) => T
			) => {
				if (val !== null) setter(transform(val));
			};

			apply(getVal("gloom-weather-enabled"), setWeatherEnabled, readBool);
			apply(getVal("gloom-calendar-enabled"), setCalendarEnabled, readBool);
			apply(getVal("gloom-timer-sound-enabled"), setTimerSoundEnabled, readBool);
			apply(getVal("gloom-music-mode-enabled"), setMusicModeEnabled, readBool);
			apply(getVal("gloom-notch-remember-panel") ?? getVal("gloom-notch-click-to-open"),
				setNotchRememberPanel, readBool);
			apply(getVal("gloom-notch-default-panel"), setNotchDefaultPanel,
				(v) => v === "command-center" ? "command-center" : "calendar");
			apply(getVal("gloom-music-compact-notch"), setMusicCompactNotch, readBool);
			apply(getVal("gloom-shelf-persist"), setShelfPersist, readBool);
			apply(getVal("gloom-volume-overlay-enabled"), setVolumeOverlayEnabled, readBool);
			apply(getVal("gloom-brightness-overlay-enabled"), setBrightnessOverlayEnabled, readBool);
			apply(getVal("gloom-media-ambience-enabled"), setMediaAmbienceEnabled, readBool);
			apply(getVal("gloom-media-compact-glow-enabled"), setMediaCompactGlowEnabled, readBool);
			apply(getVal("gloom-corners-enabled"), setCornersEnabled, readBool);
			apply(getVal("gloom-time-format-24h"), setTimeFormat24h, readBool);
			apply(getVal("gloom-show-update-indicator"), setShowUpdateIndicator, readBool);
			apply(getVal("gloom-auto-update"), setAutoUpdate, readBool);
			apply(getVal("gloom-volume-edge-enabled"), setVolumeEdgeEnabled, readBool);
			apply(getVal("gloom-brightness-edge-enabled"), setBrightnessEdgeEnabled, readBool);
			localStorage.removeItem("gloom-dock-genie-effect");
			localStorage.removeItem("gloom-dock-genie-duration");
			localStorage.removeItem("gloom-dock-genie-style");
			apply(getVal("gloom-dock-enabled"), setDockEnabled, readBool);
			apply(getVal("gloom-dock-preview-enabled"), setDockPreviewEnabled, readBool);
			apply(getVal("gloom-dock-icon-only"), setDockIconOnly, readBool);
			apply(getVal("gloom-dock-separator-enabled"), setDockSeparatorEnabled, readBool);
			apply(getVal("gloom-start-icon"), setStartIcon, (v) => v);
			apply(getVal("gloom-dock-adaptive"), setDockAdaptive, readBool);
			apply(getVal("gloom-dock-win-number-enabled"), setDockWinNumberEnabled, readBool);
			apply(getVal("gloom-dock-extras-position"), setDockExtrasPosition, (v) => v);
			apply(getVal("gloom-dock-extras-drives"), setDockExtrasDrives, readBool);
			apply(getVal("gloom-dock-extras-recycle-bin"), setDockExtrasRecycleBin, readBool);
			apply(getVal("gloom-dock-task-view"), setDockTaskView, readBool);
			apply(getVal("gloom-dock-extras-hidden-drives"), setDockExtrasHiddenDrives, (v) => v);
			apply(getVal("gloom-dock-extras-folders"), setDockExtrasFolders, (v) => v);
			apply(getVal("gloom-dock-extras-custom-folders"), setDockExtrasCustomFolders, (v) => v);

			apply(getVal("gloom-temp-unit"), setTempUnitFahrenheit, (v) => v === "fahrenheit");
			apply(getVal("gloom-scale"), setScale, parseFloat);
			apply(getVal("gloom-low-battery-threshold"), setLowBatteryThreshold, parseInt);

			apply(getVal("gloom-notch-mode"), setNotchMode, (v) => (v === "auto-hide" ? "smart" : v));
			apply(getVal("gloom-notch-edge-delay"), setNotchEdgeDelay, parseInt);
			apply(getVal("gloom-island-style"), setIslandStyle, (v) => (v === "pill" ? "pill" : "notch"));
			apply(getVal("gloom-dock-mode"), setDockMode, (v) => (v === "auto-hide" ? "smart" : v));
			apply(getVal("gloom-dock-edge-delay"), setDockEdgeDelay, parseInt);
			apply(getVal("gloom-notch-display"), setNotchDisplay, (v) => v || "primary");
			apply(getVal("gloom-dock-display"), setDockDisplay, (v) => v || "primary");

			const savedCity = getVal("gloom-weather-city");
			if (savedCity) setCityName(savedCity);

			apply(getVal("gloom-theme-mode"), setThemeMode, (v) => v);
			apply(getVal("gloom-theme-color"), setThemeColor, (v) => v);
			apply(getVal("gloom-theme-opacity"), setThemeOpacity, parseFloat);
			apply(getVal("gloom-theme-saturation"), setThemeSaturation, parseFloat);
			apply(getVal("gloom-theme-brightness"), setThemeBrightness, parseFloat);

			const widgetsVal = getVal("gloom-status-widgets");
			if (widgetsVal) {
				try {
					const parsed = JSON.parse(widgetsVal);
					if (parsed && Array.isArray(parsed.left) && Array.isArray(parsed.right)) {
						setStatusWidgets(parsed);
					}
				} catch {}
			}
		} catch (e) {
			console.error("Failed to load settings:", e);
		}
	}, []);

	// The file write is authoritative. Serialize controls that change several
	// settings, and update the local mirror only after the backend confirms the
	// write. A failed save reloads the page from persisted values and leaves a
	// visible error instead of keeping an optimistic control value on screen.
	const saveSetting = (key: string, value: string | null): Promise<boolean> => {
		const operation = saveQueue.current.then(async () => {
			if (saveFailed.current) return false;
			try {
				if (value === null) localStorage.removeItem(key);
				else localStorage.setItem(key, value);
			} catch {}
			try {
				await invoke<void>("save_setting", { key, value });
			} catch (error) {
				console.error(`Failed to save ${key}:`, error);
				saveFailed.current = true;
				sessionStorage.setItem("gloom-settings-save-error", "true");
				setSaveError(true);
				window.setTimeout(() => window.location.reload(), 0);
				return false;
			}
			try {
				if (value === null) localStorage.removeItem(key);
				else localStorage.setItem(key, value);
				sessionStorage.removeItem("gloom-settings-save-error");
			} catch (error) {
				// The file is saved; a mirror failure must not be reported as a failed write.
				console.warn("Settings mirror update failed:", error);
			}
			setSaveError(false);
			return true;
		});
		saveQueue.current = operation.then(() => {});
		return operation;
	};

	// Coordinates only need the backend settings file. Keep them out of the
	// browser's localStorage mirror while preserving the same queued/error path.
	const saveLocation = (key: "gloom-weather-lat" | "gloom-weather-lon", value: string | null): Promise<boolean> => {
		const operation = saveQueue.current.then(async () => {
			if (saveFailed.current) return false;
			try {
				await invoke<void>("save_setting", { key, value });
			} catch (error) {
				console.error(`Failed to save ${key}:`, error);
				saveFailed.current = true;
				sessionStorage.setItem("gloom-settings-save-error", "true");
				setSaveError(true);
				window.setTimeout(() => window.location.reload(), 0);
				return false;
			}
			try {
				localStorage.removeItem(key);
				sessionStorage.removeItem("gloom-settings-save-error");
			} catch (error) {
				console.warn("Settings mirror cleanup failed:", error);
			}
			setSaveError(false);
			return true;
		});
		saveQueue.current = operation.then(() => {});
		return operation;
	};

	const dismissSaveError = () => {
		sessionStorage.removeItem("gloom-settings-save-error");
		setSaveError(false);
	};

	// ── Initialize on mount ──
	useEffect(() => {
		loadAllSettings();

		isEnabled()
			.then(setAutostart)
			.catch(() => {});

		getVersion()
			.then((ver) => setAppVersion(ver || "3.1.2"))
			.catch(() => setAppVersion("3.1.2"));

		checkForUpdates(false);
	}, []);

	// ── Sync settings from other windows ──
	useSettingsSync({
		"gloom-dock-mode": setDockMode,
		"gloom-dock-edge-delay": setDockEdgeDelay,
		"gloom-notch-mode": setNotchMode,
		"gloom-notch-edge-delay": setNotchEdgeDelay,
		"gloom-island-style": (v) => setIslandStyle(v === "pill" ? "pill" : "notch"),
		"gloom-dock-enabled": setDockEnabled,
		"gloom-dock-icon-only": setDockIconOnly,
		"gloom-dock-separator-enabled": setDockSeparatorEnabled,
		"gloom-start-icon": setStartIcon,
		"gloom-dock-preview-enabled": setDockPreviewEnabled,
		"gloom-dock-adaptive": setDockAdaptive,
		"gloom-dock-win-number-enabled": setDockWinNumberEnabled,
		"gloom-dock-extras-position": setDockExtrasPosition,
		"gloom-dock-extras-drives": setDockExtrasDrives,
		"gloom-dock-extras-recycle-bin": setDockExtrasRecycleBin,
		"gloom-dock-task-view": setDockTaskView,
		"gloom-dock-extras-hidden-drives": setDockExtrasHiddenDrives,
		"gloom-dock-extras-folders": setDockExtrasFolders,
		"gloom-dock-extras-custom-folders": setDockExtrasCustomFolders,
		"gloom-weather-enabled": setWeatherEnabled,
		"gloom-calendar-enabled": setCalendarEnabled,
		"gloom-timer-sound-enabled": setTimerSoundEnabled,
		"gloom-music-mode-enabled": setMusicModeEnabled,
		"gloom-notch-remember-panel": setNotchRememberPanel,
		"gloom-notch-default-panel": (v: unknown) =>
			setNotchDefaultPanel(v === "command-center" ? "command-center" : "calendar"),
		"gloom-music-compact-notch": setMusicCompactNotch,
		"gloom-media-ambience-enabled": setMediaAmbienceEnabled,
		"gloom-media-compact-glow-enabled": setMediaCompactGlowEnabled,
		"gloom-media-layout": setMediaLayout,
		"gloom-corners-enabled": setCornersEnabled,
		"gloom-show-update-indicator": setShowUpdateIndicator,
		"gloom-time-format-24h": setTimeFormat24h,
		"gloom-low-battery-threshold": setLowBatteryThreshold,
		"gloom-scale": setScale,
		"gloom-temp-unit": (v) => setTempUnitFahrenheit(v === "fahrenheit"),
		"gloom-auto-update": setAutoUpdate,
		"gloom-volume-overlay-enabled": setVolumeOverlayEnabled,
		"gloom-volume-edge-enabled": setVolumeEdgeEnabled,
		"gloom-brightness-overlay-enabled": setBrightnessOverlayEnabled,
		"gloom-brightness-edge-enabled": setBrightnessEdgeEnabled,
		"gloom-theme-mode": setThemeMode,
		"gloom-theme-color": setThemeColor,
		"gloom-theme-opacity": setThemeOpacity,
		"gloom-theme-saturation": setThemeSaturation,
		"gloom-theme-brightness": setThemeBrightness,
		"gloom-weather-city": (v) => setCityName(v || "")
	});

	// ── Listen for system accent changes (adaptive theme) ──
	useEffect(() => {
		const unlisten = listen<string>("system-accent-changed", (event) => {
			const mode = localStorage.getItem("gloom-theme-mode") || "dark";
			if (mode === "adaptive") {
				try {
					const hsl = hexToHsl(event.payload);
					setThemeSaturation(hsl.s / 100);
					setThemeBrightness(hsl.l / 100);
				} catch (e) {
					console.error("Failed to parse system accent color change HSL:", e);
				}
			}
		});
		return () => {
			unlisten.then((fn) => fn());
		};
	}, []);

	// ── City search debounce ──
	useEffect(() => {
		if (cityName.trim().length < 2) {
			setCitySearchResults([]);
			setShowCityDropdown(false);
			return;
		}

		const timeout = setTimeout(async () => {
			try {
				const res = await fetch(
					`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(cityName)}&count=5&language=en&format=json`
				);
				const data = await res.json();
				if (data.results && data.results.length > 0) {
					setCitySearchResults(
						data.results.map((r: any) => ({
							name: r.name,
							country: r.country || "",
							latitude: r.latitude,
							longitude: r.longitude
						}))
					);
					setShowCityDropdown(true);
				} else {
					setCitySearchResults([]);
				}
			} catch {
				setCitySearchResults([]);
			}
		}, 300);

		return () => clearTimeout(timeout);
	}, [cityName]);

	// ── Update checker ──
	const checkForUpdates = async (manual = true) => {
		setUpdateStatus("checking");
		try {
			const result = await invoke<UpdateCheckResult>("check_for_updates", { force: manual });
			if (result.available) {
				setUpdateStatus("available");
				setUpdateVersion(result.version ?? "");
			} else {
				setUpdateStatus("uptodate");
			}
		} catch (e) {
			console.error("Updater error:", e);
			setUpdateStatus("error");
		}
	};

	const installUpdate = async () => {
		try {
			setUpdateStatus("downloading");
			await invoke("install_update");
			setUpdateStatus("idle");
		} catch (e) {
			console.error(e);
			setUpdateStatus("error");
		}
	};

	// Reflect install progress triggered from any window (including auto-update).
	useEffect(() => {
		const unlisten = listen<{ status: string; progress?: number }>(
			"auto-update-status",
			(event) => {
				switch (event.payload.status) {
					case "downloading":
						setUpdateStatus("downloading");
						break;
					case "installing":
						setUpdateStatus("installing");
						break;
					case "done":
						setUpdateStatus((prev) =>
							prev === "downloading" || prev === "installing" ? "idle" : prev
						);
						break;
				}
			}
		);
		return () => {
			unlisten.then((fn) => fn());
		};
	}, []);

	// ── Autostart ──
	const toggleAutostart = async () => {
		try {
			const currentlyEnabled = await isEnabled();
			if (currentlyEnabled) {
				await disable();
				setAutostart(false);
			} else {
				await enable();
				setAutostart(true);
			}
		} catch (err) {}
	};

	// ── Simple boolean toggles ──
	const toggleWeather = () => {
		const next = !weatherEnabled;
		setWeatherEnabled(next);
		saveSetting("gloom-weather-enabled", String(next));
	};

	const toggleCalendar = () => {
		const next = !calendarEnabled;
		setCalendarEnabled(next);
		saveSetting("gloom-calendar-enabled", String(next));
	};

	const toggleTimerSound = () => {
		const next = !timerSoundEnabled;
		setTimerSoundEnabled(next);
		saveSetting("gloom-timer-sound-enabled", String(next));
	};

	const toggleMusicMode = () => {
		const next = !musicModeEnabled;
		setMusicModeEnabled(next);
		saveSetting("gloom-music-mode-enabled", String(next));
	};

	const toggleNotchRememberPanel = () => {
		const next = !notchRememberPanel;
		setNotchRememberPanel(next);
		saveSetting("gloom-notch-remember-panel", String(next));
	};

	const changeNotchDefaultPanel = (panel: "calendar" | "command-center") => {
		setNotchDefaultPanel(panel);
		saveSetting("gloom-notch-default-panel", panel);
	};

	const toggleMusicCompactNotch = () => {
		const next = !musicCompactNotch;
		setMusicCompactNotch(next);
		saveSetting("gloom-music-compact-notch", String(next));
	};

	const toggleShelfPersist = () => {
		const next = !shelfPersist;
		setShelfPersist(next);
		saveSetting("gloom-shelf-persist", String(next));
	};

	const toggleMediaLayout = (layout: "classic" | "compact") => {
		setMediaLayout(layout);
		saveSetting("gloom-media-layout", layout);
	};

	const toggleVolumeOverlay = () => {
		const next = !volumeOverlayEnabled;
		setVolumeOverlayEnabled(next);
		saveSetting("gloom-volume-overlay-enabled", String(next));
	};

	const toggleVolumeEdge = () => {
		const next = !volumeEdgeEnabled;
		setVolumeEdgeEnabled(next);
		saveSetting("gloom-volume-edge-enabled", String(next));
	};

	const toggleBrightnessOverlay = () => {
		const next = !brightnessOverlayEnabled;
		setBrightnessOverlayEnabled(next);
		saveSetting("gloom-brightness-overlay-enabled", String(next));
	};

	const toggleBrightnessEdge = () => {
		const next = !brightnessEdgeEnabled;
		setBrightnessEdgeEnabled(next);
		saveSetting("gloom-brightness-edge-enabled", String(next));
	};

	const toggleAmbience = () => {
		const next = !mediaAmbienceEnabled;
		setMediaAmbienceEnabled(next);
		saveSetting("gloom-media-ambience-enabled", String(next));
	};

	const toggleCompactGlow = () => {
		const next = !mediaCompactGlowEnabled;
		setMediaCompactGlowEnabled(next);
		saveSetting("gloom-media-compact-glow-enabled", String(next));
	};

	const toggleCorners = () => {
		const next = !cornersEnabled;
		setCornersEnabled(next);
		saveSetting("gloom-corners-enabled", String(next));
	};

	const toggleUpdateIndicator = () => {
		const next = !showUpdateIndicator;
		setShowUpdateIndicator(next);
		saveSetting("gloom-show-update-indicator", String(next));
	};

	const toggleTimeFormat24h = () => {
		const next = !timeFormat24h;
		setTimeFormat24h(next);
		saveSetting("gloom-time-format-24h", String(next));
	};

	const toggleTempUnit = () => {
		const next = !tempUnitFahrenheit;
		setTempUnitFahrenheit(next);
		saveSetting("gloom-temp-unit", next ? "fahrenheit" : "celsius");
	};

	const toggleDock = () => {
		const next = !dockEnabled;
		setDockEnabled(next);
		saveSetting("gloom-dock-enabled", String(next));
	};

	const toggleDockPreview = () => {
		const next = !dockPreviewEnabled;
		setDockPreviewEnabled(next);
		saveSetting("gloom-dock-preview-enabled", String(next));
	};

	const toggleDockIconOnly = () => {
		const next = !dockIconOnly;
		setDockIconOnly(next);
		saveSetting("gloom-dock-icon-only", String(next));
	};

	const toggleDockSeparator = () => {
		const next = !dockSeparatorEnabled;
		setDockSeparatorEnabled(next);
		saveSetting("gloom-dock-separator-enabled", String(next));
	};

	const handleStartIconChange = (icon: string) => {
		setStartIcon(icon);
		saveSetting("gloom-start-icon", icon);
	};

	const toggleDockAdaptive = () => {
		const next = !dockAdaptive;
		setDockAdaptive(next);
		saveSetting("gloom-dock-adaptive", String(next));
	};

	const toggleDockWinNumber = () => {
		const next = !dockWinNumberEnabled;
		setDockWinNumberEnabled(next);
		saveSetting("gloom-dock-win-number-enabled", String(next));
	};

	const handleDockExtrasPositionChange = (position: string) => {
		setDockExtrasPosition(position);
		saveSetting("gloom-dock-extras-position", position);
	};

	const toggleDockExtrasDrives = () => {
		const next = !dockExtrasDrives;
		setDockExtrasDrives(next);
		saveSetting("gloom-dock-extras-drives", String(next));
	};

	const toggleDockExtrasDrive = (letter: string) => {
		const next = toggleHiddenDrive(dockExtrasHiddenDrives, letter);
		setDockExtrasHiddenDrives(next);
		saveSetting("gloom-dock-extras-hidden-drives", next);
	};

	const toggleDockExtrasRecycleBin = () => {
		const next = !dockExtrasRecycleBin;
		setDockExtrasRecycleBin(next);
		saveSetting("gloom-dock-extras-recycle-bin", String(next));
	};

	const toggleDockTaskView = () => {
		const next = !dockTaskView;
		setDockTaskView(next);
		saveSetting("gloom-dock-task-view", String(next));
	};

	const toggleDockExtraFolder = (id: string) => {
		const next = toggleEnabledFolder(dockExtrasFolders, id);
		setDockExtrasFolders(next);
		saveSetting("gloom-dock-extras-folders", next);
	};

	const handleAddDockExtraFolder = async () => {
		try {
			const { open: openDialog } = await import("@tauri-apps/plugin-dialog");
			const selected = await openDialog({
				title: "Add a folder to the dock",
				directory: true,
				multiple: false
			});
			if (typeof selected === "string") {
				const next = addCustomFolder(dockExtrasCustomFolders, selected);
				setDockExtrasCustomFolders(next);
				saveSetting("gloom-dock-extras-custom-folders", next);
			}
		} catch (e) {
			console.error("Folder picker failed:", e);
		}
	};

	const handleRemoveDockExtraFolder = (path: string) => {
		const next = removeCustomFolder(dockExtrasCustomFolders, path);
		setDockExtrasCustomFolders(next);
		saveSetting("gloom-dock-extras-custom-folders", next);
	};

	const toggleAutoUpdate = () => {
		const next = !autoUpdate;
		setAutoUpdate(next);
		saveSetting("gloom-auto-update", String(next));
	};

	// ── Value setters ──
	const setDockModeValue = (newMode: string) => {
		setDockMode(newMode);
		saveSetting("gloom-dock-mode", newMode);
	};

	const setNotchModeValue = (newMode: string) => {
		setNotchMode(newMode);
		saveSetting("gloom-notch-mode", newMode);
	};

	const handleDockEdgeDelayChange = (val: number) => {
		setDockEdgeDelay(val);
		saveSetting("gloom-dock-edge-delay", val.toString());
	};

	const handleNotchEdgeDelayChange = (val: number) => {
		setNotchEdgeDelay(val);
		saveSetting("gloom-notch-edge-delay", val.toString());
	};

	const handleNotchDisplayChange = (val: string) => {
		setNotchDisplay(val);
		saveSetting("gloom-notch-display", val);
	};

	const handleDockDisplayChange = (val: string) => {
		setDockDisplay(val);
		saveSetting("gloom-dock-display", val);
	};

	const handleIslandStyleChange = (style: "notch" | "pill") => {
		setIslandStyle(style);
		saveSetting("gloom-island-style", style);
	};

	const handleThresholdChange = (val: number) => {
		setLowBatteryThreshold(val);
		saveSetting("gloom-low-battery-threshold", val.toString());
	};

	const handleScaleChange = (val: number) => {
		setScale(val);
		saveSetting("gloom-scale", val.toString());
	};

	const handleWidgetsChange = (config: WidgetConfig) => {
		setStatusWidgets(config);
		saveSetting("gloom-status-widgets", JSON.stringify(config));
	};

	// ── Theme handlers ──
	const handleThemeModeChange = async (mode: string) => {
		setThemeMode(mode);
		saveSetting("gloom-theme-mode", mode);

		if (mode === "adaptive") {
			try {
				const accentHex = await invoke<string>("get_system_accent_color");
				const hsl = hexToHsl(accentHex);
				setThemeSaturation(hsl.s / 100);
				saveSetting("gloom-theme-saturation", String(hsl.s / 100));
				setThemeBrightness(hsl.l / 100);
				saveSetting("gloom-theme-brightness", String(hsl.l / 100));
			} catch (e) {
				console.error("Failed to parse adaptive accent HSL:", e);
			}
		}
	};

	const handleThemeColorChange = (color: string) => {
		setThemeColor(color);
		saveSetting("gloom-theme-color", color);

		try {
			const hsl = hexToHsl(color);
			setThemeSaturation(hsl.s / 100);
			saveSetting("gloom-theme-saturation", String(hsl.s / 100));
			setThemeBrightness(hsl.l / 100);
			saveSetting("gloom-theme-brightness", String(hsl.l / 100));
		} catch (e) {
			console.error("Failed to parse custom color HSL:", e);
		}
	};

	const handleOpacityChange = (value: number) => {
		setThemeOpacity(value);
		saveSetting("gloom-theme-opacity", String(value));
	};

	const handleSaturationChange = (value: number) => {
		setThemeSaturation(value);
		saveSetting("gloom-theme-saturation", String(value));
	};

	const handleBrightnessChange = (value: number) => {
		setThemeBrightness(value);
		saveSetting("gloom-theme-brightness", String(value));
	};

	// ── City search ──
	const selectCity = async (city: {
		name: string;
		country: string;
		latitude: number;
		longitude: number;
	}) => {
		setCityName(city.name);
		setShowCityDropdown(false);
		setCitySearchResults([]);
		const saved =
			(await saveLocation("gloom-weather-lat", city.latitude.toString())) &&
			(await saveLocation("gloom-weather-lon", city.longitude.toString())) &&
			(await saveSetting("gloom-weather-city", city.name));
		if (saved) emit("weather-refresh", { lat: city.latitude, lon: city.longitude });
	};

	const handleCityClear = async () => {
		setCityName("");
		setShowCityDropdown(false);
		setCitySearchResults([]);
		const saved =
			(await saveLocation("gloom-weather-lat", null)) &&
			(await saveLocation("gloom-weather-lon", null)) &&
			(await saveSetting("gloom-weather-city", null));
		if (saved) emit("weather-refresh", true);
	};

	// ── Reset to defaults ──
	const resetToDefaults = async () => {
		try {
			const { ask } = await import("@tauri-apps/plugin-dialog");
			const confirmed = await ask(
				"All settings return to their defaults and Gloom restarts. Pinned apps and custom icons are kept.",
				{
					title: "Reset Gloom to Defaults",
					kind: "warning",
					okLabel: "Reset and Restart",
					cancelLabel: "Cancel"
				}
			);
			if (!confirmed) return;

			await invoke("reset_settings");

			// settings.json is cleared, but every window also caches gloom keys
			// in localStorage; drop them so the restart can't resurrect values.
			// The first-run/version sentinels are lifecycle markers, not
			// preferences — keeping them avoids replaying the splash, which
			// would also re-trigger the first-run autostart enable.
			const keep = new Set(["gloom-first-run", "gloom-app-version"]);
			for (const key of Object.keys(localStorage)) {
				if (key.startsWith("gloom-") && !keep.has(key)) localStorage.removeItem(key);
			}

			await invoke("restart_gloom").catch(console.error);
		} catch (e) {
			console.error("Reset failed:", e);
		}
	};

	// ── Export / Import ──
	// The backend opens the native dialog and owns the file path, so these
	// cannot be used to read or write arbitrary files from a webview.
	const handleExportSettings = async () => {
		setExportStatus("exporting");
		try {
			const filePath = await invoke<string | null>("export_settings_to_file");
			if (filePath) {
				setExportStatus("success");
				setTimeout(() => setExportStatus("idle"), 2000);
			} else {
				setExportStatus("idle");
			}
		} catch (e) {
			console.error("Export failed:", e);
			setExportStatus("error");
			setTimeout(() => setExportStatus("idle"), 3000);
		}
	};

	const handleImportSettings = async () => {
		setImportStatus("importing");
		try {
			const filePath = await invoke<string | null>("import_settings_from_file");
			if (filePath) {
				await loadAllSettings();
				setImportStatus("success");
				setTimeout(() => setImportStatus("idle"), 2000);
			} else {
				setImportStatus("idle");
			}
		} catch (e) {
			console.error("Import failed:", e);
			setImportStatus("error");
			setTimeout(() => setImportStatus("idle"), 3000);
		}
	};

	return {
		saveError,
		dismissSaveError,
		// System
		autostart,
		toggleAutostart,
		autoUpdate,
		toggleAutoUpdate,
		lowBatteryThreshold,
		handleThresholdChange,
		timeFormat24h,
		toggleTimeFormat24h,
		showUpdateIndicator,
		toggleUpdateIndicator,
		scale,
		handleScaleChange,
		cornersEnabled,
		toggleCorners,

		// Theme
		themeMode,
		handleThemeModeChange,
		themeColor,
		handleThemeColorChange,
		themeOpacity,
		handleOpacityChange,
		themeSaturation,
		handleSaturationChange,
		themeBrightness,
		handleBrightnessChange,

		// Notch
		notchMode,
		setNotchModeValue,
		notchEdgeDelay,
		handleNotchEdgeDelayChange,
		notchDisplay,
		handleNotchDisplayChange,
		islandStyle,
		handleIslandStyleChange,
		calendarEnabled,
		toggleCalendar,
		timerSoundEnabled,
		toggleTimerSound,
		musicModeEnabled,
		toggleMusicMode,
		notchRememberPanel,
		toggleNotchRememberPanel,
		notchDefaultPanel,
		changeNotchDefaultPanel,
		musicCompactNotch,
		toggleMusicCompactNotch,
		shelfPersist,
		toggleShelfPersist,
		mediaLayout,
		toggleMediaLayout,
		mediaAmbienceEnabled,
		toggleAmbience,
		mediaCompactGlowEnabled,
		toggleCompactGlow,

		// Weather
		weatherEnabled,
		toggleWeather,
		tempUnitFahrenheit,
		toggleTempUnit,
		cityName,
		setCityName,
		citySearchResults,
		showCityDropdown,
		setShowCityDropdown,
		selectCity,
		handleCityClear,

		// Widgets
		statusWidgets,
		handleWidgetsChange,

		// Dock
		dockEnabled,
		toggleDock,
		dockMode,
		setDockModeValue,
		dockEdgeDelay,
		handleDockEdgeDelayChange,
		dockDisplay,
		handleDockDisplayChange,
		dockPreviewEnabled,
		toggleDockPreview,
		dockIconOnly,
		toggleDockIconOnly,
		dockSeparatorEnabled,
		toggleDockSeparator,
		startIcon,
		handleStartIconChange,
		dockAdaptive,
		toggleDockAdaptive,
		dockWinNumberEnabled,
		toggleDockWinNumber,
		dockExtrasPosition,
		handleDockExtrasPositionChange,
		dockExtrasDrives,
		toggleDockExtrasDrives,
		dockExtrasHiddenDrives,
		toggleDockExtrasDrive,
		dockExtrasRecycleBin,
		toggleDockExtrasRecycleBin,
		dockTaskView,
		toggleDockTaskView,
		dockExtrasFolders,
		toggleDockExtraFolder,
		dockExtrasCustomFolders,
		handleAddDockExtraFolder,
		handleRemoveDockExtraFolder,

		// Overlays
		volumeOverlayEnabled,
		toggleVolumeOverlay,
		volumeEdgeEnabled,
		toggleVolumeEdge,
		brightnessOverlayEnabled,
		toggleBrightnessOverlay,
		brightnessEdgeEnabled,
		toggleBrightnessEdge,

		// Updates
		updateStatus,
		updateVersion,
		appVersion,
		checkForUpdates,
		installUpdate,

		// Import / Export
		exportStatus,
		importStatus,
		handleExportSettings,
		handleImportSettings,

		// Utilities
		restartGloom: () => invoke("restart_gloom"),
		resetToDefaults,
		quitGloom: () => invoke("quit_gloom")
	};
}
