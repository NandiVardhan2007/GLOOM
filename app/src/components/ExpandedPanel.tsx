import React, { useState } from "react";
import { invoke } from "@tauri-apps/api/core";
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
  Pin,
  PinOff,
  Settings,
  ChevronUp,
  X,
  Zap,
  Smartphone,
  FileText,
  Activity,
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
    { id: "dashboard", icon: <LayoutGrid size={14} strokeWidth={1.7} />, label: "Dashboard" },
    { id: "media", icon: <Music size={14} strokeWidth={1.7} />, label: "Music" },
    { id: "bridge", icon: <Smartphone size={14} strokeWidth={1.7} />, label: "File Beam & Phone" },
    { id: "notes", icon: <FileText size={14} strokeWidth={1.7} />, label: "Notepad" },
    { id: "network", icon: <Activity size={14} strokeWidth={1.7} />, label: "Network" },
    { id: "controls", icon: <Sliders size={14} strokeWidth={1.7} />, label: "Control Center" },
    { id: "timer", icon: <Coffee size={14} strokeWidth={1.7} />, label: "Focus Timer" },
  ];

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        padding: "12px 16px",
        gap: "12px",
        boxSizing: "border-box",
        backgroundColor: "transparent",
        color: "#ffffff",
        fontFamily: "var(--font-sans, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, system-ui, sans-serif)",
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
            background: "rgba(120, 120, 128, 0.16)",
            borderRadius: "9px",
            padding: "3px",
            height: "32px",
          }}
        >
          {navCapsuleItems.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                title={item.label}
                aria-label={item.label}
                aria-pressed={isActive}
                style={{
                  width: "28px",
                  height: "26px",
                  borderRadius: "7px",
                  border: "none",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  background: isActive ? "rgba(255, 255, 255, 0.18)" : "transparent",
                  color: isActive ? "#f5f5f7" : "rgba(235, 235, 245, 0.52)",
                  cursor: "pointer",
                  transition: "background-color var(--duration-quick) var(--ease-out), color var(--duration-quick) var(--ease-out)",
                }}
                onMouseEnter={(e) => {
                  if (!isActive) e.currentTarget.style.color = "#f5f5f7";
                }}
                onMouseLeave={(e) => {
                  if (!isActive) e.currentTarget.style.color = "rgba(235, 235, 245, 0.52)";
                }}
              >
                {item.icon}
              </button>
            );
          })}
        </div>

        {/* Right Status & Action Controls */}
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          {/* Battery Pill (only when hardware battery is present) */}
          {telemetry.hasBattery && telemetry.batteryPercent != null && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "4px",
                fontSize: "12px",
                fontWeight: 600,
                color: "#ffffff",
              }}
            >
              <span style={{ fontVariantNumeric: "tabular-nums" }}>{telemetry.batteryPercent}%</span>
              <div
                style={{
                  width: "18px",
                  height: "18px",
                  borderRadius: "50%",
                  border: "2px solid #30d158",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  boxShadow: "0 0 6px rgba(48, 209, 88, 0.4)",
                }}
              >
                <Zap size={10} color="#30d158" fill={telemetry.isCharging ? "#30d158" : "transparent"} />
              </div>
            </div>
          )}

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
              background: isPinned ? "#0a84ff" : "rgba(255, 255, 255, 0.08)",
              color: "#ffffff",
              transition: "background 0.15s ease",
            }}
            title={isPinned ? "Pinned open (click to unpin)" : "Pin island open"}
          >
            {isPinned ? <Pin size={14} strokeWidth={1.7} /> : <PinOff size={14} strokeWidth={1.7} />}
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
              background: activeTab === "settings" ? "rgba(10, 132, 255, 0.25)" : "rgba(255, 255, 255, 0.08)",
              color: activeTab === "settings" ? "#0a84ff" : "#cbd5e1",
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
            <Settings size={14} strokeWidth={1.7} />
          </button>

          {/* Collapse / Dismiss Button */}
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
              transition: "all 0.15s ease",
            }}
            title="Dismiss / Collapse Island to Notch"
            onMouseEnter={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.18)";
              e.currentTarget.style.color = "#ffffff";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "rgba(255, 255, 255, 0.08)";
              e.currentTarget.style.color = "#cbd5e1";
            }}
          >
            <ChevronUp size={14} />
          </button>

          {/* Close & Exit JARVIS Process Button */}
          <button
            onClick={async (e) => {
              e.stopPropagation();
              try {
                await invoke("jarvis_exit");
              } catch (e) {
                console.error("Failed to exit:", e);
              }
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
              background: "rgba(255, 69, 58, 0.2)",
              color: "#f87171",
              transition: "all 0.15s ease",
            }}
            title="Exit JARVIS (Terminate Application Process)"
            onMouseEnter={(e) => {
              e.currentTarget.style.background = "rgba(255, 69, 58, 0.4)";
              e.currentTarget.style.color = "#ffffff";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "rgba(255, 69, 58, 0.2)";
              e.currentTarget.style.color = "#f87171";
            }}
          >
            <X size={14} />
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
              border: "0.5px solid rgba(255, 255, 255, 0.08)",
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
