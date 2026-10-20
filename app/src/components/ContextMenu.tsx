import React, { useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  Settings,
  Pin,
  PinOff,
  Minimize2,
  Maximize2,
  Compass,
  Laptop,
  LayoutGrid,
  Music,
  Sliders,
  FileText,
  Activity,
  Clipboard,
  Power,
} from "lucide-react";
import { ActiveTab, AppConfig } from "../types";

export interface ContextMenuProps {
  x: number;
  y: number;
  isExpanded: boolean;
  isPinned: boolean;
  config: AppConfig;
  onClose: () => void;
  onSelectTab: (tab: ActiveTab) => void;
  onToggleExpand: () => void;
  onTogglePin: () => void;
  onToggleMode: () => void;
  menuRef?: React.RefObject<HTMLDivElement | null>;
}

export const ContextMenu: React.FC<ContextMenuProps> = ({
  x,
  y,
  isExpanded,
  isPinned,
  config,
  onClose,
  onSelectTab,
  onToggleExpand,
  onTogglePin,
  onToggleMode,
  menuRef: externalRef,
}) => {
  const internalRef = useRef<HTMLDivElement>(null);
  const activeMenuRef = externalRef || internalRef;

  // Close when clicking outside or pressing Escape
  useEffect(() => {
    const handleDown = (e: MouseEvent) => {
      if (activeMenuRef.current && !activeMenuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };

    window.addEventListener("pointerdown", handleDown);
    window.addEventListener("keydown", handleKey);
    return () => {
      window.removeEventListener("pointerdown", handleDown);
      window.removeEventListener("keydown", handleKey);
    };
  }, [onClose, activeMenuRef]);

  const handleRecenter = async () => {
    try {
      await invoke("jarvis_recenter");
      onClose();
    } catch (e) {
      console.error(e);
    }
  };

  const handleExit = async () => {
    try {
      await invoke("jarvis_exit");
    } catch (e) {
      console.error(e);
    }
  };

  const itemStyle: React.CSSProperties = {
    display: "flex",
    alignItems: "center",
    gap: "9px",
    width: "100%",
    padding: "6px 10px",
    borderRadius: "8px",
    border: "none",
    background: "transparent",
    color: "#f1f5f9",
    fontSize: "12px",
    fontWeight: 500,
    cursor: "pointer",
    textAlign: "left",
    transition: "background 0.12s ease",
  };

  const separatorStyle: React.CSSProperties = {
    height: "1px",
    background: "rgba(255, 255, 255, 0.1)",
    margin: "4px 0",
  };

  // Adjust x and y so menu doesn't overflow screen
  const menuWidth = 190;
  const menuHeight = 360;
  const left = Math.min(x, window.innerWidth - menuWidth - 12);
  const top = Math.min(y, window.innerHeight - menuHeight - 12);

  return (
    <div
      ref={activeMenuRef}
      onContextMenu={(e) => e.preventDefault()}
      style={{
        position: "fixed",
        left: `${Math.max(10, left)}px`,
        top: `${Math.max(10, top)}px`,
        width: `${menuWidth}px`,
        background: "#161622",
        border: "1px solid rgba(255, 255, 255, 0.16)",
        borderRadius: "12px",
        padding: "6px",
        boxShadow: "0 16px 36px rgba(0, 0, 0, 0.9), 0 4px 12px rgba(0, 0, 0, 0.6)",
        zIndex: 100000,
        userSelect: "none",
      }}
    >
      {/* Settings (In-Island Settings) */}
      <button
        onClick={() => {
          onSelectTab("settings");
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Settings size={14} color="#38bdf8" />
        <span>Settings</span>
      </button>

      {/* Expand / Collapse */}
      <button
        onClick={() => {
          onToggleExpand();
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        {isExpanded ? (
          <>
            <Minimize2 size={14} color="#94a3b8" />
            <span>Collapse Island</span>
          </>
        ) : (
          <>
            <Maximize2 size={14} color="#34d399" />
            <span>Expand Island</span>
          </>
        )}
      </button>

      {/* Pin / Unpin */}
      <button
        onClick={() => {
          onTogglePin();
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        {isPinned ? (
          <>
            <PinOff size={14} color="#f87171" />
            <span>Unpin Island</span>
          </>
        ) : (
          <>
            <Pin size={14} color="#60a5fa" />
            <span>Pin Island Open</span>
          </>
        )}
      </button>

      {/* Recenter */}
      <button
        onClick={handleRecenter}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Compass size={14} color="#cbd5e1" />
        <span>Recenter Display</span>
      </button>

      {/* Mode switch */}
      <button
        onClick={() => {
          onToggleMode();
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Laptop size={14} color="#cbd5e1" />
        <span>Mode: {config.mode === "island" ? "Floating" : "Flush Notch"}</span>
      </button>

      <div style={separatorStyle} />

      {/* Jump to Tabs */}
      <button
        onClick={() => {
          onSelectTab("dashboard");
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <LayoutGrid size={13} color="#38bdf8" />
        <span>Dashboard</span>
      </button>

      <button
        onClick={() => {
          onSelectTab("media");
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Music size={13} color="#818cf8" />
        <span>Media Player</span>
      </button>

      <button
        onClick={() => {
          onSelectTab("controls");
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Sliders size={13} color="#38bdf8" />
        <span>Command Center</span>
      </button>

      <button
        onClick={() => {
          onSelectTab("notes");
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <FileText size={13} color="#fbbf24" />
        <span>Quick Notes</span>
      </button>

      <button
        onClick={() => {
          onSelectTab("system");
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Activity size={13} color="#34d399" />
        <span>Activity Monitor</span>
      </button>

      <button
        onClick={() => {
          onSelectTab("clipboard");
          onClose();
        }}
        style={itemStyle}
        onMouseEnter={(e) => (e.currentTarget.style.background = "#262638")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Clipboard size={13} color="#c084fc" />
        <span>Clipboard Shelf</span>
      </button>

      <div style={separatorStyle} />

      {/* Exit */}
      <button
        onClick={handleExit}
        style={{
          ...itemStyle,
          color: "#f87171",
        }}
        onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(239, 68, 68, 0.15)")}
        onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
      >
        <Power size={13} color="#f87171" />
        <span>Exit JARVIS</span>
      </button>
    </div>
  );
};
