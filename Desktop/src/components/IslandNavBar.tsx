import React, { FC } from "react";
import { motion } from "framer-motion";
import { Music, Clock, Sliders, ClipboardList, Activity } from "lucide-react";
import { playTactileSound } from "../utils/audioFeedback";
import "./IslandNavBar.css";

export type IslandTabId = "media" | "focus" | "controls" | "productivity" | "system";

export interface IslandNavBarProps {
  activeTab: IslandTabId;
  onTabChange: (tab: IslandTabId) => void;
  isMusicPlaying?: boolean;
  timerActive?: boolean;
  timerSeconds?: number;
  productivityCount?: number;
}

interface TabDef {
  id: IslandTabId;
  label: string;
  icon: React.ComponentType<{ size?: number; strokeWidth?: number; className?: string }>;
  keyShortcut: string;
}

const TABS: TabDef[] = [
  { id: "media", label: "Media", icon: Music, keyShortcut: "1" },
  { id: "focus", label: "Focus", icon: Clock, keyShortcut: "2" },
  { id: "controls", label: "Controls", icon: Sliders, keyShortcut: "3" },
  { id: "productivity", label: "Productivity", icon: ClipboardList, keyShortcut: "4" },
  { id: "system", label: "System", icon: Activity, keyShortcut: "5" },
];

export const IslandNavBar: FC<IslandNavBarProps> = ({
  activeTab,
  onTabChange,
  isMusicPlaying = false,
  timerActive = false,
  timerSeconds = 0,
  productivityCount = 0,
}) => {
  const handleSelect = (id: IslandTabId) => {
    if (id !== activeTab) {
      playTactileSound("switch");
      onTabChange(id);
    }
  };

  return (
    <nav className="island-nav-bar" aria-label="Island Navigation">
      <div className="island-nav-track">
        {TABS.map((tab) => {
          const isActive = activeTab === tab.id;
          const Icon = tab.icon;

          return (
            <button
              key={tab.id}
              type="button"
              className={`island-nav-tab ${isActive ? "active" : ""}`}
              onClick={() => handleSelect(tab.id)}
              aria-selected={isActive}
              role="tab"
              title={`${tab.label} (Press ${tab.keyShortcut})`}
            >
              {isActive && (
                <motion.div
                  layoutId="active-island-segmented-pill"
                  className="island-nav-indicator"
                  transition={{ type: "spring", stiffness: 440, damping: 32 }}
                />
              )}
              <span className="island-nav-tab-content">
                <Icon size={13} strokeWidth={2.2} className="island-nav-icon" />
                <span className="island-nav-label">{tab.label}</span>
                {tab.id === "media" && isMusicPlaying && (
                  <span className="island-nav-live-dot" title="Music Playing" />
                )}
                {tab.id === "focus" && timerActive && (
                  <span className="island-nav-badge timer-badge">
                    {Math.ceil(timerSeconds / 60)}m
                  </span>
                )}
                {tab.id === "productivity" && productivityCount > 0 && (
                  <span className="island-nav-badge count-badge">{productivityCount}</span>
                )}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
