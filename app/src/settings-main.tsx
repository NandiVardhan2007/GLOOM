import React, { useEffect, useState } from "react";
import ReactDOM from "react-dom/client";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { SettingsWindow } from "./components/SettingsWindow";
import { DEFAULT_CONFIG } from "./App";
import { AppConfig } from "./types";
import "./styles/index.css";

function SettingsApp() {
  const [config, setConfig] = useState<AppConfig>(DEFAULT_CONFIG);

  useEffect(() => {
    // Fetch initial saved config from Rust backend
    invoke<AppConfig>("jarvis_get_config")
      .then((cfg) => {
        if (cfg) setConfig(cfg);
      })
      .catch((err) => {
        console.warn("Could not load backend config, using defaults:", err);
      });

    // Listen for real-time config updates across windows
    let unlisten: (() => void) | undefined;
    listen<AppConfig>("jarvis://config-updated", (event) => {
      if (event.payload) {
        setConfig(event.payload);
      }
    }).then((fn) => {
      unlisten = fn;
    });

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  const handleUpdateConfig = (newCfg: AppConfig) => {
    setConfig(newCfg);
    invoke("jarvis_set_config", { config: newCfg }).catch(console.error);
  };

  return <SettingsWindow config={config} onUpdateConfig={handleUpdateConfig} />;
}

const rootElement = document.getElementById("root");
if (rootElement) {
  ReactDOM.createRoot(rootElement).render(
    <React.StrictMode>
      <SettingsApp />
    </React.StrictMode>
  );
}
