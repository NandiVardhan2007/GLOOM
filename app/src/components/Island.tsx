import React, { useRef, useState, useEffect, useMemo } from "react";
import { motion, AnimatePresence } from "motion/react";
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { isTauriRuntime, safeListen } from "../lib/tauri";
import { ActiveTab, AppConfig, TimerState } from "../types";
import { MOTION_PHYSICS } from "../motion/physics";
import { ISLAND, clampScale, px } from "../design/island";
import { CollapsedPill, CompactKind } from "./CollapsedPill";
import { PeekGlance, ActivityKind, TransferActivity, NotificationActivity } from "./PeekGlance";
import { VolumeHUD } from "./VolumeHUD";
import { ExpandedPanel } from "./ExpandedPanel";
import { ContextMenu } from "./ContextMenu";
import { useMediaSession } from "../hooks/useMediaSession";
import { useSystemTelemetry } from "../hooks/useSystemTelemetry";
import { useVolumeHUD } from "../hooks/useVolumeHUD";
import { useHitTest } from "../hooks/useHitTest";
import { subscribeTimer, toTimerState } from "../utils/timerStore";

interface IslandProps {
  config: AppConfig;
  onUpdateConfig: (cfg: AppConfig) => void;
}

interface BridgeTransferPayload {
  id: string;
  fileName: string;
  fileSize: number;
  bytesTransferred: number;
  isUpload: boolean;
  progress: number;
  status: "transferring" | "completed" | "failed";
}

type Phase = "closed" | "compact" | "peek" | "expanded";

export const Island: React.FC<IslandProps> = ({ config, onUpdateConfig }) => {
  const [phase, setPhase] = useState<Phase>("closed");
  const [isPinned, setIsPinned] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<ActiveTab>("dashboard");
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const [activeTimer, setActiveTimer] = useState<TimerState | null>(null);
  const [isIslandDragOver, setIsIslandDragOver] = useState<boolean>(false);
  const [isForegroundFullscreen, setIsForegroundFullscreen] = useState<boolean>(false);
  const [transfer, setTransfer] = useState<TransferActivity | null>(null);
  const [notification] = useState<NotificationActivity | null>(null);

  const containerRef = useRef<HTMLDivElement>(null);
  const contextMenuRef = useRef<HTMLDivElement>(null);
  const hoverTimerRef = useRef<number | null>(null);
  const collapseTimerRef = useRef<number | null>(null);
  const transferClearRef = useRef<number | null>(null);
  const prevTrackRef = useRef<string>("");

  const { media, togglePlay, nextTrack, prevTrack, seek } = useMediaSession();
  const telemetry = useSystemTelemetry();
  const { volume, isMuted, showHUD, setVolume } = useVolumeHUD();

  const isSuppressed = isForegroundFullscreen && config.avoidFullscreen;
  const scale = clampScale(config.islandScale);

  useHitTest(containerRef, !isSuppressed, contextMenuRef);

  const activityKind: ActivityKind | null = useMemo(() => {
    if (notification) return "notification";
    if (transfer && transfer.status !== "failed") return "transfer";
    if (media.title || media.artist || media.playing) return "media";
    if (activeTimer) return "timer";
    return null;
  }, [notification, transfer, media.title, media.artist, media.playing, activeTimer]);

  const isCharging = !!(telemetry.hasBattery && telemetry.isCharging);
  const hasActivity = activityKind !== null || isCharging;

  const compactKind: CompactKind = (() => {
    if (isIslandDragOver) return "drop";
    if (notification) return "notification";
    if (transfer && transfer.status !== "failed") return "transfer";
    if (media.title || media.artist || media.playing) return "media";
    if (activeTimer) return "timer";
    if (isCharging) return "charging";
    return "idle";
  })();

  // ── Geometry: Apple's Live Activity specifications ─────────────────────
  const restW = px(ISLAND.rest.width, scale);
  const compactW = px(ISLAND.compact.width, scale);
  const compactH = px(ISLAND.compact.height, scale);
  const capsuleRadius = compactH / 2;
  const expandedRadius = px(ISLAND.expanded.radius, scale);

  const hudActive =
    showHUD && phase !== "expanded" && phase !== "peek" && !isSuppressed;

  let targetWidth = restW;
  let targetHeight = compactH;
  let targetRadius = capsuleRadius;

  if (phase === "expanded") {
    if (activeTab === "settings") {
      targetWidth = Math.max(config.expandedWidth, 880);
      targetHeight = 560;
    } else if (activeTab === "bridge") {
      targetWidth = 700;
      targetHeight = 500;
    } else if (activeTab === "dashboard") {
      targetWidth = 660;
      targetHeight = 400;
    } else if (activeTab === "media") {
      targetWidth = 620;
      targetHeight = 380;
    } else if (activeTab === "network") {
      targetWidth = 600;
      targetHeight = 380;
    } else if (activeTab === "controls") {
      targetWidth = 580;
      targetHeight = 380;
    } else if (activeTab === "notes") {
      targetWidth = 600;
      targetHeight = 400;
    } else if (activeTab === "clipboard") {
      targetWidth = 600;
      targetHeight = 400;
    } else if (activeTab === "bluetooth") {
      targetWidth = 580;
      targetHeight = 380;
    } else if (activeTab === "timer") {
      targetWidth = 520;
      targetHeight = 360;
    } else {
      targetWidth = Math.max(config.expandedWidth, 640);
      targetHeight = Math.max(config.expandedHeight, 400);
    }
    targetRadius = expandedRadius;
  } else if (phase === "peek") {
    targetWidth = px(ISLAND.expanded.width, scale);
    // HIG: the expanded presentation is 84–160 tall. Shorter activities use
    // less height so the card hugs its content.
    targetHeight = px(activityKind === "transfer" ? 132 : ISLAND.expanded.height, scale);
    targetRadius = expandedRadius;
  } else if (hudActive) {
    targetWidth = px(180, scale);
    targetHeight = compactH;
    targetRadius = capsuleRadius;
  } else if (phase === "compact") {
    targetWidth = compactW;
    targetHeight = compactH;
    targetRadius = capsuleRadius;
  }

  if (isSuppressed) {
    targetHeight = 0;
    targetRadius = 0;
  }

  const topOffset = config.mode === "notch" ? 0 : config.topOffset;
  const borderRadius =
    config.mode === "notch"
      ? `0 0 ${targetRadius}px ${targetRadius}px`
      : targetRadius;

  const motionTransition = config.reducedMotion
    ? MOTION_PHYSICS.reducedMotion
    : phase === "expanded" || phase === "peek"
    ? MOTION_PHYSICS.expand
    : phase === "compact"
    ? MOTION_PHYSICS.morph
    : MOTION_PHYSICS.collapse;

  // ── Lifecycle subscriptions ────────────────────────────────────────────
  useEffect(() => {
    return subscribeTimer((data) => {
      if (
        data.isTimerRunning ||
        data.isStopwatchRunning ||
        data.remainingSeconds !== data.totalSeconds ||
        data.stopwatchMs > 0
      ) {
        setActiveTimer(toTimerState(data));
      } else {
        setActiveTimer(null);
      }
    });
  }, []);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    safeListen<boolean>("jarvis://fullscreen-changed", (event) => {
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

  // Bridge transfers drive the transfer Live Activity.
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    safeListen<BridgeTransferPayload[]>("jarvis://bridge-transfers", (event) => {
      const list = event.payload;
      if (!list || list.length === 0) return;
      const latest = list[0];
      if (!latest) return;

      const next: TransferActivity = {
        fileName: latest.fileName,
        fileSizeMb: latest.fileSize / (1024 * 1024),
        progress: Math.min(1, Math.max(0, latest.progress ?? 0)),
        speedMbps: latest.status === "completed" ? "Complete" : "LAN Direct",
        isUpload: latest.isUpload,
        status: latest.status,
      };
      setTransfer(next);

      if (transferClearRef.current) window.clearTimeout(transferClearRef.current);
      if (latest.status === "completed") {
        transferClearRef.current = window.setTimeout(() => setTransfer(null), 3200);
      } else if (latest.status === "failed") {
        transferClearRef.current = window.setTimeout(() => setTransfer(null), 3600);
      }
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
      if (transferClearRef.current) window.clearTimeout(transferClearRef.current);
    };
  }, []);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    safeListen("jarvis://open-settings", () => {
      setActiveTab("settings");
      setPhase("expanded");
      setIsPinned(true);
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    safeListen("jarvis://bridge-pairing-request", () => {
      setActiveTab("bridge");
      setPhase("expanded");
      setIsPinned(true);
    })
      .then((fn) => {
        unlisten = fn;
      })
      .catch(console.error);

    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  useEffect(() => {
    if (!isTauriRuntime()) return;
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
            setPhase("expanded");
            setIsPinned(true);
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

  useEffect(() => {
    const handleGlobalContextMenu = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (target?.isContentEditable || target?.closest("input, textarea, [contenteditable='true']")) {
        return;
      }
      e.preventDefault();
    };
    window.addEventListener("contextmenu", handleGlobalContextMenu);
    return () => window.removeEventListener("contextmenu", handleGlobalContextMenu);
  }, []);

  useEffect(
    () => () => {
      if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
      if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
      if (transferClearRef.current) window.clearTimeout(transferClearRef.current);
    },
    []
  );

  // Resting state follows the presence of a Live Activity.
  useEffect(() => {
    setPhase((current) => {
      if (current === "expanded" || current === "peek") return current;
      return hasActivity ? "compact" : "closed";
    });
  }, [hasActivity]);

  // A new track is a Live Activity update: show the compact presentation.
  useEffect(() => {
    if (
      media.title &&
      prevTrackRef.current &&
      media.title !== prevTrackRef.current &&
      phase === "closed" &&
      !isSuppressed
    ) {
      setPhase("compact");
    }
    prevTrackRef.current = media.title;
  }, [media.title, phase, isSuppressed]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && phase === "expanded") {
        collapseToRest();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  useEffect(() => {
    const handlePointerDown = (e: MouseEvent) => {
      if (phase === "expanded" && containerRef.current) {
        if (!containerRef.current.contains(e.target as Node)) {
          collapseToRest();
        }
      }
    };
    window.addEventListener("pointerdown", handlePointerDown);
    return () => window.removeEventListener("pointerdown", handlePointerDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  // ── Interaction ────────────────────────────────────────────────────────
  const clearTimers = () => {
    if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    hoverTimerRef.current = null;
    collapseTimerRef.current = null;
  };

  const collapseToRest = () => {
    clearTimers();
    setIsPinned(false);
    setPhase(hasActivity ? "compact" : "closed");
    containerRef.current?.focus();
  };

  const handleMouseEnter = () => {
    if (collapseTimerRef.current) {
      window.clearTimeout(collapseTimerRef.current);
      collapseTimerRef.current = null;
    }
    if (phase === "expanded") return;

    if (phase === "closed" && hasActivity) setPhase("compact");

    if (!config.expandOnHover) return;

    if (hoverTimerRef.current) window.clearTimeout(hoverTimerRef.current);
    // Apple's touch-and-hold equivalent on a pointer device.
    hoverTimerRef.current = window.setTimeout(() => {
      setPhase("peek");
    }, 300);
  };

  const handleMouseLeave = () => {
    if (hoverTimerRef.current) {
      window.clearTimeout(hoverTimerRef.current);
      hoverTimerRef.current = null;
    }
    if (isPinned || activeTab === "settings" || phase === "expanded") return;

    if (collapseTimerRef.current) window.clearTimeout(collapseTimerRef.current);
    collapseTimerRef.current = window.setTimeout(() => {
      setPhase(hasActivity ? "compact" : "closed");
    }, 160);
  };

  const handleClick = () => {
    clearTimers();
    if (phase !== "expanded") {
      setPhase("expanded");
      setIsPinned(true);
    }
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({ x: e.clientX, y: e.clientY });
  };

  const handleRegionKeyDown = (e: React.KeyboardEvent) => {
    if ((e.key === "Enter" || e.key === " ") && phase !== "expanded") {
      e.preventDefault();
      setPhase("expanded");
      setIsPinned(true);
    }
  };

  // ── Shell styling ──────────────────────────────────────────────────────
  const isExpandedLike = phase === "expanded" || phase === "peek";
  const shellStyle: React.CSSProperties & Record<string, string | number> = {
    position: "absolute",
    top: `${topOffset}px`,
    pointerEvents: isSuppressed ? "none" : "auto",
    overflow: "hidden",
    transformOrigin: "top center",
    willChange: "width, height, border-radius",
    backgroundColor: ISLAND.surface,
    border: isIslandDragOver
      ? "1.5px dashed rgba(10, 132, 255, 0.9)"
      : isExpandedLike
      ? `0.5px solid ${ISLAND.keyline}`
      : "none",
    borderRadius,
    boxShadow: isIslandDragOver
      ? "0 0 0 4px rgba(10,132,255,0.16), 0 12px 28px rgba(0,0,0,0.45)"
      : isExpandedLike
      ? "inset 0 0.5px 0 rgba(255,255,255,0.14), 0 12px 28px rgba(0, 0, 0, 0.45)"
      : "inset 0 0.5px 0 rgba(255,255,255,0.10), 0 0 0 0.5px rgba(255,255,255,0.07)",
    zIndex: 9999,
    isolation: "isolate",
  };

  // Chromium 139+ renders Apple's continuous corners natively. Older engines
  // fall back to the circular radius above.
  shellStyle["cornerShape"] = isExpandedLike ? "squircle" : "round";

  const contentTransition = config.reducedMotion ? MOTION_PHYSICS.reducedMotion : MOTION_PHYSICS.fade;
  const contentStyle: React.CSSProperties = {
    position: "absolute",
    inset: 0,
    width: "100%",
    height: "100%",
  };

  const content = (() => {
    if (hudActive) {
      return (
        <motion.div
          key="hud"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={contentTransition}
          style={contentStyle}
        >
          <VolumeHUD volume={volume} isMuted={isMuted} />
        </motion.div>
      );
    }

    if (phase === "expanded") {
      return (
        <motion.div
          key="expanded"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={contentTransition}
          style={contentStyle}
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
            onCollapse={collapseToRest}
            onTogglePlay={togglePlay}
            onNext={nextTrack}
            onPrev={prevTrack}
            onSeek={seek}
            onSetVolume={setVolume}
            onUpdateConfig={onUpdateConfig}
            onTimerUpdate={setActiveTimer}
          />
        </motion.div>
      );
    }

    if (phase === "peek") {
      return (
        <motion.div
          key={`peek-${activityKind ?? "continuity"}`}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={contentTransition}
          style={contentStyle}
        >
          <PeekGlance
            kind={activityKind ?? "continuity"}
            media={media}
            telemetry={telemetry}
            timer={activeTimer}
            transfer={transfer}
            notification={notification}
            scale={scale}
            reducedMotion={config.reducedMotion}
            onPlayPause={togglePlay}
            onNext={nextTrack}
            onPrev={prevTrack}
            onSeek={seek}
            onOpen={() => {
              setPhase("expanded");
              setIsPinned(true);
            }}
            onClose={collapseToRest}
          />
        </motion.div>
      );
    }

    return (
      <motion.div
        key={`compact-${compactKind}`}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={contentTransition}
        style={{ width: "100%", height: "100%" }}
      >
        <CollapsedPill
          kind={compactKind}
          media={media}
          telemetry={telemetry}
          timer={activeTimer}
          transfer={transfer}
          notification={notification}
          scale={scale}
          reducedMotion={config.reducedMotion}
          onClick={handleClick}
        />
      </motion.div>
    );
  })();

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
        aria-expanded={phase === "expanded"}
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
            setPhase("expanded");
            setIsPinned(true);
          }
        }}
        animate={{
          width: targetWidth,
          height: targetHeight,
          borderRadius: targetRadius,
          top: topOffset,
        }}
        transition={motionTransition}
        style={shellStyle}
      >
        <AnimatePresence initial={false} mode="popLayout">
          {content}
        </AnimatePresence>
      </motion.div>

      {contextMenu && (
        <ContextMenu
          menuRef={contextMenuRef}
          x={contextMenu.x}
          y={contextMenu.y}
          isExpanded={phase === "expanded"}
          isPinned={isPinned}
          config={config}
          onClose={() => setContextMenu(null)}
          onSelectTab={(tab) => {
            setActiveTab(tab);
            setPhase("expanded");
            setIsPinned(true);
          }}
          onToggleExpand={() => {
            if (phase === "expanded") {
              collapseToRest();
            } else {
              setPhase("expanded");
              setIsPinned(true);
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
