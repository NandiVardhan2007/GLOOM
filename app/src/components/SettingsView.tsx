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
} from "lucide-react";

export type SettingsCategory =
  | "general"
  | "appearance"
  | "widgets"
  | "tips"
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
  const [updateStatus, setUpdateStatus] = useState<string>("v1.0.7 (Latest)");
  const [isCheckingUpdate, setIsCheckingUpdate] = useState<boolean>(false);
  const [isInstallingUpdate, setIsInstallingUpdate] = useState<boolean>(false);
  const [updateInfo, setUpdateInfo] = useState<{
    hasUpdate: boolean;
    latestTag: string;
    downloadUrl: string;
    apkDownloadUrl: string;
  } | null>(null);

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

  const handleCheckUpdate = async () => {
    setIsCheckingUpdate(true);
    setUpdateStatus("Connecting to GitHub Releases...");
    try {
      const res = await fetch("https://api.github.com/repos/NandiVardhan2007/JARVIS/releases/latest");
      if (!res.ok) {
        throw new Error(`Status ${res.status}`);
      }
      const data = await res.json();
      const latestTag = data.tag_name || "v1.0.7";
      const assets = data.assets || [];
      const exe = assets.find((a: any) => a.name.endsWith(".exe"));
      const apk = assets.find((a: any) => a.name.endsWith(".apk"));
      const currentVersion = "v1.0.7";
      const hasUpdate = latestTag !== currentVersion;

      if (hasUpdate) {
        setUpdateStatus(`New release ${latestTag} is available!`);
      } else {
        setUpdateStatus(`You are running the latest version (${currentVersion})`);
      }

      setUpdateInfo({
        hasUpdate,
        latestTag,
        downloadUrl: exe?.browser_download_url || `https://github.com/NandiVardhan2007/JARVIS/releases/download/${latestTag}/JARVIS-Island-Windows.exe`,
        apkDownloadUrl: apk?.browser_download_url || data.html_url,
      });
    } catch {
      setUpdateStatus("Running v1.0.7 (Latest Release)");
    } finally {
      setIsCheckingUpdate(false);
    }
  };

  const handleInstallUpdate = async () => {
    if (!updateInfo?.downloadUrl) return;
    setIsInstallingUpdate(true);
    setUpdateStatus(`Downloading and installing ${updateInfo.latestTag}...`);
    try {
      await invoke("jarvis_install_update", { downloadUrl: updateInfo.downloadUrl });
      setUpdateStatus("Restarting JARVIS with latest version...");
    } catch (e: any) {
      setUpdateStatus(`Update failed: ${e?.message || e}`);
      setIsInstallingUpdate(false);
    }
  };

  const navItems: { id: SettingsCategory; label: string; icon: React.ReactNode }[] = [
    { id: "general", label: "General", icon: <Sliders size={15} /> },
    { id: "appearance", label: "Appearance", icon: <Palette size={15} /> },
    { id: "widgets", label: "Widgets", icon: <LayoutGrid size={15} /> },
    { id: "tips", label: "Tips & Hotkeys", icon: <Lightbulb size={15} /> },
    { id: "about", label: "About", icon: <Info size={15} /> },
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

          {/* ═════════ 6. ABOUT TAB ═════════ */}
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
                  <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                    <button
                      onClick={handleCheckUpdate}
                      disabled={isCheckingUpdate || isInstallingUpdate}
                      style={{
                        padding: "5px 12px",
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
                    {updateInfo && (
                      <button
                        onClick={handleInstallUpdate}
                        disabled={isInstallingUpdate}
                        style={{
                          padding: "5px 14px",
                          fontSize: "11px",
                          fontWeight: 600,
                          borderRadius: "6px",
                          border: "none",
                          background: "#38bdf8",
                          color: "#000",
                          cursor: isInstallingUpdate ? "wait" : "pointer",
                          display: "flex",
                          alignItems: "center",
                          gap: "6px",
                        }}
                      >
                        {isInstallingUpdate
                          ? "Downloading & Installing..."
                          : updateInfo.hasUpdate
                          ? `Update to ${updateInfo.latestTag}`
                          : "Reinstall Latest"}
                      </button>
                    )}
                  </div>
                </SettingRow>

                <SettingRow
                  icon={<ExternalLink size={15} color="#10b981" />}
                  title="PowerShell Setup Installer"
                  desc="Run a single command in PowerShell to automatically install or update JARVIS Desktop"
                >
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(
                        'irm https://raw.githubusercontent.com/NandiVardhan2007/JARVIS/main/install-desktop.ps1 | iex'
                      );
                      alert("Copied PowerShell command! Open PowerShell and press Enter to install/update JARVIS Desktop.");
                    }}
                    style={{
                      padding: "5px 12px",
                      fontSize: "11px",
                      fontWeight: 600,
                      borderRadius: "6px",
                      border: "1px solid rgba(16, 185, 129, 0.4)",
                      background: "rgba(16, 185, 129, 0.15)",
                      color: "#10b981",
                      cursor: "pointer",
                    }}
                  >
                    Copy Setup Command
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
