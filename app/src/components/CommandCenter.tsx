import React, { useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  Volume2,
  VolumeX,
  Sun,
  Wifi,
  Bluetooth,
  BellOff,
  Bell,
  Lock,
  Activity,
  Calculator,
  Settings,
} from "lucide-react";

interface CommandCenterProps {
  volume: number;
  onSetVolume: (vol: number) => void;
  onOpenSettings?: () => void;
}

export const CommandCenter: React.FC<CommandCenterProps> = ({
  volume,
  onSetVolume,
  onOpenSettings,
}) => {
  const [brightness, setBrightness] = useState<number>(() => {
    const saved = localStorage.getItem("jarvis_brightness");
    return saved ? parseInt(saved, 10) : 100;
  });
  const [dndActive, setDndActive] = useState<boolean>(() => {
    return localStorage.getItem("jarvis_dnd") === "true";
  });
  const [wifiActive, setWifiActive] = useState<boolean>(true);
  const [btActive, setBtActive] = useState<boolean>(true);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);

  const handleBrightnessChange = (val: number) => {
    setBrightness(val);
    localStorage.setItem("jarvis_brightness", val.toString());
    invoke("jarvis_set_brightness", { brightness: val }).catch(console.error);
  };

  const toggleDnd = () => {
    const next = !dndActive;
    setDndActive(next);
    localStorage.setItem("jarvis_dnd", next ? "true" : "false");
    showFeedback(next ? "Focus Mode ON" : "Focus Mode OFF");
  };

  const handleSystemAction = async (action: string, label: string) => {
    try {
      await invoke("jarvis_system_action", { action });
      showFeedback(label);
    } catch (e) {
      console.error(e);
      showFeedback(`Failed: ${label}`);
    }
  };

  const showFeedback = (msg: string) => {
    setActionFeedback(msg);
    window.setTimeout(() => setActionFeedback(null), 2000);
  };

  const volPercent = Math.round(volume * 100);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "10px",
        height: "100%",
        overflowY: "auto",
        paddingRight: "2px",
      }}
    >
      {/* Top Quick Toggles */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: "8px",
        }}
      >
        {/* Wi-Fi */}
        <button
          onClick={() => {
            const next = !wifiActive;
            setWifiActive(next);
            showFeedback(next ? "Wi-Fi Active" : "Wi-Fi Disabled");
          }}
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: "8px 4px",
            borderRadius: "12px",
            border: "none",
            cursor: "pointer",
            background: wifiActive ? "#0369a1" : "#1e1e28",
            color: wifiActive ? "#ffffff" : "#94a3b8",
            transition: "all 0.15s ease",
            gap: "4px",
          }}
          title="Wi-Fi / Network Toggle"
        >
          <Wifi size={16} />
          <span style={{ fontSize: "11px", fontWeight: 700 }}>Wi-Fi</span>
        </button>

        {/* Bluetooth */}
        <button
          onClick={() => {
            const next = !btActive;
            setBtActive(next);
            localStorage.setItem("jarvis_bt_enabled", next ? "true" : "false");
            showFeedback(next ? "Bluetooth Enabled" : "Bluetooth Disabled");
          }}
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: "8px 4px",
            borderRadius: "12px",
            border: "none",
            cursor: "pointer",
            background: btActive ? "#1d4ed8" : "#1e1e28",
            color: btActive ? "#ffffff" : "#94a3b8",
            transition: "all 0.15s ease",
            gap: "4px",
          }}
          title="Toggle Bluetooth"
        >
          <Bluetooth size={16} />
          <span style={{ fontSize: "11px", fontWeight: 700 }}>Bluetooth</span>
        </button>

        {/* DND / Focus */}
        <button
          onClick={toggleDnd}
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: "8px 4px",
            borderRadius: "12px",
            border: "none",
            cursor: "pointer",
            background: dndActive ? "#7e22ce" : "#1e1e28",
            color: dndActive ? "#ffffff" : "#94a3b8",
            transition: "all 0.15s ease",
            gap: "4px",
          }}
          title="Do Not Disturb / Focus"
        >
          {dndActive ? <BellOff size={16} /> : <Bell size={16} />}
          <span style={{ fontSize: "11px", fontWeight: 700 }}>Focus</span>
        </button>

        {/* Mute */}
        <button
          onClick={() => onSetVolume(volume > 0 ? 0 : 0.5)}
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: "8px 4px",
            borderRadius: "12px",
            border: "none",
            cursor: "pointer",
            background: volume === 0 ? "#b91c1c" : "#1e1e28",
            color: volume === 0 ? "#ffffff" : "#94a3b8",
            transition: "all 0.15s ease",
            gap: "4px",
          }}
          title="Toggle Mute"
        >
          {volume === 0 ? <VolumeX size={16} /> : <Volume2 size={16} />}
          <span style={{ fontSize: "11px", fontWeight: 700 }}>
            {volume === 0 ? "Muted" : "Mute"}
          </span>
        </button>
      </div>

      {/* Sliders: Master Volume & Display Brightness */}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: "8px",
          background: "#181820",
          padding: "10px 14px",
          borderRadius: "14px",
          border: "1px solid rgba(255, 255, 255, 0.1)",
        }}
      >
        {/* Volume Slider */}
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          {volume === 0 ? (
            <VolumeX size={16} color="#94a3b8" />
          ) : (
            <Volume2 size={16} color="#0a84ff" />
          )}
          <input
            type="range"
            min={0}
            max={100}
            value={volPercent}
            onChange={(e) => onSetVolume(parseInt(e.target.value, 10) / 100)}
            style={{
              flex: 1,
              accentColor: "#0a84ff",
              cursor: "pointer",
              height: "5px",
            }}
          />
          <span
            style={{
              fontSize: "12px",
              fontWeight: 700,
              width: "36px",
              textAlign: "right",
              color: "#ffffff",
            }}
          >
            {volPercent}%
          </span>
        </div>

        {/* Brightness Slider */}
        <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <Sun size={16} color="#fbbf24" />
          <input
            type="range"
            min={10}
            max={100}
            value={brightness}
            onChange={(e) =>
              handleBrightnessChange(parseInt(e.target.value, 10))
            }
            style={{
              flex: 1,
              accentColor: "#fbbf24",
              cursor: "pointer",
              height: "5px",
            }}
          />
          <span
            style={{
              fontSize: "12px",
              fontWeight: 700,
              width: "36px",
              textAlign: "right",
              color: "#ffffff",
            }}
          >
            {brightness}%
          </span>
        </div>
      </div>

      {/* System Quick Launch Shortcuts */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: "8px",
        }}
      >
        <button
          onClick={() => handleSystemAction("lock", "Lock Screen")}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "6px",
            padding: "8px 6px",
            borderRadius: "10px",
            border: "1px solid rgba(255, 255, 255, 0.1)",
            background: "#1e1e28",
            color: "#ffffff",
            fontSize: "11px",
            fontWeight: 600,
            cursor: "pointer",
            transition: "all 0.15s ease",
          }}
          title="Lock Windows (Win+L)"
        >
          <Lock size={13} color="#f87171" />
          <span>Lock</span>
        </button>

        <button
          onClick={() => handleSystemAction("taskmgr", "Task Manager")}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "6px",
            padding: "8px 6px",
            borderRadius: "10px",
            border: "1px solid rgba(255, 255, 255, 0.1)",
            background: "#1e1e28",
            color: "#ffffff",
            fontSize: "11px",
            fontWeight: 600,
            cursor: "pointer",
            transition: "all 0.15s ease",
          }}
          title="Open Task Manager"
        >
          <Activity size={13} color="#34d399" />
          <span>TaskMgr</span>
        </button>

        <button
          onClick={() => handleSystemAction("calc", "Calculator")}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "6px",
            padding: "8px 6px",
            borderRadius: "10px",
            border: "1px solid rgba(255, 255, 255, 0.1)",
            background: "#1e1e28",
            color: "#ffffff",
            fontSize: "11px",
            fontWeight: 600,
            cursor: "pointer",
            transition: "all 0.15s ease",
          }}
          title="Open Calculator"
        >
          <Calculator size={13} color="#fbbf24" />
          <span>Calc</span>
        </button>

        <button
          onClick={() => {
            if (onOpenSettings) {
              onOpenSettings();
            } else {
              invoke("jarvis_open_settings_window", { tab: "general" }).catch(console.error);
            }
          }}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "6px",
            padding: "8px 6px",
            borderRadius: "10px",
            border: "1px solid rgba(255, 255, 255, 0.1)",
            background: "#1e1e28",
            color: "#ffffff",
            fontSize: "11px",
            fontWeight: 600,
            cursor: "pointer",
            transition: "all 0.15s ease",
          }}
          title="Open JARVIS Settings & Customization"
        >
          <Settings size={13} color="#0a84ff" />
          <span>Config</span>
        </button>
      </div>

      {actionFeedback && (
        <div
          style={{
            textAlign: "center",
            fontSize: "11px",
            color: "#0a84ff",
            fontWeight: 700,
            letterSpacing: "0.04em",
          }}
        >
          {actionFeedback}
        </div>
      )}
    </div>
  );
};
