import React, { useRef, useState, useEffect } from "react";
import { motion, AnimatePresence } from "motion/react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { ActiveTab, AppConfig, IslandState, TimerState } from "../types";
import { MOTION_PHYSICS } from "../motion/physics";
import { CollapsedPill } from "./CollapsedPill";
import { PeekGlance } from "./PeekGlance";
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
  const [isForegroundFullscreen, setIsForegroundFullscreen] = useState<boolean>(false);
  const [peekMessage, setPeekMessage] = useState<string | null>(null);

  const containerRef = useRef<HTMLDivElement>(null);
  const contextMenuRef = useRef<HTMLDivElement>(null);
  const hoverTimerRef = useRef<number | null>(null);
  const collapseTimerRef = useRef<number | null>(null);
  const peekTimerRef = useRef<number | null>(null);
  const prevTrackRef = useRef<string>("");

  const { media, togglePlay, nextTrack, prevTrack, seek } = useMediaSession();
  const telemetry = useSystemTelemetry();
  const { volume, isMuted, showHUD, setVolume } = useVolumeHUD();

  // Fullscreen suppression condition
  const isSuppressed = isForegroundFullscreen && config.avoidFullscreen;

  // Register interactive hit rects with Rust backend (disabled when suppressed)
  useHitTest(containerRef, !isSuppressed, contextMenuRef);

  // Listen to background fullscreen change events
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    listen<boolean>("jarvis://fullscreen-changed", (event) => {
      setIsForegroundFullscreen(event.payload);
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  // Listen to bridge transfers for peek alerts
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    listen<any[]>("jarvis://bridge-transfers", (event) => {
      const transfers = event.payload;
      if (transfers && transfers.length > 0 && islandState === "closed" && !isSuppressed) {
        const latest = transfers[transfers.length - 1];
        setPeekMessage(`${latest.fileName} (${latest.status})`);
        setIslandState("peek");
        if (peekTimerRef.current) window.clearTimeout(peekTimerRef.current);
        peekTimerRef.current = window.setTimeout(() => {
          setIslandState("closed");
          setPeekMessage(null);
        }, 3000);
      }
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, [islandState, isSuppressed]);

  // Track changes trigger a transient peek glance if closed
  useEffect(() => {
    if (
      media.title &&
      prevTrackRef.current &&
      media.title !== prevTrackRef.current &&
      islandState === "closed" &&
      !isSuppressed
    ) {
      setIslandState("peek");
      if (peekTimerRef.current) window.clearTimeout(peekTimerRef.current);
      peekTimerRef.current = window.setTimeout(() => {
        setIslandState("closed");
      }, 2600);
    }
    prevTrackRef.current = media.title;
  }, [media.title, islandState, isSuppressed]);

  // Listen to global open-settings events (tray, shortcuts, external)
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    listen("jarvis://open-settings", () => {
      setActiveTab("settings");
      setIslandState("expanded");
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  // Listen to Tauri native window drag-drop events (from Windows Explorer)
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    getCurrentWebview()
      .onDragDropEvent(async (event) => {
        const payload = event.payload;
        if (payload.type === "enter" || payload.type === "over") {
          setIsIslandDragOver(true);
        } else if (payload.type === "leave") {
          setIsIslandDragOver(false);
        } else if (payload.type === "drop") {
          setIsIslandDragOver(false);
          const paths = payload.paths;
          if (paths && paths.length > 0) {
            for (const filePath of paths) {
              await invoke("jarvis_send_bridge_file_path", { filePath }).catch(console.error);
            }
            setActiveTab("bridge");
            setIslandState("expanded");
          }
        }
      })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

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
        containerRef.current?.focus();
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

  // Hover management: supports Peek and Expand
  const handleMouseEnter = () => {
    if (collapseTimerRef.current) {
      window.clearTimeout(collapseTimerRef.current);
      collapseTimerRef.current = null;
    }
    if (islandState === "expanded") return;

    if (config.expandOnHover) {
      // Immediate peek glance, followed by full expand after dwell
      setIslandState("peek");
      if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
      hoverTimerRef.current = window.setTimeout(() => {
        setIslandState("expanded");
      }, 180);
    } else {
      setIslandState("peek");
    }
  };

  const handleMouseLeave = () => {
    if (hoverTimerRef.current) {
      window.clearTimeout(hoverTimerRef.current);
      hoverTimerRef.current = null;
    }
    if (peekTimerRef.current) {
      window.clearTimeout(peekTimerRef.current);
      peekTimerRef.current = null;
    }

    // If pinned by user or currently on settings tab, do not collapse
    if (isPinned || activeTab === "settings") return;

    // Grace period to avoid accidental collapse
    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    collapseTimerRef.current = window.setTimeout(() => {
      setIslandState("closed");
    }, 140);
  };

  // Immediate expansion on click
  const handleClick = () => {
    if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    if (peekTimerRef.current) window.clearTimeout(peekTimerRef.current);
    if (islandState !== "expanded") {
      setIslandState("expanded");
    }
  };

  const handleCollapse = () => {
    if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    if (peekTimerRef.current) window.clearTimeout(peekTimerRef.current);
    setIslandState("closed");
    setIsPinned(false);
    containerRef.current?.focus();
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({ x: e.clientX, y: e.clientY });
  };

  // Keyboard navigation within island region
  const handleRegionKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" || e.key === " ") {
      if (islandState !== "expanded") {
        e.preventDefault();
        setIslandState("expanded");
      }
    }
  };

  // Compute animated dimensions
  const isExpanded = islandState === "expanded";
  const isPeek = islandState === "peek";

  let targetWidth = config.islandWidth;
  let targetHeight = config.islandHeight;
  let targetRadius = config.islandHeight / 2;

  if (isSuppressed) {
    targetWidth = config.islandWidth;
    targetHeight = 0;
    targetRadius = 0;
  } else if (showHUD && !isExpanded) {
    targetWidth = 240;
    targetHeight = 38;
    targetRadius = 19;
  } else if (isPeek) {
    targetWidth = 320;
    targetHeight = 44;
    targetRadius = 22;
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

  const motionTransition = config.reducedMotion
    ? MOTION_PHYSICS.reducedMotion
    : isExpanded
    ? MOTION_PHYSICS.expand
    : isPeek
    ? MOTION_PHYSICS.peek
    : MOTION_PHYSICS.collapse;

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
        opacity: isSuppressed ? 0 : 1,
        transition: "opacity 0.2s ease",
      }}
    >
      <motion.div
        ref={containerRef}
        role="region"
        aria-label="JARVIS Dynamic Island"
        aria-expanded={isExpanded}
        tabIndex={0}
        onKeyDown={handleRegionKeyDown}
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
        onDrop={async (e) => {
          e.preventDefault();
          setIsIslandDragOver(false);
          const files = e.dataTransfer.files;
          if (files && files.length > 0) {
            for (let i = 0; i < files.length; i++) {
              const file = files[i];
              const filePath = (file as any).path;
              if (filePath) {
                await invoke("jarvis_send_bridge_file_path", { filePath }).catch(console.error);
              } else {
                const reader = new FileReader();
                reader.onload = async () => {
                  const b64 = (reader.result as string).split(",")[1];
                  await invoke("jarvis_send_bridge_file_bytes", {
                    deviceName: "Live Bridge Device",
                    fileName: file.name,
                    fileSize: file.size,
                    dataBase64: b64,
                  }).catch(console.error);
                };
                reader.readAsDataURL(file);
              }
            }
            setActiveTab("bridge");
            setIslandState("expanded");
          }
        }}
        whileTap={!isExpanded && !isPeek ? { scale: 0.98 } : undefined}
        animate={{
          width: targetWidth,
          height: targetHeight,
          borderRadius: targetRadius,
          top: topOffset,
        }}
        transition={motionTransition}
        style={{
          position: "absolute",
          top: `${topOffset}px`,
          pointerEvents: isSuppressed ? "none" : "auto",
          overflow: "hidden",
          transformOrigin: "top center",
          willChange: "width, height, border-radius, top",
          transform: "translateZ(0)",
          zIndex: 9999,
          backgroundColor: isExpanded ? "rgba(13, 14, 20, 0.94)" : "#000000",
          backdropFilter: "blur(32px) saturate(190%)",
          WebkitBackdropFilter: "blur(32px) saturate(190%)",
          border: isIslandDragOver
            ? "2px dashed #32ade6"
            : isExpanded
            ? "0.5px solid rgba(255, 255, 255, 0.16)"
            : "0.5px solid rgba(255, 255, 255, 0.18)",
          borderRadius,
          boxShadow: isIslandDragOver
            ? "0 0 30px rgba(50, 173, 230, 0.7), 0 0 60px rgba(50, 173, 230, 0.35)"
            : isExpanded
            ? "inset 0 0.5px 0.5px 0 rgba(255, 255, 255, 0.28), 0 24px 50px -4px rgba(0, 0, 0, 0.95), 0 8px 24px -2px rgba(0, 0, 0, 0.8)"
            : `inset 0 0.5px 0.5px 0 rgba(255, 255, 255, 0.22), 0 0 16px ${
                config.accentColor ? `${config.accentColor}44` : "rgba(0, 140, 255, 0.35)"
              }, 0 12px 28px -2px rgba(0, 0, 0, 0.9)`,
        }}
      >
        <AnimatePresence initial={false}>
          {showHUD && !isExpanded ? (
            <motion.div
              key="volume-hud"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              transition={config.reducedMotion ? MOTION_PHYSICS.reducedMotion : MOTION_PHYSICS.fade}
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
              transition={config.reducedMotion ? MOTION_PHYSICS.reducedMotion : MOTION_PHYSICS.fade}
              style={{
                width: "100%",
                height: "100%",
                backgroundColor: "transparent",
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
          ) : isPeek ? (
            <motion.div
              key="peek"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={config.reducedMotion ? MOTION_PHYSICS.reducedMotion : MOTION_PHYSICS.fade}
              style={{
                width: "100%",
                height: "100%",
                backgroundColor: "#000000",
              }}
            >
              <PeekGlance
                media={media}
                telemetry={telemetry}
                timer={activeTimer}
                message={peekMessage}
                reducedMotion={config.reducedMotion}
              />
            </motion.div>
          ) : (
            <motion.div
              key="collapsed"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={config.reducedMotion ? MOTION_PHYSICS.reducedMotion : MOTION_PHYSICS.fade}
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
                reducedMotion={config.reducedMotion}
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
