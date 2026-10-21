import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { Island } from "./components/Island";
import { SettingsWindow } from "./components/SettingsWindow";
import { AppConfig } from "./types";
import "./styles/index.css";

export const DEFAULT_CONFIG: AppConfig = {
  isPro: true,
  mode: "island",
  topOffset: 12,
  islandWidth: 210,
  islandHeight: 34,
  expandedWidth: 740,
  expandedHeight: 380,
  islandScale: 1.0,
  pillEdgeGap: 12,
  clockFormat: "12h",
  selectedMonitor: "primary",
  screenPosition: "top",

  autostart: false,
  hoverToShow: false,
  expandOnHover: true,
  avoidFullscreen: true,
  hideBehindMaximized: true,
  pinMode: false,
  lockDrag: false,

  theme: "obsidian",
  accentColor: "#00d2ff",
  bgColor: "#121214",
  idleColor: "#0a0a0c",
  cornerShape: "rounded",
  panelStyle: "dark_glass",
  cardStyle: "dark",
  glowIntensity: "subtle",
  cycleColors: false,
  customBgUrl: "",
  bgAnimation: "glow",

  showMediaWidget: true,
  showAudioWaveform: true,
  showVolumeMixer: true,
  showAgentUsage: true,
  showAgentNotifications: true,
  showDropShelf: true,
  showClipboardHistory: true,
  showDownloads: true,
  showHardwareWidget: true,
  showNetworkWidget: true,
  showWeatherWidget: true,
  showPomodoro: true,
  showQuickTools: true,
  volumeHudEnabled: true,
  clipboardHistoryEnabled: true,
  reducedMotion: false,

  notchyCompactIndicator: true,
  notchyQuietMode: false,
  notchyGlowStrength: "subtle",
  notchyCompletionDuration: 5,
  notchyPreferredEditor: "auto",
  notchyModel: "neko",
};

export function App() {
  const [config, setConfig] = useState<AppConfig>(DEFAULT_CONFIG);
  const [isSettingsRoute, setIsSettingsRoute] = useState<boolean>(() => {
    // 1. Injected settings flag (native, immediate)
    if ((window as any).__IS_SETTINGS__ === true || window.name === "settings") {
      return true;
    }
    // 2. URL Query / Hash parameters
    if (window.location.search.includes("settings") || window.location.hash.includes("settings")) {
      return true;
    }
    // 3. Direct Tauri internals check
    try {
      const internals = (window as any).__TAURI_INTERNALS__;
      if (internals?.metadata?.currentWindow?.label === "settings") {
        return true;
      }
    } catch {
      // ignore
    }
    // 4. Tauri API wrapper
    try {
      const win = getCurrentWindow();
      if (win && win.label === "settings") {
        return true;
      }
    } catch {
      // ignore
    }
    return false;
  });

  useEffect(() => {
    const handleHashChange = () => {
      setIsSettingsRoute(window.location.hash.startsWith("#settings"));
    };
    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  useEffect(() => {
    // Fetch initial saved config from Rust backend
    invoke<AppConfig>("jarvis_get_config")
      .then((cfg) => {
        if (cfg) setConfig(cfg);
      })
      .catch((err) => {
        console.warn("Could not load backend config, using defaults:", err);
      });

    // Listen to tray "Toggle Mode" event
    let unlisten: (() => void) | undefined;
    listen("jarvis://toggle-mode", () => {
      setConfig((prev) => {
        const nextMode = prev.mode === "island" ? "notch" : "island";
        const updated: AppConfig = {
          ...prev,
          mode: nextMode,
          topOffset: nextMode === "notch" ? 0 : prev.pillEdgeGap ?? 12,
        };
        invoke("jarvis_set_config", { config: updated }).catch(console.error);
        return updated;
      });
    }).then((fn) => {
      unlisten = fn;
    }).catch(console.error);

    // Listen to real-time config changes from Settings window
    let unlistenConfig: (() => void) | undefined;
    listen<AppConfig>("jarvis://config-updated", (event) => {
      if (event.payload) {
        setConfig(event.payload);
      }
    }).then((fn) => {
      unlistenConfig = fn;
    }).catch(console.error);

    return () => {
      if (unlisten) unlisten();
      if (unlistenConfig) unlistenConfig();
    };
  }, []);

  const handleUpdateConfig = (newCfg: AppConfig) => {
    setConfig(newCfg);
    invoke("jarvis_set_config", { config: newCfg }).catch(console.error);
  };

  if (isSettingsRoute) {
    return <SettingsWindow config={config} onUpdateConfig={handleUpdateConfig} />;
  }

  return <Island config={config} onUpdateConfig={handleUpdateConfig} />;
}

export default App;
