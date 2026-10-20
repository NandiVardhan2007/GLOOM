import React, { useRef, useState, useEffect } from "react";
import { motion, AnimatePresence } from "motion/react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { ActiveTab, AppConfig, IslandState, TimerState } from "../types";
import { MOTION_PHYSICS } from "../motion/physics";
import { CollapsedPill } from "./CollapsedPill";
import { VolumeHUD } from "./VolumeHUD";
import { ExpandedPanel } from "./ExpandedPanel";
import { ContextMenu } from "./ContextMenu";
import { useMediaSession } from "../hooks/useMediaSession";
import { useSystemTelemetry } from "../hooks/useSystemTelemetry";
import { useVolumeHUD } from "../hooks/useVolumeHUD";
import { useHitTest } from "../hooks/useHitTest";

interface IslandProps {
  config: AppConfig;
  onUpdateConfig: (cfg: AppConfig) => void;
}

export const Island: React.FC<IslandProps> = ({ config, onUpdateConfig }) => {
  const [islandState, setIslandState] = useState<IslandState>("closed");
  const [isPinned, setIsPinned] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<ActiveTab>("dashboard");
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const [activeTimer, setActiveTimer] = useState<TimerState | null>(null);
  const [isIslandDragOver, setIsIslandDragOver] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const contextMenuRef = useRef<HTMLDivElement>(null);
  const hoverTimerRef = useRef<number | null>(null);
  const collapseTimerRef = useRef<number | null>(null);

  const { media, togglePlay, nextTrack, prevTrack, seek } = useMediaSession();
  const telemetry = useSystemTelemetry();
  const { volume, isMuted, showHUD, setVolume } = useVolumeHUD();

  // Register interactive hit rects with Rust backend (including context menu if open)
  useHitTest(containerRef, true, contextMenuRef);

  // Listen to global open-settings events (tray, shortcuts, external)
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    listen("jarvis://open-settings", () => {
      setActiveTab("settings");
      setIslandState("expanded");
    }).then((fn) => {
      unlisten = fn;
    }).catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  // Disable default browser context menu globally
  useEffect(() => {
    const handleGlobalContextMenu = (e: MouseEvent) => {
      e.preventDefault();
    };
    window.addEventListener("contextmenu", handleGlobalContextMenu);
    return () => window.removeEventListener("contextmenu", handleGlobalContextMenu);
  }, []);

  // Keyboard shortcut: Escape to collapse and unpin
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && islandState === "expanded") {
        setIslandState("closed");
        setIsPinned(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [islandState]);

  // Click outside to collapse
  useEffect(() => {
    const handlePointerDown = (e: MouseEvent) => {
      if (islandState === "expanded" && containerRef.current) {
        if (!containerRef.current.contains(e.target as Node)) {
          setIslandState("closed");
          setIsPinned(false);
        }
      }
    };
    window.addEventListener("pointerdown", handlePointerDown);
    return () => window.removeEventListener("pointerdown", handlePointerDown);
  }, [islandState]);

  // Expand on hover
  const handleMouseEnter = () => {
    if (collapseTimerRef.current) {
      window.clearTimeout(collapseTimerRef.current);
      collapseTimerRef.current = null;
    }
    if (islandState === "expanded") return;

    // 70ms dwell filter before expanding to prevent accidental fast sweeps
    if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
    hoverTimerRef.current = window.setTimeout(() => {
      setIslandState("expanded");
    }, 25);
  };

  // Collapse on mouse out
  const handleMouseLeave = () => {
    if (hoverTimerRef.current) {
      window.clearTimeout(hoverTimerRef.current);
      hoverTimerRef.current = null;
    }

    // If pinned by user or currently on settings tab, do not collapse
    if (isPinned || activeTab === "settings") return;

    // 200ms grace period to avoid accidental collapse when near borders
    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    collapseTimerRef.current = window.setTimeout(() => {
      setIslandState("closed");
    }, 120);
  };

  // Immediate expansion on click
  const handleClick = () => {
    if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    if (islandState !== "expanded") {
      setIslandState("expanded");
    }
  };

  const handleCollapse = () => {
    if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    setIslandState("closed");
    setIsPinned(false);
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({ x: e.clientX, y: e.clientY });
  };

  // Compute animated dimensions
  const isExpanded = islandState === "expanded";

  let targetWidth = config.islandWidth;
  let targetHeight = config.islandHeight;
  let targetRadius = config.islandHeight / 2;

  if (showHUD && !isExpanded) {
    targetWidth = 240;
    targetHeight = 38;
    targetRadius = 19;
  } else if (isExpanded) {
    if (activeTab === "settings") {
      targetWidth = Math.max(config.expandedWidth, 860);
      targetHeight = 520;
      targetRadius = 24;
    } else {
      targetWidth = config.expandedWidth;
      targetHeight = config.expandedHeight;
      targetRadius = 24;
    }
  }

  const topOffset = config.mode === "notch" ? 0 : config.topOffset;
  const borderRadius =
    config.mode === "notch"
      ? `0 0 ${targetRadius}px ${targetRadius}px`
      : `${targetRadius}px`;

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        display: "flex",
        justifyContent: "center",
        pointerEvents: "none",
      }}
    >
      <motion.div
        ref={containerRef}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onClick={handleClick}
        onContextMenu={handleContextMenu}
        onDragOver={(e) => {
          e.preventDefault();
          setIsIslandDragOver(true);
        }}
        onDragLeave={(e) => {
          e.preventDefault();
          setIsIslandDragOver(false);
        }}
        onDrop={(e) => {
          e.preventDefault();
          setIsIslandDragOver(false);
          const files = e.dataTransfer.files;
          if (files && files.length > 0) {
            const file = files[0];
            invoke("jarvis_send_bridge_file", {
              deviceName: "Galaxy S24 Ultra",
              fileName: file.name,
              fileSize: file.size,
            }).catch(console.error);
            setActiveTab("bridge");
            setIslandState("expanded");
          }
        }}
        animate={{
          width: targetWidth,
          height: targetHeight,
          borderRadius: targetRadius,
          top: topOffset,
        }}
        transition={MOTION_PHYSICS.morph}
        style={{
          position: "absolute",
          top: `${topOffset}px`,
          pointerEvents: "auto",
          overflow: "hidden",
          transformOrigin: "top center",
          willChange: "width, height, border-radius, top",
          transform: "translateZ(0)",
          zIndex: 9999,
          backgroundColor: isExpanded ? "#0d0d14" : "#000000",
          border: isIslandDragOver
            ? "2px dashed #38bdf8"
            : isExpanded
            ? "1px solid rgba(255, 255, 255, 0.18)"
            : "1px solid rgba(255, 255, 255, 0.2)",
          borderRadius,
          boxShadow: isIslandDragOver
            ? "0 0 30px rgba(56, 189, 248, 0.7), 0 0 60px rgba(56, 189, 248, 0.35)"
            : isExpanded
            ? "0 24px 50px -4px rgba(0, 0, 0, 0.95), 0 8px 24px -2px rgba(0, 0, 0, 0.8)"
            : `0 0 18px ${config.accentColor ? `${config.accentColor}55` : "rgba(0, 140, 255, 0.45)"}, 0 10px 24px -2px rgba(0, 0, 0, 0.9)`,
        }}
      >
        <AnimatePresence initial={false}>
          {showHUD && !isExpanded ? (
            <motion.div
              key="volume-hud"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              transition={MOTION_PHYSICS.fade}
              style={{ width: "100%", height: "100%" }}
            >
              <VolumeHUD volume={volume} isMuted={isMuted} />
            </motion.div>
          ) : isExpanded ? (
            <motion.div
              key="expanded"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={MOTION_PHYSICS.fade}
              style={{
                width: "100%",
                height: "100%",
                backgroundColor: "#0d0d14",
              }}
            >
              <ExpandedPanel
                media={media}
                telemetry={telemetry}
                volume={volume}
                config={config}
                isPinned={isPinned}
                timer={activeTimer}
                activeTab={activeTab}
                onSelectTab={setActiveTab}
                onTogglePin={() => setIsPinned(!isPinned)}
                onCollapse={handleCollapse}
                onTogglePlay={togglePlay}
                onNext={nextTrack}
                onPrev={prevTrack}
                onSeek={seek}
                onSetVolume={setVolume}
                onUpdateConfig={onUpdateConfig}
                onTimerUpdate={setActiveTimer}
              />
            </motion.div>
          ) : (
            <motion.div
              key="collapsed"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={MOTION_PHYSICS.fade}
              style={{
                width: "100%",
                height: "100%",
                backgroundColor: "#000000",
              }}
            >
              <CollapsedPill
                media={media}
                telemetry={telemetry}
                timer={activeTimer}
                clockFormat={config.clockFormat}
                isDragging={isIslandDragOver}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>

      {/* Custom Desktop Right-Click Context Menu */}
      {contextMenu && (
        <ContextMenu
          menuRef={contextMenuRef}
          x={contextMenu.x}
          y={contextMenu.y}
          isExpanded={isExpanded}
          isPinned={isPinned}
          config={config}
          onClose={() => setContextMenu(null)}
          onSelectTab={(tab) => {
            setActiveTab(tab);
            if (!isExpanded) setIslandState("expanded");
          }}
          onToggleExpand={() => {
            if (isExpanded) {
              handleCollapse();
            } else {
              setIslandState("expanded");
            }
          }}
          onTogglePin={() => setIsPinned(!isPinned)}
          onToggleMode={() => {
            const nextMode = config.mode === "island" ? "notch" : "island";
            onUpdateConfig({
              ...config,
              mode: nextMode,
              topOffset: nextMode === "notch" ? 0 : 12,
            });
          }}
        />
      )}
    </div>
  );
};
