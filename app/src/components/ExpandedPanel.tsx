import React, { useState } from "react";
import {
  ActiveTab,
  AppConfig,
  MediaState,
  SystemTelemetry,
  TimerState,
} from "../types";
import { DashboardView } from "./DashboardView";
import { MusicRoomView } from "./MusicRoomView";
import { CommandCenter } from "./CommandCenter";
import { TimerWidget } from "./TimerWidget";
import { QuickNotes } from "./QuickNotes";
import { SystemCard } from "./SystemCard";
import { ClipboardShelf } from "./ClipboardShelf";
import { BluetoothView } from "./BluetoothView";
import { NetworkView } from "./NetworkView";
import { SettingsView } from "./SettingsView";
import { LiveBridgeView } from "./LiveBridgeView";
import {
  LayoutGrid,
  Music,
  Sliders,
  Coffee,
  CloudSun,
  Pin,
  PinOff,
  Settings,
  X,
  Zap,
  Smartphone,
} from "lucide-react";

interface ExpandedPanelProps {
  media: MediaState;
  telemetry: SystemTelemetry;
  volume: number;
  config: AppConfig;
  isPinned: boolean;
  timer?: TimerState | null;
  activeTab?: ActiveTab;
  onSelectTab?: (tab: ActiveTab) => void;
  onTogglePin: () => void;
  onCollapse: () => void;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (ms: number) => void;
  onSetVolume: (vol: number) => void;
  onUpdateConfig: (cfg: AppConfig) => void;
  onTimerUpdate?: (state: TimerState) => void;
}

export const ExpandedPanel: React.FC<ExpandedPanelProps> = ({
  media,
  telemetry,
  volume,
  config,
  isPinned,
  timer,
  activeTab: controlledTab,
  onSelectTab,
  onTogglePin,
  onCollapse,
  onTogglePlay,
  onNext,
  onPrev,
  onSeek,
  onSetVolume,
  onUpdateConfig,
  onTimerUpdate,
}) => {
  const [internalTab, setInternalTab] = useState<ActiveTab>("dashboard");
  const activeTab = controlledTab ?? internalTab;

  const setActiveTab = (tab: ActiveTab | ((prev: ActiveTab) => ActiveTab)) => {
    const next = typeof tab === "function" ? tab(activeTab) : tab;
    setInternalTab(next);
    onSelectTab?.(next);
  };

  const navCapsuleItems: { id: ActiveTab; icon: React.ReactNode; label: string }[] = [
    { id: "dashboard", icon: <LayoutGrid size={13} />, label: "Dashboard" },
    { id: "media", icon: <Music size={13} />, label: "Music" },
    { id: "bridge", icon: <Smartphone size={13} />, label: "AirDrop & Phone" },
    { id: "controls", icon: <Sliders size={13} />, label: "Control Center" },
    { id: "timer", icon: <Coffee size={13} />, label: "Focus Timer" },
  ];

  const batteryPercent = telemetry.batteryPercent ?? 100;

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        padding: "10px 14px",
        gap: "10px",
        boxSizing: "border-box",
        backgroundColor: "transparent",
        color: "#ffffff",
        fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'SF Pro', system-ui, sans-serif",
      }}
    >
      {/* ── Top Header Bar ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          userSelect: "none",
        }}
      >
        {/* Left Navigation Capsule */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "2px",
            background: "rgba(255, 255, 255, 0.08)",
            border: "1px solid rgba(255, 255, 255, 0.12)",
            borderRadius: "9999px",
            padding: "3px 4px",
          }}
        >
          {navCapsuleItems.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                title={item.label}
                style={{
                  width: "26px",
                  height: "26px",
                  borderRadius: "50%",
                  border: "none",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  background: isActive ? "rgba(255, 255, 255, 0.22)" : "transparent",
                  color: isActive ? "#ffffff" : "rgba(255, 255, 255, 0.45)",
                  cursor: "pointer",
                  transition: "all 0.15s ease",
                }}
                onMouseEnter={(e) => {
                  if (!isActive) e.currentTarget.style.color = "#ffffff";
                }}
                onMouseLeave={(e) => {
                  if (!isActive) e.currentTarget.style.color = "rgba(255, 255, 255, 0.45)";
                }}
              >
                {item.icon}
              </button>
            );
          })}
        </div>

        {/* Right Status & Action Controls */}
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          {/* Weather Pill */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              fontSize: "12px",
              fontWeight: 600,
              color: "#cbd5e1",
            }}
          >
            <CloudSun size={14} color="#38bdf8" />
            <span style={{ fontVariantNumeric: "tabular-nums" }}>30°C</span>
          </div>

          {/* Battery Pill with Green Ring */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "4px",
              fontSize: "12px",
              fontWeight: 700,
              color: "#ffffff",
            }}
          >
            <span style={{ fontVariantNumeric: "tabular-nums" }}>{batteryPercent}%</span>
            <div
              style={{
                width: "18px",
                height: "18px",
                borderRadius: "50%",
                border: "2px solid #10b981",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 0 6px rgba(16, 185, 129, 0.4)",
              }}
            >
              <Zap size={10} color="#10b981" fill="#10b981" />
            </div>
          </div>

          {/* Pin Button */}
          <button
            onClick={onTogglePin}
            style={{
              width: "28px",
              height: "28px",
              borderRadius: "50%",
              border: "none",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              background: isPinned ? "#0284c7" : "rgba(255, 255, 255, 0.08)",
              color: "#ffffff",
              transition: "background 0.15s ease",
            }}
            title={isPinned ? "Pinned open (click to unpin)" : "Pin island open"}
          >
            {isPinned ? <Pin size={13} /> : <PinOff size={13} />}
          </button>

          {/* Settings Button (Opens In-Island Settings Suite) */}
          <button
            onClick={() => {
              setActiveTab(activeTab === "settings" ? "dashboard" : "settings");
            }}
            style={{
              width: "28px",
              height: "28px",
              borderRadius: "50%",
              border: "none",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              background: activeTab === "settings" ? "rgba(56, 189, 248, 0.25)" : "rgba(255, 255, 255, 0.08)",
              color: activeTab === "settings" ? "#38bdf8" : "#cbd5e1",
              transition: "all 0.15s ease",
            }}
            title="JARVIS Settings & Customization"
            onMouseEnter={(e) => {
              if (activeTab !== "settings") {
                e.currentTarget.style.background = "rgba(255, 255, 255, 0.18)";
                e.currentTarget.style.color = "#ffffff";
              }
            }}
            onMouseLeave={(e) => {
              if (activeTab !== "settings") {
                e.currentTarget.style.background = "rgba(255, 255, 255, 0.08)";
                e.currentTarget.style.color = "#cbd5e1";
              }
            }}
          >
            <Settings size={13} />
          </button>

          {/* Close / Collapse Button */}
          <button
            onClick={onCollapse}
            style={{
              width: "28px",
              height: "28px",
              borderRadius: "50%",
              border: "none",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              background: "rgba(255, 255, 255, 0.08)",
              color: "#cbd5e1",
              transition: "background 0.15s ease",
            }}
            title="Collapse Island"
          >
            <X size={13} />
          </button>
        </div>
      </div>

      {/* ── Main View Content ── */}
      <div style={{ flex: 1, minHeight: 0 }}>
        {activeTab === "dashboard" && (
          <DashboardView
            media={media}
            telemetry={telemetry}
            timer={timer}
            onTogglePlay={onTogglePlay}
            onNext={onNext}
            onPrev={onPrev}
            onSeek={onSeek}
            onSelectTab={setActiveTab}
          />
        )}

        {activeTab === "media" && (
          <MusicRoomView
            media={media}
            volume={volume}
            onTogglePlay={onTogglePlay}
            onNext={onNext}
            onPrev={onPrev}
            onSeek={onSeek}
            onSetVolume={onSetVolume}
          />
        )}

        {activeTab === "bridge" && (
          <LiveBridgeView />
        )}

        {activeTab === "controls" && (
          <CommandCenter
            volume={volume}
            onSetVolume={onSetVolume}
            onOpenSettings={() => setActiveTab("settings")}
          />
        )}

        {activeTab === "bluetooth" && <BluetoothView />}

        {activeTab === "clipboard" && <ClipboardShelf />}

        {activeTab === "notes" && <QuickNotes />}

        {activeTab === "timer" && (
          <TimerWidget onTimerUpdate={onTimerUpdate} />
        )}

        {activeTab === "system" && (
          <SystemCard telemetry={telemetry} />
        )}

        {activeTab === "network" && (
          <NetworkView telemetry={telemetry} />
        )}

        {activeTab === "settings" && (
          <div
            style={{
              width: "100%",
              height: "100%",
              borderRadius: "14px",
              overflow: "hidden",
              border: "1px solid rgba(255, 255, 255, 0.08)",
            }}
          >
            <SettingsView
              config={config}
              onUpdateConfig={onUpdateConfig}
              standalone={true}
              onClose={() => setActiveTab("dashboard")}
            />
          </div>
        )}
      </div>
    </div>
  );
};
