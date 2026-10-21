import React, { useEffect, useState } from "react";
import { AppConfig } from "../types";
import { SettingsCategory, SettingsView } from "./SettingsView";
import { ChittiLogo } from "./ChittiLogo";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { listen } from "@tauri-apps/api/event";
import { Minus, X } from "lucide-react";

interface SettingsWindowProps {
  config: AppConfig;
  onUpdateConfig: (newCfg: AppConfig) => void;
}

export const SettingsWindow: React.FC<SettingsWindowProps> = ({ config, onUpdateConfig }) => {
  const [initialTab, setInitialTab] = useState<SettingsCategory>("general");

  useEffect(() => {
    // Parse tab from hash if any
    const hash = window.location.hash;
    const match = hash.match(/settings\/([a-zA-Z0-9_-]+)/);
    if (match && match[1]) {
      setInitialTab(match[1] as SettingsCategory);
    }

    // Also listen to Tauri event if opened or switched
    let unlisten: (() => void) | undefined;
    listen<string>("settings-open-tab", (event) => {
      if (event.payload) {
        setInitialTab(event.payload as SettingsCategory);
      }
    }).then((fn) => {
      unlisten = fn;
    });

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  const handleClose = () => {
    invoke("jarvis_close_settings_window").catch(() => {
      try {
        const win = getCurrentWindow();
        win.close();
      } catch (e) {
        console.warn("Could not close current window:", e);
      }
    });
  };

  const handleMinimize = () => {
    invoke("jarvis_minimize_settings_window").catch(() => {
      try {
        const win = getCurrentWindow();
        win.minimize();
      } catch (e) {
        console.warn("Could not minimize current window:", e);
      }
    });
  };

  const handleDrag = (e: React.MouseEvent) => {
    if (e.button === 0) {
      try {
        const win = getCurrentWindow();
        win.startDragging();
      } catch {
        // Handled by data-tauri-drag-region
      }
    }
  };

  return (
    <div
      style={{
        width: "100vw",
        height: "100vh",
        display: "flex",
        flexDirection: "column",
        backgroundColor: "#131317",
        color: "#ffffff",
        overflow: "hidden",
        userSelect: "none",
        boxSizing: "border-box",
      }}
    >
      {/* ── Top Window Bar (Draggable) ── */}
      <div
        data-tauri-drag-region
        onMouseDown={handleDrag}
        style={{
          height: "38px",
          minHeight: "38px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "0 14px",
          backgroundColor: "#191920",
          borderBottom: "0.5px solid rgba(255, 255, 255, 0.08)",
          cursor: "grab",
        }}
      >
        <div
          data-tauri-drag-region
          onMouseDown={handleDrag}
          style={{
            display: "flex",
            alignItems: "center",
            gap: "9px",
            fontSize: "12.5px",
            fontWeight: 600,
            color: "#ffffff",
            letterSpacing: "0.02em",
          }}
        >
          <ChittiLogo size={20} />
          <span>JARVIS Settings & Customization</span>
        </div>

        {/* Window Control Buttons */}
        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <button
            onClick={handleMinimize}
            style={{
              width: "26px",
              height: "26px",
              borderRadius: "6px",
              border: "none",
              background: "rgba(255, 255, 255, 0.06)",
              color: "rgba(255, 255, 255, 0.7)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              transition: "background 0.15s ease",
            }}
            title="Minimize"
          >
            <Minus size={13} />
          </button>
          <button
            onClick={handleClose}
            style={{
              width: "26px",
              height: "26px",
              borderRadius: "6px",
              border: "none",
              background: "rgba(255, 69, 58, 0.15)",
              color: "#f87171",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              transition: "background 0.15s ease",
            }}
            title="Close Settings"
          >
            <X size={13} />
          </button>
        </div>
      </div>

      {/* ── Settings Content View ── */}
      <div style={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
        <SettingsView
          config={config}
          onUpdateConfig={onUpdateConfig}
          standalone={true}
          initialTab={initialTab}
          onClose={handleClose}
        />
      </div>
    </div>
  );
};
