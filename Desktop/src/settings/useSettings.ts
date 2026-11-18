import { useState, useEffect, useCallback } from "react";
import { parseCcLayout, parsePanelOrder, type CcLayout, type PanelId } from "../panelOrder";
import { invoke } from "@tauri-apps/api/core";
import { listen, emit } from "@tauri-apps/api/event";
import { getVersion } from "@tauri-apps/api/app";
import type { UpdateCheckResult } from "../updater";
import { useSettingsSync } from "../hooks/useSettingsSync";
import { hexToHsl, DEFAULT_ACCENT_COLOR, STYLE_OPACITY, type ThemePreset } from "../theme";
import { DEFAULT_DUAL_CLOCK_ZONE } from "../dualClock";
import type { WidgetConfig, MonitorInfo, MonitorMode } from "./types";

function saveSetting(key: string, value: string) {
  localStorage.setItem(key, value);
  invoke("save_setting", { key, value }).catch(console.error);
}

function readBool(val: string | null): boolean {
  return val === "true";
}

export const DOCK_MENU_IDS = ["calendar", "settings", "taskmgr", "restart", "clearicons", "quit"];

function parseMenuItems(val: unknown): string[] {
  try {
    const parsed = typeof val === "string" ? JSON.parse(val) : val;
    return Array.isArray(parsed) ? parsed.filter((id): id is string => DOCK_MENU_IDS.includes(id)) : DOCK_MENU_IDS;
  } catch {
    return DOCK_MENU_IDS;
  }
}

export interface HiddenDockApp { id: string; name: string }

function parseHiddenApps(val: unknown): HiddenDockApp[] {
  try {
    const parsed = typeof val === "string" ? JSON.parse(val) : val;
    return Array.isArray(parsed)
      ? parsed.filter((h): h is HiddenDockApp => !!h && typeof h.id === "string" && typeof h.name === "string")
      : [];
  } catch {
    return [];
  }
}

function parseDrives(val: string | null): string[] {
  try {
    const parsed = JSON.parse(val ?? "[]");
    return Array.isArray(parsed) ? parsed.filter((d): d is string => typeof d === "string") : [];
  } catch {
    return [];
  }
}

export function useSettings() {
  const [autostart, setAutostart] = useState(false);
  const [weatherEnabled, setWeatherEnabled] = useState(true);
  const [calendarEnabled, setCalendarEnabled] = useState(true);
  const [timerEnabled, setTimerEnabled] = useState(true);
  const [timerSoundEnabled, setTimerSoundEnabled] = useState(true);
  const [notchTimerProgress, setNotchTimerProgress] = useState(
    () => localStorage.getItem("gloom-notch-timer-progress") !== "false"
  );
  const [stopwatchEnabled, setStopwatchEnabled] = useState(true);
  const [dualClockEnabled, setDualClockEnabled] = useState(
    () => localStorage.getItem("gloom-dual-clock-enabled") === "true"
  );
  const [dualClockZone, setDualClockZone] = useState(
    () => localStorage.getItem("gloom-dual-clock-zone") || DEFAULT_DUAL_CLOCK_ZONE
  );
  const [dualClockLabel, setDualClockLabel] = useState(
    () => localStorage.getItem("gloom-dual-clock-label") || DEFAULT_DUAL_CLOCK_ZONE
  );
  const [musicModeEnabled, setMusicModeEnabled] = useState(true);
  const [musicCompactNotch, setMusicCompactNotch] = useState(true);
  const [agentApi, setAgentApi] = useState(() => localStorage.getItem("gloom-agent-api") === "true");
  const [notchScrollVolume, setNotchScrollVolume] = useState(
    () => localStorage.getItem("gloom-notch-scroll-volume") === "true"
  );
  const [rememberMode, setRememberMode] = useState(
    () => localStorage.getItem("gloom-notch-remember-mode") === "true"
  );
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
  const [mediaArtworkThemeEnabled, setMediaArtworkThemeEnabled] = useState(
    () => localStorage.getItem("gloom-media-artwork-theme") === "true"
  );
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
  const [legacyMode, setLegacyMode] = useState(
    () => localStorage.getItem("gloom-legacy-mode") === "true"
  );
  const [ramSaver, setRamSaver] = useState(
    () => localStorage.getItem("gloom-ram-saver") === "true"
  );
  const [ramUltra, setRamUltra] = useState(
    () => localStorage.getItem("gloom-ram-ultra") === "true"
  );
  const [tempUnitFahrenheit, setTempUnitFahrenheit] = useState(false);
  const [cityName, setCityName] = useState("");
  const [cityQuery, setCityQuery] = useState("");
  const [citySearchResults, setCitySearchResults] = useState<
    Array<{ name: string; country: string; latitude: number; longitude: number }>
  >([]);
  const [showCityDropdown, setShowCityDropdown] = useState(false);
  const [statusWidgets, setStatusWidgets] = useState<WidgetConfig>({
    left: ["weather"],
    right: ["battery"],
  });
  const [dockEnabled, setDockEnabled] = useState(true);
  const [dockPreviewEnabled, setDockPreviewEnabled] = useState(true);
  const [dockAttentionEnabled, setDockAttentionEnabled] = useState(true);
  const [dockConsoleWindows, setDockConsoleWindows] = useState(() => localStorage.getItem("gloom-dock-console-windows") === "true");
  const [dockSearchEnabled, setDockSearchEnabled] = useState(true);
  const [dockSearchProvider, setDockSearchProvider] = useState(
    () => localStorage.getItem("gloom-dock-search-provider") || "windows"
  );
  const [dockSearchShortcut, setDockSearchShortcut] = useState(
    () => localStorage.getItem("gloom-dock-search-shortcut") || ""
  );
  const [dockCalendarEnabled, setDockCalendarEnabled] = useState(true);
  const [dockTray, setDockTray] = useState(
    () => localStorage.getItem("gloom-dock-tray-enabled") !== "false"
  );
  const [dockShowDesktop, setDockShowDesktop] = useState(
    () => localStorage.getItem("gloom-dock-show-desktop-corner") === "true"
  );
  const [panelControlCenter, setPanelControlCenter] = useState(
    () => localStorage.getItem("gloom-panel-control-center-enabled") !== "false"
  );
  const [panelOrder, setPanelOrder] = useState<PanelId[] | null>(
    () => parsePanelOrder(localStorage.getItem("gloom-panel-order"))
  );
  const [ccLayout, setCcLayout] = useState<CcLayout>(
    () => parseCcLayout(localStorage.getItem("gloom-cc-layout"))
  );
  const [panelStatus, setPanelStatus] = useState(
    () => localStorage.getItem("gloom-panel-status-enabled") !== "false"
  );
  const [panelShelf, setPanelShelf] = useState(
    () => localStorage.getItem("gloom-panel-shelf-enabled") !== "false"
  );
  const [dockTaskView, setDockTaskView] = useState(
    () => localStorage.getItem("gloom-dock-taskview-enabled") === "true"
  );
  const [dockRecycle, setDockRecycle] = useState(
    () => localStorage.getItem("gloom-dock-recycle-enabled") === "true"
  );
  const [dockMenuItems, setDockMenuItems] = useState<string[]>(() => parseMenuItems(localStorage.getItem("gloom-dock-menu-items")));
  const [dockIndicator, setDockIndicator] = useState(() => localStorage.getItem("gloom-dock-indicator") ?? "bar");
  const [panelScrollDirection, setPanelScrollDirection] = useState(() => localStorage.getItem("gloom-notch-scroll-direction") ?? "down");
  const [weekStart, setWeekStart] = useState(() => localStorage.getItem("gloom-week-start") ?? "auto");
  const [dockDrives, setDockDrives] = useState<string[]>(() => parseDrives(localStorage.getItem("gloom-dock-drives")));
  const [hiddenDockApps, setHiddenDockApps] = useState<HiddenDockApp[]>(() => parseHiddenApps(localStorage.getItem("gloom-dock-hidden-apps")));
  const [dockIconOnly, setDockIconOnly] = useState(
    () => localStorage.getItem("gloom-dock-icon-only") === "true"
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
  const [dockMixedReorder, setDockMixedReorder] = useState(
    () => localStorage.getItem("gloom-dock-mixed-reorder") === "true"
  );
  const [dockMode, setDockMode] = useState(() => {
    const raw = localStorage.getItem("gloom-dock-mode") || "smart";
    return raw === "auto-hide" ? "smart" : raw;
  });
  const [notchMode, setNotchMode] = useState("fixed");
  const [dockMonitorMode, setDockMonitorMode] = useState<MonitorMode>(
    () => (localStorage.getItem("gloom-dock-monitor-mode") as MonitorMode) || "primary"
  );
  const [dockMonitorId, setDockMonitorId] = useState(
    () => localStorage.getItem("gloom-dock-monitor-id") || ""
  );
  const [notchMonitorMode, setNotchMonitorMode] = useState<MonitorMode>(
    () => (localStorage.getItem("gloom-notch-monitor-mode") as MonitorMode) || "primary"
  );
  const [notchMonitorId, setNotchMonitorId] = useState(
    () => localStorage.getItem("gloom-notch-monitor-id") || ""
  );
  const [dockModeByMonitor, setDockModeByMonitor] = useState<Record<string, string>>({});
  const [notchModeByMonitor, setNotchModeByMonitor] = useState<Record<string, string>>({});
  const [monitors, setMonitors] = useState<MonitorInfo[]>([]);
  const [lowBatteryThreshold, setLowBatteryThreshold] = useState(20);
  const [hasBattery, setHasBattery] = useState(() => localStorage.getItem("gloom-has-battery") !== "false");
  const [updateStatus, setUpdateStatus] = useState<
    "idle" | "checking" | "available" | "uptodate" | "error" | "downloading" | "installing"
  >("idle");
  const [updateVersion, setUpdateVersion] = useState("");
  const [appVersion, setAppVersion] = useState("");
  const [autoUpdate, setAutoUpdate] = useState(
    () => localStorage.getItem("gloom-auto-update") === "true"
  );
  const [prereleaseChannel, setPrereleaseChannel] = useState(
    () => localStorage.getItem("gloom-update-channel") === "prerelease"
  );
  const [scale, setScale] = useState(
    () => parseFloat(localStorage.getItem("gloom-scale") || "1.0")
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
  const [themeStyle, setThemeStyle] = useState(() => localStorage.getItem("gloom-theme-style") || "solid");
  const [themeAccent, setThemeAccent] = useState(() => localStorage.getItem("gloom-theme-accent") || "mono");
  const [themeAccentColor, setThemeAccentColor] = useState(
    () => localStorage.getItem("gloom-theme-accent-color") || DEFAULT_ACCENT_COLOR
  );
  const [exportStatus, setExportStatus] = useState<"idle" | "exporting" | "success" | "error">("idle");
  const [importStatus, setImportStatus] = useState<"idle" | "importing" | "success" | "error">("idle");
  const [resetStatus, setResetStatus] = useState<"idle" | "confirm" | "resetting">("idle");
  const [uninstallStatus, setUninstallStatus] = useState<"idle" | "confirm" | "uninstalling" | "error">("idle");
  const [uninstallError, setUninstallError] = useState("");

  // ── Load all settings from backend + localStorage ──
  const loadAllSettings = useCallback(async () => {
    try {
      const settings: Record<string, string> = await invoke("load_settings");
      const getVal = (key: string) => {
        const val = settings[key];
        if (val !== undefined && val !== null) return String(val);
        return localStorage.getItem(key);
      };

      const apply = <T>(val: string | null, setter: (v: T) => void, transform: (v: string) => T) => {
        if (val !== null) setter(transform(val));
      };

      apply(getVal("gloom-weather-enabled"), setWeatherEnabled, readBool);
      apply(getVal("gloom-calendar-enabled"), setCalendarEnabled, readBool);
      apply(getVal("gloom-timer-enabled") ?? getVal("gloom-calendar-enabled"), setTimerEnabled, readBool);
      apply(getVal("gloom-timer-sound-enabled"), setTimerSoundEnabled, readBool);
      apply(getVal("gloom-notch-timer-progress"), setNotchTimerProgress, readBool);
      apply(getVal("gloom-stopwatch-enabled") ?? getVal("gloom-calendar-enabled"), setStopwatchEnabled, readBool);
      apply(getVal("gloom-dual-clock-enabled"), setDualClockEnabled, readBool);
      apply(getVal("gloom-dual-clock-zone"), setDualClockZone, (v) => v);
      apply(getVal("gloom-dual-clock-label"), setDualClockLabel, (v) => v);
      apply(getVal("gloom-music-mode-enabled"), setMusicModeEnabled, readBool);
      apply(getVal("gloom-music-compact-notch"), setMusicCompactNotch, readBool);
      apply(getVal("gloom-notch-scroll-volume"), setNotchScrollVolume, (v) => v === "true");
      apply(getVal("gloom-agent-api"), setAgentApi, (v) => v === "true");
      apply(getVal("gloom-shelf-persist"), setShelfPersist, (v) => v === "true");
      apply(getVal("gloom-notch-remember-mode"), setRememberMode, (v) => v === "true");
      apply(getVal("gloom-volume-overlay-enabled"), setVolumeOverlayEnabled, readBool);
      apply(getVal("gloom-brightness-overlay-enabled"), setBrightnessOverlayEnabled, readBool);
      apply(getVal("gloom-media-ambience-enabled"), setMediaAmbienceEnabled, readBool);
      apply(getVal("gloom-media-compact-glow-enabled"), setMediaCompactGlowEnabled, readBool);
      apply(getVal("gloom-media-artwork-theme"), setMediaArtworkThemeEnabled, (v) => v === "true");
      apply(getVal("gloom-corners-enabled"), setCornersEnabled, readBool);
      apply(getVal("gloom-time-format-24h"), setTimeFormat24h, readBool);
      apply(getVal("gloom-legacy-mode"), setLegacyMode, readBool);
      apply(getVal("gloom-show-update-indicator"), setShowUpdateIndicator, readBool);
      apply(getVal("gloom-ram-saver"), setRamSaver, (v) => v === "true");
      apply(getVal("gloom-ram-ultra"), setRamUltra, readBool);
      apply(getVal("gloom-auto-update"), setAutoUpdate, readBool);
      apply(getVal("gloom-update-channel"), setPrereleaseChannel, (v) => v === "prerelease");
      apply(getVal("gloom-volume-edge-enabled"), setVolumeEdgeEnabled, readBool);
      apply(getVal("gloom-brightness-edge-enabled"), setBrightnessEdgeEnabled, readBool);
      apply(getVal("gloom-dock-enabled"), setDockEnabled, readBool);
      apply(getVal("gloom-dock-preview-enabled"), setDockPreviewEnabled, readBool);
      apply(getVal("gloom-dock-attention-enabled"), setDockAttentionEnabled, (v) => v !== "false");
      apply(getVal("gloom-dock-console-windows"), setDockConsoleWindows, readBool);
      apply(getVal("gloom-dock-search-enabled"), setDockSearchEnabled, readBool);
      apply(getVal("gloom-dock-search-provider"), setDockSearchProvider, (v) => v);
      apply(getVal("gloom-dock-search-shortcut"), setDockSearchShortcut, (v) => v);
      apply(getVal("gloom-dock-calendar-enabled"), setDockCalendarEnabled, readBool);
      apply(getVal("gloom-dock-tray-enabled"), setDockTray, (v) => v !== "false");
      apply(getVal("gloom-dock-taskview-enabled"), setDockTaskView, readBool);
      apply(getVal("gloom-dock-show-desktop-corner"), setDockShowDesktop, readBool);
      apply(getVal("gloom-panel-control-center-enabled"), setPanelControlCenter, (v) => v !== "false");
      apply(getVal("gloom-panel-shelf-enabled"), setPanelShelf, (v) => v !== "false");
      apply(getVal("gloom-panel-status-enabled"), setPanelStatus, (v) => v !== "false");
      apply(getVal("gloom-panel-order"), setPanelOrder, (v) => parsePanelOrder(v));
      apply(getVal("gloom-cc-layout"), setCcLayout, (v) => parseCcLayout(v));
      apply(getVal("gloom-dock-recycle-enabled"), setDockRecycle, readBool);
      apply(getVal("gloom-week-start"), setWeekStart, (v) => v ?? "auto");
      apply(getVal("gloom-notch-scroll-direction"), setPanelScrollDirection, (v) => v);
      apply(getVal("gloom-dock-indicator"), setDockIndicator, (v) => v);
      apply(getVal("gloom-dock-menu-items"), setDockMenuItems, parseMenuItems);
      apply(getVal("gloom-dock-drives"), setDockDrives, parseDrives);
      apply(getVal("gloom-dock-hidden-apps"), setHiddenDockApps, parseHiddenApps);
      apply(getVal("gloom-dock-icon-only"), setDockIconOnly, readBool);
      apply(getVal("gloom-start-icon"), setStartIcon, (v) => v);
      apply(getVal("gloom-dock-adaptive"), setDockAdaptive, readBool);
      apply(getVal("gloom-dock-win-number-enabled"), setDockWinNumberEnabled, readBool);
      apply(getVal("gloom-dock-mixed-reorder"), setDockMixedReorder, readBool);

      apply(getVal("gloom-temp-unit"), setTempUnitFahrenheit, (v) => v === "fahrenheit");
      apply(getVal("gloom-scale"), setScale, parseFloat);
      apply(getVal("gloom-low-battery-threshold"), setLowBatteryThreshold, parseInt);

      apply(getVal("gloom-notch-mode"), setNotchMode, (v) => (v === "auto-hide" ? "smart" : v));
      apply(getVal("gloom-dock-mode"), setDockMode, (v) => (v === "auto-hide" ? "smart" : v));

      apply(getVal("gloom-dock-monitor-mode"), setDockMonitorMode, (v) => v as MonitorMode);
      apply(getVal("gloom-dock-monitor-id"), setDockMonitorId, (v) => v);
      apply(getVal("gloom-notch-monitor-mode"), setNotchMonitorMode, (v) => v as MonitorMode);
      apply(getVal("gloom-notch-monitor-id"), setNotchMonitorId, (v) => v);

      const dockByMonVal = getVal("gloom-dock-mode-by-monitor");
      if (dockByMonVal) {
        try { setDockModeByMonitor(JSON.parse(dockByMonVal)); } catch {}
      }
      const notchByMonVal = getVal("gloom-notch-mode-by-monitor");
      if (notchByMonVal) {
        try { setNotchModeByMonitor(JSON.parse(notchByMonVal)); } catch {}
      }

      const savedCity = getVal("gloom-weather-city");
      if (savedCity) {
        setCityName(savedCity);
        setCityQuery(savedCity);
      }

      apply(getVal("gloom-theme-mode"), setThemeMode, (v) => v);
      apply(getVal("gloom-theme-color"), setThemeColor, (v) => v);
      apply(getVal("gloom-theme-opacity"), setThemeOpacity, parseFloat);
      apply(getVal("gloom-theme-saturation"), setThemeSaturation, parseFloat);
      apply(getVal("gloom-theme-brightness"), setThemeBrightness, parseFloat);
      apply(getVal("gloom-theme-style"), setThemeStyle, (v) => v);
      apply(getVal("gloom-theme-accent"), setThemeAccent, (v) => v);
      apply(getVal("gloom-theme-accent-color"), setThemeAccentColor, (v) => v);

      const widgetsVal = getVal("gloom-status-widgets");
      if (widgetsVal) {
        try {
          const parsed = JSON.parse(widgetsVal);
          if (parsed && Array.isArray(parsed.left) && Array.isArray(parsed.right)) {
            setStatusWidgets({ ...parsed, left: parsed.left.slice(0, 2), right: parsed.right.slice(0, 2) });
          }
        } catch {}
      }
    } catch (e) {
      console.error("Failed to load settings:", e);
    }
  }, []);

  // ── Initialize on mount ──
  useEffect(() => {
    loadAllSettings();

    invoke<boolean>("get_autostart")
      .then(setAutostart)
      .catch(() => {});

    getVersion()
      .then((ver) => setAppVersion(ver))
      .catch(() => setAppVersion(""));

    invoke<boolean>("has_battery").then((present) => {
      setHasBattery(present);
      localStorage.setItem("gloom-has-battery", String(present));
    }).catch(() => {});

    checkForUpdates(false);
  }, []);

  useEffect(() => {
    const refreshMonitors = () => {
      invoke<MonitorInfo[]>("get_monitors").then(setMonitors).catch(() => {});
    };
    refreshMonitors();
    const unlisten = listen("monitors-changed", refreshMonitors);
    return () => {
      unlisten.then((fn) => fn());
    };
  }, []);

  // ── Sync settings from other windows ──
  useSettingsSync({
    "gloom-dock-mode": setDockMode,
    "gloom-notch-mode": setNotchMode,
    "gloom-dock-monitor-mode": (v) => setDockMonitorMode(v as MonitorMode),
    "gloom-dock-monitor-id": setDockMonitorId,
    "gloom-notch-monitor-mode": (v) => setNotchMonitorMode(v as MonitorMode),
    "gloom-notch-monitor-id": setNotchMonitorId,
    "gloom-dock-mode-by-monitor": (v) => { try { setDockModeByMonitor(JSON.parse(v)); } catch {} },
    "gloom-notch-mode-by-monitor": (v) => { try { setNotchModeByMonitor(JSON.parse(v)); } catch {} },
    "gloom-dock-enabled": setDockEnabled,
    "gloom-dock-tray-enabled": (v) => setDockTray(v !== false && v !== "false"),
    "gloom-dock-taskview-enabled": setDockTaskView,
    "gloom-dock-show-desktop-corner": setDockShowDesktop,
    "gloom-panel-control-center-enabled": (v) => setPanelControlCenter(v !== false && v !== "false"),
    "gloom-panel-shelf-enabled": (v) => setPanelShelf(v !== false && v !== "false"),
    "gloom-panel-status-enabled": (v) => setPanelStatus(v !== false && v !== "false"),
    "gloom-panel-order": (v) => setPanelOrder(parsePanelOrder(v === null ? null : String(v))),
    "gloom-cc-layout": (v) => setCcLayout(parseCcLayout(v === null ? null : String(v))),
    "gloom-dock-recycle-enabled": setDockRecycle,
    "gloom-week-start": (v) => setWeekStart(String(v)),
    "gloom-notch-scroll-direction": (v) => setPanelScrollDirection(String(v)),
    "gloom-dock-indicator": (v) => setDockIndicator(String(v)),
    "gloom-dock-menu-items": (v) => setDockMenuItems(parseMenuItems(v)),
    "gloom-dock-drives": (v) => setDockDrives(parseDrives(v)),
    "gloom-dock-hidden-apps": (v) => setHiddenDockApps(parseHiddenApps(v)),
    "gloom-dock-icon-only": setDockIconOnly,
    "gloom-start-icon": setStartIcon,
    "gloom-dock-adaptive": setDockAdaptive,
    "gloom-dock-win-number-enabled": setDockWinNumberEnabled,
    "gloom-dock-preview-enabled": setDockPreviewEnabled,
    "gloom-dock-attention-enabled": (v) => setDockAttentionEnabled(v !== false && v !== "false"),
    "gloom-dock-console-windows": setDockConsoleWindows,
    "gloom-dock-search-enabled": setDockSearchEnabled,
    "gloom-dock-search-provider": (v) => setDockSearchProvider(String(v)),
    "gloom-dock-search-shortcut": (v) => setDockSearchShortcut(String(v)),
    "gloom-dock-calendar-enabled": setDockCalendarEnabled,
    "gloom-dock-mixed-reorder": setDockMixedReorder,
    "gloom-weather-enabled": setWeatherEnabled,
    "gloom-calendar-enabled": setCalendarEnabled,
    "gloom-timer-enabled": setTimerEnabled,
    "gloom-timer-sound-enabled": setTimerSoundEnabled,
    "gloom-notch-timer-progress": setNotchTimerProgress,
    "gloom-stopwatch-enabled": setStopwatchEnabled,
    "gloom-dual-clock-enabled": setDualClockEnabled,
    "gloom-dual-clock-zone": (v) => setDualClockZone(String(v)),
    "gloom-dual-clock-label": (v) => setDualClockLabel(String(v)),
    "gloom-music-mode-enabled": setMusicModeEnabled,
    "gloom-music-compact-notch": setMusicCompactNotch,
    "gloom-notch-scroll-volume": setNotchScrollVolume,
    "gloom-agent-api": setAgentApi,
    "gloom-shelf-persist": setShelfPersist,
    "gloom-notch-remember-mode": setRememberMode,
    "gloom-media-ambience-enabled": setMediaAmbienceEnabled,
    "gloom-media-compact-glow-enabled": setMediaCompactGlowEnabled,
    "gloom-media-artwork-theme": setMediaArtworkThemeEnabled,
    "gloom-media-layout": setMediaLayout,
    "gloom-corners-enabled": setCornersEnabled,
    "gloom-show-update-indicator": setShowUpdateIndicator,
    "gloom-time-format-24h": setTimeFormat24h,
    "gloom-legacy-mode": setLegacyMode,
    "gloom-ram-saver": (v) => setRamSaver(v === true || v === "true"),
    "gloom-ram-ultra": (v) => setRamUltra(v === true || v === "true"),
    "gloom-low-battery-threshold": setLowBatteryThreshold,
    "gloom-scale": setScale,
    "gloom-temp-unit": (v) => setTempUnitFahrenheit(v === "fahrenheit"),
    "gloom-auto-update": setAutoUpdate,
    "gloom-update-channel": (v) => setPrereleaseChannel(v === "prerelease"),
    "gloom-volume-overlay-enabled": setVolumeOverlayEnabled,
    "gloom-volume-edge-enabled": setVolumeEdgeEnabled,
    "gloom-brightness-overlay-enabled": setBrightnessOverlayEnabled,
    "gloom-brightness-edge-enabled": setBrightnessEdgeEnabled,
    "gloom-theme-mode": setThemeMode,
    "gloom-theme-color": setThemeColor,
    "gloom-theme-opacity": setThemeOpacity,
    "gloom-theme-saturation": setThemeSaturation,
    "gloom-theme-brightness": setThemeBrightness,
    "gloom-theme-style": (v) => setThemeStyle(String(v)),
    "gloom-theme-accent": (v) => setThemeAccent(String(v)),
    "gloom-theme-accent-color": (v) => setThemeAccentColor(String(v)),
    "gloom-weather-city": (v) => {
      setCityName(v || "");
      setCityQuery(v || "");
    },
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
    if (cityQuery.trim().length < 2 || cityQuery === cityName) {
      setCitySearchResults([]);
      setShowCityDropdown(false);
      return;
    }

    const timeout = setTimeout(async () => {
      try {
        const res = await fetch(
          `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(cityQuery)}&count=5&language=en&format=json`
        );
        const data = await res.json();
        if (data.results && data.results.length > 0) {
          setCitySearchResults(
            data.results.map((r: any) => ({
              name: r.name,
              country: r.country || "",
              latitude: r.latitude,
              longitude: r.longitude,
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
  }, [cityQuery, cityName]);

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
    const unlisten = listen<{ status: string; progress?: number }>("auto-update-status", (event) => {
      switch (event.payload.status) {
        case "downloading":
          setUpdateStatus("downloading");
          break;
        case "installing":
          setUpdateStatus("installing");
          break;
        case "done":
          setUpdateStatus((prev) => (prev === "downloading" || prev === "installing" ? "idle" : prev));
          break;
      }
    });
    return () => {
      unlisten.then((fn) => fn());
    };
  }, []);

  // ── Autostart ──
  const toggleAutostart = async () => {
    try {
      const next = !(await invoke<boolean>("get_autostart"));
      await invoke("set_autostart", { enabled: next });
      setAutostart(next);
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

  const toggleTimerEnabled = () => {
    const next = !timerEnabled;
    setTimerEnabled(next);
    saveSetting("gloom-timer-enabled", String(next));
  };

  const toggleTimerSound = () => {
    const next = !timerSoundEnabled;
    setTimerSoundEnabled(next);
    saveSetting("gloom-timer-sound-enabled", String(next));
  };

  const toggleNotchTimerProgress = () => {
    const next = !notchTimerProgress;
    setNotchTimerProgress(next);
    saveSetting("gloom-notch-timer-progress", String(next));
  };

  const toggleStopwatchEnabled = () => {
    const next = !stopwatchEnabled;
    setStopwatchEnabled(next);
    saveSetting("gloom-stopwatch-enabled", String(next));
  };

  const toggleDualClock = () => {
    const next = !dualClockEnabled;
    setDualClockEnabled(next);
    saveSetting("gloom-dual-clock-enabled", String(next));
  };

  const selectDualClockZone = ({ label, zone }: { label: string; zone: string }) => {
    setDualClockZone(zone);
    setDualClockLabel(label);
    saveSetting("gloom-dual-clock-zone", zone);
    saveSetting("gloom-dual-clock-label", label);
  };

  const toggleMusicMode = () => {
    const next = !musicModeEnabled;
    setMusicModeEnabled(next);
    saveSetting("gloom-music-mode-enabled", String(next));
  };

  const toggleMusicCompactNotch = () => {
    const next = !musicCompactNotch;
    setMusicCompactNotch(next);
    saveSetting("gloom-music-compact-notch", String(next));
  };

  const toggleAgentApi = () => {
    const next = !agentApi;
    setAgentApi(next);
    saveSetting("gloom-agent-api", String(next));
  };

  const toggleNotchScrollVolume = () => {
    const next = !notchScrollVolume;
    setNotchScrollVolume(next);
    saveSetting("gloom-notch-scroll-volume", String(next));
  };

  const toggleRememberMode = () => {
    const next = !rememberMode;
    setRememberMode(next);
    saveSetting("gloom-notch-remember-mode", String(next));
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

  const toggleArtworkTheme = () => {
    const next = !mediaArtworkThemeEnabled;
    setMediaArtworkThemeEnabled(next);
    saveSetting("gloom-media-artwork-theme", String(next));
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

  const toggleLegacyMode = () => {
    const next = !legacyMode;
    setLegacyMode(next);
    saveSetting("gloom-legacy-mode", String(next));
  };

  const toggleRamSaver = () => {
    const next = !ramSaver;
    setRamSaver(next);
    saveSetting("gloom-ram-saver", String(next));
  };

  const toggleRamUltra = () => {
    const next = !ramUltra;
    setRamUltra(next);
    saveSetting("gloom-ram-ultra", String(next));
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

  const toggleDockConsoleWindows = () => {
    const next = !dockConsoleWindows;
    setDockConsoleWindows(next);
    saveSetting("gloom-dock-console-windows", String(next));
  };

  const toggleDockAttention = () => {
    const next = !dockAttentionEnabled;
    setDockAttentionEnabled(next);
    saveSetting("gloom-dock-attention-enabled", String(next));
  };

  const handleDockSearchProviderChange = (provider: string) => {
    setDockSearchProvider(provider);
    saveSetting("gloom-dock-search-provider", provider);
  };

  const handleDockSearchShortcutChange = (shortcut: string) => {
    setDockSearchShortcut(shortcut);
    saveSetting("gloom-dock-search-shortcut", shortcut);
  };

  const toggleDockSearch = () => {
    const next = !dockSearchEnabled;
    setDockSearchEnabled(next);
    saveSetting("gloom-dock-search-enabled", String(next));
  };

  const toggleDockTray = () => {
    const next = !dockTray;
    setDockTray(next);
    saveSetting("gloom-dock-tray-enabled", String(next));
  };

  const toggleDockShowDesktop = () => {
    const next = !dockShowDesktop;
    setDockShowDesktop(next);
    saveSetting("gloom-dock-show-desktop-corner", String(next));
  };

  const togglePanelControlCenter = () => {
    const next = !panelControlCenter;
    setPanelControlCenter(next);
    saveSetting("gloom-panel-control-center-enabled", String(next));
  };

  const reorderPanels = (order: PanelId[]) => {
    setPanelOrder(order);
    saveSetting("gloom-panel-order", JSON.stringify(order));
  };

  const changeCcLayout = (next: CcLayout) => {
    setCcLayout(next);
    saveSetting("gloom-cc-layout", JSON.stringify(next));
  };

  const resetCcLayout = () => {
    setCcLayout({});
    localStorage.removeItem("gloom-cc-layout");
    invoke("save_setting", { key: "gloom-cc-layout", value: null }).catch(() => {});
  };

  const resetPanelOrder = () => {
    setPanelOrder(null);
    localStorage.removeItem("gloom-panel-order");
    invoke("save_setting", { key: "gloom-panel-order", value: null }).catch(() => {});
  };

  const togglePanelStatus = () => {
    const next = !panelStatus;
    setPanelStatus(next);
    saveSetting("gloom-panel-status-enabled", String(next));
  };

  const togglePanelShelf = () => {
    const next = !panelShelf;
    setPanelShelf(next);
    saveSetting("gloom-panel-shelf-enabled", String(next));
  };

  const toggleDockTaskView = () => {
    const next = !dockTaskView;
    setDockTaskView(next);
    saveSetting("gloom-dock-taskview-enabled", String(next));
  };

  const toggleDockRecycle = () => {
    const next = !dockRecycle;
    setDockRecycle(next);
    saveSetting("gloom-dock-recycle-enabled", String(next));
  };

  const toggleDockDrive = (letter: string) => {
    const next = dockDrives.includes(letter) ? dockDrives.filter((d) => d !== letter) : [...dockDrives, letter].sort();
    setDockDrives(next);
    saveSetting("gloom-dock-drives", JSON.stringify(next));
  };

  const toggleDockMenuItem = (id: string) => {
    const next = dockMenuItems.includes(id) ? dockMenuItems.filter((x) => x !== id) : DOCK_MENU_IDS.filter((x) => x === id || dockMenuItems.includes(x));
    setDockMenuItems(next);
    saveSetting("gloom-dock-menu-items", JSON.stringify(next));
  };

  const changeDockIndicator = (value: string) => {
    setDockIndicator(value);
    saveSetting("gloom-dock-indicator", value);
  };

  const changePanelScrollDirection = (value: string) => {
    setPanelScrollDirection(value);
    saveSetting("gloom-notch-scroll-direction", value);
  };

  const changeWeekStart = (value: string) => {
    setWeekStart(value);
    saveSetting("gloom-week-start", value);
  };

  const restoreHiddenDockApp = (id: string) => {
    const next = hiddenDockApps.filter((h) => h.id !== id);
    setHiddenDockApps(next);
    saveSetting("gloom-dock-hidden-apps", JSON.stringify(next));
  };

  const toggleDockCalendar = () => {
    const next = !dockCalendarEnabled;
    setDockCalendarEnabled(next);
    saveSetting("gloom-dock-calendar-enabled", String(next));
  };

  const toggleDockMixedReorder = () => {
    const next = !dockMixedReorder;
    setDockMixedReorder(next);
    saveSetting("gloom-dock-mixed-reorder", String(next));
  };

  const toggleDockIconOnly = () => {
    const next = !dockIconOnly;
    setDockIconOnly(next);
    saveSetting("gloom-dock-icon-only", String(next));
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

  const toggleAutoUpdate = () => {
    const next = !autoUpdate;
    setAutoUpdate(next);
    saveSetting("gloom-auto-update", String(next));
  };

  const togglePrereleaseChannel = async () => {
    const next = !prereleaseChannel;
    const value = next ? "prerelease" : "stable";
    setPrereleaseChannel(next);
    localStorage.setItem("gloom-update-channel", value);
    await invoke("save_setting", { key: "gloom-update-channel", value }).catch(console.error);
    checkForUpdates();
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

  const setDockMonitorModeValue = (mode: MonitorMode) => {
    setDockMonitorMode(mode);
    saveSetting("gloom-dock-monitor-mode", mode);
  };

  const setDockMonitorIdValue = (id: string) => {
    setDockMonitorId(id);
    saveSetting("gloom-dock-monitor-id", id);
  };

  const setNotchMonitorModeValue = (mode: MonitorMode) => {
    setNotchMonitorMode(mode);
    saveSetting("gloom-notch-monitor-mode", mode);
  };

  const setNotchMonitorIdValue = (id: string) => {
    setNotchMonitorId(id);
    saveSetting("gloom-notch-monitor-id", id);
  };

  const setDockModeForMonitor = (monitorId: string, mode: string | null) => {
    setDockModeByMonitor((prev) => {
      const next = { ...prev };
      if (mode === null) delete next[monitorId]; else next[monitorId] = mode;
      saveSetting("gloom-dock-mode-by-monitor", JSON.stringify(next));
      return next;
    });
  };

  const replaceDockModesByMonitor = (next: Record<string, string>) => {
    setDockModeByMonitor(next);
    saveSetting("gloom-dock-mode-by-monitor", JSON.stringify(next));
  };

  const replaceNotchModesByMonitor = (next: Record<string, string>) => {
    setNotchModeByMonitor(next);
    saveSetting("gloom-notch-mode-by-monitor", JSON.stringify(next));
  };

  const setNotchModeForMonitor = (monitorId: string, mode: string | null) => {
    setNotchModeByMonitor((prev) => {
      const next = { ...prev };
      if (mode === null) delete next[monitorId]; else next[monitorId] = mode;
      saveSetting("gloom-notch-mode-by-monitor", JSON.stringify(next));
      return next;
    });
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

  const handleThemeStyleChange = (style: string) => {
    setThemeStyle(style);
    saveSetting("gloom-theme-style", style);
    // Each style is tuned for an opacity; the slider can still change it afterwards.
    const opacity = STYLE_OPACITY[style];
    if (opacity !== undefined) {
      setThemeOpacity(opacity);
      saveSetting("gloom-theme-opacity", String(opacity));
    }
  };

  const handleAccentChange = (accent: string) => {
    setThemeAccent(accent);
    saveSetting("gloom-theme-accent", accent);
  };

  const handleAccentColorChange = (color: string) => {
    setThemeAccentColor(color);
    saveSetting("gloom-theme-accent-color", color);
  };

  const applyThemePreset = (preset: ThemePreset) => {
    setThemeMode("custom");
    setThemeColor(preset.color);
    setThemeSaturation(preset.saturation);
    setThemeBrightness(preset.brightness);
    setThemeAccent("custom");
    setThemeAccentColor(preset.accent);
    saveSetting("gloom-theme-mode", "custom");
    saveSetting("gloom-theme-color", preset.color);
    saveSetting("gloom-theme-saturation", String(preset.saturation));
    saveSetting("gloom-theme-brightness", String(preset.brightness));
    saveSetting("gloom-theme-accent", "custom");
    saveSetting("gloom-theme-accent-color", preset.accent);
  };

  // ── City search ──
  const selectCity = async (city: {
    name: string;
    country: string;
    latitude: number;
    longitude: number;
  }) => {
    setCityName(city.name);
    setCityQuery(city.name);
    setShowCityDropdown(false);
    setCitySearchResults([]);
    localStorage.setItem("gloom-weather-city", city.name);
    localStorage.setItem("gloom-weather-lat", city.latitude.toString());
    localStorage.setItem("gloom-weather-lon", city.longitude.toString());
    await invoke("save_setting", { key: "gloom-weather-lat", value: city.latitude.toString() }).catch(() => {});
    await invoke("save_setting", { key: "gloom-weather-lon", value: city.longitude.toString() }).catch(() => {});
    await invoke("save_setting", { key: "gloom-weather-city", value: city.name }).catch(() => {});
    emit("weather-refresh", { lat: city.latitude, lon: city.longitude, city: city.name });
  };

  const handleCityClear = async () => {
    setCityName("");
    setCityQuery("");
    setShowCityDropdown(false);
    setCitySearchResults([]);
    localStorage.removeItem("gloom-weather-city");
    localStorage.removeItem("gloom-weather-lat");
    localStorage.removeItem("gloom-weather-lon");
    await invoke("save_setting", { key: "gloom-weather-lat", value: null }).catch(() => {});
    await invoke("save_setting", { key: "gloom-weather-lon", value: null }).catch(() => {});
    await invoke("save_setting", { key: "gloom-weather-city", value: null }).catch(() => {});
    emit("weather-refresh", true);
  };

  // ── Export / Import ──
  const handleExportSettings = async () => {
    setExportStatus("exporting");
    try {
      const saved = await invoke<boolean>("export_settings_dialog");
      if (saved) {
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
      const imported = await invoke<boolean>("import_settings_dialog");
      if (imported) {
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

  const handleResetSettings = async () => {
    if (resetStatus === "idle") {
      setResetStatus("confirm");
      setTimeout(() => setResetStatus((s) => (s === "confirm" ? "idle" : s)), 4000);
      return;
    }
    if (resetStatus !== "confirm") return;

    setResetStatus("resetting");
    try {
      await invoke("reset_settings");
      localStorage.clear();
      await invoke("restart_gloom");
    } catch (e) {
      console.error("Reset failed:", e);
      setResetStatus("idle");
    }
  };

  const handleUninstallGLOOM = async () => {
    if (uninstallStatus === "idle" || uninstallStatus === "error") {
      setUninstallStatus("confirm");
      setTimeout(() => setUninstallStatus((s) => (s === "confirm" ? "idle" : s)), 4000);
      return;
    }
    if (uninstallStatus !== "confirm") return;

    setUninstallStatus("uninstalling");
    try {
      await invoke("uninstall_gloom");
    } catch (e) {
      console.error("Uninstall failed:", e);
      setUninstallError(typeof e === "string" ? e : String((e as Error)?.message ?? e));
      setUninstallStatus("error");
      setTimeout(() => setUninstallStatus((s) => (s === "error" ? "idle" : s)), 12000);
    }
  };

  return {
    // System
    autostart,
    toggleAutostart,
    autoUpdate,
    toggleAutoUpdate,
    prereleaseChannel,
    togglePrereleaseChannel,
    lowBatteryThreshold,
    handleThresholdChange,
    timeFormat24h,
    toggleTimeFormat24h,
    legacyMode,
    toggleLegacyMode,
    showUpdateIndicator,
    toggleUpdateIndicator,
    ramSaver,
    toggleRamSaver,
    ramUltra,
    toggleRamUltra,
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
    themeStyle,
    handleThemeStyleChange,
    themeAccent,
    handleAccentChange,
    themeAccentColor,
    handleAccentColorChange,
    applyThemePreset,

    // Notch
    notchMode,
    setNotchModeValue,
    calendarEnabled,
    toggleCalendar,
    timerEnabled,
    toggleTimerEnabled,
    timerSoundEnabled,
    toggleTimerSound,
    notchTimerProgress,
    toggleNotchTimerProgress,
    stopwatchEnabled,
    toggleStopwatchEnabled,
    dualClockEnabled,
    toggleDualClock,
    dualClockZone,
    dualClockLabel,
    selectDualClockZone,
    musicModeEnabled,
    toggleMusicMode,
    musicCompactNotch,
    toggleMusicCompactNotch,
    agentApi,
    toggleAgentApi,
    notchScrollVolume,
    toggleNotchScrollVolume,
    shelfPersist,
    toggleShelfPersist,
    rememberMode,
    toggleRememberMode,
    mediaLayout,
    toggleMediaLayout,
    mediaAmbienceEnabled,
    toggleAmbience,
    mediaCompactGlowEnabled,
    toggleCompactGlow,
    mediaArtworkThemeEnabled,
    toggleArtworkTheme,

    // Weather
    weatherEnabled,
    toggleWeather,
    tempUnitFahrenheit,
    toggleTempUnit,
    cityName,
    cityQuery,
    setCityQuery,
    citySearchResults,
    showCityDropdown,
    setShowCityDropdown,
    selectCity,
    handleCityClear,

    // Widgets
    statusWidgets,
    handleWidgetsChange,
    hasBattery,

    // Dock
    dockEnabled,
    toggleDock,
    dockMode,
    setDockModeValue,
    dockPreviewEnabled,
    toggleDockPreview,
    dockAttentionEnabled,
    toggleDockAttention,
    dockConsoleWindows,
    toggleDockConsoleWindows,
    dockSearchEnabled,
    toggleDockSearch,
    dockSearchProvider,
    handleDockSearchProviderChange,
    dockSearchShortcut,
    handleDockSearchShortcutChange,
    dockCalendarEnabled,
    toggleDockCalendar,
    dockTray,
    toggleDockTray,
    dockTaskView,
    toggleDockTaskView,
    dockShowDesktop,
    toggleDockShowDesktop,
    panelControlCenter,
    togglePanelControlCenter,
    panelShelf,
    togglePanelShelf,
    panelStatus,
    togglePanelStatus,
    panelOrder,
    reorderPanels,
    resetPanelOrder,
    ccLayout,
    changeCcLayout,
    resetCcLayout,
    dockRecycle,
    toggleDockRecycle,
    dockDrives,
    toggleDockDrive,
    weekStart,
    changeWeekStart,
    panelScrollDirection,
    changePanelScrollDirection,
    dockIndicator,
    changeDockIndicator,
    dockMenuItems,
    toggleDockMenuItem,
    hiddenDockApps,
    restoreHiddenDockApp,
    dockIconOnly,
    toggleDockIconOnly,
    startIcon,
    handleStartIconChange,
    dockAdaptive,
    toggleDockAdaptive,
    dockWinNumberEnabled,
    toggleDockWinNumber,
    dockMixedReorder,
    toggleDockMixedReorder,

    monitors,
    dockMonitorMode,
    setDockMonitorModeValue,
    dockMonitorId,
    setDockMonitorIdValue,
    notchMonitorMode,
    setNotchMonitorModeValue,
    notchMonitorId,
    setNotchMonitorIdValue,
    dockModeByMonitor,
    setDockModeForMonitor,
    replaceDockModesByMonitor,
    notchModeByMonitor,
    setNotchModeForMonitor,
    replaceNotchModesByMonitor,

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

    exportStatus,
    importStatus,
    handleExportSettings,
    handleImportSettings,
    resetStatus,
    handleResetSettings,
    uninstallStatus,
    uninstallError,
    handleUninstallGLOOM,

    // Utilities
    restartGLOOM: () => invoke("restart_gloom"),
    quitGLOOM: () => invoke("quit_gloom"),
  };
}
