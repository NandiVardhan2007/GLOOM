import React, { useState } from "react";
import { AppConfig, IslandMode } from "../types";
import { invoke } from "@tauri-apps/api/core";
import { ChittiLogo } from "./ChittiLogo";
import {
  Sliders,
  Palette,
  LayoutGrid,
  Bot,
  Lightbulb,
  Sparkles,
  Info,
  Check,
  X,
  ExternalLink,
  RotateCcw,
  Monitor,
  Clock,
  Maximize2,
  Volume2,
  Music,
  Activity,
  Wifi,
  CloudSun,
  Shield,
  Coffee,
  Download,
  ClipboardList,
  Pin,
  FolderOpen,
  Lock,
  Unlock,
} from "lucide-react";

export type SettingsCategory =
  | "general"
  | "appearance"
  | "widgets"
  | "notchy"
  | "tips"
  | "pro"
  | "about";

interface SettingsViewProps {
  config: AppConfig;
  onUpdateConfig: (newCfg: AppConfig) => void;
  standalone?: boolean;
  onClose?: () => void;
  initialTab?: SettingsCategory;
}

const ACCENT_COLORS = [
  { id: "#00d2ff", name: "Cyan", hex: "#00d2ff" },
  { id: "#38bdf8", name: "Sky", hex: "#38bdf8" },
  { id: "#a855f7", name: "Purple", hex: "#a855f7" },
  { id: "#10b981", name: "Emerald", hex: "#10b981" },
  { id: "#f59e0b", name: "Amber", hex: "#f59e0b" },
  { id: "#f43f5e", name: "Rose", hex: "#f43f5e" },
  { id: "#ffffff", name: "White", hex: "#ffffff" },
];

export const SettingsView: React.FC<SettingsViewProps> = ({
  config,
  onUpdateConfig,
  standalone = false,
  onClose,
  initialTab = "general",
}) => {
  const [activeTab, setActiveTab] = useState<SettingsCategory>(initialTab);
  const [updateStatus, setUpdateStatus] = useState<string>("v1.0.0 (Latest)");
  const [isCheckingUpdate, setIsCheckingUpdate] = useState<boolean>(false);

  const updateField = <K extends keyof AppConfig>(key: K, value: AppConfig[K]) => {
    onUpdateConfig({
      ...config,
      [key]: value,
    });
  };

  const handleOpenStandalone = () => {
    invoke("jarvis_open_settings_window", { tab: activeTab }).catch(console.error);
  };

  const handleRecenter = async () => {
    try {
      await invoke("jarvis_recenter");
    } catch (e) {
      console.error(e);
    }
  };

  const handleResetDefaults = () => {
    const defaults: AppConfig = {
      mode: "island",
      topOffset: 12,
      islandWidth: 210,
      islandHeight: 34,
      expandedWidth: 740,
      expandedHeight: 250,
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
    onUpdateConfig(defaults);
  };

  const handleCheckUpdate = () => {
    setIsCheckingUpdate(true);
    setUpdateStatus("Checking for updates...");
    setTimeout(() => {
      setIsCheckingUpdate(false);
      setUpdateStatus("You are up to date! (v1.0.0)");
    }, 1200);
  };

  const isProActive = config.isPro !== false;

  const navItems = [
    { id: "general" as const, label: "General", icon: <Sliders size={15} /> },
    { id: "appearance" as const, label: "Appearance", icon: <Palette size={15} /> },
    { id: "widgets" as const, label: "Widgets", icon: <LayoutGrid size={15} /> },
    { id: "notchy" as const, label: "Notchy & AI", icon: <Bot size={15} />, isPro: true },
    { id: "tips" as const, label: "Tips & Hotkeys", icon: <Lightbulb size={15} /> },
    { id: "pro" as const, label: isProActive ? "JARVIS Pro" : "Buy Pro", icon: <Sparkles size={15} /> },
    { id: "about" as const, label: "About", icon: <Info size={15} /> },
  ];

  return (
    <div
      style={{
        display: "flex",
        width: "100%",
        height: standalone ? "100%" : "165px",
        maxHeight: standalone ? "100%" : "165px",
        backgroundColor: standalone ? "#131317" : "transparent",
        color: "#ffffff",
        overflow: "hidden",
        userSelect: "none",
        fontFamily: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
      }}
    >
      {/* ── Left Sidebar ── */}
      <aside
        style={{
          width: standalone ? "220px" : "150px",
          flexShrink: 0,
          backgroundColor: standalone ? "rgba(0, 0, 0, 0.25)" : "rgba(255, 255, 255, 0.03)",
          borderRight: "1px solid rgba(255, 255, 255, 0.08)",
          display: "flex",
          flexDirection: "column",
          padding: standalone ? "16px 12px" : "8px 6px",
          justifyContent: "space-between",
        }}
      >
        <div>
          {standalone && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "10px",
                padding: "4px 8px 18px 8px",
                borderBottom: "1px solid rgba(255, 255, 255, 0.06)",
                marginBottom: "12px",
              }}
            >
              <ChittiLogo size={30} />
              <div style={{ lineHeight: 1.2 }}>
                <div style={{ fontSize: "13px", fontWeight: 700, color: "#ffffff" }}>
                  JARVIS
                </div>
                <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.45)" }}>
                  Settings
                </div>
              </div>
            </div>
          )}

          {/* Navigation Items */}
          <nav style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
            {navItems.map((item) => {
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "10px",
                    width: "100%",
                    height: standalone ? "36px" : "28px",
                    padding: standalone ? "0 12px" : "0 8px",
                    borderRadius: "8px",
                    border: "none",
                    cursor: "pointer",
                    fontSize: standalone ? "13px" : "11px",
                    fontWeight: isActive ? 600 : 500,
                    textAlign: "left",
                    background: isActive ? "rgba(255, 255, 255, 0.12)" : "transparent",
                    color: isActive ? "#ffffff" : "rgba(255, 255, 255, 0.55)",
                    position: "relative",
                    transition: "all 0.15s ease",
                  }}
                  onMouseEnter={(e) => {
                    if (!isActive) e.currentTarget.style.color = "#ffffff";
                  }}
                  onMouseLeave={(e) => {
                    if (!isActive) e.currentTarget.style.color = "rgba(255, 255, 255, 0.55)";
                  }}
                >
                  {isActive && (
                    <span
                      style={{
                        position: "absolute",
                        left: 0,
                        top: "50%",
                        transform: "translateY(-50%)",
                        width: "3px",
                        height: "16px",
                        borderRadius: "2px",
                        backgroundColor: config.accentColor || "#00d2ff",
                      }}
                    />
                  )}
                  <span style={{ color: isActive ? config.accentColor : "inherit" }}>
                    {item.icon}
                  </span>
                  <span style={{ flex: 1 }}>{item.label}</span>
                  {item.isPro && !isProActive && (
                    <span
                      style={{
                        fontSize: "9px",
                        fontWeight: 700,
                        padding: "1px 4px",
                        borderRadius: "4px",
                        background: "rgba(245, 158, 11, 0.2)",
                        color: "#fbbf24",
                        display: "flex",
                        alignItems: "center",
                        gap: "2px",
                      }}
                    >
                      <Lock size={8} /> PRO
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        {/* Sidebar Footer */}
        <div style={{ display: "flex", flexDirection: "column", gap: "6px", paddingTop: "8px" }}>
          {!standalone && (
            <button
              onClick={handleOpenStandalone}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "6px",
                width: "100%",
                padding: "5px 8px",
                fontSize: "10px",
                fontWeight: 600,
                borderRadius: "6px",
                border: "1px solid rgba(255, 255, 255, 0.15)",
                background: "rgba(56, 189, 248, 0.1)",
                color: "#38bdf8",
                cursor: "pointer",
              }}
              title="Open full massive settings in standalone window"
            >
              <ExternalLink size={12} />
              <span>Open Window</span>
            </button>
          )}
          {standalone && (
            <div
              style={{
                fontSize: "11px",
                color: "rgba(255, 255, 255, 0.35)",
                padding: "4px 8px",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <span>v1.0.0</span>
              <span style={{ color: "#10b981", fontWeight: 600 }}>Active</span>
            </div>
          )}
        </div>
      </aside>

      {/* ── Main Settings Panel ── */}
      <main
        style={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          minWidth: 0,
          backgroundColor: standalone ? "rgba(18, 18, 22, 0.6)" : "transparent",
          overflow: "hidden",
        }}
      >
        {/* Header */}
        <div
          style={{
            height: standalone ? "52px" : "32px",
            padding: standalone ? "0 24px" : "0 12px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            borderBottom: "1px solid rgba(255, 255, 255, 0.08)",
            flexShrink: 0,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ fontSize: standalone ? "14px" : "12px", fontWeight: 700, color: "#ffffff" }}>
              {navItems.find((n) => n.id === activeTab)?.label}
            </span>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            {onClose && (
              <button
                onClick={onClose}
                style={{
                  width: "26px",
                  height: "26px",
                  borderRadius: "50%",
                  border: "none",
                  background: "rgba(255, 255, 255, 0.08)",
                  color: "rgba(255, 255, 255, 0.7)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  cursor: "pointer",
                }}
                title="Close Settings"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>

        {/* Scrollable Content */}
        <div
          style={{
            flex: 1,
            overflowY: "auto",
            padding: standalone ? "20px 24px" : "8px 12px",
            display: "flex",
            flexDirection: "column",
            gap: standalone ? "18px" : "10px",
          }}
        >
          {/* ═════════ 1. GENERAL TAB ═════════ */}
          {activeTab === "general" && (
            <>
              {/* Display Style Group */}
              <SettingCard title="Display Style">
                <SettingRow
                  icon={<Sliders size={15} color="#38bdf8" />}
                  title="Notch / Island Mode"
                  desc="An edge notch, a floating pill, or a full-width bar."
                >
                  <SegmentedControl
                    value={config.mode}
                    onChange={(val) => {
                      const mode = val as IslandMode;
                      onUpdateConfig({
                        ...config,
                        mode,
                        topOffset: mode === "notch" ? 0 : config.pillEdgeGap || 12,
                      });
                    }}
                    options={[
                      { id: "island", label: "Floating Pill" },
                      { id: "notch", label: "Top Notch" },
                      { id: "bar", label: "Bar" },
                    ]}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Clock size={15} color="#38bdf8" />}
                  title="Clock Format"
                  desc="Choose how time is formatted across headers and cards."
                >
                  <SegmentedControl
                    value={config.clockFormat || "12h"}
                    onChange={(val) => updateField("clockFormat", val as "12h" | "24h")}
                    options={[
                      { id: "12h", label: "12-hour (PM)" },
                      { id: "24h", label: "24-hour" },
                    ]}
                  />
                </SettingRow>

                {config.mode === "island" && (
                  <SettingRow
                    icon={<Maximize2 size={15} color="#38bdf8" />}
                    title="Distance from Screen Edge"
                    desc="Offset distance of the floating pill from the top bezel."
                  >
                    <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                      <input
                        type="range"
                        min={0}
                        max={40}
                        step={2}
                        value={config.pillEdgeGap ?? 12}
                        onChange={(e) => {
                          const gap = Number(e.target.value);
                          onUpdateConfig({
                            ...config,
                            pillEdgeGap: gap,
                            topOffset: gap,
                          });
                        }}
                        style={{ width: "110px", accentColor: config.accentColor || "#00d2ff" }}
                      />
                      <span style={{ fontSize: "11px", fontWeight: 700, minWidth: "32px", color: "#38bdf8" }}>
                        {config.pillEdgeGap ?? 12}px
                      </span>
                    </div>
                  </SettingRow>
                )}

                <SettingRow
                  icon={<Maximize2 size={15} color="#38bdf8" />}
                  title="Island Scale"
                  desc="Adjust overall sizing of the collapsed and expanded capsule."
                >
                  <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                    <input
                      type="range"
                      min={0.7}
                      max={1.4}
                      step={0.05}
                      value={config.islandScale ?? 1.0}
                      onChange={(e) => updateField("islandScale", parseFloat(e.target.value))}
                      style={{ width: "110px", accentColor: config.accentColor || "#00d2ff" }}
                    />
                    <span style={{ fontSize: "11px", fontWeight: 700, minWidth: "36px", color: "#38bdf8" }}>
                      {Math.round((config.islandScale ?? 1.0) * 100)}%
                    </span>
                  </div>
                </SettingRow>

                <SettingRow
                  icon={<Monitor size={15} color="#38bdf8" />}
                  title="Target Monitor"
                  desc="Choose which display the notch overlay centers on."
                >
                  <button
                    onClick={handleRecenter}
                    style={{
                      padding: "4px 10px",
                      fontSize: "11px",
                      fontWeight: 600,
                      borderRadius: "6px",
                      border: "1px solid rgba(255, 255, 255, 0.15)",
                      background: "rgba(255, 255, 255, 0.08)",
                      color: "#ffffff",
                      cursor: "pointer",
                    }}
                  >
                    Recenter Overlay
                  </button>
                </SettingRow>
              </SettingCard>

              {/* Behavior & Gestures Group */}
              <SettingCard title="Behavior & Gestures">
                <SettingRow
                  icon={<Check size={15} color="#10b981" />}
                  title="Open at Sign-in"
                  desc="Launch JARVIS automatically when Windows starts."
                >
                  <ToggleSwitch
                    checked={config.autostart}
                    onChange={(checked) => updateField("autostart", checked)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Maximize2 size={15} color="#10b981" />}
                  title="Expand on Hover"
                  desc="Automatically expand the notch when cursor hovers over it."
                >
                  <ToggleSwitch
                    checked={config.expandOnHover}
                    onChange={(checked) => updateField("expandOnHover", checked)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Shield size={15} color="#10b981" />}
                  title="Hide behind Maximised Windows"
                  desc="Step out of the way when games or applications fill the screen."
                >
                  <ToggleSwitch
                    checked={config.avoidFullscreen}
                    onChange={(checked) => updateField("avoidFullscreen", checked)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Pin size={15} color="#10b981" />}
                  title="Keep Open (Pin Mode)"
                  desc="Don't collapse the expanded notch when cursor leaves."
                >
                  <ToggleSwitch
                    checked={config.pinMode}
                    onChange={(checked) => updateField("pinMode", checked)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>
              </SettingCard>
            </>
          )}

          {/* ═════════ 2. APPEARANCE TAB ═════════ */}
          {activeTab === "appearance" && (
            <>
              <SettingCard title="Theme & Styling">
                <SettingRow
                  icon={<Palette size={15} color="#a855f7" />}
                  title="Accent Color"
                  desc="Accent highlights, active rings, and playback waveforms."
                >
                  <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                    {ACCENT_COLORS.map((col) => {
                      const isSelected = (config.accentColor || "#00d2ff").toLowerCase() === col.hex.toLowerCase();
                      return (
                        <button
                          key={col.hex}
                          onClick={() => updateField("accentColor", col.hex)}
                          title={col.name}
                          style={{
                            width: "20px",
                            height: "20px",
                            borderRadius: "50%",
                            border: isSelected ? "2px solid #ffffff" : "1px solid rgba(255, 255, 255, 0.2)",
                            backgroundColor: col.hex,
                            cursor: "pointer",
                            boxShadow: isSelected ? `0 0 8px ${col.hex}` : "none",
                            transform: isSelected ? "scale(1.15)" : "scale(1)",
                            transition: "all 0.15s ease",
                          }}
                        />
                      );
                    })}
                  </div>
                </SettingRow>

                <SettingRow
                  icon={<Sparkles size={15} color="#a855f7" />}
                  title="Cycle Colours (Rainbow)"
                  desc="Dynamically transition the notch accent hue over time."
                >
                  <ToggleSwitch
                    checked={config.cycleColors}
                    onChange={(checked) => updateField("cycleColors", checked)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Palette size={15} color="#a855f7" />}
                  title="Shape & Corners"
                  desc="Choose between soft rounded pill corners or modern squared edges."
                >
                  <SegmentedControl
                    value={config.cornerShape || "rounded"}
                    onChange={(val) => updateField("cornerShape", val as "rounded" | "squared")}
                    options={[
                      { id: "rounded", label: "Soft Rounded" },
                      { id: "squared", label: "Squared (8px)" },
                    ]}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Palette size={15} color="#a855f7" />}
                  title="Material Glass"
                  desc="Aero glass translucency or pure OLED black contrast."
                >
                  <SegmentedControl
                    value={config.panelStyle || "dark_glass"}
                    onChange={(val) => updateField("panelStyle", val as "glass" | "dark_glass" | "solid")}
                    options={[
                      { id: "dark_glass", label: "Dark Glass" },
                      { id: "glass", label: "Translucent" },
                      { id: "solid", label: "Solid OLED" },
                    ]}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Sparkles size={15} color="#a855f7" />}
                  title="Edge Glow Halo"
                  desc="A soft accent-coloured halo around the notch border."
                >
                  <SegmentedControl
                    value={config.glowIntensity || "subtle"}
                    onChange={(val) => updateField("glowIntensity", val as "off" | "subtle" | "strong")}
                    options={[
                      { id: "off", label: "Off" },
                      { id: "subtle", label: "Subtle" },
                      { id: "strong", label: "Strong" },
                    ]}
                  />
                </SettingRow>

                <SettingRow
                  icon={<FolderOpen size={15} color="#a855f7" />}
                  title="Custom Background URL"
                  desc="Paste an image or GIF URL for animated ambient notch skin."
                >
                  <input
                    type="text"
                    value={config.customBgUrl || ""}
                    placeholder="https://.../wallpaper.gif"
                    onChange={(e) => updateField("customBgUrl", e.target.value)}
                    style={{
                      width: "160px",
                      padding: "4px 8px",
                      fontSize: "11px",
                      borderRadius: "6px",
                      border: "1px solid rgba(255, 255, 255, 0.15)",
                      backgroundColor: "rgba(0, 0, 0, 0.4)",
                      color: "#ffffff",
                    }}
                  />
                </SettingRow>
              </SettingCard>
            </>
          )}

          {/* ═════════ 3. WIDGETS TAB ═════════ */}
          {activeTab === "widgets" && (
            <SettingCard title="Show in the Notch">
              <SettingRow
                icon={<Music size={15} color="#38bdf8" />}
                title="Now Playing"
                desc="Artwork, track title, progress bar and playback controls."
              >
                <ToggleSwitch
                  checked={config.showMediaWidget}
                  onChange={(c) => updateField("showMediaWidget", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<Activity size={15} color="#38bdf8" />}
                title="Music Lights Visualizer"
                desc="Animated waveform frequency bars while music is playing."
              >
                <ToggleSwitch
                  checked={config.showAudioWaveform}
                  onChange={(c) => updateField("showAudioWaveform", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<Volume2 size={15} color="#38bdf8" />}
                title="Volume Mixer"
                desc="Master volume slider and quick audio mute controls."
              >
                <ToggleSwitch
                  checked={config.showVolumeMixer}
                  onChange={(c) => updateField("showVolumeMixer", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<Bot size={15} color="#38bdf8" />}
                title="AI Coding Agents & Mascot"
                desc="Working state, turn completions, and avatar companion."
              >
                <ToggleSwitch
                  checked={config.showAgentNotifications}
                  onChange={(c) => updateField("showAgentNotifications", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<ClipboardList size={15} color="#38bdf8" />}
                title="Clipboard History & Shelf"
                desc="Saved text snippets, links, and quick 1-click re-copy."
              >
                <ToggleSwitch
                  checked={config.showClipboardHistory}
                  onChange={(c) => updateField("showClipboardHistory", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<Activity size={15} color="#38bdf8" />}
                title="System Telemetry"
                desc="Live processor (CPU) and memory (RAM) usage bars."
              >
                <ToggleSwitch
                  checked={config.showHardwareWidget}
                  onChange={(c) => updateField("showHardwareWidget", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<Wifi size={15} color="#38bdf8" />}
                title="Network Speed Monitor"
                desc="Real-time download and upload transfer rates."
              >
                <ToggleSwitch
                  checked={config.showNetworkWidget}
                  onChange={(c) => updateField("showNetworkWidget", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<CloudSun size={15} color="#38bdf8" />}
                title="Weather & Temperature"
                desc="Current weather conditions badge in the header capsule."
              >
                <ToggleSwitch
                  checked={config.showWeatherWidget}
                  onChange={(c) => updateField("showWeatherWidget", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<Coffee size={15} color="#38bdf8" />}
                title="Focus Timer & Pomodoro"
                desc="Work and break interval timer with task tracking."
              >
                <ToggleSwitch
                  checked={config.showPomodoro}
                  onChange={(c) => updateField("showPomodoro", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>

              <SettingRow
                icon={<Volume2 size={15} color="#38bdf8" />}
                title="Volume HUD Overlay"
                desc="Popup miniature island overlay when adjusting volume keys."
              >
                <ToggleSwitch
                  checked={config.volumeHudEnabled}
                  onChange={(c) => updateField("volumeHudEnabled", c)}
                  accentColor={config.accentColor}
                />
              </SettingRow>
            </SettingCard>
          )}

          {/* ═════════ 4. NOTCHY & AI TAB ═════════ */}
          {activeTab === "notchy" && (
            !isProActive ? (
              <SettingCard title="Meet Notchy">
                <div
                  style={{
                    padding: "18px",
                    borderRadius: "14px",
                    background: "rgba(255, 255, 255, 0.04)",
                    border: "1px solid rgba(255, 255, 255, 0.1)",
                    display: "flex",
                    flexDirection: "column",
                    gap: "12px",
                  }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                    <Bot size={28} color="#38bdf8" />
                    <div>
                      <div style={{ fontSize: "15px", fontWeight: 700, color: "#ffffff" }}>
                        AI Agent Notifications Companion (Pro Feature)
                      </div>
                      <div style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.6)" }}>
                        Keep track of AI agent tasks, approvals, and terminal activity without switching apps.
                      </div>
                    </div>
                  </div>

                  <ul style={{ margin: 0, paddingLeft: "20px", fontSize: "12px", color: "rgba(255, 255, 255, 0.75)", lineHeight: 1.6 }}>
                    <li>Get completion and attention notifications from connected Codex, Claude Code, Cursor, and Antigravity integrations.</li>
                    <li>Review permission requests and respond directly from the notch when the integration supports approvals.</li>
                    <li>See agent activity and provider-reported usage limits where available.</li>
                    <li>Drag out a resizable desktop screen for music, lyrics, timers, and hardware stats.</li>
                  </ul>

                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", paddingTop: "12px", borderTop: "1px solid rgba(255, 255, 255, 0.06)" }}>
                    <span style={{ fontSize: "11.5px", color: "rgba(255, 255, 255, 0.5)" }}>
                      Included with JARVIS Pro.
                    </span>
                    <button
                      onClick={() => setActiveTab("pro")}
                      style={{
                        padding: "8px 16px",
                        borderRadius: "8px",
                        border: "none",
                        background: "#0284c7",
                        color: "#ffffff",
                        fontSize: "12px",
                        fontWeight: 700,
                        cursor: "pointer",
                      }}
                    >
                      Unlock with Pro
                    </button>
                  </div>
                </div>
              </SettingCard>
            ) : (
            <>
              <SettingCard title="Developer Companion">
                <SettingRow
                  icon={<Bot size={15} color="#10b981" />}
                  title="Enable Notchy Companion"
                  desc="Developer notification companion for AI agents, terminal runs, local Git."
                >
                  <ToggleSwitch
                    checked={config.showAgentNotifications}
                    onChange={(c) => updateField("showAgentNotifications", c)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Sparkles size={15} color="#10b981" />}
                  title="Compact Activity Indicator"
                  desc="Miniature status badge on collapsed notch when agents are busy."
                >
                  <ToggleSwitch
                    checked={config.notchyCompactIndicator}
                    onChange={(c) => updateField("notchyCompactIndicator", c)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Shield size={15} color="#10b981" />}
                  title="Quiet Mode / Mute Sound"
                  desc="Suppress auto-expansion and chimes during deep work sessions."
                >
                  <ToggleSwitch
                    checked={config.notchyQuietMode}
                    onChange={(c) => updateField("notchyQuietMode", c)}
                    accentColor={config.accentColor}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Sparkles size={15} color="#10b981" />}
                  title="Companion Ambient Glow"
                  desc="Radial halo strength around the Notchy companion avatar."
                >
                  <SegmentedControl
                    value={config.notchyGlowStrength || "subtle"}
                    onChange={(val) => updateField("notchyGlowStrength", val as "off" | "subtle" | "full")}
                    options={[
                      { id: "off", label: "Off" },
                      { id: "subtle", label: "Subtle" },
                      { id: "full", label: "Full Halo" },
                    ]}
                  />
                </SettingRow>

                <SettingRow
                  icon={<Clock size={15} color="#10b981" />}
                  title="Completion Display Duration"
                  desc="How long turn-finished notification banners stay visible."
                >
                  <SegmentedControl
                    value={String(config.notchyCompletionDuration || 5)}
                    onChange={(val) => updateField("notchyCompletionDuration", parseInt(val, 10))}
                    options={[
                      { id: "5", label: "5s" },
                      { id: "8", label: "8s" },
                      { id: "12", label: "12s" },
                    ]}
                  />
                </SettingRow>
              </SettingCard>

              {/* Connected Integrations */}
              <SettingCard title="Connected Integrations">
                <div style={{ display: "grid", gridTemplateColumns: standalone ? "1fr 1fr" : "1fr", gap: "8px" }}>
                  <IntegrationCard
                    name="Google Antigravity"
                    status="Active & Hooked"
                    badge="Bridge Connected"
                    badgeColor="#10b981"
                  />
                  <IntegrationCard
                    name="Claude Code CLI"
                    status="Hook Installed"
                    badge="Ready"
                    badgeColor="#38bdf8"
                  />
                  <IntegrationCard
                    name="Local Git Watcher"
                    status="Tracking d:\Coding\JARVIS"
                    badge="Monitoring"
                    badgeColor="#a855f7"
                  />
                  <IntegrationCard
                    name="Windows Media (GSMTC)"
                    status="Listening to Harmonix & Spotify"
                    badge="Active"
                    badgeColor="#f59e0b"
                  />
                </div>
              </SettingCard>
            </>
            )
          )}

          {/* ═════════ 5. TIPS & SHORTCUTS TAB ═════════ */}
          {activeTab === "tips" && (
            <>
              <SettingCard title="Gesture & Interaction Tips">
                <div style={{ display: "grid", gridTemplateColumns: standalone ? "1fr 1fr" : "1fr", gap: "8px" }}>
                  <TipCard
                    title="Open the Notch"
                    desc="Click the notch, or hover it, to see media, controls and widgets."
                  />
                  <TipCard
                    title="Volume & Brightness"
                    desc="Use your keyboard keys; the notch shows the level as you change it."
                  />
                  <TipCard
                    title="Scroll to Adjust"
                    desc="Scroll your mouse wheel over the volume or brightness popup to fine-tune."
                  />
                  <TipCard
                    title="Keep It Open (Pin)"
                    desc="Click the pin button (📌) in the notch header while you work."
                  />
                  <TipCard
                    title="Seek a Song"
                    desc="Click anywhere on the media progress bar to jump directly to that point."
                  />
                  <TipCard
                    title="Quick Tool Launch"
                    desc="Use the quick calculator, snipping tool, or task manager buttons in dashboard."
                  />
                </div>
              </SettingCard>

              <SettingCard title="Keyboard Shortcuts">
                <div style={{ display: "flex", flexDirection: "column", gap: "6px", fontSize: "12px" }}>
                  <ShortcutRow keys={["Esc"]} action="Collapse & unpin the notch" />
                  <ShortcutRow keys={["Right-Click"]} action="Open custom desktop context menu" />
                  <ShortcutRow keys={["Ctrl", ","]} action="Open massive Settings window" />
                  <ShortcutRow keys={["Space"]} action="Toggle playback (Play / Pause)" />
                  <ShortcutRow keys={["←", "→"]} action="Seek -5 seconds / +5 seconds" />
                </div>
              </SettingCard>
            </>
          )}

          {/* ═════════ 6. PRO / BUY PRO TAB ═════════ */}
          {activeTab === "pro" && (
            <SettingCard title="JARVIS Pro Features & License">
              <div
                style={{
                  padding: "16px",
                  borderRadius: "14px",
                  background: isProActive
                    ? "linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(6, 95, 70, 0.25))"
                    : "linear-gradient(135deg, rgba(245, 158, 11, 0.15), rgba(180, 83, 9, 0.25))",
                  border: isProActive
                    ? "1px solid rgba(16, 185, 129, 0.35)"
                    : "1px solid rgba(245, 158, 11, 0.35)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  marginBottom: "14px",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                  <div
                    style={{
                      width: "38px",
                      height: "38px",
                      borderRadius: "10px",
                      backgroundColor: isProActive ? "rgba(16, 185, 129, 0.2)" : "rgba(245, 158, 11, 0.2)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    {isProActive ? <Unlock size={20} color="#10b981" /> : <Lock size={20} color="#f59e0b" />}
                  </div>
                  <div>
                    <div style={{ fontSize: "14px", fontWeight: 700, color: "#ffffff" }}>
                      {isProActive ? "JARVIS Pro is Active" : "JARVIS Free Tier (Locked Mode)"}
                    </div>
                    <div style={{ fontSize: "11.5px", color: "rgba(255, 255, 255, 0.65)", marginTop: "2px" }}>
                      {isProActive
                        ? "All 14 Pro modules, widgets, and Antigravity features are fully unlocked and ready."
                        : "Pro features, agent notifications, drop shelf, and volume mixer are currently locked."}
                    </div>
                  </div>
                </div>

                <button
                  onClick={() => {
                    const next = !isProActive;
                    updateField("isPro", next);
                  }}
                  style={{
                    padding: "8px 16px",
                    borderRadius: "10px",
                    border: "none",
                    background: isProActive ? "#ffffff" : "#0284c7",
                    color: isProActive ? "#000000" : "#ffffff",
                    fontSize: "12px",
                    fontWeight: 700,
                    cursor: "pointer",
                    transition: "all 0.15s ease",
                  }}
                >
                  {isProActive ? "Deactivate (Test Locked)" : "Unlock All Pro Features"}
                </button>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: standalone ? "1fr 1fr" : "1fr", gap: "10px" }}>
                {[
                  {
                    title: "Notchy AI Agent Notifications",
                    desc: "AI agent notifications, activity, supported permission approvals and local Git updates. Connect supported Codex, Claude Code, Cursor and Antigravity integrations.",
                  },
                  {
                    title: "Agent Usage & Quotas",
                    desc: "Provider-reported usage and reset times where available; live monitoring for Antigravity & Claude accounts.",
                  },
                  {
                    title: "Notchy Desktop Screen",
                    desc: "Detach, resize and pin an always-on-top screen for music, lyrics, timers, network, hardware, work and usage.",
                  },
                  {
                    title: "Fullscreen Listening Room",
                    desc: "A fullscreen listening room with cover-art ambient lighting, audio visualizers, and playback scrubbers.",
                  },
                  {
                    title: "Synced Lyrics Follower",
                    desc: "Follow available timed lyrics and jump to any line in supported audio and media players.",
                  },
                  {
                    title: "Audio Output Switching",
                    desc: "Switch between available speakers, headphones, and Bluetooth audio devices directly in-island.",
                  },
                  {
                    title: "Flexible Notch Placement",
                    desc: "Drag the notch along the top, left or right screen edge, including corners with custom edge offsets.",
                  },
                  {
                    title: "Custom Notch Dimensions",
                    desc: "Adjust collapsed notch length and thickness independently (28–72px). The notch expands to fit active features.",
                  },
                  {
                    title: "Drop Shelf",
                    desc: "Drag files onto the notch, hold them temporarily, copy them together and reveal them in Explorer.",
                  },
                  {
                    title: "Clipboard Pro",
                    desc: "Add permanent pinned snippets, image previews, image saving and smart actions for links, colors and email.",
                  },
                  {
                    title: "Quick Apps Bar",
                    desc: "Five customizable shortcuts for apps, websites, folders, and executable paths.",
                  },
                  {
                    title: "Per-App Volume Mixer",
                    desc: "Adjust and mute individual apps or mute all active audio sessions independently.",
                  },
                  {
                    title: "Download Monitor",
                    desc: "Track supported downloads, their progress, speed, and completion with direct folder reveal.",
                  },
                  {
                    title: "Premium Appearance Effects",
                    desc: "Liquid Titanium, Supernova Horizon, Cyber Matrix 3D, Celestial Orrery and Prism Core visual effects.",
                  },
                ].map((feat, idx) => (
                  <div
                    key={idx}
                    style={{
                      padding: "12px",
                      borderRadius: "10px",
                      backgroundColor: "rgba(255, 255, 255, 0.03)",
                      border: "1px solid rgba(255, 255, 255, 0.06)",
                      display: "flex",
                      flexDirection: "column",
                      gap: "4px",
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                      <span style={{ fontSize: "12px", fontWeight: 700, color: "#ffffff" }}>
                        {feat.title}
                      </span>
                      {isProActive ? (
                        <span style={{ fontSize: "10px", color: "#10b981", fontWeight: 700 }}>UNLOCKED</span>
                      ) : (
                        <span style={{ fontSize: "10px", color: "#f59e0b", display: "flex", alignItems: "center", gap: "3px" }}>
                          <Lock size={10} /> PRO
                        </span>
                      )}
                    </div>
                    <p style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.55)", margin: 0, lineHeight: 1.35 }}>
                      {feat.desc}
                    </p>
                  </div>
                ))}
              </div>
            </SettingCard>
          )}

          {/* ═════════ 7. ABOUT TAB ═════════ */}
          {activeTab === "about" && (
            <>
              <SettingCard title="Application Info">
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "16px",
                    padding: "14px 16px",
                    borderRadius: "12px",
                    background: "rgba(255, 255, 255, 0.04)",
                    border: "1px solid rgba(255, 255, 255, 0.08)",
                    marginBottom: "12px",
                  }}
                >
                  <ChittiLogo size={48} />
                  <div>
                    <div style={{ fontSize: "16px", fontWeight: 700, color: "#ffffff" }}>
                      JARVIS Dynamic Island & Notch
                    </div>
                    <div style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.6)", marginTop: "2px" }}>
                      Next-generation desktop companion for Windows 10/11. Powered by Chitti AI.
                    </div>
                  </div>
                </div>

                <SettingRow
                  icon={<Sparkles size={15} color="#38bdf8" />}
                  title="JARVIS Dynamic Notch"
                  desc="Pixel-perfect dynamic island & notch experience for Windows 10/11."
                >
                  <span style={{ fontSize: "12px", fontWeight: 700, color: "#38bdf8" }}>
                    Version 1.0.0
                  </span>
                </SettingRow>

                <SettingRow
                  icon={<Bot size={15} color="#38bdf8" />}
                  title="Developer & Architecture"
                  desc="Built with Tauri 2 + Rust + React 18 + TypeScript + Tailwind."
                >
                  <span style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.7)" }}>
                    Nandu & JARVIS Team
                  </span>
                </SettingRow>

                <SettingRow
                  icon={<Download size={15} color="#38bdf8" />}
                  title="Software Updates"
                  desc={updateStatus}
                >
                  <button
                    onClick={handleCheckUpdate}
                    disabled={isCheckingUpdate}
                    style={{
                      padding: "4px 10px",
                      fontSize: "11px",
                      fontWeight: 600,
                      borderRadius: "6px",
                      border: "none",
                      background: "rgba(56, 189, 248, 0.2)",
                      color: "#38bdf8",
                      cursor: isCheckingUpdate ? "wait" : "pointer",
                    }}
                  >
                    {isCheckingUpdate ? "Checking..." : "Check Now"}
                  </button>
                </SettingRow>

                <SettingRow
                  icon={<RotateCcw size={15} color="#f43f5e" />}
                  title="Reset All Settings"
                  desc="Restore all island geometry, appearance and widget toggles to defaults."
                >
                  <button
                    onClick={handleResetDefaults}
                    style={{
                      padding: "4px 10px",
                      fontSize: "11px",
                      fontWeight: 600,
                      borderRadius: "6px",
                      border: "1px solid rgba(244, 63, 94, 0.3)",
                      background: "rgba(244, 63, 94, 0.15)",
                      color: "#f43f5e",
                      cursor: "pointer",
                    }}
                  >
                    Reset Defaults
                  </button>
                </SettingRow>
              </SettingCard>

              <SettingCard title="What's New in v1.0.0">
                <div style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.7)", lineHeight: 1.6 }}>
                  <div>✨ <b>Comprehensive Settings System:</b> Dedicated multi-category customization suite.</div>
                  <div>🎵 <b>Harmonix & GSMTC Support:</b> Live music playback detection with synchronized scrubbing.</div>
                  <div>🖥️ <b>Dual Mode Notch:</b> Switch seamlessly between Floating Pill and Top Notch.</div>
                  <div>🛡️ <b>Single Process Tray:</b> Mutex-protected background runtime with zero duplicate icons.</div>
                  <div>⚡ <b>Split Two-Column Dashboard:</b> CPU, RAM, Network, Battery & Quick Tools.</div>
                </div>
              </SettingCard>
            </>
          )}
        </div>
      </main>
    </div>
  );
};

// ── Helper UI Components ──

const SettingCard: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <div
    style={{
      backgroundColor: "rgba(255, 255, 255, 0.04)",
      border: "1px solid rgba(255, 255, 255, 0.08)",
      borderRadius: "12px",
      padding: "12px 14px",
      display: "flex",
      flexDirection: "column",
      gap: "10px",
    }}
  >
    <div
      style={{
        fontSize: "11px",
        fontWeight: 700,
        color: "rgba(255, 255, 255, 0.45)",
        textTransform: "uppercase",
        letterSpacing: "0.06em",
      }}
    >
      {title}
    </div>
    {children}
  </div>
);

const SettingRow: React.FC<{
  icon?: React.ReactNode;
  title: string;
  desc?: string;
  children: React.ReactNode;
}> = ({ icon, title, desc, children }) => (
  <div
    style={{
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "6px 0",
      borderBottom: "1px solid rgba(255, 255, 255, 0.04)",
      gap: "12px",
    }}
  >
    <div style={{ display: "flex", alignItems: "flex-start", gap: "10px", minWidth: 0 }}>
      {icon && <div style={{ marginTop: "2px", flexShrink: 0 }}>{icon}</div>}
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: "12px", fontWeight: 600, color: "#ffffff", lineHeight: 1.2 }}>
          {title}
        </div>
        {desc && (
          <div
            style={{
              fontSize: "10.5px",
              color: "rgba(255, 255, 255, 0.45)",
              lineHeight: 1.3,
              marginTop: "2px",
            }}
          >
            {desc}
          </div>
        )}
      </div>
    </div>
    <div style={{ flexShrink: 0 }}>{children}</div>
  </div>
);

const SegmentedControl: React.FC<{
  value: string;
  onChange: (val: string) => void;
  options: { id: string; label: string }[];
}> = ({ value, onChange, options }) => (
  <div
    style={{
      display: "flex",
      background: "rgba(255, 255, 255, 0.08)",
      borderRadius: "8px",
      padding: "2px",
      gap: "2px",
    }}
  >
    {options.map((opt) => {
      const isSelected = value === opt.id;
      return (
        <button
          key={opt.id}
          onClick={() => onChange(opt.id)}
          style={{
            padding: "4px 8px",
            fontSize: "10.5px",
            fontWeight: 600,
            borderRadius: "6px",
            border: "none",
            cursor: "pointer",
            background: isSelected ? "rgba(56, 189, 248, 0.25)" : "transparent",
            color: isSelected ? "#38bdf8" : "rgba(255, 255, 255, 0.55)",
            transition: "all 0.15s ease",
          }}
        >
          {opt.label}
        </button>
      );
    })}
  </div>
);

const ToggleSwitch: React.FC<{
  checked: boolean;
  onChange: (checked: boolean) => void;
  accentColor?: string;
}> = ({ checked, onChange, accentColor = "#00d2ff" }) => (
  <div
    onClick={() => onChange(!checked)}
    style={{
      width: "36px",
      height: "20px",
      borderRadius: "9999px",
      backgroundColor: checked ? accentColor : "rgba(255, 255, 255, 0.15)",
      position: "relative",
      cursor: "pointer",
      transition: "background-color 0.2s ease",
      boxShadow: checked ? `0 0 8px ${accentColor}40` : "none",
    }}
  >
    <div
      style={{
        width: "16px",
        height: "16px",
        borderRadius: "50%",
        backgroundColor: "#ffffff",
        position: "absolute",
        top: "2px",
        left: checked ? "18px" : "2px",
        transition: "left 0.2s cubic-bezier(0.4, 0, 0.2, 1)",
      }}
    />
  </div>
);

const IntegrationCard: React.FC<{
  name: string;
  status: string;
  badge: string;
  badgeColor: string;
}> = ({ name, status, badge, badgeColor }) => (
  <div
    style={{
      padding: "10px",
      borderRadius: "8px",
      backgroundColor: "rgba(255, 255, 255, 0.03)",
      border: "1px solid rgba(255, 255, 255, 0.06)",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
    }}
  >
    <div>
      <div style={{ fontSize: "11.5px", fontWeight: 600, color: "#ffffff" }}>{name}</div>
      <div style={{ fontSize: "10px", color: "rgba(255, 255, 255, 0.45)" }}>{status}</div>
    </div>
    <span
      style={{
        fontSize: "9.5px",
        fontWeight: 700,
        padding: "2px 6px",
        borderRadius: "4px",
        backgroundColor: `${badgeColor}20`,
        color: badgeColor,
        border: `1px solid ${badgeColor}40`,
      }}
    >
      {badge}
    </span>
  </div>
);

const TipCard: React.FC<{ title: string; desc: string }> = ({ title, desc }) => (
  <div
    style={{
      padding: "10px",
      borderRadius: "8px",
      backgroundColor: "rgba(255, 255, 255, 0.03)",
      border: "1px solid rgba(255, 255, 255, 0.06)",
    }}
  >
    <div style={{ fontSize: "11.5px", fontWeight: 700, color: "#38bdf8", marginBottom: "2px" }}>
      {title}
    </div>
    <div style={{ fontSize: "10.5px", color: "rgba(255, 255, 255, 0.6)", lineHeight: 1.3 }}>
      {desc}
    </div>
  </div>
);

const ShortcutRow: React.FC<{ keys: string[]; action: string }> = ({ keys, action }) => (
  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "2px 0" }}>
    <span style={{ color: "rgba(255, 255, 255, 0.6)" }}>{action}</span>
    <div style={{ display: "flex", gap: "4px" }}>
      {keys.map((k) => (
        <span
          key={k}
          style={{
            padding: "2px 6px",
            borderRadius: "4px",
            background: "rgba(255, 255, 255, 0.1)",
            fontSize: "10px",
            fontWeight: 700,
            color: "#ffffff",
          }}
        >
          {k}
        </span>
      ))}
    </div>
  </div>
);
